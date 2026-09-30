package za.co.titandynamix.sasbo_online_backend.support;

import org.springframework.beans.factory.config.BeanPostProcessor;

import javax.sql.DataSource;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.Statement;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

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
 * <p>Registered in a test's context with {@link #register()}; read with {@link #during}. The count is global to the
 * context, so a test that pins a number runs its work on its own thread with nothing else writing -- which is why the
 * tests that use it are in a context of their own.
 */
public final class ConnectionStatements {

    private static final AtomicLong COUNT = new AtomicLong();
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

    /** How many statements reached the database while {@code work} ran. */
    public static long during(Runnable work) {
        long before = COUNT.get();
        work.run();
        return COUNT.get() - before;
    }

    static DataSource wrap(DataSource dataSource) {
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
                COUNT.incrementAndGet();
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
