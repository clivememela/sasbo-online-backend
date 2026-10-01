package za.co.titandynamix.sasbo_online_backend.repository;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import za.co.titandynamix.sasbo_online_backend.support.AbstractSwitchedOnIT;
import za.co.titandynamix.sasbo_online_backend.support.DatabaseReset;
import za.co.titandynamix.sasbo_online_backend.utils.ReferenceListSeeder;
import za.co.titandynamix.sasbo_online_backend.utils.WorkplaceSeeder;
import za.co.titandynamix.sasbo_online_backend.utils.WorkplaceVisitSeeder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Branch visits on PostgreSQL (month end A.1, MO22): a visit captured is a visit on record; a changed complement keeps
 * what it was, and outlives the next seed; the table is append-only by its trigger, and the lane's reset still empties
 * it; SASBO's own history, the months the branch list prints, is seeded once.
 */
@DisplayName("Branch visits, on PostgreSQL")
class WorkplaceVisitIT extends AbstractSwitchedOnIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ReferenceListSeeder lists;

    @Autowired
    private WorkplaceSeeder workplaces;

    @Autowired
    private WorkplaceVisitSeeder history;

    @Autowired
    private WorkplaceVisitRepository visits;

    private long workplace;
    private int complement;

    @BeforeEach
    void sasbosWorkplaces() {
        lists.seed();
        workplaces.seed();
        workplace = jdbc.queryForObject("SELECT id FROM workplaces ORDER BY ra_number LIMIT 1", Long.class);
        complement = jdbc.queryForObject("SELECT staff_complement FROM workplaces WHERE id = ?", Integer.class, workplace);
    }

    private MockHttpServletResponse record(String body) throws Exception {
        return mockMvc.perform(post("/api/workplaces/{id}/visits", workplace)
                .contentType(MediaType.APPLICATION_JSON).content(body)).andReturn().getResponse();
    }

    @Test
    @DisplayName("🔒 A.1: a visit captured is a visit on record -- its complement written onto the workplace, what it was kept; the read answers the day and the complement, never the visitor's name")
    void aVisitCapturedIsAVisitOnRecord() throws Exception {
        MockHttpServletResponse created = record("{\"visitedOn\": \"2026-09-10\", \"timeIn\": \"09:00\", "
                + "\"timeOut\": \"10:30\", \"organiser\": \"INVENTED ORGANISER\", \"complementFound\": " + (complement + 3)
                + ", \"membershipFound\": 12, \"nonMembers\": 4, \"recruited\": 2, \"nonMembersInterviewed\": 3}");

        assertThat(created.getStatus()).isEqualTo(201);
        assertThat(visits.count(workplace)).isEqualTo(1L);
        assertThat(jdbc.queryForObject("SELECT staff_complement FROM workplaces WHERE id = ?", Integer.class, workplace))
                .as("the complement the visit found, written onto the workplace").isEqualTo(complement + 3);
        assertThat(jdbc.queryForMap("SELECT complement_before, membership_found, recruited, organiser, seeded, day_known "
                        + "FROM workplace_visits WHERE workplace_id = ?", workplace))
                .containsEntry("complement_before", complement).containsEntry("membership_found", 12)
                .containsEntry("recruited", 2).containsEntry("organiser", "INVENTED ORGANISER")
                .containsEntry("seeded", false).containsEntry("day_known", true);

        String read = mockMvc.perform(get("/api/workplaces/{id}/visits", workplace)).andReturn().getResponse()
                .getContentAsString();
        JsonNode page = objectMapper.readTree(read);
        assertThat(page.get("visits")).hasSize(1);
        assertThat(page.get("visits").get(0).get("complementFound").asInt()).isEqualTo(complement + 3);
        assertThat(page.get("visits").get(0).get("complementBefore").asInt()).isEqualTo(complement);
        assertThat(read).as("who visited, and the counts of members, are kept and not answered (MD61, B1)")
                .doesNotContain("INVENTED ORGANISER").doesNotContain("membershipFound\":12");
    }

    @Test
    @DisplayName("🔒 A.1: refused whole -- a visit with a count below nought writes no row and leaves the complement as it was")
    void refusedWhole() throws Exception {
        MockHttpServletResponse refused = record("{\"visitedOn\": \"2026-09-10\", \"complementFound\": "
                + (complement + 3) + ", \"recruited\": -1}");

        assertThat(refused.getStatus()).isEqualTo(400);
        assertThat(objectMapper.readTree(refused.getContentAsString()).get("code").asText()).isEqualTo("VISIT_INVALID");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM workplace_visits", Long.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT staff_complement FROM workplaces WHERE id = ?", Integer.class, workplace))
                .isEqualTo(complement);
    }

    @Test
    @DisplayName("🔒 A.1, MD46: append-only by its trigger, by name -- and its twin, BR64: the lane's reset still empties it")
    void appendOnly() throws Exception {
        record("{\"visitedOn\": \"2026-09-10\"}");

        assertThatThrownBy(() -> jdbc.update("UPDATE workplace_visits SET organiser = 'CHANGED'"))
                .hasMessageContaining("workplace_visits is append-only");
        assertThatThrownBy(() -> jdbc.update("DELETE FROM workplace_visits"))
                .hasMessageContaining("workplace_visits is append-only");
        DatabaseReset.reset(jdbc);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM workplace_visits", Long.class)).isZero();
    }

    @Test
    @DisplayName("🔒 A.1: SASBO's own history -- the months the branch list prints -- seeded once, its day unknown; a visit captured in a seeded month stands beside it")
    void theBranchListsHistory() throws Exception {
        history.seed();
        long seeded = jdbc.queryForObject("SELECT count(*) FROM workplace_visits WHERE seeded AND NOT day_known",
                Long.class);
        assertThat(seeded).as("the resource's months, every workplace on record here").isEqualTo(46_702L);

        history.seed();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM workplace_visits", Long.class))
                .as("a second start writes nothing twice").isEqualTo(seeded);

        String month = jdbc.queryForObject("SELECT to_char(max(visited_on), 'YYYY-MM') FROM workplace_visits "
                + "WHERE workplace_id = ?", String.class, workplace);
        assertThat(record("{\"visitedOn\": \"" + month + "-15\"}").getStatus()).isEqualTo(201);
        JsonNode newest = objectMapper.readTree(mockMvc.perform(get("/api/workplaces/{id}/visits", workplace))
                .andReturn().getResponse().getContentAsString()).get("visits").get(0);
        assertThat(newest.get("visitedOn").asText()).isEqualTo(month + "-15");
        assertThat(newest.get("seeded").asBoolean()).isFalse();
    }

    @Test
    @DisplayName("🔒 A.1: a complement a visit wrote outlives the next seed of the workplaces -- the branch list is the older of the two")
    void aVisitsComplementOutlivesTheSeed() throws Exception {
        record("{\"visitedOn\": \"2026-09-10\", \"complementFound\": " + (complement + 5) + "}");

        workplaces.seed();

        assertThat(jdbc.queryForObject("SELECT staff_complement FROM workplaces WHERE id = ?", Integer.class, workplace))
                .isEqualTo(complement + 5);
    }
}
