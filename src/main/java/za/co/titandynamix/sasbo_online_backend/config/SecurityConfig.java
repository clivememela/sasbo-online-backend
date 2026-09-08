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

import java.util.ArrayList;
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

        List<String> origins = new ArrayList<>();

        // 1. Always allow Local Development
        origins.add("http://localhost:5173");

        // 2. Always allow your future Custom Production Domain (No trailing slash!)
        origins.add("https://sasbo.online");

        // 3. Always allow current temporary Vercel domain as a fallback
        origins.add("https://vercel.app");

        // 4. Dynamically append FRONTEND_URL if provided in Render Env Variables
        if (this.frontendUrl != null && !this.frontendUrl.isBlank()) {
            String cleanFrontendUrl = this.frontendUrl.replaceAll("/+$", "");
            if (!origins.contains(cleanFrontendUrl)) {
                origins.add(cleanFrontendUrl);
            }
        }

        config.setAllowedOrigins(origins);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS", "HEAD"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
