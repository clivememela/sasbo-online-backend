package za.co.titandynamix.sasbo_online_backend.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import za.co.titandynamix.sasbo_online_backend.config.JacksonConfig;
import za.co.titandynamix.sasbo_online_backend.config.SecurityConfig;
import za.co.titandynamix.sasbo_online_backend.dto.payroll.PayrollPage;
import za.co.titandynamix.sasbo_online_backend.exception.GlobalExceptionHandler;
import za.co.titandynamix.sasbo_online_backend.exception.PayrollExceptionHandler;
import za.co.titandynamix.sasbo_online_backend.exception.PayrollRefusal;
import za.co.titandynamix.sasbo_online_backend.exception.PayrollRefusalCode;
import za.co.titandynamix.sasbo_online_backend.fixtures.PayrollFixture;
import za.co.titandynamix.sasbo_online_backend.services.PayrollEmployers;
import za.co.titandynamix.sasbo_online_backend.services.PayrollFiles;
import za.co.titandynamix.sasbo_online_backend.services.PayrollPayments;
import za.co.titandynamix.sasbo_online_backend.services.PayrollReports;

import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Payroll's files, employers and payments, through the application's REAL security configuration with the filter chain
 * on: every route needs a payroll permission (reading VIEW or PROCESS, writing PROCESS); a caller without one is
 * refused, as is one limited to a region; and each route answers its contract's shape and refusals.
 */
