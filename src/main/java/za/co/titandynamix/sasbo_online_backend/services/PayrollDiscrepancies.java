package za.co.titandynamix.sasbo_online_backend.services;

import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import za.co.titandynamix.sasbo_online_backend.dto.payroll.PayrollDiscrepancyDto;
import za.co.titandynamix.sasbo_online_backend.dto.payroll.PayrollPage;
import za.co.titandynamix.sasbo_online_backend.entity.payroll.PayrollDiscrepancy;
import za.co.titandynamix.sasbo_online_backend.entity.payroll.PayrollDiscrepancyNote;
import za.co.titandynamix.sasbo_online_backend.enums.PayrollDiscrepancyStatus;
import za.co.titandynamix.sasbo_online_backend.enums.PayrollDiscrepancyType;
import za.co.titandynamix.sasbo_online_backend.enums.PayrollEventKind;
import za.co.titandynamix.sasbo_online_backend.enums.PayrollOutcome;
import za.co.titandynamix.sasbo_online_backend.exception.PayrollRefusal;
import za.co.titandynamix.sasbo_online_backend.exception.PayrollRefusalCode;
import za.co.titandynamix.sasbo_online_backend.payroll.PayrollLimits;
import za.co.titandynamix.sasbo_online_backend.payroll.PayrollRequests;
import za.co.titandynamix.sasbo_online_backend.repository.PayrollDiscrepancyNoteRepository;
import za.co.titandynamix.sasbo_online_backend.repository.PayrollDiscrepancyRepository;
import za.co.titandynamix.sasbo_online_backend.repository.PayrollReads;
import za.co.titandynamix.sasbo_online_backend.repository.PayrollStore;

import java.time.Clock;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Payroll discrepancies: the list across every file -- the one place (PAY-32) -- one discrepancy with its notes, a note
 * added, and a discrepancy resolved with an outcome that fits its kind and a note of what was done. Every discrepancy
 * stays open, with its notes, until it is resolved (Stephne, PAY-20). Two administrators cannot both resolve one: the
 * screen sends the version it read.
 *
 * <p>🔒 A note's words are a person's: never logged, never in the record of what was done (which counts them).
 */
@Service
public class PayrollDiscrepancies {

    private static final Set<PayrollDiscrepancyStatus> UNRESOLVED =
            EnumSet.of(PayrollDiscrepancyStatus.OPEN, PayrollDiscrepancyStatus.QUERIED);

    private final PayrollDiscrepancyRepository discrepancies;
    private final PayrollDiscrepancyNoteRepository notes;
    private final PayrollReads reads;
    private final PayrollStore store;
    private final PayrollAudit audit;
    private final Clock clock;
    private final TransactionTemplate transactions;

