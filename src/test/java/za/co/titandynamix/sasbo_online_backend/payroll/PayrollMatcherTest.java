package za.co.titandynamix.sasbo_online_backend.payroll;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import za.co.titandynamix.sasbo_online_backend.enums.PayrollAmountStatus;
import za.co.titandynamix.sasbo_online_backend.enums.PayrollConfidence;
import za.co.titandynamix.sasbo_online_backend.enums.PayrollDiscrepancyType;
import za.co.titandynamix.sasbo_online_backend.enums.PayrollMatchRule;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Every rule of the comparison, each named for the answer of Stephne's it carries out (PAY.md, 6 Oct). The members and
 * lines are invented; the subscription is the dated rate R174.00 from January 2026 -- and NO rate before it, as on
 * record until the R166.65 row is added, so a month of 2025 is never priced.
 */
@DisplayName("The payroll comparison, rule by rule")
class PayrollMatcherTest {

    private static final YearMonth SEPTEMBER = YearMonth.of(2026, 9);
    private static final BigDecimal RATE = new BigDecimal("174.00");
    private static final Map<YearMonth, BigDecimal> RATES = new TreeMap<>(Map.of(YearMonth.of(2026, 1), RATE));

    // ------------------------------------------------------------------------------------------- who a line is

    @Test
    @DisplayName("Stephne, PAY-10: matched by staff number within the employer -- HIGH where the ID and the name agree")
    void byStaffNumber() {
        PayrollMatcher.Member a = member(1, "99010001", "8106145689086", "BK", "Testerson");
        PayrollMatcher.Result r = match(List.of(line(2, "099010001", "8106145689086", "BK", "Testerson", "174.00")),
                List.of(a));
        PayrollMatcher.LineMatch m = r.matches().get(0);
        assertThat(m.rule()).isEqualTo(PayrollMatchRule.STAFF_NUMBER);
        assertThat(m.confidence()).isEqualTo(PayrollConfidence.HIGH);
        assertThat(m.amountStatus()).isEqualTo(PayrollAmountStatus.MATCHES);
        assertThat(m.reason()).isEqualTo("Matched by the staff number; the ID number and the name agree.");
        assertThat(r.found()).isEmpty();
    }

    @Test
    @DisplayName("Stephne, PAY-10, PAY-19: by ID number where the staff number fits nobody -- a different employee number")
    void byIdNumberWithADifferentEmployeeNumber() {
        PayrollMatcher.Member a = member(1, "910001", "8106145689086", "BK", "Testerson");
        PayrollMatcher.Result r = match(List.of(line(2, "919999", "'8106145689086", "BK", "Testerson", "174.00")),
                List.of(a));
        assertThat(r.matches().get(0).rule()).isEqualTo(PayrollMatchRule.ID_NUMBER);
        assertThat(r.matches().get(0).confidence()).isEqualTo(PayrollConfidence.MEDIUM);
        assertThat(types(r)).containsExactly(PayrollDiscrepancyType.EMPLOYEE_NUMBER_DIFFERS);
        assertThat(r.found().get(0).detail()).isEqualTo("Line 2: the employee number on the file differs from the one "
                + "on record (matched by ID number).");
    }

    @Test
    @DisplayName("Stephne, PAY-10, PAY-19: by initials and surname where only they fit -- one member, a different number")
    void byNameWithADifferentEmployeeNumber() {
        PayrollMatcher.Member a = member(1, "99010007", null, "RT", "Ledgerwood");
        PayrollMatcher.Member b = member(2, "99010008", null, "BK", "Tallyburn");
        PayrollMatcher.Result r = match(List.of(line(5, "99019999", null, "R", "LEDGERWOOD", "174.00")), List.of(a, b));
        assertThat(r.matches().get(0).rule()).isEqualTo(PayrollMatchRule.NAME);
        assertThat(r.matches().get(0).member().id()).isEqualTo(1);
        assertThat(r.matches().get(0).confidence()).isEqualTo(PayrollConfidence.LOW);
        assertThat(types(r)).containsExactly(PayrollDiscrepancyType.EMPLOYEE_NUMBER_DIFFERS);
    }

