package za.co.titandynamix.sasbo_online_backend.acb;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import za.co.titandynamix.sasbo_online_backend.acb.RunPreparer.Preparation;
import za.co.titandynamix.sasbo_online_backend.acb.RunPreparer.PreparedLine;
import za.co.titandynamix.sasbo_online_backend.enums.AcbExclusionReason;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static za.co.titandynamix.sasbo_online_backend.enums.AcbExclusionReason.ACCOUNT_SHAPE_INVALID;
import static za.co.titandynamix.sasbo_online_backend.enums.AcbExclusionReason.DAY_OUT_OF_WINDOW;
import static za.co.titandynamix.sasbo_online_backend.enums.AcbExclusionReason.NOT_DEBIT_ORDER;
import static za.co.titandynamix.sasbo_online_backend.enums.AcbExclusionReason.NO_BANK_ON_RECORD;
import static za.co.titandynamix.sasbo_online_backend.enums.AcbExclusionReason.NO_SUBSCRIPTION_ROW;
import static za.co.titandynamix.sasbo_online_backend.enums.AcbExclusionReason.SNAPSHOT_INCOMPLETE;
import static za.co.titandynamix.sasbo_online_backend.enums.AcbExclusionReason.STATUS_NOT_ACTIVE;

/**
 * The population, date and amount rules, as a table.
 *
 * <h2>Every exclusion has an inclusion twin one change away</h2>
 * A preparer that excluded everyone would satisfy every "is excluded" row, so each row that
 * excludes a member sits beside a row one field away that includes them. The base member below
 * qualifies; each row changes ONE field and says what follows.
 */
@DisplayName("RunPreparer -- who is debited, when, and for how much")
class RunPreparerTest {

    static final YearMonth JULY = YearMonth.of(2026, 7);
    static final BigDecimal RATE = new BigDecimal("174.00");

    static final BankRow ALPHA = new BankRow(1, "Alpha Bank", "632005");
    static final BankRow BETA = new BankRow(2, "Beta Bank", "051001");

    /** Alpha has one ACTIVE subscription row; Beta has none -- SASBO does not collect there. */
    static final BankIndex BANKS = BankIndex.of(List.of(ALPHA, BETA), List.of(ALPHA.id()));

    /** A member every rule accepts. Collation-neutral surname, synthetic account. */
    static MemberDebitRow qualifying() {
        return new MemberDebitRow(7, "M-200001", "Botha", "AB", "Active", "Debit Order",
                "Alpha Bank", "1234567890", 25);
    }

    static Preparation prepare(List<MemberDebitRow> members, List<RePresentationCandidate> candidates) {
        RunPreparer preparer = new RunPreparer(JULY, new DebitWindow(HolidayCalendar.WEEKENDS_ONLY),
                BANKS, RATE, candidates);
        members.forEach(preparer::accept);
        return preparer.finish();
    }

    static Preparation prepare(MemberDebitRow member) {
        return prepare(List.of(member), List.of());
    }

    // ------------------------------------------------------------------------------------------
    // The table
    // ------------------------------------------------------------------------------------------

    /** One change to the qualifying member, and what should follow; {@code null} means included. */
    record Row(String change, UnaryOperator<MemberDebitRow> edit, AcbExclusionReason reason, String detail) {
        @Override
        public String toString() {
            return change + " -> " + (reason == null ? "debited" : reason + " (" + detail + ")");
        }
    }

    static MemberDebitRow with(MemberDebitRow m, String field, Object value) {
        return switch (field) {
            case "paymentType" -> new MemberDebitRow(m.id(), m.membershipNumber(), m.surname(), m.initials(),
                    m.status(), (String) value, m.bankName(), m.bankAccountNumber(), m.debitDay());
            case "status" -> new MemberDebitRow(m.id(), m.membershipNumber(), m.surname(), m.initials(),
                    (String) value, m.paymentType(), m.bankName(), m.bankAccountNumber(), m.debitDay());
            case "bankName" -> new MemberDebitRow(m.id(), m.membershipNumber(), m.surname(), m.initials(),
                    m.status(), m.paymentType(), (String) value, m.bankAccountNumber(), m.debitDay());
            case "account" -> new MemberDebitRow(m.id(), m.membershipNumber(), m.surname(), m.initials(),
                    m.status(), m.paymentType(), m.bankName(), (String) value, m.debitDay());
            case "debitDay" -> new MemberDebitRow(m.id(), m.membershipNumber(), m.surname(), m.initials(),
                    m.status(), m.paymentType(), m.bankName(), m.bankAccountNumber(), (Integer) value);
            case "membershipNumber" -> new MemberDebitRow(m.id(), (String) value, m.surname(), m.initials(),
                    m.status(), m.paymentType(), m.bankName(), m.bankAccountNumber(), m.debitDay());
            case "surname" -> new MemberDebitRow(m.id(), m.membershipNumber(), (String) value, m.initials(),
                    m.status(), m.paymentType(), m.bankName(), m.bankAccountNumber(), m.debitDay());
            default -> throw new IllegalArgumentException(field);
        };
    }

