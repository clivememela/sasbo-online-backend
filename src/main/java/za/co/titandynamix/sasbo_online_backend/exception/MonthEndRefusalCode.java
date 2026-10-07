package za.co.titandynamix.sasbo_online_backend.exception;

import org.springframework.http.HttpStatus;

/**
 * Every way the month-end arc says no, as a stable code with its HTTP status (MD17, MD40).
 *
 * <h2>The codes are the contract; the messages are copy (ME-1)</h2>
 * The browser branches on WHICH refusal it got, never on a sentence somebody will reword. Each refusal carries one of
 * these names in the envelope's {@code code}; the names are published in {@code api-vocabulary.json} under
 * {@code monthEnd}, and the browser's parity test lists the ones it acts on. The sentence a person reads is the
 * server's {@code message}, and every detail is a string (ME-13, ME-14).
 *
 * <p>ACB's {@code AcbRefusalCode} is ACB's and is not widened: this arc's refusals are their own enum, with their own
 * exception, handler, section of the vocabulary and parity test (MD40). A code arrives in the commit that first throws
 * it, and two guards hold the list to the code: every constant is published with its status, and every constant is
 * thrown somewhere -- so the vocabulary never promises a refusal no route can give (MV-33). The table of every code
 * the arc plans, with the unit that first throws it, is {@code testingAndErrorHandling.md} ME-1.
 */
