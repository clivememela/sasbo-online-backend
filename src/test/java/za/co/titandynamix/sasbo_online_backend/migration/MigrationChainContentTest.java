package za.co.titandynamix.sasbo_online_backend.migration;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * What the chain leaves behind, asserted rather than remembered.
 *
 * <p>{@link MigrationSchemaValidationTest} proves the migrated schema matches
 * the entity model. It cannot prove anything about the DATA the migrations
 * seed, or about the columns the model does not mention -- Hibernate
 * {@code validate} is blind to both. Every check here was previously a psql
 * query someone ran by hand once and wrote the answer down; a number in a
 * document expires silently, an assertion does not.
 *
 * <p>Needs a migrated database; see {@link MigrationDatabaseTest}.
 */
@MigrationDatabaseTest
@DisplayName("What the migration chain leaves behind")
class MigrationChainContentTest {

    /**
     * The seven columns V8 removes. Their presence would mean V8 ran against a
     * database that never had them, which is indistinguishable from V8 not
     * having run at all.
     */
    private static final List<String> DROPPED_BY_V8 = List.of(
            "managers_code", "memnet_member", "print_label_for_postage", "suspension_code",
            "deduct_acb", "print_membership_cards", "home_loan_reduced_rate");

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    @DisplayName("the history is complete, successful, and has the V6 gap it is supposed to have")
    void schemaHistoryIsComplete() {
        List<String> versions = jdbc.queryForList(
                "SELECT version FROM flyway_schema_history WHERE version IS NOT NULL "
                        + "ORDER BY installed_rank", String.class);

        assertThat(versions)
                .as("applied versions in Flyway's order -- this list is also what pins "
                        + "the deliberate gap where V6 would be. See V1's header")
                .containsExactly("1", "2", "3", "4", "5", "7", "8", "9", "10",
                        "11", "12", "13", "14", "15", "16", "17", "18");

        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM flyway_schema_history WHERE NOT success", Integer.class))
                .as("failed migrations recorded in the history")
                .isZero();
    }

    @Test
    @DisplayName("the chain builds 24 tables")
    void tableCount() {
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM information_schema.tables WHERE table_schema = 'public'",
                Integer.class))
                .as("tables in public, including flyway_schema_history")
                .isEqualTo(24);
    }

    @Test
    @DisplayName("the seed data comes from the migrations, not from a hand-run script")
    void seedDataIsApplied() {
        // 19 and 6 are V16's reseed. Under the old baseline-version: 0 configuration
        // V16 was skipped outright and both tables were left empty -- which is the
        // defect that makes these two numbers worth asserting rather than assuming.
        assertThat(jdbc.queryForObject("SELECT count(*) FROM union_structures", Integer.class))
                .as("union_structures, seeded by V16")
                .isEqualTo(19);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM representative_roles", Integer.class))
                .as("representative_roles, seeded by V16")
                .isEqualTo(6);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM recruiters", Integer.class))
                .as("recruiters, seeded by V17")
                .isEqualTo(33);

        assertThat(jdbc.queryForObject(
                "SELECT name FROM union_structures WHERE id = 1", String.class))
                .as("the root of the structure hierarchy")
                .isEqualTo("SASBO");
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM union_structures WHERE parent_id IS NULL", Integer.class))
                .as("exactly one root -- a second root means a reseed ran twice")
                .isEqualTo(1);
    }

    @Test
    @DisplayName("V18 left members_recruited numeric, not the text V17 created")
    void recruiterCountsAreNumeric() {
        assertThat(columnType("recruiters", "members_recruited"))
                .as("recruiters.members_recruited after V18's type change")
                .isEqualTo("integer");
    }

    @Test
    @DisplayName("both vestigial columns survive the chain, and both are load-bearing")
    void vestigialColumnsSurvive() {
        // Neither is mapped by any entity. Hibernate validate ignores columns the
        // model does not mention, so nothing else in the suite would notice if a
        // future migration dropped one -- and dropping either breaks the chain
        // itself, not the application. That is what makes them worth a test.
        assertThat(columnType("members", "recruitment_rep"))
                .as("members.recruitment_rep -- V2 hangs a self FK and a CHECK on it")
                .isEqualTo("integer");
        assertThat(columnType("members", "job_grade"))
                .as("members.job_grade -- V11 runs ALTER COLUMN TYPE on it, and that has no IF EXISTS")
                .isEqualTo("character varying");
    }

    @Test
    @DisplayName("the columns V8 and V9 drop really are gone")
    void droppedColumnsAreGone() {
        for (String column : DROPPED_BY_V8) {
            assertThat(columnType("members", column))
                    .as("members.%s -- dropped by V8", column)
                    .isNull();
        }
        assertThat(columnType("representatives", "name"))
                .as("representatives.name -- the NOT NULL column V9 drops, which broke every INSERT")
                .isNull();
        assertThat(columnType("representatives", "rep_role"))
                .as("representatives.rep_role -- added by V4, dropped by V9")
                .isNull();
    }

    /** The column's SQL type, or {@code null} if the column does not exist. */
    private String columnType(String table, String column) {
        List<String> types = jdbc.queryForList(
                "SELECT data_type FROM information_schema.columns "
                        + "WHERE table_schema = 'public' AND table_name = ? AND column_name = ?",
                String.class, table, column);
        return types.isEmpty() ? null : types.get(0);
    }
}
