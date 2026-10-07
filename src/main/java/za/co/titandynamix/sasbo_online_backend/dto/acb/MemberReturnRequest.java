package za.co.titandynamix.sasbo_online_backend.dto.acb;

import jakarta.validation.constraints.Size;

/**
 * A member made active again (DOD-25, DOD-26) or rejoining after their membership ended (DOD-27).
 *
 * @param joinedOn for a rejoin, the new join date, YYYY-MM-DD -- today when not given, never in the future
 * @param note     at most 500 characters, kept on the status change, never logged
 */
public record MemberReturnRequest(String joinedOn, @Size(max = 500) String note) {
}
