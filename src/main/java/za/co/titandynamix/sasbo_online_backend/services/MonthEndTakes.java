package za.co.titandynamix.sasbo_online_backend.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import za.co.titandynamix.sasbo_online_backend.config.SasboEnvironment;
import za.co.titandynamix.sasbo_online_backend.dto.monthend.MonthEndMonthDto;
import za.co.titandynamix.sasbo_online_backend.dto.monthend.MonthEndMonthsDto;
import za.co.titandynamix.sasbo_online_backend.exception.MonthEndRefusal;
import za.co.titandynamix.sasbo_online_backend.exception.MonthEndRefusalCode;
import za.co.titandynamix.sasbo_online_backend.repository.MonthEndFactsRepository;
import za.co.titandynamix.sasbo_online_backend.repository.MonthEndFactsRepository.TakenMonth;
import za.co.titandynamix.sasbo_online_backend.utils.FreeText;
import za.co.titandynamix.sasbo_online_backend.utils.MonthRange;
import za.co.titandynamix.sasbo_online_backend.utils.SeedMemberNumbers;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Taking a month (month end 04.2): the month's record, then the month frozen as facts -- one row a workplace with its
 * lineage as it stood, then its leavers by code, its suspended by code, its recruits and the statuses nobody knows --
 * IN ONE TRANSACTION AT {@code REPEATABLE READ} (MD64), the first in this codebase to name an isolation level: every
 * statement of the take reads the one snapshot the first one took, so a member captured while the month is being
 * taken is in every sheet of it or in none (MV-27).
 *
 * <ul>
 *   <li>TAKEN ONCE by the partial unique index, never by looking first: two takes released together both write, and
 *       the index refuses the second, which is answered {@code MONTH_ALREADY_TAKEN} naming who took it and when
 *       (MD46, ME-12, MV-28);</li>
 *   <li>taken only WHERE THE WORKPLACES ARE ON RECORD -- a month frozen with none would be kept that way by its own
 *       triggers -- else {@code MONTH_NOT_READY} (MD54);</li>
 *   <li>a month that has not begun is {@code MONTH_NOT_BEGUN}; the open month may be taken, which is what the figures
 *       of a month whose run was early would be -- held at the table (MM73);</li>
 *   <li>the instant is the injected clock's (BR40), and the take writes no sheet: every sheet is read from the facts.</li>
 * </ul>
 * The route that asks for a take is held by {@code MONTH_END_TAKE} (October build); the seed takes the demonstration's
 * three past months.
 */
@Service
public class MonthEndTakes {

    /** What a month a request names looks like: four digits, a hyphen, two. Anything else is not read. */
    private static final Pattern WRITTEN = Pattern.compile("\\d{4}-\\d{2}");

    /** Between the facts and the finer grains: where a test holds a take with a latch (MV-27). Nothing, otherwise. */
    @FunctionalInterface
    interface Pause {
        void between();
    }

    private final MonthEndFactsRepository facts;
    private final SasboEnvironment environment;
    private final SeedMemberNumbers seedNumbers;
    private final TransactionTemplate tx;
    private final Clock clock;
    private final Pause pause;

    @Autowired
    public MonthEndTakes(MonthEndFactsRepository facts, SasboEnvironment environment, SeedMemberNumbers seedNumbers,
                         PlatformTransactionManager transactions, Clock clock) {
        this(facts, environment, seedNumbers, transactions, clock, TransactionDefinition.ISOLATION_REPEATABLE_READ,
                () -> { });
    }

    /** For the snapshot's own test: an isolation level and a pause of its choosing. */
    MonthEndTakes(MonthEndFactsRepository facts, SasboEnvironment environment, SeedMemberNumbers seedNumbers,
                  PlatformTransactionManager transactions, Clock clock, int isolation, Pause pause) {
        this.facts = facts;
        this.environment = environment;
        this.seedNumbers = seedNumbers;
        TransactionTemplate template = new TransactionTemplate(transactions);
        template.setIsolationLevel(isolation);
        this.tx = template;
        this.clock = clock;
        this.pause = pause;
    }

    /** The isolation a take runs at: REPEATABLE READ where the application wires it (MD64) -- read by its own test. */
    int isolation() {
        return tx.getIsolationLevel();
    }

    /** The month the clock is in, in SASBO's zone: the open month. */
    public YearMonth openMonth() {
        return MonthRange.current(clock);
    }

