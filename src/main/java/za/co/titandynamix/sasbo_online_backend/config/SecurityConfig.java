package za.co.titandynamix.sasbo_online_backend.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import za.co.titandynamix.sasbo_online_backend.security.BearerTokenFilter;
import za.co.titandynamix.sasbo_online_backend.security.SessionAuthenticator;
import za.co.titandynamix.sasbo_online_backend.security.SignInChallenge;
import za.co.titandynamix.sasbo_online_backend.security.SignInMode;
import za.co.titandynamix.sasbo_online_backend.security.SignInProperties;
import za.co.titandynamix.sasbo_online_backend.security.SignedInUser;

import java.util.List;
import java.util.function.Supplier;

/**
 * Who may reach what (October build, contracts/signin.md).
 *
 * <h2>A session is a bearer token</h2>
 * {@link BearerTokenFilter} reads {@code Authorization: Bearer <token>} before the anonymous filter, so a valid token
 * makes the request its user's and anything else leaves it anonymous. Nothing is kept in an HTTP session: the chain is
 * stateless, and the front end on another site never needs a cookie. A refusal answers in JSON, the API's envelope
 * ({@link SignInChallenge}): 401 {@code SIGN_IN_REQUIRED} or {@code SESSION_EXPIRED}, 403 {@code PERMISSION_REQUIRED}
 * naming the permission.
 *
 * <h2>Two modes -- {@code sasbo.signin.mode}, from {@code SASBO_SIGNIN}</h2>
 * <ul>
 *   <li><b>open</b>, the default (the owner, 6 Oct: the link runs open until SASBO's staff have accounts): every route
 *       answers as it did, and a route that names a permission with {@code @PreAuthorize("hasAuthority('...')")} needs
 *       a signed-in user holding it. The anonymous filter stays on, so {@code isAnonymous()} works in an expression;</li>
 *   <li><b>required</b> (before go-live, on the owner's word): every {@code /api} route needs a session, except signing
 *       in, the mode and the health check -- and a user whose password is temporary reaches only their own sign-in
 *       routes until they change it.</li>
 * </ul>
 */
@Configuration
@EnableWebSecurity
// Routes that need a permission say so with @PreAuthorize("hasAuthority('...')"), naming a security.Permission;
// PermissionCatalogueTest holds every name to the catalogue.
@EnableMethodSecurity
@EnableConfigurationProperties(SignInProperties.class)
public class SecurityConfig {

    /** The routes a signed-in user reaches whatever their password: their own sign-in. */
    static final String[] OWN_SIGN_IN = {"/api/auth/me", "/api/auth/logout", "/api/auth/password"};

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, SignInProperties signIn,
                                           ObjectProvider<SessionAuthenticator> sessions,
                                           ObjectProvider<ObjectMapper> json) throws Exception {
        SignInChallenge challenge = new SignInChallenge(json::getIfAvailable);
        http.cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .logout(AbstractHttpConfigurer::disable)
                .addFilterBefore(new BearerTokenFilter(sessions::getIfAvailable), AnonymousAuthenticationFilter.class)
                .exceptionHandling(handling -> handling.authenticationEntryPoint(challenge)
                        .accessDeniedHandler(challenge));
        if (signIn.signInMode() == SignInMode.REQUIRED) {
            http.authorizeHttpRequests(auth -> auth
                    // Render's health check, and the error page a refusal is rendered through.
                    .requestMatchers("/actuator/health", "/error").permitAll()
                    // How a session begins, and how the browser learns that it must.
                    .requestMatchers(HttpMethod.POST, "/api/auth/login").permitAll()
                    .requestMatchers(HttpMethod.GET, "/api/auth/mode").permitAll()
                    .requestMatchers(OWN_SIGN_IN).authenticated()
                    .requestMatchers("/api/**").access(SecurityConfig::passwordSettled)
                    .anyRequest().authenticated());
        } else {
            http.authorizeHttpRequests(auth -> auth
                                                       // 1. Allow Render's internal deployment bots
                                                       .requestMatchers("/actuator/health", "/error").permitAll()

                                                       // 2. Open ALL nested sub-paths under /api/ using standard Ant/MVC wildcards
                                                       .requestMatchers("/api/**").permitAll()
                                                       .requestMatchers("/api/banks/**").permitAll()
                                                       .requestMatchers("/api/members/**").permitAll()
                                                       .requestMatchers("/api/representatives/**").permitAll()

                                                       // 3. Authenticate anything else
                                                       .anyRequest().authenticated()
            );
        }
        return http.build();
    }

    /**
     * Required mode's rule for {@code /api}: a signed-in user, and not one whose password is temporary -- they are
     * refused {@code PASSWORD_CHANGE_REQUIRED} until they change it.
     */
    static AuthorizationDecision passwordSettled(Supplier<Authentication> authentication,
                                                 RequestAuthorizationContext request) {
        Authentication caller = authentication.get();
        boolean signedIn = caller != null && caller.isAuthenticated() && !(caller instanceof AnonymousAuthenticationToken);
        boolean temporary = signedIn && caller.getPrincipal() instanceof SignedInUser user && user.mustChangePassword();
        return new AuthorizationDecision(signedIn && !temporary);
    }


    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();

        // The Vite dev server, and the one hosted front end there is. A hosted front end is added here
        // deliberately -- CorsPolicyTest keeps the list closed. (The compose stack needs no entry: nginx serves
        // the client and proxies /api on the same origin.)
        //
        // 2026-09-28: `https://dev.sasbo.online` admitted. The owner's backend went live on Render that day at
        // `sasbo-api.onrender.com`, and the front end at dev.sasbo.online calls it CROSS-ORIGIN: its Vercel build
        // sets VITE_API_BASE_URL to an absolute URL and its vercel.json proxies nothing (`/(.*)` -> /index.html,
        // an SPA fallback only). So without this entry every BROWSER call is refused while curl still answers 200,
        // which reads as a healthy backend and a broken app.
        //
        // 🔴 EXACTLY ONE HOST, not a pattern. `https://*.vercel.app` was added upstream in 6c00b82, caught by
        // CorsPolicyTest, and recorded as register row B7: vercel.app is a public suffix, so that pattern trusts
        // every site ever deployed to Vercel -- with credentials, on an API whose every route is permitAll. A
        // preview deployment that needs admitting is named here in full, one line each.
        config.setAllowedOriginPatterns(List.of(
                "http://localhost:5173",
                "https://dev.sasbo.online"
        ));

        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS", "HEAD"));
        config.setAllowedHeaders(List.of("*"));
        // October build: the site calls the API cross-origin, so a browser hides every response header but a few
        // unless it is exposed here -- a download's file name (Content-Disposition) and the debit order file's
        // download count and checksum. One line for every stream's file routes.
        config.setExposedHeaders(List.of("Content-Disposition", "X-File-Downloads", "X-File-Sha256"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
