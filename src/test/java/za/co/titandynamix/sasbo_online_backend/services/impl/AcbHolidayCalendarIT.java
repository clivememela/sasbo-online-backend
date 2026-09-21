package za.co.titandynamix.sasbo_online_backend.services.impl;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import za.co.titandynamix.sasbo_online_backend.acb.AcbCalendars;
import za.co.titandynamix.sasbo_online_backend.acb.DebitWindow;
import za.co.titandynamix.sasbo_online_backend.acb.HolidayCalendar;
import za.co.titandynamix.sasbo_online_backend.acb.HolidayCalendarSource;
import za.co.titandynamix.sasbo_online_backend.dto.acb.AcbRunDto;
import za.co.titandynamix.sasbo_online_backend.entity.acb.AcbHoliday;
import za.co.titandynamix.sasbo_online_backend.enums.AcbRunStatus;
import za.co.titandynamix.sasbo_online_backend.exception.AcbRefusal;
import za.co.titandynamix.sasbo_online_backend.exception.AcbRefusalCode;
import za.co.titandynamix.sasbo_online_backend.fixtures.AcbRunFixture;
import za.co.titandynamix.sasbo_online_backend.repository.AcbHolidayRepository;
import za.co.titandynamix.sasbo_online_backend.services.AcbRunService;
import za.co.titandynamix.sasbo_online_backend.support.AbstractAcbIT;
import za.co.titandynamix.sasbo_online_backend.support.MigrationSeed;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

/**
 * The holiday calendar as data (D30), on the real database: the table V47 seeds, the one query that
 * reads it, and the refusal of a month it does not reach (R1-18).
 */
@DisplayName("The holiday calendar, from V47's table")
class AcbHolidayCalendarIT extends AbstractAcbIT {

    @Autowired
    private HolidayCalendarSource calendars;

    @Autowired
    private AcbRunService service;

    @Autowired
    private AcbHolidayRepository holidays;

    @Test
    @DisplayName("🔒 F47: the migrated table IS MigrationSeed.ACB_HOLIDAYS -- every date and name, all statutory -- and known until 2027-12-31")
    void theTableIsTheOneList() {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT holiday_date, name, source, added_by FROM acb_holidays ORDER BY holiday_date");
        List<MigrationSeed.Holiday> migrated = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            migrated.add(new MigrationSeed.Holiday(((java.sql.Date) row.get("holiday_date")).toLocalDate(),
                    (String) row.get("name")));
            assertThat(row.get("source")).isEqualTo("STATUTORY");
            assertThat(row.get("added_by")).isNull();
        }
        assertThat(migrated).containsExactlyElementsOf(MigrationSeed.ACB_HOLIDAYS);
        assertThat(jdbc.queryForMap("SELECT name, known_until FROM acb_calendar WHERE id = 1"))
                .containsEntry("name", MigrationSeed.ACB_CALENDAR_NAME)
                .containsEntry("known_until", java.sql.Date.valueOf(MigrationSeed.ACB_CALENDAR_KNOWN_UNTIL));
    }

    @Test
    @DisplayName("the repository's window is inclusive at both ends and in date order -- how a year of the calendar is read")
    void theRepositoryWindowIsInclusiveAndOrdered() {
        assertThat(holidays.findByHolidayDateBetweenOrderByHolidayDate(LocalDate.of(2026, 12, 16), LocalDate.of(2026, 12, 26)))
                .extracting(AcbHoliday::getHolidayDate)
                .containsExactly(LocalDate.of(2026, 12, 16), LocalDate.of(2026, 12, 25), LocalDate.of(2026, 12, 26));
        assertThat(holidays.findByHolidayDateBetweenOrderByHolidayDate(LocalDate.of(2026, 12, 17), LocalDate.of(2026, 12, 24)))
                .as("the days between them hold no holiday").isEmpty();
    }

    @Test
    @DisplayName("production reads it: the source loads exactly the tests' calendar, in one statement")
    void theSourceLoadsTheTable() {
        HolidayCalendar loaded = calendars.load();
        assertThat(loaded.name()).isEqualTo(AcbCalendars.ZA_STATUTORY.name());
        assertThat(loaded.knownUntil()).isEqualTo(AcbCalendars.ZA_STATUTORY.knownUntil());
        assertThat(loaded.dates()).containsExactlyElementsOf(AcbCalendars.ZA_STATUTORY.dates());
    }

    @Test
    @DisplayName("🔒 R1-18: a month beyond known-until is refused HOLIDAYS_NOT_KNOWN, naming the date -- and the last month it reaches prepares")
    void aMonthBeyondTheCalendarIsRefused() {
        AcbRunFixture.insert(jdbc, 15, 2).assertSize();
        jdbc.update("UPDATE acb_calendar SET known_until = DATE '2026-09-30' WHERE id = 1");

        AcbRefusal refused = catchThrowableOfType(AcbRefusal.class, () -> service.prepare("2026-10", "system"));
        assertThat(refused.code()).isEqualTo(AcbRefusalCode.HOLIDAYS_NOT_KNOWN);
        assertThat(refused.details()).containsEntry("knownUntil", "2026-09-30");
        assertThat(refused.getMessage()).contains("2026-09-30");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM acb_runs", Long.class)).as("no run is made").isZero();

        // The must-pass twin: September, the last month the calendar reaches, prepares.
        AcbRunDto september = service.get(service.prepare("2026-09", "system").id());
        assertThat(september.status()).isEqualTo(AcbRunStatus.PREPARED);
        assertThat(september.holidaySet()).isEqualTo("za-statutory");
        assertThat(september.calendarAgrees()).isTrue();
    }

    @Test
    @DisplayName("🔒 R1-18: no action date in a prepared run is a holiday of the calendar it names -- Heritage Day's debits move to Friday the 25th")
    void noLineFallsOnAHoliday() {
        AcbRunFixture.insert(jdbc, 15, 2).assertSize();
        long run = service.prepare("2026-09", "system").id();

        assertThat(jdbc.queryForObject("SELECT count(*) FROM acb_run_lines l JOIN acb_holidays h "
                + "ON h.holiday_date = l.action_date WHERE l.run_id = ?", Long.class, run)).isZero();
        // Non-vacuity: the fixture has members on the 24th, and the table moved them off it.
        assertThat(jdbc.queryForList("SELECT DISTINCT action_date FROM acb_run_lines WHERE run_id = ? AND debit_day = 24",
                java.sql.Date.class, run)).containsExactly(java.sql.Date.valueOf(LocalDate.of(2026, 9, 25)));
    }

    @Test
    @DisplayName("R1-18's property: over the fixture's 24 months, every debit day lands on a business day of the table's calendar, in its month")
    void everyDateOfTheRangeIsABusinessDay() {
        HolidayCalendar loaded = calendars.load();
        DebitWindow window = new DebitWindow(loaded);
        int checked = 0;
        for (YearMonth month : AcbCalendars.months()) {
            for (int day = 20; day <= 31; day++) {
                LocalDate date = window.actionDate(month, day);
                assertThat(loaded.isBusinessDay(date)).as("%s, day %d -> %s", month, day, date).isTrue();
                assertThat(YearMonth.from(date)).isEqualTo(month);
                checked++;
            }
        }
        assertThat(checked).isEqualTo(24 * 12);
    }
}
