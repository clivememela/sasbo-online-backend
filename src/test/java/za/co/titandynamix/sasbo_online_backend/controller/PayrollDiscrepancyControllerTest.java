package za.co.titandynamix.sasbo_online_backend.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import za.co.titandynamix.sasbo_online_backend.config.JacksonConfig;
import za.co.titandynamix.sasbo_online_backend.config.SecurityConfig;
import za.co.titandynamix.sasbo_online_backend.dto.payroll.PayrollPage;
import za.co.titandynamix.sasbo_online_backend.dto.payroll.PayrollQueryFileDto;
import za.co.titandynamix.sasbo_online_backend.exception.GlobalExceptionHandler;
import za.co.titandynamix.sasbo_online_backend.exception.PayrollExceptionHandler;
import za.co.titandynamix.sasbo_online_backend.exception.PayrollRefusal;
import za.co.titandynamix.sasbo_online_backend.exception.PayrollRefusalCode;
import za.co.titandynamix.sasbo_online_backend.fixtures.PayrollFixture;
import za.co.titandynamix.sasbo_online_backend.services.PayrollDiscrepancies;
import za.co.titandynamix.sasbo_online_backend.services.PayrollQueries;
import za.co.titandynamix.sasbo_online_backend.services.PayrollReports;

import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Payroll's discrepancies and queries, through the application's REAL security configuration: the worklist across
 * files, a discrepancy with its notes, notes added and resolutions recorded on the version read (PAY-20), the queries
 * raised with the employer and produced as a protected file under a password the system generates and shows once
 * (PAY-21) -- and the refusals of each.
 */
