package za.co.titandynamix.sasbo_online_backend.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import za.co.titandynamix.sasbo_online_backend.acb.AcbJobLauncher;
import za.co.titandynamix.sasbo_online_backend.acb.AcbTime;

import java.time.Clock;

/**
 * What the ACB module is wired with: the clock it reads, how its jobs run, and the bureau file's
 * settings ({@code acb.file.*}). The holidays it knows are data -- V47's table, read by
 * {@code AcbHolidayCalendarSource} (D30).
 */
@Configuration
@EnableConfigurationProperties(AcbFileProperties.class)
public class AcbConfig {

    /**
     * The system clock, in Johannesburg. The ONE clock ACB reads -- every rule is handed this and
     * never asks the system for the time, so every test can freeze it.
     */
    @Bean
    public Clock acbClock() {
        return Clock.system(AcbTime.ZONE);
    }

    /**
     * One worker thread in production; the caller's own thread under every test lane
     * ({@code acb.jobs.synchronous: true}), so no test waits for a job and none outlives its test.
     */
    @Bean
    public AcbJobLauncher acbJobLauncher(@Value("${acb.jobs.synchronous:false}") boolean synchronous) {
        return synchronous ? AcbJobLauncher.inline() : AcbJobLauncher.singleThread();
    }
}
