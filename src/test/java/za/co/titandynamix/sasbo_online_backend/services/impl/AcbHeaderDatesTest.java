package za.co.titandynamix.sasbo_online_backend.services.impl;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import za.co.titandynamix.sasbo_online_backend.acb.AcbCalendars;
import za.co.titandynamix.sasbo_online_backend.acb.HolidayCalendar;
import za.co.titandynamix.sasbo_online_backend.entity.acb.AcbRun;
import za.co.titandynamix.sasbo_online_backend.enums.AcbRunStatus;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The bureau file's header dates as SASBO keeps them (D31, Q37): the purge date ten days after the
 * last debit, moved back to a business day; the file made by three days before the first. The
 * creation date is the day the file is made -- stamped at render, asserted in
 * {@code AcbFileServiceImplIT}. 🧪 Every rule here takes its date as an argument (T2-G).
 */
@DisplayName("The header's dates (02.7, D31)")
class AcbHeaderDatesTest {

    private static final HolidayCalendar STATUTORY = AcbCalendars.ZA_STATUTORY;

    @Test
    @DisplayName("July 2026 reproduced: a last debit on 30 July purges on Friday 7 August -- the 9th is a Sunday and Women's Day")
    void julysSeventhOfAugust() {
        assertThat(AcbPreparationJob.purgeDate(LocalDate.of(2026, 7, 30), STATUTORY)).isEqualTo(LocalDate.of(2026, 8, 7));
    }

    @Test
    @DisplayName("the calendar moves it off a holiday on a weekday: 15 December's purge leaves Christmas Day for the 24th")
    void offAWeekdayHoliday() {
        assertThat(AcbPreparationJob.purgeDate(LocalDate.of(2026, 12, 15), STATUTORY)).isEqualTo(LocalDate.of(2026, 12, 24));
        // The twin: on weekends alone, the Friday stands -- the table is what moved it.
        assertThat(AcbPreparationJob.purgeDate(LocalDate.of(2026, 12, 15), HolidayCalendar.WEEKENDS_ONLY))
                .isEqualTo(LocalDate.of(2026, 12, 25));
    }

    @Test
    @DisplayName("the must-pass twin: a purge date already on a business day stays ten days out")
    void aBusinessDayStays() {
        assertThat(AcbPreparationJob.purgeDate(LocalDate.of(2026, 10, 30), STATUTORY)).isEqualTo(LocalDate.of(2026, 11, 9));
    }

    @Test
    @DisplayName("Stephne, DOD-16: the file is made by three WORKING days before the first debit -- October 2026's first "
            + "debit on Tuesday the 20th makes it Thursday the 15th, the day SASBO runs October, never Saturday the 17th")
    void makeByIsThreeWorkingDaysBack() {
        assertThat(AcbRuns.makeBy(LocalDate.of(2026, 10, 20), STATUTORY)).isEqualTo(LocalDate.of(2026, 10, 15));
        assertThat(AcbRuns.makeBy(null, STATUTORY)).as("a run with no lines has no such day").isNull();
    }

    @Test
    @DisplayName("Stephne, DOD-16: a public holiday inside the three days is skipped like a weekend -- Heritage Day "
            + "(Thursday 24 September 2026) moves a first debit on Monday the 28th back to Tuesday the 22nd")
    void makeBySkipsAPublicHoliday() {
        assertThat(AcbRuns.makeBy(LocalDate.of(2026, 9, 28), STATUTORY)).isEqualTo(LocalDate.of(2026, 9, 22));
        // The twin on weekends alone: the holiday is what moved it, by one day.
        assertThat(AcbRuns.makeBy(LocalDate.of(2026, 9, 28), HolidayCalendar.WEEKENDS_ONLY))
                .isEqualTo(LocalDate.of(2026, 9, 23));
    }

    @Test
    @DisplayName("Stephne, DOD-16: Christmas, the Day of Goodwill and a weekend inside the window -- a first debit on "
            + "Tuesday 29 December 2026 is made by Wednesday the 23rd")
    void makeBySkipsAHolidayRun() {
        assertThat(AcbRuns.makeBy(LocalDate.of(2026, 12, 29), STATUTORY)).isEqualTo(LocalDate.of(2026, 12, 23));
    }

    @Test
    @DisplayName("Stephne, DOD-16: a bank closure in the calendar is a non-working day like any other")
    void makeBySkipsABankClosure() {
        HolidayCalendar withAClosure = HolidayCalendar.of("za-statutory", LocalDate.of(2027, 12, 31),
                java.util.stream.Stream.concat(AcbCalendars.ZA_STATUTORY_2026_2027.stream(),
                        java.util.stream.Stream.of(LocalDate.of(2026, 10, 16))).toList());
        assertThat(AcbRuns.makeBy(LocalDate.of(2026, 10, 20), withAClosure)).isEqualTo(LocalDate.of(2026, 10, 14));
    }

    @Test
    @DisplayName("the day has passed only AFTER it: on the day itself there is still time")
    void makeByPassedAtTheEdge() {
        LocalDate by = LocalDate.of(2026, 10, 17);
        assertThat(AcbRuns.makeByPassed(by, LocalDate.of(2026, 10, 17))).isFalse();
        assertThat(AcbRuns.makeByPassed(by, LocalDate.of(2026, 10, 18))).isTrue();
        assertThat(AcbRuns.makeByPassed((LocalDate) null, LocalDate.of(2026, 10, 18))).isFalse();
    }

    @Test
    @DisplayName("a run is flagged only while its file is still to be made -- never once made, never once sent or cancelled")
    void onlyWhileTheFileIsToBeMade() {
        LocalDate late = LocalDate.of(2026, 10, 19);
        assertThat(AcbRuns.makeByPassed(run(AcbRunStatus.PREPARED, null), late, STATUTORY)).isTrue();
        assertThat(AcbRuns.makeByPassed(run(AcbRunStatus.APPROVED, null), late, STATUTORY)).isTrue();
        assertThat(AcbRuns.makeByPassed(run(AcbRunStatus.APPROVED, LocalDate.of(2026, 10, 16)), late, STATUTORY))
                .as("made").isFalse();
        assertThat(AcbRuns.makeByPassed(run(AcbRunStatus.SUBMITTED, null), late, STATUTORY)).isFalse();
        assertThat(AcbRuns.makeByPassed(run(AcbRunStatus.CANCELLED, null), late, STATUTORY)).isFalse();
        // Thursday the 15th is October's day: on it there is still time; on Friday the 16th it has passed.
        assertThat(AcbRuns.makeByPassed(run(AcbRunStatus.PREPARED, null), LocalDate.of(2026, 10, 15), STATUTORY)).isFalse();
        assertThat(AcbRuns.makeByPassed(run(AcbRunStatus.PREPARED, null), LocalDate.of(2026, 10, 16), STATUTORY)).isTrue();
    }

    private static AcbRun run(AcbRunStatus status, LocalDate made) {
        AcbRun run = new AcbRun();
        run.setStatus(status);
        run.setFirstDueDate(LocalDate.of(2026, 10, 20));
        run.setCreationDate(made);
        return run;
    }
}
