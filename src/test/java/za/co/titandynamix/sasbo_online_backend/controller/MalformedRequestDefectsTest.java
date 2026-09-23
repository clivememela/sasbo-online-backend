package za.co.titandynamix.sasbo_online_backend.controller;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import za.co.titandynamix.sasbo_online_backend.config.JacksonConfig;
import za.co.titandynamix.sasbo_online_backend.exception.GlobalExceptionHandler;
import za.co.titandynamix.sasbo_online_backend.mapper.MemberMapperImpl;
import za.co.titandynamix.sasbo_online_backend.repository.RepresentativeRepository;
import za.co.titandynamix.sasbo_online_backend.repository.RepresentativeRoleRepository;
import za.co.titandynamix.sasbo_online_backend.repository.UnionStructureRepository;
import za.co.titandynamix.sasbo_online_backend.services.EmailService;
import za.co.titandynamix.sasbo_online_backend.services.MemberService;
import za.co.titandynamix.sasbo_online_backend.services.RepresentativeService;
import za.co.titandynamix.sasbo_online_backend.support.KnownDefects;

import java.util.stream.Stream;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Register row <b>A63</b>: a request the API cannot read answers 500, so a caller's
 * mistake is indistinguishable from a server fault in any log or alert.
 *
 * <h2>The mechanism</h2>
 * {@code GlobalExceptionHandler} does not extend {@code ResponseEntityExceptionHandler}
 * and maps none of Spring MVC's request-shape exceptions, so its catch-all
 * {@code @ExceptionHandler(Exception.class)} claims them before
 * {@code DefaultHandlerExceptionResolver} can answer 400 or 405. One request per
 * exception type below, each through the real handler.
 *
 * <p>⚠️ Not test-slice shadowing, unlike A18: no key in the production
 * {@code application.yaml} changes which resolver runs first, so the slice and the
 * deployed application agree here. The path-id case was probed against the
 * deployed API on 2026-09-19 and answered 500.
 *
 * <p>🔑 The live pin also asserts WHAT the 500 says -- the catch-all's fixed
 * "quote this reference" sentence, and not the caller's input. That is B4's fix
 * (`4e1bfac`) holding on this path, measured rather than assumed.
 */
@WebMvcTest(MemberController.class)
@Import({GlobalExceptionHandler.class, JacksonConfig.class, MemberMapperImpl.class,
        MalformedRequestDefectsTest.RequiredParameterProbe.class})
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("A request the API cannot read (A63)")
class MalformedRequestDefectsTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MemberService memberService;

    @MockitoBean
    private RepresentativeService representativeService;

    // MemberMapperImpl is imported for real, as in MemberControllerTest, so its
    // repositories must exist for the context to start.
    @MockitoBean
    private RepresentativeRepository representativeRepository;

    @MockitoBean
    private UnionStructureRepository unionStructureRepository;

    @MockitoBean
    private RepresentativeRoleRepository representativeRoleRepository;

    @MockitoBean
    private EmailService emailService;

    /**
     * 🔑 NO ROUTE IN THE APPLICATION TAKES A REQUIRED PARAMETER TODAY -- every
     * {@code @RequestParam} has a default or {@code required = false} -- so the
     * third case is latent rather than live. This probe gives the handler one,
     * because A63 is about the HANDLER's policy, and the first required parameter
     * somebody adds will meet it.
     */
    @RestController
    static class RequiredParameterProbe {

        @GetMapping("/a63-probe/required-parameter")
        String answer(@RequestParam String name) {
            return name;
        }
    }

    /** One malformed request per exception type, and the status it should answer. */
    static Stream<Arguments> malformedRequests() {
        return Stream.of(
                Arguments.of("a non-numeric member id (MethodArgumentTypeMismatchException)",
                        get("/api/members/abc"), 400),
                Arguments.of("an unreadable JSON body (HttpMessageNotReadableException)",
                        post("/api/members").contentType(MediaType.APPLICATION_JSON).content("{\"surname\": "), 400),
                Arguments.of("a missing required parameter (MissingServletRequestParameterException)",
                        get("/a63-probe/required-parameter"), 400),
                Arguments.of("an unsupported method (HttpRequestMethodNotSupportedException)",
                        delete("/api/members/1"), 405));
    }

    @ParameterizedTest(name = "{0} answers {2}")
    @MethodSource("malformedRequests")
    @Disabled(KnownDefects.MALFORMED_REQUESTS_ANSWER_500)
    @DisplayName("🔴 A63: a request the API cannot read is answered as the caller's error")
    void aMalformedRequestIsTheCallersError(String what, MockHttpServletRequestBuilder request, int expected)
            throws Exception {
        this.mockMvc.perform(request)
                .andExpect(status().is(expected))
                .andExpect(jsonPath("$.status").value(expected))
                .andExpect(jsonPath("$.message").value(not(containsString("Exception"))));
    }

    @ParameterizedTest(name = "{0} still answers 500")
    @MethodSource("malformedRequests")
    @DisplayName("🔴 A63 still reproduces: each is claimed by the catch-all and answers 500")
    void aMalformedRequestStillAnswers500(String what, MockHttpServletRequestBuilder request, int expected)
            throws Exception {
        // A63 has changed if any of these stops being a 500. For the ones that now
        // answer `expected`, enable the disabled test above and strike the row.
        this.mockMvc.perform(request)
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message", startsWith("Something went wrong on our side")));
    }
}
