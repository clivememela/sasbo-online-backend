package za.co.titandynamix.sasbo_online_backend.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Reads {@code Authorization: Bearer <token>}, asks the session store who it names, and puts that user into the
 * security context for this request (signin.md).
 *
 * <ul>
 *   <li>No header, or another scheme: nothing happens -- the request goes on anonymous, which in open mode is every
 *       request that works today;</li>
 *   <li>a token that opens no session -- unknown, revoked, idle, expired, or not even shaped like a token: the request
 *       goes on anonymous too, MARKED ({@link #SESSION_ENDED}), so a route that needs a session answers
 *       {@code SESSION_EXPIRED} rather than {@code SIGN_IN_REQUIRED}, and the browser knows to drop what it holds;</li>
 *   <li>a valid token: a {@link BearerAuthentication} for this request only -- the chain is stateless, nothing is kept
 *       in an HTTP session.</li>
 * </ul>
 *
 * <p>🔒 Never a bean: a {@code Filter} bean is picked up by every controller slice and registered with the servlet
 * container a second time. {@code SecurityConfig} makes the one instance and places it before the anonymous filter.
 * The token is never logged, here or downstream.
 */
public final class BearerTokenFilter extends OncePerRequestFilter {

    /** The request attribute that marks a token which opened no session. */
    public static final String SESSION_ENDED = BearerTokenFilter.class.getName() + ".SESSION_ENDED";

    private static final String SCHEME = "Bearer ";

    private final Supplier<SessionAuthenticator> sessions;

    /**
     * @param sessions the session store, asked for on each request -- absent in a controller slice, where every token
     *                 opens nothing and the slice's own test user stands
     */
    public BearerTokenFilter(Supplier<SessionAuthenticator> sessions) {
        this.sessions = sessions;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.regionMatches(true, 0, SCHEME, 0, SCHEME.length())) {
            String token = header.substring(SCHEME.length()).trim();
            SessionAuthenticator store = sessions.get();
            Optional<SignedInUser> user = store == null || !SessionTokens.wellFormed(token)
                    ? Optional.empty() : store.authenticate(token);
            if (user.isPresent()) {
                SecurityContext context = SecurityContextHolder.createEmptyContext();
                context.setAuthentication(new BearerAuthentication(user.get()));
                SecurityContextHolder.setContext(context);
            } else {
                request.setAttribute(SESSION_ENDED, Boolean.TRUE);
            }
        }
        chain.doFilter(request, response);
    }
}