public enum MonthEndRefusalCode {
    /**
     * The request could not be read: a body that is not JSON, a word where a number is wanted, a parameter or a part
     * that is missing, a content type the route does not take, a text over its ceiling (ME-11, MR-SEC-22). The
     * details carry the NAME of the field, never what was sent.
     */
    REQUEST_MALFORMED(HttpStatus.BAD_REQUEST),
    /**
     * 01.2: a page of the workplaces that does not exist -- and A.1: a page of a workplace's visits that does not.
     * The details carry the page, the size and the ceiling.
     */
    WORKPLACE_PAGE_INVALID(HttpStatus.BAD_REQUEST),
    /**
     * 01.3: a member's save names a workplace that is not on record here -- or none is served here (MD62). Looked for
     * BEFORE the save, so the database's foreign key is never what refuses it; answered by the global handler, the
     * save running under {@code MemberController} (ME-12). The details carry the id asked for. Nothing was written.
     * A.1: also a visit asked of, or recorded for, a workplace that is not on record here.
     */
    WORKPLACE_UNKNOWN(HttpStatus.BAD_REQUEST),
    /**
     * A.1: a visit that cannot be recorded -- no day, a day still to come in SASBO's zone, a count below nought, a
     * time out before the time in, or a visitor's name past its column. The details name the field. Nothing was
     * written, the workplace's complement included.
     */
    VISIT_INVALID(HttpStatus.BAD_REQUEST),
    /** 02.2: a grouping the figures do not take. The details carry the ones they do -- seven, with the tier (MEN-31). */
    SUMMARY_GROUPING_UNKNOWN(HttpStatus.BAD_REQUEST),
    /** 02.2: an area or a region that is not on record here. The details name which was asked for. */
    SUMMARY_AREA_UNKNOWN(HttpStatus.BAD_REQUEST),
    /** 02.2: a month that cannot be read. The details name the field and give nothing of it back (ME-14). */
    SUMMARY_MONTH_INVALID(HttpStatus.BAD_REQUEST),
    /** 02.2: a month after the server's own. The details carry the month asked for and the server's. */
    SUMMARY_MONTH_IN_FUTURE(HttpStatus.BAD_REQUEST),
    /**
     * 02.2: the figures could not be worked out in time -- the query outlived its timeout -- or two are being worked out
     * already (MD47, MD63). Answered at once, with {@code Retry-After}: the read is open, and the hosted database is a
     * tenth of a processor, so a figure never queues and never hangs a screen.
     */
    SUMMARY_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE),
    /** 03.6: a page of a member's history that does not exist. The details carry the page, the size and the ceiling. */
    HISTORY_PAGE_INVALID(HttpStatus.BAD_REQUEST),
    /** 03.1: the member is no longer on record. The details carry the id asked for. */
    MEMBER_NOT_FOUND(HttpStatus.NOT_FOUND),
    /** 03.1: not one of the statuses the record takes. The details carry the ones it does. */
    STATUS_UNKNOWN(HttpStatus.BAD_REQUEST),
    /** 03.1: a change with no reason -- or a leaving or a suspension with no code of SASBO's for it. */
    STATUS_REASON_REQUIRED(HttpStatus.BAD_REQUEST),
    /**
     * 03.1: the reason does not fit the status, JUDGED BY KIND AND NO FINER until the table (MM49): a deletion code
     * fits a leaving status, a suspension code Suspended, a reinstatement neither. The details say what fits.
     */
    STATUS_REASON_MISMATCH(HttpStatus.BAD_REQUEST),
    /** 03.1: no deletion code has that number of SASBO's (RC7: the number, never the table's id). */
    DELETION_CODE_UNKNOWN(HttpStatus.BAD_REQUEST),
    /** 03.1: no suspension code has that number. The details carry the ones that do. */
    SUSPENSION_CODE_UNKNOWN(HttpStatus.BAD_REQUEST),
    /**
     * 03.1: the change was asked for on a stale view -- the member is not in the status the screen showed (MV-5). The
     * details say what the status is now, when it changed and by whom. Nothing was changed.
     */
    STATUS_CHANGED_SINCE(HttpStatus.CONFLICT),
    /**
     * 03.4: an edit that would change the member's status. A status is changed with Change status, by SASBO's code
     * ({@code POST /api/members/{id}/status}), and never by an edit (MD13, BR77). The edit is refused whole and nothing
     * is written; the browser keeps the form as typed (ME-2). The details carry the status on record and the route a
     * status is changed by. The same status sent back unchanged is no change, and is accepted. October build: a capture
     * in a suspended or a leaving status is refused the same way -- a suspension carries SASBO's code and its reason
     * (Stephne, MEN-17, MEN-18), which only Change status asks for.
     */
    STATUS_VIA_CHANGE_STATUS(HttpStatus.CONFLICT),
    /** 04.2: a month that has not begun -- after the server's own, in SASBO's zone. The details carry both. */
    MONTH_NOT_BEGUN(HttpStatus.BAD_REQUEST),
    /**
     * 04.2: the month has been taken already -- refused by the partial unique index where two takes meet, never by
     * looking first (ME-12, MV-28). The details say who took it, when, and the take's id: taking it again names that
     * take (04.11).
     */
    MONTH_ALREADY_TAKEN(HttpStatus.CONFLICT),
    /**
     * 04.11: a take again that names a take which is not the month's newest -- another took it again since. Nobody
     * overwrites a take they have not seen; refused by the index that replaces a take once where two meet, never by
     * looking first. The details say who took the newest, when, and its id.
     */
    MONTH_TAKEN_SINCE(HttpStatus.CONFLICT),
    /**
     * 04.2: the month cannot be taken here -- what it would be frozen from is not on record: the workplaces (MD54). A
     * month frozen with none would be kept that way by its own triggers. The details name what is missing.
     */
    MONTH_NOT_READY(HttpStatus.CONFLICT),
    /**
     * 04.2: a past month that has not been taken. Its statistics are read from what was frozen, never worked out again
     * in place (MD19); only the open month is answered live. The details carry the month and the open month.
     */
    MONTH_NOT_TAKEN(HttpStatus.NOT_FOUND),
    /**
     * 04.10: a statistics pack is being prepared for another month, and there is no queue (MR-PERF-11). The details
     * carry the month asked for, the month in hand and its pack. A pack asked for the same month is the one in hand.
     */
    PACK_BUSY(HttpStatus.CONFLICT),
    /** 04.10: no such pack -- or no such workbook of it, or none yet: a pack's files are there once it is READY. */
    PACK_NOT_FOUND(HttpStatus.NOT_FOUND),
    /**
     * October build, Stephne MEN-11: a month taken again with no reason -- "This will help to understand why the file
     * was replaced!". Said before anything else is asked of the take. The details carry the month.
     */
    TAKE_REASON_REQUIRED(HttpStatus.BAD_REQUEST),
    /**
     * October build: a reason for taking a month again that is longer than 500 characters, or holds a control
     * character. The details carry the field and the limit -- never the words.
     */
    TAKE_REASON_INVALID(HttpStatus.BAD_REQUEST),
    /**
     * October build, Stephne MEN-19 -- "Going forward can we avoid capturing duplicates": the person is on record
     * already -- by identity number, or, where none is held, by passport number or by employee number at the
     * employer. The sentence and the details name the existing membership number and what matched, never the person;
     * {@code canReinstate} says the existing member has left, and is reinstated with Change status rather than captured
     * again (MEN-23: in the same month they keep their membership). Nothing was written.
     */
    MEMBER_ALREADY_CAPTURED(HttpStatus.CONFLICT),
    /**
     * October build, Stephne MEN-17: a suspension for no payroll deduction -- under a code the reasons stand under --
     * with neither a reason from the list nor one typed. The details carry the code and the reasons that fit it.
     */
    NO_DEDUCTION_REASON_REQUIRED(HttpStatus.BAD_REQUEST),
    /**
     * October build: a status change names a reason for no deduction that is not on the list, or is no longer offered.
     * The details carry the id asked for.
     */
    NO_DEDUCTION_REASON_UNKNOWN(HttpStatus.BAD_REQUEST),
    /**
     * October build: a reason for no deduction that cannot be kept -- no name, a name past 60 characters or with a
     * control character, a code SASBO does not have, a place on the list outside 1 to 999, or a list already at its 50.
     * The details name the field.
     */
    NO_DEDUCTION_REASON_INVALID(HttpStatus.BAD_REQUEST),
    /** October build: another reason has that name already, whatever its case. The details carry the other's id. */
    NO_DEDUCTION_REASON_TAKEN(HttpStatus.CONFLICT),
    /** October build: no reason for no deduction has that id. */
    NO_DEDUCTION_REASON_NOT_FOUND(HttpStatus.NOT_FOUND),
    /**
     * October build: the reason was changed by someone else since the screen read it. The details carry who and when;
     * nothing was changed.
     */
    NO_DEDUCTION_REASON_CHANGED_SINCE(HttpStatus.CONFLICT),
    /**
     * October build: a page of the suspended members that does not exist. The details carry the page, the size and the
     * ceiling.
     */
    SUSPENDED_PAGE_INVALID(HttpStatus.BAD_REQUEST),
    /** October build, Stephne MEN-31: a core tier that is not A, B or C. The details carry the ones that are. */
    CORE_TIER_UNKNOWN(HttpStatus.BAD_REQUEST),
    /** October build: no table code has that number here. The details carry the number asked for. */
    TABLE_CODE_UNKNOWN(HttpStatus.NOT_FOUND);

    private final HttpStatus status;

    MonthEndRefusalCode(HttpStatus status) {
        this.status = status;
    }

    public HttpStatus status() {
        return status;
    }
}
