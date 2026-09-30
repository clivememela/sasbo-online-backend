package za.co.titandynamix.sasbo_online_backend.controller;

import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import za.co.titandynamix.sasbo_online_backend.entity.member.Member;
import za.co.titandynamix.sasbo_online_backend.fixtures.MemberFixture;
import za.co.titandynamix.sasbo_online_backend.repository.MemberRepository;
import za.co.titandynamix.sasbo_online_backend.services.MemberService;
import za.co.titandynamix.sasbo_online_backend.support.AbstractSwitchedOnIT;
import za.co.titandynamix.sasbo_online_backend.support.DatabaseReset;
import za.co.titandynamix.sasbo_online_backend.support.QueryCount;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * A member's workplace on PostgreSQL, with the switch at {@code all} (month end 01.3): saved with one, with none, and
 * with one not on record; answered as three scalars in the list and with its lineage for one member -- read through
 * the controller, where the session has closed by the time the answer is written (MR-ROB-36, MV-64); the list's cost
 * held at 5 members and at 50, each at a workplace of its own (MR-PERF-9); V57's key and index by name (MR-ROB-21).
 * Every workplace here is invented (MD57).
 */
@DisplayName("A member's workplace, on PostgreSQL")
class MemberWorkplaceIT extends AbstractSwitchedOnIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MemberService members;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    /** Invented workplaces in one invented area and committee -- never SASBO's (MD57). Their ids, by R/A number. */
    private List<Long> invented(int count) {
        jdbc.update("INSERT INTO sasbo_regions (code, name) VALUES ('NORTH', 'NORTH')");
        jdbc.update("INSERT INTO sasbo_areas (letter, region_id) SELECT 'A', id FROM sasbo_regions WHERE code = 'NORTH'");
        for (int i = 1; i <= count; i++) {
            jdbc.update("INSERT INTO workplaces (ra_number, name, employer_printed, area_id, committee_code, "
                    + "staff_complement, cl_code) SELECT ?, ?, 'INVENTED BANK', id, 'Q99', 10, '00000000' "
                    + "FROM sasbo_areas WHERE letter = 'A'", 900000 + i, "INVENTED BRANCH " + i);
        }
        return jdbc.queryForList("SELECT id FROM workplaces ORDER BY ra_number", Long.class);
    }

    private Long workplaceOf(Integer memberId) {
        return jdbc.queryForObject("SELECT workplace_id FROM members WHERE id = ?", Long.class, memberId);
    }

    /** A save of the member through {@code PUT /api/members/{id}}, as the record's form sends one. */
    private ResultActions save(Integer memberId, String body) throws Exception {
        return mockMvc.perform(put("/api/members/{id}", memberId).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    @Test
    @DisplayName("saved with a workplace: the list answers three scalars and no lineage; the member's own answer, its lineage")
    void savedWithAWorkplace() throws Exception {
        Long workplace = invented(2).get(0);
        Member member = MemberFixture.aMember();
        member.setWorkplaceId(workplace);
        Integer id = members.create(member).getId();

        assertThat(workplaceOf(id)).isEqualTo(workplace);
        mockMvc.perform(get("/api/members"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].workplaceId").value(workplace))
                .andExpect(jsonPath("$[0].workplaceName").value("INVENTED BRANCH 1"))
                .andExpect(jsonPath("$[0].workplaceRaNumber").value(900001))
                .andExpect(jsonPath("$[0].workplace").doesNotExist());
        mockMvc.perform(get("/api/members/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.workplaceId").value(workplace))
                .andExpect(jsonPath("$.workplace.raNumber").value(900001))
                .andExpect(jsonPath("$.workplace.name").value("INVENTED BRANCH 1"))
                .andExpect(jsonPath("$.workplace.area").value("A"))
                .andExpect(jsonPath("$.workplace.region").value("NORTH"))
                .andExpect(jsonPath("$.workplace.committee").value("Q99"))
                .andExpect(jsonPath("$.workplace.staffComplement").value(10));
    }

    @Test
    @DisplayName("ME-12: a workplace not on record is WORKPLACE_UNKNOWN, answered with its code -- and nothing is written")
    void aWorkplaceNotOnRecord() throws Exception {
        long absent = invented(1).get(0) + 1000;
        Integer id = memberRepository.save(MemberFixture.aMember()).getId();
        Map<String, Object> before = jdbc.queryForMap("SELECT * FROM members WHERE id = ?", id);

        save(id, "{\"workplaceId\": " + absent + ", \"surname\": \"Changed\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("WORKPLACE_UNKNOWN"))
                .andExpect(jsonPath("$.details.workplaceId").value(String.valueOf(absent)));

        assertThat(jdbc.queryForMap("SELECT * FROM members WHERE id = ?", id))
                .as("a refused save writes nothing -- not the surname it also carried")
                .isEqualTo(before);
    }

    @Test
    @DisplayName("BR21: bankName, branch and ibtNo stay as they were -- through a save with a workplace, with none, and with one not on record")
    void theThreeTextFieldsStay() throws Exception {
        List<Long> places = invented(1);
        Member member = MemberFixture.aMember();
        member.setBankName("INVENTED BANK OF RECORD");
        member.setBranch("INVENTED BRANCH OF RECORD");
        member.setIbtNo("000001");
        Integer id = memberRepository.save(member).getId();

        save(id, "{\"workplaceId\": " + places.get(0) + "}").andExpect(status().isOk());
        save(id, "{\"surname\": \"Named None\"}").andExpect(status().isOk());
        save(id, "{\"workplaceId\": " + (places.get(0) + 1000) + "}").andExpect(status().isBadRequest());

        assertThat(workplaceOf(id)).isEqualTo(places.get(0));
        assertThat(jdbc.queryForMap("SELECT bank_name, branch, ibt_no FROM members WHERE id = ?", id))
                .containsEntry("bank_name", "INVENTED BANK OF RECORD")
                .containsEntry("branch", "INVENTED BRANCH OF RECORD")
                .containsEntry("ibt_no", "000001");
    }

    @Test
    @DisplayName("BR66, MD58: a workplace OBJECT in a save changes nothing; a save naming none leaves it; one naming another moves it")
    void namedByItsIdAndNothingElse() throws Exception {
        List<Long> places = invented(2);
        Integer id = memberRepository.save(MemberFixture.aMember()).getId();
        jdbc.update("UPDATE members SET workplace_id = ? WHERE id = ?", places.get(0), id);

        save(id, "{\"workplace\": {\"id\": " + places.get(1) + "}}").andExpect(status().isOk());
        assertThat(workplaceOf(id)).as("a workplace object is never bound").isEqualTo(places.get(0));

        save(id, "{\"surname\": \"Named None\"}").andExpect(status().isOk());
        assertThat(workplaceOf(id)).as("a save that names none leaves it as it was").isEqualTo(places.get(0));

        save(id, "{\"workplaceId\": " + places.get(1) + "}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.workplaceRaNumber").value(900002))
                .andExpect(jsonPath("$.workplace.raNumber").value(900002));
        assertThat(workplaceOf(id)).as("a save that names one moves the member to it").isEqualTo(places.get(1));
    }

    @Test
    @DisplayName("BR66: a capture that carries a workplace OBJECT is saved with no workplace -- it is READ_ONLY over the wire")
    void aCaptureCannotBindAWorkplace() throws Exception {
        Long workplace = invented(1).get(0);

        mockMvc.perform(post("/api/members").contentType(MediaType.APPLICATION_JSON).content("""
                        {"firstNames": "Invented", "surname": "Capture", "identityNumber": "%s",
                         "paymentType": "Payroll", "status": "Active", "isRepresentative": false,
                         "workplace": {"id": %d}}
                        """.formatted(MemberFixture.syntheticIdFor(901), workplace)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.workplaceId").doesNotExist());

        assertThat(jdbc.queryForList("SELECT workplace_id FROM members", Long.class)).containsExactly((Long) null);
    }

    @Test
    @DisplayName("MR-PERF-9: the list costs the same number of statements at 5 members and at 50, each at a workplace of its own")
    void theListWithAWorkplaceOnEveryOne() {
        QueryCount.assertConstantAcrossSizes(
                entityManagerFactory,
                this::seedTo,
                () -> memberRepository.findAll().forEach(m -> {
                    m.getSurname();
                    if (m.getWorkplace() != null) {
                        m.getWorkplace().getName();
                        m.getWorkplace().getRaNumber();
                    }
                }),
                5, 50);
    }

    /** Exactly {@code rows} members, each at a workplace of its own -- so a workplace read later is one read a member. */
    private void seedTo(int rows) {
        DatabaseReset.reset(jdbc);
        List<Long> places = invented(rows);
        for (int i = 0; i < rows; i++) {
            Integer id = memberRepository.save(MemberFixture.withSurname("Member" + i)).getId();
            jdbc.update("UPDATE members SET workplace_id = ? WHERE id = ?", places.get(i), id);
        }
    }

    @Test
    @DisplayName("🔒 MR-ROB-21: V57's key and index, by name, in the catalogue")
    void theKeyAndTheIndexByName() {
        assertThat(jdbc.queryForList("SELECT conname FROM pg_constraint WHERE conrelid = 'members'::regclass",
                String.class)).contains("members_workplace_fk");
        assertThat(jdbc.queryForList("SELECT indexname FROM pg_indexes WHERE tablename = 'members'", String.class))
                .contains("members_workplace");
    }
}
