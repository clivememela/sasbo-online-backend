package za.co.titandynamix.sasbo_online_backend.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import za.co.titandynamix.sasbo_online_backend.repository.MemberRepository;
import za.co.titandynamix.sasbo_online_backend.repository.MonthEndPackRepository;
import za.co.titandynamix.sasbo_online_backend.support.AbstractSwitchedOnIT;
import za.co.titandynamix.sasbo_online_backend.support.DatabaseReset;
import za.co.titandynamix.sasbo_online_backend.support.SummaryFixture;

import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The statistics pack on PostgreSQL (month end 04.10): prepared and READY with both workbooks; NO QUEUE, held by the
 * partial unique index -- by name, and refusing a second pack in preparation (MR-PERF-11); the table's own rules
 * refusing a READY pack without its workbooks and a FAILED one without its reason; and the lane's reset emptying it.
 */
@DisplayName("The statistics pack, on PostgreSQL")
class MonthEndPackIT extends AbstractSwitchedOnIT {

    @Autowired
    private MemberRepository members;

    @Autowired
    private MonthEndPacks packs;

    @Autowired
    private MonthEndPackRepository repository;

    @BeforeEach
    void thePopulation() {
        SummaryFixture.insert(jdbc, members);
    }

    @Test
    @DisplayName("a pack of the open month on PostgreSQL: READY, both workbooks an .xlsx")
    void aPackOnPostgres() {
        MonthEndPacks.Asked asked = packs.ask("2026-09");

        assertThat(asked.pack().status()).isEqualTo("READY");
        assertThat(packs.file(asked.pack().id(), "statistics").bytes()).startsWith((byte) 'P', (byte) 'K');
        assertThat(packs.file(asked.pack().id(), "monthly").bytes()).startsWith((byte) 'P', (byte) 'K');
    }

    @Test
    @DisplayName("🔒 MR-PERF-11: one pack in preparation at a time -- the partial unique index, by name, refuses a second")
    void oneInHand() {
        String index = jdbc.queryForObject("SELECT indexdef FROM pg_indexes WHERE indexname = 'month_end_packs_one_in_hand'",
                String.class);
        assertThat(index).contains("UNIQUE").contains("WHERE").contains("PREPARING");

        repository.insertPreparing(LocalDate.of(2026, 7, 1), "system", Instant.parse("2026-09-15T08:00:00Z"));
        assertThatThrownBy(() -> repository.insertPreparing(LocalDate.of(2026, 9, 1), "system",
                Instant.parse("2026-09-15T08:00:00Z"))).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("the table's own rules: a READY pack holds both workbooks, and a FAILED one says why")
    void theTablesRules() {
        long id = repository.insertPreparing(LocalDate.of(2026, 9, 1), "system", Instant.parse("2026-09-15T08:00:00Z"));

        assertThatThrownBy(() -> jdbc.update("UPDATE month_end_packs SET status = 'READY', finished_at = now() "
                + "WHERE id = ?", id)).hasMessageContaining("month_end_packs_ready_holds_both");
        assertThatThrownBy(() -> jdbc.update("UPDATE month_end_packs SET status = 'FAILED', finished_at = now() "
                + "WHERE id = ?", id)).hasMessageContaining("month_end_packs_failed_says_why");
    }

    @Test
    @DisplayName("the lane's reset empties the packs")
    void theResetEmptiesThem() {
        packs.ask("2026-09");

        DatabaseReset.reset(jdbc);

        assertThat(jdbc.queryForObject("SELECT count(*) FROM month_end_packs", Long.class)).isZero();
    }
}