    /**
     * The month a request names. One that cannot be read is {@code REQUEST_MALFORMED}, naming the field and giving
     * nothing of it back (ME-14); one that has not begun is {@code MONTH_NOT_BEGUN}.
     */
    public YearMonth month(String month) {
        YearMonth asked;
        try {
            if (month == null || !WRITTEN.matcher(month.trim()).matches()) {
                throw new DateTimeParseException("not a month", String.valueOf(month), 0);
            }
            asked = YearMonth.parse(month.trim());
        } catch (DateTimeParseException unreadable) {
            throw new MonthEndRefusal(MonthEndRefusalCode.REQUEST_MALFORMED,
                    "That month could not be read. Name it as year and month -- 2026-09.", Map.of("field", "month"));
        }
        requireBegun(asked);
        return asked;
    }

    /** The month's record, where it has been taken. */
    public Optional<TakenMonth> taken(YearMonth month) {
        return facts.taken(month.atDay(1));
    }

    /** Every month taken, the newest first. */
    public List<TakenMonth> takenMonths() {
        return facts.takenMonths();
    }

    /** The months taken, the newest first, beside the open month -- the Month end page's read. */
    public MonthEndMonthsDto months() {
        return new MonthEndMonthsDto(openMonth().toString(), takenMonths().stream().map(MonthEndTakes::dto).toList());
    }

    /**
     * One month's record, as a request names it: taken, or the open month; a past month not taken is
     * {@code MONTH_NOT_TAKEN} -- its statistics are read only from what was frozen (MD19).
     */
    public MonthEndMonthDto record(String month) {
        YearMonth asked = month(month);
        Optional<TakenMonth> taken = taken(asked);
        if (taken.isPresent()) {
            return dto(taken.get());
        }
        YearMonth open = openMonth();
        if (!asked.equals(open)) {
            throw new MonthEndRefusal(MonthEndRefusalCode.MONTH_NOT_TAKEN,
                    "That month was not taken, and a past month is read only from what was taken.",
                    MonthEndRefusal.details("month", asked.toString(), "open", open.toString()));
        }
        return new MonthEndMonthDto(asked.toString(), MonthEndMonthDto.OPEN, null, null, null, null, null, null, null,
                counted(asked).start().toString(), null);
    }

    private static MonthEndMonthDto dto(TakenMonth taken) {
        return new MonthEndMonthDto(YearMonth.from(taken.monthStart()).toString(), MonthEndMonthDto.TAKEN,
                taken.takenAt().toString(), taken.takenBy(), taken.id(),
                taken.firstTakenAt() == null ? null : taken.firstTakenAt().toString(), taken.firstTakenBy(),
                taken.takeNumber(), taken.reason(), taken.countsFrom() == null ? null : taken.countsFrom().toString(),
                taken.countsUntil() == null ? null : taken.countsUntil().toString());
    }

    // ------------------------------------------------------------------------------------------------ the cut-off

    /**
     * The window {@code month} counts (Stephne, MEN-14: "After month end is done - all capturing and changes move to
     * the next month"): every capture and status change from the cut-off of the month before -- its newest take, or
     * the 1st at 00:00 where it was never taken -- up to its own: its newest take's, where it is taken; else the end of
     * the calendar month, which for the open month is still to come. Its year counts from the cut-off of the December
     * before. The windows of the months follow one another, so nothing is counted twice and nothing falls between.
     */
    public MonthRange counted(YearMonth month) {
        return window(month, null);
    }

    /**
     * The window a take of {@code month} at {@code now} counts: from the cut-off of the month before, up to now -- but
     * never past the end of the calendar month, so a month taken late does not count the next month's changes; and
     * never past where the month after starts counting, where that month is taken already.
     */
    MonthRange forTake(YearMonth month, Instant now) {
        return window(month, now);
    }

    private MonthRange window(YearMonth month, Instant takingAt) {
        YearMonth before = month.minusMonths(1);
        YearMonth after = month.plusMonths(1);
        YearMonth december = YearMonth.of(month.getYear() - 1, 12);
        Map<LocalDate, TakenMonth> newest = facts.taken(List.of(before.atDay(1), month.atDay(1), after.atDay(1),
                december.atDay(1)));
        MonthRange calendar = MonthRange.of(month);
        Instant from = cutOff(newest.get(before.atDay(1)), calendar.start());
        Instant until;
        if (takingAt == null) {
            until = cutOff(newest.get(month.atDay(1)), calendar.next());
        } else {
            until = earliest(takingAt, calendar.next());
            TakenMonth next = newest.get(after.atDay(1));
            if (next != null) {
                until = earliest(until, next.countsFrom() != null ? next.countsFrom() : calendar.next());
            }
        }
        if (until.isBefore(from)) {
            until = from;
        }
        Instant yearFrom = month.getMonthValue() == 1 ? from
                : cutOff(newest.get(december.atDay(1)), MonthRange.of(YearMonth.of(month.getYear(), 1)).start());
        return MonthRange.counted(month, from, until, yearFrom);
    }

