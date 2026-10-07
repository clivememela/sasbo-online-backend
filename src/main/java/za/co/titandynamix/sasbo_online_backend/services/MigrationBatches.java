package za.co.titandynamix.sasbo_online_backend.services;

import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import za.co.titandynamix.sasbo_online_backend.acb.AcbActor;
import za.co.titandynamix.sasbo_online_backend.acb.AcbLogging;
import za.co.titandynamix.sasbo_online_backend.acb.AcbTime;
import za.co.titandynamix.sasbo_online_backend.config.MigrationProperties;
import za.co.titandynamix.sasbo_online_backend.dto.migration.MigrationBatchDto;
import za.co.titandynamix.sasbo_online_backend.dto.migration.MigrationBatchPageDto;
import za.co.titandynamix.sasbo_online_backend.dto.migration.MigrationReportDto;
import za.co.titandynamix.sasbo_online_backend.dto.migration.MigrationRowPageDto;
import za.co.titandynamix.sasbo_online_backend.exception.MigrationRefusal;
import za.co.titandynamix.sasbo_online_backend.exception.MigrationRefusalCode;
import za.co.titandynamix.sasbo_online_backend.legacy.StagedJson;
import za.co.titandynamix.sasbo_online_backend.legacy.mapping.FieldMapping;
import za.co.titandynamix.sasbo_online_backend.legacy.mapping.MappingDefinition;
import za.co.titandynamix.sasbo_online_backend.legacy.mapping.MappingParser;
import za.co.titandynamix.sasbo_online_backend.legacy.mapping.MemberField;
import za.co.titandynamix.sasbo_online_backend.legacy.read.LimitedInputStream;
import za.co.titandynamix.sasbo_online_backend.legacy.read.ReadSummary;
import za.co.titandynamix.sasbo_online_backend.legacy.read.RowSink;
import za.co.titandynamix.sasbo_online_backend.legacy.read.SourceReader;
import za.co.titandynamix.sasbo_online_backend.legacy.read.SourceRefused;
import za.co.titandynamix.sasbo_online_backend.legacy.read.SourceRow;
import za.co.titandynamix.sasbo_online_backend.legacy.rules.Problem;
import za.co.titandynamix.sasbo_online_backend.legacy.rules.RuleCode;
import za.co.titandynamix.sasbo_online_backend.repository.MigrationBatchRepository;
import za.co.titandynamix.sasbo_online_backend.repository.MigrationBatchRepository.Batch;
import za.co.titandynamix.sasbo_online_backend.repository.MigrationRowRepository;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * A legacy file registered as a batch, and what a batch says about itself: its report and its rows.
 *
 * <h2>Registering a file streams it</h2>
 * The request's body is read through a byte ceiling and a SHA-256 digest, by the reader the mapping names, and each row
 * is staged the moment it is read -- a chunk at a time, in JDBC batches, the whole file never in memory. A file that
 * cannot be read at all keeps NOTHING: its batch and every row staged so far are deleted, and the refusal says why.
 *
 * <h2>The same file twice is the batch it already is</h2>
 * Known by its hash: declared up front ({@code sha256}), it is answered without reading the body; otherwise it is known
 * once read, and the second reading is thrown away. A batch that was undone frees its file to be moved again. The
 * database's partial unique index holds this where two registrations of one file meet.
 *
 * <h2>Nothing of a member is logged</h2>
 * A log line carries a batch's number, counts, a format and a code -- never a file's name (people name files after
 * people), never a value of a row.
 */
@Slf4j
@Service
public class MigrationBatches {

    /** The largest page of batches or of rows. */
    public static final int MAX_PAGE_SIZE = 100;

    /** How many rows with problems the report reads at a time -- numbers and codes only. */
    static final int REPORT_PAGE = 5000;

    /** How many lines a report names for each problem. */
    static final int FIRST_ROWS = 20;

    private static final Pattern SHA256 = Pattern.compile("[0-9a-fA-F]{64}");
    private static final Pattern CONTROL = Pattern.compile("\\p{Cntrl}");

