package za.co.titandynamix.sasbo_online_backend.services.impl;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import za.co.titandynamix.sasbo_online_backend.dto.acb.AcbRunDto;
import za.co.titandynamix.sasbo_online_backend.enums.AcbRunStatus;
import za.co.titandynamix.sasbo_online_backend.exception.AcbRefusal;
import za.co.titandynamix.sasbo_online_backend.exception.AcbRefusalCode;
import za.co.titandynamix.sasbo_online_backend.fixtures.AcbRunFixture;
import za.co.titandynamix.sasbo_online_backend.services.AcbCalendarService;
import za.co.titandynamix.sasbo_online_backend.services.AcbFileService;
import za.co.titandynamix.sasbo_online_backend.services.AcbRunService;
import za.co.titandynamix.sasbo_online_backend.support.AbstractAcbIT;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

/**
 * 🔒 R1-20, SD3-F: a calendar that changes under a prepared run is caught. A holiday added inside
 * the run's month after its dates were computed could put a debit on it -- and the bank declines the
 * whole file for one such date -- so the run neither approves nor becomes a file until it is
 * prepared again. A holiday in another month changes nothing.
 */
@DisplayName("A holiday declared after a run was prepared")
class AcbCalendarChangeIT extends AbstractAcbIT {

    private static final String OCTOBER = "2026-10";

    @Autowired
    private AcbRunService service;

    @Autowired
    private AcbFileService files;

    @Autowired
    private AcbCalendarService calendar;

    @Test
    @DisplayName("inside the month: calendarAgrees turns false and approval is refused RUN_CALENDAR_CHANGED")
    void insideTheMonth() {
        AcbRunFixture.insert(jdbc, 15, 2).assertSize();
        AcbRunDto prepared = service.get(service.prepare(OCTOBER, "system").id());
        assertThat(prepared.calendarAgrees()).isTrue();

        calendar.add("2026-10-28", "A day declared by proclamation", "someone");

        AcbRunDto now = service.get(prepared.id());
        assertThat(now.calendarAgrees()).isFalse();
        assertThat(service.list(0, 50).content()).singleElement()
                .satisfies(summary -> assertThat(summary.calendarAgrees()).as("the list says it too").isFalse());
        AcbRefusal refused = catchThrowableOfType(AcbRefusal.class,
                () -> service.approve(now.id(), now.version(), "approver", null));
        assertThat(refused.code()).isEqualTo(AcbRefusalCode.RUN_CALENDAR_CHANGED);
        assertThat(service.get(now.id()).status()).isEqualTo(AcbRunStatus.PREPARED);
    }

    @Test
    @DisplayName("the must-pass twin: a holiday in another month leaves the run agreeing, and it approves")
    void inAnotherMonth() {
        AcbRunFixture.insert(jdbc, 15, 2).assertSize();
        AcbRunDto prepared = service.get(service.prepare(OCTOBER, "system").id());

        calendar.add("2026-11-04", "A day declared by proclamation", "someone");

        AcbRunDto now = service.get(prepared.id());
        assertThat(now.calendarAgrees()).isTrue();
        assertThat(service.approve(now.id(), now.version(), "approver", null).status()).isEqualTo(AcbRunStatus.APPROVED);
    }

    @Test
    @DisplayName("an approved run whose month gains a holiday is not made into a file")
    void theFileIsRefusedToo() {
        AcbRunFixture.insert(jdbc, 15, 2).assertSize();
        AcbRunDto prepared = service.get(service.prepare(OCTOBER, "system").id());
        AcbRunDto approved = service.approve(prepared.id(), prepared.version(), "approver", null);

        calendar.add("2026-10-28", "A day declared by proclamation", "someone");

        AcbRefusal refused = catchThrowableOfType(AcbRefusal.class, () -> files.render(approved.id(), "approver"));
        assertThat(refused.code()).isEqualTo(AcbRefusalCode.RUN_CALENDAR_CHANGED);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM acb_run_files", Long.class)).isZero();
    }

    @Test
    @DisplayName("the month prepared again agrees -- and moves the 28th's debits off the new holiday")
    void preparedAgainItAgrees() {
        AcbRunFixture.insert(jdbc, 15, 2).assertSize();
        AcbRunDto first = service.get(service.prepare(OCTOBER, "system").id());
        calendar.add("2026-10-28", "A day declared by proclamation", "someone");
        service.cancel(first.id(), first.version(), "system", "the calendar changed");

        AcbRunDto again = service.get(service.prepare(OCTOBER, "system").id());
        assertThat(again.status()).isEqualTo(AcbRunStatus.PREPARED);
        assertThat(again.calendarAgrees()).isTrue();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM acb_run_lines WHERE run_id = ? "
                + "AND action_date = DATE '2026-10-28'", Long.class, again.id())).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM acb_run_lines WHERE run_id = ? AND debit_day = 28",
                Long.class, again.id())).as("non-vacuity: members on the 28th were debited").isPositive();
    }
}
