package za.co.titandynamix.sasbo_online_backend.services;

import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import za.co.titandynamix.sasbo_online_backend.acb.AcbActor;
import za.co.titandynamix.sasbo_online_backend.acb.AcbLogging;
import za.co.titandynamix.sasbo_online_backend.acb.AcbTime;
import za.co.titandynamix.sasbo_online_backend.config.MigrationProperties;
import za.co.titandynamix.sasbo_online_backend.dto.migration.MigrationBatchDto;
import za.co.titandynamix.sasbo_online_backend.exception.MigrationRefusal;
import za.co.titandynamix.sasbo_online_backend.exception.MigrationRefusalCode;
import za.co.titandynamix.sasbo_online_backend.legacy.StagedJson;
import za.co.titandynamix.sasbo_online_backend.legacy.mapping.MappingDefinition;
import za.co.titandynamix.sasbo_online_backend.legacy.mapping.MappingParser;
import za.co.titandynamix.sasbo_online_backend.legacy.mapping.MemberField;
import za.co.titandynamix.sasbo_online_backend.repository.MigrationBatchRepository;
import za.co.titandynamix.sasbo_online_backend.repository.MigrationBatchRepository.Batch;
import za.co.titandynamix.sasbo_online_backend.repository.MigrationCommitRepository;
import za.co.titandynamix.sasbo_online_backend.repository.MigrationRowRepository;
import za.co.titandynamix.sasbo_online_backend.utils.MemberStatuses;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The move and its undo.
 *
 * <h2>The move: a transaction a chunk, and a cursor</h2>
 * The rows that passed the check -- VALID, and WARNING, which move and are listed -- are written a chunk at a time,
 * each chunk in its own transaction: its members, the status change a deleted or suspended member is given, and the
 * link from each member to its row. The batch's cursor moves with each chunk, in the same transaction, so a move that is
 * interrupted -- a redeploy, a failure -- RESUMES from the cursor when asked again, and no row is written twice. Before
 * the first chunk the rows are checked once more against the members on record (a member captured since the check
 * holds its row back), and the capture sequence is moved past every number the move writes.
 *
 * <h2>The undo: exactly the members the move created, and only while nobody has touched them</h2>
 * A member edited, given another status change, or taken into a debit order run since the move is reason to undo
 * nothing at all: the undo is refused, ROLLBACK_MEMBERS_CHANGED. Otherwise each chunk's members are locked, asked again,
 * and deleted with their status changes and links -- in one transaction a chunk. The staged rows stay: the batch is
 * ROLLED_BACK, and its file may be registered again.
 */
@Slf4j
@Service
public class MigrationCommits {

    static final String COMMIT_FAILED = "The move stopped before it finished. The members moved so far stay, and the "
            + "move resumes from where it stopped when it is asked for again.";
    static final String ROLLBACK_FAILED = "The undo stopped before it finished. Ask for it again: it resumes where it "
            + "stopped.";
    static final String CHANGED_DURING = "A member of this batch was changed while the undo ran. The members not yet "
            + "undone stay; nothing more is undone while any of them has been changed.";

    /** Watches a move chunk by chunk -- for the test that interrupts one. Production watches nothing. */
    @FunctionalInterface
    interface ChunkObserver {
        void committed(int chunk);
    }

    private final MigrationBatchRepository batches;
    private final MigrationRowRepository rows;
    private final MigrationCommitRepository writes;
    private final MigrationBatches batchService;
    private final MigrationJobLauncher launcher;
    private final MigrationProperties properties;
    private final Clock clock;
    private final TransactionTemplate transactions;
    private final MappingParser parser = new MappingParser();

