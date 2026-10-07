package za.co.titandynamix.sasbo_online_backend.services;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Starts a data-migration job -- a check, a move, an undo -- off the request thread: the migration's OWN launcher, of
 * the shape of ACB's and month end's and never theirs, so a half-million-row check never stands in front of a debit run
 * or a statistics pack.
 *
 * <p>A type of its own, and no {@code Executor} bean (which would change how the rest of the application runs
 * asynchronous work). Under test it runs on the caller's thread ({@code migration.jobs.synchronous}, set in both test
 * profiles). Production has one worker thread: one job at a time, which the batches' own index holds as well.
 */
@FunctionalInterface
public interface MigrationJobLauncher {

    /** Starts {@code job}. May run it before returning, or on another thread. */
    void launch(Runnable job);

    /** Runs every job on the caller's thread, before returning. For tests. */
    static MigrationJobLauncher inline() {
        return Runnable::run;
    }

    /** One worker thread, a daemon: a job killed by a redeploy is found and swept at the next start, never resumed. */
    static SingleThread singleThread() {
        return new SingleThread();
    }

    /** The production launcher, closed with the application context. */
    final class SingleThread implements MigrationJobLauncher, AutoCloseable {

        private final ExecutorService worker = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "migration-job");
            thread.setDaemon(true);
            return thread;
        });

        private SingleThread() {
        }

        @Override
        public void launch(Runnable job) {
            worker.execute(job);
        }

        @Override
        public void close() {
            worker.shutdown();
        }
    }
}
