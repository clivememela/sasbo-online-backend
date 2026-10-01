package za.co.titandynamix.sasbo_online_backend.services.impl;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import za.co.titandynamix.sasbo_online_backend.dto.member.MemberStatusAnswer;
import za.co.titandynamix.sasbo_online_backend.dto.member.MemberStatusRequest;
import za.co.titandynamix.sasbo_online_backend.entity.member.Member;
import za.co.titandynamix.sasbo_online_backend.entity.member.MemberStatusChange;
import za.co.titandynamix.sasbo_online_backend.enums.MemberStatusReason;
import za.co.titandynamix.sasbo_online_backend.exception.MonthEndRefusal;
import za.co.titandynamix.sasbo_online_backend.exception.MonthEndRefusalCode;
import za.co.titandynamix.sasbo_online_backend.repository.MemberStatusChangeRepository;
import za.co.titandynamix.sasbo_online_backend.repository.SasboReferenceRepository;
import za.co.titandynamix.sasbo_online_backend.services.MemberService;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * A member's status, changed with SASBO's code for it (month end 03.1): every refusal by its code, a reason judged by
 * the status's kind and no finer (MM49), the actor and the instant the server's, and a retry that writes nothing.
 */
@DisplayName("MemberStatusServiceImpl")
class MemberStatusServiceImplTest {

    private static final Instant NOW = Instant.parse("2026-09-29T08:00:00Z");

    private final MemberService members = mock(MemberService.class);
    private final SasboReferenceRepository codes = mock(SasboReferenceRepository.class);
    private final MemberStatusChangeRepository changes = mock(MemberStatusChangeRepository.class);
    private final MemberStatusServiceImpl service = new MemberStatusServiceImpl(members, codes, changes,
            Clock.fixed(NOW, ZoneOffset.UTC));

    private static Member member(String status) {
        Member member = new Member();
        member.setId(7);
        member.setMembershipNumber("M-900001");
        member.setStatus(status);
        return member;
    }

    private static MemberStatusRequest request(String to, String expected, String reason, Integer deletion,
                                               Integer suspension, String note) {
        return new MemberStatusRequest(to, expected, reason, deletion, suspension, note, null);
    }

    /**
     * The refusal a call is answered with, by its code; none is {@code null}. Anything else thrown fails here, as an
     * assertion, naming it -- a refusal by code is what is asserted, and an exception in its place is not one (DL-64).
     */
    private static MonthEndRefusalCode codeOf(Runnable call) {
        try {
            call.run();
        } catch (MonthEndRefusal refusal) {
            return refusal.code();
        } catch (RuntimeException other) {
            throw new AssertionError("expected a refusal by code, and " + other.getClass().getSimpleName()
                    + " was thrown instead", other);
        }
        return null;
    }

    @Test
    @DisplayName("PA-4, PA-12: a leaving is written with SASBO's deletion code, by the server's actor -- `system` until sign-in -- and clock")
    void aLeavingWithItsCode() {
        when(codes.deletionCode(2)).thenReturn(Optional.of(new SasboReferenceRepository.Code(12L, 2, "Resigned Society")));
        when(codes.deletionCodeById(12L)).thenReturn(Optional.of(new SasboReferenceRepository.Code(12L, 2, "Resigned Society")));
        MemberStatusChange written = new MemberStatusChange();
        written.setId(1L);
        written.setFromStatus("Active");
        written.setToStatus("Resigned");
        written.setReason(MemberStatusReason.DELETION_CODE);
        written.setDeletionCodeId(12L);
        written.setChangedBy("system");
        written.setChangedAt(NOW);
        when(members.changeStatus(eq(7), any(MemberService.StatusChange.class)))
                .thenReturn(Optional.of(new MemberService.StatusChangeOutcome(member("Resigned"), written, false)));
        ArgumentCaptor<MemberService.StatusChange> change = ArgumentCaptor.forClass(MemberService.StatusChange.class);

        MemberStatusAnswer answer = service.change(7, request("Resigned", "Active", "DELETION_CODE", 2, null, null));

        verify(members).changeStatus(eq(7), change.capture());
        assertThat(change.getValue()).isEqualTo(new MemberService.StatusChange("Resigned", "Active",
                MemberStatusReason.DELETION_CODE, 12L, null, null, "system", NOW));
        assertThat(answer.status()).isEqualTo("Resigned");
        assertThat(answer.change().deletionCode()).as("SASBO's number, never the table's id (RC7)").isEqualTo(2);
        assertThat(answer.change().deletionReason()).isEqualTo("Resigned Society");
    }

