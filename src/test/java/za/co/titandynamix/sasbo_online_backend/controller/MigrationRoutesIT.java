package za.co.titandynamix.sasbo_online_backend.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import za.co.titandynamix.sasbo_online_backend.legacy.LegacyFixtures;
import za.co.titandynamix.sasbo_online_backend.repository.MemberRepository;
import za.co.titandynamix.sasbo_online_backend.support.AbstractSwitchedOnIT;
import za.co.titandynamix.sasbo_online_backend.support.MigrationFixture;
import za.co.titandynamix.sasbo_online_backend.utils.ReferenceListSeeder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * A rehearsal through the routes, end to end on PostgreSQL with the application's whole configuration: a file sent as
 * a raw body, checked, reported, moved, reconciled and undone by a holder of {@code MIGRATION_RUN} -- and the same file
 * refused 403 to anyone else, nothing kept.
 */
@DisplayName("A rehearsal through the migration's routes, on PostgreSQL")
class MigrationRoutesIT extends AbstractSwitchedOnIT {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ReferenceListSeeder lists;

    @Autowired
    private MemberRepository members;

    @BeforeEach
    void theLists() {
        MigrationFixture.prepare(jdbc, lists, members);
    }

    @Test
    @WithMockUser(authorities = "MIGRATION_RUN")
    @DisplayName("register (201), check (202), report, move holding errors back (202), reconcile, undo (202)")
    void rehearsal() throws Exception {
        String registered = mockMvc.perform(post("/api/migration/batches")
                        .param("fileName", "member-export-faults.csv")
                        .param("mapping", LegacyFixtures.CSV_MAPPING)
                        .param("asAt", "2026-08-31")
                        .contentType(MediaType.APPLICATION_OCTET_STREAM)
                        .content(LegacyFixtures.resource(LegacyFixtures.CSV_FIXTURE)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.rowsRead").value(MigrationFixture.CSV_ROWS))
                .andReturn().getResponse().getContentAsString();
        long id = JSON.readTree(registered).get("id").asLong();

        mockMvc.perform(post("/api/migration/batches/{id}/validate", id))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("VALIDATED"))
                .andExpect(jsonPath("$.rowsInvalid").value(MigrationFixture.CSV_INVALID));
        mockMvc.perform(get("/api/migration/batches/{id}/report", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.errors[0].code").exists());
        mockMvc.perform(post("/api/migration/batches/{id}/commit", id))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("COMMIT_HAS_ERRORS"));
        mockMvc.perform(post("/api/migration/batches/{id}/commit", id).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"holdBackErrors\": true}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("COMMITTED"))
                .andExpect(jsonPath("$.rowsCommitted").value(MigrationFixture.CSV_VALID + MigrationFixture.CSV_WARNED));
        mockMvc.perform(put("/api/migration/months/2026-08/totals").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"figures\": [{\"dimension\": \"TOTAL\", \"key\": \"\", \"measure\": \"MEMBERS\","
                                + " \"value\": 18, \"source\": \"grlist\"}]}"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/migration/batches/{id}/reconciliation", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.proved").value(true));
        mockMvc.perform(post("/api/migration/batches/{id}/rollback", id).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\": \"A rehearsal through the routes.\"}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("ROLLED_BACK"));

        JsonNode page = JSON.readTree(mockMvc.perform(get("/api/migration/batches")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        assertThat(page.get("total").asLong()).isEqualTo(1);
        assertThat(members.count()).as("the member on record alone").isEqualTo(1);
    }

    @Test
    @WithMockUser(authorities = "MEMBERS_VIEW")
    @DisplayName("🔒 anyone without MIGRATION_RUN is refused 403 -- and the file sent is never read or kept")
    void refused() throws Exception {
        mockMvc.perform(post("/api/migration/batches")
                        .param("fileName", "member-export-faults.csv")
                        .param("mapping", LegacyFixtures.CSV_MAPPING)
                        .param("asAt", "2026-08-31")
                        .contentType(MediaType.APPLICATION_OCTET_STREAM)
                        .content(LegacyFixtures.resource(LegacyFixtures.CSV_FIXTURE)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/migration/batches")).andExpect(status().isForbidden());

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM migration_batches", Long.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM migration_rows", Long.class)).isZero();
    }
}
