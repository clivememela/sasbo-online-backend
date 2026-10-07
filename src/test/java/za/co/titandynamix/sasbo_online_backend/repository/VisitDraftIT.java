package za.co.titandynamix.sasbo_online_backend.repository;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import za.co.titandynamix.sasbo_online_backend.security.CallerScope;
import za.co.titandynamix.sasbo_online_backend.security.ScopedPrincipal;
import za.co.titandynamix.sasbo_online_backend.support.AbstractSwitchedOnIT;
import za.co.titandynamix.sasbo_online_backend.support.DatabaseReset;
import za.co.titandynamix.sasbo_online_backend.utils.ReferenceListSeeder;
import za.co.titandynamix.sasbo_online_backend.utils.VisitDraftSeeder;
import za.co.titandynamix.sasbo_online_backend.utils.WorkplaceSeeder;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * The visit forms on PostgreSQL (October build, V69_1): a draft saved, saved again from an older version and refused,
 * submitted -- the visit on record through the visit's own path, its complement written through -- and read-only after;
 * a submission sent twice is one visit; another person's draft is not there; the table's constraints and its trigger,
 * by name; and the seed's invented drafts, once.
 */
@DisplayName("Visit drafts, on PostgreSQL")
class VisitDraftIT extends AbstractSwitchedOnIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ReferenceListSeeder lists;

    @Autowired
    private WorkplaceSeeder workplaces;

    @Autowired
    private VisitDraftSeeder seed;

    @Autowired
    private WorkplaceVisitDraftRepository drafts;

    private long workplace;
    private int raNumber;
    private int complement;

    /** A signed-in caller of the sign-in seed's shape: a username, a name, and how far they may see. */
    record Caller(String username, String displayName, CallerScope scope) implements ScopedPrincipal {
    }

    @BeforeEach
    void sasbosWorkplaces() {
        lists.seed();
        workplaces.seed();
        Map<String, Object> first = jdbc.queryForMap("SELECT w.id, w.ra_number, w.staff_complement FROM workplaces w "
                + "JOIN sasbo_areas a ON a.id = w.area_id WHERE a.letter = 'L' ORDER BY w.ra_number LIMIT 1");
        workplace = ((Number) first.get("id")).longValue();
        raNumber = ((Number) first.get("ra_number")).intValue();
        complement = ((Number) first.get("staff_complement")).intValue();
    }

    private static MockHttpServletRequestBuilder as(String username, CallerScope scope,
                                                    MockHttpServletRequestBuilder request) {
        return request.with(authentication(new UsernamePasswordAuthenticationToken(
                new Caller(username, "Invented " + username, scope), null,
                AuthorityUtils.createAuthorityList("VISITS_CAPTURE"))));
    }

    private MockHttpServletResponse asOrganiser(MockHttpServletRequestBuilder request) throws Exception {
        return mockMvc.perform(as("invented.organiser", CallerScope.areas(Set.of("L")), request)).andReturn()
                .getResponse();
    }

    private JsonNode json(MockHttpServletResponse response) throws Exception {
        return objectMapper.readTree(response.getContentAsString());
    }

    private String saved(int complementFound, int recruited, Integer version) {
        return "{\"raNumber\": " + raNumber + ", \"visitedOn\": \"2026-09-10\", \"timeIn\": \"09:00\", \"timeOut\": "
                + "\"10:30\", \"organiser\": \"INVENTED ORGANISER\", \"complementFound\": " + complementFound
                + ", \"membershipFound\": 12, \"recruited\": " + recruited + (version == null ? "" : ", \"version\": "
                + version) + "}";
    }

    @Test
    @DisplayName("🔒 5 Oct: saved, saved again, a stale save refused, submitted -- one visit on record, its complement written through -- and read-only after")
    void savedAndSubmitted() throws Exception {
        MockHttpServletResponse created = asOrganiser(post("/api/workplaces/visit-drafts")
                .contentType(MediaType.APPLICATION_JSON).content(saved(complement + 2, 1, null)));
        assertThat(created.getStatus()).isEqualTo(201);
        long draft = json(created).get("id").asLong();
        assertThat(json(created).get("version").asInt()).isZero();
        assertThat(jdbc.queryForObject("SELECT owner FROM workplace_visit_drafts WHERE id = ?", String.class, draft))
                .isEqualTo("invented.organiser");

        MockHttpServletResponse again = asOrganiser(put("/api/workplaces/visit-drafts/{id}", draft)
                .contentType(MediaType.APPLICATION_JSON).content(saved(complement + 2, 3, 0)));
        assertThat(again.getStatus()).isEqualTo(200);
        assertThat(json(again).get("version").asInt()).isEqualTo(1);

        MockHttpServletResponse stale = asOrganiser(put("/api/workplaces/visit-drafts/{id}", draft)
                .contentType(MediaType.APPLICATION_JSON).content(saved(complement + 2, 9, 0)));
        assertThat(stale.getStatus()).isEqualTo(409);
        assertThat(json(stale).get("code").asText()).isEqualTo("VISIT_DRAFT_STALE");
        assertThat(jdbc.queryForObject("SELECT recruited FROM workplace_visit_drafts WHERE id = ?", Integer.class, draft))
                .as("the newer save stands; the stale one wrote nothing").isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM workplace_visits", Long.class))
                .as("a draft is not yet a visit").isZero();

        MockHttpServletResponse submitted = asOrganiser(post("/api/workplaces/visit-drafts/{id}/submit", draft)
                .contentType(MediaType.APPLICATION_JSON).content("{\"version\": 1}"));
        assertThat(submitted.getStatus()).isEqualTo(200);
        JsonNode answer = json(submitted);
        assertThat(answer.get("status").asText()).isEqualTo("SUBMITTED");
        assertThat(answer.get("complementBefore").asInt()).isEqualTo(complement);
        long visit = answer.get("visitId").asLong();
        assertThat(jdbc.queryForMap("SELECT workplace_id, complement_found, complement_before, recruited, seeded, "
                + "day_known FROM workplace_visits WHERE id = ?", visit))
                .containsEntry("complement_found", complement + 2).containsEntry("complement_before", complement)
                .containsEntry("recruited", 3).containsEntry("seeded", false).containsEntry("day_known", true);
        assertThat(jdbc.queryForObject("SELECT staff_complement FROM workplaces WHERE id = ?", Integer.class, workplace))
                .as("the complement the visit found, written onto the workplace").isEqualTo(complement + 2);

        MockHttpServletResponse twice = asOrganiser(post("/api/workplaces/visit-drafts/{id}/submit", draft)
                .contentType(MediaType.APPLICATION_JSON).content("{\"version\": 1}"));
        assertThat(twice.getStatus()).as("the same submission again answers the first").isEqualTo(200);
        assertThat(json(twice).get("visitId").asLong()).isEqualTo(visit);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM workplace_visits", Long.class)).isEqualTo(1L);

        MockHttpServletResponse after = asOrganiser(put("/api/workplaces/visit-drafts/{id}", draft)
                .contentType(MediaType.APPLICATION_JSON).content(saved(complement, 0, 2)));
        assertThat(after.getStatus()).isEqualTo(409);
        assertThat(json(after).get("code").asText()).isEqualTo("VISIT_ALREADY_SUBMITTED");
    }

    @Test
    @DisplayName("🔒 the optimistic lock is in the statement: of two saves made from one version, one lands -- whatever checked first -- and never another person's")
    void theLockIsInTheStatement() {
        WorkplaceVisitDraftRepository.Fields fields = new WorkplaceVisitDraftRepository.Fields(
                java.time.LocalDate.of(2026, 9, 10), null, null, "INVENTED ORGANISER", complement, null, null, null, null);
        java.time.Instant at = java.time.Instant.parse("2026-09-14T08:00:00Z");
        long id = drafts.insert(workplace, fields, "invented.organiser", "system", at);

        assertThat(drafts.update(id, "invented.organiser", 0, workplace, fields, at)).isTrue();
        assertThat(drafts.update(id, "invented.organiser", 0, workplace, fields, at))
                .as("the second save made from version 0").isFalse();
        assertThat(drafts.update(id, "invented.other", 1, workplace, fields, at)).as("another person's save").isFalse();
        assertThat(drafts.mine("invented.organiser", "DRAFT", 0, 20).rows())
                .extracting(za.co.titandynamix.sasbo_online_backend.dto.workplace.VisitDraftDto::version)
                .containsExactly(1);
    }

    @Test
    @DisplayName("🔒 another person's draft is not there -- not read, not listed -- and a workplace outside the caller's area is refused")
    void anotherPersonsDraft() throws Exception {
        long draft = json(asOrganiser(post("/api/workplaces/visit-drafts").contentType(MediaType.APPLICATION_JSON)
                .content(saved(complement, 0, null)))).get("id").asLong();

        MockHttpServletResponse read = mockMvc.perform(as("invented.other", CallerScope.all(),
                get("/api/workplaces/visit-drafts/{id}", draft))).andReturn().getResponse();
        assertThat(read.getStatus()).isEqualTo(404);
        assertThat(json(read).get("code").asText()).isEqualTo("VISIT_DRAFT_NOT_FOUND");
        JsonNode theirs = json(mockMvc.perform(as("invented.other", CallerScope.all(),
                get("/api/workplaces/visit-drafts"))).andReturn().getResponse());
        assertThat(theirs.get("totalElements").asLong()).isZero();
        assertThat(json(asOrganiser(get("/api/workplaces/visit-drafts"))).get("totalElements").asLong()).isEqualTo(1L);

        MockHttpServletResponse elsewhere = mockMvc.perform(as("invented.mso", CallerScope.areas(Set.of("W")),
                post("/api/workplaces/visit-drafts").contentType(MediaType.APPLICATION_JSON)
                        .content(saved(complement, 0, null)))).andReturn().getResponse();
        assertThat(elsewhere.getStatus()).isEqualTo(403);
        assertThat(json(elsewhere).get("code").asText()).isEqualTo("WORKPLACE_OUT_OF_SCOPE");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM workplace_visit_drafts", Long.class)).isEqualTo(1L);
    }

    /** A draft written straight to the table: of a status, naming a visit or none, with one column given a value. */
    private void insertDraft(String status, Long visit, String column, Object value) {
        boolean submitted = "SUBMITTED".equals(status);
        jdbc.update("INSERT INTO workplace_visit_drafts (workplace_id, status, visited_on, owner, created_by, created_at, "
                + "updated_at, submitted_at, submitted_by, visit_id, version, " + column + ") VALUES (?, ?, "
                + "DATE '2026-09-10', 'invented.organiser', 'system', now(), now(), "
                + (submitted ? "now(), 'system'" : "NULL, NULL") + ", ?, 0, ?)", workplace, status, visit, value);
    }

    private void insertDraft(String status, Long visit) {
        insertDraft(status, visit, "organiser", "INVENTED ORGANISER");
    }

    @Test
    @DisplayName("🔒 V69_1's constraints, by name: a known status, a submission whole, counts in range, the time out after the time in")
    void theConstraintsByName() {
        assertThatThrownBy(() -> insertDraft("LOST", null))
                .hasMessageContaining("workplace_visit_drafts_status_known");
        assertThatThrownBy(() -> insertDraft("SUBMITTED", null))
                .hasMessageContaining("workplace_visit_drafts_submitted_whole");
        assertThatThrownBy(() -> insertDraft("DRAFT", null, "recruited", -1))
                .hasMessageContaining("workplace_visit_drafts_counts_in_range");
        assertThatThrownBy(() -> insertDraft("DRAFT", null, "complement_found", 100_000))
                .hasMessageContaining("workplace_visit_drafts_counts_in_range");
        assertThatThrownBy(() -> jdbc.update("INSERT INTO workplace_visit_drafts (workplace_id, status, time_in, "
                        + "time_out, owner, created_by, created_at, updated_at, version) VALUES (?, 'DRAFT', TIME '11:00', "
                        + "TIME '10:00', 'invented.organiser', 'system', now(), now(), 0)", workplace))
                .hasMessageContaining("workplace_visit_drafts_out_after_in");
        assertThatThrownBy(() -> jdbc.update("INSERT INTO workplace_visit_drafts (workplace_id, status, owner, created_by, "
                        + "created_at, updated_at, version) VALUES (?, 'DRAFT', 'invented.organiser', 'system', now(), "
                        + "now(), -1)", workplace))
                .hasMessageContaining("workplace_visit_drafts_version_not_negative");
        // The must-pass twin: a draft with nothing but its workplace and its day is a draft.
        insertDraft("DRAFT", null);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM workplace_visit_drafts", Long.class)).isEqualTo(1L);
    }

    @Test
    @DisplayName("🔒 V69_1's trigger, by name: a submitted visit is read-only -- a draft is not -- and the lane's reset still empties the table")
    void submittedIsReadOnly() {
        Long visit = jdbc.queryForObject("INSERT INTO workplace_visits (workplace_id, visited_on, day_known, seeded, "
                + "captured_at) VALUES (?, DATE '2026-09-10', TRUE, FALSE, now()) RETURNING id", Long.class, workplace);
        insertDraft("SUBMITTED", visit);
        insertDraft("DRAFT", null);

        assertThatThrownBy(() -> jdbc.update("UPDATE workplace_visit_drafts SET organiser = 'CHANGED' WHERE status = "
                + "'SUBMITTED'")).hasMessageContaining("a submitted visit is read-only");
        assertThatThrownBy(() -> jdbc.update("DELETE FROM workplace_visit_drafts WHERE status = 'SUBMITTED'"))
                .hasMessageContaining("a submitted visit is read-only");
        assertThat(jdbc.update("UPDATE workplace_visit_drafts SET organiser = 'CHANGED' WHERE status = 'DRAFT'"))
                .as("a draft is changed until it is submitted").isEqualTo(1);

        DatabaseReset.reset(jdbc);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM workplace_visit_drafts", Long.class)).isZero();
    }

    @Test
    @DisplayName("🔒 the seed: six invented visits of the demonstration organiser in area L -- three submitted, each a visit on record with NO complement, captured at 10:00 on its own day -- and a second start writes none")
    void theSeed() {
        int written = seed.seed();

        assertThat(written).isEqualTo(6);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM workplace_visit_drafts WHERE owner = ? AND status = "
                + "'DRAFT'", Long.class, VisitDraftSeeder.SEED_OWNER)).isEqualTo(3L);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM workplace_visit_drafts d JOIN workplace_visits v "
                        + "ON v.id = d.visit_id JOIN workplaces w ON w.id = d.workplace_id JOIN sasbo_areas a "
                        + "ON a.id = w.area_id WHERE d.status = 'SUBMITTED' AND a.letter = 'L' AND v.seeded = FALSE "
                        + "AND v.complement_found IS NULL AND d.complement_found IS NULL AND v.complement_before IS NULL",
                Long.class)).as("review: an invented visit takes no staff complement -- no real branch's is frozen")
                .isEqualTo(3L);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM workplace_visits WHERE visited_on >= DATE '2026-08-01' "
                + "AND visited_on < DATE '2026-09-01' AND captured_at = (CAST(visited_on AS TIMESTAMP) + INTERVAL '10 hours') "
                + "AT TIME ZONE 'Africa/Johannesburg'", Long.class))
                .as("review: each submitted visit is in the month before the clock's, captured at 10:00 SAST on its own day")
                .isEqualTo(3L);

        assertThat(seed.seed()).as("a second start writes nothing").isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM workplace_visit_drafts", Long.class)).isEqualTo(6L);
    }

    @Test
    @DisplayName("🔒 review: a seeded draft the organiser submitted does not come back as a new draft at the next start")
    void aSubmittedSeededDraftStaysSubmitted() {
        seed.seed();
        Long draft = jdbc.queryForObject("SELECT min(id) FROM workplace_visit_drafts WHERE status = 'DRAFT' "
                + "AND visited_on IS NOT NULL", Long.class);
        Long visit = jdbc.queryForObject("INSERT INTO workplace_visits (workplace_id, visited_on, day_known, seeded, "
                + "captured_at) SELECT workplace_id, visited_on, TRUE, FALSE, now() FROM workplace_visit_drafts WHERE id = ? "
                + "RETURNING id", Long.class, draft);
        jdbc.update("UPDATE workplace_visit_drafts SET status = 'SUBMITTED', visit_id = ?, submitted_at = now(), "
                + "submitted_by = 'system', version = version + 1 WHERE id = ?", visit, draft);

        assertThat(seed.seed()).as("the workplace already has the seed's draft, submitted").isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM workplace_visit_drafts", Long.class)).isEqualTo(6L);
    }

    @Test
    @DisplayName("🔒 review: V69_1's workplace_visit_drafts_visit_once, by name -- a visit is submitted from one draft, never two")
    void aVisitIsSubmittedFromOneDraft() {
        Long visit = jdbc.queryForObject("INSERT INTO workplace_visits (workplace_id, visited_on, day_known, seeded, "
                + "captured_at) VALUES (?, DATE '2026-09-10', TRUE, FALSE, now()) RETURNING id", Long.class, workplace);
        insertDraft("SUBMITTED", visit);

        assertThatThrownBy(() -> insertDraft("SUBMITTED", visit))
                .hasMessageContaining("workplace_visit_drafts_visit_once");
    }

    @Test
    @DisplayName("🔒 review: Discard draft deletes my draft at its version -- and a submitted visit is never discarded")
    void discarding() throws Exception {
        long draft = json(asOrganiser(post("/api/workplaces/visit-drafts").contentType(MediaType.APPLICATION_JSON)
                .content(saved(complement, 0, null)))).get("id").asLong();

        MockHttpServletResponse stale = asOrganiser(delete("/api/workplaces/visit-drafts/{id}", draft).param("version", "3"));
        assertThat(stale.getStatus()).isEqualTo(409);
        assertThat(asOrganiser(delete("/api/workplaces/visit-drafts/{id}", draft).param("version", "0")).getStatus())
                .isEqualTo(204);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM workplace_visit_drafts", Long.class)).isZero();

        long submitted = json(asOrganiser(post("/api/workplaces/visit-drafts").contentType(MediaType.APPLICATION_JSON)
                .content(saved(complement, 0, null)))).get("id").asLong();
        asOrganiser(post("/api/workplaces/visit-drafts/{id}/submit", submitted).contentType(MediaType.APPLICATION_JSON)
                .content("{\"version\": 0}"));
        MockHttpServletResponse refused = asOrganiser(delete("/api/workplaces/visit-drafts/{id}", submitted)
                .param("version", "1"));
        assertThat(refused.getStatus()).isEqualTo(409);
        assertThat(json(refused).get("code").asText()).isEqualTo("VISIT_ALREADY_SUBMITTED");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM workplace_visit_drafts", Long.class)).isEqualTo(1L);
    }
}
