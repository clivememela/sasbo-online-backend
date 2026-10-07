package za.co.titandynamix.sasbo_online_backend.services;

import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import za.co.titandynamix.sasbo_online_backend.dto.payroll.PayrollQueryDto;
import za.co.titandynamix.sasbo_online_backend.dto.payroll.PayrollQueryFileDto;
import za.co.titandynamix.sasbo_online_backend.entity.payroll.PayrollDiscrepancy;
import za.co.titandynamix.sasbo_online_backend.entity.payroll.PayrollFile;
import za.co.titandynamix.sasbo_online_backend.entity.payroll.PayrollQuery;
import za.co.titandynamix.sasbo_online_backend.entity.payroll.PayrollQueryFile;
import za.co.titandynamix.sasbo_online_backend.enums.PayrollDiscrepancyStatus;
import za.co.titandynamix.sasbo_online_backend.enums.PayrollEventKind;
import za.co.titandynamix.sasbo_online_backend.enums.PayrollFileProtection;
import za.co.titandynamix.sasbo_online_backend.enums.PayrollFileStatus;
import za.co.titandynamix.sasbo_online_backend.enums.PayrollQueryAction;
import za.co.titandynamix.sasbo_online_backend.exception.PayrollRefusal;
import za.co.titandynamix.sasbo_online_backend.exception.PayrollRefusalCode;
import za.co.titandynamix.sasbo_online_backend.payroll.ProtectedFiles;
import za.co.titandynamix.sasbo_online_backend.repository.PayrollDiscrepancyRepository;
import za.co.titandynamix.sasbo_online_backend.repository.PayrollFileRepository;
import za.co.titandynamix.sasbo_online_backend.repository.PayrollQueryFileRepository;
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
 * is on no query yet, and marks each QUERIED; it stays open until resolved (PAY-20). The list the employer receives
 * leaves SASBO as a protected file (POPIA) under the one rule for every file SASBO sends out: a password the SYSTEM
 * generates for that file, shown once to whoever produced it, kept nowhere ({@link ProtectedFiles}).
 */
@Service
public class PayrollQueries {

    private final PayrollFileRepository files;
    private final PayrollDiscrepancyRepository discrepancies;
    private final PayrollQueryRepository queries;
    private final PayrollQueryFileRepository queryFiles;
    private final PayrollReads reads;
    private final PayrollStore store;
    private final PayrollReports reports;
    private final PayrollAudit audit;
    private final Clock clock;
    private final TransactionTemplate transactions;

    public PayrollQueries(PayrollFileRepository files, PayrollDiscrepancyRepository discrepancies,
                          PayrollQueryRepository queries, PayrollQueryFileRepository queryFiles,
                          PayrollReads reads, PayrollStore store,
                          PayrollReports reports, PayrollAudit audit, Clock clock,
                          PlatformTransactionManager transactionManager) {
        this.files = files;
        this.discrepancies = discrepancies;
        this.queries = queries;
        this.queryFiles = queryFiles;
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

    /** What producing a protected list answers: the file, and its password -- shown once, kept nowhere. */
    public record Produced(PayrollQueryFileDto file, String password) {

        @Override
        public String toString() {
            return "Produced[file=" + (file == null ? null : file.id()) + ", password withheld]";
        }
    }

    /**
     * The employer's list as a protected file, under a password generated for this file alone: the ENCRYPTED file kept,
     * to be downloaded and sent; the password returned here once and kept nowhere -- not in the database, not in a log,
     * not in the record of what was done. A lost password is a file produced again.
     */
    public Produced produce(long queryId, String format, String actor) {
        PayrollReports.Format chosen = PayrollReports.format(format);
        PayrollQueryDto query = get(queryId);
        PayrollFile file = files.findById(query.fileId()).orElseThrow(PayrollFiles::notFound);
        if (file.getStatus() == PayrollFileStatus.PURGED) {
            throw PayrollFiles.purged();
        }
        PayrollReports.Report plain = reports.queryList(query, chosen);
        String password = ProtectedFiles.newPassword();
        byte[] locked = chosen == PayrollReports.Format.XLSX ? ProtectedFiles.workbook(plain.bytes(), password)
                : ProtectedFiles.pdf(plain.bytes(), password);
        Long id = transactions.execute(status -> {
            PayrollQueryFile produced = new PayrollQueryFile();
            produced.setQueryId(queryId);
            produced.setFileId(query.fileId());
            produced.setFormat(chosen.name());
            produced.setProtection(chosen == PayrollReports.Format.XLSX ? PayrollFileProtection.EXCEL_AES_256
                    : PayrollFileProtection.PDF_AES_256);
            produced.setFileName(plain.fileName());
            produced.setContent(locked);
            produced.setSizeBytes(locked.length);
            produced.setSha256(PayrollFiles.sha256(locked));
            produced.setProducedAt(clock.instant());
            produced.setProducedBy(actor);
            produced.setDownloads(0);
            produced = queryFiles.saveAndFlush(produced);
            audit.record(PayrollEventKind.QUERY_FILE_PRODUCED, query.fileId(), null, "Query " + queryId
                    + "'s list produced as " + chosen.extension() + " (file " + produced.getId()
                    + "), protected by a password shown once", actor);
            return produced.getId();
        });
        return new Produced(reads.queryFile(id).orElseThrow(), password);
    }

    /** A produced list's encrypted bytes, as produced -- each download counted and recorded. */
    public PayrollReports.Report fileContent(long queryFileId, String actor) {
        return transactions.execute(status -> {
            PayrollQueryFile produced = queryFiles.findById(queryFileId).orElseThrow(() -> new PayrollRefusal(
                    PayrollRefusalCode.QUERY_FILE_NOT_FOUND, "No such query list file."));
            if (produced.getContent() == null) {
                throw PayrollFiles.purged();
            }
            store.countDownload(queryFileId, clock.instant());
            audit.record(PayrollEventKind.QUERY_FILE_DOWNLOADED, produced.getFileId(), null, "Query list file "
                    + queryFileId + " downloaded", actor);
            PayrollReports.Format format = PayrollReports.Format.valueOf(produced.getFormat());
            return new PayrollReports.Report(produced.getFileName(), format.mediaType(), produced.getContent());
        });
    }
}
