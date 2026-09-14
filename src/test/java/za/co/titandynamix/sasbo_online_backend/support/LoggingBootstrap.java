package za.co.titandynamix.sasbo_online_backend.support;

import org.junit.platform.launcher.LauncherSession;
import org.junit.platform.launcher.LauncherSessionListener;
import org.slf4j.LoggerFactory;

/**
 * Binds SLF4J to Logback once, on the launcher thread, before any test runs.
 *
 * <h2>The race this removes</h2>
 * SLF4J initialises lazily on whichever thread asks first. While that is in
 * progress, every OTHER thread is handed a {@code SubstituteLoggerFactory} --
 * which is not Logback's {@code LoggerContext}, so a test that captures log
 * output cannot attach an appender and fails in its own setup.
 *
 * <p>Under a parallel runner that is a coin toss: a log-capturing class starting
 * beside a class booting a Spring context loses, and the same suite is green on
 * the next run. Observed on 2026-09-04 as four tests failing together with
 * {@code ClassCastException}, on a suite that had passed minutes earlier.
 *
 * <h2>Why waiting for it is the wrong fix</h2>
 * The first attempt was to spin until the factory was the real one. It made
 * things worse: the spinning workers starved the thread that was doing the
 * initialising, so a million spins later it still had not finished. A wait that
 * competes with the thing it is waiting for does not converge -- and a
 * {@code sleep} in its place would just be a flake with a delay in front of it.
 *
 * <p>So nothing waits. The work is done once, first, on a thread that has no
 * competition, and by the time the parallel phase begins there is nothing left
 * to race over. Registered through {@code META-INF/services}, which is how the
 * JUnit Platform finds it without any test having to remember it exists.
 */
public class LoggingBootstrap implements LauncherSessionListener {

    @Override
    public void launcherSessionOpened(LauncherSession session) {
        // Forces the binding on this thread. The return value is deliberately
        // unused: the point is the initialisation, not the logger.
        LoggerFactory.getILoggerFactory();
    }
}
