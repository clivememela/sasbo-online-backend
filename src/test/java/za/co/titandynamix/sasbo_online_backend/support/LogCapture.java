package za.co.titandynamix.sasbo_online_backend.support;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.slf4j.ILoggerFactory;
import org.slf4j.LoggerFactory;

/**
 * Collects what one named logger emits, for the duration of one test.
 *
 * <h2>One logger, never the root one</h2>
 * Attaching to the root logger is the obvious move and it is wrong here. Test
 * classes run concurrently, so a root appender collects every sibling's output
 * too -- which makes "the log contained no identity number" depend on what else
 * happened to be running. That is a flake with a plausible-looking assertion in
 * front of it. Capturing one named logger makes a test's evidence its own.
 *
 * <h2>Nothing here waits, and that took two attempts</h2>
 * Logback's {@code LoggerContext} is reached by casting what SLF4J hands back.
 * While <b>another thread</b> is initialising SLF4J, that is a
 * {@code SubstituteLoggerFactory} instead and the cast throws -- which is what
 * happens when a log-capturing class starts on a ForkJoin worker beside a class
 * booting a Spring context. Observed on 2026-09-04 as four tests failing
 * together in their own setup, on a suite that had been green minutes before.
 *
 * <p>The first fix spun until the factory was the real one. It made things
 * worse: the spinning workers starved the thread doing the initialising, so a
 * million spins later it still had not finished. <b>A wait that competes with
 * the thing it is waiting for does not converge</b>, and a {@code sleep} in its
 * place is a flake with a delay in front of it.
 *
 * <p>So the race is removed instead. {@link LoggingBootstrap} does the binding
 * once, on the launcher thread, before the parallel phase begins -- and the
 * check below is a plain assertion that it happened, not a retry.
 */
public final class LogCapture implements AutoCloseable {

    private final Logger logger;
    private final ListAppender<ILoggingEvent> appender;
    private final Level previousLevel;

    private LogCapture(Logger logger) {
        this.logger = logger;
        this.previousLevel = logger.getLevel();
        this.appender = new ListAppender<>();
        this.appender.start();
        logger.addAppender(this.appender);
        logger.setLevel(Level.TRACE);
    }

    /** Starts collecting everything {@code type}'s logger emits. */
    public static LogCapture on(Class<?> type) {
        return new LogCapture(context().getLogger(type));
    }

    /**
     * Everything captured so far, one event per line, with any attached
     * throwable's message on its own line -- because that is where a driver
     * puts the SQL, and the SQL is what a PII assertion is looking for.
     */
    public String rendered() {
        StringBuilder all = new StringBuilder();
        for (ILoggingEvent event : this.appender.list) {
            all.append(event.getFormattedMessage()).append('\n');
            if (event.getThrowableProxy() != null) {
                all.append(event.getThrowableProxy().getMessage()).append('\n');
            }
        }
        return all.toString();
    }

    /**
     * Only what was logged at exactly {@code level}, one event per line -- for a test whose claim IS
     * the level ("said at WARN"). {@link #rendered()} captures at TRACE and carries no level, so a
     * WARN demoted to DEBUG reads the same there; found by deleting a WARN and watching a test stay
     * green (2026-09-21).
     */
    public String renderedAt(Level level) {
        StringBuilder at = new StringBuilder();
        for (ILoggingEvent event : this.appender.list) {
            if (event.getLevel() == level) {
                at.append(event.getFormattedMessage()).append('\n');
            }
        }
        return at.toString();
    }

    /** Writes through the captured logger, for a test that needs a canary. */
    public void emitCanary(String message) {
        this.logger.error(message);
    }

    /**
     * Detaches and restores the level. Not optional: a logger is process-global,
     * so an appender left attached keeps collecting for the rest of the JVM's
     * life, and a level left at TRACE changes what every later test logs.
     */
    @Override
    public void close() {
        this.logger.detachAppender(this.appender);
        this.logger.setLevel(this.previousLevel);
        this.appender.stop();
    }

    private static LoggerContext context() {
        ILoggerFactory factory = LoggerFactory.getILoggerFactory();
        if (factory instanceof LoggerContext loggerContext) {
            return loggerContext;
        }
        throw new IllegalStateException(
                "SLF4J is bound to " + factory.getClass().getName() + " rather than Logback's "
                        + "LoggerContext. That means initialisation was still in progress on "
                        + "another thread -- which LoggingBootstrap exists to make impossible by "
                        + "doing it once, first, on the launcher thread. Check that "
                        + "META-INF/services/org.junit.platform.launcher.LauncherSessionListener "
                        + "still names it. Nothing here waits or retries: a wait that competes "
                        + "with the thread it is waiting for does not converge, and a sleep in "
                        + "its place is a flake with a delay.");
    }
}
