package za.co.titandynamix.sasbo_online_backend.dto.member;

import za.co.titandynamix.sasbo_online_backend.enums.MemberStatusReason;

import java.time.Instant;

/**
 * A status change as month end asks for one (03.1) -- A REQUEST OF THIS ARC'S OWN, beside ACB's
 * {@link MemberStatusChangeRequest}, which keeps its six arguments and is never touched (BR67).
 *
 * @param toStatus         the status the member moves to, as the record stores it
 * @param expectedStatus   the status the screen showed; a member no longer in it is not changed (MV-5)
 * @param reason           why, as {@code member_status_changes} records it
 * @param deletionCodeId   the row of {@code deletion_codes} a leaving carries, resolved from SASBO's number
 * @param suspensionCodeId the row of {@code suspension_codes} a suspension carries
 * @param note             the administrator's words, at most 500 characters, never logged
 * @param actor            who -- {@code system} until sign-in (PA-12)
 * @param at               when -- the injected clock's instant
 */
public record StatusChange(String toStatus, String expectedStatus, MemberStatusReason reason, Long deletionCodeId,
                           Long suspensionCodeId, String note, String actor, Instant at) {
}
