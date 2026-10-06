package za.co.titandynamix.sasbo_online_backend.migration;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import za.co.titandynamix.sasbo_online_backend.support.MigrationSeed;

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
                        + "the deliberate gap where V6 would be, and the fractional 1.1. "
                        + "See V1_1__baseline_completion.sql's header")
                .containsExactlyElementsOf(MigrationSeed.APPLIED_VERSIONS);

        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM flyway_schema_history WHERE NOT success", Integer.class))
                .as("failed migrations recorded in the history")
                .isZero();
    }

    @Test
    @DisplayName("the chain builds the pinned number of tables -- the pin's comment names what each migration added")
    void tableCount() {
        // 24 until V40, which adds members_money_migration_report -- the record of
        // every money value it could not convert verbatim. A migration that adds a
        // table has to be acknowledged here, which is the whole point of pinning a
        // number that nothing else would notice changing.
        //
        // 31 from ACB P1: V41 adds acb_rates, acb_runs, acb_run_exclusions and
        // acb_run_exclusion_counts; V42 acb_run_lines; V43 acb_audit_events.
        // 32 from ACB P2: V44 adds acb_run_files.
        // 34 from ACB P3: V45 adds acb_unpaid_codes, V46 acb_letters.
        // 36 from ACB 01.8: V47 adds acb_holidays and acb_calendar (D30).
        // 37 from ACB 03.0: V48 adds acb_opening_position (D24).
        // 38 from ACB 03.5: V50 adds member_status_changes -- not acknowledged here at the time, because this class
        // runs in neither lane; counted from the migrations on 2026-09-29 by month end, which moves the pin next.
        // 48 from month end 01.1 and 01.2: V53 adds reference_lists, sasbo_regions, sasbo_areas, employer_groups,
        // employers, table_codes, committee_provinces, committees and suspension_codes; V54 adds workplaces.
        // 49 from month end 03.5: V59 adds member_changes.
        // 52 from month end 04.2: V60 adds month_end_months, month_end_workplaces and month_end_counts.
        // 53 from month end 04.10: V61 adds month_end_packs.
        // 54 from month end A.1: V62 adds workplace_visits.
        // 55 from the October build's reports-visits: V82 adds workplace_visit_drafts.
        //
        // 🔑 This class is gated behind P0_DB_URL and so runs in neither ordinary
        // lane, which makes "25" an edit nobody would catch being wrong. The delta
        // itself is measured in MemberMoneyMigrationIT#v40AddsOnlyTheReportTable,
        // which runs in the integration lane: one table added, none dropped.
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM information_schema.tables WHERE table_schema = 'public'",
                Integer.class))
                .as("tables in public, including flyway_schema_history")
                .isEqualTo(55);
    }

    @Test
    @DisplayName("the seed data comes from the migrations, not from a hand-run script")
    void seedDataIsApplied() {
        // 19 and 6 are V16's reseed. Under the old baseline-version: 0 configuration
        // V16 was skipped outright and both tables were left empty -- which is the
        // defect that makes these two numbers worth asserting rather than assuming.
        assertThat(jdbc.queryForObject("SELECT count(*) FROM union_structures", Integer.class))
                .as("union_structures, seeded by V16")
                .isEqualTo(MigrationSeed.UNION_STRUCTURES);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM representative_roles", Integer.class))
                .as("representative_roles, seeded by V16")
                .isEqualTo(MigrationSeed.REPRESENTATIVE_ROLES);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM recruiters", Integer.class))
                .as("recruiters, seeded by V17")
                .isEqualTo(MigrationSeed.RECRUITERS);

        assertThat(jdbc.queryForObject(
                "SELECT name FROM union_structures WHERE id = 1", String.class))
                .as("the root of the structure hierarchy")
                .isEqualTo(MigrationSeed.ROOT_STRUCTURE);
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

    /**
     * These columns are absent at the end of the chain and no later migration
     * re-adds them.
     *
     * <p>Note what this does NOT prove, because the name it used to have --
     * "the columns V8 and V9 drop really are gone" -- claimed it did. The
     * baseline this was written against created every one of these columns, so
     * their absence could only mean the drops ran. The surviving baseline
     * creates none of them, and V8 and V9 both use {@code DROP COLUMN IF
     * EXISTS}, so for these nine the assertion holds whether the migration ran
     * or not. {@code schemaHistoryIsComplete} is what asserts they applied;
     * {@code representativeRoleWasAddedThenDropped} below is the one column
     * that still distinguishes the two.
     */
    @Test
    @DisplayName("these columns are absent, and no later migration re-adds them")
    void droppedColumnsHaveNotReappeared() {
        for (String column : DROPPED_BY_V8) {
            assertThat(columnType("members", column))
                    .as("members.%s -- dropped by V8", column)
                    .isNull();
        }
        assertThat(columnType("representatives", "name"))
                .as("representatives.name -- the NOT NULL column V9 drops, which broke every INSERT")
                .isNull();
    }

    @Test
    @DisplayName("V9's drop really ran -- the one column that can still prove it")
    void representativeRoleWasAddedThenDropped() {
        // 🔑 THE ONLY ADD-THEN-DROP PAIR THE CHAIN STILL HAS against this
        // baseline, which is what makes it the only column whose absence is
        // evidence rather than a coincidence. V4 adds representatives.rep_role;
        // V9 drops it. The baseline does not create it, so if V9 had never run
        // this column would be PRESENT, put there by V4 -- and that is the
        // difference from every assertion in the test above, where absence is
        // also what a chain that never ran would produce.
        //
        // Kept as its own test, with its own name, so that a future reader
        // deleting the vacuous assertions above does not take this one with
        // them.
        assertThat(columnType("representatives", "rep_role"))
                .as("representatives.rep_role -- added by V4, dropped by V9. Present here means "
                        + "V9 did not run; the other drop assertions cannot tell you that")
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