    private final MigrationBatchRepository batches;
    private final MigrationRowRepository rows;
    private final MigrationMappings mappings;
    private final MigrationProperties properties;
    private final Clock clock;
    private final MappingParser parser = new MappingParser();

    public MigrationBatches(MigrationBatchRepository batches, MigrationRowRepository rows, MigrationMappings mappings,
                            MigrationProperties properties, Clock clock) {
        this.batches = batches;
        this.rows = rows;
        this.mappings = mappings;
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * A registration's answer: the batch, whether this call created it, and -- for the proof that a read streams -- the
     * most rows it held at once.
     */
    public record Registered(MigrationBatchDto batch, boolean created, int mostRowsHeld) {
    }

    // ------------------------------------------------------------------------------------------------ registering

    public Registered register(String fileName, String mappingKey, String asAtText, String declaredSha256,
                               InputStream body) {
        String name = fileName(fileName);
        LocalDate asAt = asAt(asAtText);
        String declared = declaredSha256 == null || declaredSha256.isBlank() ? null : declaredSha256.strip();
        if (declared != null && !SHA256.matcher(declared).matches()) {
            throw new MigrationRefusal(MigrationRefusalCode.REQUEST_INVALID,
                    "The SHA-256 given is not 64 hexadecimal digits.", MigrationRefusal.details("fields", "sha256"));
        }
        if (declared != null) {
            declared = declared.toLowerCase(Locale.ROOT);
            Batch existing = batches.findLiveByHash(declared).orElse(null);
            if (existing != null) {
                log.info("Migration file already registered as batch {}; nothing read", existing.id());
                return new Registered(dto(existing, true), false, 0);
            }
        }
        MappingDefinition mapping = mappings.definition(mappingKey);
        if (batches.anyReceiving()) {
            throw busy();
        }
        long id;
        try {
            id = batches.insertReceiving(name, mapping.format().format().name(), mapping.key(), mapping.json(), asAt,
                    AcbActor.current(), clock.instant());
        } catch (DuplicateKeyException another) {
            throw busy();
        }
        try {
            return read(id, mapping, declared, body);
        } catch (RuntimeException failed) {
            batches.delete(id);
            if (failed instanceof SourceRefused refused) {
                log.info("Migration batch {} refused while reading: {}", id, refused.reason());
                throw translate(refused);
            }
            if (!(failed instanceof MigrationRefusal)) {
                log.error("Migration batch {} could not be read; nothing kept", id, AcbLogging.scrubbed(failed));
            }
            throw failed;
        }
    }

    private Registered read(long id, MappingDefinition mapping, String declared, InputStream body) {
        MessageDigest digest = sha256();
        LimitedInputStream limited = new LimitedInputStream(body, properties.limits(mapping.format().format()).maxBytes());
        Stager stager = new Stager(id);
        ReadSummary summary;
        try (DigestInputStream hashing = new DigestInputStream(limited, digest)) {
            summary = SourceReader.of(mapping.format().format()).read(hashing, mapping.format(),
                    properties.limits(mapping.format().format()), stager);
            stager.flush();
        } catch (IOException unreadable) {
            throw new UncheckedIOException(unreadable);
        }
        String hash = HexFormat.of().formatHex(digest.digest());
        if (declared != null && !declared.equals(hash)) {
            throw new MigrationRefusal(MigrationRefusalCode.UPLOAD_HASH_MISMATCH, "The file is not the one its SHA-256 "
                    + "says: it changed on the way, or the wrong file was sent. Nothing was kept.");
        }
        Batch twin = batches.findLiveByHash(hash).orElse(null);
        if (twin != null) {
            batches.delete(id);
            log.info("Migration file already registered as batch {}; the second reading is not kept", twin.id());
            return new Registered(dto(twin, true), false, stager.mostHeld);
        }
        try {
            if (!batches.received(id, hash, limited.count(), summary.rows())) {
                throw new IllegalStateException("the batch was no longer being received");
            }
        } catch (DuplicateKeyException race) {
            batches.delete(id);
            Batch winner = batches.findLiveByHash(hash).orElseThrow(() -> race);
            return new Registered(dto(winner, true), false, stager.mostHeld);
        }
        log.info("Migration batch {} registered: {} rows, {} bytes, {}", id, summary.rows(), limited.count(),
                mapping.format().format());
        return new Registered(dto(require(id), false), true, stager.mostHeld);
    }

    /** Rows staged a chunk at a time: the most this ever holds is one chunk. */
    private final class Stager implements RowSink {

        private final long batchId;
        private final List<MigrationRowRepository.ToStage> chunk = new ArrayList<>();
        private int staged;
        private int mostHeld;

        Stager(long batchId) {
            this.batchId = batchId;
        }

        @Override
        public void accept(SourceRow row) {
            chunk.add(new MigrationRowRepository.ToStage(row.rowNumber(), StagedJson.raw(row.values()),
                    Problem.joinAll(row.problems(), 2000)));
            mostHeld = Math.max(mostHeld, chunk.size());
            if (chunk.size() >= properties.chunkSize()) {
                flush();
            }
        }

        void flush() {
            if (chunk.isEmpty()) {
                return;
            }
            rows.stage(batchId, chunk);
            staged += chunk.size();
            chunk.clear();
            batches.receiving(batchId, staged, clock.instant());
        }
    }

    private static MigrationRefusal translate(SourceRefused refused) {
        return switch (refused.reason()) {
            case EMPTY -> new MigrationRefusal(MigrationRefusalCode.UPLOAD_EMPTY,
                    "The file holds no rows to import. Nothing was kept.");
            case TOO_LARGE -> new MigrationRefusal(MigrationRefusalCode.UPLOAD_TOO_LARGE, "The file is larger than the "
                    + "limit of " + refused.limit() / (1024 * 1024) + " MB. Nothing was kept.",
                    MigrationRefusal.details("limitBytes", String.valueOf(refused.limit())));
            case TOO_MANY_ROWS -> new MigrationRefusal(MigrationRefusalCode.UPLOAD_TOO_MANY_ROWS, "The file has more "
                    + "rows than the limit of " + refused.limit() + ". Nothing was kept.",
                    MigrationRefusal.details("limitRows", String.valueOf(refused.limit())));
            // The reader's sentence is its own, and quotes nothing of the file.
            case NOT_READABLE -> new MigrationRefusal(MigrationRefusalCode.UPLOAD_NOT_READABLE,
                    refused.getMessage() + " Nothing was kept.");
        };
    }

    private static MigrationRefusal busy() {
        return new MigrationRefusal(MigrationRefusalCode.MIGRATION_BUSY, "Another file is being read, or another "
                + "batch checked, moved or undone. Try again when it has finished.");
    }

    /** The name a person gave the file -- its last path segment, no control characters, 1 to 200 characters. */
    static String fileName(String given) {
        String name = given == null ? "" : given;
        name = name.substring(Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\')) + 1);
        name = CONTROL.matcher(name).replaceAll("").strip();
        if (name.isEmpty() || name.length() > 200) {
            throw new MigrationRefusal(MigrationRefusalCode.FILE_NAME_INVALID,
                    "Give the file's name, of 1 to 200 characters.");
        }
        return name;
    }

    private LocalDate asAt(String text) {
        LocalDate asAt;
        try {
            asAt = LocalDate.parse(text == null ? "" : text.strip());
        } catch (DateTimeParseException unreadable) {
            throw new MigrationRefusal(MigrationRefusalCode.AS_AT_INVALID, "Give the date the export represents -- the "
                    + "old system's month end it was taken at -- as a date (2026-08-31).");
        }
        if (asAt.isAfter(AcbTime.today(clock))) {
            throw new MigrationRefusal(MigrationRefusalCode.AS_AT_INVALID, "The date the export represents cannot be "
                    + "after today.", MigrationRefusal.details("asAt", asAt.toString(),
                    "today", AcbTime.today(clock).toString()));
        }
        return asAt;
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException absent) {
            throw new IllegalStateException("SHA-256 is not available", absent);
        }
    }

    // ------------------------------------------------------------------------------------------------ reading

    public MigrationBatchDto find(long id) {
        return dto(require(id), false);
    }

    public MigrationBatchPageDto page(int page, int size) {
        MigrationPages.check(page, size, MAX_PAGE_SIZE);
        MigrationBatchRepository.Slice slice = batches.page(page, size);
        return new MigrationBatchPageDto(slice.rows().stream().map(batch -> dto(batch, false)).toList(), page, size,
                slice.total());
    }

    /** The batch, or BATCH_NOT_FOUND. */
    Batch require(long id) {
        return batches.find(id).orElseThrow(() -> new MigrationRefusal(MigrationRefusalCode.BATCH_NOT_FOUND,
                "There is no batch with that number.", MigrationRefusal.details("batchId", String.valueOf(id))));
    }

    /**
     * The report: every problem of the batch's rows by its code and field, how many rows have it and the first of them by
     * line, worst first -- read a page of codes at a time, never the rows' values.
     */
    public MigrationReportDto report(long id) {
        Batch batch = checked(require(id));
        Map<String, Tally> errors = new LinkedHashMap<>();
        Map<String, Tally> warnings = new LinkedHashMap<>();
        long after = 0;
        while (true) {
            List<MigrationRowRepository.Problems> page = rows.problemsPage(id, after, REPORT_PAGE);
            if (page.isEmpty()) {
                break;
            }
            for (MigrationRowRepository.Problems row : page) {
                tally(errors, row.errorCodes(), row.rowNumber());
                tally(warnings, row.warningCodes(), row.rowNumber());
            }
            after = page.get(page.size() - 1).id();
        }
        return new MigrationReportDto(batch.id(), batch.status(), batch.rowsRead(), batch.rowsValid(),
                batch.rowsWarned(), batch.rowsInvalid(), batch.rowsCommitted(), counts(errors, "ERROR"),
                counts(warnings, "WARNING"));
    }

    private static final class Tally {
        private final Problem problem;
        private long rows;
        private final List<Integer> first = new ArrayList<>();

        Tally(Problem problem) {
            this.problem = problem;
        }
    }

    private static void tally(Map<String, Tally> tallies, String tokens, int rowNumber) {
        for (Problem problem : Problem.parseAll(tokens)) {
            Tally tally = tallies.computeIfAbsent(problem.token(), token -> new Tally(problem));
            tally.rows++;
            if (tally.first.size() < FIRST_ROWS) {
                tally.first.add(rowNumber);
            }
        }
    }

    private static List<MigrationReportDto.CodeCount> counts(Map<String, Tally> tallies, String severity) {
        return tallies.values().stream()
                .sorted(Comparator.comparingLong((Tally tally) -> -tally.rows).thenComparing(tally -> tally.problem.token()))
                .map(tally -> new MigrationReportDto.CodeCount(tally.problem.code().name(), tally.problem.field(),
                        severity, tally.rows, List.copyOf(tally.first), tally.problem.code().sentence()))
                .toList();
    }

    /**
     * A page of rows for the person fixing the file -- narrowed to an outcome and a rule -- with their values, the
     * identity, passport and account numbers as their last four.
     */
    public MigrationRowPageDto rows(long id, String outcome, String code, int page, int size) {
        Batch batch = checked(require(id));
        MigrationPages.check(page, size, MAX_PAGE_SIZE);
        String wanted = outcome == null || outcome.isBlank() ? null : outcome.strip().toUpperCase(Locale.ROOT);
        if (wanted != null && !Set.of("VALID", "WARNING", "ERROR").contains(wanted)) {
            throw new MigrationRefusal(MigrationRefusalCode.REQUEST_INVALID,
                    "The outcome is VALID, WARNING or ERROR.", MigrationRefusal.details("fields", "outcome"));
        }
        RuleCode rule = null;
        if (code != null && !code.isBlank()) {
            try {
                rule = RuleCode.valueOf(code.strip());
            } catch (IllegalArgumentException unknown) {
                throw new MigrationRefusal(MigrationRefusalCode.REQUEST_INVALID, "That is not one of the rules' codes.",
                        MigrationRefusal.details("fields", "code"));
            }
        }
        MappingDefinition mapping = parser.parse(batch.mappingSnapshot());
        List<String> maskedColumns = mapping.fields().entrySet().stream()
                .filter(entry -> entry.getKey().masked() && entry.getValue().column() != null)
                .map(Map.Entry::getValue).map(FieldMapping::column).map(MigrationBatches::normalise).toList();
        MigrationRowRepository.Slice slice = rows.list(id, wanted, rule, page, size);
        List<MigrationRowPageDto.Row> items = slice.rows().stream().map(row -> {
            List<MigrationRowPageDto.ProblemDto> problems = new ArrayList<>();
            Problem.parseAll(row.errorCodes()).forEach(problem -> problems.add(problem(problem, "ERROR")));
            Problem.parseAll(row.warningCodes()).forEach(problem -> problems.add(problem(problem, "WARNING")));
            Map<String, String> raw = new LinkedHashMap<>();
            StagedJson.raw(row.rawJson()).forEach((column, value) ->
                    raw.put(column, maskedColumns.contains(normalise(column)) ? mask(value) : value));
            Map<String, String> values = new LinkedHashMap<>();
            if (row.normalisedJson() != null) {
                StagedJson.normalised(row.normalisedJson()).fields().forEach((field, value) ->
                        values.put(field.wire(), field.masked() ? mask(value) : value));
            }
            return new MigrationRowPageDto.Row(row.rowNumber(), row.outcome(), problems, values, raw);
        }).toList();
        return new MigrationRowPageDto(items, page, size, slice.total());
    }

    private static MigrationRowPageDto.ProblemDto problem(Problem problem, String severity) {
        return new MigrationRowPageDto.ProblemDto(problem.code().name(), problem.field(), severity,
                problem.code().sentence());
    }

    /** As the member's history shows a masked number: its last four. */
    static String mask(String value) {
        if (value == null || value.isBlank()) {
            return value;
        }
        String text = value.strip();
        return text.length() <= 4 ? "****" : "****" + text.substring(text.length() - 4);
    }

    private static String normalise(String column) {
        return column.strip().replaceAll("\\s+", " ").toUpperCase(Locale.ROOT);
    }

    /** A batch whose file has been read -- a report of one still being read would be a report of half a file. */
    private static Batch checked(Batch batch) {
        if (MigrationBatchRepository.RECEIVING.equals(batch.status())) {
            throw new MigrationRefusal(MigrationRefusalCode.BATCH_NOT_IN_STATE, "This batch's file is still being read.",
                    MigrationRefusal.details("status", batch.status()));
        }
        return batch;
    }

    // ------------------------------------------------------------------------------------------------ the answer

    static MigrationBatchDto dto(Batch b, boolean alreadyRegistered) {
        MigrationBatchDto.Job job = b.jobKind() == null ? null : new MigrationBatchDto.Job(b.jobKind(), b.jobState(),
                b.jobDone(), b.jobTotal(), AcbTime.iso(b.jobStartedAt()), AcbTime.iso(b.jobHeartbeatAt()),
                AcbTime.iso(b.jobFinishedAt()), b.jobFailure());
        return new MigrationBatchDto(b.id(), b.sourceFileName(), b.sourceSha256(), b.sourceBytes(), b.sourceFormat(),
                b.mappingKey(), b.asAt().toString(), b.status(), b.rowsRead(), b.rowsValid(), b.rowsWarned(),
                b.rowsInvalid(), b.rowsCommitted(), b.registeredBy(), AcbTime.iso(b.registeredAt()), b.validatedBy(),
                AcbTime.iso(b.validatedAt()), b.committedBy(), AcbTime.iso(b.committedAt()), b.rolledBackBy(),
                AcbTime.iso(b.rolledBackAt()), b.rollbackReason(), job, alreadyRegistered);
    }

    /** For the deck and tests: the member field a masked raw column stands for is never shown whole. */
    static boolean masked(MemberField field) {
        return field.masked();
    }
}
