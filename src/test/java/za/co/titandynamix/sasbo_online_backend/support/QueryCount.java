package za.co.titandynamix.sasbo_online_backend.support;

import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;

import jakarta.persistence.EntityManagerFactory;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Counts the SQL statements a piece of work actually issues.
 *
 * <h2>The defect it exists to catch</h2>
 * N+1. A list endpoint fetches ten rows and then, lazily, one more query per row
 * to fill a relation -- eleven statements where one would do. Nothing else in a
 * suite notices: the response is correct, the test is green, and the cost is
 * invisible until the table is large and the page stops loading.
 *
 * <p>It is invisible to a mocked service test by construction, and invisible to
 * a repository test that only asserts the rows, which is why the count has to
 * be asserted deliberately.
 *
 * <h2>Asserted at two sizes, and that is the whole point</h2>
 * A single measurement tells you nothing: 11 queries for 10 rows and 11 queries
 * for 50 rows are different bugs, and only one of them is N+1. <b>The count is
 * asserted CONSTANT across two seed sizes</b> -- if it grows with the data, the
 * query grows with the data.
 *
 * <p>The measured number is recorded in the assertion so a later regression has
 * something concrete to fail against, rather than a fresh guess.
 */
public final class QueryCount {

    private QueryCount() {
    }

    /** Hibernate's statistics for the given factory, enabled and cleared. */
    private static Statistics statistics(EntityManagerFactory factory) {
        Statistics statistics = factory.unwrap(SessionFactory.class).getStatistics();
        statistics.setStatisticsEnabled(true);
        statistics.clear();
        return statistics;
    }

    /** Runs {@code work} and answers how many SQL statements it issued. */
    public static long during(EntityManagerFactory factory, Runnable work) {
        Statistics statistics = statistics(factory);
        work.run();
        return statistics.getPrepareStatementCount();
    }

    /**
     * Asserts that the same work costs the same number of statements at two
     * different data sizes.
     *
     * @param seedTo    seeds the database to the given number of rows
     * @param work      the query under test
     * @param small     a small row count
     * @param large     a larger one -- large enough that N+1 is unmistakable
     */
    public static void assertConstantAcrossSizes(
            EntityManagerFactory factory,
            java.util.function.IntConsumer seedTo,
            Runnable work,
            int small,
            int large) {

        seedTo.accept(small);
        long atSmall = during(factory, work);

        seedTo.accept(large);
        long atLarge = during(factory, work);

        assertThat(atLarge)
                .as("SQL statements for the same query at %d rows and at %d rows. A count that "
                        + "grows with the data is N+1: the query fetches the rows and then issues "
                        + "one more statement per row to fill a relation. Nothing else in this "
                        + "suite would notice -- the response is correct and the test is green "
                        + "until the table is large. Measured %d at %d rows",
                        small, large, atSmall, small)
                .isEqualTo(atSmall);
    }
}
