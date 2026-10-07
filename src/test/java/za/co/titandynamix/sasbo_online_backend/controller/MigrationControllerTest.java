package za.co.titandynamix.sasbo_online_backend.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import za.co.titandynamix.sasbo_online_backend.config.JacksonConfig;
import za.co.titandynamix.sasbo_online_backend.config.SecurityConfig;
import za.co.titandynamix.sasbo_online_backend.dto.migration.MigrationBatchDto;
import za.co.titandynamix.sasbo_online_backend.dto.migration.MigrationBatchPageDto;
import za.co.titandynamix.sasbo_online_backend.dto.migration.MigrationMappingDto;
import za.co.titandynamix.sasbo_online_backend.dto.migration.MigrationMappingPageDto;
import za.co.titandynamix.sasbo_online_backend.dto.migration.MigrationMonthTotalsDto;
import za.co.titandynamix.sasbo_online_backend.dto.migration.MigrationReconciliationDto;
import za.co.titandynamix.sasbo_online_backend.dto.migration.MigrationReportDto;
import za.co.titandynamix.sasbo_online_backend.dto.migration.MigrationRowPageDto;
import za.co.titandynamix.sasbo_online_backend.exception.GlobalExceptionHandler;
import za.co.titandynamix.sasbo_online_backend.exception.MigrationExceptionHandler;
import za.co.titandynamix.sasbo_online_backend.exception.MigrationRefusal;
import za.co.titandynamix.sasbo_online_backend.exception.MigrationRefusalCode;
import za.co.titandynamix.sasbo_online_backend.services.MigrationBatches;
import za.co.titandynamix.sasbo_online_backend.services.MigrationCommits;
import za.co.titandynamix.sasbo_online_backend.services.MigrationMappings;
import za.co.titandynamix.sasbo_online_backend.services.MigrationReconciliations;
import za.co.titandynamix.sasbo_online_backend.services.MigrationValidation;

import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The data migration's routes, under the application's real security configuration with the filter chain on: every
 * route answers a holder of {@code MIGRATION_RUN}, refuses anyone else 403 -- and an anonymous caller -- before a byte of
 * a request is handed to a service; a request that cannot be read is {@code REQUEST_MALFORMED}, a field out of its rule
 * {@code REQUEST_INVALID}; a service's refusal answers its code and status.
 */
@WebMvcTest(MigrationController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class, MigrationExceptionHandler.class, JacksonConfig.class})
@DisplayName("MigrationController, with the application's real security configuration")
class MigrationControllerTest {

    private static final String CSV = "MEMBERSHIP NO,SURNAME\n700002,Example\n";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MigrationBatches batches;

    @MockitoBean
    private MigrationValidation validation;

    @MockitoBean
    private MigrationCommits commits;

    @MockitoBean
    private MigrationMappings mappings;

    @MockitoBean
    private MigrationReconciliations reconciliations;

    private static MigrationBatchDto batch(String status) {
        return new MigrationBatchDto(7, "export.csv", "a".repeat(64), 1234, "CSV", "legacy-member-export-csv",
                "2026-08-31", status, 53, 20, 10, 23, 0, "system", "2026-09-15T10:00+02:00", null, null, null, null,
                null, null, null, null, false);
    }

