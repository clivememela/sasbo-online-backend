package za.co.titandynamix.sasbo_online_backend.migration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import za.co.titandynamix.sasbo_online_backend.support.AbstractWritingIT;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * V80 and V81 hold their own rules, asserted BY NAME on PostgreSQL -- the fast lane's H2 builds these tables from the
 * entities and has none of them: a status, format, job or outcome nobody knows; a file hashed only once read; the same
 * file twice unless undone; one job and one file being read at a time; a row once per line; an outcome that fits its
 * problems; a member linked once; a month's figure once, its key fitting its dimension.
 */
@DisplayName("The migration's tables hold their own rules, on PostgreSQL")
class MigrationTablesIT extends AbstractWritingIT {

    private long batch(String status, String sha256, String jobState) {
        return jdbc.queryForObject("""
                INSERT INTO migration_batches (source_file_name, source_sha256, source_bytes, source_format, mapping_key,
                  mapping_snapshot, as_at, status, rows_read, rows_valid, rows_warned, rows_invalid, rows_committed,
                  commit_cursor, registered_by, registered_at, job_kind, job_state, job_done, job_total)
                VALUES ('f.csv', ?, 10, 'CSV', 'm', '{}', DATE '2026-08-31', ?, 0, 0, 0, 0, 0, 0, 'system', now(),
                  ?, ?, 0, 0)
                RETURNING id
                """, Long.class, sha256, status, jobState == null ? null : "VALIDATE", jobState);
    }

    private static String hash(char c) {
        return String.valueOf(c).repeat(64);
    }

    @Test
    @DisplayName("migration_batches: status, format and job known; a hash once read and only then; failures say why")
    void batchChecks() {
        long id = batch("DRAFT", hash('a'), null);

        assertThatThrownBy(() -> jdbc.update("UPDATE migration_batches SET status = 'LOST' WHERE id = ?", id))
                .hasMessageContaining("migration_batches_status_known");
        assertThatThrownBy(() -> jdbc.update("UPDATE migration_batches SET source_format = 'PDF' WHERE id = ?", id))
                .hasMessageContaining("migration_batches_format_known");
        assertThatThrownBy(() -> jdbc.update("UPDATE migration_batches SET source_sha256 = NULL WHERE id = ?", id))
                .hasMessageContaining("migration_batches_hashed_once_read");
        assertThatThrownBy(() -> jdbc.update("UPDATE migration_batches SET source_sha256 = 'ABC' WHERE id = ?", id))
                .hasMessageContaining("migration_batches_sha256_shape");
        assertThatThrownBy(() -> jdbc.update("UPDATE migration_batches SET rows_valid = 5 WHERE id = ?", id))
                .hasMessageContaining("migration_batches_outcomes_within_read");
        assertThatThrownBy(() -> jdbc.update("UPDATE migration_batches SET job_kind = 'VALIDATE' WHERE id = ?", id))
                .hasMessageContaining("migration_batches_job_whole");
        assertThatThrownBy(() -> jdbc.update("UPDATE migration_batches SET job_kind = 'VALIDATE', job_state = 'FAILED'"
                + " WHERE id = ?", id)).hasMessageContaining("migration_batches_job_failed_says_why");
        assertThatThrownBy(() -> jdbc.update("UPDATE migration_batches SET status = 'ROLLED_BACK' WHERE id = ?", id))
                .hasMessageContaining("migration_batches_rolled_back_says_why");
    }

    @Test
    @DisplayName("🔒 migration_batches_file_once: the same file twice is refused -- unless the first was undone")
    void fileOnce() {
        batch("DRAFT", hash('b'), null);

        assertThatThrownBy(() -> batch("DRAFT", hash('b'), null)).hasMessageContaining("migration_batches_file_once");
        jdbc.update("UPDATE migration_batches SET status = 'ROLLED_BACK', rolled_back_by = 'system',"
                + " rolled_back_at = now(), rollback_reason = 'rehearsal'");
        assertThat(batch("DRAFT", hash('b'), null)).isPositive();
    }

    @Test
    @DisplayName("🔒 migration_batches_one_job and migration_batches_one_receiving: no queue -- one job, one file read at a time")
    void oneAtATime() {
        batch("DRAFT", hash('c'), "RUNNING");
        assertThatThrownBy(() -> batch("DRAFT", hash('d'), "RUNNING")).hasMessageContaining("migration_batches_one_job");

        batch("RECEIVING", null, null);
        assertThatThrownBy(() -> batch("RECEIVING", null, null)).hasMessageContaining("migration_batches_one_receiving");
    }

