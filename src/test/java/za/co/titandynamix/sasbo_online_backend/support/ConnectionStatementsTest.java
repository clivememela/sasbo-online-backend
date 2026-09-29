package za.co.titandynamix.sasbo_online_backend.support;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The count at the connection is the calling thread's (month end, MR-PERF-19). This module runs its test classes
 * concurrently, so a count that took every thread's statements took every other class's too -- and a pinned number
 * then reddened whenever the random order ran two counting classes together (run 36621599441).
 *
 * <p>No database: a {@link DataSource} that answers every statement and holds nothing is enough to count.
 */
@DisplayName("Statements counted at the connection")
class ConnectionStatementsTest {

    @Test
    @DisplayName("must-pass: every statement the calling thread sends is counted")
    void theCallingThreadsStatementsAreCounted() {
        DataSource counted = ConnectionStatements.wrap(nowhere());

        long statements = ConnectionStatements.during(() -> {
            send(counted);
            send(counted);
            send(counted);
        });

        assertThat(statements).isEqualTo(3);
    }

    @Test
    @DisplayName("🔒 a statement another thread sends during the count is not this count's -- the class beside it is not")
    void anotherThreadsStatementsAreNotCounted() throws Exception {
        DataSource counted = ConnectionStatements.wrap(nowhere());
        ExecutorService beside = Executors.newSingleThreadExecutor();
        try {
            long statements = ConnectionStatements.during(() -> {
                send(counted);
                CompletableFuture.runAsync(() -> {
                    send(counted);
                    send(counted);
                    send(counted);
                }, beside).join();
            });

            assertThat(statements).as("the one statement this thread sent, and none of the three sent beside it")
                    .isEqualTo(1);
            long besideCounted = CompletableFuture.supplyAsync(() -> ConnectionStatements.during(() -> send(counted)),
                    beside).get();
            assertThat(besideCounted).as("the thread beside counts its own").isEqualTo(1);
        } finally {
            beside.shutdownNow();
        }
    }

    /** One statement, on a connection of its own: a query. */
    private static void send(DataSource dataSource) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT 1")) {
            statement.executeQuery();
        } catch (SQLException unreachable) {
            throw new IllegalStateException(unreachable);
        }
    }

    /** A database that answers every statement and holds nothing. */
    private static DataSource nowhere() {
        PreparedStatement statement = (PreparedStatement) Proxy.newProxyInstance(
                PreparedStatement.class.getClassLoader(), new Class<?>[] {PreparedStatement.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "execute", "isClosed" -> false;
                    case "executeUpdate" -> 0;
                    case "executeBatch" -> new int[0];
                    default -> null;
                });
        Connection connection = (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(),
                new Class<?>[] {Connection.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "prepareStatement", "createStatement" -> statement;
                    case "isClosed" -> false;
                    default -> null;
                });
        return (DataSource) Proxy.newProxyInstance(DataSource.class.getClassLoader(),
                new Class<?>[] {DataSource.class}, (proxy, method, args) ->
                        method.getName().equals("getConnection") ? connection : null);
    }
}
