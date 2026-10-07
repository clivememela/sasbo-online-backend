package za.co.titandynamix.sasbo_online_backend.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import za.co.titandynamix.sasbo_online_backend.config.SignInApi;
import za.co.titandynamix.sasbo_online_backend.dto.signin.LoginRequest;
import za.co.titandynamix.sasbo_online_backend.dto.signin.ModeDto;
import za.co.titandynamix.sasbo_online_backend.dto.signin.PasswordChangeRequest;
import za.co.titandynamix.sasbo_online_backend.dto.signin.SessionDto;
import za.co.titandynamix.sasbo_online_backend.dto.signin.UserAnswerDto;
import za.co.titandynamix.sasbo_online_backend.exception.SignInRefusal;
import za.co.titandynamix.sasbo_online_backend.exception.SignInRefusalCode;
import za.co.titandynamix.sasbo_online_backend.security.SignedInUser;
import za.co.titandynamix.sasbo_online_backend.services.SignInService;

/**
 * Signing in and out, under {@code /api/auth} (signin.md).
 *
 * <ul>
 *   <li>{@code POST /login} and {@code GET /mode} answer anyone, in either mode: the one is how a session begins, the
 *       other is how the browser learns whether it must;</li>
 *   <li>{@code POST /logout}, {@code GET /me} and {@code POST /password} need a session -- nobody signed in is
 *       {@code 401 SIGN_IN_REQUIRED}, a token that opens no session any more {@code 401 SESSION_EXPIRED}. A user with a
 *       temporary password reaches these three and nothing else that needs a permission.</li>
 * </ul>
 */
@SignInApi
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final SignInService signIn;

    public AuthController(SignInService signIn) {
        this.signIn = signIn;
    }

    /** Signs in: {@code {token, expiresAt, user}}. The token is shown in this answer only. */
    @PostMapping("/login")
    public SessionDto login(@Valid @RequestBody LoginRequest request, HttpServletRequest http) {
        return signIn.signIn(request, http.getRemoteAddr(), http.getHeader(HttpHeaders.USER_AGENT));
    }

    /** Ends the session this request came with. */
    @PreAuthorize("isAuthenticated()")
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@AuthenticationPrincipal SignedInUser caller) {
        signIn.signOut(signedIn(caller));
        return ResponseEntity.noContent().build();
    }

    /** The signed-in user. */
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/me")
    public UserAnswerDto me(@AuthenticationPrincipal SignedInUser caller) {
        return signIn.me(signedIn(caller));
    }

    /** Changes the signed-in user's own password -- the one in use first. */
    @PreAuthorize("isAuthenticated()")
    @PostMapping("/password")
    public ResponseEntity<Void> password(@AuthenticationPrincipal SignedInUser caller,
                                         @Valid @RequestBody PasswordChangeRequest request) {
        signIn.changePassword(signedIn(caller), request);
        return ResponseEntity.noContent().build();
    }

    /** Whether sign-in is in force: {@code {mode: "open" | "required"}}. */
    @GetMapping("/mode")
    public ModeDto mode() {
        return signIn.mode();
    }

    /** Only a session's own user reaches past {@code isAuthenticated()}; any other caller is nobody signed in here. */
    private static SignedInUser signedIn(SignedInUser caller) {
        if (caller == null) {
            throw new SignInRefusal(SignInRefusalCode.SIGN_IN_REQUIRED, "Please sign in to use this part of SASBO "
                    + "Online.");
        }
        return caller;
    }
}
