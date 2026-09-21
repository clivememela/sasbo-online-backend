package za.co.titandynamix.sasbo_online_backend.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import za.co.titandynamix.sasbo_online_backend.config.JacksonConfig;
import za.co.titandynamix.sasbo_online_backend.exception.AcbExceptionHandler;
import za.co.titandynamix.sasbo_online_backend.exception.GlobalExceptionHandler;
import za.co.titandynamix.sasbo_online_backend.services.DeletionCodeService;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

/**
 * 🧪 R1-21's twin, EH2-A: the ACB advice answers ONLY the ACB controller. A non-ACB route sent the
 * same malformed JSON answers as it did before ACB's handler existed -- with no refusal code -- so
 * fixing A63 on ACB's routes changed nothing anywhere else. (A63 itself, on every other route, is
 * the automation arc's.)
 */
@WebMvcTest(DeletionCodeController.class)
@Import({GlobalExceptionHandler.class, AcbExceptionHandler.class, JacksonConfig.class})
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("AcbExceptionHandler is scoped to the ACB controller")
class AcbExceptionScopeTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DeletionCodeService service;

    @Test
    @DisplayName("malformed JSON on a non-ACB route carries no REQUEST_MALFORMED -- the ACB advice did not answer it")
    void aNonAcbRouteAnswersAsItDid() throws Exception {
        mockMvc.perform(post("/api/deletion-codes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"unterminated"))
                .andExpect(jsonPath("$.code").doesNotExist());
    }
}
