package za.co.titandynamix.sasbo_online_backend.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.parallel.ResourceAccessMode;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import za.co.titandynamix.sasbo_online_backend.config.JacksonConfig;
import za.co.titandynamix.sasbo_online_backend.config.SecurityConfig;
import za.co.titandynamix.sasbo_online_backend.controller.AuditController;
import za.co.titandynamix.sasbo_online_backend.controller.AuthController;
import za.co.titandynamix.sasbo_online_backend.controller.UserAdminController;
import za.co.titandynamix.sasbo_online_backend.exception.GlobalExceptionHandler;
import za.co.titandynamix.sasbo_online_backend.exception.SignInExceptionHandler;
import za.co.titandynamix.sasbo_online_backend.security.SessionAuthenticator;
import za.co.titandynamix.sasbo_online_backend.security.SignInThrottle;
import za.co.titandynamix.sasbo_online_backend.security.SignedInUser;
import za.co.titandynamix.sasbo_online_backend.services.AuditTrail;
import za.co.titandynamix.sasbo_online_backend.services.SignInService;
import za.co.titandynamix.sasbo_online_backend.services.UserAdministration;

import java.util.Optional;

import static org.mockito.Mockito.when;

/**
 * The sign-in slice: sign-in's three controllers and {@link SignInSliceRoutes}, under the application's REAL security
 * configuration with the filter chain on -- so a 401 or a 403 here is the chain's, never a slice's default
 * ({@code SecuritySliceTest}). The services are mocks; the session store is a mock too, so a request carries a real
 * bearer token through the real filter ({@link #as}).
 *
 * <p>One base for every class of the slice, so they share one cached context; a class that sets the mode adds one more.
 *
 * <h2>🔴 One class at a time -- the mocks are shared</h2>
 * Sharing the context means sharing its mock beans, and the suite runs test CLASSES concurrently: one class's stubbing
 * of {@code sessions.authenticate}, or the reset that follows each of its tests, landed in another class's request
 * mid-test -- a 401 where the token had been stubbed, a 200 where a refusal had (run 37610088942; the run before it
 * passed). Every class of this base takes {@link #MOCKS} for the whole class, so they run one after another and each
 * test's stubs are its own. ({@code @ResourceLock} is inherited.)
 */
@WebMvcTest(controllers = {AuthController.class, UserAdminController.class, AuditController.class,
        SignInSliceRoutes.class})
@ResourceLock(value = AbstractSignInSliceTest.MOCKS, mode = ResourceAccessMode.READ_WRITE)
@Import({SecurityConfig.class, GlobalExceptionHandler.class, SignInExceptionHandler.class, JacksonConfig.class,
        SignInSliceRoutes.class})
public abstract class AbstractSignInSliceTest {

    /** The lock every class of the sign-in slice holds: their shared context's mock beans. */
    public static final String MOCKS = "the sign-in slice's shared mock beans";

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    @MockitoBean
    protected SignInService signIn;

    @MockitoBean
    protected UserAdministration admin;

    @MockitoBean
    protected AuditTrail audit;

    @MockitoBean
    protected SessionAuthenticator sessions;

    /** The login throttle: admits every request unless a test makes it refuse (SignInThrottleTest holds its counts). */
    @MockitoBean
    protected SignInThrottle throttle;

    /** The request carries {@code user}'s own bearer token, which the (mock) store opens to them. */
    protected RequestPostProcessor as(SignedInUser user) {
        String token = SignInTestUsers.tokenFor(user.username());
        when(sessions.authenticate(token)).thenReturn(Optional.of(user));
        return SignInTestUsers.bearer(token);
    }

    /** The request carries a token of the right shape that opens no session: idle, expired, revoked or unknown. */
    protected RequestPostProcessor ended() {
        String token = SignInTestUsers.tokenFor("a session that has ended");
        when(sessions.authenticate(token)).thenReturn(Optional.empty());
        return SignInTestUsers.bearer(token);
    }
}