    @Test
    @DisplayName("Stephne, PAY-10: a name two members share matches neither -- unless one alone pays by payroll and is current")
    void anAmbiguousNameMatchesNobody() {
        PayrollMatcher.Member a = member(1, "1", null, "B", "Testerson");
        PayrollMatcher.Member b = member(2, "2", null, "BK", "Testerson");
        PayrollMatcher.Result r = match(List.of(line(3, null, null, "B", "Testerson", "174.00")), List.of(a, b));
        assertThat(r.matches().get(0).rule()).isEqualTo(PayrollMatchRule.NONE);
        assertThat(r.matches().get(0).reason()).startsWith("More than one member of this employer has this name");
        assertThat(types(r)).contains(PayrollDiscrepancyType.NOT_A_MEMBER);

        PayrollMatcher.Member resigned = new PayrollMatcher.Member(2, "M-2", "2", null, "BK", "Testerson", true, true,
                false, true, null, null);
        PayrollMatcher.Result preferred = match(List.of(line(3, null, null, "B", "Testerson", "174.00")),
                List.of(a, resigned));
        assertThat(preferred.matches().get(0).member().id()).as("the one current payroll member").isEqualTo(1);
    }

    @Test
    @DisplayName("Stephne, PAY-12: on the file but not a member -- queried with the employer")
    void notAMember() {
        PayrollMatcher.Result r = match(List.of(line(4, "123", null, "ZZ", "Nobodyelse", "174.00")), List.of());
        assertThat(types(r)).containsExactly(PayrollDiscrepancyType.NOT_A_MEMBER);
        assertThat(r.found().get(0).detail()).isEqualTo("Line 4: R174.00 deducted from someone who is not a member of SASBO.");
        assertThat(r.notMembers()).isEqualTo(1);
    }

    @Test
    @DisplayName("Stephne, PAY-13: a member whose membership has ended, still deducted -- ask the employer to stop")
    void membershipEnded() {
        PayrollMatcher.Member resigned = new PayrollMatcher.Member(1, "M-1", "7", null, "BK", "Testerson", true, true,
                false, true, null, null);
        PayrollMatcher.Result r = match(List.of(line(2, "7", null, "BK", "Testerson", "174.00")), List.of(resigned));
        assertThat(types(r)).containsExactly(PayrollDiscrepancyType.MEMBERSHIP_ENDED);
        assertThat(r.missing()).as("a member who has left is never missing").isZero();
    }

    // ----------------------------------------------------------------------------------------------- missing

    @Test
    @DisplayName("Stephne, PAY-14, PAY-15: a payroll member missing from the file -- the months in a row counted, three said")
    void missingFromTheFile() {
        PayrollMatcher.Member missing = member(1, "7", null, "BK", "Testerson");
        PayrollMatcher.Member joinedLater = new PayrollMatcher.Member(2, "M-2", "8", null, "R", "Ledgerwood", false,
                true, false, true, LocalDate.of(2026, 10, 1), null);
        PayrollMatcher.Member debitOrder = new PayrollMatcher.Member(3, "M-3", "9", null, "T", "Tallyburn", false,
                false, true, true, null, null);
        Map<Integer, PayrollMatcher.History> history = Map.of(1, new PayrollMatcher.History(2, 0, 2));
        PayrollMatcher.Result r = PayrollMatcher.match(List.of(), inputs(List.of(missing, joinedLater, debitOrder),
                Map.of(), history));
        assertThat(types(r)).containsExactly(PayrollDiscrepancyType.MISSING_FROM_FILE);
        PayrollMatcher.Found found = r.found().get(0);
        assertThat(found.memberId()).isEqualTo(1);
        assertThat(found.months()).isEqualTo(3);
        assertThat(found.detail()).isEqualTo("Not on the file for September 2026. Missing 3 months in a row: due for "
                + "deletion after three months without a deduction (PAY-15).");
        assertThat(r.expectedTotal()).as("the missing member's subscription is expected").isEqualByComparingTo("174.00");
    }