    @Test
    @DisplayName("migration_rows: once per line, a positive line, an outcome that fits its problems; deleted with its batch")
    void rows() {
        long id = batch("DRAFT", hash('e'), null);
        jdbc.update("INSERT INTO migration_rows (batch_id, row_number, raw_json) VALUES (?, 2, '{}')", id);

        assertThatThrownBy(() -> jdbc.update("INSERT INTO migration_rows (batch_id, row_number) VALUES (?, 2)", id))
                .hasMessageContaining("migration_rows_one_a_line");
        assertThatThrownBy(() -> jdbc.update("INSERT INTO migration_rows (batch_id, row_number) VALUES (?, 0)", id))
                .hasMessageContaining("migration_rows_row_number_positive");
        assertThatThrownBy(() -> jdbc.update("UPDATE migration_rows SET outcome = 'ERROR' WHERE batch_id = ?", id))
                .hasMessageContaining("migration_rows_outcome_fits");
        assertThatThrownBy(() -> jdbc.update("UPDATE migration_rows SET outcome = 'VALID', warning_codes = 'ROW_SHORT'"
                + " WHERE batch_id = ?", id)).hasMessageContaining("migration_rows_outcome_fits");
        assertThatThrownBy(() -> jdbc.update("UPDATE migration_rows SET status_kind = 'X' WHERE batch_id = ?", id))
                .hasMessageContaining("migration_rows_kind_known");
        jdbc.update("UPDATE migration_rows SET outcome = 'WARNING', warning_codes = 'ROW_SHORT' WHERE batch_id = ?", id);

        jdbc.update("DELETE FROM migration_batches WHERE id = ?", id);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM migration_rows", Long.class)).isZero();
    }

    @Test
    @DisplayName("migration_month_totals: a month's first day, a dimension and measure known, the key fitting, each figure once")
    void totals() {
        String insert = "INSERT INTO migration_month_totals (month_start, dimension, item_key, measure, legacy_value,"
                + " entered_by, entered_at) VALUES (?::date, ?, ?, ?, ?, 'system', now())";
        jdbc.update(insert, "2026-08-01", "TOTAL", "", "MEMBERS", 65_569);

        assertThatThrownBy(() -> jdbc.update(insert, "2026-08-01", "TOTAL", "", "MEMBERS", 1))
                .hasMessageContaining("migration_month_totals_once");
        assertThatThrownBy(() -> jdbc.update(insert, "2026-08-15", "TOTAL", "", "SUSPENDED", 1))
                .hasMessageContaining("migration_month_totals_month_is_a_month");
        assertThatThrownBy(() -> jdbc.update(insert, "2026-08-01", "PROVINCE", "G", "MEMBERS", 1))
                .hasMessageContaining("migration_month_totals_dimension_known");
        assertThatThrownBy(() -> jdbc.update(insert, "2026-08-01", "AREA", "", "MEMBERS", 1))
                .hasMessageContaining("migration_month_totals_key_fits");
        assertThatThrownBy(() -> jdbc.update(insert, "2026-08-01", "TOTAL", "D", "MEMBERS", 1))
                .hasMessageContaining("migration_month_totals_key_fits");
        assertThatThrownBy(() -> jdbc.update(insert, "2026-08-01", "AREA", "D", "MEMBERS", -1))
                .hasMessageContaining("migration_month_totals_not_negative");
    }

    @Test
    @DisplayName("migration_mappings: a key of lower-case letters, digits and hyphens, once")
    void mappings() {
        String insert = "INSERT INTO migration_mappings (mapping_key, name, definition, version, created_by, created_at,"
                + " updated_by, updated_at) VALUES (?, 'Mine', '{}', 1, 'system', now(), 'system', now())";
        jdbc.update(insert, "mine");

        assertThatThrownBy(() -> jdbc.update(insert, "mine")).hasMessageContaining("migration_mappings_key_unique");
        assertThatThrownBy(() -> jdbc.update(insert, "Has Spaces")).hasMessageContaining("migration_mappings_key_shape");
    }
}
