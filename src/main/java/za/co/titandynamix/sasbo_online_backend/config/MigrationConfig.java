package za.co.titandynamix.sasbo_online_backend.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import za.co.titandynamix.sasbo_online_backend.acb.AcbLogging;
import za.co.titandynamix.sasbo_online_backend.repository.MigrationBatchRepository;
import za.co.titandynamix.sasbo_online_backend.services.MigrationJobLauncher;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * The data migration's wiring: its ceilings ({@code migration.*}), its own job launcher -- synchronous under every test
 * lane ({@code migration.jobs.synchronous: true}), so no test waits for a job and none outlives its test -- and, at
 * start, the sweep that finds what died with the previous instance: a check, move or undo still RUNNING with a quiet
 * heartbeat is FAILED, interrupted (a move resumes from its cursor when asked again); a file still being read is
 * deleted with its rows, since a half-read file keeps nothing.
 */
@Slf4j
@Configuration
@EnableConfigurationProperties(MigrationProperties.class)
public class MigrationConfig {

    static final String INTERRUPTED = "The job was interrupted when the application restarted. Ask for it again.";

    @Bean
    public MigrationJobLauncher migrationJobLauncher(@Value("${migration.jobs.synchronous:false}") boolean synchronous) {
        return synchronous ? MigrationJobLauncher.inline() : MigrationJobLauncher.singleThread();
    }

    @Bean
    public MigrationSweep migrationSweep(MigrationBatchRepository batches, MigrationProperties properties, Clock clock) {
        return new MigrationSweep(batches, properties, clock);
    }

    /** Sweeps once the application is ready -- caught, so a sweep that fails never stops a start. */
    public static final class MigrationSweep {

        private final MigrationBatchRepository batches;
        private final MigrationProperties properties;
        private final Clock clock;

        MigrationSweep(MigrationBatchRepository batches, MigrationProperties properties, Clock clock) {
            this.batches = batches;
            this.properties = properties;
            this.clock = clock;
        }

        @EventListener(ApplicationReadyEvent.class)
        public void sweep() {
            try {
                int[] swept = sweepNow();
                if (swept[0] > 0 || swept[1] > 0) {
                    log.warn("Migration: {} interrupted job(s) swept to FAILED, {} half-read file(s) removed at startup",
                            swept[0], swept[1]);
                }
            } catch (RuntimeException failed) {
                log.error("Migration: the startup sweep failed; the application starts without it",
                        AcbLogging.scrubbed(failed));
            }
        }

        /** The jobs swept, and the half-read files removed. */
        public int[] sweepNow() {
            Instant now = clock.instant();
            Instant stale = now.minus(Duration.ofMinutes(properties.staleJobMinutes()));
            List<Long> jobs = batches.sweepJobs(stale, INTERRUPTED, now);
            for (long id : jobs) {
                // A check cut short leaves its rows half re-checked: the batch is a DRAFT until it is checked again.
                batches.find(id).filter(batch -> MigrationBatchRepository.VALIDATE.equals(batch.jobKind()))
                        .ifPresent(batch -> batches.toDraft(id));
            }
            List<Long> receiving = batches.staleReceiving(stale);
            receiving.forEach(batches::delete);
            return new int[] {jobs.size(), receiving.size()};
        }
    }
}