    @Test
    @DisplayName("04.5: a resignation with the reason given under code 2 is written as that reason's row, and answered with SASBO's number and the reason")
    void aReasonUnderTheCode() {
        when(codes.deletionCode(2)).thenReturn(Optional.of(new SasboReferenceRepository.Code(12L, 2, "Resigned Society")));
        when(codes.reasonUnder(12L, "financial")).thenReturn(Optional.of(21L));
        when(codes.deletionCodeById(21L)).thenReturn(Optional.of(new SasboReferenceRepository.Code(21L, 2, "Resigned Society")));
        when(codes.reasonUnderCode(21L)).thenReturn(Optional.of("Financial"));
        MemberStatusChange written = new MemberStatusChange();
        written.setId(1L);
        written.setFromStatus("Active");
        written.setToStatus("Resigned");
        written.setReason(MemberStatusReason.DELETION_CODE);
        written.setDeletionCodeId(21L);
        written.setChangedBy("system");
        written.setChangedAt(NOW);
        when(members.changeStatus(eq(7), any(MemberService.StatusChange.class)))
                .thenReturn(Optional.of(new MemberService.StatusChangeOutcome(member("Resigned"), written, false)));
        ArgumentCaptor<MemberService.StatusChange> change = ArgumentCaptor.forClass(MemberService.StatusChange.class);

        MemberStatusAnswer answer = service.change(7, new MemberStatusRequest("Resigned", "Active", "DELETION_CODE", 2,
                null, null, " financial "));

        verify(members).changeStatus(eq(7), change.capture());
        assertThat(change.getValue().deletionCodeId()).as("the reason's own row").isEqualTo(21L);
        assertThat(answer.change().deletionCode()).as("SASBO's number, the code it is under").isEqualTo(2);
        assertThat(answer.change().deletionReason()).isEqualTo("Resigned Society");
        assertThat(answer.change().reasonUnderCode()).isEqualTo("Financial");
    }

    @Test
    @DisplayName("04.5: a reason SASBO does not keep under the code is DELETION_CODE_UNKNOWN, naming the ones it does")
    void aReasonNotUnderTheCode() {
        when(codes.deletionCode(2)).thenReturn(Optional.of(new SasboReferenceRepository.Code(12L, 2, "Resigned Society")));
        when(codes.reasonUnder(12L, "Lottery")).thenReturn(Optional.empty());
        when(codes.reasonsUnder(12L)).thenReturn(List.of("Financial", "Personal"));

        assertThatThrownBy(() -> service.change(7, new MemberStatusRequest("Resigned", "Active", "DELETION_CODE", 2,
                null, null, "Lottery"))).isInstanceOfSatisfying(MonthEndRefusal.class, refusal -> {
                    assertThat(refusal.code()).isEqualTo(MonthEndRefusalCode.DELETION_CODE_UNKNOWN);
                    assertThat(refusal.details()).containsEntry("deletionCode", "2")
                            .containsEntry("known", "Financial,Personal");
                });
    }

    @Test
    @DisplayName("04.5: a reason under a deletion code does not fit a suspension")
    void aReasonUnderACodeWithASuspension() {
        assertThat(codeOf(() -> service.change(7, new MemberStatusRequest("Suspended", "Active", "SUSPENSION_CODE",
                null, 11, null, "Financial")))).isEqualTo(MonthEndRefusalCode.STATUS_REASON_MISMATCH);
    }

    @Test
    @DisplayName("ME-3: the same change asked twice writes nothing the second time, and answers the member as they are")
    void aRetryWritesNothing() {
        when(codes.deletionCode(2)).thenReturn(Optional.of(new SasboReferenceRepository.Code(12L, 2, "Resigned Society")));
        when(members.changeStatus(eq(7), any(MemberService.StatusChange.class)))
                .thenReturn(Optional.of(new MemberService.StatusChangeOutcome(member("Resigned"), null, false)));

        MemberStatusAnswer answer = service.change(7, request("Resigned", "Active", "DELETION_CODE", 2, null, null));

        assertThat(answer.status()).isEqualTo("Resigned");
        assertThat(answer.change()).isNull();
    }

    @Test
    @DisplayName("🔒 MV-5: a change on a stale view is STATUS_CHANGED_SINCE, saying what the status is now, when and by whom")
    void aStaleView() {
        when(codes.deletionCode(2)).thenReturn(Optional.of(new SasboReferenceRepository.Code(12L, 2, "Resigned Society")));
        when(members.changeStatus(eq(7), any(MemberService.StatusChange.class)))
                .thenReturn(Optional.of(new MemberService.StatusChangeOutcome(member("Suspended"), null, true)));
        MemberStatusChange last = new MemberStatusChange();
        last.setChangedAt(Instant.parse("2026-09-28T10:00:00Z"));
        last.setChangedBy("system");
        when(changes.findByMemberIdOrderByChangedAtAscIdAsc(7)).thenReturn(List.of(last));

        assertThatThrownBy(() -> service.change(7, request("Resigned", "Active", "DELETION_CODE", 2, null, null)))
                .isInstanceOfSatisfying(MonthEndRefusal.class, refusal -> {
                    assertThat(refusal.code()).isEqualTo(MonthEndRefusalCode.STATUS_CHANGED_SINCE);
                    assertThat(refusal.details()).containsEntry("currentStatus", "Suspended")
                            .containsEntry("changedAt", "2026-09-28T10:00:00Z").containsEntry("changedBy", "system");
                });
    }

