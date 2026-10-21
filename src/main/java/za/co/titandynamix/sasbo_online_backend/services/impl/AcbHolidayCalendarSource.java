package za.co.titandynamix.sasbo_online_backend.services.impl;

import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Component;
import za.co.titandynamix.sasbo_online_backend.acb.HolidayCalendar;
import za.co.titandynamix.sasbo_online_backend.acb.HolidayCalendarSource;
import za.co.titandynamix.sasbo_online_backend.entity.acb.AcbCalendar;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * The holiday calendar as V47's two tables hold it: the calendar's row and every holiday, in ONE
 * statement (SD3-L), made into an immutable {@link HolidayCalendar} for the caller's one piece of work.
 *
 * <p>Issued through the entity manager, not a {@code JdbcTemplate}, so the statement is one Hibernate
 * counts: the preparation's {@code QueryCount} test sees the calendar's read and holds it to one,
 * whatever the membership (R1-10).
 *
 * <p>A database with no calendar row -- the fast lane's H2 before a test seeds one -- reads as a
 * calendar called {@value HolidayCalendarSource#NONE} that covers no month, so a preparation is
 * refused {@code HOLIDAYS_NOT_KNOWN} rather than computed on weekends alone.
 */
@Component
public class AcbHolidayCalendarSource implements HolidayCalendarSource {

    /**
     * The calendar's row and its non-working days. A day the bank is closed counts while the calendar's setting says
     * so -- DOD-17's open choice, on by default (V70); a public holiday always does. NULLs read as V70's defaults, for
     * the fast lane's tables built from the entities.
     */
    private static final String LOAD = "SELECT c.name, c.known_until, h.holiday_date "
            + "FROM acb_calendar c LEFT JOIN acb_holidays h ON (h.kind IS NULL OR h.kind <> 'BANK_CLOSED' "
            + "OR c.bank_closures_count IS NULL OR c.bank_closures_count = TRUE) "
            + "WHERE c.id = ?1 ORDER BY h.holiday_date";

    private final EntityManager entityManager;

    public AcbHolidayCalendarSource(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    public HolidayCalendar load() {
        @SuppressWarnings("unchecked")
        List<Object[]> rows = entityManager.createNativeQuery(LOAD)
                .setParameter(1, AcbCalendar.THE_ROW)
                .getResultList();
        if (rows.isEmpty()) {
            return HolidayCalendar.of(NONE, null, Set.of());
        }
        List<LocalDate> dates = new ArrayList<>();
        for (Object[] row : rows) {
            LocalDate holiday = asDate(row[2]);
            if (holiday != null) {
                dates.add(holiday);
            }
        }
        return HolidayCalendar.of((String) rows.get(0)[0], asDate(rows.get(0)[1]), dates);
    }

    /** A DATE column as the driver hands it back through a native query: {@code java.sql.Date} or already a {@code LocalDate}. */
    private static LocalDate asDate(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof LocalDate date) {
            return date;
        }
        if (value instanceof java.sql.Date date) {
            return date.toLocalDate();
        }
        throw new IllegalStateException("A calendar date came back as " + value.getClass().getName());
    }
}
