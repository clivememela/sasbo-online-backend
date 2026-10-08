package za.co.titandynamix.sasbo_online_backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import java.util.List;

@Configuration
@EnableWebSecurity
// October build: routes that need a permission say so with @PreAuthorize("hasAuthority('...')"), naming a
// security.Permission. Nothing on main carries one, so this changes no route that exists; every route stays open
// until the sign-in work decides what an anonymous caller may reach.
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http.cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth
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
        return http.build();
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