    /** Every route, as a request a holder of the permission could send. */
    static Stream<MockHttpServletRequestBuilder> everyRoute() {
        return Stream.of(
                get("/api/migration/mappings"),
                get("/api/migration/mappings/legacy-member-export-csv"),
                post("/api/migration/mappings").contentType(MediaType.APPLICATION_JSON).content("{}"),
                put("/api/migration/mappings/mine").param("version", "1").contentType(MediaType.APPLICATION_JSON).content("{}"),
                post("/api/migration/batches").param("fileName", "export.csv").param("mapping", "legacy-member-export-csv")
                        .param("asAt", "2026-08-31").contentType("text/csv").content(CSV),
                get("/api/migration/batches"),
                get("/api/migration/batches/7"),
                post("/api/migration/batches/7/validate"),
                get("/api/migration/batches/7/report"),
                get("/api/migration/batches/7/rows"),
                post("/api/migration/batches/7/commit"),
                post("/api/migration/batches/7/rollback").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\": \"rehearsal\"}"),
                get("/api/migration/batches/7/reconciliation"),
                get("/api/migration/months/2026-08/totals"),
                put("/api/migration/months/2026-08/totals").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"figures\": []}"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("everyRoute")
    @WithMockUser(authorities = "MEMBERS_VIEW")
    @DisplayName("🔒 every route refuses a user without MIGRATION_RUN, 403 -- and no service is asked anything")
    void refusedWithoutThePermission(MockHttpServletRequestBuilder request) throws Exception {
        mockMvc.perform(request).andExpect(status().isForbidden());
        verifyNoInteractions(batches, validation, commits, mappings, reconciliations);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("everyRoute")
    @WithAnonymousUser
    @DisplayName("🔒 every route refuses an anonymous caller -- never a 2xx")
    void refusedToNobody(MockHttpServletRequestBuilder request) throws Exception {
        mockMvc.perform(request).andExpect(status().is4xxClientError());
        verifyNoInteractions(batches, validation, commits, mappings, reconciliations);
    }

    // ------------------------------------------------------------------------------------------------ happy paths

    @Test
    @WithMockUser(authorities = "MIGRATION_RUN")
    @DisplayName("a file is registered from the request's raw body: 201 for a new batch, 200 for one registered before")
    void register() throws Exception {
        when(batches.register(eq("export.csv"), eq("legacy-member-export-csv"), eq("2026-08-31"), isNull(),
                any(InputStream.class))).thenReturn(new MigrationBatches.Registered(batch("DRAFT"), true, 1));
        mockMvc.perform(post("/api/migration/batches").param("fileName", "export.csv")
                        .param("mapping", "legacy-member-export-csv").param("asAt", "2026-08-31")
                        .contentType(MediaType.APPLICATION_OCTET_STREAM).content(CSV))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.rowsRead").value(53));

        when(batches.register(anyString(), anyString(), anyString(), anyString(), any(InputStream.class)))
                .thenReturn(new MigrationBatches.Registered(batch("VALIDATED"), false, 0));
        mockMvc.perform(post("/api/migration/batches").param("fileName", "export.csv")
                        .param("mapping", "legacy-member-export-csv").param("asAt", "2026-08-31")
                        .param("sha256", "a".repeat(64)).contentType("text/csv").content(CSV))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = "MIGRATION_RUN")
    @DisplayName("a check, a move and an undo are jobs: 202, the batch to poll")
    void jobs() throws Exception {
        when(validation.validate(7, null)).thenReturn(batch("DRAFT"));
        mockMvc.perform(post("/api/migration/batches/7/validate")).andExpect(status().isAccepted());
        when(validation.validate(7, "my-mapping")).thenReturn(batch("DRAFT"));
        mockMvc.perform(post("/api/migration/batches/7/validate").contentType(MediaType.APPLICATION_JSON)
                .content("{\"mappingKey\": \"my-mapping\"}")).andExpect(status().isAccepted());

        when(commits.commit(7, true)).thenReturn(batch("COMMITTING"));
        mockMvc.perform(post("/api/migration/batches/7/commit").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"holdBackErrors\": true}"))
                .andExpect(status().isAccepted()).andExpect(jsonPath("$.status").value("COMMITTING"));
        when(commits.commit(7, false)).thenReturn(batch("COMMITTING"));
        mockMvc.perform(post("/api/migration/batches/7/commit")).andExpect(status().isAccepted());
        verify(commits).commit(7, false);

        when(commits.rollback(7, "rehearsal")).thenReturn(batch("ROLLING_BACK"));
        mockMvc.perform(post("/api/migration/batches/7/rollback").contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\": \"rehearsal\"}")).andExpect(status().isAccepted());
    }

