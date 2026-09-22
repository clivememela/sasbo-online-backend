package za.co.titandynamix.sasbo_online_backend.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import za.co.titandynamix.sasbo_online_backend.services.AcbFileService;
import za.co.titandynamix.sasbo_online_backend.services.AcbRunService;

/**
 * At startup, fails every run whose preparation died with the previous instance, and drops the
 * bytes of every bureau file past its purge date.
 *
 * <p>Every merge redeploys the service, and a redeploy kills a running preparation. Its run is left
 * {@code PREPARING} with a heartbeat that has stopped. Reading the runs sweeps it too; this makes
 * sure it is swept even if nobody opens the screen.
 *
 * <p>A stored file is member data at rest -- every debited member's account number. Past the purge
 * date the bank no longer holds it either, so its bytes go and its hash and totals stay. At startup
 * because the service restarts at least on every deploy; a schedule can come when the files do.
 */
@Slf4j
@Component
public class AcbStartupSweep {

    private final AcbRunService runs;
    private final AcbFileService files;

    public AcbStartupSweep(AcbRunService runs, AcbFileService files) {
        this.runs = runs;
        this.files = files;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void sweepInterruptedPreparations() {
        int swept = runs.sweepInterrupted();
        if (swept > 0) {
            log.warn("ACB: {} interrupted preparation(s) swept to FAILED at startup", swept);
        }
        int purged = files.purgeExpired();
        if (purged > 0) {
            log.info("ACB: {} bureau file(s) past their purge date had their bytes dropped", purged);
        }
    }
}
