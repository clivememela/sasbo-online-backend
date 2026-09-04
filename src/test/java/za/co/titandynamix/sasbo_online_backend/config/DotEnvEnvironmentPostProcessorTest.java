package za.co.titandynamix.sasbo_online_backend.config;

import org.apache.commons.logging.LogFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.logging.DeferredLogFactory;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.StandardEnvironment;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Parser tests for {@link DotEnvEnvironmentPostProcessor}.
 *
 * <p>These cover the cases that would otherwise be discovered by an unresolved-placeholder failure at
 * startup, where the error names the missing property rather than the malformed line that caused it.
 */
class DotEnvEnvironmentPostProcessorTest {

    @Test
    @DisplayName("reads plain KEY=VALUE pairs and preserves file order")
    void readsPlainPairs() {
        Map<String, Object> parsed = DotEnvEnvironmentPostProcessor.parse("""
                DB_USERNAME=clivememela
                SERVER_PORT=8080
                """);

        assertEquals("clivememela", parsed.get("DB_USERNAME"));
        assertEquals("8080", parsed.get("SERVER_PORT"));
        assertEquals(List.of("DB_USERNAME", "SERVER_PORT"), List.copyOf(parsed.keySet()));
    }

    @Test
    @DisplayName("ignores comments, blank lines and a leading 'export'")
    void ignoresNoise() {
        Map<String, Object> parsed = DotEnvEnvironmentPostProcessor.parse("""
                # a comment

                   # an indented comment
                export DB_PASSWORD=secret
                """);

        assertEquals(1, parsed.size());
        assertEquals("secret", parsed.get("DB_PASSWORD"));
    }

    @Test
    @DisplayName("keeps '=' inside a value — a JDBC URL or a password may contain one")
    void keepsEqualsInsideValue() {
        Map<String, Object> parsed = DotEnvEnvironmentPostProcessor.parse(
                "SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/sasbo_membership?ssl=true\n");

        assertEquals("jdbc:postgresql://localhost:5432/sasbo_membership?ssl=true",
                parsed.get("SPRING_DATASOURCE_URL"));
    }

    @Test
    @DisplayName("an empty value is kept as an empty string, not dropped")
    void keepsEmptyValue() {
        // .env ships DB_PASSWORD= blank. It must resolve to "" rather than go missing, or the
        // placeholder fails and the error points at the wrong thing.
        Map<String, Object> parsed = DotEnvEnvironmentPostProcessor.parse("DB_PASSWORD=\n");

        assertTrue(parsed.containsKey("DB_PASSWORD"));
        assertEquals("", parsed.get("DB_PASSWORD"));
    }

    @Test
    @DisplayName("strips surrounding quotes and keeps spaces inside them")
    void stripsQuotes() {
        Map<String, Object> parsed = DotEnvEnvironmentPostProcessor.parse("""
                SINGLE='pass word'
                DOUBLE="pass word"
                BARE=plain
                """);

        assertEquals("pass word", parsed.get("SINGLE"));
        assertEquals("pass word", parsed.get("DOUBLE"));
        assertEquals("plain", parsed.get("BARE"));
    }

    @Test
    @DisplayName("expands escapes only inside double quotes, and only once")
    void expandsEscapesCorrectly() {
        Map<String, Object> parsed = DotEnvEnvironmentPostProcessor.parse("""
                NEWLINE="a\\nb"
                LITERAL='a\\nb'
                BACKSLASH_N="a\\\\nb"
                WINDOWS_PATH="C:\\Users\\mfazl"
                """);

        assertEquals("a\nb", parsed.get("NEWLINE"));
        // Single quotes are literal — no expansion.
        assertEquals("a\\nb", parsed.get("LITERAL"));
        // The regression the single-pass expander exists for: an escaped backslash followed by 'n'
        // must NOT become a newline.
        assertEquals("a\\nb", parsed.get("BACKSLASH_N"));
        // An unrecognised escape survives verbatim, so an unescaped Windows path is not mangled.
        assertEquals("C:\\Users\\mfazl", parsed.get("WINDOWS_PATH"));
    }

    @Test
    @DisplayName("skips malformed lines instead of guessing at them")
    void skipsMalformedLines() {
        Map<String, Object> parsed = DotEnvEnvironmentPostProcessor.parse("""
                NO_SEPARATOR
                =NO_KEY
                GOOD=value
                """);

        assertEquals(1, parsed.size());
        assertEquals("value", parsed.get("GOOD"));
        assertNull(parsed.get(""));
    }

    @Test
    @DisplayName("strips a byte-order mark so the first key is not corrupted")
    void stripsByteOrderMark() {
        // Notepad and PowerShell redirection both write a BOM. Without stripping it the first key
        // becomes "\uFEFFDB_USERNAME", which silently fails to match anything.
        Map<String, Object> parsed = DotEnvEnvironmentPostProcessor.parse("\uFEFFDB_USERNAME=clivememela\n");

        assertEquals("clivememela", parsed.get("DB_USERNAME"));
        assertTrue(parsed.containsKey("DB_USERNAME"));
    }

