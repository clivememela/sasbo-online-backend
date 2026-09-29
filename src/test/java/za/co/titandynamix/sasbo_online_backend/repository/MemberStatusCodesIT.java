package za.co.titandynamix.sasbo_online_backend.repository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import za.co.titandynamix.sasbo_online_backend.enums.MemberStatusReason;
import za.co.titandynamix.sasbo_online_backend.fixtures.MemberFixture;
import za.co.titandynamix.sasbo_online_backend.support.AbstractWritingIT;
import za.co.titandynamix.sasbo_online_backend.utils.ReferenceListSeeder;

import java.sql.Timestamp;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * V55 on the real chain (month end 03.2): the reasons widened in one statement, a code carried exactly when its
 * reason says so -- ACB's two reasons carrying none -- and the index on the month's changes, each BY NAME in the
 * catalogue (MR-ROB-21). ACB's count of terminations reads SUBS_IN_ARREARS and nothing else (BR39).
 */
@DisplayName("A status change's codes on PostgreSQL (V55)")
class MemberStatusCodesIT extends AbstractWritingIT {

    @Autowired
    private MemberRepository members;

    @Autowired
    private MemberStatusChangeRepository changes;

    @Autowired
    private ReferenceListSeeder lists;

    private Integer memberId;

    @BeforeEach
    void aMemberAndTheCodes() {
        lists.seed();
        memberId = members.save(MemberFixture.aMember()).getId();
    }

    private void insert(String reason, String deletionCode, String suspensionCode) {
        jdbc.update("INSERT INTO member_status_changes (member_id, from_status, to_status, reason, changed_by, "
                + "changed_at, deletion_code_id, suspension_code_id) VALUES (?, 'Active', 'Resigned', ?, 'system', ?, "
                + (deletionCode == null ? "NULL" : "(SELECT id FROM deletion_codes WHERE sasbo_code = " + deletionCode + ")")
                + ", "
                + (suspensionCode == null ? "NULL" : "(SELECT id FROM suspension_codes WHERE code = " + suspensionCode + ")")
                + ")", memberId, reason, Timestamp.from(Instant.parse("2026-08-14T10:00:00Z")));
    }

    @Test
    @DisplayName("🔒 MR-ROB-21: the widened reason, the code's fit and the index on changed_at, by name")
    void theConstraintsByName() {
        assertThat(jdbc.queryForList("SELECT conname FROM pg_constraint c JOIN pg_class t ON t.oid = c.conrelid "
                + "WHERE t.relname = 'member_status_changes'", String.class))
                .contains("member_status_changes_reason_known", "member_status_changes_code_fits",
                        "member_status_changes_deletion_code_fk", "member_status_changes_suspension_code_fk");
        assertThat(jdbc.queryForList("SELECT indexname FROM pg_indexes WHERE tablename = 'member_status_changes'",
                String.class)).contains("member_status_changes_changed_at", "member_status_changes_member");
    }

    @Test
    @DisplayName("a leaving carries its deletion code, a suspension its suspension code -- and nothing else is accepted")
    void aCodeExactlyWhenItsReasonSaysSo() {
        insert("DELETION_CODE", "2", null);
        insert("SUSPENSION_CODE", null, "11");
        insert("REINSTATED", null, null);
        insert("CAPTURED", null, null);

        assertThatThrownBy(() -> insert("DELETION_CODE", null, null)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insert("SUSPENSION_CODE", "2", null)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insert("SUBS_IN_ARREARS", "5", null))
                .as("ACB's reasons carry no code: how they read in SASBO's words is data, and a proposal (MM58)")
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insert("MOVED_BY_MAGIC", null, null)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("🔒 BR39: ACB's count of terminations reads SUBS_IN_ARREARS alone -- a leaver given deletion code 5 by hand is not in it")
    void acbsCountIsUnmoved() {
        insert("SUBS_IN_ARREARS", null, null);
        insert("DELETION_CODE", "5", null);

        assertThat(changes.countByReason(MemberStatusReason.SUBS_IN_ARREARS)).isEqualTo(1);
    }
}
