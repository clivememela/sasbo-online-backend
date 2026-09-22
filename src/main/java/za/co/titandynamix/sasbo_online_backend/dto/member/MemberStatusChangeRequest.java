package za.co.titandynamix.sasbo_online_backend.dto.member;

import za.co.titandynamix.sasbo_online_backend.enums.MemberStatusReason;

import java.time.Instant;

/**
 * A status change for {@code MemberService.changeStatus} (ACB {@code 03.5}).
 *
 * @param toStatus the status the member moves to, as the member record stores it -- "Suspended", "Terminated"
 * @param reason   why, as SASBO counts it (Q25)
 * @param runId    the run a suspension came from; {@code null} for a termination
 * @param lineId   the unpaid line a suspension came from; {@code null} for a termination
 * @param actor    who, as the audit trail records it
 * @param at       when -- the caller's clock, so a change and the rest of its work share one instant
 */
public record MemberStatusChangeRequest(String toStatus, MemberStatusReason reason, Long runId, Long lineId,
                                        String actor, Instant at) {
}
