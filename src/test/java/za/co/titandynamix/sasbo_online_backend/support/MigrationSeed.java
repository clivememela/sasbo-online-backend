package za.co.titandynamix.sasbo_online_backend.support;

import java.time.LocalDate;
import java.util.List;

/**
 * The shape of the migration chain, and what it seeds, written down once.
 *
 * <p>These numbers were spread across four files before this existed -- the
 * chain-content assertions, the harness check, the self-test and the reset
 * snapshot -- which is four places for one fact to go stale independently, and
 * the exact shape of drift these plans took from MobiZone, where a floor
 * copy-pasted into four guards went stale in all four and two copies ended up
 * disagreeing with each other.
 *
 * <p>They are a SPECIFICATION, not an observation: a test asserting the database
 * matches them is a real assertion, because the database is not where they come
 * from. Re-derive them from the migrations if they ever change; never edit one
 * to make a red test green.
 *
 * <ul>
 *   <li>{@code union_structures} and {@code representative_roles} are V16's
 *       reseed. Under the incumbent's {@code baseline-version: 0} configuration
 *       V16 was skipped outright and both tables were left empty, which is
 *       exactly why they are worth asserting.
 *   <li>{@code recruiters} is V17's seed.
 * </ul>
 */
public final class MigrationSeed {

    public static final int UNION_STRUCTURES = 19;
    public static final int REPRESENTATIVE_ROLES = 6;
    public static final int RECRUITERS = 33;

    /**
     * {@code acb_rates} is V41's seed: the one flat rate the July 2026 bureau file charged. A run
     * cannot be priced without it, so it is restored after every reset like the other seeds.
     */
    public static final int ACB_RATES = 1;

    /**
     * {@code acb_unpaid_codes} is V45's seed: the four numeric codes on Standard Bank's returns
     * report of 21 August 2026 (2, 4, 30, 34), each mapped to a reason -- the last two wordings
     * inferred and marked so, until question 39 is answered.
     */
    public static final int ACB_UNPAID_CODES = 4;

    /** One statutory public holiday, as V47 seeds it. */
    public record Holiday(LocalDate date, String name) {
        Holiday(String date, String name) {
            this(LocalDate.parse(date), name);
        }
    }

    /**
     * {@code acb_holidays} is V47's seed: South Africa's statutory public holidays for 2026 and 2027 --
     * the Public Holidays Act's schedule, Good Friday and Family Day, and each Monday the Act's Sunday
     * rule makes a holiday, as its own row (D30). THE ONE COPY of the list (F47): the window fixture's
     * {@code za-statutory} set and the tests' calendar ({@code AcbCalendars}) read it, and
     * {@code AcbHolidayCalendarIT} asserts the migrated table equals it. Checked against a printed
     * calendar, and re-derived independently by the planning pack's own script.
     *
     * <p>⚠️ Days declared ad hoc -- the 2026 local-government election day among them -- are not here:
     * they are added as data when declared, which is why the calendar says until when it is known.
     */
    public static final List<Holiday> ACB_HOLIDAYS = List.of(
            new Holiday("2026-01-01", "New Year's Day"),
            new Holiday("2026-03-21", "Human Rights Day"),
            new Holiday("2026-04-03", "Good Friday"),
            new Holiday("2026-04-06", "Family Day"),
            new Holiday("2026-04-27", "Freedom Day"),
            new Holiday("2026-05-01", "Workers' Day"),
            new Holiday("2026-06-16", "Youth Day"),
            new Holiday("2026-08-09", "National Women's Day"),
            new Holiday("2026-08-10", "National Women's Day, observed"),
            new Holiday("2026-09-24", "Heritage Day"),
            new Holiday("2026-12-16", "Day of Reconciliation"),
            new Holiday("2026-12-25", "Christmas Day"),
            new Holiday("2026-12-26", "Day of Goodwill"),
            new Holiday("2027-01-01", "New Year's Day"),
            new Holiday("2027-03-21", "Human Rights Day"),
            new Holiday("2027-03-22", "Human Rights Day, observed"),
            new Holiday("2027-03-26", "Good Friday"),
            new Holiday("2027-03-29", "Family Day"),
            new Holiday("2027-04-27", "Freedom Day"),
            new Holiday("2027-05-01", "Workers' Day"),
            new Holiday("2027-06-16", "Youth Day"),
            new Holiday("2027-08-09", "National Women's Day"),
            new Holiday("2027-09-24", "Heritage Day"),
            new Holiday("2027-12-16", "Day of Reconciliation"),
            new Holiday("2027-12-25", "Christmas Day"),
            new Holiday("2027-12-26", "Day of Goodwill"),
            new Holiday("2027-12-27", "Day of Goodwill, observed"));

    /** {@code acb_calendar}'s one row, V47's: the calendar's name and the last date it is complete for. */
    public static final String ACB_CALENDAR_NAME = "za-statutory";
    public static final LocalDate ACB_CALENDAR_KNOWN_UNTIL = LocalDate.of(2027, 12, 31);

    /** The root of the structure hierarchy, {@code union_structures.id = 1}. */
    public static final String ROOT_STRUCTURE = "SASBO";

