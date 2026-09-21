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
    @DisplayName("the file is made by three days before the first debit; a run with no lines has no such day")
    void makeBy() {
        assertThat(AcbRuns.makeBy(LocalDate.of(2026, 10, 20))).isEqualTo(LocalDate.of(2026, 10, 17));
        assertThat(AcbRuns.makeBy(null)).isNull();
    }

    @Test
    @DisplayName("the day has passed only AFTER it: on the day itself there is still time")
    void makeByPassedAtTheEdge() {
        LocalDate by = LocalDate.of(2026, 10, 17);
        assertThat(AcbRuns.makeByPassed(by, LocalDate.of(2026, 10, 17))).isFalse();
        assertThat(AcbRuns.makeByPassed(by, LocalDate.of(2026, 10, 18))).isTrue();
        assertThat(AcbRuns.makeByPassed(null, LocalDate.of(2026, 10, 18))).isFalse();
    }

    @Test
    @DisplayName("a run is flagged only while its file is still to be made -- never once made, never once sent or cancelled")
    void onlyWhileTheFileIsToBeMade() {
        LocalDate late = LocalDate.of(2026, 10, 19);
        assertThat(AcbRuns.makeByPassed(run(AcbRunStatus.PREPARED, null), late)).isTrue();
        assertThat(AcbRuns.makeByPassed(run(AcbRunStatus.APPROVED, null), late)).isTrue();
        assertThat(AcbRuns.makeByPassed(run(AcbRunStatus.APPROVED, LocalDate.of(2026, 10, 16)), late)).as("made").isFalse();
        assertThat(AcbRuns.makeByPassed(run(AcbRunStatus.SUBMITTED, null), late)).isFalse();
        assertThat(AcbRuns.makeByPassed(run(AcbRunStatus.CANCELLED, null), late)).isFalse();
    }

    private static AcbRun run(AcbRunStatus status, LocalDate made) {
        AcbRun run = new AcbRun();
        run.setStatus(status);
        run.setFirstDueDate(LocalDate.of(2026, 10, 20));
        run.setCreationDate(made);
        return run;
    }
}