    public MigrationCommits(MigrationBatchRepository batches, MigrationRowRepository rows,
                            MigrationCommitRepository writes, MigrationBatches batchService,
                            MigrationJobLauncher launcher, MigrationProperties properties, Clock clock,
                            PlatformTransactionManager transactionManager) {
        this.batches = batches;
        this.rows = rows;
        this.writes = writes;
        this.batchService = batchService;
        this.launcher = launcher;
        this.properties = properties;
        this.clock = clock;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    // ------------------------------------------------------------------------------------------------ the move

    /**
     * Starts a move. A batch with errors moves only when asked to hold them back: an informed choice, never a default.
     */
    public MigrationBatchDto commit(long id, boolean holdBackErrors) {
        String actor = beginCommit(id, holdBackErrors);
        launcher.launch(() -> runCommit(id, actor, properties.chunkSize(), chunk -> { }));
        return batchService.find(id);
    }

    /** Every refusal a move can meet, then its job started. The actor, read on the request's thread. */
    String beginCommit(long id, boolean holdBackErrors) {
        Batch batch = batchService.require(id);
        if (!MigrationBatchRepository.VALIDATED.equals(batch.status())
                && !MigrationBatchRepository.COMMITTING.equals(batch.status())) {
            throw MigrationValidation.notInState(batch, "moved", "VALIDATED, COMMITTING");
        }
        if (batch.rowsInvalid() > 0 && !holdBackErrors) {
            throw new MigrationRefusal(MigrationRefusalCode.COMMIT_HAS_ERRORS, batch.rowsInvalid() + " row(s) have "
                    + "errors. Fix them in the file and import it again, or move the others and hold these back.",
                    MigrationRefusal.details("rowsInvalid", String.valueOf(batch.rowsInvalid())));
        }
        start(batch, MigrationBatchRepository.COMMIT, List.of(MigrationBatchRepository.VALIDATED,
                MigrationBatchRepository.COMMITTING), batch.rowsValid() + batch.rowsWarned());
        return AcbActor.current();
    }

    /** The move's job. Never throws: a failure is written on the batch, which stays COMMITTING with its cursor. */
    void runCommit(long id, String actor, int chunkSize, ChunkObserver observer) {
        try {
            move(id, actor, chunkSize, observer);
        } catch (RuntimeException failed) {
            log.error("Migration batch {}: the move failed (SQL state {})", id, AcbLogging.sqlState(failed),
                    AcbLogging.scrubbed(failed));
            batches.failJob(id, COMMIT_FAILED, clock.instant());
        }
    }

    private void move(long id, String actor, int chunkSize, ChunkObserver observer) {
        Batch batch = batchService.require(id);
        MappingDefinition mapping = parser.parse(batch.mappingSnapshot());
        // Members captured since the check hold their rows back, before anything is written.
        rows.markAcrossRows(id, true);
        Map<String, Integer> outcomes = rows.countByOutcome(id);
        batches.recount(id, outcomes.getOrDefault("VALID", 0), outcomes.getOrDefault("WARNING", 0),
                outcomes.getOrDefault("ERROR", 0));
        long highest = rows.maxMembershipSeq(id);
        if (highest > 0) {
            writes.moveMembershipSequencePast(highest);
        }
        if (!batches.committing(id)) {
            throw new IllegalStateException("the batch could not begin its move");
        }
        long cursor = batch.commitCursor();
        int done = batch.rowsCommitted();
        int chunk = 0;
        while (true) {
            List<MigrationRowRepository.Movable> page = rows.movablePage(id, cursor, chunkSize);
            if (page.isEmpty()) {
                break;
            }
            long last = page.get(page.size() - 1).id();
            int moved = done + page.size();
            transactions.executeWithoutResult(status -> {
                writeChunk(id, page, mapping, actor);
                if (!batches.advanced(id, last, page.size(), moved, clock.instant())) {
                    throw new IllegalStateException("the move was swept while it ran");
                }
            });
            cursor = last;
            done = moved;
            observer.committed(++chunk);
        }
        if (!batches.committed(id, actor, clock.instant())) {
            throw new IllegalStateException("the move was swept while it ran");
        }
        log.info("Migration batch {} moved: {} members", id, done);
    }

    /** One chunk: the members, then the status changes of those who left or were suspended, then the links. */
    private void writeChunk(long id, List<MigrationRowRepository.Movable> page, MappingDefinition mapping, String actor) {
        List<MigrationCommitRepository.MemberInsert> members = new ArrayList<>(page.size());
        List<StagedJson.Normalised> normalised = new ArrayList<>(page.size());
        List<String> numbers = new ArrayList<>(page.size());
        for (MigrationRowRepository.Movable row : page) {
            StagedJson.Normalised member = StagedJson.normalised(row.normalisedJson());
            String number = row.membershipKey();
            if (number == null) {
                if (!mapping.membershipNumber().allocate()) {
                    throw new IllegalStateException("a row checked to move has no membership number");
                }
                long next = writes.nextMembershipNumber();
                number = mapping.membershipNumber().prefix() + next;
                rows.allocated(row.id(), number, next);
            }
            numbers.add(number);
            normalised.add(member);
            members.add(insert(member, number));
        }
        writes.insertMembers(members, LocalDateTime.ofInstant(clock.instant(), ZoneId.systemDefault()));
        Map<String, List<Integer>> ids = writes.memberIdsByNumber(numbers);
        List<Integer> memberIds = new ArrayList<>(page.size());
        for (String number : numbers) {
            List<Integer> found = ids.get(number);
            if (found == null || found.size() != 1) {
                // A number on two members: one was captured with it while this chunk was written. The transaction
                // rolls back, the cursor stays, and the next move's re-check holds the row back.
                throw new IllegalStateException("a membership number did not come back once");
            }
            memberIds.add(found.get(0));
        }
        List<MigrationCommitRepository.StatusInsert> changes = new ArrayList<>();
        for (int i = 0; i < page.size(); i++) {
            StagedJson.Normalised member = normalised.get(i);
            if ("L".equals(member.statusKind()) || "S".equals(member.statusKind())) {
                boolean leaving = "L".equals(member.statusKind());
                changes.add(new MigrationCommitRepository.StatusInsert(memberIds.get(i), member.memberStatus(),
                        leaving ? "DELETION_CODE" : "SUSPENSION_CODE", leaving ? member.deletionCodeId() : null,
                        leaving ? null : member.suspensionCodeId(),
                        "Moved from the old system: batch " + id + ", line " + page.get(i).rowNumber(), actor,
                        startOf(member.statusChangedOn())));
            }
        }
        writes.insertStatusChanges(changes);
        Map<Integer, Long> changeIds = writes.statusChangeIds(memberIds);
        List<MigrationCommitRepository.LinkInsert> links = new ArrayList<>(page.size());
        for (int i = 0; i < page.size(); i++) {
            links.add(new MigrationCommitRepository.LinkInsert(memberIds.get(i), id, page.get(i).id(),
                    changeIds.get(memberIds.get(i))));
        }
        writes.insertLinks(links, clock.instant());
    }

    private static MigrationCommitRepository.MemberInsert insert(StagedJson.Normalised member, String number) {
        Map<MemberField, String> f = member.fields();
        String personal = f.get(MemberField.PERSONAL_EMAIL);
        String work = f.get(MemberField.WORK_EMAIL);
        return new MigrationCommitRepository.MemberInsert(number, f.get(MemberField.TITLE), f.get(MemberField.INITIALS),
                f.get(MemberField.FIRST_NAMES), f.get(MemberField.SURNAME), member.identityType(),
                f.get(MemberField.IDENTITY_NUMBER), f.get(MemberField.PASSPORT_NUMBER), date(f.get(MemberField.DATE_OF_BIRTH)),
                f.get(MemberField.GENDER), f.get(MemberField.RACE),
                member.memberStatus() == null ? MemberStatuses.ACTIVE : member.memberStatus(),
                date(f.get(MemberField.JOIN_DATE)), personal, work,
                personal != null ? "personal" : work != null ? "work" : null, f.get(MemberField.CELL_TEL),
                f.get(MemberField.WORK_TEL), f.get(MemberField.HOME_TEL), f.get(MemberField.ADDRESS),
                f.get(MemberField.BANK_NAME), f.get(MemberField.BANK_ACCOUNT_NUMBER), f.get(MemberField.ACCOUNT_TYPE),
                f.get(MemberField.PAYMENT_TYPE),
                f.get(MemberField.DEBIT_DAY) == null ? null : Integer.valueOf(f.get(MemberField.DEBIT_DAY)),
                f.get(MemberField.MONTHLY_SUBSCRIPTION) == null ? null : new BigDecimal(f.get(MemberField.MONTHLY_SUBSCRIPTION)),
                f.get(MemberField.EMPLOYEE_NUMBER), member.workplaceId(), f.get(MemberField.REMARKS));
    }

    private static LocalDate date(String iso) {
        return iso == null ? null : LocalDate.parse(iso);
    }

    /** A status change on a day: the start of that day in SASBO's zone, as a person reads the date. */
    private static OffsetDateTime startOf(LocalDate day) {
        return day.atStartOfDay(AcbTime.ZONE).toOffsetDateTime();
    }

    // ------------------------------------------------------------------------------------------------ the undo

    /**
     * Starts an undo -- refused at once, with nothing done, where any member the batch moved has been changed or used.
     */
    public MigrationBatchDto rollback(long id, String reason) {
        Batch batch = batchService.require(id);
        if (!List.of(MigrationBatchRepository.COMMITTED, MigrationBatchRepository.COMMITTING,
                MigrationBatchRepository.ROLLING_BACK).contains(batch.status())) {
            throw MigrationValidation.notInState(batch, "undone", "COMMITTED, COMMITTING, ROLLING_BACK");
        }
        long changed = writes.changedSince(id);
        if (changed > 0) {
            throw changedSince(changed);
        }
        start(batch, MigrationBatchRepository.ROLLBACK, List.of(MigrationBatchRepository.COMMITTED,
                MigrationBatchRepository.COMMITTING, MigrationBatchRepository.ROLLING_BACK), batch.rowsCommitted());
        if (!batches.rollingBack(id, reason.strip())) {
            batches.failJob(id, ROLLBACK_FAILED, clock.instant());
            throw MigrationValidation.notInState(batchService.require(id), "undone", "COMMITTED, COMMITTING, ROLLING_BACK");
        }
        String actor = AcbActor.current();
        launcher.launch(() -> runRollback(id, actor, properties.chunkSize()));
        return batchService.find(id);
    }

    /** The undo's job. Never throws: a failure is written on the batch, which stays ROLLING_BACK. */
    void runRollback(long id, String actor, int chunkSize) {
        try {
            undo(id, actor, chunkSize);
        } catch (ChangedDuring changed) {
            log.warn("Migration batch {}: the undo stopped, {} member(s) changed meanwhile", id, changed.count);
            batches.failJob(id, CHANGED_DURING, clock.instant());
        } catch (DataIntegrityViolationException referenced) {
            log.warn("Migration batch {}: the undo stopped, a member is referred to elsewhere", id);
            batches.failJob(id, CHANGED_DURING, clock.instant());
        } catch (RuntimeException failed) {
            log.error("Migration batch {}: the undo failed (SQL state {})", id, AcbLogging.sqlState(failed),
                    AcbLogging.scrubbed(failed));
            batches.failJob(id, ROLLBACK_FAILED, clock.instant());
        }
    }

    private void undo(long id, String actor, int chunkSize) {
        Batch batch = batchService.require(id);
        int done = 0;
        int removedAll = 0;
        while (true) {
            List<MigrationCommitRepository.Link> links = writes.linksPage(id, chunkSize);
            if (links.isEmpty()) {
                break;
            }
            List<Long> linkIds = links.stream().map(MigrationCommitRepository.Link::id).toList();
            List<Integer> memberIds = links.stream().map(MigrationCommitRepository.Link::memberId).toList();
            List<Long> changeIds = links.stream().map(MigrationCommitRepository.Link::statusChangeId)
                    .filter(java.util.Objects::nonNull).toList();
            int next = done + links.size();
            int removed = transactions.execute(status -> {
                writes.lock(memberIds);
                long changed = writes.changedAmong(linkIds);
                if (changed > 0) {
                    throw new ChangedDuring(changed);
                }
                int gone = writes.remove(linkIds, changeIds, memberIds);
                if (!batches.undone(id, gone, next, clock.instant())) {
                    throw new IllegalStateException("the undo was swept while it ran");
                }
                return gone;
            });
            done = next;
            removedAll += removed;
        }
        if (!batches.rolledBack(id, actor, clock.instant())) {
            throw new IllegalStateException("the undo was swept while it ran");
        }
        log.info("Migration batch {} undone: {} members removed of {} moved", id, removedAll, batch.rowsCommitted());
    }

    /** Members changed while the undo ran: nothing more is undone. */
    private static final class ChangedDuring extends RuntimeException {

        private static final long serialVersionUID = 1L;

        private final long count;

        ChangedDuring(long count) {
            super("members changed during the undo");
            this.count = count;
        }
    }

    private static MigrationRefusal changedSince(long changed) {
        return new MigrationRefusal(MigrationRefusalCode.ROLLBACK_MEMBERS_CHANGED, changed + " member(s) moved by this "
                + "batch have been changed or used since -- edited, given another status, or taken into a debit order "
                + "run. Nothing was undone.", MigrationRefusal.details("changedMembers", String.valueOf(changed)));
    }

    // ------------------------------------------------------------------------------------------------ jobs

    private void start(Batch batch, String kind, List<String> statuses, int total) {
        if (batches.anyJobRunning()) {
            throw MigrationValidation.busy();
        }
        try {
            if (!batches.startJob(batch.id(), kind, statuses, total, clock.instant())) {
                throw MigrationValidation.notInState(batchService.require(batch.id()),
                        MigrationBatchRepository.COMMIT.equals(kind) ? "moved" : "undone", String.join(", ", statuses));
            }
        } catch (DuplicateKeyException another) {
            throw MigrationValidation.busy();
        }
    }
}