    /**
     * Every migration the chain applies, in order, by filename -- the ONE
     * declaration of the chain's shape.
     *
     * <p>🔑 FILENAMES RATHER THAN BARE VERSIONS, because the filename carries
     * the version AND the description, and Flyway derives its own
     * {@code description} column from exactly this text. A list of versions
     * cannot tell {@code V8__drop_removed_member_columns.sql} from some other
     * file renamed to {@code V8__}; this can.
     *
     * <p>🔑 AND IT IS CLOSED ON PURPOSE. It pins two things a
     * {@code containsAtLeast} would let through: the deliberate absence of a
     * V6, and the presence of {@code 1.1}. Do not relax it to make a red test
     * green -- a new migration is a deliberate act, and adding its name here is
     * part of writing it.
     *
     * <p>⚠️ It is a SPECIFICATION and must never be derived from the files on
     * disk. Derived, deleting a migration would delete its own expectation and
     * the suite would stay green over the loss -- which is the one failure this
     * list exists to make loud.
     *
     * <p>{@code V1_1} is a fractional version, not a typo -- see that file's
     * header.
     */
    public static final List<String> MIGRATIONS = List.of(
            "V1__initial_schema.sql",
            "V1_1__baseline_completion.sql",
            "V2__Update_Union_Schema.sql",
            "V3__add_member_email_fields.sql",
            "V4__representative_member_link.sql",
            "V5__representative_area_nullable.sql",
            "V7__add_role_description.sql",
            "V8__drop_removed_member_columns.sql",
            "V9__drop_representatives_name_column.sql",
            "V10__remove_cash_payment_type.sql",
            "V11__job_grade_alphanumeric.sql",
            "V12__add_representative_email_fields.sql",
            "V13__restructure_representative_to_many_structures.sql",
            "V14__add_position_hierarchy_to_union_structures.sql",
            "V15__add_member_date_of_birth.sql",
            "V16__reseed_union_structures_hierarchy.sql",
            "V17__seed_recruiters.sql",
            "V18__recruiter_members_recruited_to_integer.sql",
            "V19__add_cosatu_structure_fields.sql",
            "V20__job_grade_verified_flag.sql",
            "V21__job_grade_bargaining_unit.sql",
            "V22__membership_number_sequence.sql",
            "V23__member_recruitment_channel.sql",
            "V24__member_recruited_area.sql",
            "V25__member_recruiter.sql",
            // SR-5. A PARTIAL unique index -- passport holders have no South African
            // identity number, and the capture form sends "" rather than NULL for a
            // field left alone, so both are excluded or the second passport holder
            // captured is rejected as a duplicate of the first.
            "V26__member_identity_number_unique.sql",
            // main's is_bargaining_council had no migration (C13); derived from V21's
            // bargaining_unit and backfilled, IF NOT EXISTS because production has it.
            "V27__job_grade_bargaining_council.sql",
            // CM-05: Shop Steward / BEC had no column; COSATU Local / Regional as yes/no.
            "V29__member_structure_participation.sql",
            // 🔑 The ACB arc starts at V40, leaving the capture arc room (V28 is reserved for a
            // job_grades unique index). The gap is deliberate and this list is closed, so it is
            // asserted rather than assumed.
            //
            // Step ONE of the money migration: the four member money columns become
            // NUMERIC(12,2) beside the text they came from, which V49 drops later (D16).
            "V40__member_money_columns_to_numeric_step_one.sql",
            // ACB P1: the run and the dated flat rate (V41), its lines (V42), its audit trail (V43).
            // V49 stays reserved for the money migration's second step.
            "V41__acb_runs.sql",
            "V42__acb_run_lines.sql",
            "V43__acb_audit_events.sql",
            // ACB P2: the bureau file a run produced, kept as the evidence of what was sent.
            "V44__acb_run_files.sql",
            // ACB P3: the bank's numeric unpaid codes, mapped to reasons (V45); the letters (V46).
            "V45__acb_unpaid_codes.sql",
            "V46__acb_letters.sql",
            // ACB 01.8: the holiday calendar as data -- acb_holidays and acb_calendar (D30).
            "V47__acb_holidays.sql");

    /**
     * The versions those filenames declare, as Flyway records them in
     * {@code flyway_schema_history.version} — via {@link #versionOf}, which is
     * where the parsing rule is written down.
     *
     * <p>⚠️ It was written out here as well, inside an extraction whose own
     * comment says the rule now exists once. Two statements of one rule, added
     * in the same change that existed to remove exactly that.
     */
    public static final List<String> APPLIED_VERSIONS = MIGRATIONS.stream()
            .map(MigrationSeed::versionOf)
            .toList();

    /**
     * The Flyway version a migration filename declares: the text between the
     * leading {@code V} and the {@code __}, with {@code _} read as the decimal
     * point.
     *
     * <p>🔑 EXTRACTED SO THE RULE EXISTS ONCE. It was an inline lambda here, and
     * a guard that needed the same mapping wrote the identical expression out
     * again -- one rule in two places, agreeing silently until one of them was
     * edited. A repeatable migration prefix, an undo prefix, or a second
     * fractional version has to be handled once.
     *
     * <p>It is a {@code String} rather than a number because a Flyway version is
     * not one: {@code 1.1} is in this chain.
     */
    public static String versionOf(String fileName) {
        return fileName.substring(1, fileName.indexOf("__")).replace('_', '.');
    }

    /**
     * The last migration in the chain, DERIVED rather than restated.
     *
     * <p>It was previously an {@code int} written out a second time, which is
     * one fact in two places and the shape this class exists to prevent. It is
     * a {@code String} because a Flyway version is not a number -- {@code 1.1}
     * is in the chain, and the query that used to read this cast the column to
     * {@code int}, which that value would have made fail.
     */
    public static final String LAST_MIGRATION_VERSION = APPLIED_VERSIONS.getLast();

    private MigrationSeed() {
    }
}