    @Test
    @DisplayName("handles CRLF line endings")
    void handlesCrlf() {
        Map<String, Object> parsed = DotEnvEnvironmentPostProcessor.parse("A=1\r\nB=2\r\n");

        assertEquals("1", parsed.get("A"));
        assertEquals("2", parsed.get("B"));
    }

    @Test
    @DisplayName("a later duplicate key wins")
    void laterDuplicateWins() {
        Map<String, Object> parsed = DotEnvEnvironmentPostProcessor.parse("PORT=8080\nPORT=9090\n");

        assertEquals("9090", parsed.get("PORT"));
    }

    @Test
    @DisplayName("empty content yields no entries rather than a bogus one")
    void emptyContentYieldsNothing() {
        assertTrue(DotEnvEnvironmentPostProcessor.parse("").isEmpty());
        assertTrue(DotEnvEnvironmentPostProcessor.parse("\n\n   \n").isEmpty());
    }

    // -----------------------------------------------------------------------
    // The developer-checkout guard.
    //
    // This is the safety property that makes the class shippable: a tracked .env
    // must never become production configuration. Without these tests the guard
    // is an assertion in a javadoc comment rather than a behaviour.
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("developer-checkout guard")
    class Guard {

        private String originalWorkingDirectory;

        @BeforeEach
        void rememberWorkingDirectory() {
            this.originalWorkingDirectory = System.getProperty("user.dir");
        }

        @AfterEach
        void restoreWorkingDirectory() {
            System.setProperty("user.dir", this.originalWorkingDirectory);
        }

        @Test
        @DisplayName("🔴 .env is NOT read when .env.local is absent — the production case")
        void ignoresSharedEnvWithoutLocalFile(@TempDir Path dir) throws Exception {
            // Exactly the Render situation: the tracked .env is present, nothing else is.
            // SASBO_GUARD_PROBE stands in for SSL_MODE — a real environment variable of that name on
            // the machine running the test would make the assertion below pass or fail for the wrong
            // reason, and a test that can be swayed by the developer's shell is not a test.
            Files.writeString(dir.resolve(".env"), "SSL_MODE=prefer\nSASBO_GUARD_PROBE=leaked\n");

            ConfigurableEnvironment environment = runProcessorIn(dir);

            assertFalse(environment.getPropertySources().contains("dotenv[.env]"),
                    ".env must not be loaded without .env.local — it is tracked and ships to production");
            assertFalse(environment.getPropertySources().contains("dotenv[.env.local]"));
            // The consequence that matters: a value sitting in the committed .env did not silently
            // satisfy a placeholder that production expects the host to supply.
            assertNull(environment.getProperty("SASBO_GUARD_PROBE"));
        }

        @Test
        @DisplayName("both files load when .env.local is present, with .env as fallback")
        void loadsBothWhenLocalFilePresent(@TempDir Path dir) throws Exception {
            Files.writeString(dir.resolve(".env"),
                    "SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/shared\nDB_PASSWORD=fromShared\n");
            Files.writeString(dir.resolve(".env.local"), "DB_PASSWORD=fromLocal\n");

            ConfigurableEnvironment environment = runProcessorIn(dir);

            // .env.local wins where both define a key...
            assertEquals("fromLocal", environment.getProperty("DB_PASSWORD"));
            // ...and .env still supplies what .env.local leaves out.
            assertEquals("jdbc:postgresql://localhost:5432/shared",
                    environment.getProperty("SPRING_DATASOURCE_URL"));
        }

        @Test
        @DisplayName(".env.local outranks real environment variables; .env does not")
        void precedenceSitsEitherSideOfSystemEnvironment(@TempDir Path dir) throws Exception {
            Files.writeString(dir.resolve(".env"), "A=1\n");
            Files.writeString(dir.resolve(".env.local"), "B=2\n");

            ConfigurableEnvironment environment = runProcessorIn(dir);

            List<String> order = new ArrayList<>();
            environment.getPropertySources().forEach(source -> order.add(source.getName()));

            int local = order.indexOf("dotenv[.env.local]");
            int systemEnvironment = order.indexOf(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
            int shared = order.indexOf("dotenv[.env]");

            assertTrue(local >= 0 && systemEnvironment >= 0 && shared >= 0, "all three sources present: " + order);
            assertTrue(local < systemEnvironment,
                    ".env.local must outrank real environment variables, order was " + order);
            assertTrue(shared > systemEnvironment,
                    ".env must rank below real environment variables, order was " + order);
        }

        /** Runs the post-processor as though {@code dir} were the process working directory. */
        private ConfigurableEnvironment runProcessorIn(Path dir) {
            System.setProperty("user.dir", dir.toAbsolutePath().toString());
            ConfigurableEnvironment environment = new StandardEnvironment();
            DeferredLogFactory logFactory = supplier -> LogFactory.getLog(DotEnvEnvironmentPostProcessor.class);
            new DotEnvEnvironmentPostProcessor(logFactory).postProcessEnvironment(environment, null);
            return environment;
        }
    }
}
