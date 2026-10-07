package za.co.titandynamix.sasbo_online_backend.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import za.co.titandynamix.sasbo_online_backend.config.MigrationProperties;
import za.co.titandynamix.sasbo_online_backend.dto.migration.MigrationBatchDto;
import za.co.titandynamix.sasbo_online_backend.dto.migration.MigrationReportDto;
import za.co.titandynamix.sasbo_online_backend.dto.migration.MigrationRowPageDto;
import za.co.titandynamix.sasbo_online_backend.exception.MigrationRefusal;
import za.co.titandynamix.sasbo_online_backend.exception.MigrationRefusalCode;
import za.co.titandynamix.sasbo_online_backend.legacy.LegacyFixtures;
import za.co.titandynamix.sasbo_online_backend.repository.MemberRepository;
import za.co.titandynamix.sasbo_online_backend.repository.MigrationBatchRepository;
import za.co.titandynamix.sasbo_online_backend.repository.MigrationMappingRepository;
import za.co.titandynamix.sasbo_online_backend.repository.MigrationRowRepository;
import za.co.titandynamix.sasbo_online_backend.support.AbstractSwitchedOnIT;
import za.co.titandynamix.sasbo_online_backend.support.MigrationFixture;
import za.co.titandynamix.sasbo_online_backend.utils.ReferenceListSeeder;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Registering a legacy file, on PostgreSQL: every row staged as read, numbered by its line; the same file twice is the
 * batch it already is -- by a hash declared up front without a byte read, or by the hash once read, the second reading
 * kept nowhere; a file refused while it is read keeps NOTHING; and the report and the rows say what the check found,
 * the identity numbers as their last four.
 */
@DisplayName("Registering a legacy file, on PostgreSQL")
class MigrationBatchesIT extends AbstractSwitchedOnIT {

    @Autowired
    private MigrationBatches batches;

    @Autowired
    private MigrationValidation validation;

    @Autowired
    private MigrationMappings mappings;

    @Autowired
    private MigrationBatchRepository batchRepository;

    @Autowired
    private MigrationRowRepository rowRepository;

    @Autowired
    private MigrationMappingRepository mappingRepository;

    @Autowired
    private ReferenceListSeeder lists;

    @Autowired
    private MemberRepository members;

    @Autowired
    private Clock clock;

    @BeforeEach
    void theLists() {
        MigrationFixture.prepare(jdbc, lists, members);
    }

    private MigrationBatches.Registered registerCsv() {
        return batches.register("member-export-faults.csv", LegacyFixtures.CSV_MAPPING, "2026-08-31", null,
                MigrationFixture.csv());
    }

    private long rows(long batchId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM migration_rows WHERE batch_id = ?", Long.class, batchId);
    }

