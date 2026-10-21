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
import za.co.titandynamix.sasbo_online_backend.acb.returns.LetterDocument;
import za.co.titandynamix.sasbo_online_backend.config.JacksonConfig;
import za.co.titandynamix.sasbo_online_backend.config.SecurityConfig;
import za.co.titandynamix.sasbo_online_backend.enums.LetterTemplate;
import za.co.titandynamix.sasbo_online_backend.exception.GlobalExceptionHandler;
import za.co.titandynamix.sasbo_online_backend.services.AcbCalendarService;
import za.co.titandynamix.sasbo_online_backend.services.AcbCodeService;
import za.co.titandynamix.sasbo_online_backend.services.AcbDebitDayService;
import za.co.titandynamix.sasbo_online_backend.services.AcbOpeningPositionService;
import za.co.titandynamix.sasbo_online_backend.services.AcbRateService;
import za.co.titandynamix.sasbo_online_backend.services.AcbReturnsService;
import za.co.titandynamix.sasbo_online_backend.services.AcbRunService;
import za.co.titandynamix.sasbo_online_backend.services.AcbUnpaidCountService;

import java.nio.charset.StandardCharsets;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The letters' preview, {@code GET /api/acb/letters/specimen/{template}}, behind {@code DOD_PREPARE} -- under the
 * application's real security configuration, so a refusal can only be the permission's: the holder is answered
 * through the same chain.
 */
@WebMvcTest(ACBController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class, JacksonConfig.class})
@DisplayName("The debit order letters' preview, behind DOD_PREPARE")
class AcbLetterPreviewSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AcbRunService runs;

    @MockitoBean
    private AcbReturnsService returns;

    @MockitoBean
    private AcbCalendarService calendar;

    @MockitoBean
    private AcbOpeningPositionService openingPosition;

    @MockitoBean
    private AcbRateService rates;

    @MockitoBean
    private AcbCodeService codes;

    @MockitoBean
    private AcbUnpaidCountService unpaidCounts;

    @MockitoBean
    private AcbDebitDayService debitDays;

    @MockitoBean
    private LetterDocument letters;

    @Test
    @WithMockUser(authorities = "DOD_PREPARE")
    @DisplayName("a holder of DOD_PREPARE is answered with the PDF, inline, named for its letter")
    void theHolderIsAnswered() throws Exception {
        when(letters.specimen(LetterTemplate.PAYMENT_STOPPED))
                .thenReturn("%PDF-1.7 specimen".getBytes(StandardCharsets.US_ASCII));

        mockMvc.perform(get("/api/acb/letters/specimen/PAYMENT_STOPPED"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andExpect(header().string("Content-Disposition", "inline; filename=\"specimen-payment_stopped.pdf\""));
    }

    @Test
    @WithMockUser(authorities = "MEMBERS_VIEW")
    @DisplayName("a user without DOD_PREPARE is refused 403, and no letter is made")
    void anotherPermissionIsRefused() throws Exception {
        mockMvc.perform(get("/api/acb/letters/specimen/RE_DEBIT")).andExpect(status().isForbidden());
        verifyNoInteractions(letters);
    }

    @Test
    @WithAnonymousUser
    @DisplayName("nobody signed in is refused -- never a 2xx, never a 500 -- and no letter is made")
    void nobodySignedInIsRefused() throws Exception {
        mockMvc.perform(get("/api/acb/letters/specimen/RE_DEBIT")).andExpect(status().is4xxClientError());
        verifyNoInteractions(letters);
    }
}