    // ------------------------------------------------------------------------------------------------- amounts

    @Test
    @DisplayName("Stephne, PAY-16: the wrong amount -- the old R166.65 is R7.35 short, to correct and record as arrears")
    void theWrongAmount() {
        PayrollMatcher.Result r = match(List.of(line(8, "7", null, "BK", "Testerson", "166.65")),
                List.of(member(1, "7", null, "BK", "Testerson")));
        assertThat(types(r)).containsExactly(PayrollDiscrepancyType.AMOUNT_DIFFERS);
        PayrollMatcher.Found f = r.found().get(0);
        assertThat(f.detail()).isEqualTo("Line 8: R166.65 deducted; R174.00 expected: R7.35 short.");
        assertThat(f.difference()).isEqualByComparingTo("-7.35");
        assertThat(r.matches().get(0).amountStatus()).isEqualTo(PayrollAmountStatus.DIFFERS);
    }

    @Test
    @DisplayName("Stephne, PAY-18: deducted twice with no missed month on record -- refund the member directly")
    void deductedTwice() {
        PayrollMatcher.Result r = match(List.of(line(2, "7", null, "BK", "Testerson", "174.00"),
                line(3, "7", null, "BK", "Testerson", "174.00")), List.of(member(1, "7", null, "BK", "Testerson")));
        assertThat(types(r)).containsExactly(PayrollDiscrepancyType.DEDUCTED_TWICE);
        assertThat(r.found().get(0).detail()).isEqualTo("Lines 2 and 3: deducted twice (R348.00) with no missed month "
                + "on record: refund R174.00 to the member.");
        assertThat(r.matches()).extracting(PayrollMatcher.LineMatch::amountStatus)
                .containsOnly(PayrollAmountStatus.LISTED_TWICE);
        assertThat(r.listedTwice()).isEqualTo(1);
    }

    @Test
    @DisplayName("Stephne, PAY-26: listed twice with a month owed on the record -- a possible catch-up, told apart from PAY-18")
    void possibleCatchUp() {
        PayrollMatcher.Member owing = new PayrollMatcher.Member(1, "M-1", "7", null, "BK", "Testerson", false, true,
                false, true, null, new BigDecimal("174.00"));
        PayrollMatcher.Result r = match(List.of(line(2, "7", null, "BK", "Testerson", "174.00"),
                line(3, "7", null, "BK", "Testerson", "174.00")), List.of(owing));
        assertThat(types(r)).containsExactly(PayrollDiscrepancyType.POSSIBLE_CATCH_UP);
        assertThat(r.found().get(0).detail()).isEqualTo("Lines 2 and 3: listed twice (R348.00), and 1 month is owed: "
                + "confirm it is a catch-up.");
    }

    @Test
    @DisplayName("Stephne, PAY-26: a second line the file marks for an earlier month (Absa's For-period) is a catch-up")
    void aMarkedCatchUp() {
        PayrollMatcher.Result r = match(List.of(line(5, "7", null, "D", "Dummikazi", "174.00"),
                withPeriod(line(6, "7", null, "D", "Dummikazi", "174.00"), YearMonth.of(2026, 8))),
                List.of(member(1, "7", null, "D", "Dummikazi")));
        assertThat(r.found()).isEmpty();
        assertThat(r.matches()).extracting(PayrollMatcher.LineMatch::amountStatus)
                .containsOnly(PayrollAmountStatus.CATCH_UP);
        assertThat(r.catchUps()).isEqualTo(1);
    }

