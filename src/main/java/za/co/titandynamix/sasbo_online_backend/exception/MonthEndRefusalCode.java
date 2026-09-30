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
    WORKPLACE_PAGE_INVALID(HttpStatus.BAD_REQUEST);

    private final HttpStatus status;

    MonthEndRefusalCode(HttpStatus status) {
        this.status = status;
    }

    public HttpStatus status() {
        return status;
    }
}
