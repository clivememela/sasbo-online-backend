package za.co.titandynamix.sasbo_online_backend.services;

import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
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
import za.co.titandynamix.sasbo_online_backend.legacy.mapping.RowMapper;
import za.co.titandynamix.sasbo_online_backend.legacy.rules.CleanRow;
import za.co.titandynamix.sasbo_online_backend.legacy.rules.Problem;
import za.co.titandynamix.sasbo_online_backend.legacy.rules.RowRules;
import za.co.titandynamix.sasbo_online_backend.legacy.rules.RuleCode;
import za.co.titandynamix.sasbo_online_backend.repository.MigrationBatchRepository;
import za.co.titandynamix.sasbo_online_backend.repository.MigrationBatchRepository.Batch;
import za.co.titandynamix.sasbo_online_backend.repository.MigrationReferenceRepository;
import za.co.titandynamix.sasbo_online_backend.repository.MigrationRowRepository;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The check: every staged row of a batch through its mapping and every rule, then the rows against one another and
 * against the members on record -- a DRY RUN that writes nothing but the rows' own outcomes. A job: started here,
 * answered at once, run off the request thread, its progress written a page at a time for the screen to poll.
 *
 * <p>A page of rows at a time, by id -- the most a check holds is one page and SASBO's lists (5,507 workplaces). A check
 * may be run again, by the same mapping or by another of the same format, until the batch is moved: each run rewrites
 * every row's outcome. A check that does not finish leaves the batch a DRAFT, since its rows are then half re-checked.
 */
@Slf4j
@Service
public class MigrationValidation {

    /** What a failed check says on the batch: a sentence of this code's own, never an exception's message. */
    static final String FAILED = "The check stopped before it finished, and nothing was moved. Check the batch again.";

    private final MigrationBatchRepository batches;
    private final MigrationRowRepository rows;
    private final MigrationReferenceRepository reference;
    private final MigrationMappings mappings;
    private final MigrationBatches batchService;
    private final MigrationJobLauncher launcher;
    private final MigrationProperties properties;
    private final Clock clock;
    private final MappingParser parser = new MappingParser();

