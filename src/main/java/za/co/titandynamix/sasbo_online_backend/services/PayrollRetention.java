package za.co.titandynamix.sasbo_online_backend.services;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import za.co.titandynamix.sasbo_online_backend.acb.AcbLogging;
import za.co.titandynamix.sasbo_online_backend.enums.PayrollEventKind;
import za.co.titandynamix.sasbo_online_backend.repository.PayrollFileRepository;
import za.co.titandynamix.sasbo_online_backend.repository.PayrollStore;
import za.co.titandynamix.sasbo_online_backend.utils.MonthRange;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Payroll files are kept for one year (Stephne, PAY-39): a year after a file was loaded its bytes and the lines read
 * from it -- every staff number, ID number and name on it -- are deleted. What was found and what was done about it,
 * the discrepancies with their notes and outcomes, stays, each keeping the number of the line it was about.
 *
 * <p>Run every night and once at start-up ({@code PayrollConfig}), a file at a time, each in its own transaction with
 * its entry in the record of what was done -- so one that fails is said, and the rest are still deleted.
 */
@Slf4j
@Service
public class PayrollRetention {

    /** Who deletes, in the record. */
    static final String ACTOR = "system (one year's retention)";

    private static final int BATCH = 50;

    private final PayrollFileRepository files;
    private final PayrollStore store;
    private final PayrollAudit audit;
    private final Clock clock;
    private final TransactionTemplate transactions;

    public PayrollRetention(PayrollFileRepository files, PayrollStore store, PayrollAudit audit, Clock clock,
                            PlatformTransactionManager transactionManager) {
        this.files = files;
        this.store = store;
        this.audit = audit;
        this.clock = clock;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    /** @return how many files were deleted */
    public int purgeDue() {
        LocalDate today = LocalDate.now(clock.withZone(MonthRange.ZONE));
        int purged = 0;
        int failed = 0;
        while (true) {
            List<Long> due = files.idsToPurge(today, PageRequest.of(0, BATCH));
            List<Long> todo = due.stream().skip(failed).toList();
            if (todo.isEmpty()) {
                break;
            }
            for (Long id : todo) {
                try {
                    transactions.executeWithoutResult(status -> {
                        Instant now = clock.instant();
                        int lines = store.purge(id, now);
                        audit.record(PayrollEventKind.FILE_PURGED, id, null, "Deleted a year after it was loaded "
                                + "(PAY-39): the file and its " + lines + " lines; its discrepancies are kept", ACTOR);
                    });
                    purged++;
                } catch (RuntimeException couldNotPurge) {
                    failed++;
                    log.error("Payroll file {} could not be deleted at the end of its year", id,
                            AcbLogging.scrubbed(couldNotPurge));
                }
            }
            if (due.size() < BATCH) {
                break;
            }
        }
        if (purged > 0) {
            log.info("Deleted {} payroll file(s) a year after they were loaded", purged);
        }
        return purged;
    }
}
