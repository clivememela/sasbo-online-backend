package za.co.titandynamix.sasbo_online_backend.services.impl;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import za.co.titandynamix.sasbo_online_backend.dto.acb.AcbCalendarDto;
import za.co.titandynamix.sasbo_online_backend.dto.acb.AcbHolidayDto;
import za.co.titandynamix.sasbo_online_backend.exception.AcbRefusal;
import za.co.titandynamix.sasbo_online_backend.exception.AcbRefusalCode;
import za.co.titandynamix.sasbo_online_backend.services.AcbCalendarService;
import za.co.titandynamix.sasbo_online_backend.support.AbstractAcbIT;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

/**
 * The holiday calendar's writes, on the real database (D30): each refusal by its code, and 🔒 each
 * change attributed -- one run-less audit row, written in the change's own transaction (SD4-A, R1-24).
 */
@DisplayName("AcbCalendarService -- the calendar's reads and writes")
class AcbCalendarServiceIT extends AbstractAcbIT {

    @Autowired
    private AcbCalendarService calendar;

    @Autowired
    private PlatformTransactionManager transactions;

    @Test
    @DisplayName("a year reads as the calendar, its known-until and that year's days, in order")
    void aYearReads() {
        AcbCalendarDto year = calendar.calendar(2026);
        assertThat(year.name()).isEqualTo("za-statutory");
        assertThat(year.knownUntil()).isEqualTo("2027-12-31");
        assertThat(year.endingSoon()).as("the frozen clock is fifteen months out").isFalse();
        assertThat(year.holidays()).hasSize(13).first()
                .isEqualTo(new AcbHolidayDto("2026-01-01", "New Year's Day", "STATUTORY"));
        assertThat(calendar.calendar(null).year()).as("this year, by the clock").isEqualTo(2026);
    }

    @Test
    @DisplayName("🔒 R1-24: adding a day writes it and ONE audit row -- who, which kind, what changed")
    void addingIsAttributed() {
        AcbHolidayDto added = calendar.add("2026-10-28", "A day declared by proclamation", "admin-a");
        assertThat(added.source()).isEqualTo("ADDED");
        assertThat(jdbc.queryForMap("SELECT name, source, added_by FROM acb_holidays WHERE holiday_date = DATE '2026-10-28'"))
                .containsEntry("source", "ADDED").containsEntry("added_by", "admin-a");
        assertThat(audits()).singleElement().satisfies(row -> {
            assertThat(row).containsEntry("kind", "HOLIDAY_ADDED").containsEntry("actor", "admin-a");
            assertThat(row.get("run_id")).isNull();
            assertThat((String) row.get("reason")).contains("2026-10-28").contains("proclamation");
        });
    }

    @Test
    @DisplayName("🔒 R1-24: removing an added day writes ONE audit row; extending the calendar writes one")
    void removingAndExtendingAreAttributed() {
        calendar.add("2026-10-28", "A day declared by proclamation", "admin-a");
        calendar.remove("2026-10-28", "admin-a");
        calendar.extend("2028-12-31", "admin-a");
        assertThat(audits()).extracting(row -> row.get("kind"))
                .containsExactly("HOLIDAY_ADDED", "HOLIDAY_REMOVED", "CALENDAR_EXTENDED");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM acb_holidays WHERE holiday_date = DATE '2026-10-28'", Long.class))
                .isZero();
        assertThat(jdbc.queryForObject("SELECT known_until FROM acb_calendar WHERE id = 1", java.sql.Date.class))
                .isEqualTo(java.sql.Date.valueOf("2028-12-31"));
    }

