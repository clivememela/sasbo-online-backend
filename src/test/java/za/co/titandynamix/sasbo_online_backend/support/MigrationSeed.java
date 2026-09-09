package za.co.titandynamix.sasbo_online_backend.support;

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
            "V22__membership_number_sequence.sql");

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
