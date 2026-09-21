package za.co.titandynamix.sasbo_online_backend.services.impl;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.YearMonth;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 🔒 F62: a seeded return is noted two days after its debit, and never after its own month ends --
 * or the seed dates a return, and its letter, in the clock's own month or after the clock.
 */
@DisplayName("AcbHistorySeeder.notedOn -- two days on, inside the month")
class AcbHistorySeederNotedOnTest {

    private static final YearMonth OCTOBER = YearMonth.of(2026, 10);

    @Test
    @DisplayName("a debit on Friday 30 October is noted on the 31st, not on 1 November")
    void theMonthsLastDaysAreHeldInside() {
        assertThat(AcbHistorySeeder.notedOn(LocalDate.of(2026, 10, 30), OCTOBER)).isEqualTo(LocalDate.of(2026, 10, 31));
        assertThat(AcbHistorySeeder.notedOn(LocalDate.of(2026, 10, 31), OCTOBER)).isEqualTo(LocalDate.of(2026, 10, 31));
    }

    @Test
    @DisplayName("the must-pass twin: a debit earlier in the month is noted two days on, as the report arrives")
    void twoDaysOnOtherwise() {
        assertThat(AcbHistorySeeder.notedOn(LocalDate.of(2026, 10, 20), OCTOBER)).isEqualTo(LocalDate.of(2026, 10, 22));
        assertThat(AcbHistorySeeder.notedOn(LocalDate.of(2026, 10, 29), OCTOBER)).isEqualTo(LocalDate.of(2026, 10, 31));
    }
}
