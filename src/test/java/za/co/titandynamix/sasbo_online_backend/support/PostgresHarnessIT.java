package za.co.titandynamix.sasbo_online_backend.support;

import java.sql.Connection;
import java.sql.SQLException;

import javax.sql.DataSource;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The harness proving itself: is the database these tests talk to really a
 * container running the real migration chain?
 *
 * <p>Every other integration test in the estate inherits its database from
 * {@link AbstractPostgresIT} and would pass just as happily against an empty
 * schema, an H2 instance, or -- the failure that matters -- a developer's own
 * database. These assertions are what make that impossible to miss.
 */
@DisplayName("The Postgres harness")
class PostgresHarnessIT extends AbstractPostgresIT {

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private DataSource dataSource;

    @Test
    @DisplayName("is a container, not this machine's database")
    void isAThrowawayContainer() throws SQLException {
        String url = POSTGRES.getJdbcUrl();

        assertThat(url)
                .as("the container's own JDBC URL")
                .contains("localhost")
                .doesNotContain("sasbo_membership");

        // Where the connection ACTUALLY goes. Asked of the live connection, not of
        // the Environment -- see the note below on why that distinction is the whole
        // guard.
        try (Connection connection = dataSource.getConnection()) {
            assertThat(connection.getMetaData().getURL())
                    .as("the URL the pool is really dialling")
                    .isEqualTo(url);
        }

        assertThat(jdbc.queryForObject("SELECT current_database()", String.class))
                .as("the database actually connected to. 'sasbo_membership' here would mean "
                        + "the integration lane had found the one database on this machine "
                        + "that cannot be rebuilt from migrations")
                .isEqualTo("sasbo_it");
    }

    @Test
    @DisplayName("ignores a .env.local pointed at the real database")
    void envLocalCannotReachTheDatasource() throws SQLException {
        // DotEnvEnvironmentPostProcessor places .env.local ABOVE application.yaml, so
        // a developer's own SPRING_DATASOURCE_URL outranks anything this profile says.
        // @ServiceConnection wins anyway -- but NOT by setting the property. It
        // contributes a JdbcConnectionDetails bean that the DataSource
        // auto-configuration prefers over every property source, so:
        //
        //     spring.datasource.url  can still read jdbc:...:5432/sasbo_membership
        //     the actual connection  is the container, every time
        //
        // Verified 2026-09-04 by putting exactly that into .env.local and running the
        // lane. That gap is why this guard asks the CONNECTION and never the property:
        // an assertion on spring.datasource.url reports a developer's real database
        // while the test is safely on a container -- and, read the other way round,
        // would pass while the connection had gone somewhere else entirely.
        try (Connection connection = dataSource.getConnection()) {
            assertThat(connection.getCatalog())
                    .as("the catalog the live connection is bound to")
                    .isEqualTo("sasbo_it");
            assertThat(connection.getMetaData().getURL())
                    .as("no developer database, whatever the environment claims")
                    .doesNotContain("sasbo_membership")
                    .isEqualTo(POSTGRES.getJdbcUrl());
        }
    }

    @Test
    @DisplayName("ran the whole migration chain, not just a schema")
    void ranTheFullMigrationChain() {
        // 19 union structures exist only because V16 ran. A container given a
        // Hibernate-generated schema, or an empty one, has the columns and none
        // of the rows -- so this single number separates "a database" from "the
        // database the application actually runs against".
        assertThat(jdbc.queryForObject("SELECT count(*) FROM union_structures", Integer.class))
                .as("union_structures seeded by V16")
                .isEqualTo(19);

        assertThat(jdbc.queryForObject(
                "SELECT max(version::int) FROM flyway_schema_history WHERE version IS NOT NULL",
                Integer.class))
                .as("the last migration applied")
                .isEqualTo(18);
    }

    @Test
    @DisplayName("fails fast rather than hanging")
    void failFastSettingsArePresent() {
        // A test that deadlocks or writes a runaway query must FAIL, in seconds,
        // with an error. A build that hangs has no message and blocks everything
        // behind it. Asserted here so a later edit to the container definition
        // cannot quietly drop them.
        assertThat(jdbc.queryForObject("SHOW lock_timeout", String.class))
                .as("lock_timeout")
                .isEqualTo("5s");
        assertThat(jdbc.queryForObject("SHOW statement_timeout", String.class))
                .as("statement_timeout")
                .isEqualTo("30s");
    }

    @Test
    @DisplayName("sorts surnames the way this estate sorts surnames")
    void collationIsPinned() {
        assertThat(jdbc.queryForObject(
                "SELECT datcollate FROM pg_database WHERE datname = current_database()",
                String.class))
                .as("collation -- 'de Villiers', 'De Wet' and 'van Zyl' order differently under C")
                .isEqualTo("en_ZA.UTF-8");
    }

    @Test
    @DisplayName("does not run DataSeeder")
    void dataSeederIsNeutralised() {
        // DataSeeder has no @Profile and no @ConditionalOnProperty, so it runs on
        // every Spring context. If the @MockitoBean in the base class ever stops
        // covering a context, 160 members appear before the first assertion of
        // whatever test booted it, and the symptom looks like a data bug.
        assertThat(jdbc.queryForObject("SELECT count(*) FROM members", Integer.class))
                .as("members -- the migrations seed reference data, never member records")
                .isZero();
    }
}
