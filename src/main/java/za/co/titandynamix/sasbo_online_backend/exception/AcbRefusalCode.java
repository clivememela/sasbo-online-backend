package za.co.titandynamix.sasbo_online_backend.exception;

import org.springframework.http.HttpStatus;

/**
 * Every way the ACB module says no, as a stable code with its HTTP status.
 *
 * <h2>The codes are the contract; the messages are copy</h2>
 * A run's state machine refuses things constantly -- a month already prepared, a run already
 * approved by someone else, a version gone stale. The browser has to branch on WHICH refusal it got
 * to say something useful, and branching on a message is branching on copy somebody will reword.
 * So each refusal carries one of these names in the error envelope's {@code code} field; the names
 * are published in {@code api-vocabulary.json}, and the browser's parity test lists them.
 *
 * <p>Adding a code is adding a constant here and regenerating the vocabulary. Two guards hold the
 * list to the code: every constant is published, so a code the browser has never heard of cannot
 * ship; and every constant is thrown somewhere, so the vocabulary never promises a refusal the
 * server cannot give. A code arrives in the commit that first throws it.
 */
public enum AcbRefusalCode {
    /** A live run already holds that month. The details carry its id, status and version. */
    RUN_ALREADY_EXISTS(HttpStatus.CONFLICT),
    RUN_NOT_FOUND(HttpStatus.NOT_FOUND),
    /** The run is not in a state that action may act on. The details carry the state. */
    RUN_NOT_IN_STATE(HttpStatus.CONFLICT),
    /** Someone else changed the run first. The details carry its current status and version. */
    RUN_VERSION_CONFLICT(HttpStatus.CONFLICT),
    /** A run with no lines cannot be approved: "no eligible members" is a finding, not a month. */
    RUN_EMPTY(HttpStatus.UNPROCESSABLE_ENTITY),
    /**
     * A holiday was added or removed inside the run's month after its dates were computed, so a line
     * may now fall on a holiday -- and the bank declines such a file whole (D30, SD3-F). Neither
     * approved nor made into a file: prepare the month again. The details carry the run's facts.
     */
    RUN_CALENDAR_CHANGED(HttpStatus.CONFLICT),
    /**
     * A submitted run with a line already returned cannot be recorded as declined: a file the bank
     * declined produces no returns (F45). Not RUN_NOT_IN_STATE -- the run is in the right state, and
     * the screen has to be able to say why (EH2-B). The details carry the count.
     */
    RUN_HAS_RETURNS(HttpStatus.CONFLICT),
    /** A bank has two ACTIVE subscription rows. The details name every such bank. */
    BANK_SUBSCRIPTION_AMBIGUOUS(HttpStatus.UNPROCESSABLE_ENTITY),
    /** No flat rate is in force for the action month, so no line can be priced. */
    RATE_NOT_IN_FORCE(HttpStatus.UNPROCESSABLE_ENTITY),
    /**
     * The holiday calendar is not known to the end of the action month, so its dates cannot be
     * computed honestly: a month is never prepared on weekends alone (D30). The details carry the
     * date the calendar is known until.
     */
    HOLIDAYS_NOT_KNOWN(HttpStatus.UNPROCESSABLE_ENTITY),
    /** Only an approved run -- or one whose file already went to the bank -- has a bureau file. */
    FILE_NOT_APPROVED(HttpStatus.CONFLICT),
    /** The run's lines no longer produce the file stored for it. Nothing is overwritten. */
    FILE_CHANGED_SINCE(HttpStatus.CONFLICT),
    /** The union's collection identifiers are not configured, so no bureau file can be written. */
    FILE_SETTINGS_MISSING(HttpStatus.UNPROCESSABLE_ENTITY),
    /** The file's header dates are out of order, or disagree with the lines they head. */
    FILE_HEADER_INVALID(HttpStatus.UNPROCESSABLE_ENTITY),
    /** Lines the file cannot carry -- no account type, a number wider than its field. Counted by problem. */
    FILE_LINES_INCOMPLETE(HttpStatus.UNPROCESSABLE_ENTITY),
    /** Submission cannot be recorded for a run whose file was never produced. */
    FILE_NOT_RENDERED(HttpStatus.CONFLICT),
    /** The run's file passed its purge date; its bytes are gone, its hash and totals remain. */
    FILE_PURGED(HttpStatus.GONE),
    /**
     * Where the production marker is set, a run holding a member the seed files list is neither
     * approved nor made into a file: synthetic people are never debited (SD3-A). The details carry
     * how many lines. An exact match against the listed numbers, never a numeric range (SD4-E).
     */
    FILE_HAS_SEED_MEMBERS(HttpStatus.UNPROCESSABLE_ENTITY),
    /** The run has no such line. The details carry both ids. */
    LINE_NOT_FOUND(HttpStatus.NOT_FOUND),
    /**
     * The debit is already recorded as unpaid with a different code or decision. A return is a
     * fact about what the bank did: it is corrected deliberately, never overwritten by a second click.
     */
    UNPAID_ALREADY_RECORDED(HttpStatus.CONFLICT),
    /**
     * A month is not closed while an unpaid in it has no decision: an undecided unpaid is neither
     * presented again nor suspended, and closing would leave it so for ever. The details carry the count.
     */
    UNPAIDS_UNDECIDED(HttpStatus.CONFLICT),
    /** A date is malformed -- dates are written YYYY-MM-DD -- or is not one this action accepts. */
    DATE_INVALID(HttpStatus.BAD_REQUEST),
    /**
     * The request could not be read: malformed JSON, a value of the wrong type, a missing part
     * (EH2-A). Answered with the envelope and nothing of what was sent.
     */
    REQUEST_MALFORMED(HttpStatus.BAD_REQUEST),
    /** The action month is malformed, or not one a run may be prepared for. */
    ACTION_MONTH_INVALID(HttpStatus.BAD_REQUEST),
    /** The day is already in the holiday calendar. The details carry the date and its name. */
    HOLIDAY_ALREADY_LISTED(HttpStatus.CONFLICT),
    /**
     * Only a day an administrator added can be removed: the statutory days are the Act's, and a day
     * not listed has nothing to remove. The details carry the date.
     */
    HOLIDAY_NOT_REMOVABLE(HttpStatus.UNPROCESSABLE_ENTITY),
    /**
     * The action moves money, sends a letter, submits a file or changes a member's status, and the
     * API does not yet know who is calling (register B1). Refused to every caller until it does.
     */
    IDENTITY_REQUIRED(HttpStatus.FORBIDDEN);

    private final HttpStatus status;

    AcbRefusalCode(HttpStatus status) {
        this.status = status;
    }

    public HttpStatus status() {
        return status;
    }
}
