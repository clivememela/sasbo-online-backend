package za.co.titandynamix.sasbo_online_backend.acb.file;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import za.co.titandynamix.sasbo_online_backend.exception.AcbRefusal;
import za.co.titandynamix.sasbo_online_backend.exception.AcbRefusalCode;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The bureau file, byte for byte.
 *
 * <h2>The golden file is written from the layout, not from the writer</h2>
 * {@link #golden()} is typed field by field from the planning pack's measured layout of the July
 * 2026 file -- each record a list of literal fields with their widths -- for a synthetic run of three
 * debits on two action dates, one of them a re-presentation. Generating the expectation by running
 * the writer would make this test a comparison of the writer with itself. Synthetic values only:
 * no member, no account and no union identifier from the real file appears here.
 *
 * <h2>And the arithmetic is re-derived independently</h2>
 * The trailer's hash total -- the last twelve digits of the sum of every homing account number,
 * contras included -- is computed below with {@link BigInteger} over the same numbers, so the rule
 * is asserted, not merely the writer's answer to it.
 */
@DisplayName("BureauFileWriter -- the file Standard Bank accepts, byte for byte")
class BureauFileWriterTest {

    static final BureauFileSettings SETTINGS = new BureauFileSettings(
            "SASBO", "9876", "123456", "10020030040", "1111122222", "1001", "000018", "SASBO", "TWO DAY", "BEFT");

    static final BureauFileWriter.Header HEADER = new BureauFileWriter.Header(
            LocalDate.of(2026, 10, 17), LocalDate.of(2026, 11, 9),
            LocalDate.of(2026, 10, 20), LocalDate.of(2026, 10, 30));

    static BureauFileLine botha() {
        return new BureauFileLine(11L, "632005", "1234567890", 1, new BigDecimal("174.00"),
                LocalDate.of(2026, 10, 20), "M-100116", "Botha", "AB", null);
    }

    static BureauFileLine naidoo() {
        return new BureauFileLine(12L, "051001", "98765432101", 2, new BigDecimal("174.00"),
                LocalDate.of(2026, 10, 20), "1234567", "Naidoo", "K", null);
    }

    /** A re-presentation of a September unpaid, at September's amount, on the 30th. */
    static BureauFileLine vanDerMerwe() {
        return new BureauFileLine(13L, "470010", "555", 3, new BigDecimal("170.00"),
                LocalDate.of(2026, 10, 30), "M-200001", "Van der Merwe", "JPL", YearMonth.of(2026, 9));
    }

    static List<BureauFileLine> lines() {
        return List.of(botha(), naidoo(), vanDerMerwe());
    }

    /** One record from its fields, which must add up to 180 characters exactly. */
    private static String record(String... fields) {
        String record = String.join("", fields);
        assertThat(record).as("a golden record's own width -- a typo in the expectation").hasSize(180);
        return record;
    }

    private static String sp(int n) {
        return " ".repeat(n);
    }

    static String golden() {
        String header02Body = "1001" + "SASBO " + "1111122222" + "261017" + "261109" + "000018" + "000180" + "BEFT";
        List<String> records = List.of(
                record("02", header02Body, sp(130)),
                record("04", "9876", "261017", "261109", "261020", "261030", "000001", "0000", "TWO DAY   ", sp(130)),
                record("50", "123456", "10020030040", "9876", "000001", "632005", "01234567890", "1", "00000017400",
                        "261020", "35", "0000", "SASBO     ", "M-100116", "C  ", " 0", "SUBSCRIP",
                        "BOTHA" + sp(15), "AB  ", sp(41), "21", sp(12)),
                record("50", "123456", "10020030040", "9876", "000002", "051001", "98765432101", "2", "00000017400",
                        "261020", "35", "0000", "SASBO     ", " 1234567", "C  ", " 0", "SUBSCRIP",
                        "NAIDOO" + sp(14), "K   ", sp(41), "21", sp(12)),
                record("52", "123456", "10020030040", "9876", "000003", "123456", "10020030040", "1", "00000034800",
                        "261020", "10", "0000", "SASBO     ", "CONTRA 261020       ", "SASBO     ", sp(70)),
                record("50", "123456", "10020030040", "9876", "000004", "470010", "00000000555", "3", "00000017000",
                        "261030", "35", "0000", "SASBO     ", "M-200001", "C  ", " 0", "SEPTEMBE",
                        "VAN DER MERWE" + sp(7), "JPL ", sp(41), "21", sp(12)),
                record("52", "123456", "10020030040", "9876", "000005", "123456", "10020030040", "1", "00000017000",
                        "261030", "10", "0000", "SASBO     ", "CONTRA 261030       ", "SASBO     ", sp(70)),
                record("92", "9876", "000001", "000005", "261020", "261030", "000003", "000002", "000002",
                        "000000051800", "000000051800", "120040060626", sp(96)),
                record("94", header02Body, sp(6), "000001", "000009", "000002", sp(106)));
        return String.join("\r\n", records) + "\r\n";
    }

    // ------------------------------------------------------------------------------------------

    @Test
    @DisplayName("the rendered file equals the golden file written from the layout, byte for byte")
    void theFileIsTheGoldenFile() {
        BureauFileWriter.Written written = BureauFileWriter.write(SETTINGS, HEADER, lines());
        assertThat(new String(written.bytes(), StandardCharsets.US_ASCII)).isEqualTo(golden());
    }

    @Test
    @DisplayName("ASCII, CRLF, 180 characters -- on every record, asserted rather than assumed")
    void everyRecordIsTheShapeTheBankTakes() {
        byte[] bytes = BureauFileWriter.write(SETTINGS, HEADER, lines()).bytes();
        for (byte b : bytes) {
            assertThat(b & 0xFF).isLessThan(0x80);
        }
        String text = new String(bytes, StandardCharsets.US_ASCII);
        assertThat(text).endsWith("\r\n");
        String[] records = text.split("\r\n");
        assertThat(records).hasSize(9).allSatisfy(r -> assertThat(r).hasSize(180).doesNotContain("\n"));
    }

    @Test
    @DisplayName("the trailer's hash total is the last twelve digits of the sum of every homing account")
    void theHashTotalIsTheRule() {
        BigInteger sum = BigInteger.ZERO;
        for (String account : List.of("1234567890", "98765432101", "555", "10020030040", "10020030040")) {
            sum = sum.add(new BigInteger(account));
        }
        String expected = String.format("%012d", sum.mod(BigInteger.TEN.pow(12)));
        BureauFileWriter.Written written = BureauFileWriter.write(SETTINGS, HEADER, lines());
        assertThat(written.hashTotal()).isEqualTo(expected).isEqualTo("120040060626");
        assertThat(written.debitCents()).isEqualTo(51800);
        assertThat(written.debitCount()).isEqualTo(3);
        assertThat(written.contraCount()).as("one contra per action date").isEqualTo(2);
        assertThat(written.records()).isEqualTo(9);
    }

    @Test
    @DisplayName("each line learns the sequence the bank's returns report will name it by")
    void sequencesAreAssigned() {
        BureauFileWriter.Written written = BureauFileWriter.write(SETTINGS, HEADER, lines());
        // The contra for the 20th takes 3, so the 30th's debit is 4.
        assertThat(written.sequenceByLine()).containsExactly(
                java.util.Map.entry(11L, 1), java.util.Map.entry(12L, 2), java.util.Map.entry(13L, 4));
    }

    @Test
    @DisplayName("the same run gives the same bytes, whatever order its lines arrive in")
    void theBytesAreDeterministic() {
        List<BureauFileLine> shuffled = new ArrayList<>(lines());
        Collections.reverse(shuffled);
        assertThat(BureauFileWriter.write(SETTINGS, HEADER, shuffled).bytes())
                .isEqualTo(BureauFileWriter.write(SETTINGS, HEADER, lines()).bytes());
    }

    @Test
    @DisplayName("one amount changed changes the bytes -- the file is not blind to its content")
    void oneAmountChangesTheFile() {
        BureauFileLine changed = new BureauFileLine(11L, "632005", "1234567890", 1, new BigDecimal("175.00"),
                LocalDate.of(2026, 10, 20), "M-100116", "Botha", "AB", null);
        assertThat(BureauFileWriter.write(SETTINGS, HEADER, List.of(changed, naidoo(), vanDerMerwe())).bytes())
                .isNotEqualTo(BureauFileWriter.write(SETTINGS, HEADER, lines()).bytes());
    }

    // ------------------------------------------------------------------------------------------
    // Refusals, each with the must-pass twin above
    // ------------------------------------------------------------------------------------------

    @Test
    @DisplayName("an unconfigured identifier refuses the file and names the setting -- never a zero in its place")
    void unconfiguredSettingsRefuse() {
        BureauFileSettings unset = new BureauFileSettings(
                "SASBO", null, "123456", "10020030040", "1111122222", "1001", "000018", "SASBO", "TWO DAY", "BEFT");
        AcbRefusal refused = refusal(() -> BureauFileWriter.write(unset, HEADER, lines()));
        assertThat(refused.code()).isEqualTo(AcbRefusalCode.FILE_SETTINGS_MISSING);
        assertThat(refused.details().get("settings")).contains("userCode");
        assertThat(unset.toString()).doesNotContain("10020030040");
    }

    @Test
    @DisplayName("a header out of order is refused: purge must follow the last action date")
    void aHeaderOutOfOrderRefuses() {
        BureauFileWriter.Header purgedEarly = new BureauFileWriter.Header(
                LocalDate.of(2026, 10, 17), LocalDate.of(2026, 10, 29),
                LocalDate.of(2026, 10, 20), LocalDate.of(2026, 10, 30));
        assertThat(refusal(() -> BureauFileWriter.write(SETTINGS, purgedEarly, lines())).code())
                .isEqualTo(AcbRefusalCode.FILE_HEADER_INVALID);

        BureauFileWriter.Header createdLate = new BureauFileWriter.Header(
                LocalDate.of(2026, 10, 21), LocalDate.of(2026, 11, 9),
                LocalDate.of(2026, 10, 20), LocalDate.of(2026, 10, 30));
        assertThat(refusal(() -> BureauFileWriter.write(SETTINGS, createdLate, lines())).code())
                .isEqualTo(AcbRefusalCode.FILE_HEADER_INVALID);
    }

    @Test
    @DisplayName("a header whose action dates are not the lines' own is refused")
    void aHeaderThatDisagreesWithItsLinesRefuses() {
        BureauFileWriter.Header wrongLast = new BureauFileWriter.Header(
                LocalDate.of(2026, 10, 17), LocalDate.of(2026, 11, 9),
                LocalDate.of(2026, 10, 20), LocalDate.of(2026, 10, 29));
        assertThat(refusal(() -> BureauFileWriter.write(SETTINGS, wrongLast, lines())).code())
                .isEqualTo(AcbRefusalCode.FILE_HEADER_INVALID);
    }

    @Test
    @DisplayName("a line with no account type refuses the file, counted -- the member record does not carry one yet")
    void noAccountTypeRefuses() {
        BureauFileLine untyped = new BureauFileLine(11L, "632005", "1234567890", null, new BigDecimal("174.00"),
                LocalDate.of(2026, 10, 20), "M-100116", "Botha", "AB", null);
        AcbRefusal refused = refusal(() -> BureauFileWriter.write(SETTINGS, HEADER,
                List.of(untyped, naidoo(), vanDerMerwe())));
        assertThat(refused.code()).isEqualTo(AcbRefusalCode.FILE_LINES_INCOMPLETE);
        assertThat(refused.details()).containsEntry("lines", "1").containsEntry("no account type", "1")
                .containsEntry("firstLineIds", "11");
        assertThat(refused.getMessage()).doesNotContain("1234567890");
    }

    @Test
    @DisplayName("fields wider than the file's are refused, never truncated into someone else's number")
    void tooWideRefuses() {
        BureauFileLine wideAccount = new BureauFileLine(11L, "632005", "123456789012", 1, new BigDecimal("174.00"),
                LocalDate.of(2026, 10, 20), "M-100116", "Botha", "AB", null);
        BureauFileLine wideNumber = new BureauFileLine(11L, "632005", "1234567890", 1, new BigDecimal("174.00"),
                LocalDate.of(2026, 10, 20), "M-1001160", "Botha", "AB", null);
        assertThat(refusal(() -> BureauFileWriter.write(SETTINGS, HEADER, List.of(wideAccount, naidoo(), vanDerMerwe())))
                .code()).isEqualTo(AcbRefusalCode.FILE_LINES_INCOMPLETE);
        assertThat(refusal(() -> BureauFileWriter.write(SETTINGS, HEADER, List.of(wideNumber, naidoo(), vanDerMerwe())))
                .code()).isEqualTo(AcbRefusalCode.FILE_LINES_INCOMPLETE);
    }

    @Test
    @DisplayName("a run with no lines is not a file")
    void noLinesRefuses() {
        assertThat(refusal(() -> BureauFileWriter.write(SETTINGS, HEADER, List.of())).code())
                .isEqualTo(AcbRefusalCode.FILE_LINES_INCOMPLETE);
    }

    @Test
    @DisplayName("names are printed in capitals and ASCII: a diacritic loses its mark, not its letter")
    void namesAreAscii() {
        assertThat(BureauFileWriter.ascii("Müller")).isEqualTo("MULLER");
        assertThat(BureauFileWriter.ascii(" O'Brien ")).isEqualTo("O'BRIEN");
        assertThat(BureauFileWriter.ascii("Nkosi\tDlamini")).isEqualTo("NKOSI DLAMINI");
        assertThat(BureauFileWriter.monthReference(YearMonth.of(2026, 6))).isEqualTo("JUNE");
        assertThat(BureauFileWriter.monthReference(null)).isEqualTo("SUBSCRIP");
    }

    private static AcbRefusal refusal(Runnable call) {
        try {
            call.run();
        } catch (AcbRefusal refusal) {
            return refusal;
        }
        throw new AssertionError("expected an AcbRefusal and the call returned normally");
    }
}
