package za.co.titandynamix.sasbo_online_backend.services.impl;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import za.co.titandynamix.sasbo_online_backend.enums.AcbRunlessEvent;
import za.co.titandynamix.sasbo_online_backend.support.AbstractAcbIT;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 🔒 A write that belongs to no run is attributed, and ONLY those may omit the run (SD4-A, R1-24).
 *
 * <p>V43's {@code acb_audit_events_runless_kinds} CHECK is the rule; {@link AcbRunlessEvent} is its twin
 * in Java. The CHECK is read back from PostgreSQL itself and compared with the enum -- the literals it
 * quotes, since PostgreSQL rewrites {@code IN (...)} as {@code = ANY (ARRAY[...])} (F60).
 */
@DisplayName("acb_audit_events -- only a run-less kind may omit its run (SD4-A)")
class AcbAuditTrailIT extends AbstractAcbIT {

    @Autowired
    private AcbAuditTrail audit;

    @Autowired
    private PlatformTransactionManager transactions;

    @Test
    @DisplayName("every run-less kind is written with no run, and says who wrote it")
    void everyRunlessKindIsAccepted() {
        TransactionTemplate tx = new TransactionTemplate(transactions);
        for (AcbRunlessEvent kind : AcbRunlessEvent.values()) {
            tx.executeWithoutResult(status -> audit.recordRunless(kind, "what changed", "someone"));
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM acb_audit_events WHERE run_id IS NULL AND actor = 'someone'",
                Long.class)).isEqualTo((long) AcbRunlessEvent.values().length);
    }

    @Test
    @DisplayName("an ordinary transition without its run is refused by the table's own CHECK")
    void aTransitionStillNeedsItsRun() {
        assertThatThrownBy(() -> jdbc.update("INSERT INTO acb_audit_events (run_id, occurred_at, actor, kind) "
                + "VALUES (NULL, now(), 'someone', 'APPROVE')"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("a change that rolls back takes its audit row with it -- the row is written in the change's transaction")
    void theRowRollsBackWithItsChange() {
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            audit.recordRunless(AcbRunlessEvent.SEED_COMPLETE, "rolled back", "someone");
            status.setRollbackOnly();
        });
        assertThat(jdbc.queryForObject("SELECT count(*) FROM acb_audit_events", Long.class)).isZero();
    }

    @Test
    @DisplayName("the CHECK names exactly the enum's kinds, read from PostgreSQL's own definition")
    void theCheckIsTheEnum() {
        String definition = jdbc.queryForObject("SELECT pg_get_constraintdef(oid) FROM pg_constraint "
                + "WHERE conname = 'acb_audit_events_runless_kinds'", String.class);
        assertThat(definition).as("the constraint exists").isNotBlank();
        List<String> quoted = quotedLiterals(definition);
        assertThat(quoted).containsExactlyInAnyOrderElementsOf(
                Arrays.stream(AcbRunlessEvent.values()).map(Enum::name).toList());
        // Canary: the reader finds literals at all, and would see one too many.
        assertThat(quotedLiterals("(kind = ANY (ARRAY['A'::text, 'B'::text]))")).containsExactly("A", "B");
    }

    private static List<String> quotedLiterals(String sql) {
        Matcher m = Pattern.compile("'([^']*)'").matcher(sql);
        List<String> found = new java.util.ArrayList<>();
        while (m.find()) {
            found.add(m.group(1));
        }
        return found;
    }
}
