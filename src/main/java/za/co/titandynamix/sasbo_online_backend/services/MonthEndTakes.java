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
import za.co.titandynamix.sasbo_online_backend.utils.MonthRange;
import za.co.titandynamix.sasbo_online_backend.utils.SeedMemberNumbers;

import java.time.Clock;
import java.time.Instant;
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
 * The route that asks for a take refuses every caller until sign-in exists ({@code IDENTITY_REQUIRED}, as ACB's writes
 * do); until then the seed is the one caller, taking the demonstration's three past months.
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
        return new MonthEndMonthDto(asked.toString(), MonthEndMonthDto.OPEN, null, null);
    }

    private static MonthEndMonthDto dto(TakenMonth taken) {
        return new MonthEndMonthDto(YearMonth.from(taken.monthStart()).toString(), MonthEndMonthDto.TAKEN,
                taken.takenAt().toString(), taken.takenBy());
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
     * Takes {@code month} for {@code actor}: its record and its facts, in one snapshot.
     *
     * @throws MonthEndRefusal {@code MONTH_NOT_BEGUN}, {@code MONTH_NOT_READY}, or {@code MONTH_ALREADY_TAKEN} where
     *                         the index refused it
     */
    public TakenMonth take(YearMonth month, String actor) {
        requireBegun(month);
        refuseIfNotReady();
        MonthRange range = MonthRange.of(month);
        Instant now = clock.instant();
        Collection<String> seeded = environment.isProduction() ? List.of() : seedNumbers.all();
        try {
            return tx.execute(status -> {
                long id = facts.insertMonth(range.firstDay(), actor, now);
                facts.insertFacts(id, range, seeded);
                pause.between();
                facts.insertLeftByCode(id, range);
                facts.insertSuspendedByCode(id, range);
                facts.insertRecruited(id, range);
                facts.insertUnknownStatuses(id, range);
                return facts.taken(range.firstDay()).orElseThrow(() -> new IllegalStateException(
                        "the month's record was written and cannot be read back in its own transaction"));
            });
        } catch (DataIntegrityViolationException lostTheRace) {
            // Another take of the same month committed first. The index is the arbiter, and its answer is the same
            // refusal the route gives -- read now, in a snapshot of its own, where the winner is visible.
            TakenMonth taken = facts.taken(range.firstDay()).orElseThrow(() -> lostTheRace);
            throw alreadyTaken(taken);
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
                "That month has been taken already, and a month is taken once. Its figures are read from what was "
                        + "frozen then.", MonthEndRefusal.details("month", YearMonth.from(taken.monthStart()).toString(),
                "takenBy", taken.takenBy(), "takenAt", taken.takenAt().toString()));
    }
}