    public MigrationValidation(MigrationBatchRepository batches, MigrationRowRepository rows,
                               MigrationReferenceRepository reference, MigrationMappings mappings,
                               MigrationBatches batchService, MigrationJobLauncher launcher,
                               MigrationProperties properties, Clock clock) {
        this.batches = batches;
        this.rows = rows;
        this.reference = reference;
        this.mappings = mappings;
        this.batchService = batchService;
        this.launcher = launcher;
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * Starts a check -- by the batch's mapping, or by {@code mappingKey}, which must read the batch's format -- and
     * answers with the batch as it stands once started (or, under test, finished).
     */
    public MigrationBatchDto validate(long id, String mappingKey) {
        String actor = begin(id, mappingKey);
        launcher.launch(() -> run(id, actor));
        return batchService.find(id);
    }

    /** A check run on the caller's thread, whatever the launcher -- for the demonstration's seed, which waits for it. */
    public MigrationBatchDto validateNow(long id) {
        run(id, begin(id, null));
        return batchService.find(id);
    }

    /** Every refusal a check can meet, then its job started. The actor, read on the request's thread. */
    private String begin(long id, String mappingKey) {
        Batch batch = batchService.require(id);
        if (!MigrationBatchRepository.DRAFT.equals(batch.status())
                && !MigrationBatchRepository.VALIDATED.equals(batch.status())) {
            throw notInState(batch, "checked", "DRAFT, VALIDATED");
        }
        if (mappingKey != null && !mappingKey.equals(batch.mappingKey())) {
            MappingDefinition mapping = mappings.definition(mappingKey);
            if (!mapping.format().format().name().equals(batch.sourceFormat())) {
                throw new MigrationRefusal(MigrationRefusalCode.MAPPING_NOT_FOR_THIS_FILE, "That mapping reads another "
                        + "format than this file. Choose a mapping for " + batch.sourceFormat() + ".",
                        MigrationRefusal.details("fileFormat", batch.sourceFormat(),
                                "mappingFormat", mapping.format().format().name()));
            }
            if (!batches.remap(id, mapping.key(), mapping.json())) {
                throw busy();
            }
        }
        if (batches.anyJobRunning()) {
            throw busy();
        }
        String actor = AcbActor.current();
        try {
            if (!batches.startJob(id, MigrationBatchRepository.VALIDATE,
                    List.of(MigrationBatchRepository.DRAFT, MigrationBatchRepository.VALIDATED), batch.rowsRead(),
                    clock.instant())) {
                throw notInState(batchService.require(id), "checked", "DRAFT, VALIDATED");
            }
        } catch (DuplicateKeyException another) {
            throw busy();
        }
        return actor;
    }

    /** The job. Never throws: a failure is written on the batch and logged scrubbed. */
    void run(long id, String actor) {
        try {
            check(id, actor);
        } catch (RuntimeException failed) {
            log.error("Migration batch {}: the check failed", id, AcbLogging.scrubbed(failed));
            batches.failJob(id, FAILED, clock.instant());
            batches.toDraft(id);
        }
    }

    private void check(long id, String actor) {
        Batch batch = batchService.require(id);
        MappingDefinition mapping = parser.parse(batch.mappingSnapshot());
        RowMapper mapper = new RowMapper(mapping);
        RowRules rules = new RowRules(mapping, reference.load(), AcbTime.today(clock), batch.asAt());
        long after = 0;
        int done = 0;
        while (true) {
            List<MigrationRowRepository.Staged> page = rows.stagedPage(id, after, properties.chunkSize());
            if (page.isEmpty()) {
                break;
            }
            List<MigrationRowRepository.Checked> checked = new ArrayList<>(page.size());
            for (MigrationRowRepository.Staged staged : page) {
                CleanRow row = rules.check(Problem.parseAll(staged.readProblems()), mapper.map(StagedJson.raw(staged.rawJson())));
                checked.add(new MigrationRowRepository.Checked(staged.id(), row.outcome().name(),
                        Problem.joinAll(row.errors(), 2000), Problem.joinAll(row.warnings(), 2000),
                        StagedJson.normalised(row), row.identityKey(), row.membershipKey(), row.membershipSeq(),
                        row.statusKind(), row.areaLetter(), row.tableCode(), row.workplaceId(), row.suspensionCode(),
                        row.deletionCode(), row.joinDate(), row.leftOn()));
            }
            rows.saveChecked(checked);
            after = page.get(page.size() - 1).id();
            done += page.size();
            if (!batches.progress(id, done, clock.instant())) {
                throw new IllegalStateException("the check was swept while it ran");
            }
        }
        Map<RuleCode, Integer> across = rows.markAcrossRows(id, false);
        Map<String, Integer> outcomes = rows.countByOutcome(id);
        int valid = outcomes.getOrDefault("VALID", 0);
        int warned = outcomes.getOrDefault("WARNING", 0);
        int invalid = outcomes.getOrDefault("ERROR", 0);
        if (!batches.validated(id, valid, warned, invalid, actor, clock.instant())) {
            throw new IllegalStateException("the check was swept while it ran");
        }
        log.info("Migration batch {} checked: {} valid, {} with warnings, {} held back; across rows {}", id, valid,
                warned, invalid, across);
    }

    static MigrationRefusal notInState(Batch batch, String action, String allowed) {
        return new MigrationRefusal(MigrationRefusalCode.BATCH_NOT_IN_STATE, "This batch cannot be " + action
                + " while it is " + batch.status() + ".", MigrationRefusal.details("status", batch.status(),
                "allowed", allowed));
    }

    static MigrationRefusal busy() {
        return new MigrationRefusal(MigrationRefusalCode.MIGRATION_BUSY, "Another batch is being read, checked, moved "
                + "or undone. Try again when it has finished.");
    }
}
