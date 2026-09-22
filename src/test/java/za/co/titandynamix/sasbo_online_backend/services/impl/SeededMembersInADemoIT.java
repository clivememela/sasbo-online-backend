package za.co.titandynamix.sasbo_online_backend.services.impl;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import za.co.titandynamix.sasbo_online_backend.enums.AcbRunStatus;
import za.co.titandynamix.sasbo_online_backend.services.AcbRunService;
import za.co.titandynamix.sasbo_online_backend.support.AbstractAcbIT;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The must-pass twin of {@link SeededMembersInProductionIT}: in a demo -- no marker -- a run made of
 * seeded members is exactly what the seed is for, and it is approved like any other (R0-9).
 */
@DisplayName("FILE_HAS_SEED_MEMBERS -- a demo's runs are made of seeded members, and that is allowed")
class SeededMembersInADemoIT extends AbstractAcbIT {

    @Autowired
    private AcbRunService service;

    @Test
    @DisplayName("without the marker, a run holding a seeded member is approved")
    void aDemoApprovesSeededMembers() {
        int memberId = 7002;
        LocalDateTime created = LocalDateTime.of(2026, 9, 1, 9, 0);
        jdbc.update("INSERT INTO members (id, membership_number, surname, initials, status, payment_type, bank_name, "
                        + "bank_account_number, debit_day, created_at, updated_at) "
                        + "VALUES (?, ?, 'Surname', 'AB', 'Active', 'Debit Order', 'Fixture Bank 00', '9999000002', 25, ?, ?)",
                memberId, SeededMembersInProductionIT.SEEDED, created, created);
        long run = insertRun(NEXT_MONTH, "PREPARED", FROZEN, "system");
        jdbc.update("INSERT INTO acb_run_lines (run_id, member_id, membership_number, surname, bank_name, branch_code, "
                        + "account_number, debit_day, action_date, amount, outcome) "
                        + "VALUES (?, ?, ?, 'Surname', 'Fixture Bank 00', '250000', '9999000002', 25, ?, 174.00, 'PENDING')",
                run, memberId, SeededMembersInProductionIT.SEEDED, NEXT_MONTH.withDayOfMonth(26));
        jdbc.update("UPDATE acb_runs SET line_count = 1, total_amount = 174.00 WHERE id = ?", run);

        assertThat(service.approve(run, 0, "approver", null).status()).isEqualTo(AcbRunStatus.APPROVED);
    }
}
