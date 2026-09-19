package za.co.titandynamix.sasbo_online_backend.acb.returns;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import za.co.titandynamix.sasbo_online_backend.acb.returns.TerminationRule.Presentation;
import za.co.titandynamix.sasbo_online_backend.acb.returns.TerminationRule.Reading;
import za.co.titandynamix.sasbo_online_backend.enums.AcbLineOutcome;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * "Three unsuccessful retries" as a table of month sequences, with BOTH readings SASBO has been
 * asked to choose between, written before the rule and asserted against it.
 *
 * <p>A history is a sequence of presentations, one a month: {@code U} the month's own debit
 * returned unpaid, {@code P} paid, {@code R} a presentation AGAIN of an earlier unpaid that was
 * returned again, {@code r} one that was paid, {@code -} not yet answered.
 */
@DisplayName("TerminationRule -- three unsuccessful presentations, both readings")
class TerminationRuleTest {

    @ParameterizedTest(name = "{0}: every-unpaid-counts={1}, retries-only={2}")
    @CsvSource({
            // history,  terminate under EVERY_UNPAID_COUNTS, under RETRIES_ONLY
            "U U U,      true,  false",   // three of the month's own debits returned, no retries
            "U R R R,    true,  true",    // the first unpaid, then three failed retries
            "U R R,      true,  false",   // three unpaid presentations -- but only two retries
            "U U,        false, false",   // two is not three
            "U P U U,    false, false",   // a paid month in between resets the count
            "U R r U,    false, false",   // a retry that pays resets it too
            "U R R -,    true,  false",   // an unanswered debit neither counts nor resets
            "R R R,      true,  true",    // three failed retries, whatever came before
            "P P P,      false, false",   // the must-pass twin: a member who pays
    })
    void theTable(String history, boolean everyUnpaid, boolean retriesOnly) {
        List<Presentation> presentations = parse(history);
        assertThat(TerminationRule.judge(presentations, Reading.EVERY_UNPAID_COUNTS).terminate())
                .as("'%s' under every-unpaid-counts", history).isEqualTo(everyUnpaid);
        assertThat(TerminationRule.judge(presentations, Reading.RETRIES_ONLY).terminate())
                .as("'%s' under retries-only", history).isEqualTo(retriesOnly);
    }

    @Test
    @DisplayName("the verdict names the months it counted, oldest first")
    void theMonthsAreNamed() {
        TerminationRule.Verdict verdict = TerminationRule.judge(parse("P U U U"), Reading.EVERY_UNPAID_COUNTS);
        assertThat(verdict.count()).isEqualTo(3);
        assertThat(verdict.unpaidMonths()).containsExactly(
                YearMonth.of(2026, 2), YearMonth.of(2026, 3), YearMonth.of(2026, 4));
    }

    @Test
    @DisplayName("the order the lines arrive in does not matter -- the dates do")
    void orderIsByDate() {
        List<Presentation> shuffled = new ArrayList<>(parse("U P U U"));
        Collections.reverse(shuffled);
        assertThat(TerminationRule.judge(shuffled, Reading.EVERY_UNPAID_COUNTS).terminate()).isFalse();
    }

    /** One presentation per month from January 2026, the 25th of each. */
    private static List<Presentation> parse(String history) {
        List<Presentation> presentations = new ArrayList<>();
        String[] steps = history.trim().split("\\s+");
        for (int i = 0; i < steps.length; i++) {
            LocalDate date = LocalDate.of(2026, 1, 25).plusMonths(i);
            AcbLineOutcome outcome = switch (steps[i]) {
                case "U", "R" -> AcbLineOutcome.UNPAID;
                case "P", "r" -> AcbLineOutcome.PAID;
                case "-" -> AcbLineOutcome.PENDING;
                default -> throw new IllegalArgumentException(steps[i]);
            };
            boolean retry = steps[i].equals("R") || steps[i].equals("r");
            presentations.add(new Presentation(100L + i, date, retry, outcome));
        }
        return presentations;
    }
}
