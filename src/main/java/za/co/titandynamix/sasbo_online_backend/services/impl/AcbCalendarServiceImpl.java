package za.co.titandynamix.sasbo_online_backend.services.impl;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import za.co.titandynamix.sasbo_online_backend.acb.AcbTime;
import za.co.titandynamix.sasbo_online_backend.acb.HolidayCalendarSource;
import za.co.titandynamix.sasbo_online_backend.dto.acb.AcbCalendarDto;
import za.co.titandynamix.sasbo_online_backend.dto.acb.AcbHolidayDto;
import za.co.titandynamix.sasbo_online_backend.entity.acb.AcbCalendar;
import za.co.titandynamix.sasbo_online_backend.entity.acb.AcbHoliday;
import za.co.titandynamix.sasbo_online_backend.enums.AcbHolidaySource;
import za.co.titandynamix.sasbo_online_backend.enums.AcbRunlessEvent;
import za.co.titandynamix.sasbo_online_backend.exception.AcbRefusal;
import za.co.titandynamix.sasbo_online_backend.exception.AcbRefusalCode;
import za.co.titandynamix.sasbo_online_backend.repository.AcbCalendarRepository;
import za.co.titandynamix.sasbo_online_backend.repository.AcbHolidayRepository;
import za.co.titandynamix.sasbo_online_backend.services.AcbCalendarService;