    @Test
    @DisplayName("Stephne, PAY-25, PAY-26: one larger amount is a catch-up where months are owed, and the wrong amount where none is")
    void oneLargerAmount() {
        PayrollMatcher.Member missedAugust = member(1, "7", null, "BK", "Testerson");
        PayrollMatcher.Result caughtUp = PayrollMatcher.match(List.of(line(9, "7", null, "BK", "Testerson", "348.00")),
                inputs(List.of(missedAugust), Map.of(), Map.of(1, new PayrollMatcher.History(1, 0, 1))));
        assertThat(caughtUp.found()).isEmpty();
        assertThat(caughtUp.matches().get(0).amountStatus()).isEqualTo(PayrollAmountStatus.CATCH_UP);
        assertThat(caughtUp.matches().get(0).months()).isEqualTo(2);

        PayrollMatcher.Result notOwed = match(List.of(line(9, "7", null, "BK", "Testerson", "348.00")),
                List.of(member(1, "7", null, "BK", "Testerson")));
        assertThat(types(notOwed)).containsExactly(PayrollDiscrepancyType.AMOUNT_DIFFERS);
        assertThat(notOwed.found().get(0).detail()).isEqualTo("Line 9: R348.00 is 2 months' subscription, but no "
                + "missed month is on record; R174.00 expected.");
    }

    @Test
    @DisplayName("🔒 a catch-up reaching a month with no rate on record is RATE_UNKNOWN, naming the month -- never priced at R174.00")
    void aMonthWithNoRateIsNeverPriced() {
        PayrollMatcher.Member owing = new PayrollMatcher.Member(1, "M-1", "7", null, "BK", "Testerson", false, true,
                false, true, null, new BigDecimal("348.00"));
        PayrollMatcher.Result january = PayrollMatcher.match(List.of(line(2, "7", null, "BK", "Testerson", "348.00")),
                new PayrollMatcher.Inputs(YearMonth.of(2026, 1), List.of(owing), Map.of(), rates(),
                        Map.of(), "Absa"));
        PayrollMatcher.LineMatch m = january.matches().get(0);
        assertThat(m.amountStatus()).isEqualTo(PayrollAmountStatus.RATE_UNKNOWN);
        assertThat(m.rateUnknownFor()).isEqualTo(YearMonth.of(2025, 12));
        assertThat(january.found()).as("not called wrong, not called right: not checked").isEmpty();
        assertThat(january.rateUnknown()).isEqualTo(1);

        PayrollMatcher.Result marked = PayrollMatcher.match(List.of(line(2, "7", null, "BK", "Testerson", "174.00"),
                withPeriod(line(3, "7", null, "BK", "Testerson", "166.65"), YearMonth.of(2025, 12))),
                new PayrollMatcher.Inputs(YearMonth.of(2026, 1), List.of(owing), Map.of(), rates(), Map.of(), "Absa"));
        assertThat(marked.matches().get(0).amountStatus()).isEqualTo(PayrollAmountStatus.RATE_UNKNOWN);
        assertThat(marked.matches().get(0).rateUnknownFor()).isEqualTo(YearMonth.of(2025, 12));

        PayrollMatcher.Result noRateAtAll = PayrollMatcher.match(List.of(line(2, "7", null, "BK", "Testerson", "166.65")),
                new PayrollMatcher.Inputs(YearMonth.of(2025, 11), List.of(member(1, "7", null, "BK", "Testerson")),
                        Map.of(), rates(), Map.of(), "Absa"));
        assertThat(noRateAtAll.matches().get(0).amountStatus()).isEqualTo(PayrollAmountStatus.RATE_UNKNOWN);
        assertThat(noRateAtAll.matches().get(0).rateUnknownFor()).isEqualTo(YearMonth.of(2025, 11));
        assertThat(noRateAtAll.expectedTotal()).isNull();
    }

    // ------------------------------------------------------------------------------------------------- details

