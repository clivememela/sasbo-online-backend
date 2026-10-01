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
import za.co.titandynamix.sasbo_online_backend.entity.member.Member;
import za.co.titandynamix.sasbo_online_backend.exception.GlobalExceptionHandler;
import za.co.titandynamix.sasbo_online_backend.mapper.MemberMapperImpl;
import za.co.titandynamix.sasbo_online_backend.repository.RepresentativeRepository;
import za.co.titandynamix.sasbo_online_backend.repository.RepresentativeRoleRepository;
import za.co.titandynamix.sasbo_online_backend.repository.UnionStructureRepository;
import za.co.titandynamix.sasbo_online_backend.services.MemberService;
import za.co.titandynamix.sasbo_online_backend.services.RepresentativeService;
import za.co.titandynamix.sasbo_online_backend.services.impl.WelcomeLetterDispatcher;
import za.co.titandynamix.sasbo_online_backend.support.NoTransactions;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The member endpoints when mail is NOT configured.
 *
 * <p>{@code EmailServiceImpl} only exists when {@code spring.mail.username} is
 * set ({@code @ConditionalOnExpression}), and {@code MemberController} takes it
 * as {@code @Autowired(required = false)}. So an environment without
 * {@code MAIL_USERNAME} -- the test profiles, {@code dev.sasbo.online}, and any
 * deploy that forgot the variable -- still starts. Sending a letter on its own
 * answers 503 there; a CAPTURE that asks for one saves the member and says the
 * letter was not emailed (A64). That is a separate application context from
 * {@code MemberControllerTest}, where a mail bean exists, which is why this is
 * its own class rather than a nested one.
 */
@WebMvcTest(MemberController.class)
@Import({GlobalExceptionHandler.class, JacksonConfig.class, MemberMapperImpl.class,
        WelcomeLetterDispatcher.class, NoTransactions.class})
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("The member endpoints without mail configured")
class MemberControllerWithoutMailTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MemberService memberService;

    @MockitoBean
    private RepresentativeService representativeService;

    /** Month end 01.3: does nothing here, so every answer is the mapper's, as it was before the workplace. */
    @MockitoBean
    private za.co.titandynamix.sasbo_online_backend.services.MemberWorkplaces memberWorkplaces;

    @MockitoBean
    private RepresentativeRepository representativeRepository;

    @MockitoBean
    private UnionStructureRepository unionStructureRepository;

    @MockitoBean
    private RepresentativeRoleRepository representativeRoleRepository;

    @Test
    @DisplayName("answers 503 before looking the member up, rather than failing to start")
    void answers503() throws Exception {
        this.mockMvc.perform(post("/api/members/7/send-welcome"))
                .andExpect(status().isServiceUnavailable());

        verifyNoInteractions(this.memberService);
    }

    @Test
    @DisplayName("GET /api/members/{id}/welcome-letter-preview answers 503 before lookup")
    void previewAnswers503() throws Exception {
        this.mockMvc.perform(get("/api/members/7/welcome-letter-preview"))
                .andExpect(status().isServiceUnavailable());

        verifyNoInteractions(this.memberService);
    }

    @Test
    @DisplayName("🔴 A64: POST /api/members?sendWelcome=true SAVES the member, and says the letter was not emailed")
    void createWithWelcomeStillSaves() throws Exception {
        // It answered 503 here, BEFORE saving -- so on dev.sasbo.online, which has no mail,
        // nobody could capture anyone. The member is saved and the answer says why no letter went.
        Member saved = new Member();
        saved.setId(3);
        saved.setSurname("Botha");
        saved.setPersonalEmail("botha@example.invalid");
        when(this.memberService.create(any(Member.class))).thenReturn(saved);

        this.mockMvc.perform(post("/api/members?sendWelcome=true")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"surname\":\"Botha\",\"personalEmail\":\"botha@example.invalid\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(3))
                .andExpect(jsonPath("$.welcomeLetter").value("EMAIL_OFF"));

        verify(this.memberService).create(any(Member.class));
    }

    @Test
    @DisplayName("GET /api/members/metadata/welcome-letter says the letter is not emailed here")
    void metadataSaysEmailIsOff() throws Exception {
        this.mockMvc.perform(get("/api/members/metadata/welcome-letter"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.emailEnabled").value(false));
    }
}
