package za.co.titandynamix.sasbo_online_backend.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import za.co.titandynamix.sasbo_online_backend.fixtures.MemberFixture;
import za.co.titandynamix.sasbo_online_backend.services.MemberService;
import za.co.titandynamix.sasbo_online_backend.support.AbstractSwitchedOnIT;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 🔴 V68 -- TWO PEOPLE EDITING ONE MEMBER, on PostgreSQL through {@code PUT /api/members/{id}}.
 *
 * <p>The later save silently undid the earlier one: a member carried nothing to say which save of the record an edit
 * had read (the manual stream, 7 Oct, {@code Members.jsx:1016-1022}). Now the answer for one member carries its
 * {@code version}, an edit sends back the version it read, and an edit of a record saved since is refused
 * {@code MEMBER_CHANGED_SINCE} -- the first save survives, and the second editor is told. Every member already on
 * record reads as never saved since: V68's default.
 *
 * <p>In the switched-on lane's one context (MockMvc on), as the member's other PostgreSQL round trips are -- a context
 * of its own would hold another pool on the container. Writes rows; the shared database is reset either side.
 */
@DisplayName("Two people editing one member, on PostgreSQL (V68)")
class MemberEditVersionIT extends AbstractSwitchedOnIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MemberService members;

    private ResultActions save(Integer id, String body) throws Exception {
        return mockMvc.perform(put("/api/members/{id}", id).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private String phoneOf(Integer id) {
        return jdbc.queryForObject("SELECT phone FROM members WHERE id = ?", String.class, id);
    }

    @Test
    @DisplayName("🔴 the second of two editors who read the same record is refused MEMBER_CHANGED_SINCE, and the first save survives")
    void theLaterOfTwoEditsIsRefused() throws Exception {
        Integer id = members.create(MemberFixture.aMember()).getId();
        mockMvc.perform(get("/api/members/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(0));

        // Two people opened the record at version 0. The first saves:
        save(id, "{\"version\": 0, \"phone\": \"+27 60 555 0101\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.phone").value("+27 60 555 0101"));

        // ...and the second, still holding version 0, is refused -- nothing of theirs is written.
        save(id, "{\"version\": 0, \"phone\": \"+27 60 555 0202\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("MEMBER_CHANGED_SINCE"))
                .andExpect(jsonPath("$.details.memberId").value(String.valueOf(id)))
                .andExpect(jsonPath("$.details.versionOnRecord").value("1"));

        assertThat(phoneOf(id)).as("the first save survives").isEqualTo("+27 60 555 0101");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM member_changes WHERE member_id = ? AND field = 'phone'",
                Long.class, id)).as("the refused save wrote no history").isEqualTo(1);
    }

    @Test
    @DisplayName("must-pass: an edit naming the version on record is saved, and each save moves the version on")
    void anEditOfTheCurrentVersionIsSaved() throws Exception {
        Integer id = members.create(MemberFixture.aMember()).getId();

        save(id, "{\"version\": 0, \"phone\": \"+27 60 555 0303\"}").andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(1));
        save(id, "{\"version\": 1, \"phone\": \"+27 60 555 0404\"}").andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(2));

        assertThat(phoneOf(id)).isEqualTo("+27 60 555 0404");
        assertThat(jdbc.queryForObject("SELECT version FROM members WHERE id = ?", Long.class, id)).isEqualTo(2);
    }

    @Test
    @DisplayName("a member already on record -- written without a version, as a seed or an import writes one -- reads as never saved since")
    void aMemberOnRecordStartsAtNought() throws Exception {
        jdbc.update("INSERT INTO members (membership_number, surname, first_names, status, payment_type, created_at, "
                + "updated_at) VALUES ('M-960001', 'Invented', 'Already On Record', 'Active', 'Payroll Deduction', ?, ?)",
                LocalDateTime.of(2026, 9, 1, 8, 0), LocalDateTime.of(2026, 9, 1, 8, 0));
        Integer id = jdbc.queryForObject("SELECT id FROM members WHERE membership_number = 'M-960001'", Integer.class);

        mockMvc.perform(get("/api/members/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(0));
        save(id, "{\"version\": 0, \"phone\": \"+27 60 555 0505\"}").andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(1));
    }
}
