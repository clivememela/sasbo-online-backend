package za.co.titandynamix.sasbo_online_backend.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.security.authorization.AuthorizationResult;
import org.springframework.security.authorization.ExpressionAuthorizationDecision;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import za.co.titandynamix.sasbo_online_backend.exception.SignInRefusalCode;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * How the security filter chain says no -- the envelope every other refusal of the API answers with (code, message,
 * the request's path), so the browser reads one shape (signin.md):
 *
 * <ul>
 *   <li>401 {@code SIGN_IN_REQUIRED}: nobody is signed in and the route needs somebody -- or
 *       {@code SESSION_EXPIRED}, where the request came with a token that opens no session any more;</li>
 *   <li>403 {@code PERMISSION_REQUIRED}: signed in, without the permission -- named at the top level, as the contract
 *       asks, and in {@code details}, as every other refusal carries its facts;</li>
 *   <li>403 {@code PASSWORD_CHANGE_REQUIRED}: signed in with a temporary password, which opens nothing but changing
 *       it.</li>
 * </ul>
 *
 * <p>Method security reaches here too: the application's catch-all handlers throw a refusal on (oct/base), and Spring
 * Security's {@code ExceptionTranslationFilter} hands it to this class -- the entry point for an anonymous caller, the
 * access-denied handler for everyone else. Nothing a caller sent is written back.
 */
@Slf4j
public final class SignInChallenge implements AuthenticationEntryPoint, AccessDeniedHandler {

    static final String SIGN_IN_REQUIRED_MESSAGE = "Please sign in to use this part of SASBO Online.";
    static final String SESSION_EXPIRED_MESSAGE = "Your session has ended. Please sign in again.";
    static final String PERMISSION_REQUIRED_MESSAGE = "You do not have permission to do this. Ask a user "
            + "administrator if your work needs it.";
    static final String PASSWORD_CHANGE_REQUIRED_MESSAGE = "Please change your temporary password before you go on.";

    /** A permission literal inside {@code hasAuthority('...')} or {@code hasAnyAuthority('...', ...)}. */
    private static final Pattern AUTHORITY_CALL = Pattern.compile("has(?:Any)?Authority\\(([^)]*)\\)");
    private static final Pattern LITERAL = Pattern.compile("'([A-Z][A-Z0-9_]*)'");

    /** Where no mapper is in the context -- a slice without the application's: the same modules, found the same way. */
    private static final ObjectMapper FALLBACK = JsonMapper.builder().findAndAddModules().build();

    private final Supplier<ObjectMapper> json;

    public SignInChallenge(Supplier<ObjectMapper> json) {
        this.json = json;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException reason)
            throws IOException {
        boolean ended = request.getAttribute(BearerTokenFilter.SESSION_ENDED) != null;
        SignInRefusalCode code = ended ? SignInRefusalCode.SESSION_EXPIRED : SignInRefusalCode.SIGN_IN_REQUIRED;
        write(request, response, code, ended ? SESSION_EXPIRED_MESSAGE : SIGN_IN_REQUIRED_MESSAGE, null);
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException denied)
            throws IOException {
        Authentication caller = SecurityContextHolder.getContext().getAuthentication();
        if (caller != null && caller.getPrincipal() instanceof SignedInUser user && user.mustChangePassword()) {
            write(request, response, SignInRefusalCode.PASSWORD_CHANGE_REQUIRED, PASSWORD_CHANGE_REQUIRED_MESSAGE,
                    null);
            return;
        }
        write(request, response, SignInRefusalCode.PERMISSION_REQUIRED, PERMISSION_REQUIRED_MESSAGE,
                permissionOf(denied));
    }

    /**
     * The one permission a refused {@code @PreAuthorize} names -- read off the expression the refusal carries. Null
     * where it names none, or more than one: the refusal then says no permission rather than guess at one.
     */
    static String permissionOf(AccessDeniedException denied) {
        if (denied instanceof AuthorizationDeniedException authorization) {
            AuthorizationResult result = authorization.getAuthorizationResult();
            if (result instanceof ExpressionAuthorizationDecision decision && decision.getExpression() != null) {
                Set<String> named = permissionsIn(decision.getExpression().getExpressionString());
                return named.size() == 1 ? named.iterator().next() : null;
            }
        }
        return null;
    }

    /** Every catalogue permission an expression names in a {@code hasAuthority} or {@code hasAnyAuthority} call. */
    static Set<String> permissionsIn(String expression) {
        Set<String> catalogue = Arrays.stream(Permission.values()).map(Enum::name).collect(Collectors.toSet());
        Set<String> named = new LinkedHashSet<>();
        Matcher call = AUTHORITY_CALL.matcher(expression == null ? "" : expression);
        while (call.find()) {
            Matcher literal = LITERAL.matcher(call.group(1));
            while (literal.find()) {
                if (catalogue.contains(literal.group(1))) {
                    named.add(literal.group(1));
                }
            }
        }
        return named;
    }

    /**
     * The envelope, keyed as {@code ApiErrorResponse} is and written by the application's own mapper -- so the timestamp
     * renders exactly as on every other refusal -- plus the top-level {@code permission} the contract asks of a 403.
     */
    private void write(HttpServletRequest request, HttpServletResponse response, SignInRefusalCode code,
                       String message, String permission) throws IOException {
        log.info("Sign-in refused {} {}: {}", request.getMethod(), request.getRequestURI(), code);
        HttpStatus status = code.status();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", LocalDateTime.now(ZoneOffset.UTC));
        body.put("status", status.value());
        body.put("error", status.getReasonPhrase());
        body.put("message", message);
        body.put("path", request.getRequestURI());
        body.put("code", code.name());
        if (permission != null) {
            body.put("details", Map.of("permission", permission));
            body.put("permission", permission);
        }
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        ObjectMapper mapper = json.get();
        (mapper == null ? FALLBACK : mapper).writeValue(response.getOutputStream(), body);
    }
}