    static Row row(String field, Object value, AcbExclusionReason reason, String detail) {
        return new Row(field + " = " + (value == null ? "null" : "'" + value + "'"),
                m -> with(m, field, value), reason, detail);
    }

    static Stream<Arguments> table() {
        return Stream.of(
                // 1 -- payment type. Counted by the value, not listed.
                row("paymentType", "Payroll Deduction", NOT_DEBIT_ORDER, "Payroll Deduction"),
                row("paymentType", "Cash", NOT_DEBIT_ORDER, "Cash"),
                row("paymentType", null, NOT_DEBIT_ORDER, RunPreparer.BLANK),
                row("paymentType", "DEBIT_ORDER", null, null),
                row("paymentType", " debit order ", null, null),
                // 2 -- status, case and spaces forgiven, anything else counted by its raw value.
                row("status", "Suspended", STATUS_NOT_ACTIVE, "Suspended"),
                row("status", "Pending", STATUS_NOT_ACTIVE, "Pending"),
                row("status", "Actve", STATUS_NOT_ACTIVE, "Actve"),
                row("status", null, STATUS_NOT_ACTIVE, RunPreparer.BLANK),
                row("status", " active ", null, null),
                row("status", "ACTIVE", null, null),
                // 3 -- the bank on record, by name, trimmed and case-insensitive.
                row("bankName", "Nobody Bank", NO_BANK_ON_RECORD, "Nobody Bank"),
                row("bankName", null, NO_BANK_ON_RECORD, RunPreparer.BLANK),
                row("bankName", "  alpha BANK ", null, null),
                // 4 -- one ACTIVE subscription row at that bank.
                row("bankName", "Beta Bank", NO_SUBSCRIPTION_ROW, "Beta Bank"),
                // 5 -- the member's own day, 20th to 31st.
                row("debitDay", 5, DAY_OUT_OF_WINDOW, "5"),
                row("debitDay", 19, DAY_OUT_OF_WINDOW, "19"),
                row("debitDay", 32, DAY_OUT_OF_WINDOW, "32"),
                row("debitDay", null, DAY_OUT_OF_WINDOW, RunPreparer.BLANK),
                row("debitDay", 20, null, null),
                row("debitDay", 31, null, null),
                // 6 -- one to eleven digits: the file's width.
                row("account", "123456789012", ACCOUNT_SHAPE_INVALID, "12 digits -- the file carries at most 11"),
                row("account", "62 1234 5678", ACCOUNT_SHAPE_INVALID, "contains characters other than digits"),
                row("account", "", ACCOUNT_SHAPE_INVALID, "blank"),
                row("account", null, ACCOUNT_SHAPE_INVALID, "blank"),
                row("account", "12345678901", null, null),
                row("account", " 1234567890 ", null, null),
                // 7 -- what the file and letters print.
                row("membershipNumber", null, SNAPSHOT_INCOMPLETE, "no membership number"),
                row("membershipNumber", "  ", SNAPSHOT_INCOMPLETE, "no membership number"),
                row("surname", "", SNAPSHOT_INCOMPLETE, "no surname"),
                row("surname", "Naidoo", null, null)
        ).map(Arguments::of);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("table")
    @DisplayName("the rules, one change at a time")
    void theTable(Row row) {
        Preparation prepared = prepare(row.edit().apply(qualifying()));
        if (row.reason() == null) {
            assertThat(prepared.lines()).as(row.toString()).hasSize(1);
            assertThat(prepared.exclusions()).isEmpty();
            assertThat(prepared.counted()).isEmpty();
        } else {
            assertThat(prepared.lines()).as(row.toString()).isEmpty();
            assertThat(prepared.excludedFor(row.reason())).as(row.toString()).isEqualTo(1);
            if (row.reason().listed()) {
                assertThat(prepared.exclusions()).singleElement()
                        .satisfies(e -> {
                            assertThat(e.memberId()).isEqualTo(7);
                            assertThat(e.reason()).isEqualTo(row.reason());
                            assertThat(e.detail()).isEqualTo(row.detail());
                        });
            } else {
                assertThat(prepared.exclusions()).as("a counted reason lists nobody").isEmpty();
                assertThat(prepared.counted().get(row.reason())).containsExactly(Map.entry(row.detail(), 1));
            }
        }
    }

    @Test
    @DisplayName("the table exercises every reason, and includes somebody -- it is not vacuous")
    void theTableCoversEveryReason() {
        List<Row> rows = table().map(a -> (Row) a.get()[0]).toList();
        assertThat(rows.stream().map(Row::reason).filter(r -> r != null).distinct())
                .containsExactlyInAnyOrder(AcbExclusionReason.values());
        assertThat(rows.stream().filter(r -> r.reason() == null)).as("inclusion twins").hasSizeGreaterThan(5);
    }

    // ------------------------------------------------------------------------------------------

    @Nested
    @DisplayName("one reason per member: the first rule failed, in the declared order")
    class OneReasonPerMember {

        @Test
        @DisplayName("the declared order is the order the rules apply")
        void theOrderIsTheContract() {
            assertThat(Arrays.stream(AcbExclusionReason.values()).map(Enum::name)).containsExactly(
                    "NOT_DEBIT_ORDER", "STATUS_NOT_ACTIVE", "NO_BANK_ON_RECORD", "NO_SUBSCRIPTION_ROW",
                    "DAY_OUT_OF_WINDOW", "ACCOUNT_SHAPE_INVALID", "SNAPSHOT_INCOMPLETE");
        }

        @Test
        @DisplayName("a payroll member with no bank and a day of the 5th is reported once, as payroll")
        void manyFailuresOneReason() {
            MemberDebitRow everything = with(with(with(qualifying(), "paymentType", "Payroll Deduction"),
                    "bankName", "Nobody Bank"), "debitDay", 5);
            Preparation prepared = prepare(everything);
            assertThat(prepared.excludedFor(NOT_DEBIT_ORDER)).isEqualTo(1);
            assertThat(prepared.exclusions()).isEmpty();
        }

        @Test
        @DisplayName("an Active debit-order member with no bank and a day of the 5th is reported for the bank")
        void theEarlierListedRuleWins() {
            MemberDebitRow both = with(with(qualifying(), "bankName", "Nobody Bank"), "debitDay", 5);
            assertThat(prepare(both).exclusions()).singleElement()
                    .extracting(RunPreparer.Exclusion::reason).isEqualTo(NO_BANK_ON_RECORD);
        }
    }

    @Nested
    @DisplayName("the line a qualifying member gets")
    class TheLine {

        @Test
        @DisplayName("the flat rate in force, never anything the member record says")
        void theAmountIsTheRate() {
            // MemberDebitRow does not even carry the member's stored subscription: the rule cannot
            // read it by construction. The amount is the rate the run was handed.
            PreparedLine line = prepare(qualifying()).lines().get(0);
            assertThat(line.amount()).isEqualByComparingTo(RATE);
        }

        @Test
        @DisplayName("the member's own day, moved by the window rule: Saturday 25 July is Friday the 24th")
        void theActionDateIsTheWindowsAnswer() {
            PreparedLine line = prepare(qualifying()).lines().get(0);
            assertThat(line.debitDay()).isEqualTo(25);
            assertThat(line.actionDate()).isEqualTo(LocalDate.of(2026, 7, 24));
        }

        @Test
        @DisplayName("a snapshot of the banking, with the bank's own name and universal branch code")
        void theSnapshot() {
            PreparedLine line = prepare(with(qualifying(), "bankName", " alpha bank")).lines().get(0);
            assertThat(line.bankName()).as("the bank as it is on record, not as it was typed").isEqualTo("Alpha Bank");
            assertThat(line.branchCode()).isEqualTo("632005");
            assertThat(line.accountNumber()).isEqualTo("1234567890");
            assertThat(line.membershipNumber()).isEqualTo("M-200001");
            assertThat(line.surname()).isEqualTo("Botha");
            assertThat(line.initials()).isEqualTo("AB");
            assertThat(line.accountType()).as("not on the member record yet").isNull();
            assertThat(line.rePresentsLineId()).isNull();
        }

        @Test
        @DisplayName("its toString never prints the account number")
        void theLineDoesNotPrintTheAccount() {
            PreparedLine line = prepare(qualifying()).lines().get(0);
            assertThat(line.toString()).doesNotContain("1234567890");
            assertThat(qualifying().toString()).doesNotContain("1234567890");
        }
    }

    @Nested
    @DisplayName("re-presentations: a second line, never a combined amount")
    class RePresentations {

        static final RePresentationCandidate JUNE_UNPAID =
                new RePresentationCandidate(501L, 7, new BigDecimal("170.00"), YearMonth.of(2026, 6));

        @Test
        @DisplayName("a member with an unpaid chosen to present again gets two lines, same day, the old amount")
        void twoLines() {
            Preparation prepared = prepare(List.of(qualifying()), List.of(JUNE_UNPAID));
            assertThat(prepared.lines()).hasSize(2);
            PreparedLine current = prepared.lines().get(0);
            PreparedLine again = prepared.lines().get(1);
            assertThat(current.rePresentsLineId()).isNull();
            assertThat(current.amount()).isEqualByComparingTo(RATE);
            assertThat(again.rePresentsLineId()).isEqualTo(501L);
            assertThat(again.amount()).as("the amount of the line it re-presents").isEqualByComparingTo("170.00");
            assertThat(again.actionDate()).isEqualTo(current.actionDate());
            assertThat(prepared.total()).isEqualByComparingTo("344.00");
        }

        @Test
        @DisplayName("an excluded member's unpaid is not presented, and stays unclaimed for a later month")
        void anExcludedMemberGetsNeither() {
            Preparation prepared = prepare(List.of(with(qualifying(), "debitDay", 5)), List.of(JUNE_UNPAID));
            assertThat(prepared.lines()).isEmpty();
            assertThat(prepared.exclusions()).singleElement()
                    .extracting(RunPreparer.Exclusion::reason).isEqualTo(DAY_OUT_OF_WINDOW);
        }

        @Test
        @DisplayName("an unpaid of a member not in this population is simply not presented")
        void aCandidateForSomebodyElse() {
            RePresentationCandidate stranger = new RePresentationCandidate(502L, 999, RATE, YearMonth.of(2026, 6));
            assertThat(prepare(List.of(qualifying()), List.of(stranger)).lines()).hasSize(1);
        }
    }

    @Nested
    @DisplayName("the run's totals and dates")
    class Totals {

        @Test
        @DisplayName("the control total is the sum of the lines, and the dates are their first and last")
        void totalsAreDerived() {
            MemberDebitRow first = with(qualifying(), "debitDay", 20);
            MemberDebitRow second = new MemberDebitRow(8, "M-200002", "Khumalo", "T", "Active", "Debit Order",
                    "Alpha Bank", "9876543210", 30);
            Preparation prepared = prepare(List.of(second, first), List.of());
            assertThat(prepared.lineCount()).isEqualTo(2);
            assertThat(prepared.total()).isEqualByComparingTo("348.00").hasScaleOf(2);
            assertThat(prepared.firstActionDate()).contains(LocalDate.of(2026, 7, 20));
            assertThat(prepared.lastActionDate()).contains(LocalDate.of(2026, 7, 30));
            assertThat(prepared.lines()).extracting(PreparedLine::memberId)
                    .as("in action-date order, whatever order the members came in").containsExactly(7, 8);
        }

        @Test
        @DisplayName("an empty run has a total of zero and no dates -- it is a finding, not an error")
        void anEmptyRun() {
            Preparation prepared = prepare(List.of(), List.of());
            assertThat(prepared.lineCount()).isZero();
            assertThat(prepared.total()).isEqualByComparingTo("0.00");
            assertThat(prepared.firstActionDate()).isEmpty();
        }

        @Test
        @DisplayName("counted values are tallied per raw value")
        void countedValuesAreTallied() {
            MemberDebitRow payroll = with(qualifying(), "paymentType", "Payroll Deduction");
            MemberDebitRow cash = with(qualifying(), "paymentType", "Cash");
            Preparation prepared = prepare(List.of(payroll, payroll, cash), List.of());
            assertThat(prepared.counted().get(NOT_DEBIT_ORDER))
                    .containsExactlyInAnyOrderEntriesOf(Map.of("Payroll Deduction", 2, "Cash", 1));
            assertThat(prepared.excludedFor(NOT_DEBIT_ORDER)).isEqualTo(3);
        }
    }

    @Nested
    @DisplayName("the bank index")
    class Banks {

        @Test
        @DisplayName("a bank with two ACTIVE rows is named as ambiguous; one with one or none is not")
        void ambiguity() {
            BankIndex twice = BankIndex.of(List.of(ALPHA, BETA), List.of(1, 1, 2));
            assertThat(twice.ambiguousBanks()).containsExactly("Alpha Bank");
            assertThat(BANKS.ambiguousBanks()).isEmpty();
        }

        @Test
        @DisplayName("two banks whose names differ only in case match neither -- a guess would be worse")
        void aSharedNameMatchesNothing() {
            BankIndex shared = BankIndex.of(List.of(new BankRow(1, "Gamma", "1"), new BankRow(2, "GAMMA", "2")),
                    List.of(1, 2));
            assertThat(shared.find("gamma")).isEmpty();
            assertThat(shared.sharedByTwoBanks("gamma")).isTrue();
        }

        @Test
        @DisplayName("a subscription row with no bank is ignored, not counted against anyone")
        void aRowWithNoBank() {
            BankIndex index = BankIndex.of(List.of(ALPHA), java.util.Arrays.asList(1, null));
            assertThat(index.activeRowsFor(ALPHA)).isEqualTo(1);
        }
    }
}
