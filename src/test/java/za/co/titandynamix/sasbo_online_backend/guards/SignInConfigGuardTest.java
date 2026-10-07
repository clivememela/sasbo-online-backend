package za.co.titandynamix.sasbo_online_backend.guards;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Sign-in's settings as the PRODUCTION configuration writes them (contracts/signin.md; the parent's settlement of
 * 7 Oct): each bound explicitly to its documented variable, open by default, and NO password with a default -- not
 * the demonstration users', not the first administrator's. And no in-memory user whose generated password the start
 * would log. Read from {@code src/main/resources/application.yaml} itself: the test classpath carries a file of the
 * same name, which the application's own tests read instead.
 */
@DisplayName("Sign-in's production settings")
class SignInConfigGuardTest {

    private static final Path PRODUCTION_CONFIG = Path.of("src", "main", "resources", "application.yaml");

    static Map<String, String> flattened(Resource yaml) {
        YamlPropertiesFactoryBean factory = new YamlPropertiesFactoryBean();
        factory.setResources(yaml);
        Properties properties = factory.getObject();
        Map<String, String> found = new TreeMap<>();
        properties.forEach((key, value) -> found.put(key.toString(), value.toString().trim()));
        return found;
    }

    /** A placeholder for {@code variable} with no default at all: {@code ${VARIABLE:}}. */
    static boolean noDefault(String value, String variable) {
        return ("${" + variable + ":}").equals(value);
    }

    @Test
    @DisplayName("🔒 each setting bound to its documented variable; open by default; no password with a default")
    void theBindingsAreExplicitAndHoldNoPassword() {
        assertThat(PRODUCTION_CONFIG).as("the guard reads the production file from the project root").isRegularFile();
        Map<String, String> config = flattened(new FileSystemResource(PRODUCTION_CONFIG));
        assertThat(config).as("this is the production file").containsEntry("spring.datasource.url",
                "${SPRING_DATASOURCE_URL}");
        assertThat(config.get("sasbo.signin.mode")).isEqualTo("${SASBO_SIGNIN:open}");
        assertThat(config.get("sasbo.signin.demo-users")).isEqualTo("${SASBO_SIGNIN_DEMO_USERS:false}");
        assertThat(noDefault(config.get("sasbo.signin.demo-password"), "SASBO_SIGNIN_DEMO_PASSWORD"))
                .as("the demonstration password comes from the environment only").isTrue();
        assertThat(noDefault(config.get("sasbo.signin.bootstrap-admin.username"), "SASBO_BOOTSTRAP_ADMIN_USERNAME"))
                .isTrue();
        assertThat(noDefault(config.get("sasbo.signin.bootstrap-admin.password"), "SASBO_BOOTSTRAP_ADMIN_PASSWORD"))
                .as("the first administrator's password comes from the host only").isTrue();
    }

    @Test
    @DisplayName("🔒 no in-memory user: its generated password would be written to the log at every start")
    void noInMemoryUser() {
        Map<String, String> config = flattened(new FileSystemResource(PRODUCTION_CONFIG));
        assertThat(config.get("spring.autoconfigure.exclude"))
                .contains("org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration");
    }

    @Test
    @DisplayName("canary: a password given a default is caught, and a placeholder without one passes")
    void thePredicateCanFail() {
        Map<String, String> planted = flattened(new ByteArrayResource(("sasbo:\n  signin:\n    demo-password: "
                + "${SASBO_SIGNIN_DEMO_PASSWORD:an-invented-default}\n").getBytes(StandardCharsets.UTF_8)));
        assertThat(noDefault(planted.get("sasbo.signin.demo-password"), "SASBO_SIGNIN_DEMO_PASSWORD")).isFalse();
        assertThat(noDefault("${SASBO_SIGNIN_DEMO_PASSWORD:}", "SASBO_SIGNIN_DEMO_PASSWORD")).isTrue();
        assertThat(noDefault(null, "SASBO_SIGNIN_DEMO_PASSWORD")).isFalse();
    }
}
