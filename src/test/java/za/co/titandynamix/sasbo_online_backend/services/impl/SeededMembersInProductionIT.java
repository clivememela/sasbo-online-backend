package za.co.titandynamix.sasbo_online_backend.services.impl;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import za.co.titandynamix.sasbo_online_backend.enums.AcbRunStatus;
import za.co.titandynamix.sasbo_online_backend.exception.AcbRefusal;
import za.co.titandynamix.sasbo_online_backend.exception.AcbRefusalCode;
import za.co.titandynamix.sasbo_online_backend.services.AcbFileService;
import za.co.titandynamix.sasbo_online_backend.services.AcbRunService;
import za.co.titandynamix.sasbo_online_backend.support.AbstractAcbIT;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

/**
 * 🔒 Where the production marker is set, a run holding a seeded member is neither approved nor made
 * into a file (SD3-A, R0-9) -- and the match is EXACT: a number that merely falls inside the seed's
 * numeric block is a real member's, and is not refused (SD4-E).
 *
 * <p>A context of its own -- the marker is part of the configuration -- so it is one class, and its
 * demo twin ({@link SeededMembersInADemoIT}) runs on the shared ACB context.
 */
@TestPropertySource(properties = "sasbo.environment=production")
@DisplayName("FILE_HAS_SEED_MEMBERS -- a seeded member is never debited where the marker is set")
class SeededMembersInProductionIT extends AbstractAcbIT {

    /** Listed in debit_order_members.json -- a seeded, synthetic member. */
    static final String SEEDED = "M-099851";

    /**
     * Inside the seed's numeric block (99851..100160), in the shape a migrated seven-digit legacy number
     * might be stored -- but listed by no seed file. A real member: never refused.
     */
    static final String REAL_BUT_INSIDE_THE_BLOCK = "100005";

    @Autowired
    private AcbRunService service;

    @Autowired
    private AcbFileService files;

    @Test
    @DisplayName("approving a run that holds a seeded member is refused, with the count")
    void approveIsRefused() {
        long run = runHolding(SEEDED, "PREPARED");
        AcbRefusal refused = refusal(() -> service.approve(run, 0, "approver", null));
        assertThat(refused.code()).isEqualTo(AcbRefusalCode.FILE_HAS_SEED_MEMBERS);
        assertThat(refused.details()).containsEntry("count", "1");
        assertThat(service.get(run).status()).as("nothing moved").isEqualTo(AcbRunStatus.PREPARED);
    }

    @Test
    @DisplayName("rendering the file of a run that holds a seeded member is refused before a byte is written")
    void renderIsRefused() {
        long run = runHolding(SEEDED, "APPROVED");
        AcbRefusal refused = refusal(() -> files.render(run, "cfo"));
        assertThat(refused.code()).isEqualTo(AcbRefusalCode.FILE_HAS_SEED_MEMBERS);
        assertThat(files.hasRendered(run)).isFalse();
    }

    @Test
    @DisplayName("🔒 the must-pass twin (SD4-E): a real member whose number falls inside the block is approved")
    void anExactMatchNotARange() {
        long run = runHolding(REAL_BUT_INSIDE_THE_BLOCK, "PREPARED");
        assertThat(service.approve(run, 0, "approver", null).status()).isEqualTo(AcbRunStatus.APPROVED);
    }

    /** A run in {@code status} with one line, debiting a member numbered {@code membershipNumber}. */
    private long runHolding(String membershipNumber, String status) {
        int memberId = 7001;
        LocalDateTime created = LocalDateTime.of(2026, 9, 1, 9, 0);
        jdbc.update("INSERT INTO members (id, membership_number, surname, initials, status, payment_type, bank_name, "
                        + "bank_account_number, debit_day, created_at, updated_at) "
                        + "VALUES (?, ?, 'Surname', 'AB', 'Active', 'Debit Order', 'Fixture Bank 00', '9999000001', 25, ?, ?)",
                memberId, membershipNumber, created, created);
        long run = insertRun(NEXT_MONTH, status, FROZEN, "system");
        jdbc.update("INSERT INTO acb_run_lines (run_id, member_id, membership_number, surname, bank_name, branch_code, "
                        + "account_number, debit_day, action_date, amount, outcome) "
                        + "VALUES (?, ?, ?, 'Surname', 'Fixture Bank 00', '250000', '9999000001', 25, ?, 174.00, 'PENDING')",
                run, memberId, membershipNumber, NEXT_MONTH.withDayOfMonth(26));
        jdbc.update("UPDATE acb_runs SET line_count = 1, total_amount = 174.00 WHERE id = ?", run);
        return run;
    }

    private static AcbRefusal refusal(Runnable call) {
        try {
            call.run();
        } catch (AcbRefusal refusal) {
            return refusal;
        }
        return fail("expected an AcbRefusal and the call returned normally");
    }
}
