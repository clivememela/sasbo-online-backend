package za.co.titandynamix.sasbo_online_backend.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.cors.CorsConfiguration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The headers a browser may read on a cross-origin answer, beyond the few it always shows. The site calls the API
 * cross-origin, so without these a download arrives with no file name, and the debit order file's download count and
 * checksum are invisible to the screen. The origin list itself is CorsPolicyTest's.
 */
class CorsExposedHeadersTest {

    @Test
    void aDownloadsNameAndTheDebitOrderFilesCountAndChecksumAreReadable() {
        CorsConfiguration config = new SecurityConfig().corsConfigurationSource()
                .getCorsConfiguration(new MockHttpServletRequest("GET", "/api/payroll/files/1/report"));

        assertThat(config).isNotNull();
        assertThat(config.getExposedHeaders())
                .containsExactlyInAnyOrder("Content-Disposition", "X-File-Downloads", "X-File-Sha256");
    }
}
