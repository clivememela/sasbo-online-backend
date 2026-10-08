package za.co.titandynamix.sasbo_online_backend.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import za.co.titandynamix.sasbo_online_backend.dto.migration.MigrationBatchDto;
import za.co.titandynamix.sasbo_online_backend.exception.MigrationRefusal;
import za.co.titandynamix.sasbo_online_backend.exception.MigrationRefusalCode;
import za.co.titandynamix.sasbo_online_backend.legacy.LegacyFixtures;
import za.co.titandynamix.sasbo_online_backend.repository.MemberRepository;
import za.co.titandynamix.sasbo_online_backend.repository.MigrationCommitRepository;
import za.co.titandynamix.sasbo_online_backend.support.AbstractSwitchedOnIT;
import za.co.titandynamix.sasbo_online_backend.support.MigrationFixture;
import za.co.titandynamix.sasbo_online_backend.utils.ReferenceListSeeder;

import java.sql.Timestamp;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The move and its undo, on PostgreSQL: the rows that passed the check become members through capture's columns, each
 * linked to its line; a deleted or suspended member is given SASBO's code as month end gives it; the capture sequence
 * is moved past every number written; an interrupted move resumes from its cursor and writes no row twice; an undo
 * removes exactly the members the move created -- and is refused, nothing done, while any of them has been touched.
 */
@DisplayName("Moving and undoing a batch, on PostgreSQL")
class MigrationCommitsIT extends AbstractSwitchedOnIT {

    /** The CSV fixture's rows that move: its valid rows and those with warnings. */
    private static final int MOVED = MigrationFixture.CSV_VALID + MigrationFixture.CSV_WARNED;

    @Autowired
    private MigrationBatches batches;

    @Autowired
    private MigrationValidation validation;

    @Autowired
    private MigrationCommits commits;

    @Autowired
    private MigrationCommitRepository writes;

    @Autowired
    private ReferenceListSeeder lists;

    @Autowired
    private MemberRepository members;

    private Integer onRecord;

    @BeforeEach
    void theLists() {
        onRecord = MigrationFixture.prepare(jdbc, lists, members);
    }

    private long checked() {
        long id = batches.register("member-export-faults.csv", LegacyFixtures.CSV_MAPPING, "2026-08-31", null,
                MigrationFixture.csv()).batch().id();
        validation.validate(id, null);
        return id;
    }

    private long moved() {
        long id = checked();
        commits.commit(id, true);
        return id;
    }

    private long count(String sql, Object... args) {
        return jdbc.queryForObject(sql, Long.class, args);
    }

    @Test
    @DisplayName("🔒 COMMIT_HAS_ERRORS: a batch with errors moves only when asked to hold them back -- an informed choice")
    void errorsHeldBackOnlyWhenAsked() {
        long id = checked();

        assertThatThrownBy(() -> commits.commit(id, false)).isInstanceOf(MigrationRefusal.class)
                .extracting(r -> ((MigrationRefusal) r).code()).isEqualTo(MigrationRefusalCode.COMMIT_HAS_ERRORS);
        assertThat(count("SELECT COUNT(*) FROM members")).as("the member on record alone").isEqualTo(1);
    }

    @Test
    @DisplayName("the move: every row that passed becomes a member through capture's columns, each linked to its line")
    void moves() {
        long id = checked();

        MigrationBatchDto batch = commits.commit(id, true);

        assertThat(batch.status()).isEqualTo("COMMITTED");
        assertThat(batch.rowsCommitted()).isEqualTo(MOVED);
        assertThat(batch.committedBy()).isEqualTo("system");
        assertThat(count("SELECT COUNT(*) FROM members")).isEqualTo(MOVED + 1);
        assertThat(count("SELECT COUNT(*) FROM migration_member_links WHERE batch_id = ?", id)).isEqualTo(MOVED);
        assertThat(count("SELECT COUNT(*) FROM migration_member_links l JOIN migration_rows r ON r.id = l.row_id"
                + " WHERE l.batch_id = ? AND r.outcome = 'ERROR'", id)).as("no held-back row moved").isZero();
        Map<String, Object> zoe = jdbc.queryForMap("SELECT m.* FROM members m JOIN migration_member_links l ON l.member_id = m.id"
                + " JOIN migration_rows r ON r.id = l.row_id WHERE r.row_number = 3");
        assertThat(zoe.get("membership_number")).isEqualTo("M-700003");
        assertThat(zoe.get("first_names")).isEqualTo("Zoë");
        assertThat(zoe.get("status")).isEqualTo("Active");
        assertThat(zoe.get("payment_type")).isEqualTo("Debit Order");
        assertThat(zoe.get("account_type")).isEqualTo("Savings");
        assertThat(zoe.get("identity_type")).isEqualTo("I.D. Number");
        assertThat(zoe.get("cell_tel")).isEqualTo("+27820000003");
        assertThat(zoe.get("workplace_id")).isEqualTo(count("SELECT id FROM workplaces WHERE ra_number = 990002"));
        assertThat(zoe.get("created_at")).isEqualTo(zoe.get("updated_at"));
        assertThat(count("SELECT COUNT(*) FROM member_changes")).as("a move writes no history row").isZero();
    }

