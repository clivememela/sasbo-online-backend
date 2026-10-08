package za.co.titandynamix.sasbo_online_backend.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import za.co.titandynamix.sasbo_online_backend.dto.migration.MigrationBatchDto;
import za.co.titandynamix.sasbo_online_backend.exception.MigrationRefusal;
import za.co.titandynamix.sasbo_online_backend.exception.MigrationRefusalCode;
import za.co.titandynamix.sasbo_online_backend.legacy.LegacyFixtures;
import za.co.titandynamix.sasbo_online_backend.legacy.rules.ReferenceIndex;
import za.co.titandynamix.sasbo_online_backend.repository.MemberRepository;
import za.co.titandynamix.sasbo_online_backend.repository.MigrationReferenceRepository;
import za.co.titandynamix.sasbo_online_backend.repository.MigrationRowRepository;
import za.co.titandynamix.sasbo_online_backend.support.AbstractSwitchedOnIT;
import za.co.titandynamix.sasbo_online_backend.support.MigrationFixture;
import za.co.titandynamix.sasbo_online_backend.utils.ReferenceListSeeder;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The check, on PostgreSQL: every row through its mapping and every rule, then -- by the check's own statements -- the
 * rows against one another and against the members on record; SASBO's lists read once; a check run again rewrites
 * every outcome; one that cannot finish leaves the batch a DRAFT.
 */
@DisplayName("Checking a batch, on PostgreSQL")
class MigrationValidationIT extends AbstractSwitchedOnIT {

    @Autowired
    private MigrationBatches batches;

    @Autowired
    private MigrationValidation validation;

    @Autowired
    private MigrationReferenceRepository reference;

    @Autowired
    private MigrationRowRepository rowRepository;

    @Autowired
    private ReferenceListSeeder lists;

    @Autowired
    private MemberRepository members;

    @BeforeEach
    void theLists() {
        MigrationFixture.prepare(jdbc, lists, members);
    }

    private long registered() {
        return batches.register("member-export-faults.csv", LegacyFixtures.CSV_MAPPING, "2026-08-31", null,
                MigrationFixture.csv()).batch().id();
    }

    private String errors(long id, int line) {
        return jdbc.queryForObject("SELECT error_codes FROM migration_rows WHERE batch_id = ? AND row_number = ?",
                String.class, id, line);
    }

    @Test
    @DisplayName("SASBO's lists are read whole: the invented workplaces with their areas and table codes, the codes by number")
    void theReferenceIndex() {
        ReferenceIndex index = reference.load();

        assertThat(index.workplaces()).containsKeys(990001, 990002, 990003, 990004);
        assertThat(index.workplaces().get(990001).areaLetter()).isEqualTo("D");
        assertThat(index.workplaces().get(990001).tableCode()).isEqualTo(13);
        assertThat(index.workplaces().get(990004).tableCode()).isNull();
        assertThat(index.tableCodes()).contains(1, 13, 24, 43);
        assertThat(index.areaLetters()).contains("A", "L", "W", "XX");
        assertThat(index.deletionCodes()).containsKeys(1, 2, 4, 5, 10);
        assertThat(index.suspensionCodes()).containsKeys(11, 13, 19);
    }

    @Test
    @DisplayName("🔒 the four checks about two rows, by statement: a number twice in the file, or already a member's -- both rows of a pair")
    void acrossRows() {
        long id = registered();

        MigrationBatchDto checked = validation.validate(id, null);

        assertThat(checked.status()).isEqualTo("VALIDATED");
        assertThat(checked.job().state()).isEqualTo("DONE");
        assertThat(checked.validatedBy()).isEqualTo("system");
        assertThat(errors(id, 47)).isEqualTo("ID_NUMBER_DUPLICATE_IN_BATCH@identityNumber");
        assertThat(errors(id, 48)).isEqualTo("ID_NUMBER_DUPLICATE_IN_BATCH@identityNumber");
        assertThat(errors(id, 49)).isEqualTo("MEMBERSHIP_NUMBER_DUPLICATE_IN_BATCH@membershipNumber");
        assertThat(errors(id, 50)).isEqualTo("MEMBERSHIP_NUMBER_DUPLICATE_IN_BATCH@membershipNumber");
        assertThat(errors(id, 51)).isEqualTo("ID_NUMBER_ALREADY_MEMBER@identityNumber");
        assertThat(errors(id, 52)).isEqualTo("MEMBERSHIP_NUMBER_ALREADY_USED@membershipNumber");
        assertThat(errors(id, 2)).isNull();
        assertThat(checked.rowsValid()).isEqualTo(MigrationFixture.CSV_VALID);
        assertThat(checked.rowsWarned()).isEqualTo(MigrationFixture.CSV_WARNED);
        assertThat(checked.rowsInvalid()).isEqualTo(MigrationFixture.CSV_INVALID);
        assertThat(rowRepository.countByOutcome(id)).containsEntry("VALID", MigrationFixture.CSV_VALID)
                .containsEntry("WARNING", MigrationFixture.CSV_WARNED).containsEntry("ERROR", MigrationFixture.CSV_INVALID);
    }

