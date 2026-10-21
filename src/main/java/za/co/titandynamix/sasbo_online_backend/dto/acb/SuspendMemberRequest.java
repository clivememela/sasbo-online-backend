package za.co.titandynamix.sasbo_online_backend.dto.acb;

import jakarta.validation.constraints.Size;

/**
 * Suspending the member of an unpaid decided SUSPEND (DOD-12: "Administrators records the redebits and suspensions").
 *
 * @param suspensionCode SASBO's suspension code, when the bank's code names none or another applies; null for the one
 *                       the bank's code records (her sheet, DOD-01)
 * @param note           at most 500 characters, kept on the status change, never logged
 */
public record SuspendMemberRequest(Integer suspensionCode, @Size(max = 500) String note) {
}