    public PayrollDiscrepancies(PayrollDiscrepancyRepository discrepancies, PayrollDiscrepancyNoteRepository notes,
                                PayrollReads reads, PayrollStore store, PayrollAudit audit, Clock clock,
                                PlatformTransactionManager transactionManager) {
        this.discrepancies = discrepancies;
        this.notes = notes;
        this.reads = reads;
        this.store = store;
        this.audit = audit;
        this.clock = clock;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    /**
     * The discrepancies, filtered. {@code status} OPEN, QUERIED, RESOLVED, or ALL; by default the ones still to be
     * resolved, so the worklist opens on what needs doing.
     */
    public PayrollPage<PayrollDiscrepancyDto> list(Long employerId, String period, Long fileId, String type,
                                                   String status, Integer page, Integer size) {
        int[] paging = PayrollRequests.page(page, size);
        PayrollDiscrepancyType wantedType = PayrollRequests.word(type, PayrollDiscrepancyType.class, "type");
        Set<PayrollDiscrepancyStatus> statuses;
        if (status == null || status.isBlank()) {
            statuses = UNRESOLVED;
        } else if ("ALL".equalsIgnoreCase(status.trim())) {
            statuses = null;
        } else {
            statuses = EnumSet.of(PayrollRequests.word(status, PayrollDiscrepancyStatus.class, "status"));
        }
        return reads.discrepancies(new PayrollReads.Filter(employerId, PayrollRequests.periodFilter(period, clock),
                fileId, wantedType, statuses), paging[0], paging[1]);
    }

    /** A file's discrepancies, filtered: every status unless one is asked for. */
    public PayrollPage<PayrollDiscrepancyDto> ofFile(long fileId, String type, String status, Integer page,
                                                     Integer size) {
        return list(null, null, fileId, type, status == null || status.isBlank() ? "ALL" : status, page, size);
    }

    public PayrollDiscrepancyDto get(long id) {
        return reads.discrepancy(id).orElseThrow(PayrollDiscrepancies::notFound);
    }

    /** A note added to a discrepancy, whatever its status. */
    public PayrollDiscrepancyDto addNote(long id, String text, String actor) {
        String note = checkedNote(text);
        transactions.executeWithoutResult(status -> {
            PayrollDiscrepancy discrepancy = discrepancies.findById(id).orElseThrow(PayrollDiscrepancies::notFound);
            write(discrepancy.getId(), note, actor, clock.instant());
            audit.record(PayrollEventKind.NOTE_ADDED, discrepancy.getFileId(), discrepancy.getId(), "Note added to a "
                    + discrepancy.getType() + " discrepancy (" + note.length() + " characters)", actor);
        });
        return get(id);
    }

    /**
     * Resolved with an outcome that fits its kind and a note of what was done. Recording an outcome changes no
     * member's record -- a status or a payment type is changed on the record itself (MEMBERS_EDIT).
     */
    public PayrollDiscrepancyDto resolve(long id, String outcomeName, String text, Integer version, String actor) {
        String note = checkedNote(text);
        PayrollOutcome outcome;
        try {
            outcome = PayrollOutcome.valueOf(outcomeName.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException | NullPointerException unknown) {
            outcome = null;
        }
        PayrollOutcome chosen = outcome;
        try {
            transactions.executeWithoutResult(status -> {
                PayrollDiscrepancy discrepancy = discrepancies.findById(id)
                        .orElseThrow(PayrollDiscrepancies::notFound);
                if (discrepancy.getStatus() == PayrollDiscrepancyStatus.RESOLVED) {
                    throw new PayrollRefusal(PayrollRefusalCode.DISCREPANCY_ALREADY_RESOLVED, "This discrepancy was "
                            + "resolved already.", PayrollRefusal.details("outcome", discrepancy.getOutcome().name(),
                            "resolvedBy", discrepancy.getResolvedBy(), "resolvedAt",
                            discrepancy.getResolvedAt() == null ? null : discrepancy.getResolvedAt().toString()));
                }
                if (version == null || !version.equals(discrepancy.getVersion())) {
                    throw new PayrollRefusal(PayrollRefusalCode.DISCREPANCY_CHANGED_SINCE, "This discrepancy changed "
                            + "after you opened it. Reload it to see what changed.", Map.of("version",
                            String.valueOf(discrepancy.getVersion())));
                }
                List<PayrollOutcome> allowed = discrepancy.getType().outcomes();
                if (chosen == null || !allowed.contains(chosen)) {
                    throw new PayrollRefusal(PayrollRefusalCode.OUTCOME_NOT_FOR_TYPE, "That outcome does not fit this "
                            + "kind of discrepancy.", Map.of("allowed", String.join(", ",
                            allowed.stream().map(Enum::name).toList())));
                }
                Instant now = clock.instant();
                discrepancy.setStatus(PayrollDiscrepancyStatus.RESOLVED);
                discrepancy.setOutcome(chosen);
                discrepancy.setResolvedBy(actor);
                discrepancy.setResolvedAt(now);
                discrepancies.saveAndFlush(discrepancy);
                write(discrepancy.getId(), note, actor, now);
                store.recount(discrepancy.getFileId());
                audit.record(PayrollEventKind.DISCREPANCY_RESOLVED, discrepancy.getFileId(), discrepancy.getId(),
                        discrepancy.getType() + " resolved " + chosen, actor);
            });
        } catch (ObjectOptimisticLockingFailureException raced) {
            throw new PayrollRefusal(PayrollRefusalCode.DISCREPANCY_CHANGED_SINCE, "Someone else changed this "
                    + "discrepancy at the same moment. Reload it to see what changed.");
        }
        return get(id);
    }

    private void write(long discrepancyId, String note, String actor, Instant at) {
        PayrollDiscrepancyNote row = new PayrollDiscrepancyNote();
        row.setDiscrepancyId(discrepancyId);
        row.setNote(note);
        row.setWrittenBy(actor);
        row.setWrittenAt(at);
        notes.save(row);
    }

    private static String checkedNote(String text) {
        String note = text == null ? "" : text.strip();
        if (note.isEmpty() || note.length() > PayrollLimits.NOTE_MAX_LENGTH) {
            throw new PayrollRefusal(PayrollRefusalCode.NOTE_INVALID, "Write a note of up to 1,000 characters saying "
                    + "what was done.", Map.of("field", "note"));
        }
        return note;
    }

    static PayrollRefusal notFound() {
        return new PayrollRefusal(PayrollRefusalCode.DISCREPANCY_NOT_FOUND, "No such discrepancy.");
    }
}