    @Test
    @DisplayName("each row's normalised fields, beside its outcome: the keys duplicates are found by, the workplace, area, table code, status")
    void normalisedColumns() {
        long id = registered();
        validation.validate(id, null);

        Map<String, Object> clean = jdbc.queryForMap("SELECT * FROM migration_rows WHERE batch_id = ? AND row_number = 3", id);
        assertThat(clean.get("outcome")).isEqualTo("VALID");
        assertThat(clean.get("membership_key")).isEqualTo("M-700003");
        assertThat(((Number) clean.get("membership_seq")).longValue()).isEqualTo(700003L);
        assertThat(clean.get("identity_key")).isNotNull();
        assertThat(clean.get("status_kind")).isEqualTo("N");
        assertThat(clean.get("area_letter")).isEqualTo("L");
        assertThat(clean.get("table_code")).isEqualTo(24);
        assertThat((String) clean.get("normalised_json")).contains("\"firstNames\":\"Zoë\"").contains("\"Debit Order\"");
        Map<String, Object> suspended = jdbc.queryForMap("SELECT * FROM migration_rows WHERE batch_id = ? AND row_number = 4", id);
        assertThat(suspended.get("status_kind")).isEqualTo("S");
        assertThat(suspended.get("suspension_code")).isEqualTo(11);
        Map<String, Object> deleted = jdbc.queryForMap("SELECT * FROM migration_rows WHERE batch_id = ? AND row_number = 5", id);
        assertThat(deleted.get("status_kind")).isEqualTo("L");
        assertThat(deleted.get("deletion_code")).isEqualTo(2);
        assertThat(deleted.get("left_on").toString()).isEqualTo("2026-03-15");
    }

    @Test
    @DisplayName("a check run again rewrites every outcome -- never adds a code twice; another mapping must read the same format")
    void checkedAgain() {
        long id = registered();
        validation.validate(id, null);
        validation.validate(id, null);

        assertThat(errors(id, 47)).isEqualTo("ID_NUMBER_DUPLICATE_IN_BATCH@identityNumber");
        assertThatThrownBy(() -> validation.validate(id, LegacyFixtures.FIXED_WIDTH_MAPPING))
                .isInstanceOf(MigrationRefusal.class).extracting(r -> ((MigrationRefusal) r).code())
                .isEqualTo(MigrationRefusalCode.MAPPING_NOT_FOR_THIS_FILE);
    }

    @Test
    @DisplayName("refused: a batch already moved, and any check while another job runs")
    void refusals() {
        long id = registered();
        jdbc.update("UPDATE migration_batches SET status = 'COMMITTED' WHERE id = ?", id);
        assertThatThrownBy(() -> validation.validate(id, null)).extracting(r -> ((MigrationRefusal) r).code())
                .isEqualTo(MigrationRefusalCode.BATCH_NOT_IN_STATE);

        long other = batches.register("member-export-faults.txt", LegacyFixtures.FIXED_WIDTH_MAPPING, "2026-08-31", null,
                MigrationFixture.fixedWidth()).batch().id();
        jdbc.update("UPDATE migration_batches SET job_kind = 'COMMIT', job_state = 'RUNNING' WHERE id = ?", id);
        assertThatThrownBy(() -> validation.validate(other, null)).extracting(r -> ((MigrationRefusal) r).code())
                .isEqualTo(MigrationRefusalCode.MIGRATION_BUSY);
    }

    @Test
    @DisplayName("a check that cannot finish says so on the batch, in a sentence of its own, and leaves it a DRAFT")
    void failed() {
        long id = registered();
        validation.validate(id, null);
        jdbc.update("UPDATE migration_batches SET mapping_snapshot = 'not a mapping' WHERE id = ?", id);

        MigrationBatchDto failed = validation.validate(id, null);

        assertThat(failed.status()).isEqualTo("DRAFT");
        assertThat(failed.job().state()).isEqualTo("FAILED");
        assertThat(failed.job().failure()).isEqualTo(MigrationValidation.FAILED);
        assertThat(failed.rowsValid()).isZero();
    }
}
