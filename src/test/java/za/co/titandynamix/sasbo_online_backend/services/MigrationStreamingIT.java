package za.co.titandynamix.sasbo_online_backend.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import za.co.titandynamix.sasbo_online_backend.config.MigrationProperties;
import za.co.titandynamix.sasbo_online_backend.dto.migration.MigrationBatchDto;
import za.co.titandynamix.sasbo_online_backend.legacy.LegacyFixtures;
import za.co.titandynamix.sasbo_online_backend.legacy.rules.SaIdNumber;
import za.co.titandynamix.sasbo_online_backend.repository.MemberRepository;
import za.co.titandynamix.sasbo_online_backend.support.AbstractSwitchedOnIT;
import za.co.titandynamix.sasbo_online_backend.support.MigrationFixture;
import za.co.titandynamix.sasbo_online_backend.utils.ReferenceListSeeder;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 100,000 invented members, registered and checked on PostgreSQL -- the file GENERATED a line at a time as the reader
 * asks for bytes, never held anywhere whole -- within stated budgets, and never more than one chunk of rows held by the
 * registration at once. SASBO's member file held 549,992 records at 31 August 2026 (legacy/README.md §3.1): this is a
 * fifth of it, in the lane that runs on every pull request.
 *
 * <p>The budgets are ceilings with room, not targets: measured times are printed to the report (the JUnit XML's
 * system-out), and the bound is several times them, so a slow runner does not turn this red -- a quadratic step does.
 */
@DisplayName("Streaming 100,000 rows, on PostgreSQL")
class MigrationStreamingIT extends AbstractSwitchedOnIT {

    private static final int ROWS = 100_000;

    /** Stated budgets for the lane's runner: reading and staging, and the check. */
    private static final Duration REGISTER_BUDGET = Duration.ofSeconds(180);
    private static final Duration CHECK_BUDGET = Duration.ofSeconds(300);

    @Autowired
    private MigrationBatches batches;

    @Autowired
    private MigrationValidation validation;

    @Autowired
    private MigrationProperties properties;

    @Autowired
    private ReferenceListSeeder lists;

    @Autowired
    private MemberRepository members;

    @BeforeEach
    void theLists() {
        MigrationFixture.prepare(jdbc, lists, members);
    }

    /** Invented members, a line at a time, made only when read: every identity number valid and its own. */
    private static final class GeneratedExport extends InputStream {

        private static final LocalDate FIRST_BIRTHDAY = LocalDate.of(1960, 1, 1);

        private byte[] line = header();
        private int position;
        private int next = 1;

        private static byte[] header() {
            return ("MEMBERSHIP NO,TITLE,INITIALS,FIRST NAMES,SURNAME,ID NUMBER,DATE JOINED,STATUS,R/A NO,"
                    + "PAYMENT METHOD,CELL NO,E-MAIL\r\n").getBytes(StandardCharsets.US_ASCII);
        }

        private static String row(int n) {
            LocalDate born = FIRST_BIRTHDAY.plusDays(n / 1000);
            String first12 = String.format("%02d%02d%02d%04d08", born.getYear() % 100, born.getMonthValue(),
                    born.getDayOfMonth(), 1000 + n % 1000);
            String id = first12 + SaIdNumber.checkDigit(first12);
            return (100_000 + n) + ",Ms,I,Invented,Streamed" + (n % 977) + "," + id + ", 1/ 3/2015,A,"
                    + (n % 2 == 0 ? "990001" : "990002") + ",QPAC,082 000 " + String.format("%04d", n % 10_000)
                    + ",streamed" + n + "@example.org\r\n";
        }

        @Override
        public int read() {
            if (position >= line.length && !advance()) {
                return -1;
            }
            return line[position++] & 0xFF;
        }

        @Override
        public int read(byte[] buffer, int offset, int length) {
            if (length == 0) {
                return 0;
            }
            if (position >= line.length && !advance()) {
                return -1;
            }
            int n = Math.min(length, line.length - position);
            System.arraycopy(line, position, buffer, offset, n);
            position += n;
            return n;
        }

        private boolean advance() {
            if (next > ROWS) {
                return false;
            }
            line = row(next++).getBytes(StandardCharsets.US_ASCII);
            position = 0;
            return true;
        }
    }

    @Test
    @DisplayName("registered and checked within budget, one chunk held at a time, every row staged and every outcome counted")
    void streams() {
        long started = System.nanoTime();
        MigrationBatches.Registered registered = batches.register("generated-export.csv", LegacyFixtures.CSV_MAPPING,
                "2026-08-31", null, new GeneratedExport());
        Duration registering = Duration.ofNanos(System.nanoTime() - started);

        started = System.nanoTime();
        MigrationBatchDto checked = validation.validate(registered.batch().id(), null);
        Duration checking = Duration.ofNanos(System.nanoTime() - started);
        System.out.printf("Migration streaming: %d rows registered in %d ms, checked in %d ms; at most %d rows held%n",
                ROWS, registering.toMillis(), checking.toMillis(), registered.mostRowsHeld());

        assertThat(registered.created()).isTrue();
        assertThat(registered.batch().rowsRead()).isEqualTo(ROWS);
        assertThat(registered.mostRowsHeld()).as("rows held at once while reading: one chunk")
                .isLessThanOrEqualTo(properties.chunkSize());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM migration_rows WHERE batch_id = ?", Long.class,
                registered.batch().id())).isEqualTo(ROWS);
        assertThat(checked.status()).isEqualTo("VALIDATED");
        assertThat(checked.rowsValid() + checked.rowsWarned() + checked.rowsInvalid()).isEqualTo(ROWS);
        assertThat(checked.rowsInvalid()).as("every generated member is valid").isZero();
        assertThat(registering).as("registering 100,000 rows").isLessThanOrEqualTo(REGISTER_BUDGET);
        assertThat(checking).as("checking 100,000 rows").isLessThanOrEqualTo(CHECK_BUDGET);
    }
}
