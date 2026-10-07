package za.co.titandynamix.sasbo_online_backend.support;

import org.springframework.beans.factory.config.BeanPostProcessor;

import javax.sql.DataSource;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.Statement;
import java.util.Set;

/**
 * Counts the statements that reach the database AT THE CONNECTION -- whoever sends them (month end, MR-PERF-19,
 * MV-63).
 *
 * <h2>Why not {@link QueryCount}</h2>
 * {@code QueryCount} reads Hibernate's statistics, and a statement that JDBC issues is not Hibernate's: month end's
 * seeders write by {@code JdbcTemplate} batch, so a guard built on {@code QueryCount} would count nothing, pass, and
 * pin nothing. This wraps the {@link DataSource} itself, so every {@code execute}, {@code executeQuery},
 * {@code executeUpdate} and {@code executeBatch} on any connection it hands out is one statement -- a batch of five
 * hundred rows is one round trip, and is counted once, as the database sees it.
 *
 * <p>Registered in a test's context with {@link #register()}; read with {@link #during}.
 *
 * <h2>Counted on the thread that runs the work</h2>
 * A statement is counted against the thread that sends it, and {@link #during} reads the calling thread's count --
 * so the work it measures must run on the calling thread, which every JDBC call of month end does. 🔴 It was one count
 * for the whole JVM until 2026-09-29, and this module runs its test classes concurrently: once a second class counted
 * (02.2's {@code MemberSummaryTest}, beside {@code MonthEndSeedTest}), each counted the other's statements whenever the
 * random class order ran them together -- 27 for a pinned 1, 18 for a pinned 16, in the same code that had passed an
 * hour before (run 36621599441). A context of one's own does not scope a static. {@code ConnectionStatementsTest}
 * holds the rule, with a statement sent from another thread during the count.
 */
public final class ConnectionStatements {

    /** Each thread's count. One slot, read and written only by its own thread. */
    private static final ThreadLocal<long[]> COUNT = ThreadLocal.withInitial(() -> new long[1]);
    private static final Set<String> EXECUTIONS = Set.of("execute", "executeQuery", "executeUpdate", "executeBatch",
            "executeLargeUpdate", "executeLargeBatch");

    private ConnectionStatements() {
    }

    /** A bean post-processor that wraps the context's {@code DataSource}. Declare it as a static {@code @Bean}. */
    public static BeanPostProcessor register() {
        return new BeanPostProcessor() {
            @Override
            public Object postProcessAfterInitialization(Object bean, String beanName) {
                return bean instanceof DataSource dataSource && !Proxy.isProxyClass(bean.getClass())
                        ? wrap(dataSource) : bean;
            }
        };
    }

    /** How many statements this thread sent to the database while {@code work} ran on it. */
    public static long during(Runnable work) {
        long[] count = COUNT.get();
        long before = count[0];
        work.run();
        return count[0] - before;
    }

    /**
     * A data source whose every statement is counted on the sending thread. Public since the October build, so an
     * integration test can count one repository's statements without a context of its own -- each context the lane
     * caches holds a pool against the one container.
     */
    public static DataSource wrap(DataSource dataSource) {
        return (DataSource) Proxy.newProxyInstance(DataSource.class.getClassLoader(),
                new Class<?>[] {DataSource.class}, forwarding(dataSource, (method, result) ->
                        result instanceof Connection connection ? connection(connection) : result));
    }

    private static Connection connection(Connection connection) {
        return (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(),
                new Class<?>[] {Connection.class}, forwarding(connection, (method, result) ->
                        result instanceof Statement statement ? statement(statement) : result));
    }

    private static Statement statement(Statement statement) {
        Class<?>[] types = statement instanceof java.sql.CallableStatement
                ? new Class<?>[] {java.sql.CallableStatement.class}
                : statement instanceof java.sql.PreparedStatement
                        ? new Class<?>[] {java.sql.PreparedStatement.class}
                        : new Class<?>[] {Statement.class};
        return (Statement) Proxy.newProxyInstance(Statement.class.getClassLoader(), types, (proxy, method, args) -> {
            if (EXECUTIONS.contains(method.getName())) {
                COUNT.get()[0]++;
            }
            return invoke(statement, method, args);
        });
    }

    private interface After {
        Object apply(java.lang.reflect.Method method, Object result);
    }

    private static InvocationHandler forwarding(Object target, After after) {
        return (proxy, method, args) -> {
            if (method.getName().equals("unwrap") && args != null && args.length == 1
                    && args[0] instanceof Class<?> type && type.isInstance(target)) {
                return target;
            }
            if (method.getName().equals("isWrapperFor") && args != null && args.length == 1
                    && args[0] instanceof Class<?> type && type.isInstance(target)) {
                return true;
            }
            return after.apply(method, invoke(target, method, args));
        };
    }

    private static Object invoke(Object target, java.lang.reflect.Method method, Object[] args) throws Throwable {
        try {
            return method.invoke(target, args);
        } catch (InvocationTargetException thrown) {
            throw thrown.getCause();
        }
    }
}
