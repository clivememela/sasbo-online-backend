package za.co.titandynamix.sasbo_online_backend.utils;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import za.co.titandynamix.sasbo_online_backend.config.SasboEnvironment;
import za.co.titandynamix.sasbo_online_backend.config.WorkplaceSwitch;
import za.co.titandynamix.sasbo_online_backend.exception.MonthEndRefusal;
import za.co.titandynamix.sasbo_online_backend.exception.MonthEndRefusalCode;
import za.co.titandynamix.sasbo_online_backend.repository.MonthEndFactsRepository.TakenMonth;
import za.co.titandynamix.sasbo_online_backend.services.MonthEndTakes;

import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Set;
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

    private final MonthEndTakes takes;
    private final SasboEnvironment environment;
    private final WorkplaceSwitch workplaceSwitch;
    private final Clock clock;

    public MonthEndTakeSeeder(MonthEndTakes takes, SasboEnvironment environment, WorkplaceSwitch workplaceSwitch,
                              Clock clock) {
        this.takes = takes;
        this.environment = environment;
        this.workplaceSwitch = workplaceSwitch;
        this.clock = clock;
    }

    /** @return how many months this start took */
    public int seed() {
        if (environment.isProduction() || !workplaceSwitch.staffNumbersServed()) {
            return 0;
        }
        YearMonth open = MonthRange.current(clock);
        Set<LocalDate> taken = takes.takenMonths().stream().map(TakenMonth::monthStart).collect(Collectors.toSet());
        int took = 0;
        for (int back = MONTHS; back >= 1; back--) {
            YearMonth month = open.minusMonths(back);
            if (taken.contains(month.atDay(1))) {
                continue;
            }
            try {
                takes.take(month, DemonstrationMemberSeeder.SEED_ACTOR);
                took++;
            } catch (MonthEndRefusal refused) {
                if (refused.code() == MonthEndRefusalCode.MONTH_NOT_READY) {
                    log.info("The demonstration's past months are not taken: no workplace is on record here");
                    return took;
                }
                if (refused.code() != MonthEndRefusalCode.MONTH_ALREADY_TAKEN) {
                    throw refused;
                }
            }
        }
        if (took > 0) {
            log.info("Took {} of the demonstration's past months", took);
        }
        return took;
    }
}
