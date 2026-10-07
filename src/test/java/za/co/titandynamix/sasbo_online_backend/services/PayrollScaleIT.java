package za.co.titandynamix.sasbo_online_backend.services;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import za.co.titandynamix.sasbo_online_backend.dto.payroll.PayrollFileSummaryDto;
import za.co.titandynamix.sasbo_online_backend.entity.payroll.PayrollEmployer;
import za.co.titandynamix.sasbo_online_backend.enums.PayrollDelivery;
import za.co.titandynamix.sasbo_online_backend.payroll.PayrollLayout;
import za.co.titandynamix.sasbo_online_backend.payroll.PayrollLayouts;
import za.co.titandynamix.sasbo_online_backend.payroll.PayrollLimits;
import za.co.titandynamix.sasbo_online_backend.repository.PayrollEmployerRepository;
import za.co.titandynamix.sasbo_online_backend.support.AbstractSwitchedOnIT;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Payroll at the size SASBO's largest employer sends, on PostgreSQL: a file of {@link PayrollLimits#MAX_ROWS} lines
 * compared against 100,000 members on record -- loaded, matched and written within {@link #BOUND}, then compared again
 * within it. The comparison reads the employer's members in one statement and the ID numbers it does not hold a
 * thousand to a statement; it writes in batches of a thousand. A statement per line would not fit.
 *
 * <p>The members are invented in one statement: 22,000 at the test's employer by payroll deduction, 78,000 elsewhere.
 * The file names 19,700 of the employer's by staff number, 200 by ID number with another staff number, 50 members of
 * other employers by ID number, and 50 people who are no members; so 2,100 of the employer's are missing from it.
 */
@DisplayName("Payroll at scale: 20,000 lines against 100,000 members, on PostgreSQL")
class PayrollScaleIT extends AbstractSwitchedOnIT {

    /** The stated time for a month's largest file, on the CI runner: loaded and compared, or compared again. */
    static final Duration BOUND = Duration.ofSeconds(60);

    private static final int MEMBERS = 100_000;
    private static final int AT_THE_EMPLOYER = 22_000;
    private static final int BY_STAFF_NUMBER = 19_700;
    private static final int BY_ID_NUMBER = 200;
    private static final int ELSEWHERE = 50;
    private static final int STRANGERS = 50;

    @Autowired
    private PayrollFiles files;

    @Autowired
    private PayrollEmployerRepository employerRepository;

    @Test
    @DisplayName("a 20,000-line file against 100,000 members: loaded and compared, and compared again, within the bound")
    void twentyThousandLinesAgainstAHundredThousandMembers() {
        long employer = theEmployer();
        jdbc.update("INSERT INTO members (membership_number, employee_number, title, initials, first_names, surname, "
                + "identity_type, identity_number, status, join_date, bank_name, payment_type, arrears_balance_numeric, "
                + "created_at, updated_at) "
                + "SELECT 'S-' || lpad(g::text, 6, '0'), (5000000 + g)::text, 'Mr', 'PQ', 'Perry Quill', "
                + "'Scale' || translate(g::text, '0123456789', 'abcdefghij'), 'I.D. Number', (7001010000000 + g)::text, "
                + "'Active', DATE '2015-03-01', CASE WHEN g <= " + AT_THE_EMPLOYER + " THEN 'SCALE BANK' ELSE "
                + "'ELSEWHERE BANK' END, 'Payroll Deduction', 0.00, TIMESTAMP '2026-09-01 08:00:00', "
                + "TIMESTAMP '2026-09-01 08:00:00' FROM generate_series(1, " + MEMBERS + ") AS g");
        byte[] file = theFile();
        assertThat((long) file.length).isLessThan(PayrollLimits.MAX_BYTES);

        long started = System.nanoTime();
        PayrollFileSummaryDto loaded = files.upload(employer, "2026-09", "scale.csv", file, false, null, "an IT")
                .summary();
        Duration load = Duration.ofNanos(System.nanoTime() - started);

        PayrollFileSummaryDto.Counts counts = loaded.counts();
        assertThat(counts.rowsRead()).isEqualTo(PayrollLimits.MAX_ROWS);
        assertThat(counts.matchedByStaffNumber()).isEqualTo(BY_STAFF_NUMBER);
        assertThat(counts.matchedByIdNumber()).isEqualTo(BY_ID_NUMBER + ELSEWHERE);
        assertThat(counts.matchedByName()).isZero();
        assertThat(counts.notMembers()).isEqualTo(STRANGERS);
        assertThat(counts.missingMembers()).isEqualTo(AT_THE_EMPLOYER - BY_STAFF_NUMBER - BY_ID_NUMBER);
        assertThat(kinds(loaded.file().id())).isEqualTo(Map.of(
                "EMPLOYEE_NUMBER_DIFFERS", (long) BY_ID_NUMBER + ELSEWHERE,
                "DETAILS_DIFFER", (long) ELSEWHERE,
                "NOT_A_MEMBER", (long) STRANGERS,
                "MISSING_FROM_FILE", (long) AT_THE_EMPLOYER - BY_STAFF_NUMBER - BY_ID_NUMBER));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM payroll_matches WHERE file_id = ?", Integer.class,
                loaded.file().id())).isEqualTo(PayrollLimits.MAX_ROWS);
        assertThat(load).as("loaded and compared in %s", load).isLessThan(BOUND);

        started = System.nanoTime();
        PayrollFileSummaryDto again = files.reconcile(loaded.file().id(), null, "an IT");
        Duration reconcile = Duration.ofNanos(System.nanoTime() - started);
        assertThat(again.counts()).isEqualTo(counts);
        assertThat(kinds(loaded.file().id())).containsEntry("MISSING_FROM_FILE",
                (long) AT_THE_EMPLOYER - BY_STAFF_NUMBER - BY_ID_NUMBER);
        assertThat(reconcile).as("compared again in %s", reconcile).isLessThan(BOUND);
    }

    /** An employer of the test's own, reading the file by the general headings, its members' records naming it. */
    private long theEmployer() {
        PayrollLayout general = PayrollLayouts.byKey(PayrollLayouts.GENERAL).orElseThrow();
        PayrollEmployer employer = new PayrollEmployer();
        employer.setCode("SCALE");
        employer.setName("Scale Bank");
        employer.setOrdinal(99);
        employer.setDelivery(PayrollDelivery.EMAIL);
        employer.setReadable(true);
        employer.setLayoutKey(general.key());
        employer.setLayoutVersion(general.version());
        employer.setLayout(general.toJson());
        employer.setAdminFeePercent(new BigDecimal("0.00"));
        long id = employerRepository.saveAndFlush(employer).getId();
        jdbc.update("INSERT INTO payroll_employer_names (payroll_employer_id, name_key) VALUES (?, 'SCALE BANK')", id);
        return id;
    }

    /** The CSV the employer sends: the general headings, then 20,000 lines. */
    private static byte[] theFile() {
        StringBuilder csv = new StringBuilder(1_200_000).append("Staff Number,ID Number,Initials,Surname,Amount\r\n");
        int line = 0;
        for (int g = 1; g <= BY_STAFF_NUMBER; g++) {
            csv.append(5_000_000 + g).append(',').append(7_001_010_000_000L + g).append(",PQ,").append(surname(g))
                    .append(",174.00\r\n");
            line++;
        }
        for (int g = BY_STAFF_NUMBER + 1; g <= BY_STAFF_NUMBER + BY_ID_NUMBER; g++) {
            csv.append("X").append(g).append(',').append(7_001_010_000_000L + g).append(",PQ,").append(surname(g))
                    .append(",174.00\r\n");
            line++;
        }
        for (int g = 50_001; g <= 50_000 + ELSEWHERE; g++) {
            csv.append("Y").append(g).append(',').append(7_001_010_000_000L + g).append(",PQ,").append(surname(g))
                    .append(",174.00\r\n");
            line++;
        }
        for (int s = 1; s <= STRANGERS; s++) {
            csv.append("Z").append(s).append(',').append(8_001_010_000_000L + s).append(",ZZ,Strangerkey,174.00\r\n");
            line++;
        }
        assertThat(line).isEqualTo(PayrollLimits.MAX_ROWS);
        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    /** The surname the statement gives member {@code g}: its digits as letters. */
    private static String surname(int g) {
        StringBuilder name = new StringBuilder("Scale");
        for (char digit : Integer.toString(g).toCharArray()) {
            name.append((char) ('a' + (digit - '0')));
        }
        return name.toString();
    }

    private Map<String, Long> kinds(long fileId) {
        Map<String, Long> kinds = new HashMap<>();
        jdbc.query("SELECT type, count(*) FROM payroll_discrepancies WHERE file_id = ? AND status <> 'RESOLVED' "
                + "GROUP BY type", rs -> {
                    kinds.put(rs.getString(1), rs.getLong(2));
                }, fileId);
        return kinds;
    }
}
