package za.co.titandynamix.sasbo_online_backend.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import za.co.titandynamix.sasbo_online_backend.config.JacksonConfig;
import za.co.titandynamix.sasbo_online_backend.exception.GlobalExceptionHandler;
import za.co.titandynamix.sasbo_online_backend.mapper.MemberMapperImpl;
import za.co.titandynamix.sasbo_online_backend.repository.RepresentativeRepository;
import za.co.titandynamix.sasbo_online_backend.repository.RepresentativeRoleRepository;
import za.co.titandynamix.sasbo_online_backend.repository.UnionStructureRepository;
import za.co.titandynamix.sasbo_online_backend.services.MemberService;
import za.co.titandynamix.sasbo_online_backend.services.RepresentativeService;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The welcome-letter endpoint when mail is NOT configured.
 *
 * <p>{@code EmailServiceImpl} only exists when {@code spring.mail.username} is
 * set ({@code @ConditionalOnExpression}), and {@code MemberController} takes it
 * as {@code @Autowired(required = false)}. So an environment without
 * {@code MAIL_USERNAME} -- the test profiles, and any deploy that forgot the
 * variable -- still starts, and the endpoint answers 503. That is a separate
 * application context from {@code MemberControllerTest}, where a mail bean
 * exists, which is why this is its own class rather than a nested one.
 */
@WebMvcTest(MemberController.class)
@Import({GlobalExceptionHandler.class, JacksonConfig.class, MemberMapperImpl.class})
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("POST /api/members/{id}/send-welcome without mail configured")
class MemberControllerWithoutMailTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MemberService memberService;

    @MockitoBean
    private RepresentativeService representativeService;

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
}
