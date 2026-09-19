package za.co.titandynamix.sasbo_online_backend.enums;

/**
 * Where a letter is on its way to the member. A {@code SENT} letter is never sent again.
 *
 * <pre>
 *   QUEUED ─▶ RENDERED ─▶ SENT
 *                 └─────▶ FAILED ─▶ (a retry: RENDERED again, attempts counted)
 * </pre>
 */
public enum LetterStatus {
    QUEUED,
    RENDERED,
    SENT,
    FAILED
}
