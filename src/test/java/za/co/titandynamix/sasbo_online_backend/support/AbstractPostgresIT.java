package za.co.titandynamix.sasbo_online_backend.support;

import org.junit.jupiter.api.Tag;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import za.co.titandynamix.sasbo_online_backend.utils.DataSeeder;

import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Base class for every integration test: a real, throwaway PostgreSQL with the
 * full migration chain applied.
 *
 * <p>Extend it and the database is there. Nothing else in the suite may create
 * a container, and nothing else may name a JDBC URL.
 *
 * <h2>One container per JVM</h2>
 * The container is {@code static} and started once, in a static initialiser,
 * deliberately without {@code @Container} -- that annotation ties a container's
 * lifecycle to a class, and a fresh PostgreSQL per test class costs 30-90
 * seconds each. Testcontainers' Ryuk sidecar removes it when the JVM exits, so
 * nothing is left behind. Subclasses therefore share one real database, which
 * is why each of them must leave it as it found it.
 *
 * <h2>The image is built, not pulled</h2>
 * {@code postgres:18.3} ships only {@code C}, {@code C.utf8} and
 * {@code en_US.utf8}; there is no {@code en_ZA.UTF-8} in it, and passing that
 * locale to {@code initdb} fails at container start. The locale SOURCE is
 * present, so this builds a one-line derived image that generates it first.
 *
 * <p>Collation is not cosmetic. {@code de Villiers}, {@code De Wet},
 * {@code O'Brien} and {@code van Zyl} sort differently under {@code C} than
 * under {@code en_ZA.UTF-8}, and a members register ordered by surname is
 * exactly that surface. The tag is exact and never {@code :latest} -- an
 * unpinned image is a scheduled, unannounced change to the test environment.
 *
 * <h2>Fail fast, never hang</h2>
 * {@code lock_timeout} and {@code statement_timeout} are set on the server so a
 * test that deadlocks or writes a runaway query FAILS in seconds instead of
 * hanging until someone notices. A hung build is worse than a red one: it has
 * no error message and it blocks everything behind it. These settings are
 * asserted present by a committed test, so a later edit cannot quietly drop
 * them.
 *
 * <h2>{@code DataSeeder} is neutralised</h2>
 * It is a bare {@code @Component implements ApplicationRunner} with no
 * {@code @Profile} and no {@code @ConditionalOnProperty}, so it runs on EVERY
 * Spring context and writes 160 members, 68 banks and 68 branches before the
 * first assertion in any test. That would break "each test seeds its own
 * state" before an author could obey it, and it seeds through a known silent
 * bank-name drop, so the lane would be built on data that is already partly
 * wrong. {@code @MockitoBean} leaves the runner present and makes it do
 * nothing. The real fix is a conditional guard on the bean itself; that is
 * production code and is filed as a finding rather than applied here.
 */
@SpringBootTest
@ActiveProfiles("integration")
@Tag("integration")
public abstract class AbstractPostgresIT {

    /** Pinned exactly. Never {@code :latest}. */
    private static final String POSTGRES_IMAGE = "postgres:18.3";

    /** The locale this estate sorts surnames in. */
    private static final String LOCALE = "en_ZA.UTF-8";

    /**
     * {@code @ServiceConnection} on this field is what points Spring Boot's
     * datasource at the container. Spring finds it by walking the test class
     * hierarchy, so declaring it once here serves every subclass.
     *
     * <p>Deliberately NOT annotated {@code @Container}: that annotation ties the
     * lifecycle to a class, giving a fresh database -- and 30-90 seconds -- per
     * test class. The static initialiser below starts it once instead, and Ryuk
     * removes it when the JVM exits.
     */
    /**
     * Generates the locale, then hands over to the image's own entrypoint.
     *
     * <p>This is an entrypoint override rather than a derived image because it
     * needs no build step, and because it is character-for-character the recipe
     * the migration chain was proved against outside the JVM -- one recipe, one
     * place it can be wrong.
     *
     * <p>The server flags ride along here for the same reason: with the
     * entrypoint replaced, the image's own CMD is no longer what starts
     * PostgreSQL.
     */
    private static final String[] ENTRYPOINT = {
        "bash", "-c",
        "localedef -i en_ZA -c -f UTF-8 -A /usr/share/locale/locale.alias " + LOCALE
                + " && exec docker-entrypoint.sh postgres"
                // Fail fast: a deadlocked or runaway test dies in seconds with an
                // error instead of hanging the build, which has no message and
                // blocks everything behind it.
                + " -c lock_timeout=5s"
                + " -c statement_timeout=30s"
                // Durability is worthless in a container destroyed at the end of
                // the run, and turning it off is most of the speed.
                + " -c fsync=off"
                + " -c full_page_writes=off",
    };

    /**
     * {@code @ServiceConnection} on this field is what points Spring Boot's
     * datasource at the container. Spring finds it by walking the test class
     * hierarchy, so declaring it once here serves every subclass.
     *
     * <p>Deliberately NOT annotated {@code @Container}: that annotation ties the
     * lifecycle to a class, giving a fresh database -- and 30-90 seconds -- per
     * test class. The static initialiser below starts it once instead, and Ryuk
     * removes it when the JVM exits.
     */
    @ServiceConnection
    protected static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse(POSTGRES_IMAGE))
                    .withDatabaseName("sasbo_it")
                    .withUsername("sasbo")
                    .withPassword("sasbo")
                    .withEnv("LANG", LOCALE)
                    .withEnv("POSTGRES_INITDB_ARGS", "--locale=" + LOCALE + " --encoding=UTF8")
                    .withCreateContainerCmdModifier(cmd -> cmd.withEntrypoint(ENTRYPOINT));

    static {
        POSTGRES.start();
    }

    @MockitoBean
    private DataSeeder dataSeeder;
}
