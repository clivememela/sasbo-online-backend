package za.co.titandynamix.sasbo_online_backend.acb;

/**
 * Where the ACB module's holiday calendar comes from (D30).
 *
 * <p>🔒 <b>Read fresh, in one query, every time it is asked (SD3-L).</b> Each preparation, approval
 * and render loads the calendar as the table holds it NOW into an immutable {@link HolidayCalendar}
 * and keeps it no longer than that one piece of work: nothing to invalidate when a holiday is added,
 * nothing two instances can disagree about, and never a query per member.
 */
@FunctionalInterface
public interface HolidayCalendarSource {

    /** The name of the calendar a database with no calendar row reads as -- it covers no month. */
    String NONE = "none";

    /** The calendar as it stands, in one query. */
    HolidayCalendar load();
}
