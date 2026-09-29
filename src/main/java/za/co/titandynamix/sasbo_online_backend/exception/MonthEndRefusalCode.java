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
    /** 01.2: a page of the workplaces that does not exist. The details carry the page, the size and the ceiling. */
    WORKPLACE_PAGE_INVALID(HttpStatus.BAD_REQUEST),
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
    STATUS_CHANGED_SINCE(HttpStatus.CONFLICT);

    private final HttpStatus status;

    MonthEndRefusalCode(HttpStatus status) {
        this.status = status;
    }

    public HttpStatus status() {
        return status;
    }
}