    @Test
    @DisplayName("Stephne, PAY-19: a name that does not match the employee number on record")
    void nameDiffers() {
        PayrollMatcher.Result r = match(List.of(line(9, "7", null, "E", "Pilotdale", "174.00")),
                List.of(member(1, "7", null, "P", "Pilotdale")));
        assertThat(types(r)).containsExactly(PayrollDiscrepancyType.NAME_DIFFERS);
        assertThat(r.found().get(0).detail()).isEqualTo("Line 9: the name on the file does not match the employee "
                + "number on record.");
    }

    @Test
    @DisplayName("Stephne, PAY-17: another ID number, or another employer on record, is a detail to check")
    void detailsDiffer() {
        PayrollMatcher.Result idDiffers = match(List.of(line(4, "7", "7503144286081", "BK", "Testerson", "174.00")),
                List.of(member(1, "7", "8106145689086", "BK", "Testerson")));
        assertThat(types(idDiffers)).containsExactly(PayrollDiscrepancyType.DETAILS_DIFFER);
        assertThat(idDiffers.matches().get(0).confidence()).isEqualTo(PayrollConfidence.MEDIUM);

        PayrollMatcher.Member elsewhere = new PayrollMatcher.Member(9, "M-9", "55", "8106145689086", "BK", "Testerson",
                false, true, false, false, null, null);
        PayrollMatcher.Result another = PayrollMatcher.match(List.of(line(4, null, "8106145689086", "BK", "Testerson",
                "174.00")), inputs(List.of(), Map.of("8106145689086", elsewhere), Map.of()));
        assertThat(types(another)).containsExactly(PayrollDiscrepancyType.DETAILS_DIFFER);
        assertThat(another.found().get(0).detail()).contains("the member's employer on record is not Absa");
    }

    @Test
    @DisplayName("Stephne, PAY-29: a member who pays by debit order, found on a payroll file -- their bank is no employer")
    void aDebitOrderMember() {
        PayrollMatcher.Member debitOrder = new PayrollMatcher.Member(9, "M-9", "EMP99855", "8106145689086", "BK",
                "Testerson", false, false, true, false, null, null);
        PayrollMatcher.Result r = PayrollMatcher.match(List.of(line(4, "EMP-99855", "8106145689086", "BK", "Testerson",
                "174.00")), inputs(List.of(), Map.of("8106145689086", debitOrder), Map.of()));
        assertThat(types(r)).containsExactly(PayrollDiscrepancyType.DEBIT_ORDER_MEMBER);
    }

    @Test
    @DisplayName("a line that cannot be read is a discrepancy of its own, with the reason")
    void anUnreadableLine() {
        PayrollParser.Line broken = new PayrollParser.Line(7, "7", "7", null, null, name("BK", "Testerson"), null,
                null, false, "The amount could not be read.", "[]");
        PayrollMatcher.Result r = match(List.of(broken), List.of());
        assertThat(types(r)).containsExactly(PayrollDiscrepancyType.UNREADABLE_ROW);
        assertThat(r.found().get(0).detail()).isEqualTo("Line 7: The amount could not be read.");
    }

    @Test
    @DisplayName("names alone -- GBS's letter, SA Reserve Bank's picture -- match by initials and surname, LOW confidence")
    void namesAlone() {
        PayrollMatcher.Result r = match(List.of(line(1, null, null, "RT", "Ledgerwood", "174.00")),
                List.of(member(1, "881001", null, "RT", "Ledgerwood")));
        assertThat(r.matches().get(0).rule()).isEqualTo(PayrollMatchRule.NAME);
        assertThat(r.matches().get(0).confidence()).isEqualTo(PayrollConfidence.LOW);
        assertThat(r.found()).isEmpty();
    }

