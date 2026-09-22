package za.co.titandynamix.sasbo_online_backend.acb;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Starts an ACB job -- a preparation now, the letters later -- off the request thread.
 *
 * <h2>Triggered, never scheduled</h2>
 * A job starts because an administrator asked for it. Nothing in ACB runs on a timer, so nothing
 * runs when nobody is watching and nothing needs a scheduler that every test context would start.
 *
 * <h2>Why this is not an {@code Executor} bean</h2>
 * Spring Boot backs off its own application task executor the moment ANY bean of type
 * {@code Executor} exists, which would quietly change how every other part of the application runs
 * asynchronous work. A launcher of ACB's own type changes nothing but ACB.
 *
 * <h2>Synchronous under every test lane</h2>
 * {@link #inline()} runs the job on the caller's thread, so a test observes its result without
 * waiting and nothing runs after the test has finished. Production uses one worker thread: a month
 * is prepared at most once at a time, and a queue of one is honest about that.
 */
@FunctionalInterface
public interface AcbJobLauncher {

    /** Starts {@code job}. May run it before returning, or on another thread. */
    void launch(Runnable job);

    /** Runs every job on the caller's thread, before returning. For tests. */
    static AcbJobLauncher inline() {
        return Runnable::run;
    }

    /** One worker thread, a daemon: a job killed by a redeploy is found and swept, never resumed. */
    static SingleThread singleThread() {
        return new SingleThread();
    }

    /** The production launcher, closed with the application context. */
    final class SingleThread implements AcbJobLauncher, AutoCloseable {

        private final ExecutorService worker = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "acb-job");
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
