package za.co.titandynamix.sasbo_online_backend.acb.returns;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import za.co.titandynamix.sasbo_online_backend.acb.returns.TerminationRule.Presentation;
import za.co.titandynamix.sasbo_online_backend.enums.AcbLineOutcome;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Three unpaid presentations as a table of month sequences -- SASBO's reading of question 27, every
 * unpaid counts, re-debits included -- written before the rule and asserted against it.
 *
 * <p>A history is a sequence of months, one presentation each unless joined by {@code +} (two on
 * the same file, the month's own debit first): {@code U} the month's own debit returned unpaid,
 * {@code P} paid, {@code R} a re-debit of an earlier unpaid returned again, {@code r} one that was
 * paid, {@code -} not yet answered. A re-debit is written apart from a debit only so the table reads
 * like the months it describes: the rule counts it the same.
 */
@DisplayName("TerminationRule -- three unpaid presentations, every one counts (Q27)")
class TerminationRuleTest {

    @ParameterizedTest(name = "{0}: terminate={1}")
    @CsvSource({
            // history,  terminate
            "U U U,      true",    // three of the month's own debits returned
            "U U+R,      true",    // SASBO's own example: a debit, the next month's and the re-debit of the first -- three in TWO months
            "U R R,      true",    // three unpaid presentations, two of them re-debits: every one counts
            "U R R R,    true",    // more than three is still three
            "R R R,      true",    // three failed re-debits, whatever came before
            "U U,        false",   // two is not three
            "U+R,        false",   // two in one month is two
            "U P U U,    false",   // a paid month in between resets the count (provisional -- Q49)
            "U R r U,    false",   // a re-debit that pays resets it too (provisional -- Q49)
            "U U -,      false",   // an unanswered debit does not count...
            "U U - U,    true",    // ...and does not reset
            "P P P,      false",   // the must-pass twin: a member who pays
    })
    void theTable(String history, boolean terminate) {
        assertThat(TerminationRule.judge(parse(history)).terminate())
                .as("'%s'", history).isEqualTo(terminate);
    }

    @Test
    @DisplayName("the verdict names the months it counted, oldest first")
    void theMonthsAreNamed() {
        TerminationRule.Verdict verdict = TerminationRule.judge(parse("P U U U"));
        assertThat(verdict.count()).isEqualTo(3);
        assertThat(verdict.unpaidMonths()).containsExactly(
                YearMonth.of(2026, 2), YearMonth.of(2026, 3), YearMonth.of(2026, 4));
    }

    @Test
    @DisplayName("a month whose debit and re-debit both came back is named twice -- three counted, two months")
    void aMonthCanCountTwice() {
        TerminationRule.Verdict verdict = TerminationRule.judge(parse("P U U+R"));
        assertThat(verdict.count()).isEqualTo(3);
        assertThat(verdict.unpaidMonths()).containsExactly(
                YearMonth.of(2026, 2), YearMonth.of(2026, 3), YearMonth.of(2026, 3));
    }

    @Test
    @DisplayName("the order the lines arrive in does not matter -- the dates do")
    void orderIsByDate() {
        List<Presentation> shuffled = new ArrayList<>(parse("U P U U"));
        Collections.reverse(shuffled);
        assertThat(TerminationRule.judge(shuffled).terminate()).isFalse();
    }

    /** One month per step from January 2026, the 25th of each; a {@code +} puts two on that date. */
    private static List<Presentation> parse(String history) {
        List<Presentation> presentations = new ArrayList<>();
        String[] months = history.trim().split("\\s+");
        long lineId = 100L;
        for (int i = 0; i < months.length; i++) {
            LocalDate date = LocalDate.of(2026, 1, 25).plusMonths(i);
            for (String step : months[i].split("\\+")) {
                AcbLineOutcome outcome = switch (step) {
                    case "U", "R" -> AcbLineOutcome.UNPAID;
                    case "P", "r" -> AcbLineOutcome.PAID;
                    case "-" -> AcbLineOutcome.PENDING;
                    default -> throw new IllegalArgumentException(step);
                };
                presentations.add(new Presentation(lineId++, date, outcome));
            }
        }
        return presentations;
    }
}
