package za.co.titandynamix.sasbo_online_backend.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import za.co.titandynamix.sasbo_online_backend.acb.AcbLogging;
import za.co.titandynamix.sasbo_online_backend.services.PayrollRetention;

/**
 * Payroll's one job: deleting files a year after they were loaded (Stephne, PAY-39), every night at 02:30 in SASBO's
 * zone and once when the application is ready -- a host that sleeps between requests may never be awake at 02:30.
 * {@code payroll.retention.enabled=false} turns both off (the test lanes do, so no job runs beside a test).
 */
@Slf4j
@Configuration
@EnableScheduling
@ConditionalOnProperty(prefix = "payroll.retention", name = "enabled", havingValue = "true", matchIfMissing = true)
public class PayrollConfig {

    private final PayrollRetention retention;

    public PayrollConfig(PayrollRetention retention) {
        this.retention = retention;
    }

    @Scheduled(cron = "${payroll.retention.cron:0 30 2 * * *}", zone = "Africa/Johannesburg")
    public void nightly() {
        run();
    }

    @EventListener(ApplicationReadyEvent.class)
    public void atStart() {
        run();
    }

    private void run() {
        try {
            retention.purgeDue();
        } catch (RuntimeException failed) {
            log.error("The payroll retention job failed; it runs again tonight", AcbLogging.scrubbed(failed));
        }
    }
}
