package za.co.titandynamix.sasbo_online_backend.enums;

/**
 * Where a letter is on its way to the member. A {@code SENT} letter is never sent again.
 *
 * <pre>
 *   QUEUED ─▶ RENDERED ─▶ SENT
 *     │           └─────▶ FAILED ─▶ (a retry: RENDERED again, attempts counted)
 *     └──▶ HANDLED   (held -- no address, or not one that can be sent to -- and dealt with by hand)
 * </pre>
 *
 * <p>{@code HANDLED} is Q46's answer: there is no post any longer, "so someone needs to deal with it".
 * A held letter is marked handled with a note, audited, and never sent afterwards.
 */
public enum LetterStatus {
    QUEUED,
    RENDERED,
    SENT,
    FAILED,
    HANDLED
}