    @Test
    @DisplayName("a deleted member leaves with SASBO's code on the day they left; a suspended one is suspended with theirs")
    void statusChanges() {
        long id = moved();

        List<Map<String, Object>> changes = jdbc.queryForList("""
                SELECT r.row_number, s.from_status, s.to_status, s.reason, d.sasbo_code AS deletion, sc.code AS suspension,
                  s.changed_at, s.note, l.status_change_id = s.id AS linked
                FROM member_status_changes s
                JOIN migration_member_links l ON l.member_id = s.member_id
                JOIN migration_rows r ON r.id = l.row_id
                LEFT JOIN deletion_codes d ON d.id = s.deletion_code_id
                LEFT JOIN suspension_codes sc ON sc.id = s.suspension_code_id
                WHERE l.batch_id = ? ORDER BY r.row_number
                """, id);
        assertThat(changes).extracting(row -> row.get("row_number")).containsExactly(4, 5, 42);
        assertThat(changes.get(0)).containsEntry("to_status", "Suspended").containsEntry("reason", "SUSPENSION_CODE")
                .containsEntry("suspension", 11).containsEntry("from_status", "Active").containsEntry("linked", true);
        assertThat(changes.get(1)).containsEntry("to_status", "Resigned").containsEntry("reason", "DELETION_CODE")
                .containsEntry("deletion", 2);
        assertThat(changes.get(1).get("note").toString()).isEqualTo("Moved from the old system: batch " + id + ", line 5");
        assertThat(((Timestamp) changes.get(1).get("changed_at")).toInstant().toString())
                .as("the start of 15 March 2026 in SASBO's zone").isEqualTo("2026-03-14T22:00:00Z");
        assertThat(changes.get(2)).containsEntry("suspension", 13);
        assertThat(((Timestamp) changes.get(2).get("changed_at")).toInstant().toString())
                .as("no date in the file: the date the export represents").isEqualTo("2026-08-30T22:00:00Z");
    }

    @Test
    @DisplayName("🔒 the capture sequence is moved past every number the move wrote: a capture after it never takes one")
    void sequenceMovedPast() {
        long id = moved();

        long highest = count("SELECT MAX(CAST(SUBSTRING(m.membership_number FROM 3) AS BIGINT)) FROM members m"
                + " JOIN migration_member_links l ON l.member_id = m.id WHERE l.batch_id = ?", id);
        assertThat(highest).isEqualTo(700046L);
        assertThat(writes.nextMembershipNumber()).isGreaterThan(highest);
    }

    @Test
    @DisplayName("🔒 an interrupted move resumes from its cursor: no row written twice, none left out")
    void resumes() {
        long id = checked();
        String actor = commits.beginCommit(id, true);

        commits.runCommit(id, actor, 5, chunk -> {
            if (chunk == 2) {
                throw new IllegalStateException("the instance stopped");
            }
        });

        Map<String, Object> interrupted = jdbc.queryForMap("SELECT status, job_state, rows_committed, commit_cursor,"
                + " job_failure FROM migration_batches WHERE id = ?", id);
        assertThat(interrupted).containsEntry("status", "COMMITTING").containsEntry("job_state", "FAILED")
                .containsEntry("rows_committed", 10).containsEntry("job_failure", MigrationCommits.COMMIT_FAILED);
        assertThat(count("SELECT COUNT(*) FROM migration_member_links WHERE batch_id = ?", id)).isEqualTo(10);

        MigrationBatchDto resumed = commits.commit(id, true);

        assertThat(resumed.status()).isEqualTo("COMMITTED");
        assertThat(resumed.rowsCommitted()).isEqualTo(MOVED);
        assertThat(count("SELECT COUNT(*) FROM migration_member_links WHERE batch_id = ?", id)).isEqualTo(MOVED);
        assertThat(count("SELECT COUNT(DISTINCT row_id) FROM migration_member_links WHERE batch_id = ?", id)).isEqualTo(MOVED);
        assertThat(count("SELECT COUNT(*) FROM members")).isEqualTo(MOVED + 1);
    }

    @Test
    @DisplayName("🔒 a member captured since the check holds its row back when the move begins -- never a duplicate member")
    void capturedSinceTheCheck() {
        long id = checked();
        String rowTwoId = jdbc.queryForObject("SELECT identity_key FROM migration_rows WHERE batch_id = ? AND row_number = 2",
                String.class, id);
        jdbc.update("UPDATE members SET identity_number = ? WHERE id = ?", rowTwoId, onRecord);

        MigrationBatchDto batch = commits.commit(id, true);

        assertThat(batch.rowsCommitted()).isEqualTo(MOVED - 1);
        assertThat(jdbc.queryForObject("SELECT error_codes FROM migration_rows WHERE batch_id = ? AND row_number = 2",
                String.class, id)).isEqualTo("ID_NUMBER_ALREADY_MEMBER@identityNumber");
    }

