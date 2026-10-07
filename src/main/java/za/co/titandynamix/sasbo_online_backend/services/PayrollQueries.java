package za.co.titandynamix.sasbo_online_backend.services;

import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import za.co.titandynamix.sasbo_online_backend.dto.payroll.PayrollQueryDto;
import za.co.titandynamix.sasbo_online_backend.entity.payroll.PayrollDiscrepancy;
import za.co.titandynamix.sasbo_online_backend.entity.payroll.PayrollFile;
import za.co.titandynamix.sasbo_online_backend.entity.payroll.PayrollQuery;
import za.co.titandynamix.sasbo_online_backend.enums.PayrollDiscrepancyStatus;
import za.co.titandynamix.sasbo_online_backend.enums.PayrollEventKind;
import za.co.titandynamix.sasbo_online_backend.enums.PayrollFileStatus;
import za.co.titandynamix.sasbo_online_backend.enums.PayrollQueryAction;
import za.co.titandynamix.sasbo_online_backend.exception.PayrollRefusal;
import za.co.titandynamix.sasbo_online_backend.exception.PayrollRefusalCode;
import za.co.titandynamix.sasbo_online_backend.payroll.ProtectedFiles;
import za.co.titandynamix.sasbo_online_backend.repository.PayrollDiscrepancyRepository;
import za.co.titandynamix.sasbo_online_backend.repository.PayrollFileRepository;
import za.co.titandynamix.sasbo_online_backend.repository.PayrollQueryRepository;
import za.co.titandynamix.sasbo_online_backend.repository.PayrollReads;
import za.co.titandynamix.sasbo_online_backend.repository.PayrollStore;

import java.time.Clock;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;

/**
 * The queries sent back to an employer -- "We send them the discrepancy queries - To add or delete." (Stephne,
 * PAY-21; PAY-22). Raising a query takes every open discrepancy of the file that asks the employer for something and
 * is on no query yet, and marks each QUERIED; it stays open until resolved (PAY-20). The list the employer receives is
 * downloaded protected by a password the administrator chooses -- it leaves SASBO (POPIA).
 */
@Service
public class PayrollQueries {

    private final PayrollFileRepository files;
    private final PayrollDiscrepancyRepository discrepancies;
    private final PayrollQueryRepository queries;
    private final PayrollReads reads;
    private final PayrollStore store;
    private final PayrollReports reports;
    private final PayrollAudit audit;
    private final Clock clock;
    private final TransactionTemplate transactions;

    public PayrollQueries(PayrollFileRepository files, PayrollDiscrepancyRepository discrepancies,
                          PayrollQueryRepository queries, PayrollReads reads, PayrollStore store,
                          PayrollReports reports, PayrollAudit audit, Clock clock,
                          PlatformTransactionManager transactionManager) {
        this.files = files;
        this.discrepancies = discrepancies;
        this.queries = queries;
        this.reads = reads;
        this.store = store;
        this.reports = reports;
        this.audit = audit;
        this.clock = clock;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    /** A query raised for a file: every open discrepancy that asks the employer for something, on no query yet. */
    public PayrollQueryDto raise(long fileId, String actor) {
        Long id = transactions.execute(status -> {
            PayrollFile file = files.findById(fileId).orElseThrow(PayrollFiles::notFound);
            if (file.getStatus() == PayrollFileStatus.PURGED) {
                throw PayrollFiles.purged();
            }
            if (file.getStatus() == PayrollFileStatus.SUPERSEDED) {
                throw new PayrollRefusal(PayrollRefusalCode.FILE_SUPERSEDED, "This file was replaced: raise the query "
                        + "from the file that replaced it.", PayrollRefusal.details("supersededBy",
                        file.getSupersededBy() == null ? null : String.valueOf(file.getSupersededBy())));
            }
            List<PayrollDiscrepancy> candidates = discrepancies.findAllByFileIdAndStatusIn(fileId,
                    EnumSet.of(PayrollDiscrepancyStatus.OPEN)).stream()
                    .filter(d -> d.getType().queryAction() != null && d.getQueryId() == null).toList();
            if (candidates.isEmpty()) {
                throw nothing();
            }
            Map<PayrollQueryAction, Integer> counts = new EnumMap<>(PayrollQueryAction.class);
            candidates.forEach(d -> counts.merge(d.getType().queryAction(), 1, Integer::sum));
            PayrollQuery query = new PayrollQuery();
            query.setFileId(fileId);
            query.setPayrollEmployerId(file.getPayrollEmployerId());
            query.setPeriod(file.getPeriod());
            query.setRaisedBy(actor);
            query.setRaisedAt(clock.instant());
            query.setAddCount(counts.getOrDefault(PayrollQueryAction.ADD, 0));
            query.setDeleteCount(counts.getOrDefault(PayrollQueryAction.DELETE, 0));
            query.setCorrectCount(counts.getOrDefault(PayrollQueryAction.CORRECT, 0));
            query = queries.saveAndFlush(query);
            // Claimed with the condition they were read under: a query raised at the same moment takes none twice.
            int claimed = store.putOnQuery(query.getId(), candidates.stream().map(PayrollDiscrepancy::getId).toList());
            if (claimed != candidates.size()) {
                status.setRollbackOnly();
                throw nothing();
            }
            store.recount(fileId);
            audit.record(PayrollEventKind.QUERY_RAISED, fileId, null, "Query " + query.getId() + " raised: add "
                    + query.getAddCount() + ", delete " + query.getDeleteCount() + ", correct " + query.getCorrectCount(),
                    actor);
            return query.getId();
        });
        return get(id);
    }

    private static PayrollRefusal nothing() {
        return new PayrollRefusal(PayrollRefusalCode.NOTHING_TO_QUERY, "No open discrepancy of this file asks the "
                + "employer for anything that is not on a query already.");
    }

    /** A file's queries, newest first. */
    public List<PayrollQueryDto> ofFile(long fileId) {
        if (files.findById(fileId).isEmpty()) {
            throw PayrollFiles.notFound();
        }
        return queries.findAllByFileIdOrderByRaisedAtDescIdDesc(fileId).stream()
                .map(q -> reads.query(q.getId()).orElseThrow()).toList();
    }

    public PayrollQueryDto get(long id) {
        return reads.query(id).orElseThrow(() -> new PayrollRefusal(PayrollRefusalCode.QUERY_NOT_FOUND,
                "No such query."));
    }

    /** The employer's list, protected by the administrator's password. 🔒 The password is used once, here. */
    public PayrollReports.Report download(long id, String format, String password, String actor) {
        PayrollReports.Format chosen = PayrollReports.format(format);
        if (password == null || password.length() < 8 || password.length() > 64) {
            throw new PayrollRefusal(PayrollRefusalCode.PASSWORD_INVALID, "Choose a password of 8 to 64 characters "
                    + "for the employer's list.");
        }
        PayrollQueryDto query = get(id);
        PayrollReports.Report plain = reports.queryList(query, chosen);
        byte[] locked = chosen == PayrollReports.Format.XLSX ? ProtectedFiles.workbook(plain.bytes(), password)
                : ProtectedFiles.pdf(plain.bytes(), password);
        transactions.executeWithoutResult(status -> audit.record(PayrollEventKind.QUERY_DOWNLOADED, query.fileId(),
                null, "Query " + id + " downloaded as " + chosen.extension() + ", protected by a password", actor));
        return new PayrollReports.Report(plain.fileName(), plain.mediaType(), locked);
    }
}
