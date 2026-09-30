package za.co.titandynamix.sasbo_online_backend.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import za.co.titandynamix.sasbo_online_backend.services.MonthEndPackLauncher;
import za.co.titandynamix.sasbo_online_backend.services.MonthEndPacks;

/**
 * Month end's statistics pack (04.10): its own launcher -- synchronous under every test lane
 * ({@code month-end.packs.synchronous: true}), so no test waits for a job and none outlives its test -- and, at start,
 * the sweep that finds a pack whose job died with the previous instance (ME-21). Every merge redeploys the service,
 * and a redeploy kills a pack being prepared; reading the packs sweeps it too, and this makes sure it is swept even
 * if nobody asks.
 */
@Slf4j
@Configuration
public class MonthEndPackConfig {

    @Bean
    public MonthEndPackLauncher monthEndPackLauncher(@Value("${month-end.packs.synchronous:false}") boolean synchronous) {
        return synchronous ? MonthEndPackLauncher.inline() : MonthEndPackLauncher.singleThread();
    }

    /** The sweep at start: a pack left PREPARING by an instance that died is FAILED, interrupted. */
    @Bean
    public MonthEndPackSweep monthEndPackSweep(MonthEndPacks packs) {
        return new MonthEndPackSweep(packs);
    }

    /** Sweeps once the application is ready. */
    public static final class MonthEndPackSweep {

        private final MonthEndPacks packs;

        MonthEndPackSweep(MonthEndPacks packs) {
            this.packs = packs;
        }

        @EventListener(ApplicationReadyEvent.class)
        public void sweepInterrupted() {
            int swept = packs.sweepInterrupted();
            if (swept > 0) {
                log.warn("Month end: {} interrupted statistics pack(s) swept to FAILED at startup", swept);
            }
        }
    }
}