@WebMvcTest(PayrollController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class, PayrollExceptionHandler.class, JacksonConfig.class})
@DisplayName("PayrollController, with the application's real security configuration")
class PayrollControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PayrollEmployers employers;

    @MockitoBean
    private PayrollFiles files;

    @MockitoBean
    private PayrollPayments payments;

    @MockitoBean
    private PayrollReports reports;

    private static MockMultipartFile upload() {
        return new MockMultipartFile("file", "Absa payroll August 2026.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", new byte[] {'P', 'K', 3, 4, 1});
    }

    // ------------------------------------------------------------------------------------------------- reading

    @Test
    @WithMockUser(authorities = "PAYROLL_VIEW")
    @DisplayName("the files, a page at a time, for a holder of PAYROLL_VIEW")
    void theFiles() throws Exception {
        when(files.list(isNull(), eq("2026-08"), isNull(), isNull(), isNull()))
                .thenReturn(PayrollPage.of(List.of(PayrollFixture.file()), 0, 20, 1));
        mockMvc.perform(get("/api/payroll/files").param("period", "2026-08"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].period").value("2026-08"))
                .andExpect(jsonPath("$.content[0].paymentStatus").value("EXACT"))
                .andExpect(jsonPath("$.content[0].fileTotal").value("4168.65"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    @WithMockUser(authorities = "PAYROLL_VIEW")
    @DisplayName("one file's summary: flat file fields, the layout's columns, the totals as decimal strings")
    void oneFile() throws Exception {
        when(files.summary(12)).thenReturn(PayrollFixture.summary(false));
        mockMvc.perform(get("/api/payroll/files/12"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(12))
                .andExpect(jsonPath("$.employer.code").value("ABSA"))
                .andExpect(jsonPath("$.alreadyLoaded").value(false))
                .andExpect(jsonPath("$.layout.columns[0]").value("Pers.No."))
                .andExpect(jsonPath("$.totals.adminFee").value("125.06"))
                .andExpect(jsonPath("$.totals.expectedPayment").value("4043.59"))
                .andExpect(jsonPath("$.queries[0].delete").value(2));
    }

    @Test
    @WithMockUser(authorities = "PAYROLL_PROCESS")
    @DisplayName("a file id that is no number is REQUEST_MALFORMED, naming the field")
    void aWordForAnId() throws Exception {
        mockMvc.perform(get("/api/payroll/files/abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("REQUEST_MALFORMED"))
                .andExpect(jsonPath("$.details.field").value("id"));
    }

    @Test
    @WithMockUser(authorities = "PAYROLL_VIEW")
    @DisplayName("a page that does not exist is PAGE_INVALID, with the limits")
    void aPageThatDoesNotExist() throws Exception {
        when(files.rows(12, null, -1, null)).thenThrow(new PayrollRefusal(PayrollRefusalCode.PAGE_INVALID,
                "That page does not exist.", Map.of("page", "-1", "size", "20", "maxSize", "100")));
        mockMvc.perform(get("/api/payroll/files/12/rows").param("page", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PAGE_INVALID"))
                .andExpect(jsonPath("$.details.maxSize").value("100"));
    }

    @Test
    @WithMockUser(authorities = "PAYROLL_VIEW")
    @DisplayName("a report downloads as a file, its name in Content-Disposition")
    void aReport() throws Exception {
        when(reports.full(eq(12L), eq("pdf"), anyString())).thenReturn(new PayrollReports.Report(
                "Payroll reconciliation - Absa - August 2026.pdf", "application/pdf", new byte[] {'%', 'P', 'D', 'F'}));
        mockMvc.perform(get("/api/payroll/files/12/reports/full").param("format", "pdf"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/pdf"))
                .andExpect(header().string("Content-Disposition", containsString("attachment")))
                .andExpect(header().string("Content-Disposition", containsString("Payroll reconciliation")));
    }

    @Test
    @WithMockUser(authorities = "PAYROLL_PROCESS")
    @DisplayName("🔒 the file as it arrived (PAY-32): PAYROLL_PROCESS downloads it, each time recorded; PAYROLL_VIEW does not")
    void theFileAsItArrived() throws Exception {
        when(files.content(eq(12L), anyString())).thenReturn(new PayrollFiles.Content("Absa payroll August 2026.xlsx",
                za.co.titandynamix.sasbo_online_backend.enums.PayrollFormat.XLSX, new byte[] {'P', 'K', 3, 4}));
        mockMvc.perform(get("/api/payroll/files/12/content"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type",
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .andExpect(header().string("Content-Disposition", containsString("Absa payroll August 2026.xlsx")))
                .andExpect(header().string("Cache-Control", "no-store"));
        verify(files).content(eq(12L), anyString());
    }

    @Test
    @WithMockUser(authorities = "PAYROLL_VIEW")
    @DisplayName("🔒 PAYROLL_VIEW reads the summary and the masked lines, never the unmasked file as it arrived")
    void viewingDoesNotDownloadTheFile() throws Exception {
        mockMvc.perform(get("/api/payroll/files/12/content")).andExpect(status().isForbidden());
        verify(files, never()).content(anyLong(), any());
    }

    // ------------------------------------------------------------------------------------------------- writing

    @Test
    @WithMockUser(authorities = "PAYROLL_PROCESS")
    @DisplayName("an employer's file loaded: 201 with its summary; the same bytes again: 200, alreadyLoaded")
    void anUpload() throws Exception {
        when(files.upload(eq(1L), eq("2026-08"), eq("Absa payroll August 2026.xlsx"), any(), eq(false), isNull(),
                anyString())).thenReturn(new PayrollFiles.Loaded(PayrollFixture.summary(false), true));
        mockMvc.perform(multipart("/api/payroll/files").file(upload()).param("employerId", "1").param("period", "2026-08"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(12));

        when(files.upload(eq(1L), eq("2026-08"), anyString(), any(), eq(false), isNull(), anyString()))
                .thenReturn(new PayrollFiles.Loaded(PayrollFixture.summary(true), false));
        mockMvc.perform(multipart("/api/payroll/files").file(upload()).param("employerId", "1").param("period", "2026-08"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.alreadyLoaded").value(true));
    }

    @Test
    @WithMockUser(authorities = "PAYROLL_PROCESS")
    @DisplayName("an upload with no file is FILE_MISSING; a month's other file is FILE_EXISTS_FOR_PERIOD, naming it")
    void uploadRefusals() throws Exception {
        mockMvc.perform(multipart("/api/payroll/files").param("employerId", "1").param("period", "2026-08"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("FILE_MISSING"));

        when(files.upload(eq(1L), eq("2026-08"), anyString(), any(), anyBoolean(), isNull(), anyString()))
                .thenThrow(new PayrollRefusal(PayrollRefusalCode.FILE_EXISTS_FOR_PERIOD, "Already loaded.",
                        Map.of("fileId", "12", "uploadedAt", "2026-09-15T08:00:00Z", "uploadedBy", "system")));
        mockMvc.perform(multipart("/api/payroll/files").file(upload()).param("employerId", "1").param("period", "2026-08"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("FILE_EXISTS_FOR_PERIOD"))
                .andExpect(jsonPath("$.details.fileId").value("12"));
    }

    @Test
    @WithMockUser(authorities = "PAYROLL_PROCESS")
    @DisplayName("typed rows with none in them are TYPED_ROWS_INVALID; a payment with no amount is PAYMENT_INVALID")
    void bodyRefusals() throws Exception {
        mockMvc.perform(post("/api/payroll/files/typed").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"employerId\": 7, \"period\": \"2026-08\", \"rows\": []}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("TYPED_ROWS_INVALID"));
        mockMvc.perform(post("/api/payroll/payments").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"employerId\": 1, \"period\": \"2026-08\", \"receivedOn\": \"2026-08-31\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PAYMENT_INVALID"))
                .andExpect(jsonPath("$.details.field").value("amount"));
        mockMvc.perform(post("/api/payroll/payments").contentType(MediaType.APPLICATION_JSON).content("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("REQUEST_MALFORMED"));
    }

    @Test
    @WithMockUser(authorities = "PAYROLL_PROCESS")
    @DisplayName("a payment recorded: 201; the same payment again: 200, alreadyRecorded")
    void aPayment() throws Exception {
        String body = "{\"employerId\": 1, \"period\": \"2026-08\", \"amount\": \"4043.59\", \"receivedOn\": "
                + "\"2026-08-31\", \"reference\": \"EFT ABSA 2026-08\"}";
        when(payments.record(any(), anyString())).thenReturn(PayrollFixture.payment(false));
        mockMvc.perform(post("/api/payroll/payments").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.amount").value("4043.59"));
        when(payments.record(any(), anyString())).thenReturn(PayrollFixture.payment(true));
        mockMvc.perform(post("/api/payroll/payments").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.alreadyRecorded").value(true));
    }

    @Test
    @WithMockUser(authorities = "PAYROLL_PROCESS")
    @DisplayName("an employer's settings changed on the version read; a fee that is no number is SETTINGS_INVALID")
    void settings() throws Exception {
        mockMvc.perform(put("/api/payroll/employers/1").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"adminFeePercent\": \"\", \"version\": 0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SETTINGS_INVALID"));
        verify(employers, never()).update(anyLong(), any());
    }

    // ----------------------------------------------------------------------------------------------- refusing

    @Test
    @WithMockUser(authorities = "MEMBERS_VIEW")
    @DisplayName("🔒 PAY-33: a caller without a payroll permission is refused 403, and nothing is read")
    void withoutThePermission() throws Exception {
        mockMvc.perform(get("/api/payroll/files")).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/payroll/files/12")).andExpect(status().isForbidden());
        verify(files, never()).summary(anyLong());
    }

    @Test
    @WithMockUser(authorities = "PAYROLL_VIEW")
    @DisplayName("🔒 PAYROLL_VIEW reads but never writes: an upload with it is refused 403")
    void viewingIsNotProcessing() throws Exception {
        mockMvc.perform(multipart("/api/payroll/files").file(upload()).param("employerId", "1").param("period", "2026-08"))
                .andExpect(status().isForbidden());
        verify(files, never()).upload(any(), any(), any(), any(), anyBoolean(), any(), any());
    }

    @Test
    @WithAnonymousUser
    @DisplayName("🔒 nobody signed in is refused -- not a 2xx, and never a 500")
    void anonymous() throws Exception {
        mockMvc.perform(get("/api/payroll/files")).andExpect(status().is4xxClientError());
        mockMvc.perform(get("/api/payroll/vocabulary")).andExpect(status().is4xxClientError());
    }

    @Test
    @DisplayName("🔒 a caller limited to a region, even holding the permission, is HEAD_OFFICE_ONLY")
    void aRegionalCaller() throws Exception {
        TestingAuthenticationToken regional = new TestingAuthenticationToken(
                PayrollFixture.ScopedCaller.regional(), null, "PAYROLL_VIEW");
        mockMvc.perform(get("/api/payroll/files").with(authentication(regional)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("HEAD_OFFICE_ONLY"));
    }

    @Test
    @WithMockUser(authorities = "PAYROLL_VIEW")
    @DisplayName("must-pass: the same chain answers the vocabulary to a holder of PAYROLL_VIEW")
    void theVocabulary() throws Exception {
        when(employers.vocabulary()).thenReturn(new za.co.titandynamix.sasbo_online_backend.dto.payroll
                .PayrollVocabularyDto(List.of(), List.of(), List.of("OPEN"), List.of(), List.of(), List.of(),
                new za.co.titandynamix.sasbo_online_backend.dto.payroll.PayrollVocabularyDto.Limits(10_485_760L,
                        20_000, 5_000, 1_000, 100), List.of("xlsx", "xls", "csv")));
        mockMvc.perform(get("/api/payroll/vocabulary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.limits.maxRows").value(20000));
    }
}