    @Test
    @DisplayName("🔒 R1-24: a change that rolls back takes its audit row with it")
    void aRollbackTakesTheAuditRow() {
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            calendar.add("2026-10-28", "A day declared by proclamation", "admin-a");
            status.setRollbackOnly();
        });
        assertThat(jdbc.queryForObject("SELECT count(*) FROM acb_holidays WHERE holiday_date = DATE '2026-10-28'", Long.class))
                .isZero();
        assertThat(audits()).isEmpty();
    }

    @Test
    @DisplayName("a listed day is refused HOLIDAY_ALREADY_LISTED -- a statutory day is never renamed by an add")
    void aListedDayIsRefused() {
        AcbRefusal refused = catchThrowableOfType(AcbRefusal.class,
                () -> calendar.add("2026-12-25", "Something else", "admin-a"));
        assertThat(refused.code()).isEqualTo(AcbRefusalCode.HOLIDAY_ALREADY_LISTED);
        assertThat(refused.details()).containsEntry("name", "Christmas Day");
        assertThat(jdbc.queryForObject("SELECT name FROM acb_holidays WHERE holiday_date = DATE '2026-12-25'", String.class))
                .isEqualTo("Christmas Day");
        assertThat(audits()).isEmpty();
    }

    @Test
    @DisplayName("a statutory day, or a day not listed, is refused HOLIDAY_NOT_REMOVABLE -- and nothing is removed")
    void onlyAnAddedDayIsRemovable() {
        assertThat(catchThrowableOfType(AcbRefusal.class, () -> calendar.remove("2026-12-25", "admin-a")).code())
                .isEqualTo(AcbRefusalCode.HOLIDAY_NOT_REMOVABLE);
        assertThat(catchThrowableOfType(AcbRefusal.class, () -> calendar.remove("2026-10-28", "admin-a")).code())
                .isEqualTo(AcbRefusalCode.HOLIDAY_NOT_REMOVABLE);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM acb_holidays", Long.class)).isEqualTo(27L);
        assertThat(audits()).isEmpty();
    }

    @Test
    @DisplayName("known-until only moves later: the same date or an earlier one is DATE_INVALID")
    void knownUntilOnlyMovesLater() {
        assertThat(catchThrowableOfType(AcbRefusal.class, () -> calendar.extend("2027-12-31", "admin-a")).code())
                .isEqualTo(AcbRefusalCode.DATE_INVALID);
        assertThat(catchThrowableOfType(AcbRefusal.class, () -> calendar.extend("2026-12-31", "admin-a")).code())
                .isEqualTo(AcbRefusalCode.DATE_INVALID);
        assertThat(catchThrowableOfType(AcbRefusal.class, () -> calendar.extend("31/12/2028", "admin-a")).code())
                .isEqualTo(AcbRefusalCode.DATE_INVALID);
        assertThat(audits()).isEmpty();
    }

    @Test
    @DisplayName("two administrators adding one day at once: one day, and one HOLIDAY_ALREADY_LISTED -- never a 500")
    void twoAddsRace() throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        List<Future<Object>> outcomes = new ArrayList<>();
        try {
            for (String who : List.of("admin-a", "admin-b")) {
                outcomes.add(pool.submit(() -> {
                    start.await();
                    try {
                        return calendar.add("2026-10-28", "Declared by " + who, who);
                    } catch (AcbRefusal refused) {
                        return refused.code();
                    }
                }));
            }
            start.countDown();
            List<Object> results = new ArrayList<>();
            for (Future<Object> outcome : outcomes) {
                results.add(outcome.get(30, TimeUnit.SECONDS));
            }
            assertThat(results).filteredOn(AcbHolidayDto.class::isInstance).hasSize(1);
            assertThat(results).filteredOn(AcbRefusalCode.HOLIDAY_ALREADY_LISTED::equals).hasSize(1);
        } finally {
            pool.shutdownNow();
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM acb_holidays WHERE holiday_date = DATE '2026-10-28'", Long.class))
                .isEqualTo(1L);
        assertThat(audits()).as("the loser's audit row rolled back with its insert").hasSize(1);
    }

    private List<Map<String, Object>> audits() {
        return Collections.unmodifiableList(jdbc.queryForList(
                "SELECT run_id, kind, actor, reason FROM acb_audit_events ORDER BY id"));
    }
}
