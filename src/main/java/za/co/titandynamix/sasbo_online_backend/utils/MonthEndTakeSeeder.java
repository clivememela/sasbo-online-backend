package za.co.titandynamix.sasbo_online_backend.utils;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import za.co.titandynamix.sasbo_online_backend.config.SasboEnvironment;
import za.co.titandynamix.sasbo_online_backend.config.WorkplaceSwitch;
import za.co.titandynamix.sasbo_online_backend.exception.MonthEndRefusal;
import za.co.titandynamix.sasbo_online_backend.exception.MonthEndRefusalCode;
import za.co.titandynamix.sasbo_online_backend.repository.MonthEndFactsRepository.TakenMonth;
import za.co.titandynamix.sasbo_online_backend.services.MonthEndTakes;

import za.co.titandynamix.sasbo_online_backend.acb.HolidayCalendar;
import za.co.titandynamix.sasbo_online_backend.acb.HolidayCalendarSource;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The demonstration's three past months, TAKEN (month end 04.2): the months before the clock's, each through the real
 * take, by {@value DemonstrationMemberSeeder#SEED_ACTOR} -- after {@code 03.7} has given them their leavers and their
 * suspended, which is why it runs after the invented members' past in {@code DataSeeder.run}.
 *
 * <ul>
 *   <li>Only where the switch says {@code all}, as the invented members are (MD53), and never where the production
 *       marker is set;</li>
 *   <li>ONLY WHERE THE WORKPLACES ARE ON RECORD (MD54): the take refuses {@code MONTH_NOT_READY} without them, and the
 *       seed takes none -- a month frozen with none would be kept that way by its own triggers;</li>
 *   <li>a month taken already is left as it was: read once, by one statement, at every start (MR-PERF-19) -- and a
 *       month another start took meanwhile is the index's refusal, which is the same answer.</li>
 * </ul>
 * The months are taken the oldest first, so each is frozen before the one after it is.
 */
@Slf4j
@Component
public class MonthEndTakeSeeder {

    /** How many past months the demonstration has: the three 03.7 gives a past. */
    public static final int MONTHS = 3;

    /**
     * When the seed takes a past month: 07:10 on its last working day -- SASBO's month end runs on the last working day
     * and they start at 06:30 to 07:00 (MEN.md, the sitting-in answer) -- so a demonstration reads "September, taken
     * Wednesday 30 September at 07:10", as SASBO's own would, rather than every month taken at the start's instant.
     */
    public static final LocalTime TAKEN_AT = LocalTime.of(7, 10);

    private final MonthEndTakes takes;
    private final SasboEnvironment environment;
    private final WorkplaceSwitch workplaceSwitch;
    private final Clock clock;
    private final HolidayCalendarSource holidays;

    public MonthEndTakeSeeder(MonthEndTakes takes, SasboEnvironment environment, WorkplaceSwitch workplaceSwitch,
                              Clock clock, HolidayCalendarSource holidays) {
        this.takes = takes;
        this.environment = environment;
        this.workplaceSwitch = workplaceSwitch;
        this.clock = clock;
        this.holidays = holidays;
    }

    /**
     * 07:10 in SASBO's zone on {@code month}'s last working day: neither a weekend nor a public holiday of the calendar
     * the debit order runs keep (V47) -- a calendar that knows no holiday leaves the weekends alone to skip.
     */
    static Instant takenAt(YearMonth month, HolidayCalendar calendar) {
        return calendar.businessDayOnOrBefore(month.atEndOfMonth()).atTime(TAKEN_AT).atZone(MonthRange.ZONE).toInstant();
    }

    /**
     * Why the seed takes one of its own months again -- the reason a take again carries (04.11). monthend-rules makes
     * the reason part of the take; until that lands the call below takes it without one.
     */
    public static final String TAKEN_AGAIN_BECAUSE = "Taken again by the seed: demonstration members were added after "
            + "it was first taken.";

    /** @return how many months this start took */
    public int seed() {
        return seed(false);
    }

    /**
     * Takes the past months not taken yet -- and, where {@code demonstrationWrote}, takes AGAIN each one whose newest
     * take is the seed's own (MD70): a month the seed took at an earlier start is frozen without whatever a
     * demonstration seeder wrote into it since, as dev.sasbo.online's July to September were taken on 1 Oct before the
     * October population existed. A month whose newest take is a person's is never touched, and a second start writes
     * nothing, so it takes nothing again.
     *
     * @param demonstrationWrote whether a demonstration seeder wrote rows at this start ({@code DataSeeder.run})
     * @return how many months this start took, taken again included
     */
    public int seed(boolean demonstrationWrote) {
        if (environment.isProduction() || !workplaceSwitch.staffNumbersServed()) {
            return 0;
        }
        YearMonth open = MonthRange.current(clock);
        Map<LocalDate, TakenMonth> taken = takes.takenMonths().stream()
                .collect(Collectors.toMap(TakenMonth::monthStart, Function.identity(), (newer, older) -> newer));
        int took = 0;
        int again = 0;
        HolidayCalendar calendar = null;
        for (int back = MONTHS; back >= 1; back--) {
            YearMonth month = open.minusMonths(back);
            TakenMonth newest = taken.get(month.atDay(1));
            if (newest != null && !(demonstrationWrote
                    && DemonstrationMemberSeeder.SEED_ACTOR.equals(newest.takenBy()))) {
                continue;
            }
            try {
                if (newest == null) {
                    // The calendar is read once, and only by a start that takes a month (MR-PERF-19).
                    calendar = calendar == null ? holidays.load() : calendar;
                    takes.takeAsAt(month, DemonstrationMemberSeeder.SEED_ACTOR, takenAt(month, calendar));
                    took++;
                } else {
                    takeAgain(month, newest);
                    again++;
                }
            } catch (MonthEndRefusal refused) {
                if (refused.code() == MonthEndRefusalCode.MONTH_NOT_READY) {
                    log.info("The demonstration's past months are not taken: no workplace is on record here");
                    return took + again;
                }
                // Another start took the month, or took it again, at the same moment: the index's answer, and the same.
                if (refused.code() != MonthEndRefusalCode.MONTH_ALREADY_TAKEN
                        && refused.code() != MonthEndRefusalCode.MONTH_TAKEN_SINCE) {
                    throw refused;
                }
            }
        }
        if (took + again > 0) {
            log.info("Took {} of the demonstration's past months, and took {} of the seed's own again", took, again);
        }
        return took + again;
    }

    /**
     * A new take of {@code month} by the seed, beside the newest it replaces (04.11) -- at the clock's instant, because
     * it happens now, after the take it replaces. At integration this becomes monthend-rules'
     * {@code take(month, actor, replaces, reason)} with {@link #TAKEN_AGAIN_BECAUSE}.
     */
    private void takeAgain(YearMonth month, TakenMonth newest) {
        takes.take(month, DemonstrationMemberSeeder.SEED_ACTOR, newest.id());
    }
}