    @Test
    @WithMockUser(authorities = "MIGRATION_RUN")
    @DisplayName("the reads: batches, a batch, its report, its rows, its reconciliation, the mappings, a month's totals")
    void reads() throws Exception {
        when(batches.page(0, 20)).thenReturn(new MigrationBatchPageDto(List.of(batch("DRAFT")), 0, 20, 1));
        mockMvc.perform(get("/api/migration/batches")).andExpect(status().isOk()).andExpect(jsonPath("$.total").value(1));
        when(batches.find(7)).thenReturn(batch("VALIDATED"));
        mockMvc.perform(get("/api/migration/batches/7")).andExpect(status().isOk()).andExpect(jsonPath("$.id").value(7));
        when(batches.report(7)).thenReturn(new MigrationReportDto(7, "VALIDATED", 53, 20, 10, 23, 0, List.of(
                new MigrationReportDto.CodeCount("ID_NUMBER_CHECK_DIGIT", "identityNumber", "ERROR", 1, List.of(6),
                        "The identity number's check digit is wrong.")), List.of()));
        mockMvc.perform(get("/api/migration/batches/7/report")).andExpect(status().isOk())
                .andExpect(jsonPath("$.errors[0].firstRows[0]").value(6));
        when(batches.rows(7, "ERROR", "ID_NUMBER_CHECK_DIGIT", 0, 50)).thenReturn(new MigrationRowPageDto(List.of(
                new MigrationRowPageDto.Row(6, "ERROR", List.of(), Map.of("identityNumber", "****7087"), Map.of())), 0,
                50, 1));
        mockMvc.perform(get("/api/migration/batches/7/rows").param("outcome", "ERROR")
                        .param("code", "ID_NUMBER_CHECK_DIGIT"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].values.identityNumber").value("****7087"));
        when(reconciliations.reconcile(7)).thenReturn(new MigrationReconciliationDto(7, "2026-08", "VALIDATED", 1, 23,
                true, Map.of("MATCH", 1L), List.of(new MigrationReconciliationDto.Line("TOTAL", "", "MEMBERS", 20L, 20,
                23, 0L, "MATCH"))));
        mockMvc.perform(get("/api/migration/batches/7/reconciliation")).andExpect(status().isOk())
                .andExpect(jsonPath("$.proved").value(true));
        when(mappings.page(0, 50)).thenReturn(new MigrationMappingPageDto(List.of(), 0, 50, 0));
        mockMvc.perform(get("/api/migration/mappings")).andExpect(status().isOk());
        when(mappings.detail("legacy-member-export-csv")).thenReturn(new MigrationMappingDto(
                "legacy-member-export-csv", "CSV", true, null, null));
        mockMvc.perform(get("/api/migration/mappings/legacy-member-export-csv")).andExpect(status().isOk())
                .andExpect(jsonPath("$.builtIn").value(true));
        when(reconciliations.totals("2026-08")).thenReturn(new MigrationMonthTotalsDto("2026-08", List.of(), null, null));
        mockMvc.perform(get("/api/migration/months/2026-08/totals")).andExpect(status().isOk())
                .andExpect(jsonPath("$.month").value("2026-08"));
    }

    @Test
    @WithMockUser(authorities = "MIGRATION_RUN")
    @DisplayName("a mapping is defined (201) and changed (200) from the request's JSON; a month's totals entered whole")
    void writes() throws Exception {
        when(mappings.define(any(InputStream.class))).thenReturn(new MigrationMappingDto("mine", "Mine", false, 1, null));
        mockMvc.perform(post("/api/migration/mappings").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.version").value(1));
        when(mappings.update(eq("mine"), eq(1), any(InputStream.class)))
                .thenReturn(new MigrationMappingDto("mine", "Mine", false, 2, null));
        mockMvc.perform(put("/api/migration/mappings/mine").param("version", "1").contentType(MediaType.APPLICATION_JSON)
                .content("{}")).andExpect(status().isOk()).andExpect(jsonPath("$.version").value(2));
        when(reconciliations.replace(eq("2026-08"), any())).thenReturn(new MigrationMonthTotalsDto("2026-08", List.of(
                new MigrationMonthTotalsDto.Figure("TOTAL", "", "MEMBERS", 65_569L, "grlist")), "system", null));
        mockMvc.perform(put("/api/migration/months/2026-08/totals").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"figures\": [{\"dimension\": \"TOTAL\", \"key\": \"\", \"measure\": \"MEMBERS\","
                                + " \"value\": 65569, \"source\": \"grlist\"}]}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.figures[0].value").value(65569));
    }

    // ------------------------------------------------------------------------------------------------ refusals

    @Test
    @WithMockUser(authorities = "MIGRATION_RUN")
    @DisplayName("REQUEST_MALFORMED: a missing parameter, a word for a number, a body that is not JSON")
    void malformed() throws Exception {
        mockMvc.perform(post("/api/migration/batches").param("mapping", "m").param("asAt", "2026-08-31")
                        .contentType("text/csv").content(CSV))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("REQUEST_MALFORMED"))
                .andExpect(jsonPath("$.details.field").value("fileName"));
        mockMvc.perform(get("/api/migration/batches/seven"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("REQUEST_MALFORMED"));
        mockMvc.perform(post("/api/migration/batches").param("fileName", "f").param("mapping", "m")
                        .contentType(MediaType.APPLICATION_OCTET_STREAM).content(new byte[] {1}))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.details.field").value("asAt"));
        mockMvc.perform(post("/api/migration/batches/7/commit").contentType(MediaType.APPLICATION_JSON).content("{not json"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("REQUEST_MALFORMED"));
        verifyNoInteractions(batches, commits);
    }

    @Test
    @WithMockUser(authorities = "MIGRATION_RUN")
    @DisplayName("REQUEST_INVALID: an undo with no reason, a reason too long, a figure below nought -- the fields named")
    void invalid() throws Exception {
        mockMvc.perform(post("/api/migration/batches/7/rollback").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\": \"  \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("REQUEST_INVALID"))
                .andExpect(jsonPath("$.details.fields").value("reason"));
        mockMvc.perform(post("/api/migration/batches/7/rollback").contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\": \"" + "x".repeat(301) + "\"}")).andExpect(jsonPath("$.code").value("REQUEST_INVALID"));
        mockMvc.perform(put("/api/migration/months/2026-08/totals").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"figures\": [{\"dimension\": \"TOTAL\", \"measure\": \"MEMBERS\", \"value\": -1}]}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("REQUEST_INVALID"));
        mockMvc.perform(post("/api/migration/batches/7/validate").contentType(MediaType.APPLICATION_JSON)
                .content("{\"mappingKey\": \"Not A Key\"}")).andExpect(jsonPath("$.code").value("REQUEST_INVALID"));
        verifyNoInteractions(commits, reconciliations, validation);
    }

    @Test
    @WithMockUser(authorities = "MIGRATION_RUN")
    @DisplayName("a service's refusal answers its own status and code, its sentence and its details")
    void refusals() throws Exception {
        when(batches.find(99)).thenThrow(new MigrationRefusal(MigrationRefusalCode.BATCH_NOT_FOUND,
                "There is no batch with that number.", MigrationRefusal.details("batchId", "99")));
        mockMvc.perform(get("/api/migration/batches/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("BATCH_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("There is no batch with that number."))
                .andExpect(jsonPath("$.details.batchId").value("99"));
        when(commits.commit(7, false)).thenThrow(new MigrationRefusal(MigrationRefusalCode.COMMIT_HAS_ERRORS,
                "23 row(s) have errors.", MigrationRefusal.details("rowsInvalid", "23")));
        mockMvc.perform(post("/api/migration/batches/7/commit"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("COMMIT_HAS_ERRORS"));
        when(batches.register(anyString(), anyString(), anyString(), isNull(), any(InputStream.class)))
                .thenThrow(new MigrationRefusal(MigrationRefusalCode.UPLOAD_TOO_LARGE, "The file is too large.",
                        MigrationRefusal.details("limitBytes", "100")));
        mockMvc.perform(post("/api/migration/batches").param("fileName", "f.csv").param("mapping", "m")
                        .param("asAt", "2026-08-31").contentType("text/csv").content(CSV))
                .andExpect(status().isPayloadTooLarge()).andExpect(jsonPath("$.code").value("UPLOAD_TOO_LARGE"));
    }

    @Test
    @WithMockUser(authorities = "MIGRATION_RUN")
    @DisplayName("anything else is the 500 with a reference -- and never the exception's message")
    void unexpected() throws Exception {
        when(batches.page(anyInt(), anyInt())).thenThrow(new IllegalStateException("8001015009087 should never be seen"));
        mockMvc.perform(get("/api/migration/batches"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.startsWith("Something went wrong on our side")))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("8001015009087"))));
        verify(batches).page(anyInt(), anyInt());
        verifyNoInteractions(commits);
    }
}