    @Test
    @DisplayName("the totals: the file's own, and what the members' subscriptions come to")
    void totals() {
        PayrollMatcher.Result r = match(List.of(line(2, "7", null, "BK", "Testerson", "174.00"),
                line(3, "8", null, "R", "Ledgerwood", "166.65")),
                List.of(member(1, "7", null, "BK", "Testerson"), member(2, "8", null, "R", "Ledgerwood"),
                        member(3, "9", null, "T", "Tallyburn")));
        assertThat(r.fileTotal()).isEqualByComparingTo("340.65");
        assertThat(r.expectedTotal()).as("three members expected: two on the file, one missing").isEqualByComparingTo("522.00");
        assertThat(r.matched()).isEqualTo(2);
        assertThat(r.missing()).isEqualTo(1);
    }

    @Test
    @DisplayName("twenty thousand lines against twenty-five thousand members: one pass over indexed maps, in seconds")
    void twentyThousandLines() {
        List<PayrollMatcher.Member> members = new ArrayList<>();
        List<PayrollParser.Line> lines = new ArrayList<>();
        for (int i = 1; i <= 25_000; i++) {
            members.add(member(i, String.valueOf(1_000_000 + i), null, "A", "Surname" + Integer.toString(i, 36)));
            if (i <= 20_000) {
                lines.add(line(i + 1, String.valueOf(1_000_000 + i), null, "A", "Surname" + Integer.toString(i, 36),
                        "174.00"));
            }
        }
        long started = System.nanoTime();
        PayrollMatcher.Result r = match(lines, members);
        long millis = (System.nanoTime() - started) / 1_000_000;
        assertThat(r.matched()).isEqualTo(20_000);
        assertThat(r.missing()).isEqualTo(5_000);
        assertThat(millis).as("the comparison alone, in milliseconds").isLessThan(10_000);
    }

    // ---------------------------------------------------------------------------------------------- helpers

    private static PayrollMatcher.Result match(List<PayrollParser.Line> lines, List<PayrollMatcher.Member> members) {
        return PayrollMatcher.match(lines, inputs(members, Map.of(), Map.of()));
    }

    private static PayrollMatcher.Inputs inputs(List<PayrollMatcher.Member> members,
                                                Map<String, PayrollMatcher.Member> outside,
                                                Map<Integer, PayrollMatcher.History> history) {
        Map<String, PayrollMatcher.Member> byId = new HashMap<>(outside);
        members.stream().filter(m -> m.idKey() != null).forEach(m -> byId.putIfAbsent(m.idKey(), m));
        return new PayrollMatcher.Inputs(SEPTEMBER, members, byId, rates(), history, "Absa");
    }

    private static Function<YearMonth, Optional<BigDecimal>> rates() {
        TreeMap<YearMonth, BigDecimal> sorted = new TreeMap<>(RATES);
        return month -> Optional.ofNullable(sorted.floorEntry(month)).map(Map.Entry::getValue);
    }

    private static PayrollMatcher.Member member(int id, String staff, String idNumber, String initials, String surname) {
        return new PayrollMatcher.Member(id, "M-" + id, PayrollKeys.staffKey(staff), PayrollKeys.idKey(idNumber),
                initials, surname, false, true, false, true, null, null);
    }

    private static PayrollParser.Line line(int number, String staff, String id, String initials, String surname,
                                           String amount) {
        return new PayrollParser.Line(number, staff, PayrollKeys.staffKey(staff), id, PayrollKeys.idKey(id),
                name(initials, surname), new BigDecimal(amount), null, true, null, "[]");
    }

    private static PayrollParser.Line withPeriod(PayrollParser.Line line, YearMonth period) {
        return new PayrollParser.Line(line.lineNumber(), line.staffNumber(), line.staffKey(), line.idNumber(),
                line.idKey(), line.name(), line.amount(), period, true, null, "[]");
    }

    private static PayrollNames.Name name(String initials, String surname) {
        return PayrollNames.read(NameStyle.SEPARATE, initials, null, surname, null);
    }

    private static List<PayrollDiscrepancyType> types(PayrollMatcher.Result result) {
        return result.found().stream().map(PayrollMatcher.Found::type).toList();
    }
}
