package za.co.titandynamix.sasbo_online_backend.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
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
public class SecurityConfig {

    @Value("${FRONTEND_URL:}")
    private String frontendUrl;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http.cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth
                                                       .requestMatchers("/actuator/health").permitAll()
                                                       .requestMatchers(HttpMethod.GET, "/api/banks", "/api/job-grades", "/api/banks/**").permitAll()
                                                       .requestMatchers(HttpMethod.GET, "/api/subscriptions", "/api/subscriptions/**").permitAll()
                                                       .requestMatchers("/api/**").permitAll()
                                                       .anyRequest().authenticated()
                );
        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();

        // Strip trailing slash if present on FRONTEND_URL string
        String cleanFrontendUrl = (this.frontendUrl != null) ? this.frontendUrl.replaceAll("/+$", "") : "";

        List<String> origins = !cleanFrontendUrl.isBlank()
                                       ? List.of("http://localhost:5173", cleanFrontendUrl,"https://dev.sasbo.online/")
                                       : List.of("http://localhost:5173");

        config.setAllowedOrigins(origins);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS", "HEAD"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        // Global rule alignment capturing browser handshake updates
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