@WebMvcTest(PayrollDiscrepancyController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class, PayrollExceptionHandler.class, JacksonConfig.class})
@DisplayName("PayrollDiscrepancyController, with the application's real security configuration")
class PayrollDiscrepancyControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PayrollDiscrepancies discrepancies;

    @MockitoBean
    private PayrollQueries queries;

    @Test
    @WithMockUser(authorities = "PAYROLL_VIEW")
    @DisplayName("the worklist across files: the kind, Stephne's rule, what to do, who and the amounts")
    void theWorklist() throws Exception {
        when(discrepancies.list(isNull(), isNull(), isNull(), eq("AMOUNT_DIFFERS"), isNull(), isNull(), isNull()))
                .thenReturn(PayrollPage.of(List.of(PayrollFixture.discrepancy()), 0, 20, 1));
        mockMvc.perform(get("/api/payroll/discrepancies").param("type", "AMOUNT_DIFFERS"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].type").value("AMOUNT_DIFFERS"))
                .andExpect(jsonPath("$.content[0].rule").value("PAY-16"))
                .andExpect(jsonPath("$.content[0].row.idNumber").value("****9086"))
                .andExpect(jsonPath("$.content[0].difference").value("-7.35"))
                .andExpect(jsonPath("$.content[0].queryAction").value("CORRECT"));
    }

    @Test
    @WithMockUser(authorities = "PAYROLL_VIEW")
    @DisplayName("a filter word the list does not take is FILTER_UNKNOWN, with the ones it does")
    void anUnknownFilter() throws Exception {
        when(discrepancies.list(isNull(), isNull(), isNull(), eq("BOGUS"), isNull(), isNull(), isNull()))
                .thenThrow(new PayrollRefusal(PayrollRefusalCode.FILTER_UNKNOWN, "Not a word of this list.",
                        Map.of("field", "type", "accepted", "NOT_A_MEMBER, ...")));
        mockMvc.perform(get("/api/payroll/discrepancies").param("type", "BOGUS"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("FILTER_UNKNOWN"));
    }

    @Test
    @WithMockUser(authorities = "PAYROLL_PROCESS")
    @DisplayName("a note added: 201 with the discrepancy; a blank note is NOTE_INVALID, and its words are not echoed")
    void notes() throws Exception {
        when(discrepancies.addNote(eq(77L), eq("Queried with Absa by e-mail."), anyString()))
                .thenReturn(PayrollFixture.discrepancy());
        mockMvc.perform(post("/api/payroll/discrepancies/77/notes").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\": \"Queried with Absa by e-mail.\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(77));
        mockMvc.perform(post("/api/payroll/discrepancies/77/notes").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\": \"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("NOTE_INVALID"));
    }

    @Test
    @WithMockUser(authorities = "PAYROLL_PROCESS")
    @DisplayName("resolved on the version read; a stale version is DISCREPANCY_CHANGED_SINCE; an outcome that does not fit is refused")
    void resolving() throws Exception {
        String body = "{\"outcome\": \"ARREARS_RECORDED\", \"note\": \"R7.35 recorded as arrears.\", \"version\": 0}";
        when(discrepancies.resolve(eq(77L), eq("ARREARS_RECORDED"), anyString(), eq(0), anyString()))
                .thenReturn(PayrollFixture.discrepancy());
        mockMvc.perform(post("/api/payroll/discrepancies/77/resolve").contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());

        when(discrepancies.resolve(eq(78L), anyString(), anyString(), eq(0), anyString()))
                .thenThrow(new PayrollRefusal(PayrollRefusalCode.DISCREPANCY_CHANGED_SINCE, "Changed.",
                        Map.of("version", "2")));
        mockMvc.perform(post("/api/payroll/discrepancies/78/resolve").contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.details.version").value("2"));

        mockMvc.perform(post("/api/payroll/discrepancies/77/resolve").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"outcome\": \"ARREARS_RECORDED\", \"version\": 0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("NOTE_INVALID"));
    }

    @Test
    @WithMockUser(authorities = "PAYROLL_PROCESS")
    @DisplayName("a query raised: 201 with its lines; nothing to query is NOTHING_TO_QUERY")
    void raisingAQuery() throws Exception {
        when(queries.raise(eq(12L), anyString())).thenReturn(PayrollFixture.query());
        mockMvc.perform(post("/api/payroll/files/12/queries"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.counts.add").value(1))
                .andExpect(jsonPath("$.lines[0].action").value("ADD"));
        when(queries.raise(eq(13L), anyString())).thenThrow(new PayrollRefusal(PayrollRefusalCode.NOTHING_TO_QUERY,
                "Nothing to query."));
        mockMvc.perform(post("/api/payroll/files/13/queries"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("NOTHING_TO_QUERY"));
    }

    @Test
    @WithMockUser(authorities = "PAYROLL_PROCESS")
    @DisplayName("🔒 the employer's list is produced under a password the system generates: in the 201 answer only, never cached")
    void producingAQueryList() throws Exception {
        PayrollQueryFileDto produced = new PayrollQueryFileDto(5, 2, "XLSX", "EXCEL_AES_256",
                "Payroll queries - Absa - August 2026.xlsx", 5120, "a".repeat(64), "2026-09-28T08:15:00Z", "system", 0,
                null, false);
        when(queries.produce(eq(2L), eq("xlsx"), anyString()))
                .thenReturn(new PayrollQueries.Produced(produced, "K7MW-Q3ZR-T8XH-N4CP"));
        mockMvc.perform(post("/api/payroll/queries/2/files").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"format\": \"xlsx\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.file.id").value(5))
                .andExpect(jsonPath("$.file.queryId").value(2))
                .andExpect(jsonPath("$.file.protection").value("EXCEL_AES_256"))
                .andExpect(jsonPath("$.file.bytes").value(5120))
                .andExpect(jsonPath("$.password").value("K7MW-Q3ZR-T8XH-N4CP"))
                .andExpect(jsonPath("$.passwordNote").value(containsString("shown once")));

        when(queries.fileContent(eq(5L), anyString())).thenReturn(new PayrollReports.Report(
                "Payroll queries - Absa - August 2026.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", new byte[] {1, 2, 3}));
        mockMvc.perform(get("/api/payroll/query-files/5"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", containsString("Payroll queries")))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(content().bytes(new byte[] {1, 2, 3}));

        when(queries.fileContent(eq(6L), anyString())).thenThrow(new PayrollRefusal(
                PayrollRefusalCode.QUERY_FILE_NOT_FOUND, "No such query list file."));
        mockMvc.perform(get("/api/payroll/query-files/6"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("QUERY_FILE_NOT_FOUND"));
    }

    @Test
    @WithMockUser(authorities = "PAYROLL_VIEW")
    @DisplayName("🔒 PAYROLL_VIEW alone neither produces a list that leaves SASBO nor downloads one")
    void viewingDoesNotSendAnythingOut() throws Exception {
        mockMvc.perform(post("/api/payroll/queries/2/files").contentType(MediaType.APPLICATION_JSON)
                .content("{\"format\": \"xlsx\"}")).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/payroll/query-files/5")).andExpect(status().isForbidden());
        verify(queries, never()).produce(anyLong(), any(), any());
        verify(queries, never()).fileContent(anyLong(), any());
    }

    @Test
    @WithMockUser(authorities = "VISITS_CAPTURE")
    @DisplayName("🔒 PAY-33: an organiser's permissions see no discrepancy, and write nothing")
    void organisersSeeNoDiscrepancy() throws Exception {
        mockMvc.perform(get("/api/payroll/discrepancies")).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/payroll/discrepancies/77")).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/payroll/discrepancies/77/resolve").contentType(MediaType.APPLICATION_JSON)
                .content("{\"outcome\": \"OTHER\", \"note\": \"x\", \"version\": 0}")).andExpect(status().isForbidden());
        verify(discrepancies, never()).get(anyLong());
        verify(discrepancies, never()).resolve(anyLong(), any(), any(), any(), any());
    }

    @Test
    @WithMockUser(authorities = "PAYROLL_VIEW")
    @DisplayName("🔒 PAYROLL_VIEW reads the worklist but raises no query")
    void viewingRaisesNoQuery() throws Exception {
        mockMvc.perform(post("/api/payroll/files/12/queries")).andExpect(status().isForbidden());
        verify(queries, never()).raise(anyLong(), any());
    }

    @Test
    @WithAnonymousUser
    @DisplayName("🔒 nobody signed in is refused -- not a 2xx, never a 500")
    void anonymous() throws Exception {
        mockMvc.perform(get("/api/payroll/discrepancies")).andExpect(status().is4xxClientError());
    }
}
