package za.co.titandynamix.sasbo_online_backend.utils;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * One call of month end's block in {@code DataSeeder.run}, caught (MD65, MV-58). The whole start with a seeder made
 * to throw is {@link MonthEndSeedFailureTest}; this is the catch itself, so that deleting it reddens a test on an
 * assertion (DL-70) -- deleted, the whole start's context would not load, which proves nothing a test can call red.
 */
@DisplayName("Month end's block in DataSeeder -- each call caught")
class DataSeederBlockTest {

    @Test
    @DisplayName("🔒 MV-58, DL-70: a seeder of the block that throws is caught, and the start goes on to the next")
    void aSeederThatThrowsIsCaught() {
        assertThatCode(() -> DataSeeder.seedMonthEnd("a planted seeder", () -> {
            throw new IllegalStateException("planted: a seeder of the block throws");
        })).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("must-pass: a seeder of the block that does not throw is run, once")
    void aSeederThatSucceedsRunsOnce() {
        AtomicInteger runs = new AtomicInteger();

        DataSeeder.seedMonthEnd("a seeder", runs::incrementAndGet);

        assertThat(runs).hasValue(1);
    }
}
