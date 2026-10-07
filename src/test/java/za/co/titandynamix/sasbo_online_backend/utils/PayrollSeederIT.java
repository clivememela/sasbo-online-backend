package za.co.titandynamix.sasbo_online_backend.utils;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import za.co.titandynamix.sasbo_online_backend.config.SasboEnvironment;
import za.co.titandynamix.sasbo_online_backend.config.WorkplaceSwitch;
import za.co.titandynamix.sasbo_online_backend.enums.PayrollDiscrepancyType;
import za.co.titandynamix.sasbo_online_backend.payroll.PayrollMoney;
import za.co.titandynamix.sasbo_online_backend.repository.MemberRepository;
import za.co.titandynamix.sasbo_online_backend.repository.PayrollDiscrepancyRepository;
import za.co.titandynamix.sasbo_online_backend.repository.PayrollEmployerRepository;
import za.co.titandynamix.sasbo_online_backend.repository.PayrollStore;
import za.co.titandynamix.sasbo_online_backend.repository.SeedMemberRepository;
import za.co.titandynamix.sasbo_online_backend.services.PayrollDiscrepancies;
import za.co.titandynamix.sasbo_online_backend.services.PayrollFiles;
import za.co.titandynamix.sasbo_online_backend.services.PayrollPayments;
import za.co.titandynamix.sasbo_online_backend.services.PayrollQueries;
import za.co.titandynamix.sasbo_online_backend.support.AbstractSwitchedOnIT;
import za.co.titandynamix.sasbo_online_backend.support.PayrollItSupport;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The payroll seed on PostgreSQL, at the switched-on lane's clock (15 September 2026): the sixteen employers; the
 * demonstration's members; and for Absa, Capitec, FNB &amp; Wesbank and Standard Bank two months of files in their
 * own layouts, GBS's typed in -- last month's carrying every kind of discrepancy its columns can carry (the parent's
 * requirement for the deck: a processed file per seeded layout, every type somewhere), the month before worked. A
 * second start loads nothing; under the production marker only the employers are seeded.
 */
@DisplayName("The payroll seed, on PostgreSQL")
class PayrollSeederIT extends AbstractSwitchedOnIT {

    /** What a spreadsheet without ID numbers or a stated total can show: all but DETAILS_DIFFER and the total. */
    private static final Set<String> EVERY_KIND_A_SPREADSHEET_CARRIES = Set.of("NOT_A_MEMBER", "MEMBERSHIP_ENDED",
            "MISSING_FROM_FILE", "AMOUNT_DIFFERS", "DEDUCTED_TWICE", "POSSIBLE_CATCH_UP", "EMPLOYEE_NUMBER_DIFFERS",
            "NAME_DIFFERS", "DEBIT_ORDER_MEMBER", "UNREADABLE_ROW", "PAYMENT_DIFFERS");

    @Autowired
    private PayrollSeeder seeder;

    @Autowired
    private PayrollEmployerRepository employerRepository;

    @Autowired
    private SeedMemberRepository seedMemberRepository;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private PayrollDiscrepancyRepository discrepancyRepository;

    @Autowired
    private PayrollStore store;

    @Autowired
    private PayrollFiles files;

    @Autowired
    private PayrollDiscrepancies discrepancies;

    @Autowired
    private PayrollQueries queries;

    @Autowired
    private PayrollPayments payments;

    @Autowired
    private NamedParameterJdbcTemplate namedJdbc;

    @Autowired
    private WorkplaceSwitch workplaceSwitch;

