package za.co.titandynamix.sasbo_online_backend.services;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Starts a statistics pack's job off the request thread (month end 04.10) -- month end's OWN launcher, of the shape of
 * ACB's and never ACB's bean: a statistics pack must not stand in front of a debit run (MR-PERF-11, MR-SCALE-7).
 *
 * <p>A TYPE OF ITS OWN: ACB's services take ACB's launcher by its type, and a second bean of that type would stop the
 * start. And no {@code Executor} bean, which would quietly change how the rest of the application runs asynchronous
 * work. Under test it runs on the caller's thread ({@code month-end.packs.synchronous}, set in both test profiles, as
 * {@code acb.jobs.synchronous} is), or every test of the pack would race its own job (MR-ROB-33). Production has one
 * worker thread: one pack is prepared at a time, and the table holds the rest to that (no queue).
 */
@FunctionalInterface
public interface MonthEndPackLauncher {

    /** Starts {@code job}. May run it before returning, or on another thread. */
    void launch(Runnable job);

    /** Runs every job on the caller's thread, before returning. For tests. */
    static MonthEndPackLauncher inline() {
        return Runnable::run;
    }

    /** One worker thread, a daemon: a job killed by a redeploy is found and swept, never resumed. */
    static SingleThread singleThread() {
        return new SingleThread();
    }

    /** The production launcher, closed with the application context. */
    final class SingleThread implements MonthEndPackLauncher, AutoCloseable {

        private final ExecutorService worker = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "month-end-pack");
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
