package za.co.titandynamix.sasbo_online_backend.exception;

import org.springframework.http.HttpStatus;

/**
 * Every way the payroll module says no, as a stable code with its HTTP status -- the pattern of ACB's and month end's
 * refusals. The browser branches on the code, never on the sentence; the codes are published in
 * {@code api-vocabulary.json} under {@code payroll}, every constant is thrown somewhere, and the client's parity test
 * reads the list ({@code PayrollGuardsTest} holds both).
 */
public enum PayrollRefusalCode {
    /** A body, parameter or part that cannot be read. The details name the field, never what was sent. */
    REQUEST_MALFORMED(HttpStatus.BAD_REQUEST),
    /** A page that does not exist, or a size outside 1 to 100. */
    PAGE_INVALID(HttpStatus.BAD_REQUEST),
    /** A filter word the list does not take: a type, a status, a row filter. The details carry the ones it does. */
    FILTER_UNKNOWN(HttpStatus.BAD_REQUEST),
    /** An upload or a typed entry naming a payroll employer that is not on record. */
    EMPLOYER_UNKNOWN(HttpStatus.BAD_REQUEST),
    /** A payroll employer asked for by its address that is not on record. */
    EMPLOYER_NOT_FOUND(HttpStatus.NOT_FOUND),
    /** A month that cannot be read -- it is written 2026-09. */
    PERIOD_INVALID(HttpStatus.BAD_REQUEST),
    /** A month that has not begun, in SASBO's zone. */
    PERIOD_IN_FUTURE(HttpStatus.BAD_REQUEST),
    /** No file part, or an empty file. */
    FILE_MISSING(HttpStatus.BAD_REQUEST),
    /** Over the upload limit. The details carry the limit. */
    FILE_TOO_LARGE(HttpStatus.PAYLOAD_TOO_LARGE),
    /** A PDF or a picture: the system cannot read it, so its rows are typed in (PAY-03). */
    TYPED_ENTRY_REQUIRED(HttpStatus.UNSUPPORTED_MEDIA_TYPE),
    /** Not a spreadsheet the system reads -- judged by the content, never by the name. */
    FILE_TYPE_NOT_ACCEPTED(HttpStatus.UNSUPPORTED_MEDIA_TYPE),
    /** A damaged or password-protected workbook, or one that expands far beyond its size. */
    FILE_UNREADABLE(HttpStatus.UNPROCESSABLE_ENTITY),
    /** More rows than one file may carry. The details carry the limit. */
    TOO_MANY_ROWS(HttpStatus.UNPROCESSABLE_ENTITY),
    /** No row of headings the system recognises. The details say what it looked for. */
    HEADING_ROW_NOT_FOUND(HttpStatus.UNPROCESSABLE_ENTITY),
    /** A column mapping naming a heading that is not on the file, or leaving out the amount or every identifier. */
    COLUMN_MAPPING_INVALID(HttpStatus.BAD_REQUEST),
    /** Headings, but no lines under them. */
    NO_ROWS(HttpStatus.UNPROCESSABLE_ENTITY),
    /** A different file is loaded for this employer and month. The details name it; replacing it is asked for. */
    FILE_EXISTS_FOR_PERIOD(HttpStatus.CONFLICT),
    /** Typed rows that cannot be taken. The details carry the row and the field. */
    TYPED_ROWS_INVALID(HttpStatus.BAD_REQUEST),
    /** No such payroll file. */
    FILE_NOT_FOUND(HttpStatus.NOT_FOUND),
    /** The file's contents were deleted a year after it was loaded (PAY-39); what was found is kept. */
    FILE_PURGED(HttpStatus.GONE),
    /** The file was replaced by a later one for the same employer and month. */
    FILE_SUPERSEDED(HttpStatus.CONFLICT),
    /** The file changed since the screen read it. The details carry its version now. */
    FILE_CHANGED_SINCE(HttpStatus.CONFLICT),
    /** No such discrepancy. */
    DISCREPANCY_NOT_FOUND(HttpStatus.NOT_FOUND),
    /** The discrepancy is resolved already. The details say how, by whom and when. */
    DISCREPANCY_ALREADY_RESOLVED(HttpStatus.CONFLICT),
    /** The discrepancy changed since the screen read it. The details carry its version now. */
    DISCREPANCY_CHANGED_SINCE(HttpStatus.CONFLICT),
    /** An outcome that does not fit the kind of discrepancy. The details carry the ones that do. */
    OUTCOME_NOT_FOR_TYPE(HttpStatus.BAD_REQUEST),
    /** A note that is blank or longer than its limit. A resolution needs one. */
    NOTE_INVALID(HttpStatus.BAD_REQUEST),
    /** No open discrepancy of the file asks the employer for anything that is not on a query already. */
    NOTHING_TO_QUERY(HttpStatus.CONFLICT),
    /** No such query. */
    QUERY_NOT_FOUND(HttpStatus.NOT_FOUND),
    /** A download password outside the rules: 8 to 64 characters. */
    PASSWORD_INVALID(HttpStatus.BAD_REQUEST),
    /** A report format other than xlsx or pdf. */
    REPORT_FORMAT_UNKNOWN(HttpStatus.BAD_REQUEST),
    /** A payment that cannot be recorded. The details name the field. */
    PAYMENT_INVALID(HttpStatus.BAD_REQUEST),
    /** Employer settings that cannot be taken: a fee outside 0 to 99.99%, or a mapping without the amount. */
    SETTINGS_INVALID(HttpStatus.BAD_REQUEST),
    /** The employer's settings changed since the screen read them. */
    SETTINGS_CHANGED_SINCE(HttpStatus.CONFLICT),
    /**
     * A caller limited to a region or an area. An employer's file covers every region, so payroll is head office's
     * (Stephne, PAY-32, PAY-33) -- refused even where a role was given a payroll permission by mistake.
     */
    HEAD_OFFICE_ONLY(HttpStatus.FORBIDDEN);

    private final HttpStatus status;

    PayrollRefusalCode(HttpStatus status) {
        this.status = status;
    }

    public HttpStatus status() {
        return status;
    }
}