    private static Instant cutOff(TakenMonth taken, Instant otherwise) {
        return taken == null ? otherwise : taken.cutOff();
    }

    /**
     * Where counting is open from: the cut-off of the newest month taken -- nothing after it is in any take yet -- or,
     * where no month has been taken, the start of the clock's month. A leaving after it, undone by a rejoin, is counted
     * in no month (MEN-23).
     */
    public Instant openSince() {
        return takenMonths().stream().findFirst().map(TakenMonth::cutOff)
                .orElseGet(() -> MonthRange.of(openMonth()).start());
    }

    private static Instant earliest(Instant a, Instant b) {
        return a.isBefore(b) ? a : b;
    }

    // ------------------------------------------------------------------------------------------------ the reason

    /**
     * Why a month is taken (Stephne, MEN-11, on taking a month again: "This will help to understand why the file was
     * replaced!"): required of a take again, and kept for any take that gives one -- ordinary words, at most
     * {@value FreeText#NOTE_LIMIT} characters, line breaks allowed. Said before anything else is asked of the take.
     *
     * @return the reason, trimmed; {@code null} for a first take that gives none
     * @throws MonthEndRefusal {@code TAKE_REASON_REQUIRED}, {@code TAKE_REASON_INVALID} -- nothing of the words given back
     */
    public String reason(YearMonth month, Long replaces, String reason) {
        String why = FreeText.clean(reason);
        if (replaces != null && why == null) {
            throw new MonthEndRefusal(MonthEndRefusalCode.TAKE_REASON_REQUIRED,
                    "Say why the month is being taken again: it will stand beside the take it replaces. Nothing has "
                            + "been taken.", Map.of("month", month.toString()));
        }
        if (FreeText.problem(why, FreeText.NOTE_LIMIT, true) != null) {
            throw new MonthEndRefusal(MonthEndRefusalCode.TAKE_REASON_INVALID,
                    "A reason may be at most " + FreeText.NOTE_LIMIT + " characters of ordinary text. Nothing has been "
                            + "taken.", MonthEndRefusal.details("field", "reason",
                            "limit", String.valueOf(FreeText.NOTE_LIMIT)));
        }
        return why;
    }

    /** What the route says before it refuses: a month taken already is said so, naming who took it and when. */
    public void refuseIfTaken(YearMonth month) {
        Optional<TakenMonth> taken = taken(month);
        if (taken.isPresent()) {
            throw alreadyTaken(taken.get());
        }
    }

    /** A month is taken only where the workplaces are on record (MD54). */
    public void refuseIfNotReady() {
        if (facts.workplacesOnRecord() == 0) {
            throw new MonthEndRefusal(MonthEndRefusalCode.MONTH_NOT_READY,
                    "A month can be taken only where SASBO's workplaces are on record, and none is here. Nothing has "
                            + "been taken.", Map.of("missing", "workplaces"));
        }
    }

    /**
     * The take a take again replaces (04.11): the month's newest, named by its id -- a month never taken is
     * {@code MONTH_NOT_TAKEN}, and a take that is not the newest is {@code MONTH_TAKEN_SINCE}, naming the newest.
     */
    public TakenMonth replacing(YearMonth month, long replaces) {
        TakenMonth newest = taken(month).orElseThrow(() -> new MonthEndRefusal(MonthEndRefusalCode.MONTH_NOT_TAKEN,
                "That month has not been taken, so there is no take to take again.",
                MonthEndRefusal.details("month", month.toString(), "open", openMonth().toString())));
        if (newest.id() != replaces) {
            throw takenSince(newest);
        }
        return newest;
    }

    /**
     * Takes {@code month} for {@code actor}: its record and its facts, in one snapshot -- a month's first take, with no
     * reason given.
     *
     * @throws MonthEndRefusal {@code MONTH_NOT_BEGUN}, {@code MONTH_NOT_READY}, or {@code MONTH_ALREADY_TAKEN} where
     *                         the index refused it
     */
    public TakenMonth take(YearMonth month, String actor) {
        return take(month, actor, null, null);
    }