    @Autowired
    private Clock clock;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    @DisplayName("every seeded layout's last month carries every kind its columns can carry; the month before is worked")
    void theDemonstration() throws Exception {
        // The debit-order population a stack already holds, one member whose record names each employer (PAY-29).
        PayrollItSupport.insert(jdbc, List.of(
                debitOrder("D-00001", "EMP-90001", "8101015800087", "Debitwell", "ABSA BANK LIMITED"),
                debitOrder("D-00002", "EMP-90002", "8202025800081", "Debitmore", "CAPITEC BANK LIMITED"),
                debitOrder("D-00003", "EMP-90003", "8303035800085", "Debitfair", "FIRST NATIONAL BANK (FNB)"),
                debitOrder("D-00004", "EMP-90004", "8404045800089", "Debitgood",
                        "THE STANDARD BANK OF SOUTH AFRICA LIMITED")));
        assertThat(seeder.seedEmployers()).isEqualTo(16);
        assertThat(seeder.seedDemonstration()).as("files loaded: two months for four employers, one typed for GBS")
                .isEqualTo(9);

        Map<String, Set<String>> lastMonth = kinds("2026-08-01");
        for (String employer : List.of("ABSA", "CAPITEC", "STANDARD_BANK")) {
            assertThat(lastMonth.get(employer)).as(employer).isEqualTo(EVERY_KIND_A_SPREADSHEET_CARRIES);
        }
        Set<String> fnb = new TreeSet<>(EVERY_KIND_A_SPREADSHEET_CARRIES);
        fnb.add("DETAILS_DIFFER");
        assertThat(lastMonth.get("FNB_WESBANK")).as("FNB prints ID numbers").isEqualTo(fnb);
        assertThat(lastMonth.get("GBS")).as("GBS's letter, typed in").isEqualTo(Set.of("NOT_A_MEMBER",
                "MEMBERSHIP_ENDED", "MISSING_FROM_FILE", "AMOUNT_DIFFERS", "DEDUCTED_TWICE", "FILE_TOTAL_DIFFERS",
                "PAYMENT_DIFFERS"));
        assertThat(lastMonth.values().stream().flatMap(Set::stream).collect(Collectors.toSet()))
                .as("every type of discrepancy, somewhere in last month's files")
                .isEqualTo(Arrays.stream(PayrollDiscrepancyType.values()).map(Enum::name).collect(Collectors.toSet()));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM payroll_discrepancies d JOIN payroll_files f ON f.id = "
                + "d.file_id WHERE f.period = DATE '2026-08-01' AND d.status <> 'OPEN'", Integer.class))
                .as("last month is left for the demonstration").isZero();

        // The catch-ups told apart from double deductions: exactly the members planted for them (PAY-26, PAY-25).
        Set<String> plantedCatchUps = PayrollSeeder.readSeedFile().members().stream()
                .filter(m -> Set.of("RETRO", "MISSED_THEN_CAUGHT_UP", "CATCH_UP_ARREARS", "ADJUSTMENT_CATCH_UP")
                        .contains(m.role()))
                .map(PayrollSeedMember::membershipNumber).collect(Collectors.toSet());
        assertThat(plantedCatchUps).hasSize(5);
        assertThat(new TreeSet<>(jdbc.queryForList("SELECT DISTINCT m.membership_number FROM payroll_matches pm "
                + "JOIN payroll_files f ON f.id = pm.file_id JOIN members m ON m.id = pm.member_id "
                + "WHERE f.period = DATE '2026-08-01' AND pm.amount_status = 'CATCH_UP'", String.class)))
                .isEqualTo(new TreeSet<>(plantedCatchUps));

        // The month before, worked: a resolution, a query raised, notes, and the money in -- Absa's less its 3% fee.
        assertThat(jdbc.queryForObject("SELECT count(*) FROM payroll_discrepancies d JOIN payroll_files f ON f.id = "
                + "d.file_id WHERE f.period = DATE '2026-07-01' AND d.status = 'RESOLVED' AND d.outcome = "
                + "'DEDUCTION_RESTARTED'", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM payroll_queries WHERE period = DATE '2026-07-01'",
                Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM payroll_discrepancy_notes", Integer.class))
                .as("Absa's not-a-member and Standard Bank's ended membership").isGreaterThanOrEqualTo(2);
        BigDecimal absaTotal = jdbc.queryForObject("SELECT f.file_total FROM payroll_files f JOIN payroll_employers e "
                + "ON e.id = f.payroll_employer_id WHERE e.code = 'ABSA' AND f.period = DATE '2026-07-01'", BigDecimal.class);
        BigDecimal absaPaid = jdbc.queryForObject("SELECT sum(p.amount) FROM payroll_payments p JOIN payroll_employers e "
                + "ON e.id = p.payroll_employer_id WHERE e.code = 'ABSA' AND p.period = DATE '2026-07-01'", BigDecimal.class);
        assertThat(absaPaid).isLessThan(absaTotal)
                .isEqualByComparingTo(PayrollMoney.expectedPayment(absaTotal, new BigDecimal("3.00")));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM payroll_discrepancies d JOIN payroll_files f ON f.id = "
                + "d.file_id WHERE f.period = DATE '2026-07-01' AND d.type = 'PAYMENT_DIFFERS'", Integer.class))
                .as("the month before was paid as expected").isZero();

        // A second start: the members on record, the files loaded -- nothing twice.
        int members = jdbc.queryForObject("SELECT count(*) FROM members", Integer.class);
        assertThat(seeder.seedEmployers()).isZero();
        assertThat(seeder.seedDemonstration()).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM members", Integer.class)).isEqualTo(members);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM payroll_files", Integer.class)).isEqualTo(9);
    }

    @Test
    @DisplayName("🔒 under the production marker the employers are seeded -- they are reference data -- and nothing invented")
    void productionGetsTheEmployersOnly() throws Exception {
        PayrollSeeder production = new PayrollSeeder(employerRepository, seedMemberRepository, memberRepository,
                discrepancyRepository, store, files, discrepancies, queries, payments, namedJdbc,
                new SasboEnvironment(SasboEnvironment.PRODUCTION), workplaceSwitch, clock, transactionManager);
        assertThat(production.seedEmployers()).isEqualTo(16);
        assertThat(production.seedMembers()).isZero();
        assertThat(production.seedDemonstration()).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM members", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM payroll_files", Integer.class)).isZero();
    }

    /** Each employer's kinds of discrepancy on its file of the month. */
    private Map<String, Set<String>> kinds(String firstOfMonth) {
        Map<String, Set<String>> kinds = new HashMap<>();
        jdbc.query("SELECT e.code, d.type FROM payroll_discrepancies d JOIN payroll_files f ON f.id = d.file_id "
                + "JOIN payroll_employers e ON e.id = f.payroll_employer_id WHERE f.period = CAST(? AS DATE)", rs -> {
                    kinds.computeIfAbsent(rs.getString(1), k -> new TreeSet<>()).add(rs.getString(2));
                }, firstOfMonth);
        return kinds;
    }

    private static PayrollItSupport.MemberRow debitOrder(String number, String staff, String id, String surname,
                                                         String bank) {
        return new PayrollItSupport.MemberRow(number, staff, id, "Mr", "DO", "Debo Ordo", surname, "Active",
                "Debit Order", bank, BigDecimal.ZERO);
    }
}
