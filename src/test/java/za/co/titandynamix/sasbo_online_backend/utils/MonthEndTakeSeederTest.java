package za.co.titandynamix.sasbo_online_backend.utils;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import za.co.titandynamix.sasbo_online_backend.acb.HolidayCalendar;
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
import java.util.Set;

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
        return seeder(environment, workplaces, HolidayCalendar.WEEKENDS_ONLY);
    }

    private MonthEndTakeSeeder seeder(String environment, String workplaces, HolidayCalendar calendar) {
        return new MonthEndTakeSeeder(takes, new SasboEnvironment(environment), new WorkplaceSwitch(workplaces),
                LATE_SEPTEMBER, () -> calendar);
    }

    /** 07:10 in Johannesburg on a day -- when SASBO takes a month. */
    private static Instant tenPastSeven(int year, int month, int day) {
        return LocalDate.of(year, month, day).atTime(7, 10).atZone(MonthRange.ZONE).toInstant();
    }

    @Test
    @DisplayName("🔒 never under the production marker, and never below 'all' -- nothing is even read")
    void onlyWhereAllowed() {
        assertThat(seeder("production", "all").seed()).isZero();
        assertThat(seeder("", "names").seed()).isZero();
        assertThat(seeder("", "none").seed()).isZero();
        verify(takes, never()).takenMonths();
        verify(takes, never()).take(any(), any());
        verify(takes, never()).takeAsAt(any(), any(), any());
    }

    @Test
    @DisplayName("the three months before the clock's -- in SASBO's zone -- the oldest first, a month taken already left alone")
    void theThreeMonthsBefore() {
        when(takes.takenMonths()).thenReturn(List.of(new TakenMonth(1, LocalDate.of(2026, 7, 1), "seed",
                Instant.parse("2026-08-01T06:00:00Z"), null)));

        assertThat(seeder("", "all").seed()).as("October in Johannesburg: July, August and September").isEqualTo(2);

        // Each at 07:10 on its last working day: Monday 31 August, Wednesday 30 September.
        verify(takes).takeAsAt(YearMonth.of(2026, 8), DemonstrationMemberSeeder.SEED_ACTOR, tenPastSeven(2026, 8, 31));
        verify(takes).takeAsAt(YearMonth.of(2026, 9), DemonstrationMemberSeeder.SEED_ACTOR, tenPastSeven(2026, 9, 30));
        verify(takes, never()).takeAsAt(eq(YearMonth.of(2026, 7)), any(), any());
        verify(takes, never()).takeAsAt(eq(YearMonth.of(2026, 10)), any(), any());
    }

    @Test
    @DisplayName("SASBO takes a month at 07:10 on its last working day -- a weekend and a public holiday of the calendar skipped")
    void takenOnTheLastWorkingDay() {
        assertThat(MonthEndTakeSeeder.takenAt(YearMonth.of(2026, 7), HolidayCalendar.WEEKENDS_ONLY))
                .as("Friday 31 July").isEqualTo(tenPastSeven(2026, 7, 31));
        assertThat(MonthEndTakeSeeder.takenAt(YearMonth.of(2026, 10), HolidayCalendar.WEEKENDS_ONLY))
                .as("Saturday 31 October falls back to Friday 30 October").isEqualTo(tenPastSeven(2026, 10, 30));
        HolidayCalendar planted = HolidayCalendar.of("planted", LocalDate.of(2026, 12, 31), Set.of(LocalDate.of(2026, 8, 31)));
        assertThat(MonthEndTakeSeeder.takenAt(YearMonth.of(2026, 8), planted))
                .as("a public holiday on Monday 31 August falls back past the weekend to Friday 28 August")
                .isEqualTo(tenPastSeven(2026, 8, 28));
        assertThat(tenPastSeven(2026, 9, 30)).as("07:10 SAST is 05:10 UTC").isEqualTo(Instant.parse("2026-09-30T05:10:00Z"));
    }

    @Test
    @DisplayName("🔒 MD54: with no workplace on record it takes none, and says so in one line")
    void noneWithoutWorkplaces() {
        when(takes.takenMonths()).thenReturn(List.of());
        when(takes.takeAsAt(any(), any(), any()))
                .thenThrow(new MonthEndRefusal(MonthEndRefusalCode.MONTH_NOT_READY, "none"));

        assertThat(seeder("", "all").seed()).isZero();
        verify(takes).takeAsAt(any(), any(), any());
    }

    @Test
    @DisplayName("MD70, MEN-11: where a demonstration seeder wrote rows, the seed's own past months are taken again, saying why -- a month a person took last never")
    void theSeedsOwnMonthsTakenAgain() {
        when(takes.takenMonths()).thenReturn(List.of(
                new TakenMonth(3, LocalDate.of(2026, 9, 1), "an administrator", Instant.parse("2026-10-01T06:00:00Z"), null),
                new TakenMonth(2, LocalDate.of(2026, 8, 1), "seed", Instant.parse("2026-10-01T06:00:00Z"), null),
                new TakenMonth(1, LocalDate.of(2026, 7, 1), "seed", Instant.parse("2026-10-01T06:00:00Z"), null)));

        assertThat(seeder("", "all").seed(true)).isEqualTo(2);

        verify(takes).take(YearMonth.of(2026, 7), DemonstrationMemberSeeder.SEED_ACTOR, 1L,
                MonthEndTakeSeeder.TAKEN_AGAIN_BECAUSE);
        verify(takes).take(YearMonth.of(2026, 8), DemonstrationMemberSeeder.SEED_ACTOR, 2L,
                MonthEndTakeSeeder.TAKEN_AGAIN_BECAUSE);
        verify(takes, never()).take(eq(YearMonth.of(2026, 9)), any(), any(), any());
        verify(takes, never()).takeAsAt(any(), any(), any());
    }

    @Test
    @DisplayName("🔒 MR-PERF-19: where nothing was written, a month taken is left as it was -- nothing taken again")
    void nothingWrittenNothingTakenAgain() {
        when(takes.takenMonths()).thenReturn(List.of(
                new TakenMonth(2, LocalDate.of(2026, 8, 1), "seed", Instant.parse("2026-10-01T06:00:00Z"), null),
                new TakenMonth(1, LocalDate.of(2026, 7, 1), "seed", Instant.parse("2026-10-01T06:00:00Z"), null)));

        assertThat(seeder("", "all").seed(false)).as("September only, taken for the first time").isEqualTo(1);

        verify(takes).takeAsAt(YearMonth.of(2026, 9), DemonstrationMemberSeeder.SEED_ACTOR, tenPastSeven(2026, 9, 30));
        verify(takes, never()).take(any(), any(), any(), any());
    }

    @Test
    @DisplayName("a month another start took again meanwhile is the index's refusal -- the same answer, and the next is taken again")
    void takenAgainMeanwhile() {
        when(takes.takenMonths()).thenReturn(List.of(
                new TakenMonth(2, LocalDate.of(2026, 8, 1), "seed", Instant.parse("2026-10-01T06:00:00Z"), null),
                new TakenMonth(1, LocalDate.of(2026, 7, 1), "seed", Instant.parse("2026-10-01T06:00:00Z"), null)));
        when(takes.take(eq(YearMonth.of(2026, 7)), any(), any(), any()))
                .thenThrow(new MonthEndRefusal(MonthEndRefusalCode.MONTH_TAKEN_SINCE, "taken again"));

        assertThat(seeder("", "all").seed(true)).as("August taken again, September taken").isEqualTo(2);
    }

    @Test
    @DisplayName("a month another start took meanwhile is the index's refusal -- the same answer, and the next is taken")
    void takenMeanwhile() {
        when(takes.takenMonths()).thenReturn(List.of());
        when(takes.takeAsAt(eq(YearMonth.of(2026, 7)), any(), any()))
                .thenThrow(new MonthEndRefusal(MonthEndRefusalCode.MONTH_ALREADY_TAKEN, "taken"));

        assertThat(seeder("", "all").seed()).isEqualTo(2);
    }
}
