package za.co.titandynamix.sasbo_online_backend.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import za.co.titandynamix.sasbo_online_backend.services.AcbRunService;

/**
 * At startup, fails every run whose preparation died with the previous instance.
 *
 * <p>Every merge redeploys the service, and a redeploy kills a running preparation. Its run is left
 * {@code PREPARING} with a heartbeat that has stopped. Reading the runs sweeps it too; this makes
 * sure it is swept even if nobody opens the screen.
 */
@Slf4j
@Component
public class AcbStartupSweep {

    private final AcbRunService runs;

    public AcbStartupSweep(AcbRunService runs) {
        this.runs = runs;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void sweepInterruptedPreparations() {
        int swept = runs.sweepInterrupted();
        if (swept > 0) {
            log.warn("ACB: {} interrupted preparation(s) swept to FAILED at startup", swept);
        }
    }
}
