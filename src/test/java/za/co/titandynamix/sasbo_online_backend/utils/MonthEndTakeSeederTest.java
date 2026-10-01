package za.co.titandynamix.sasbo_online_backend.utils;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import za.co.titandynamix.sasbo_online_backend.config.SasboEnvironment;
import za.co.titandynamix.sasbo_online_backend.config.WorkplaceSwitch;
import za.co.titandynamix.sasbo_online_backend.exception.MonthEndRefusal;
import za.co.titandynamix.sasbo_online_backend.exception.MonthEndRefusalCode;
import za.co.titandynamix.sasbo_online_backend.repository.MonthEndFactsRepository.TakenMonth;
import za.co.titandynamix.sasbo_online_backend.services.MonthEndTakes;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The demonstration's past months, taken (month end 04.2) -- where, and which: only where the switch says {@code all}
 * and never under the production marker (MD53); the three months before the clock's, the oldest first, a month taken
 * already left alone; none where no workplace is on record (MD54).
 */
@DisplayName("The demonstration's past months, taken")
class MonthEndTakeSeederTest {

    /** An hour into the first of October in Johannesburg -- and still the last of September in UTC (rule M). */
    private static final Clock LATE_SEPTEMBER = Clock.fixed(Instant.parse("2026-09-30T23:00:00Z"), ZoneOffset.UTC);

    private final MonthEndTakes takes = mock(MonthEndTakes.class);

    private MonthEndTakeSeeder seeder(String environment, String workplaces) {
        return new MonthEndTakeSeeder(takes, new SasboEnvironment(environment), new WorkplaceSwitch(workplaces),
                LATE_SEPTEMBER);
    }

    @Test
    @DisplayName("🔒 never under the production marker, and never below 'all' -- nothing is even read")
    void onlyWhereAllowed() {
        assertThat(seeder("production", "all").seed()).isZero();
        assertThat(seeder("", "names").seed()).isZero();
        assertThat(seeder("", "none").seed()).isZero();
        verify(takes, never()).takenMonths();
        verify(takes, never()).take(any(), any());
    }

    @Test
    @DisplayName("the three months before the clock's -- in SASBO's zone -- the oldest first, a month taken already left alone")
    void theThreeMonthsBefore() {
        when(takes.takenMonths()).thenReturn(List.of(new TakenMonth(1, LocalDate.of(2026, 7, 1), "seed",
                Instant.parse("2026-08-01T06:00:00Z"), null)));

        assertThat(seeder("", "all").seed()).as("October in Johannesburg: July, August and September").isEqualTo(2);

        verify(takes).take(YearMonth.of(2026, 8), DemonstrationMemberSeeder.SEED_ACTOR);
        verify(takes).take(YearMonth.of(2026, 9), DemonstrationMemberSeeder.SEED_ACTOR);
        verify(takes, never()).take(eq(YearMonth.of(2026, 7)), any());
        verify(takes, never()).take(eq(YearMonth.of(2026, 10)), any());
    }

    @Test
    @DisplayName("🔒 MD54: with no workplace on record it takes none, and says so in one line")
    void noneWithoutWorkplaces() {
        when(takes.takenMonths()).thenReturn(List.of());
        when(takes.take(any(), any())).thenThrow(new MonthEndRefusal(MonthEndRefusalCode.MONTH_NOT_READY, "none"));

        assertThat(seeder("", "all").seed()).isZero();
        verify(takes).take(any(), any());
    }

    @Test
    @DisplayName("a month another start took meanwhile is the index's refusal -- the same answer, and the next is taken")
    void takenMeanwhile() {
        when(takes.takenMonths()).thenReturn(List.of());
        when(takes.take(eq(YearMonth.of(2026, 7)), any()))
                .thenThrow(new MonthEndRefusal(MonthEndRefusalCode.MONTH_ALREADY_TAKEN, "taken"));

        assertThat(seeder("", "all").seed()).isEqualTo(2);
    }
}