    @Test
    @DisplayName("the undo removes exactly the members the move created, their status changes and links; the rows stay")
    void undoes() {
        long id = moved();

        MigrationBatchDto undone = commits.rollback(id, "The first rehearsal: the old system's file was re-exported.");

        assertThat(undone.status()).isEqualTo("ROLLED_BACK");
        assertThat(undone.rolledBackBy()).isEqualTo("system");
        assertThat(undone.rollbackReason()).isEqualTo("The first rehearsal: the old system's file was re-exported.");
        assertThat(undone.rowsCommitted()).isZero();
        assertThat(count("SELECT COUNT(*) FROM members")).as("the member on record alone").isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM member_status_changes")).isZero();
        assertThat(count("SELECT COUNT(*) FROM migration_member_links")).isZero();
        assertThat(count("SELECT COUNT(*) FROM migration_rows WHERE batch_id = ?", id)).isEqualTo(MigrationFixture.CSV_ROWS);

        long again = batches.register("member-export-faults.csv", LegacyFixtures.CSV_MAPPING, "2026-08-31", null,
                MigrationFixture.csv()).batch().id();
        assertThat(again).as("a file undone may be registered again").isNotEqualTo(id);
    }

    @Test
    @DisplayName("🔒 ROLLBACK_MEMBERS_CHANGED: one member edited since -- nothing undone; put back, the undo goes ahead")
    void refusedWhenEdited() {
        long id = moved();
        Integer member = jdbc.queryForObject("SELECT member_id FROM migration_member_links WHERE batch_id = ? LIMIT 1",
                Integer.class, id);
        jdbc.update("UPDATE members SET updated_at = updated_at + INTERVAL '1 second' WHERE id = ?", member);

        assertThatThrownBy(() -> commits.rollback(id, "a rehearsal")).isInstanceOf(MigrationRefusal.class)
                .satisfies(refused -> {
                    assertThat(((MigrationRefusal) refused).code()).isEqualTo(MigrationRefusalCode.ROLLBACK_MEMBERS_CHANGED);
                    assertThat(((MigrationRefusal) refused).details()).containsEntry("changedMembers", "1");
                });
        assertThat(count("SELECT COUNT(*) FROM members")).isEqualTo(MOVED + 1);

        jdbc.update("UPDATE members SET updated_at = created_at WHERE id = ?", member);
        assertThat(commits.rollback(id, "a rehearsal").status()).isEqualTo("ROLLED_BACK");
    }

    @Test
    @DisplayName("🔒 refused too for a member with a history row since, or another status change, or taken into a debit order run")
    void refusedWhenUsed() {
        long id = moved();
        Integer member = jdbc.queryForObject("SELECT member_id FROM migration_member_links WHERE batch_id = ? LIMIT 1",
                Integer.class, id);
        jdbc.update("INSERT INTO member_status_changes (member_id, from_status, to_status, reason, changed_by, changed_at)"
                + " VALUES (?, 'Active', 'Pending', 'CAPTURED', 'someone', now())", member);
        assertThat(writes.changedSince(id)).isEqualTo(1);
        jdbc.update("DELETE FROM member_status_changes WHERE reason = 'CAPTURED'");
        assertThat(writes.changedSince(id)).isZero();

        jdbc.update("INSERT INTO member_changes (member_id, save_id, field, old_value, new_value, changed_by, changed_at)"
                + " VALUES (?, 'save-1', 'cellTel', '****0001', '****0002', 'someone', now())", member);
        assertThatThrownBy(() -> commits.rollback(id, "a rehearsal")).extracting(r -> ((MigrationRefusal) r).code())
                .isEqualTo(MigrationRefusalCode.ROLLBACK_MEMBERS_CHANGED);
        assertThat(count("SELECT COUNT(*) FROM members")).isEqualTo(MOVED + 1);
    }

    @Test
    @DisplayName("refused: a move of a batch not checked, an undo of a batch not moved")
    void states() {
        long id = batches.register("member-export-faults.csv", LegacyFixtures.CSV_MAPPING, "2026-08-31", null,
                MigrationFixture.csv()).batch().id();

        assertThatThrownBy(() -> commits.commit(id, true)).extracting(r -> ((MigrationRefusal) r).code())
                .isEqualTo(MigrationRefusalCode.BATCH_NOT_IN_STATE);
        assertThatThrownBy(() -> commits.rollback(id, "nothing to undo")).extracting(r -> ((MigrationRefusal) r).code())
                .isEqualTo(MigrationRefusalCode.BATCH_NOT_IN_STATE);
    }
}