import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * The holiday calendar's reads and writes (D30).
 *
 * <p>The writes go through JDBC, not {@code save}: a holiday's key is its date, assigned, so a JPA
 * save of a listed day would MERGE -- quietly renaming a statutory day -- where an {@code INSERT}
 * meets the primary key. Two administrators adding one day at once therefore get one day and one
 * {@code HOLIDAY_ALREADY_LISTED}, never a 500 out of the unique index (F64's pattern). Each write
 * records its run-less audit row in the same transaction (SD4-A, R1-24).
 */
@Service
public class AcbCalendarServiceImpl implements AcbCalendarService {

    /** From this many days before known-until, the screen says when the calendar ends (SD3-G). */
    static final int NOTICE_DAYS = 60;

    /** The years a calendar can be asked for -- wide, and still a year {@link LocalDate} can hold. */
    private static final int FIRST_YEAR = 2000;
    private static final int LAST_YEAR = 2100;

    private final AcbHolidayRepository holidays;
    private final AcbCalendarRepository calendarRow;
    private final AcbAuditTrail audit;
    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final TransactionTemplate tx;

    public AcbCalendarServiceImpl(AcbHolidayRepository holidays, AcbCalendarRepository calendarRow,
                                  AcbAuditTrail audit, JdbcTemplate jdbc, Clock clock,
                                  PlatformTransactionManager transactions) {
        this.holidays = holidays;
        this.calendarRow = calendarRow;
        this.audit = audit;
        this.jdbc = jdbc;
        this.clock = clock;
        this.tx = new TransactionTemplate(transactions);
    }

    @Override
    public AcbCalendarDto calendar(Integer year) {
        int asked = year == null ? AcbTime.today(clock).getYear() : requireYear(year);
        return dto(calendarRow.findById(AcbCalendar.THE_ROW).orElse(null), asked);
    }

    @Override
    public AcbHolidayDto add(String date, String name, String actor) {
        LocalDate day = parseDate(date);
        String named = name == null ? "" : name.trim();
        if (named.isEmpty() || named.length() > 100) {
            throw new AcbRefusal(AcbRefusalCode.REQUEST_MALFORMED,
                    "A holiday needs a name of at most 100 characters.", Map.of("date", day.toString()));
        }
        try {
            return tx.execute(status -> {
                holidays.findById(day).ifPresent(listed -> {
                    throw alreadyListed(listed.getHolidayDate(), listed.getName());
                });
                jdbc.update("INSERT INTO acb_holidays (holiday_date, name, source, added_by, added_at) "
                                + "VALUES (?, ?, ?, ?, ?)",
                        day, named, AcbHolidaySource.ADDED.name(), actor,
                        OffsetDateTime.ofInstant(AcbTime.instant(clock), ZoneOffset.UTC));
                audit.recordRunless(AcbRunlessEvent.HOLIDAY_ADDED, "added " + day + " (" + named + ")", actor);
                return new AcbHolidayDto(day.toString(), named, AcbHolidaySource.ADDED.name());
            });
        } catch (DataIntegrityViolationException lostTheRace) {
            // Another administrator added the same day between the check and the insert. The
            // primary key is the arbiter, and its answer is the same refusal.
            throw alreadyListed(day, holidays.findById(day).map(AcbHoliday::getName).orElse(named));
        }
    }

    @Override
    public AcbCalendarDto remove(String date, String actor) {
        LocalDate day = parseDate(date);
        tx.executeWithoutResult(status -> {
            AcbHoliday listed = holidays.findById(day).orElseThrow(() -> new AcbRefusal(
                    AcbRefusalCode.HOLIDAY_NOT_REMOVABLE,
                    day + " is not in the holiday calendar, so there is nothing to remove.",
                    Map.of("date", day.toString())));
            if (listed.getSource() != AcbHolidaySource.ADDED) {
                throw new AcbRefusal(AcbRefusalCode.HOLIDAY_NOT_REMOVABLE,
                        day + " (" + listed.getName() + ") is a statutory holiday. Only a day an "
                                + "administrator added can be removed.",
                        Map.of("date", day.toString(), "source", listed.getSource().name()));
            }
            jdbc.update("DELETE FROM acb_holidays WHERE holiday_date = ? AND source = ?",
                    day, AcbHolidaySource.ADDED.name());
            audit.recordRunless(AcbRunlessEvent.HOLIDAY_REMOVED,
                    "removed " + day + " (" + listed.getName() + ")", actor);
        });
        return calendar(day.getYear());
    }

    @Override
    public AcbCalendarDto extend(String knownUntil, String actor) {
        LocalDate until = parseDate(knownUntil);
        tx.executeWithoutResult(status -> {
            LocalDate before = jdbc.queryForList("SELECT known_until FROM acb_calendar WHERE id = ? FOR UPDATE",
                    LocalDate.class, AcbCalendar.THE_ROW).stream().findFirst().orElseThrow(() -> new AcbRefusal(
                    AcbRefusalCode.HOLIDAYS_NOT_KNOWN, "No holiday calendar is on record to extend.", Map.of()));
            if (!until.isAfter(before)) {
                Map<String, String> facts = new LinkedHashMap<>();
                facts.put("knownUntil", before.toString());
                facts.put("date", until.toString());
                throw new AcbRefusal(AcbRefusalCode.DATE_INVALID, "The calendar is already known until "
                        + before + ". A new known-until date must be later.", facts);
            }
            jdbc.update("UPDATE acb_calendar SET known_until = ? WHERE id = ?", until, AcbCalendar.THE_ROW);
            audit.recordRunless(AcbRunlessEvent.CALENDAR_EXTENDED, "known until " + before + ", now " + until, actor);
        });
        return calendar(until.getYear());
    }

    /**
     * 🧪 Whether the screen should say when the calendar ends: from {@value #NOTICE_DAYS} days before
     * its known-until -- and always once it has passed, or when there is none. {@code today} is an
     * argument, never read here (T2-G).
     */
    static boolean endingSoon(LocalDate knownUntil, LocalDate today) {
        return knownUntil == null || !today.isBefore(knownUntil.minusDays(NOTICE_DAYS));
    }

    // ------------------------------------------------------------------------------------------

    private AcbCalendarDto dto(AcbCalendar row, int year) {
        List<AcbHolidayDto> listed = holidays.findByHolidayDateBetweenOrderByHolidayDate(
                        LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31)).stream()
                .map(h -> new AcbHolidayDto(h.getHolidayDate().toString(), h.getName(), h.getSource().name()))
                .toList();
        LocalDate knownUntil = row == null ? null : row.getKnownUntil();
        return new AcbCalendarDto(row == null ? HolidayCalendarSource.NONE : row.getName(),
                AcbTime.iso(knownUntil), endingSoon(knownUntil, AcbTime.today(clock)), year, listed);
    }

    private static AcbRefusal alreadyListed(LocalDate day, String name) {
        Map<String, String> facts = new LinkedHashMap<>();
        facts.put("date", day.toString());
        facts.put("name", Objects.toString(name, ""));
        return new AcbRefusal(AcbRefusalCode.HOLIDAY_ALREADY_LISTED,
                day + " is already in the holiday calendar, as " + name + ".", facts);
    }

    private static int requireYear(int year) {
        if (year < FIRST_YEAR || year > LAST_YEAR) {
            throw new AcbRefusal(AcbRefusalCode.DATE_INVALID,
                    "A calendar year is between " + FIRST_YEAR + " and " + LAST_YEAR + ".",
                    Map.of("year", String.valueOf(year)));
        }
        return year;
    }

    /** A calendar date: ISO-8601, in a year the calendar can be asked for -- checked before anything is written. */
    private static LocalDate parseDate(String text) {
        try {
            LocalDate date = LocalDate.parse(text == null ? "" : text.trim());
            requireYear(date.getYear());
            return date;
        } catch (DateTimeParseException malformed) {
            throw new AcbRefusal(AcbRefusalCode.DATE_INVALID, "Dates are written YYYY-MM-DD, for example 2026-12-16.",
                    Map.of("date", String.valueOf(text)));
        }
    }
}
