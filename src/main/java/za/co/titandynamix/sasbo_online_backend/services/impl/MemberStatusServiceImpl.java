package za.co.titandynamix.sasbo_online_backend.services.impl;

import org.springframework.stereotype.Service;
import za.co.titandynamix.sasbo_online_backend.acb.AcbActor;
import za.co.titandynamix.sasbo_online_backend.dto.member.MemberStatusAnswer;
import za.co.titandynamix.sasbo_online_backend.dto.member.MemberStatusRequest;
import za.co.titandynamix.sasbo_online_backend.entity.member.MemberStatusChange;
import za.co.titandynamix.sasbo_online_backend.enums.MemberStatusKind;
import za.co.titandynamix.sasbo_online_backend.enums.MemberStatusReason;
import za.co.titandynamix.sasbo_online_backend.exception.MonthEndRefusal;
import za.co.titandynamix.sasbo_online_backend.exception.MonthEndRefusalCode;
import za.co.titandynamix.sasbo_online_backend.repository.MemberStatusChangeRepository;
import za.co.titandynamix.sasbo_online_backend.repository.SasboReferenceRepository;
import za.co.titandynamix.sasbo_online_backend.services.MemberService;
import za.co.titandynamix.sasbo_online_backend.services.MemberService.StatusChange;
import za.co.titandynamix.sasbo_online_backend.services.MemberStatusService;
import za.co.titandynamix.sasbo_online_backend.utils.MemberStatuses;
import za.co.titandynamix.sasbo_online_backend.utils.MonthRange;

