package za.co.titandynamix.sasbo_online_backend.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import za.co.titandynamix.sasbo_online_backend.acb.AcbTime;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/**
 * Base class for the ACB module's integration tests: the shared PostgreSQL, reset around every
 * test, with ACB's clock FROZEN.
 *
 * <h2>Why the clock is frozen here</h2>
 * A run may be prepared for this month or next, in Johannesburg. A test that asked the wall clock
 * which month that is would pass every day but the one where the month turns between two of its
 * statements -- a flake with a calendar. So every ACB IT runs at 10:00 on 18 September 2026: this
 * month is 2026-09, next month is 2026-10, and it is always so.
 *
 * <p>One base class so every ACB IT shares one application context: the frozen clock is part of
 * the context's cache key, and two classes declaring it separately would boot two contexts.
 */
@Import(AbstractAcbIT.FrozenClock.class)
public abstract class AbstractAcbIT extends AbstractWritingIT {

    /** 10:00 on 18 September 2026 in Johannesburg. */
    public static final Instant FROZEN = Instant.parse("2026-09-18T08:00:00Z");

    /** The month a run is prepared for in these tests: next month, as the screen does. */
    public static final LocalDate NEXT_MONTH = LocalDate.of(2026, 10, 1);

    @TestConfiguration
    static class FrozenClock {

        @Bean
        @Primary
        Clock frozenAcbClock() {
            return Clock.fixed(FROZEN, AcbTime.ZONE);
        }
    }

    /** Inserts a run straight into the table, in any state -- for tests of what the service does TO one. */
    protected long insertRun(LocalDate month, String status, Instant heartbeat, String createdBy) {
        return this.jdbc.queryForObject(
                "INSERT INTO acb_runs (action_month, status, setup_date, holiday_set, line_count, total_amount, "
                        + "created_by, started_at, heartbeat_at) VALUES (?, ?, ?, 'weekends-only', 0, 0, ?, ?, ?) "
                        + "RETURNING id",
                Long.class, month, status, LocalDate.of(2026, 9, 18), createdBy,
                OffsetDateTime.ofInstant(heartbeat, ZoneOffset.UTC), OffsetDateTime.ofInstant(heartbeat, ZoneOffset.UTC));
    }

    /** Inserts one line straight into the table. */
    protected long insertLine(long runId, int memberId, Long rePresentsLineId, String outcome, String decision,
                              BigDecimal amount) {
        return this.jdbc.queryForObject(
                "INSERT INTO acb_run_lines (run_id, member_id, re_presents_line_id, membership_number, surname, "
                        + "bank_name, branch_code, account_number, debit_day, action_date, amount, outcome, decision) "
                        + "VALUES (?, ?, ?, ?, ?, 'Fixture Bank 00', '250000', ?, 25, ?, ?, ?, ?) RETURNING id",
                Long.class, runId, memberId, rePresentsLineId, "F-" + memberId, "Surname" + memberId,
                String.format("62%08d", memberId), LocalDate.of(2026, 9, 25), amount, outcome, decision);
    }
}
