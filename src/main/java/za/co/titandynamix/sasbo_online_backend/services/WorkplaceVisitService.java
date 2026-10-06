package za.co.titandynamix.sasbo_online_backend.services;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.titandynamix.sasbo_online_backend.config.WorkplaceSwitch;
import za.co.titandynamix.sasbo_online_backend.dto.workplace.WorkplaceVisitPageDto;
import za.co.titandynamix.sasbo_online_backend.dto.workplace.WorkplaceVisitRequest;
import za.co.titandynamix.sasbo_online_backend.exception.MonthEndRefusal;
import za.co.titandynamix.sasbo_online_backend.exception.MonthEndRefusalCode;
import za.co.titandynamix.sasbo_online_backend.repository.WorkplaceVisitRepository;
import za.co.titandynamix.sasbo_online_backend.utils.MonthRange;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;

/**
 * Branch visits (month end A.1, MO22): a visit captured is a visit on record, and a workplace shows its visits newest
 * first, a page at a time. Quick wins' QW10 -- the Location Visit form keeps its data, register A60 -- is closed by it
 * and not built twice (BR53).
 *
 * <h2>A visit that finds another complement writes it</h2>
 * The complement is kept by hand from organisers' visits (SASBO's reply, point 42). A visit that found one other than
 * the workplace's writes it onto the workplace, in the same transaction, and keeps what it was on the visit
 * ({@code complementBefore}); the workplaces' seed leaves a complement a visit wrote as it is ({@code WorkplaceSeeder}).
 *
 * <h2>Open, as every write is today</h2>
 * The write is as open as the member's and is named in B1's list in the commit that creates it (MR-SEC-1). What the
 * read answers, and what it keeps back, is {@link WorkplaceVisitPageDto}'s.
 */
@Service
public class WorkplaceVisitService {

    /** The largest page there is, as the workplaces' and the history's (MR-PERF-12). */
    public static final int MAX_PAGE_SIZE = 200;

    static final int DEFAULT_PAGE_SIZE = 20;

    /** The longest name a visitor's line takes -- the column's. */
    static final int ORGANISER_MAX = 120;

    /**
     * The largest count a visit takes -- of staff, members, non-members or recruits (October build): no workplace of
     * SASBO's has a staff of a hundred thousand, so a larger number is a typing slip, and V82 holds a draft to it too.
     */
    public static final int MAX_COUNT = 99_999;

    /** What every visit keeps and the read does not answer (MD61, B1). */
    static final List<String> WITHHELD = List.of("organiser", "membershipFound", "nonMembers", "recruited",
            "nonMembersInterviewed");

    private final WorkplaceVisitRepository visits;
    private final WorkplaceSwitch workplaceSwitch;
    private final Clock clock;

    public WorkplaceVisitService(WorkplaceVisitRepository visits, WorkplaceSwitch workplaceSwitch, Clock clock) {
        this.visits = visits;
        this.workplaceSwitch = workplaceSwitch;
        this.clock = clock;
    }

    /**
     * A workplace's visits, a page at a time, newest first. A workplace not on record here -- or none, where the
     * deployment's switch serves none (MD62) -- is {@code WORKPLACE_UNKNOWN}; a page that does not exist is
     * {@code WORKPLACE_PAGE_INVALID}, never an empty page that looks like the end of the visits.
     */
    public WorkplaceVisitPageDto page(Long workplaceId, Integer page, Integer size) {
        int number = page == null ? 0 : page;
        int rows = size == null ? DEFAULT_PAGE_SIZE : size;
        if (number < 0 || rows < 1 || rows > MAX_PAGE_SIZE) {
            throw pageInvalid(number, rows);
        }
        long id = onRecord(workplaceId);
        long total = visits.count(id);
        List<WorkplaceVisitPageDto.Visit> found = visits.page(id, number, rows);
        if (found.isEmpty() && number > 0) {
            throw pageInvalid(number, rows);
        }
        return new WorkplaceVisitPageDto(id, found, number, rows, total, (int) ((total + rows - 1) / rows), WITHHELD);
    }

    /**
     * Records a visit to a workplace, and -- where it found another complement -- writes the workplace's, keeping what
     * it was. Refused whole, nothing written: a workplace not on record ({@code WORKPLACE_UNKNOWN}); a visit with no
     * day, a day still to come in SASBO's zone, a count below nought, a time out before the time in, or a visitor's
     * name past its column ({@code VISIT_INVALID}, the field named in the details).
     */
    @Transactional
    public WorkplaceVisitPageDto.Visit record(Long workplaceId, WorkplaceVisitRequest visit) {
        long id = onRecord(workplaceId);
        Checked checked = check(visit, true);
        Integer onRecord = visits.complementOf(id).orElseThrow(() -> unknown(workplaceId))[0];
        Integer before = null;
        if (visit.complementFound() != null && !visit.complementFound().equals(onRecord)) {
            before = onRecord;
            visits.setComplement(id, visit.complementFound());
        }
        long visitId = visits.insert(id, visit, checked.day(), checked.in(), checked.out(), before, clock.instant());
        return new WorkplaceVisitPageDto.Visit(visitId, checked.day().toString(), true, visit.complementFound(), before,
                false);
    }