import java.time.Clock;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * A member's status, changed with SASBO's code for it (month end 03.1), through {@link MemberService#changeStatus}.
 *
 * <h2>A reason fits a status by KIND, and no finer, until the table (MM49)</h2>
 * A leaving status takes {@code DELETION_CODE} with SASBO's deletion code; {@code Suspended} takes
 * {@code SUSPENSION_CODE} with SASBO's suspension code; a status of neither kind takes {@code REINSTATED} -- from a
 * leaving or a suspended one -- or {@code CAPTURED}, and no code. Which deletion code fits which leaving status is
 * SASBO's to say, and is not guessed here. ACB's two reasons are ACB's, and are not accepted from this route.
 *
 * <h2>Safe to ask twice (ME-3), and never on a stale view (MV-5)</h2>
 * The member's row is locked for the change. A member already in the status asked for is answered as they are, and
 * nothing is written; a member no longer in the status the screen showed is not changed, and the refusal says what
 * their status is now, when it changed and by whom.
 *
 * <p>The actor is {@code system} until sign-in exists (PA-12); the instant is the injected clock's. The note is
 * capped at 500 characters and never reaches a log line (MR-SEC-6, MD68).
 */
@Service
public class MemberStatusServiceImpl implements MemberStatusService {

    /**
     * A moment as a person reads it, in SASBO's zone -- {@code 2026-09-28T10:00:00Z} is "28 September 2026 at 12:00", as
     * the screens write an instant. The sentence of a refusal is a person's; its details keep the ISO instant.
     */
    static final DateTimeFormatter SAID = DateTimeFormatter.ofPattern("d MMMM yyyy 'at' HH:mm", Locale.ENGLISH)
            .withZone(MonthRange.ZONE);

    private final MemberService members;
    private final SasboReferenceRepository codes;
    private final MemberStatusChangeRepository changes;
    private final Clock clock;

    public MemberStatusServiceImpl(MemberService members, SasboReferenceRepository codes,
                                   MemberStatusChangeRepository changes, Clock clock) {
        this.members = members;
        this.codes = codes;
        this.changes = changes;
        this.clock = clock;
    }

    @Override
    public MemberStatusAnswer change(Integer memberId, MemberStatusRequest request) {
        String toStatus = request.toStatus() == null ? null : request.toStatus().trim();
        MemberStatusKind kind = MemberStatuses.kindOf(toStatus);
        if (kind == null) {
            throw new MonthEndRefusal(MonthEndRefusalCode.STATUS_UNKNOWN,
                    MonthEndRefusal.echo(toStatus) == null ? "That is not a membership status."
                            : "\"" + toStatus + "\" is not a membership status.",
                    MonthEndRefusal.details("toStatus", MonthEndRefusal.echo(toStatus),
                            "known", String.join(",", MemberStatuses.KINDS.keySet())));
        }
        if (request.note() != null && request.note().length() > MAX_NOTE_LENGTH) {
            throw new MonthEndRefusal(MonthEndRefusalCode.REQUEST_MALFORMED,
                    "This request could not be read. Send each field in the form it is expected in.",
                    Map.of("field", "note"));
        }
        MemberStatusReason reason = reasonOf(request.reason(), toStatus);
        Long deletionCodeId = null;
        Long suspensionCodeId = null;
        switch (kind) {
            case LEAVING -> {
                fits(reason == MemberStatusReason.DELETION_CODE && request.suspensionCode() == null, toStatus, reason,
                        MemberStatusReason.DELETION_CODE.name());
                deletionCodeId = deletionCode(request.deletionCode(), blankToNull(request.reasonUnderCode()), toStatus);
            }
            case SUSPENDED -> {
                fits(reason == MemberStatusReason.SUSPENSION_CODE && request.deletionCode() == null
                        && blankToNull(request.reasonUnderCode()) == null, toStatus, reason,
                        MemberStatusReason.SUSPENSION_CODE.name());
                suspensionCodeId = suspensionCode(request.suspensionCode(), toStatus);
            }
            default -> fits((reason == MemberStatusReason.REINSTATED || reason == MemberStatusReason.CAPTURED)
                            && request.deletionCode() == null && request.suspensionCode() == null
                            && blankToNull(request.reasonUnderCode()) == null, toStatus, reason,
                    MemberStatusReason.REINSTATED.name() + "," + MemberStatusReason.CAPTURED.name());
        }
        StatusChange change = new StatusChange(toStatus, blankToNull(request.expectedStatus()), reason, deletionCodeId,
                suspensionCodeId, blankToNull(request.note()), AcbActor.current(), clock.instant());
        MemberService.StatusChangeOutcome outcome = members.changeStatus(memberId, change)
                .orElseThrow(() -> new MonthEndRefusal(MonthEndRefusalCode.MEMBER_NOT_FOUND,
                        "That member is no longer on record.", Map.of("memberId", String.valueOf(memberId))));
        if (outcome.stale()) {
            throw stale(outcome.member().getId(), outcome.member().getStatus());
        }
        return new MemberStatusAnswer(outcome.member().getId(), outcome.member().getMembershipNumber(),
                outcome.member().getStatus(), outcome.written() == null ? null : answer(outcome.written()));
    }

    // ------------------------------------------------------------------------------------------------ the rules

    private static MemberStatusReason reasonOf(String reason, String toStatus) {
        if (reason == null || reason.isBlank()) {
            throw new MonthEndRefusal(MonthEndRefusalCode.STATUS_REASON_REQUIRED,
                    "Say why this member is moving to " + toStatus + ".", Map.of("toStatus", toStatus));
        }
        try {
            MemberStatusReason parsed = MemberStatusReason.valueOf(reason.trim());
            if (parsed == MemberStatusReason.SUBS_IN_ARREARS || parsed == MemberStatusReason.UNPAID_SUSPENDED) {
                throw new IllegalArgumentException("ACB's reasons are ACB's");
            }
            return parsed;
        } catch (IllegalArgumentException notOurs) {
            throw new MonthEndRefusal(MonthEndRefusalCode.STATUS_REASON_MISMATCH,
                    "That reason does not fit " + toStatus + ".",
                    MonthEndRefusal.details("toStatus", toStatus, "reason", MonthEndRefusal.echo(reason.trim()),
                            "fits", fitsFor(toStatus)));
        }
    }

    private static String fitsFor(String toStatus) {
        return switch (MemberStatuses.kindOf(toStatus)) {
            case LEAVING -> MemberStatusReason.DELETION_CODE.name();
            case SUSPENDED -> MemberStatusReason.SUSPENSION_CODE.name();
            case NEITHER -> MemberStatusReason.REINSTATED.name() + "," + MemberStatusReason.CAPTURED.name();
        };
    }

    private static void fits(boolean fits, String toStatus, MemberStatusReason reason, String what) {
        if (!fits) {
            throw new MonthEndRefusal(MonthEndRefusalCode.STATUS_REASON_MISMATCH,
                    "That reason does not fit " + toStatus + ".",
                    MonthEndRefusal.details("toStatus", toStatus, "reason", reason.name(), "fits", what));
        }
    }

    /**
     * The row a leaving is written with: SASBO's numbered code -- or, where the reason given is one SASBO keeps under
     * it (the reasons for resigning, under 2), that reason's own row, which every figure reads as the code it is under
     * and SASBO's {@code resigned} report counts by (04.5).
     */
    private Long deletionCode(Integer number, String reasonUnder, String toStatus) {
        if (number == null) {
            throw new MonthEndRefusal(MonthEndRefusalCode.STATUS_REASON_REQUIRED,
                    "Say why this member is moving to " + toStatus + ".", Map.of("toStatus", toStatus));
        }
        long code = codes.deletionCode(number).map(SasboReferenceRepository.Code::id)
                .orElseThrow(() -> new MonthEndRefusal(MonthEndRefusalCode.DELETION_CODE_UNKNOWN,
                        "That reason is no longer on the list. Choose another.",
                        Map.of("deletionCode", String.valueOf(number))));
        if (reasonUnder == null) {
            return code;
        }
        return codes.reasonUnder(code, reasonUnder).orElseThrow(() -> new MonthEndRefusal(
                MonthEndRefusalCode.DELETION_CODE_UNKNOWN,
                "That reason is not one SASBO keeps under code " + number + ". Choose one of its own, or none.",
                MonthEndRefusal.details("deletionCode", String.valueOf(number), "known",
                        String.join(",", codes.reasonsUnder(code)))));
    }

    private Long suspensionCode(Integer number, String toStatus) {
        if (number == null) {
            throw new MonthEndRefusal(MonthEndRefusalCode.STATUS_REASON_REQUIRED,
                    "Say why this member is moving to " + toStatus + ".", Map.of("toStatus", toStatus));
        }
        return codes.suspensionCode(number).map(SasboReferenceRepository.Code::id)
                .orElseThrow(() -> new MonthEndRefusal(MonthEndRefusalCode.SUSPENSION_CODE_UNKNOWN,
                        "That reason for suspending is not on the list. Choose another.",
                        MonthEndRefusal.details("suspensionCode", String.valueOf(number), "known",
                                codes.suspensionCodeNumbers().stream().map(String::valueOf)
                                        .collect(Collectors.joining(",")))));
    }

    private MonthEndRefusal stale(Integer memberId, String currentStatus) {
        List<MemberStatusChange> history = changes.findByMemberIdOrderByChangedAtAscIdAsc(memberId);
        MemberStatusChange last = history.isEmpty() ? null : history.get(history.size() - 1);
        String at = last == null ? null : last.getChangedAt().toString();
        return new MonthEndRefusal(MonthEndRefusalCode.STATUS_CHANGED_SINCE,
                "This member was moved to " + currentStatus
                        + (last == null ? "" : " on " + SAID.format(last.getChangedAt())) + ". Nothing was changed.",
                MonthEndRefusal.details("currentStatus", currentStatus, "changedAt", at,
                        "changedBy", last == null ? null : last.getChangedBy()));
    }

    private MemberStatusAnswer.Change answer(MemberStatusChange written) {
        SasboReferenceRepository.Code deletion = written.getDeletionCodeId() == null ? null
                : codes.deletionCodeById(written.getDeletionCodeId()).orElse(null);
        SasboReferenceRepository.Code suspension = written.getSuspensionCodeId() == null ? null
                : codes.suspensionCodeById(written.getSuspensionCodeId()).orElse(null);
        String under = written.getDeletionCodeId() == null ? null
                : codes.reasonUnderCode(written.getDeletionCodeId()).orElse(null);
        return new MemberStatusAnswer.Change(written.getId(), written.getFromStatus(), written.getToStatus(),
                written.getReason().name(),
                deletion == null ? null : deletion.number(), deletion == null ? null : deletion.name(), under,
                suspension == null ? null : suspension.number(), suspension == null ? null : suspension.name(),
                written.getNote(), written.getChangedBy(), written.getChangedAt().toString());
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
