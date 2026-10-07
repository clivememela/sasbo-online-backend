package za.co.titandynamix.sasbo_online_backend.payroll;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.YearMonth;

import static org.assertj.core.api.Assertions.assertThat;

/** How numbers on a payroll line are compared -- each habit from SASBO's samples (fixtures' LAYOUTS.md, rules 3-6). */
@DisplayName("Payroll keys: staff numbers, ID numbers, amounts, months")
class PayrollKeysTest {

    @Test
    @DisplayName("a staff number compares as its digits without leading noughts, whatever its decorations")
    void staffNumbers() {
        assertThat(PayrollKeys.staffKey("0900006")).isEqualTo("900006");
        assertThat(PayrollKeys.staffKey("900006")).isEqualTo("900006");
        assertThat(PayrollKeys.staffKey("‭990005‬")).as("Old Mutual's direction marks").isEqualTo("990005");
        assertThat(PayrollKeys.staffKey(" 99000041 ")).isEqualTo("99000041");
        assertThat(PayrollKeys.staffKey("EMP-99850")).isEqualTo("EMP99850");
        assertThat(PayrollKeys.staffKey("0000")).isEqualTo("0");
        assertThat(PayrollKeys.staffKey("  ")).isNull();
        assertThat(PayrollKeys.staffKey(null)).isNull();
    }

    @Test
    @DisplayName("an ID number loses FNB's apostrophe and Access's ID prefix; thirteen digits are a South African one")
    void idNumbers() {
        assertThat(PayrollKeys.idKey("'8106145689086")).isEqualTo("8106145689086");
        assertThat(PayrollKeys.idKey("ID8106145689086")).isEqualTo("8106145689086");
        assertThat(PayrollKeys.idKey("810614 5689 086")).isEqualTo("8106145689086");
        assertThat(PayrollKeys.isSaId("8106145689086")).isTrue();
        assertThat(PayrollKeys.isSaId("A1234567")).isFalse();
        assertThat(PayrollKeys.masked("8106145689086")).isEqualTo("****9086");
        assertThat(PayrollKeys.masked("123")).isEqualTo("****");
    }

    @Test
    @DisplayName("an amount is read however it is written, its sign dropped -- five employers store deductions negative")
    void amounts() {
        assertThat(PayrollKeys.amount("-174")).isEqualByComparingTo("174.00");
        assertThat(PayrollKeys.amount("  174.00")).as("Access's text amount").isEqualByComparingTo("174.00");
        assertThat(PayrollKeys.amount("R1 218,00")).isEqualByComparingTo("1218.00");
        assertThat(PayrollKeys.amount("1,218.00")).isEqualByComparingTo("1218.00");
        assertThat(PayrollKeys.amount("(174.00)")).isEqualByComparingTo("174.00");
        assertThat(PayrollKeys.amount("174.00-")).isEqualByComparingTo("174.00");
        assertThat(PayrollKeys.amount("166.65")).isEqualByComparingTo("166.65");
        assertThat(PayrollKeys.amount("174.00000000000003")).as("a formula's cached tail").isEqualByComparingTo("174.00");
        assertThat(PayrollKeys.signedAmount("-174")).isEqualByComparingTo("-174.00");
        assertThat(PayrollKeys.amount("n/a")).isNull();
        assertThat(PayrollKeys.amount("")).isNull();
        assertThat(PayrollKeys.money(new BigDecimal("174"))).isEqualTo("174.00");
    }

    @Test
    @DisplayName("a For-period reads as Absa's number, Nedbank's text, a month or a day")
    void months() {
        assertThat(PayrollKeys.forPeriod("202608")).isEqualTo(YearMonth.of(2026, 8));
        assertThat(PayrollKeys.forPeriod("2026-08")).isEqualTo(YearMonth.of(2026, 8));
        assertThat(PayrollKeys.forPeriod("2026-08-25")).isEqualTo(YearMonth.of(2026, 8));
        assertThat(PayrollKeys.forPeriod("202613")).isNull();
        assertThat(PayrollKeys.forPeriod("soon")).isNull();
    }
}
