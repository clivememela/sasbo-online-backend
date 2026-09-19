package za.co.titandynamix.sasbo_online_backend.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import za.co.titandynamix.sasbo_online_backend.acb.AcbJobLauncher;
import za.co.titandynamix.sasbo_online_backend.acb.AcbTime;
import za.co.titandynamix.sasbo_online_backend.acb.HolidayCalendar;

import java.time.Clock;

/**
 * What the ACB module is wired with: the clock it reads, the holidays it knows, and how its jobs run.
 */
@Configuration
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
     * Weekends only, until a maintained public-holiday calendar is wired. Stated rather than
     * defaulted: every run records the calendar it used, and every screen showing its dates
     * carries the caveat that holidays were not considered.
     */
    @Bean
    public HolidayCalendar acbHolidayCalendar() {
        return HolidayCalendar.WEEKENDS_ONLY;
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
