package za.co.titandynamix.sasbo_online_backend.services.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import za.co.titandynamix.sasbo_online_backend.dto.member.MemberStatusRequest;
import za.co.titandynamix.sasbo_online_backend.exception.MonthEndRefusal;
import za.co.titandynamix.sasbo_online_backend.exception.MonthEndRefusalCode;
import za.co.titandynamix.sasbo_online_backend.fixtures.MemberFixture;
import za.co.titandynamix.sasbo_online_backend.repository.MemberRepository;
import za.co.titandynamix.sasbo_online_backend.services.MemberStatusService;
import za.co.titandynamix.sasbo_online_backend.support.AbstractWritingIT;
import za.co.titandynamix.sasbo_online_backend.utils.ReferenceListSeeder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A member's status changed on PostgreSQL (month end 03.1): two changes on one view, released together by a latch,
 * and ONE is written -- the member's row is locked for the change, and the second finds the status the first wrote
 * (MV-5). What a lock does is the database's, so this is where it is proved.
 */
@DisplayName("A status change, on PostgreSQL")
class MemberStatusChangeIT extends AbstractWritingIT {

    @Autowired
    private MemberStatusService statuses;

    @Autowired
    private MemberRepository members;

    @Autowired
    private ReferenceListSeeder lists;

    private Integer memberId;

    @BeforeEach
    void anActiveMember() {
        lists.seed();
        var member = MemberFixture.aMember();
        member.setStatus("Active");
        memberId = members.save(member).getId();
    }

    @Test
    @DisplayName("🔒 MV-5: two changes on one view released together -- one is written, the other is STATUS_CHANGED_SINCE")
    void twoChangesAtOnce() throws Exception {
        CountDownLatch go = new CountDownLatch(1);
        List<String> outcomes = Collections.synchronizedList(new ArrayList<>());
        Thread resign = new Thread(() -> change(go, outcomes, new MemberStatusRequest("Resigned", "Active",
                "DELETION_CODE", 2, null, null, null)), "month-end-resign");
        Thread suspend = new Thread(() -> change(go, outcomes, new MemberStatusRequest("Suspended", "Active",
                "SUSPENSION_CODE", null, 11, null, null)), "month-end-suspend");
        resign.start();
        suspend.start();
        go.countDown();
        resign.join(30_000);
        suspend.join(30_000);

        assertThat(outcomes).containsExactlyInAnyOrder("written", MonthEndRefusalCode.STATUS_CHANGED_SINCE.name());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM member_status_changes WHERE member_id = ?", Long.class,
                memberId)).isEqualTo(1L);
    }

    @Test
    @DisplayName("ME-3: the same change asked twice is written once")
    void theSameChangeTwice() {
        MemberStatusRequest resign = new MemberStatusRequest("Resigned", "Active", "DELETION_CODE", 2, null, null, null);

        assertThat(statuses.change(memberId, resign).change()).isNotNull();
        assertThat(statuses.change(memberId, resign).change()).as("a retry after a timeout").isNull();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM member_status_changes WHERE member_id = ?", Long.class,
                memberId)).isEqualTo(1L);
    }

    @Autowired
    private za.co.titandynamix.sasbo_online_backend.repository.MemberHistoryRepository history;

    @Test
    @DisplayName("04.5: a resignation with the reason given under code 2 -- that reason's row written, SASBO's number answered and in the history")
    void aReasonUnderTheCode() {
        String reason = jdbc.queryForObject("SELECT d.name FROM deletion_codes d JOIN deletion_codes p ON p.id = "
                + "d.parent_id WHERE p.sasbo_code = 2 ORDER BY d.id LIMIT 1", String.class);

        var answer = statuses.change(memberId, new MemberStatusRequest("Resigned", "Active", "DELETION_CODE", 2, null,
                null, reason.toUpperCase(java.util.Locale.ROOT)));

        assertThat(answer.change().deletionCode()).isEqualTo(2);
        assertThat(answer.change().reasonUnderCode()).isEqualTo(reason);
        Long row = jdbc.queryForObject("SELECT deletion_code_id FROM member_status_changes WHERE member_id = ?",
                Long.class, memberId);
        assertThat(jdbc.queryForObject("SELECT name FROM deletion_codes WHERE id = ?", String.class, row))
                .as("the reason's own row").isEqualTo(reason);
        assertThat(history.page(memberId, 0, 10).entries()).filteredOn(e -> "STATUS".equals(e.kind()))
                .extracting(e -> e.code()).as("SASBO's number, the code it is under (RC7)").containsExactly(2);
    }

    private void change(CountDownLatch go, List<String> outcomes, MemberStatusRequest request) {
        try {
            go.await(10, TimeUnit.SECONDS);
            statuses.change(memberId, request);
            outcomes.add("written");
        } catch (MonthEndRefusal refusal) {
            outcomes.add(refusal.code().name());
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }
}
