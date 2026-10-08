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
    /** 02.2: a grouping the figures do not take. The details carry the six they do. */
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
     * status is changed by. The same status sent back unchanged is no change, and is accepted.
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
     * 04.2: refused to every caller until this system knows who is calling -- as ACB's writes are (MD4): taking a
     * month freezes its figures for good, and a listing names members. Nothing was done.
     */
    IDENTITY_REQUIRED(HttpStatus.FORBIDDEN),
    /**
     * 04.10: a statistics pack is being prepared for another month, and there is no queue (MR-PERF-11). The details
     * carry the month asked for, the month in hand and its pack. A pack asked for the same month is the one in hand.
     */
    PACK_BUSY(HttpStatus.CONFLICT),
    /** 04.10: no such pack -- or no such workbook of it, or none yet: a pack's files are there once it is READY. */
    PACK_NOT_FOUND(HttpStatus.NOT_FOUND),

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
    SFTP_FAILED(HttpStatus.BAD_GATEWAY);

    private final HttpStatus status;

    MonthEndRefusalCode(HttpStatus status) {
        this.status = status;
    }

    public HttpStatus status() {
        return status;
    }
}
