package za.co.titandynamix.sasbo_online_backend.fixtures;

import org.springframework.jdbc.core.JdbcTemplate;
import za.co.titandynamix.sasbo_online_backend.support.MigrationSeed;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * The ACB run fixture: a membership a run can actually debit.
 *
 * <h2>Why it exists</h2>
 * The seed debits ONE member of 160 -- most seeded debit days are the 1st, most seeded bank names
 * match no bank on record, and most banks have no ACTIVE subscription row. A preparation test run
 * against the seed proves almost nothing, so this builds a population on purpose: banks that each
 * carry exactly one ACTIVE subscription row, and members at them on days inside the window -- plus
 * one member for every reason a run excludes somebody, so the report is exercised as well.
 *
 * <h2>It asserts its own size</h2>
 * A population test that passes on an empty fixture has proved nothing. {@link Built#assertSize}
 * insists on at least fifteen banks with at least two debitable members each before any test uses
 * what was built.
 *
 * <h2>JDBC batches, not JPA saves</h2>
 * Hundreds of members through the entity manager is seconds a lane does not have. One batch per
 * table is milliseconds, and works identically against the fast lane's H2 and the integration
 * lane's PostgreSQL: it names only columns both schemas have.
 *
 * <p>Synthetic values throughout: invented account numbers, collation-neutral surnames.
 */
public final class AcbRunFixture {

    /** Fixed. Nothing that seeds a fixture reads the wall clock. */
    public static final LocalDateTime CREATED = LocalDateTime.of(2026, 9, 1, 9, 0);

    /** The first id each table's rows take, well clear of anything else a test inserts. */
    public static final int FIRST_BANK_ID = 100;
    public static final int FIRST_MEMBER_ID = 1000;

    /** The bank with a PENDING row and no ACTIVE one: SASBO does not collect there. */
    public static final String UNCOLLECTED_BANK = "Fixture Uncollected Bank";

    private AcbRunFixture() {
    }

    /** What was built, and what a run over it should make of it. */
    public record Built(int banks, int debitableMembers, List<Integer> debitableMemberIds,
                        int excludedByName, int payroll, int suspended) {

        /** The fixture's own non-vacuity, asserted before any test relies on it. */
        public Built assertSize() {
            if (banks < 15 || debitableMembers < 2 * 15) {
                throw new AssertionError("The ACB run fixture built " + banks + " banks and "
                        + debitableMembers + " debitable members; every population test needs at "
                        + "least 15 banks with 2 members each, or it can pass having debited nobody.");
            }
            return this;
        }
    }

    /**
     * Builds {@code banks} collectable banks with {@code membersPerBank} debitable members each, and
     * the excluded cast: one member at a bank SASBO does not collect from, one on the 5th, one with a
     * twelve-digit account, one whose bank name matches nothing, two payroll members and one
     * suspended member.
     */
    public static Built insert(JdbcTemplate jdbc, int banks, int membersPerBank) {
        List<Object[]> bankRows = new ArrayList<>();
        List<Object[]> subscriptionRows = new ArrayList<>();
        for (int b = 0; b < banks; b++) {
            int bankId = FIRST_BANK_ID + b;
            bankRows.add(new Object[] {bankId, bankName(b), String.format("%06d", 250000 + b)});
            subscriptionRows.add(new Object[] {(long) bankId, bankId, 25, new BigDecimal("174.00"), false,
                    "ACTIVE", CREATED});
        }
        int uncollectedId = FIRST_BANK_ID + banks;
        bankRows.add(new Object[] {uncollectedId, UNCOLLECTED_BANK, "470010"});
        subscriptionRows.add(new Object[] {(long) uncollectedId, uncollectedId, 25, new BigDecimal("174.00"),
                false, "PENDING", CREATED});

        jdbc.batchUpdate("INSERT INTO banks (id, name, universal_branch_code) VALUES (?, ?, ?)", bankRows);
        jdbc.batchUpdate("INSERT INTO subscriptions (id, bank_id, acb_debit_day, subscription_amount, "
                + "core_banking, status, registration_date) VALUES (?, ?, ?, ?, ?, ?, ?)", subscriptionRows);

        List<Object[]> members = new ArrayList<>();
        List<Integer> debitable = new ArrayList<>();
        int id = FIRST_MEMBER_ID;
        for (int b = 0; b < banks; b++) {
            for (int m = 0; m < membersPerBank; m++) {
                int day = 20 + (id % 12);
                members.add(member(id, "Active", "Debit Order", bankName(b), account(id), day));
                debitable.add(id);
                id++;
            }
        }
        // The excluded cast, one per listed reason, then the counted ones.
        members.add(member(id, "Active", "Debit Order", UNCOLLECTED_BANK, account(id), 25));
        id++;
        members.add(member(id, "Active", "Debit Order", bankName(0), account(id), 5));
        id++;
        members.add(member(id, "Active", "Debit Order", bankName(0), "123456789012", 25));
        id++;
        members.add(member(id, "Active", "Debit Order", "Fixture Bank Nobody Has", account(id), 25));
        id++;
        members.add(member(id, "Active", "Payroll Deduction", bankName(0), account(id), 25));
        id++;
        members.add(member(id, "Active", "Payroll Deduction", bankName(1), account(id), 25));
        id++;
        members.add(member(id, "Suspended", "Debit Order", bankName(0), account(id), 25));

        jdbc.batchUpdate("INSERT INTO members (id, membership_number, surname, initials, status, "
                + "payment_type, bank_name, bank_account_number, debit_day, account_type, created_at, updated_at) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)", members);

        return new Built(banks, debitable.size(), List.copyOf(debitable), 4, 2, 1);
    }

    /** The name of collectable bank {@code b}. Two digits, so they sort as they are numbered. */
    public static String bankName(int b) {
        return String.format("Fixture Bank %02d", b);
    }

    /** A synthetic ten-digit account number, unique per member. */
    public static String account(int memberId) {
        return String.format("62%08d", memberId);
    }

    /** The membership number the fixture gives member {@code id}: eight characters, like M-100001. */
    public static String membershipNumber(int memberId) {
        return String.format("F-%06d", memberId);
    }

    private static Object[] member(int id, String status, String paymentType, String bank, String account, int day) {
        // Every fixture member carries a type (00.5): the excluded cast fails an earlier rule, as before.
        return new Object[] {id, membershipNumber(id), "Surname" + id, "AB", status, paymentType, bank, account,
                day, "Cheque", CREATED, CREATED};
    }

    /** Inserts the flat rate a run needs -- the integration lane has it from V41; H2 does not. */
    public static void insertRate(JdbcTemplate jdbc, LocalDate effectiveFrom, BigDecimal amount) {
        jdbc.update("INSERT INTO acb_rates (effective_from, amount, source) VALUES (?, ?, ?)",
                effectiveFrom, amount, "fixture");
    }

    /**
     * What V47 seeds, for a database Flyway never ran on -- the fast lane's H2, built from the entities:
     * the statutory holidays from {@link MigrationSeed#ACB_HOLIDAYS} (the one copy, F47) and the
     * calendar's row. Without it every preparation there is refused {@code HOLIDAYS_NOT_KNOWN}, as it
     * should be.
     */
    public static void insertCalendar(JdbcTemplate jdbc) {
        jdbc.batchUpdate("INSERT INTO acb_holidays (holiday_date, name, source) VALUES (?, ?, 'STATUTORY')",
                MigrationSeed.ACB_HOLIDAYS.stream().map(h -> new Object[] {h.date(), h.name()}).toList());
        jdbc.update("INSERT INTO acb_calendar (id, name, known_until) VALUES (1, ?, ?)",
                MigrationSeed.ACB_CALENDAR_NAME, MigrationSeed.ACB_CALENDAR_KNOWN_UNTIL);
    }
}
