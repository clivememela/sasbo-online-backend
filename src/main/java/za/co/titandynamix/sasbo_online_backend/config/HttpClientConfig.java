package za.co.titandynamix.sasbo_online_backend.config;

import jakarta.annotation.Nonnull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.BufferingClientHttpRequestFactory;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 🔴 FIX: Request timeout monitoring and performance interceptor.
 *
 * <p>Logs slow requests and provides diagnostics for timeout issues.
 * Helps identify which endpoints are causing delays on Render.
 */
@Configuration
@Slf4j
public class HttpClientConfig implements WebMvcConfigurer {

    /**
     * Request timing interceptor to log slow endpoints.
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new PerformanceInterceptor())
                .addPathPatterns("/api/**");
    }

    /**
     * RestTemplate with connection timeouts (for external API calls if added later).
     */
    @Bean
    public RestTemplate restTemplate() {
        ClientHttpRequestFactory factory = clientHttpRequestFactory();
        return new RestTemplate(factory);
    }

    /**
     * Configure HTTP client with timeouts for external calls.
     */
    private ClientHttpRequestFactory clientHttpRequestFactory() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000);      // 5 second connect timeout
        factory.setReadTimeout(10000);        // 10 second read timeout
        return new BufferingClientHttpRequestFactory(factory);
    }

    /**
     * Interceptor that logs request timing and identifies slow endpoints.
     */
    @Slf4j
    static class PerformanceInterceptor implements HandlerInterceptor {

        private static final String START_TIME = "START_TIME";
        private static final long SLOW_THRESHOLD_MS = 2000L;  // Log if > 2 seconds

        @Override
        public boolean preHandle(HttpServletRequest request, @Nonnull HttpServletResponse response, @Nonnull Object handler) {
            request.setAttribute(START_TIME, System.currentTimeMillis());
            return true;
        }

        @Override
        public void afterCompletion(HttpServletRequest request, @Nonnull HttpServletResponse response, @Nonnull Object handler, Exception ex) {
            long startTime = (long) request.getAttribute(START_TIME);
            long duration = System.currentTimeMillis() - startTime;

            String endpoint = request.getRequestURI();
            int status = response.getStatus();

            // 🔴 FIX: Log slow requests to help diagnose timeouts
            if (duration > SLOW_THRESHOLD_MS) {
                log.warn("⏱️  SLOW REQUEST: {} {} took {}ms (status: {})",
                        request.getMethod(),
                        endpoint,
                        duration,
                        status);
            } else if (log.isDebugEnabled()) {
                log.debug("✓ {} {} completed in {}ms (status: {})",
                        request.getMethod(),
                        endpoint,
                        duration,
                        status);
            }

            // 🔴 FIX: Log errors with full context
            if (status >= 500) {
                log.error("❌ ERROR RESPONSE: {} {} returned {} in {}ms",
                        request.getMethod(),
                        endpoint,
                        status,
                        duration);
                if (ex != null) {
                    log.error("Exception details: ", ex);
                }
            }
        }
    }
}
