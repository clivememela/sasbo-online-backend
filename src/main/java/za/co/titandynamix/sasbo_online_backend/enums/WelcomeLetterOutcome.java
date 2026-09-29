package za.co.titandynamix.sasbo_online_backend.enums;

/**
 * What happened to the welcome letter a capture asked for ({@code POST /api/members?sendWelcome=true}).
 *
 * <p>🔴 A64: the letter is no longer part of the capture. The member is saved first, in a transaction
 * that has COMMITTED before the letter is attempted, so none of these outcomes can undo a capture --
 * where mail is not configured, where the member has no address, and where sending fails, the member
 * is on record all the same. The browser says which of the four happened.
 *
 * <p>Published in {@code api-vocabulary.json} under {@code welcomeLetter}, so the capture screen's
 * sentence for each outcome is held to this list rather than to a copy of it.
 */
public enum WelcomeLetterOutcome {

    /** The letter was emailed to every address the member's preference selects. */
    SENT,

    /** This server has no mail configured ({@code MAIL_USERNAME} is empty), so nothing was attempted. */
    EMAIL_OFF,

    /** The member has neither a personal nor a work email address, so there was nowhere to send it. */
    NO_ADDRESS,

    /**
     * Sending was attempted and did not complete. The member is saved; the server's log says why,
     * naming no address (B8). With two addresses the first may already have been sent.
     */
    FAILED
}
