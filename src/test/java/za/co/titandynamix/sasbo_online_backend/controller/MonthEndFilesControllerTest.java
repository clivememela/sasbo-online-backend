package za.co.titandynamix.sasbo_online_backend.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import za.co.titandynamix.sasbo_online_backend.config.JacksonConfig;
import za.co.titandynamix.sasbo_online_backend.config.SecurityConfig;
import za.co.titandynamix.sasbo_online_backend.dto.monthend.MonthEndFileDto;
import za.co.titandynamix.sasbo_online_backend.dto.monthend.MonthEndProducedFileDto;
import za.co.titandynamix.sasbo_online_backend.exception.GlobalExceptionHandler;
import za.co.titandynamix.sasbo_online_backend.exception.MonthEndExceptionHandler;
import za.co.titandynamix.sasbo_online_backend.exception.MonthEndRefusal;
import za.co.titandynamix.sasbo_online_backend.exception.MonthEndRefusalCode;
import za.co.titandynamix.sasbo_online_backend.services.MonthEndRegionPacks;
import za.co.titandynamix.sasbo_online_backend.services.MonthEndSentFiles;

import java.util.Map;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The banks' and providers' files, through their routes (October build), under the real security configuration: a
 * file made answers {@code 201} with its password, never cached; the encrypted file is written to the response; SFTP
 * not set up is SFTP_NOT_CONFIGURED; a region's user is SCOPE_TOO_NARROW; MONTH_END_SEND enforced.
 */
@WebMvcTest(MonthEndFilesController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class, MonthEndExceptionHandler.class, JacksonConfig.class})
@DisplayName("MonthEndFilesController")
class MonthEndFilesControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MonthEndSentFiles files;

    static final MonthEndFileDto ABSA = new MonthEndFileDto(7, "2026-09", 1, "BANK", "ABSA", "Absa",
            "ABSA_FIXED_WIDTH", true, "ABSA_260930.zip", "ZIP", "AES_256_ZIP", 412, 0, 0, 2, 5120, "ef",
            "2026-10-01T05:10:00Z", "system", 0, null);

    @Test
    @WithMockUser(authorities = "MONTH_END_SEND")
    @DisplayName("🔒 a bank's file made is 201, its password in the answer, and the answer is never cached")
    void produce() throws Exception {
        when(files.produceBank("2026-09", "ABSA")).thenReturn(new MonthEndProducedFileDto(ABSA, "K7MW-Q3ZR-T8XH-N4CP",
                "This password is shown once and is not kept."));

        mockMvc.perform(post("/api/month-end/2026-09/files/banks/ABSA"))
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, containsString("no-store")))
                .andExpect(jsonPath("$.file.protection").value("AES_256_ZIP"))
                .andExpect(jsonPath("$.password").value("K7MW-Q3ZR-T8XH-N4CP"))
                .andExpect(jsonPath("$.file.leftOut").value(2));
    }

    @Test
    @WithMockUser(authorities = "MONTH_END_SEND")
    @DisplayName("the encrypted file is written to the response as a ZIP, with its name and hash")
    void download() throws Exception {
        when(files.download(7L)).thenReturn(new MonthEndRegionPacks.Download("ABSA_260930.zip", "application/zip", 2,
                "ef", out -> {
                    try {
                        out.write(new byte[] {'P', 'K'});
                    } catch (java.io.IOException e) {
                        throw new java.io.UncheckedIOException(e);
                    }
                }));

        mockMvc.perform(get("/api/month-end/files/7"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/zip"))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("ABSA_260930.zip")))
                .andExpect(content().bytes(new byte[] {'P', 'K'}));
    }

    @Test
    @WithMockUser(authorities = "MONTH_END_SEND")
    @DisplayName("SFTP not set up is SFTP_NOT_CONFIGURED 409; a region's user is SCOPE_TOO_NARROW 403")
    void refusals() throws Exception {
        when(files.upload(8L)).thenThrow(new MonthEndRefusal(MonthEndRefusalCode.SFTP_NOT_CONFIGURED,
                "No SFTP server is set up on this server.", Map.of("file", "8")));
        mockMvc.perform(post("/api/month-end/files/8/sftp"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SFTP_NOT_CONFIGURED"));

        when(files.list("2026-09")).thenThrow(new MonthEndRefusal(MonthEndRefusalCode.SCOPE_TOO_NARROW,
                "A bank's file reaches members of every region."));
        mockMvc.perform(get("/api/month-end/2026-09/files"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("SCOPE_TOO_NARROW"));
    }

    @Test
    @WithMockUser(authorities = "DOD_FILE")
    @DisplayName("🔒 a user without MONTH_END_SEND -- the debit order file's holder, say -- is refused 403")
    void withoutThePermission() throws Exception {
        mockMvc.perform(post("/api/month-end/2026-09/files/banks/ABSA")).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/month-end/files/7")).andExpect(status().isForbidden());
        verifyNoInteractions(files);
    }

    @Test
    @WithAnonymousUser
    @DisplayName("🔒 nobody signed in is refused -- not answered, and not a 500")
    void anonymous() throws Exception {
        mockMvc.perform(post("/api/month-end/2026-09/files/providers/LAW_CALL")).andExpect(status().is4xxClientError());
        verifyNoInteractions(files);
    }
}