    @Test
    @DisplayName("every refusal before a write, by its code -- and nothing is changed by any of them")
    void everyRefusalByCode() {
        when(codes.deletionCode(anyInt())).thenReturn(Optional.empty());
        when(codes.suspensionCode(anyInt())).thenReturn(Optional.empty());
        when(codes.suspensionCodeNumbers()).thenReturn(List.of(11, 13, 19));

        assertThat(codeOf(() -> service.change(7, request("Gone", null, "DELETION_CODE", 2, null, null))))
                .isEqualTo(MonthEndRefusalCode.STATUS_UNKNOWN);
        assertThat(codeOf(() -> service.change(7, request("Resigned", null, null, 2, null, null))))
                .isEqualTo(MonthEndRefusalCode.STATUS_REASON_REQUIRED);
        assertThat(codeOf(() -> service.change(7, request("Resigned", null, "SUSPENSION_CODE", null, 11, null))))
                .as("a suspension code does not fit a leaving status").isEqualTo(MonthEndRefusalCode.STATUS_REASON_MISMATCH);
        assertThat(codeOf(() -> service.change(7, request("Terminated", null, "SUBS_IN_ARREARS", null, null, null))))
                .as("ACB's reasons are ACB's, not this route's").isEqualTo(MonthEndRefusalCode.STATUS_REASON_MISMATCH);
        assertThat(codeOf(() -> service.change(7, request("Active", null, "REINSTATED", 2, null, null))))
                .as("a reinstatement carries no code").isEqualTo(MonthEndRefusalCode.STATUS_REASON_MISMATCH);
        assertThat(codeOf(() -> service.change(7, request("Resigned", null, "DELETION_CODE", null, null, null))))
                .as("a leaving with no code of SASBO's is not a reason").isEqualTo(MonthEndRefusalCode.STATUS_REASON_REQUIRED);
        assertThat(codeOf(() -> service.change(7, request("Suspended", null, "SUSPENSION_CODE", null, null, null))))
                .as("DL-64: a suspension with no code is refused").isEqualTo(MonthEndRefusalCode.STATUS_REASON_REQUIRED);
        assertThat(codeOf(() -> service.change(7, request("Resigned", null, "DELETION_CODE", 77, null, null))))
                .isEqualTo(MonthEndRefusalCode.DELETION_CODE_UNKNOWN);
        assertThat(codeOf(() -> service.change(7, request("Suspended", null, "SUSPENSION_CODE", null, 77, null))))
                .isEqualTo(MonthEndRefusalCode.SUSPENSION_CODE_UNKNOWN);
        verify(members, never()).changeStatus(anyInt(), any(MemberService.StatusChange.class));
    }

    @Test
    @DisplayName("🔒 ME-14: a refusal gives back a status only when it is short and of the expected alphabet; a note over 500 is named, never echoed")
    void nothingLongIsGivenBack() {
        String planted = "PLANTED STATUS <script>" + "x".repeat(60);
        assertThatThrownBy(() -> service.change(7, request(planted, null, "DELETION_CODE", 2, null, null)))
                .isInstanceOfSatisfying(MonthEndRefusal.class, refusal -> {
                    assertThat(refusal.getMessage()).doesNotContain("PLANTED");
                    assertThat(refusal.details()).doesNotContainKey("toStatus");
                });
        assertThatThrownBy(() -> service.change(7, request("Resigned", null, "DELETION_CODE", 2, null,
                "PLANTED NOTE " + "y".repeat(500))))
                .isInstanceOfSatisfying(MonthEndRefusal.class, refusal -> {
                    assertThat(refusal.code()).isEqualTo(MonthEndRefusalCode.REQUEST_MALFORMED);
                    assertThat(refusal.details()).containsEntry("field", "note");
                    assertThat(refusal.getMessage()).doesNotContain("PLANTED");
                });
    }

    @Test
    @DisplayName("a member who is gone is MEMBER_NOT_FOUND")
    void aMemberWhoIsGone() {
        when(codes.suspensionCode(11)).thenReturn(Optional.of(new SasboReferenceRepository.Code(3L, 11, "NOT ON PAYROLL")));
        when(members.changeStatus(eq(404), any(MemberService.StatusChange.class))).thenReturn(Optional.empty());

        assertThat(codeOf(() -> service.change(404, request("Suspended", null, "SUSPENSION_CODE", null, 11, null))))
                .isEqualTo(MonthEndRefusalCode.MEMBER_NOT_FOUND);
    }
}
