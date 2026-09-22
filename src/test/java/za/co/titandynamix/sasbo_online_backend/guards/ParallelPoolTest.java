package za.co.titandynamix.sasbo_online_backend.guards;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.platform.engine.ConfigurationParameters;
import org.junit.platform.engine.support.config.PrefixedConfigurationParameters;
import org.junit.platform.engine.support.hierarchical.DefaultParallelExecutionConfigurationStrategy;
import org.junit.platform.engine.support.hierarchical.ParallelExecutionConfiguration;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * JUnit's worker pool may not grow past its parallelism.
 *
 * <h2>🔴 Why: one test's transaction leaked into another class's test</h2>
 * On 2026-09-22, the first P1 runs on the owner's repositories: {@code RecruiterControllerTest}
 * failed with <i>"Cannot start new transaction without ending existing transaction"</i>, thrown
 * by Spring's {@code TransactionalTestExecutionListener} before the test body ran, in a pull
 * request that changed only CI configuration (run 35720901980). It ran on JUnit worker 3 of a
 * pool whose parallelism is 2. The mechanism, read from the bytecode of the versions in use
 * rather than assumed:
 * <ul>
 *   <li>Spring 6.2's {@code TransactionContextHolder} keeps the test-managed transaction in a
 *       {@code NamedInheritableThreadLocal}. A thread <b>created by</b> a thread that is inside
 *       a test transaction starts out holding that transaction.</li>
 *   <li>JUnit Platform 1.11's pool workers are {@code ForkJoinWorkerThread}s built with the
 *       constructor that inherits thread locals.</li>
 *   <li>The {@code dynamic} strategy's default maximum pool size is 256 + parallelism. When a
 *       test blocks in a way the JDK reports to the pool ({@code ForkJoinPool.managedBlock},
 *       which {@code CompletableFuture.join}, condition waits and queue hand-offs use) and no
 *       worker is idle, the pool compensates by creating a worker <b>from the blocked
 *       thread</b>. That worker inherits the blocked test's transaction and takes the next
 *       queued class, and the first transactional test it runs finds a transaction already
 *       open. A worker-3 appears in that run's log and in a green run's from the same hour:
 *       compensation is routine, and it only fails a test when the blocked thread was inside a
 *       transaction at the time.</li>
 * </ul>
 * Capping the pool at its parallelism removes the compensation, so no worker is ever created
 * from inside a test. {@code junit-platform.properties} carries the setting, and this holds it
 * there. The cost is small: while a test blocks, the pool runs one class fewer.
 *
 * <p>Fast lane: no Spring context, no database. It asks JUnit's own launcher for the
 * configuration parameters, which reads {@code junit-platform.properties} from the classpath as
 * a real run does, and asks JUnit's own strategy what pool they configure.
 */
@DisplayName("JUnit's parallel pool")
class ParallelPoolTest {

    private static final String PREFIX = "junit.jupiter.execution.parallel.config.";

    private static final ConfigurationParameters PARAMETERS =
            LauncherDiscoveryRequestBuilder.request().build().getConfigurationParameters();

    private static final ParallelExecutionConfiguration POOL = DefaultParallelExecutionConfigurationStrategy
            .valueOf(PARAMETERS.get(PREFIX + "strategy").orElse("dynamic").toUpperCase(Locale.ROOT))
            .createConfiguration(new PrefixedConfigurationParameters(PARAMETERS, PREFIX));

    @Test
    @DisplayName("the configuration was read: classes run in parallel under the dynamic strategy")
    void theConfigurationWasRead() {
        // Non-vacuity. Had the properties file not been found, the cap below would be asserted
        // about JUnit's defaults, and a suite running serially has no pool to cap.
        assertThat(PARAMETERS.get("junit.jupiter.execution.parallel.enabled")).contains("true");
        assertThat(PARAMETERS.get(PREFIX + "strategy")).contains("dynamic");
    }

    @Test
    @DisplayName("the pool cannot grow past its parallelism, so no worker is created from inside a test")
    void thePoolCannotOutgrowItsParallelism() {
        assertThat(POOL.getMaxPoolSize())
                .as("a maximum above the parallelism lets a blocked test's thread create a worker, and "
                        + "that worker inherits the test's transaction: see ParallelPoolTest's Javadoc")
                .isEqualTo(POOL.getParallelism());
    }

    @Test
    @DisplayName("a blocked worker waits for its turn rather than throwing")
    void aBlockedWorkerWaits() {
        // With the pool capped, a blocked worker cannot be compensated. `saturate` lets it simply
        // wait; without it the pool would throw RejectedExecutionException out of whatever the
        // test was doing.
        assertThat(POOL.getSaturatePredicate()).isNotNull();
        assertThat(POOL.getSaturatePredicate().test(null)).isTrue();
    }
}
