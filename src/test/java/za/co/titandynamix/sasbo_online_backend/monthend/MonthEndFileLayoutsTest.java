package za.co.titandynamix.sasbo_online_backend.monthend;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import za.co.titandynamix.sasbo_online_backend.fixtures.MemberFixture;
import za.co.titandynamix.sasbo_online_backend.monthend.MonthEndFileLayouts.AbsaFile;
import za.co.titandynamix.sasbo_online_backend.monthend.MonthEndFileLayouts.Counts;
import za.co.titandynamix.sasbo_online_backend.monthend.MonthEndFileLayouts.CsvFile;
import za.co.titandynamix.sasbo_online_backend.repository.MonthEndListingsRepository.MemberLine;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The layouts of the files that leave SASBO (October build), byte for byte, from invented members only -- above all
 * Absa's record as measured from Stephne's example (MEN-34, the answers' attachments section 2): the employee number at
 * 1-8, the surname at 9-28, the initials at 29-31, the title at 32-35, the digit 4 at 36, three spaces.
 */
@DisplayName("The files' layouts")
class MonthEndFileLayoutsTest {

    /** An invented member, as a file names one. */
    static MemberLine member(String number, String title, String initials, String firstNames, String surname,
                             String employee) {
        return new MemberLine(1L, number, title, initials, firstNames, surname, employee,
                MemberFixture.syntheticIdFor(77), null, "1 Invented Road, Madeup Town, 0000", LocalDate.of(2020, 3, 1),
                "Payroll Deduction", "Monthly", new BigDecimal("174.00"), 990001, "INVENTED MAIN STREET", "A", "NORTH",
                901, "INVENTED BANK", "NEW", null, null, null, null);
    }

    /** The golden file: three members Absa's record can carry, and three it cannot. */
    static final List<MemberLine> ABSA_MEMBERS = List.of(
            member("M-990001", "Mr", "TB", "Thabo Bongani", "Fictitious", "12345678"),
            member("M-990002", "Prof", null, "Anna Maria", "Inventedsurnamethatislong", "7654321"),
            member("M-990003", "Dr.", "j.p.", "Jan Pieter", "Éxample-O'Neil", " 00042 "),
            member("M-990004", "Ms", "K", "Kim", "Madeup", "EMP-99850"),
            member("M-990005", "Mr", "L", "Lee", "Madeup", "123456789"),
            member("M-990006", "Mrs", "N", "Nomsa", "  ", "11112222"));

    /** What Absa's file holds for them: CR LF after each record, nothing before the first or after the last. */
    static final String ABSA_GOLDEN =
            "12345678" + "FICTITIOUS          " + "TB " + "MR  " + "4" + "   " + "\r\n"
            + "07654321" + "INVENTEDSURNAMETHATI" + "AM " + "PROF" + "4" + "   " + "\r\n"
            + "00000042" + "EXAMPLE-O'NEIL      " + "JP " + "DR  " + "4" + "   " + "\r\n";

    @Test
    @DisplayName("MEN-34: Absa's file, byte for byte -- zero-filled, cut, unaccented, the 4 at 36, three spaces, CR LF")
    void absaGolden() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        AbsaFile absa = new AbsaFile(out, Set.of());
        ABSA_MEMBERS.forEach(absa);
        Counts counts = absa.finish();

        assertThat(out.toByteArray()).isEqualTo(ABSA_GOLDEN.getBytes(StandardCharsets.US_ASCII));
        assertThat(counts).isEqualTo(new Counts(3, 0, 0, 3));
    }

    @Test
    @DisplayName("every Absa record is 39 characters, the digit 4 at position 36 and three spaces after it")
    void absaPositions() {
        for (String record : ABSA_GOLDEN.split("\r\n")) {
            assertThat(record).hasSize(MonthEndFileLayouts.ABSA_RECORD_LENGTH);
            assertThat(record.substring(0, 8)).containsOnlyDigits();
            assertThat(record.charAt(35)).isEqualTo(MonthEndFileLayouts.ABSA_RECORD_MARK);
            assertThat(record.substring(36)).isEqualTo("   ");
        }
    }

    @Test
    @DisplayName("Absa's record cannot carry an employee number that is not one to eight digits, or no surname: left out, counted")
    void absaLeavesOut() {
        assertThat(MonthEndFileLayouts.absaRecord(ABSA_MEMBERS.get(3))).isNull();
        assertThat(MonthEndFileLayouts.absaRecord(ABSA_MEMBERS.get(4))).isNull();
        assertThat(MonthEndFileLayouts.absaRecord(ABSA_MEMBERS.get(5))).isNull();
        assertThat(MonthEndFileLayouts.absaRecord(ABSA_MEMBERS.get(0))).isNotNull();
    }

    @Test
    @DisplayName("a member named to be left out -- a seeded member where the production marker is set -- is never written")
    void absaLeaveOutSet() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        AbsaFile absa = new AbsaFile(out, Set.of("M-990001"));
        absa.accept(ABSA_MEMBERS.get(0));
        absa.accept(ABSA_MEMBERS.get(1));

        assertThat(absa.finish()).isEqualTo(new Counts(1, 0, 0, 1));
        assertThat(out.toString(StandardCharsets.US_ASCII)).startsWith("07654321").doesNotContain("FICTITIOUS");
    }

    @Test
    @DisplayName("layout D, the full list: SASBO's Miscellaneous Extract, column for column")
    void fullListRow() {
        MemberLine m = ABSA_MEMBERS.get(0);
        assertThat(MonthEndFileLayouts.FULL_LIST_COLUMNS).containsExactly("Title", "Initial", "Name",
                "Membership Number", "Employee Number", "Bank Name", "ID Number", "SUB");
        assertThat(MonthEndFileLayouts.fullListRow(m)).containsExactly("MR", "TB", "FICTITIOUS", "M-990001", "12345678",
                "INVENTED BANK", MemberFixture.syntheticIdFor(77), "174.00");
        assertThat(MonthEndFileLayouts.fullListTitle(LocalDate.of(2026, 9, 30)))
                .isEqualTo("MISCELLANEOUS EXTRACT - 2026/09/30");
    }

    @Test
    @DisplayName("layout A, new and deleted: SASBO's standard, Class NEW or DEL")
    void newAndDeletedRow() {
        MemberLine m = ABSA_MEMBERS.get(0);
        assertThat(MonthEndFileLayouts.NEW_AND_DELETED_COLUMNS).containsExactly("Class", "Title", "Initial", "Name",
                "Employee Number", "Membership Number", "ID Number", "Branch Name");
        assertThat(MonthEndFileLayouts.newAndDeletedRow(m)).containsExactly("NEW", "MR", "TB", "FICTITIOUS",
                "12345678", "M-990001", MemberFixture.syntheticIdFor(77), "INVENTED MAIN STREET");
        assertThat(MonthEndFileLayouts.newAndDeletedTitle("Invented Bank", LocalDate.of(2026, 9, 30)))
                .isEqualTo("INVENTED BANK NEW AND DELETED MEMBERS - 2026/09/30");
    }

    @Test
    @DisplayName("MEN-35: Old Mutual's 24 headings in her order, each value space-filled to the width her rows show")
    void oldMutual() {
        assertThat(MonthEndFileLayouts.OLD_MUTUAL_COLUMNS).hasSize(24);
        assertThat(MonthEndFileLayouts.OLD_MUTUAL_WIDTHS).hasSize(24);
        assertThat(MonthEndFileLayouts.OLD_MUTUAL_COLUMNS).containsAll(MonthEndFileLayouts.OLD_MUTUAL_READ_HEADINGS);
        assertThat(MonthEndFileLayouts.OLD_MUTUAL_COLUMNS.subList(2, 6)).containsExactly("Initials", "Surname",
                "FirstNames", "Employer");
        List<String> row = MonthEndFileLayouts.oldMutualRow(ABSA_MEMBERS.get(0));
        assertThat(row).hasSize(24);
        assertThat(row.get(3)).isEqualTo("FICTITIOUS          ").hasSize(20);
        assertThat(row.get(4)).hasSize(33).startsWith("THABO BONGANI");
        assertThat(row.get(5)).hasSize(30).startsWith("INVENTED BANK");
        assertThat(row.get(10)).isEqualTo(MonthEndFileLayouts.field("1 INVENTED ROAD", 30));
        assertThat(row.get(11)).isEqualTo(MonthEndFileLayouts.field("MADEUP TOWN", 25));
        assertThat(row.get(12)).isEqualTo(MonthEndFileLayouts.field("0000", 20));
        assertThat(row.get(14)).isEqualTo("P");
        assertThat(row.get(15)).isEqualTo(MemberFixture.syntheticIdFor(77));
        assertThat(row.get(16)).as("an employee number longer than her rows' seven is never cut").isEqualTo("12345678");
        assertThat(row.get(17)).isEqualTo("M-990001");
        assertThat(row.subList(19, 24)).containsOnly("");
    }

    @Test
    @DisplayName("a CSV cell is quoted where it must be and never runs as a formula; CR LF after each row")
    void csv() {
        assertThat(MonthEndFileLayouts.csvCell("=1+1")).isEqualTo("'=1+1");
        assertThat(MonthEndFileLayouts.csvCell("Madeup, Town")).isEqualTo("\"Madeup, Town\"");
        assertThat(MonthEndFileLayouts.csvCell("say \"hi\"")).isEqualTo("\"say \"\"hi\"\"\"");
        assertThat(MonthEndFileLayouts.csvCell(null)).isEmpty();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        CsvFile csv = new CsvFile(out, List.of("Class", "Name"));
        csv.row(List.of("NEW", "Fictitious, Thabo"));
        csv.finish();
        assertThat(out.toString(StandardCharsets.UTF_8)).isEqualTo("Class,Name\r\nNEW,\"Fictitious, Thabo\"\r\n");
        assertThat(csv.rows()).isEqualTo(1);
    }

    @Test
    @DisplayName("a member's address as three lines: its parts, the rest in the third")
    void addressLines() {
        assertThat(MonthEndFileLayouts.addressLines("1 Invented Road, Madeup Town, Region, 0000"))
                .containsExactly("1 Invented Road", "Madeup Town", "Region, 0000");
        assertThat(MonthEndFileLayouts.addressLines(null)).containsExactly("", "", "");
    }
}
