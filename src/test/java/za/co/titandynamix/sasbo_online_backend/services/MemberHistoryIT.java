package za.co.titandynamix.sasbo_online_backend.services;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import za.co.titandynamix.sasbo_online_backend.dto.member.MemberHistoryPageDto;
import za.co.titandynamix.sasbo_online_backend.entity.member.Member;
import za.co.titandynamix.sasbo_online_backend.exception.MonthEndRefusal;
import za.co.titandynamix.sasbo_online_backend.exception.MonthEndRefusalCode;
import za.co.titandynamix.sasbo_online_backend.fixtures.MemberFixture;
import za.co.titandynamix.sasbo_online_backend.repository.MemberRepository;
import za.co.titandynamix.sasbo_online_backend.support.AbstractWritingIT;
import za.co.titandynamix.sasbo_online_backend.support.DatabaseReset;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * A member's history on PostgreSQL (month end 03.5, 03.6): written INSIDE each save's own transaction -- a save
 * rolled back leaves no row; append-only by the database's own trigger, proved by name -- and the lane's reset still
 * empties it (BR64); read back a page at a time, the field changes and the status changes merged by time.
 */
@DisplayName("A member's history, on PostgreSQL")
class MemberHistoryIT extends AbstractWritingIT {

    @Autowired
    private MemberService members;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private MemberHistoryService history;

    @Autowired
    private PlatformTransactionManager transactions;

    private long rows(Integer memberId) {
        return jdbc.queryForObject("SELECT count(*) FROM member_changes WHERE member_id = ?", Long.class, memberId);
    }

    private static Member edit(String surname, String phone) {
        Member edit = new Member();
        edit.setSurname(surname);
        edit.setCellTel(phone);
        return edit;
    }

    @Test
    @DisplayName("a capture is one row; a PUT of two fields is two rows under one save; a save of nothing writes nothing")
    void theRowsASaveWrites() {
        Member captured = members.create(MemberFixture.aMember());
        assertThat(rows(captured.getId())).isEqualTo(1);

        members.update(captured.getId(), edit("Changed", "+27 60 555 0009"));
        List<String> saves = jdbc.queryForList("SELECT DISTINCT save_id FROM member_changes WHERE member_id = ? "
                + "AND field <> 'captured'", String.class, captured.getId());
        assertThat(rows(captured.getId())).isEqualTo(3);
        assertThat(saves).as("the two rows of one save share its id").hasSize(1);

        members.update(captured.getId(), edit("Changed", "+27 60 555 0009"));
        assertThat(rows(captured.getId())).as("the same values again change nothing").isEqualTo(3);

        members.updateMonthlySubscription(captured.getId(), new BigDecimal("180.00"));
        assertThat(jdbc.queryForObject("SELECT new_value FROM member_changes WHERE member_id = ? AND field = "
                + "'monthlySubscription'", String.class, captured.getId())).isEqualTo("180.00");
    }

    @Test
    @DisplayName("🔒 every history row is written in the transaction of its save -- a save rolled back leaves none, on each write method")
    void aSaveRolledBackLeavesNoRow() {
        Integer id = memberRepository.save(MemberFixture.aMember()).getId();
        TransactionTemplate outer = new TransactionTemplate(transactions);

        outer.executeWithoutResult(status -> {
            members.update(id, edit("Rolled", "+27 60 555 0008"));
            status.setRollbackOnly();
        });
        outer.executeWithoutResult(status -> {
            members.updateMonthlySubscription(id, new BigDecimal("190.00"));
            status.setRollbackOnly();
        });
        outer.executeWithoutResult(status -> {
            Member banking = new Member();
            banking.setBankName("INVENTED BANK");
            banking.setPaymentType("Payroll");
            members.updateBanking(id, banking);
            status.setRollbackOnly();
        });
        outer.executeWithoutResult(status -> {
            members.create(MemberFixture.aMember());
            status.setRollbackOnly();
        });

        assertThat(jdbc.queryForObject("SELECT count(*) FROM member_changes", Long.class)).isZero();
    }

    @Test
    @DisplayName("🔒 MD46: the table is append-only by its own trigger -- an UPDATE or a DELETE of a row is refused, by name")
    void appendOnly() {
        Member captured = members.create(MemberFixture.aMember());

        assertThatThrownBy(() -> jdbc.update("UPDATE member_changes SET new_value = 'rewritten' WHERE member_id = ?",
                captured.getId())).hasMessageContaining("append-only");
        assertThatThrownBy(() -> jdbc.update("DELETE FROM member_changes WHERE member_id = ?", captured.getId()))
                .hasMessageContaining("append-only");
        assertThat(jdbc.queryForList("SELECT tgname FROM pg_trigger WHERE tgrelid = 'member_changes'::regclass",
                String.class)).contains("member_changes_append_only");
        assertThat(jdbc.queryForList("SELECT conname FROM pg_constraint WHERE conrelid = 'member_changes'::regclass",
                String.class)).contains("member_changes_member_fk");
    }

    @Test
    @DisplayName("🔒 BR64: the twin -- the lane's reset still empties it, because a TRUNCATE is not a row")
    void theResetStillEmptiesIt() {
        members.create(MemberFixture.aMember());

        DatabaseReset.reset(jdbc);

        assertThat(jdbc.queryForObject("SELECT count(*) FROM member_changes", Long.class)).isZero();
    }

    @Test
    @DisplayName("03.6: the history a page at a time, newest first -- the field changes and the status changes merged by time")
    void thePage() {
        Member captured = members.create(MemberFixture.aMember());
        jdbc.update("INSERT INTO member_status_changes (member_id, from_status, to_status, reason, changed_by, changed_at) "
                + "VALUES (?, 'Active', 'Active', 'REINSTATED', 'system', now() + interval '1 hour')", captured.getId());

        MemberHistoryPageDto page = history.page(captured.getId(), 0, 50);

        assertThat(page.content()).extracting(MemberHistoryPageDto.Entry::kind).containsExactly("STATUS", "FIELD");
        assertThat(page.content().get(1).what()).isEqualTo("captured");
        assertThat(page.totalElements()).isEqualTo(2);
        assertThatThrownBy(() -> history.page(captured.getId(), 5, 50))
                .isInstanceOfSatisfying(MonthEndRefusal.class,
                        refusal -> assertThat(refusal.code()).isEqualTo(MonthEndRefusalCode.HISTORY_PAGE_INVALID));
        assertThatThrownBy(() -> history.page(captured.getId() + 1000, 0, 50))
                .isInstanceOfSatisfying(MonthEndRefusal.class,
                        refusal -> assertThat(refusal.code()).isEqualTo(MonthEndRefusalCode.MEMBER_NOT_FOUND));
    }
}
