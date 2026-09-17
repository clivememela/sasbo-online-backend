package za.co.titandynamix.sasbo_online_backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
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

        // Use allowed origin PATTERNS to support wildcards for Vercel and sasbo online previews
        config.setAllowedOriginPatterns(List.of(
                "http://localhost:5173",
                "https://vercel.app",
                "https://*.vercel.app", // This allows any Vercel deployment URL
                "https://sasbo.online",
                "https://*.sasbo.online"
        ));

        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS", "HEAD"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