    /**
     * Takes {@code month} for {@code actor} -- again, where {@code replaces} names its newest take (04.11, MD70), with
     * the reason the month is taken again (MEN-11): a new take beside it, its record and its facts in one snapshot,
     * counting the month's window (MEN-14); every read of the month is of it from then on. There is no overload
     * without the reason: a caller that takes a month again says why, or does not compile.
     *
     * @throws MonthEndRefusal {@code TAKE_REASON_REQUIRED}, {@code TAKE_REASON_INVALID}, {@code MONTH_NOT_BEGUN},
     *                         {@code MONTH_NOT_READY}; for a first take {@code MONTH_ALREADY_TAKEN} where the index
     *                         refused it; for a take again {@code MONTH_NOT_TAKEN}, or {@code MONTH_TAKEN_SINCE} where it
     *                         is not the newest or the index refused it
     */
    public TakenMonth take(YearMonth month, String actor, Long replaces, String reason) {
        return taking(month, actor, replaces, reason, clock.instant());
    }

    /**
     * The seed's first take of a past month, dated as SASBO takes a month -- {@code at} is the instant the record
     * carries, 07:10 on the month's last working day ({@code MonthEndTakeSeeder}), and where the window it counts ends
     * (MEN-14), as a take made then would have counted: the facts are the month as it stood at that cut-off (rule S),
     * whenever they are worked out. A first take, so no reason is asked of it. Never later than the clock's instant: a
     * take is not dated in the future.
     */
    public TakenMonth takeAsAt(YearMonth month, String actor, Instant at) {
        if (at.isAfter(clock.instant())) {
            throw new IllegalArgumentException("a take is never dated after the clock's instant");
        }
        return taking(month, actor, null, null, at);
    }

    /** Every take: {@code now} is the instant it carries and counts up to -- the clock's, but for the seed's dated one. */
    private TakenMonth taking(YearMonth month, String actor, Long replaces, String reason, Instant now) {
        String why = reason(month, replaces, reason);
        requireBegun(month);
        refuseIfNotReady();
        TakenMonth replaced = replaces == null ? null : replacing(month, replaces);
        Collection<String> seeded = environment.isProduction() ? List.of() : seedNumbers.all();
        LocalDate firstDay = month.atDay(1);
        try {
            return tx.execute(status -> {
                // The window is read inside the snapshot, with the takes it counts from.
                MonthRange window = forTake(month, now);
                long id = facts.insertMonth(firstDay, actor, now, replaced, why, window);
                facts.insertFacts(id, window, seeded);
                pause.between();
                facts.insertLeftByCode(id, window);
                facts.insertSuspendedByCode(id, window);
                facts.insertRecruited(id, window);
                facts.insertUnknownStatuses(id, window);
                return facts.taken(firstDay).orElseThrow(() -> new IllegalStateException(
                        "the month's record was written and cannot be read back in its own transaction"));
            });
        } catch (DataIntegrityViolationException lostTheRace) {
            // Another take of the same month committed first. The index is the arbiter, and its answer is the same
            // refusal the route gives -- read now, in a snapshot of its own, where the winner is visible.
            TakenMonth taken = facts.taken(firstDay).orElseThrow(() -> lostTheRace);
            throw replaced == null ? alreadyTaken(taken) : takenSince(taken);
        }
    }

    private void requireBegun(YearMonth month) {
        YearMonth open = openMonth();
        if (month.isAfter(open)) {
            throw new MonthEndRefusal(MonthEndRefusalCode.MONTH_NOT_BEGUN, "That month has not begun yet.",
                    MonthEndRefusal.details("month", month.toString(), "current", open.toString()));
        }
    }

    private static MonthEndRefusal alreadyTaken(TakenMonth taken) {
        return new MonthEndRefusal(MonthEndRefusalCode.MONTH_ALREADY_TAKEN,
                "That month has been taken already. Its figures are read from what was frozen then; to correct them, "
                        + "take it again.", MonthEndRefusal.details("month", YearMonth.from(taken.monthStart()).toString(),
                "takenBy", taken.takenBy(), "takenAt", taken.takenAt().toString(), "takeId", String.valueOf(taken.id())));
    }

    private static MonthEndRefusal takenSince(TakenMonth newest) {
        return new MonthEndRefusal(MonthEndRefusalCode.MONTH_TAKEN_SINCE,
                "That month has been taken again since. Nothing was taken: look at the newest take, and take it again "
                        + "from there if it still needs correcting.",
                MonthEndRefusal.details("month", YearMonth.from(newest.monthStart()).toString(),
                        "takenBy", newest.takenBy(), "takenAt", newest.takenAt().toString(),
                        "takeId", String.valueOf(newest.id())));
    }
}
