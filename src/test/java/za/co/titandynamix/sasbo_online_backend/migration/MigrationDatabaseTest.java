package za.co.titandynamix.sasbo_online_backend.migration;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * A test that needs a PostgreSQL database the migration chain has already been
 * applied to.
 *
 * <p>Three things always travel together for such a test, and this is the one
 * place they are written down:
 *
 * <ul>
 *   <li>the {@code migration} profile, which is where {@code ddl-auto: validate}
 *       and the real datasource live -- and which is only visible from a test,
 *       because {@code src/test/resources} is not on the main runtime classpath;
 *   <li>a Spring context, since {@code validate} runs when the
 *       EntityManagerFactory is built;
 *   <li>the {@code P0_DB_URL} gate.
 * </ul>
 *
 * <p><b>The gate, and why it is not a quarantine.</b> These classes live in the
 * ordinary test source set, so ungated they would join {@code test} ->
 * {@code check} -> {@code build} and {@code ./gradlew build} would start
 * demanding a database of everyone who runs it -- work added to a name people
 * already run, without asking. So they run only when told where to look:
 * set {@code P0_DB_URL} and they run; leave it unset and JUnit reports them
 * skipped, by name, with the reason below. The gate is the SAME variable that
 * addresses the database, which is what keeps it honest: there is no way to
 * enable these tests without also pointing them at something, and no way to
 * point them at something without enabling them. Nothing here is a reaction to
 * flakiness, and nothing here is skipped because it failed.
 *
 * <p>Run them against a container the Flyway CLI has already migrated:
 * <pre>
 *   P0_DB_URL=jdbc:postgresql://localhost:5434/sasbo_membership_test \
 *     ./gradlew test --tests '*Migration*' --rerun
 * </pre>
 * {@code --rerun} is not optional. Gradle's up-to-date check knows nothing
 * about the database, so a second invocation with unchanged sources reports
 * BUILD SUCCESSFUL in a few seconds without running anything -- including
 * against a database that has since been broken. That is a green result
 * meaning nothing was checked, which is the failure mode these tests exist to
 * eliminate.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@SpringBootTest
@ActiveProfiles("migration")
@EnabledIfEnvironmentVariable(named = "P0_DB_URL", matches = ".+",
        disabledReason = "needs a migrated PostgreSQL database; set P0_DB_URL to point at one")
public @interface MigrationDatabaseTest {
}
