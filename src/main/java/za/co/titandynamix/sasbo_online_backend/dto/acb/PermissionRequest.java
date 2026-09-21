package za.co.titandynamix.sasbo_online_backend.dto.acb;

/**
 * The member's permission for a further re-debit in one run (D32): who agreed, when, how. Checked by
 * the service, not by bean validation, so a missing note answers {@code PERMISSION_NOTE_REQUIRED} and
 * a long one {@code REQUEST_MALFORMED} -- coded refusals, never an uncoded 400 (EH2-B).
 */
public record PermissionRequest(String note) {
}