    private static String sha256(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    @Test
    @DisplayName("every row staged, raw as read and numbered by its line; the batch a DRAFT with the file's hash and size")
    void registers() throws Exception {
        byte[] file = LegacyFixtures.resource(LegacyFixtures.CSV_FIXTURE);

        MigrationBatches.Registered registered = registerCsv();

        MigrationBatchDto batch = registered.batch();
        assertThat(registered.created()).isTrue();
        assertThat(batch.status()).isEqualTo("DRAFT");
        assertThat(batch.rowsRead()).isEqualTo(MigrationFixture.CSV_ROWS);
        assertThat(batch.sha256()).isEqualTo(sha256(file));
        assertThat(batch.bytes()).isEqualTo(file.length);
        assertThat(batch.format()).isEqualTo("CSV");
        assertThat(batch.registeredBy()).isEqualTo("system");
        assertThat(rows(batch.id())).isEqualTo(MigrationFixture.CSV_ROWS);
        assertThat(jdbc.queryForList("SELECT row_number FROM migration_rows WHERE batch_id = ? ORDER BY id LIMIT 3",
                Integer.class, batch.id())).containsExactly(2, 3, 4);
        assertThat(jdbc.queryForObject("SELECT raw_json FROM migration_rows WHERE batch_id = ? AND row_number = 3",
                String.class, batch.id())).contains("\"FIRST NAMES\":\"Zo\u00eb\"");
        assertThat(rowRepository.stagedPage(batch.id(), 0, 5)).hasSize(5);
    }

    @Test
    @DisplayName("🔒 the same file twice is the batch it already is: nothing staged twice, by the hash read or the hash declared")
    void idempotent() throws Exception {
        long first = registerCsv().batch().id();

        MigrationBatches.Registered again = registerCsv();
        assertThat(again.created()).isFalse();
        assertThat(again.batch().id()).isEqualTo(first);
        assertThat(again.batch().alreadyRegistered()).isTrue();

        String hash = sha256(LegacyFixtures.resource(LegacyFixtures.CSV_FIXTURE));
        MigrationBatches.Registered declared = batches.register("renamed.csv", LegacyFixtures.CSV_MAPPING, "2026-08-31",
                hash.toUpperCase(), new ByteArrayInputStream(new byte[0]) {
                    @Override
                    public synchronized int read(byte[] b, int off, int len) {
                        throw new AssertionError("a file declared by its hash and registered already is never read");
                    }
                });
        assertThat(declared.batch().id()).isEqualTo(first);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM migration_batches", Long.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM migration_rows", Long.class))
                .isEqualTo(MigrationFixture.CSV_ROWS);
    }

    @Test
    @DisplayName("🔒 a file refused while read keeps nothing: empty, binary, past the row ceiling, its hash not the one declared")
    void refusalsKeepNothing() {
        assertThatThrownBy(() -> batches.register("empty.csv", LegacyFixtures.CSV_MAPPING, "2026-08-31", null,
                new ByteArrayInputStream(new byte[0])))
                .isInstanceOf(MigrationRefusal.class).extracting(r -> ((MigrationRefusal) r).code())
                .isEqualTo(MigrationRefusalCode.UPLOAD_EMPTY);
        assertThatThrownBy(() -> batches.register("binary.csv", LegacyFixtures.CSV_MAPPING, "2026-08-31", null,
                new ByteArrayInputStream(new byte[] {'P', 'K', 3, 4, 0, 0})))
                .isInstanceOf(MigrationRefusal.class).extracting(r -> ((MigrationRefusal) r).code())
                .isEqualTo(MigrationRefusalCode.UPLOAD_NOT_READABLE);
        MigrationBatches small = new MigrationBatches(batchRepository, rowRepository, mappings,
                new MigrationProperties(1_000_000, 1_000_000, 10, 200, 1000, 4, 10), clock);
        assertThatThrownBy(() -> small.register("long.csv", LegacyFixtures.CSV_MAPPING, "2026-08-31", null,
                MigrationFixture.csv()))
                .isInstanceOf(MigrationRefusal.class).extracting(r -> ((MigrationRefusal) r).code())
                .isEqualTo(MigrationRefusalCode.UPLOAD_TOO_MANY_ROWS);
        MigrationBatches tiny = new MigrationBatches(batchRepository, rowRepository, mappings,
                new MigrationProperties(2_000, 2_000, 1_000_000, 200, 1000, 4, 10), clock);
        assertThatThrownBy(() -> tiny.register("big.csv", LegacyFixtures.CSV_MAPPING, "2026-08-31", null,
                MigrationFixture.csv()))
                .isInstanceOf(MigrationRefusal.class).extracting(r -> ((MigrationRefusal) r).code())
                .isEqualTo(MigrationRefusalCode.UPLOAD_TOO_LARGE);
        assertThatThrownBy(() -> batches.register("lied.csv", LegacyFixtures.CSV_MAPPING, "2026-08-31", "0".repeat(64),
                MigrationFixture.csv()))
                .isInstanceOf(MigrationRefusal.class).extracting(r -> ((MigrationRefusal) r).code())
                .isEqualTo(MigrationRefusalCode.UPLOAD_HASH_MISMATCH);

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM migration_batches", Long.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM migration_rows", Long.class)).isZero();
    }

    @Test
    @DisplayName("the request's own fields: a file's name (its last segment, no path), the date it represents, a mapping on record")
    void requestFields() {
        assertThat(MigrationBatches.fileName("C:\\exports\\august\\members.csv")).isEqualTo("members.csv");
        assertThat(MigrationBatches.fileName("../../etc/passwd")).isEqualTo("passwd");
        assertThatThrownBy(() -> MigrationBatches.fileName("dir/")).isInstanceOf(MigrationRefusal.class);
        assertThatThrownBy(() -> batches.register("f.csv", LegacyFixtures.CSV_MAPPING, "31/08/2026", null,
                MigrationFixture.csv())).extracting(r -> ((MigrationRefusal) r).code())
                .isEqualTo(MigrationRefusalCode.AS_AT_INVALID);
        assertThatThrownBy(() -> batches.register("f.csv", LegacyFixtures.CSV_MAPPING, "2026-12-31", null,
                MigrationFixture.csv())).extracting(r -> ((MigrationRefusal) r).code())
                .isEqualTo(MigrationRefusalCode.AS_AT_INVALID);
        assertThatThrownBy(() -> batches.register("f.csv", "no-such-mapping", "2026-08-31", null,
                MigrationFixture.csv())).extracting(r -> ((MigrationRefusal) r).code())
                .isEqualTo(MigrationRefusalCode.MAPPING_NOT_FOUND);
        assertThatThrownBy(() -> batches.register("f.csv", LegacyFixtures.CSV_MAPPING, "2026-08-31", "not-a-hash",
                MigrationFixture.csv())).extracting(r -> ((MigrationRefusal) r).code())
                .isEqualTo(MigrationRefusalCode.REQUEST_INVALID);
    }

    @Test
    @DisplayName("a mapping defined here reads a file as a built-in one does -- and is stored, version 1")
    void definedMapping() {
        String definition = mappings.detail(LegacyFixtures.FIXED_WIDTH_MAPPING).definition().toString()
                .replace("\"legacy-member-export-fixed-width\"", "\"our-fixed-width\"");
        mappings.define(new ByteArrayInputStream(definition.getBytes(StandardCharsets.UTF_8)));
        assertThat(mappingRepository.find("our-fixed-width")).hasValueSatisfying(saved -> assertThat(saved.version()).isEqualTo(1));

        MigrationBatches.Registered registered = batches.register("member-export-faults.txt", "our-fixed-width",
                "2026-08-31", null, MigrationFixture.fixedWidth());

        assertThat(registered.batch().format()).isEqualTo("FIXED_WIDTH");
        assertThat(registered.batch().rowsRead()).as("the header, trailer and blank line are not rows").isEqualTo(8);
    }

    @Test
    @DisplayName("the report: every problem by its code and field, how many rows, the first lines -- codes and numbers, never a value")
    void report() {
        long id = registerCsv().batch().id();
        validation.validate(id, null);

        MigrationReportDto report = batches.report(id);

        assertThat(report.status()).isEqualTo("VALIDATED");
        assertThat(report.rowsValid()).isEqualTo(MigrationFixture.CSV_VALID);
        assertThat(report.rowsWarned()).isEqualTo(MigrationFixture.CSV_WARNED);
        assertThat(report.rowsInvalid()).isEqualTo(MigrationFixture.CSV_INVALID);
        assertThat(report.errors()).anySatisfy(count -> {
            assertThat(count.code()).isEqualTo("ID_NUMBER_DUPLICATE_IN_BATCH");
            assertThat(count.rows()).isEqualTo(2);
            assertThat(count.firstRows()).containsExactly(47, 48);
            assertThat(count.sentence()).isNotBlank();
        });
        assertThat(report.warnings()).anySatisfy(count -> {
            assertThat(count.code()).isEqualTo("PHONE_INVALID");
            assertThat(count.field()).isEqualTo("cellTel");
            assertThat(count.firstRows()).containsExactly(16);
        });
        assertThat(report.toString()).doesNotContain(LegacyFixtures.ON_RECORD_ID);
    }

    @Test
    @DisplayName("🔒 the rows, for the person fixing the file: narrowed by outcome and rule; identity numbers as their last four")
    void rows() {
        long id = registerCsv().batch().id();
        validation.validate(id, null);

        MigrationRowPageDto checkDigits = batches.rows(id, "error", "ID_NUMBER_CHECK_DIGIT", 0, 50);
        assertThat(checkDigits.total()).isEqualTo(1);
        MigrationRowPageDto.Row row = checkDigits.items().get(0);
        assertThat(row.rowNumber()).isEqualTo(6);
        assertThat(row.problems()).extracting(MigrationRowPageDto.ProblemDto::code).containsExactly("ID_NUMBER_CHECK_DIGIT");
        assertThat(row.raw().get("ID NUMBER")).startsWith("****").hasSize(8);

        MigrationRowPageDto valid = batches.rows(id, "VALID", null, 0, 2);
        assertThat(valid.total()).isEqualTo(MigrationFixture.CSV_VALID);
        assertThat(valid.items()).hasSize(2);
        assertThat(valid.items().get(0).values().get("identityNumber")).startsWith("****");
        assertThat(valid.items().get(0).values()).containsEntry("membershipNumber", "M-700002");

        assertThatThrownBy(() -> batches.rows(id, "SOMETIMES", null, 0, 10)).extracting(r -> ((MigrationRefusal) r).code())
                .isEqualTo(MigrationRefusalCode.REQUEST_INVALID);
        assertThatThrownBy(() -> batches.rows(id, null, "NO_SUCH_RULE", 0, 10)).extracting(r -> ((MigrationRefusal) r).code())
                .isEqualTo(MigrationRefusalCode.REQUEST_INVALID);
        assertThatThrownBy(() -> batches.rows(id, null, null, 0, 101)).extracting(r -> ((MigrationRefusal) r).code())
                .isEqualTo(MigrationRefusalCode.PAGE_INVALID);
    }

    @Test
    @DisplayName("the batches, newest first, a page at a time; one not on record is BATCH_NOT_FOUND")
    void pages() {
        long id = registerCsv().batch().id();
        batches.register("member-export-faults.txt", LegacyFixtures.FIXED_WIDTH_MAPPING, "2026-08-31", null,
                MigrationFixture.fixedWidth());

        assertThat(batches.page(0, 1).total()).isEqualTo(2);
        assertThat(batches.page(0, 1).items().get(0).format()).isEqualTo("FIXED_WIDTH");
        assertThat(batches.page(1, 1).items().get(0).id()).isEqualTo(id);
        assertThatThrownBy(() -> batches.find(id + 1000)).extracting(r -> ((MigrationRefusal) r).code())
                .isEqualTo(MigrationRefusalCode.BATCH_NOT_FOUND);
    }
}
