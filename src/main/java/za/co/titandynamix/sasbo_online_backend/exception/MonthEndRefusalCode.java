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
     * 01.2: a page of the workplaces that does not exist -- and A.1: a page of a workplace's visits that does not; the
     * October build: a page of the members at a workplace, or of a person's own visit drafts, that does not. The
     * details carry the page, the size and the ceiling.
     */
    WORKPLACE_PAGE_INVALID(HttpStatus.BAD_REQUEST),
    /**
     * October build, the organisers' batch print (5 Oct): the R/A numbers asked for could not be read -- none, more
     * than fifty, or one that is not a number of up to nine digits. The details say which ({@code reason}: NONE,
     * TOO_MANY or NOT_A_NUMBER) and the ceiling, and give none of what was sent back. Nothing was looked up.
     */
    RA_NUMBERS_INVALID(HttpStatus.BAD_REQUEST),
    /**
     * October build: the workplace is outside the caller's region or area -- its members are not listed to them, and
     * a visit to it is not captured by them (the permission contract's scope). The details carry the workplace's id.
     */
    WORKPLACE_OUT_OF_SCOPE(HttpStatus.FORBIDDEN),
    /**
     * October build, the visit forms (5 Oct): no visit draft of the caller's has that number -- another person's draft
     * is never found, so it is never shown. The details carry the id asked for.
     */
    VISIT_DRAFT_NOT_FOUND(HttpStatus.NOT_FOUND),
    /**
     * October build: the draft was saved since the version this save was made from -- in another tab, or on another
     * device. Nothing was written over it; the details carry the version now and when it was saved.
     */
    VISIT_DRAFT_STALE(HttpStatus.CONFLICT),
    /**
     * October build: the visit has been submitted, and a submitted visit is SASBO's record -- read-only. The details
     * carry the visit's number and when it was submitted.
     */
    VISIT_ALREADY_SUBMITTED(HttpStatus.CONFLICT),
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
     * Register row A10: a page of the member register that cannot be asked for -- below nought, or a size outside one to
     * a hundred. The details carry the page, the size and the ceiling. A page past the last is not refused: the last
     * page is answered, and says so.
     */
    REGISTER_PAGE_INVALID(HttpStatus.BAD_REQUEST),
    /** Register row A10: an order the register is not read in. The details carry the orders and directions it takes. */
    REGISTER_SORT_UNKNOWN(HttpStatus.BAD_REQUEST),
    /**
     * An edit made on a stale view of a member's record: somebody saved the record after this edit read it -- the
     * {@code version} the edit names is not the version on record, or a save racing this one won at the database.
     * Without it the later save silently undid the earlier one. Thrown under {@code MemberController} and answered by
     * the global handler, as {@code STATUS_VIA_CHANGE_STATUS} is. The details carry the member's id, and -- where it was
     * read before anything was written -- the version on record and when the record was last saved. Nothing was
     * written; the browser keeps the form as typed and offers to read the record again.
     */
    MEMBER_CHANGED_SINCE(HttpStatus.CONFLICT),

    // ---------------------------------------------------------------- October build: the files that leave SASBO
    /** No such recipient of a region's pack here -- or one outside the caller's region, answered as if absent. */
    PACK_SET_NOT_FOUND(HttpStatus.NOT_FOUND),
    /** A recipient that cannot be saved as sent: a code, a label, a position, an address or an area. Names the field. */
    PACK_SET_INVALID(HttpStatus.BAD_REQUEST),
    /** A recipient's code another recipient has. Nothing was saved. */
    PACK_SET_TAKEN(HttpStatus.CONFLICT),
    /**
     * Two active recipients of one region covering one area, or a whole-region recipient beside another: a member would
     * be in two packs, or a split region's member in none. Names the area and the other recipient.
     */
    PACK_SET_OVERLAP(HttpStatus.CONFLICT),
    /** A setting saved by someone else since the screen read it. Says the version now, by whom and when. */
    SETTING_CHANGED_SINCE(HttpStatus.CONFLICT),
    /** No such bank's file here. */
    BANK_NOT_FOUND(HttpStatus.NOT_FOUND),
    /** A bank's file that cannot be saved as sent: a code, a name, a layout or its table codes. Names the field. */
    BANK_SETTING_INVALID(HttpStatus.BAD_REQUEST),
    /** A bank's code another bank has. Nothing was saved. */
    BANK_TAKEN(HttpStatus.CONFLICT),
    /** A table code already in another bank's file -- one file a member at most. Names the code and the bank. */
    TABLE_CODE_TAKEN(HttpStatus.CONFLICT),
    /** No such provider here. */
    PROVIDER_NOT_FOUND(HttpStatus.NOT_FOUND),
    /** A recipient, bank or provider that is switched off (Criticare and the holiday benefit are suspended, MEN-35). */
    RECIPIENT_INACTIVE(HttpStatus.CONFLICT),
    /**
     * This server does not serve SASBO's workplaces in full (the workplaces switch is below {@code all}), so nothing can
     * be placed in a region and no region's pack can be made (MD62).
     */
    REGION_PACKS_UNAVAILABLE(HttpStatus.CONFLICT),
    /** No such region's pack -- or another region's, answered as if absent -- or no such format of it. */
    REGION_PACK_NOT_FOUND(HttpStatus.NOT_FOUND),
    /**
     * A region's pack asked to be e-mailed before it was made: a recipient's result inside the month's sending, and the
     * whole answer where none of the recipients asked for has a pack yet. Nothing was sent.
     */
    PACK_NOT_PRODUCED(HttpStatus.CONFLICT),
    /** No such bank's or provider's file here. */
    FILE_NOT_FOUND(HttpStatus.NOT_FOUND),
    /** A bank's or provider's file carries members of every region, so it needs a caller who sees every region. */
    SCOPE_TOO_NARROW(HttpStatus.FORBIDDEN),
    /** This server has no mail set up. Nothing was sent; downloading still works. Recorded as refused. */
    MAIL_NOT_CONFIGURED(HttpStatus.CONFLICT),
    /** The recipient has no e-mail address on record. Nothing was sent; recorded as refused. */
    RECIPIENT_HAS_NO_ADDRESS(HttpStatus.CONFLICT),
    /** The mail server refused the message or could not be reached. Recorded as failed; the log says the class only. */
    MAIL_FAILED(HttpStatus.BAD_GATEWAY),
    /** No SFTP server is set up on this server (host, user, key and known host). Nothing was sent; recorded as refused. */
    SFTP_NOT_CONFIGURED(HttpStatus.CONFLICT),
    /** A file whose recipient is not sent by SFTP: a bank's file, or a provider's that is downloaded. */
    SFTP_NOT_FOR_THIS_RECIPIENT(HttpStatus.CONFLICT),
    /** The SFTP server refused the file or could not be reached. Recorded as failed; the log says the class only. */
    SFTP_FAILED(HttpStatus.BAD_GATEWAY),
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