    /** A visit's day and times, read from the text the form sent; the day is {@code null} where none was sent. */
    public record Checked(LocalDate day, LocalTime in, LocalTime out) {
    }

    /**
     * Reads a visit's fields and holds them to a visit's rules -- the ONE place both a visit recorded and a visit draft
     * saved are checked (October build), so a draft that saves is a draft that submits, but for its day. Refused
     * {@code VISIT_INVALID}, the field named: a day that is not a date or is still to come in SASBO's zone, a time
     * that is not one, a time out before the time in, a count below nought or above {@value #MAX_COUNT}, a visitor's
     * name past its column.
     *
     * @param dayRequired a visit recorded has its day; a draft may be saved before the organiser has chosen one
     */
    public Checked check(WorkplaceVisitRequest visit, boolean dayRequired) {
        boolean noDay = visit == null || visit.visitedOn() == null || visit.visitedOn().isBlank();
        if (noDay && (dayRequired || visit == null)) {
            throw invalid("visitedOn", "The day of the visit is needed.");
        }
        LocalDate day = noDay ? null
                : parse("visitedOn", visit.visitedOn(), LocalDate::parse, "The day of the visit is not a date.");
        LocalTime in = visit.timeIn() == null || visit.timeIn().isBlank() ? null
                : parse("timeIn", visit.timeIn(), LocalTime::parse, "The time in is not a time of day.");
        LocalTime out = visit.timeOut() == null || visit.timeOut().isBlank() ? null
                : parse("timeOut", visit.timeOut(), LocalTime::parse, "The time out is not a time of day.");
        validate(visit, day, in, out);
        return new Checked(day, in, out);
    }

    private static <T> T parse(String field, String text, java.util.function.Function<String, T> parser,
                               String message) {
        try {
            return parser.apply(text.trim());
        } catch (DateTimeParseException notOne) {
            throw invalid(field, message);
        }
    }

    private void validate(WorkplaceVisitRequest visit, LocalDate day, LocalTime in, LocalTime out) {
        if (day != null && day.isAfter(LocalDate.now(clock.withZone(MonthRange.ZONE)))) {
            throw invalid("visitedOn", "A visit cannot be on a day still to come.");
        }
        Map<String, Integer> counts = new java.util.LinkedHashMap<>();
        counts.put("complementFound", visit.complementFound());
        counts.put("membershipFound", visit.membershipFound());
        counts.put("nonMembers", visit.nonMembers());
        counts.put("recruited", visit.recruited());
        counts.put("nonMembersInterviewed", visit.nonMembersInterviewed());
        for (Map.Entry<String, Integer> count : counts.entrySet()) {
            if (count.getValue() != null && count.getValue() < 0) {
                throw invalid(count.getKey(), "A count cannot be below nought.");
            }
            if (count.getValue() != null && count.getValue() > MAX_COUNT) {
                throw invalid(count.getKey(), "A count cannot be more than 99,999.");
            }
        }
        if (in != null && out != null && out.isBefore(in)) {
            throw invalid("timeOut", "The time out is before the time in.");
        }
        if (visit.organiser() != null && visit.organiser().length() > ORGANISER_MAX) {
            throw invalid("organiser", "The name is longer than " + ORGANISER_MAX + " characters.");
        }
    }

    /** The workplace's id, where it is on record here and the switch serves workplaces. */
    private long onRecord(Long workplaceId) {
        if (workplaceId == null || !workplaceSwitch.workplacesOnRecord() || visits.complementOf(workplaceId).isEmpty()) {
            throw unknown(workplaceId);
        }
        return workplaceId;
    }

    private static MonthEndRefusal unknown(Long workplaceId) {
        return new MonthEndRefusal(MonthEndRefusalCode.WORKPLACE_UNKNOWN,
                "That workplace is not on record here. Choose another.",
                Map.of("workplaceId", String.valueOf(workplaceId)));
    }

    private static MonthEndRefusal invalid(String field, String message) {
        return new MonthEndRefusal(MonthEndRefusalCode.VISIT_INVALID, message + " Nothing was saved.",
                Map.of("field", field));
    }

    private static MonthEndRefusal pageInvalid(int page, int size) {
        return new MonthEndRefusal(MonthEndRefusalCode.WORKPLACE_PAGE_INVALID,
                "That page of the workplace's visits does not exist.",
                MonthEndRefusal.details("page", String.valueOf(page), "size", String.valueOf(size),
                        "maxSize", String.valueOf(MAX_PAGE_SIZE)));
    }
}
