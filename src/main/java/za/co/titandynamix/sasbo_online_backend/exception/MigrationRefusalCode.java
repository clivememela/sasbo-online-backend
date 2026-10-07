package za.co.titandynamix.sasbo_online_backend.exception;

import org.springframework.http.HttpStatus;

/**
 * Every way the data migration says no, as a stable code with its HTTP status -- the pattern of ACB's and month end's
 * refusals: the browser branches on the code, never on a sentence somebody will reword; the codes are published in
 * {@code api-vocabulary.json} under {@code migration}, each with its status; and each is thrown somewhere, so the
 * vocabulary promises nothing no route can give ({@code MigrationGuardsTest}).
 *
 * <p>The sentence a person reads is the refusal's message, and every detail is a string that names a field, a count or
 * a limit -- never a value of the file.
 */
public enum MigrationRefusalCode {
    /** The request could not be read: a body that is not JSON, a word where a number is wanted, a missing parameter. */
    REQUEST_MALFORMED(HttpStatus.BAD_REQUEST),
    /** A field of the request breaks its rule -- too long, blank, out of range. The details name the fields. */
    REQUEST_INVALID(HttpStatus.BAD_REQUEST),
    /** A page that does not exist. The details carry the page, the size and the ceiling. */
    PAGE_INVALID(HttpStatus.BAD_REQUEST),
    /** The file holds no rows. Nothing was kept. */
    UPLOAD_EMPTY(HttpStatus.BAD_REQUEST),
    /** The file is over its size limit, met while it was being read. The details carry the limit. Nothing was kept. */
    UPLOAD_TOO_LARGE(HttpStatus.PAYLOAD_TOO_LARGE),
    /** The file has more rows than the limit. The details carry it. Nothing was kept. */
    UPLOAD_TOO_MANY_ROWS(HttpStatus.PAYLOAD_TOO_LARGE),
    /** The file is not what the mapping says: binary where text is expected, not a workbook, no such sheet. */
    UPLOAD_NOT_READABLE(HttpStatus.BAD_REQUEST),
    /** The SHA-256 the request declared is not the file's. Nothing was kept. */
    UPLOAD_HASH_MISMATCH(HttpStatus.BAD_REQUEST),
    /** The file's name is blank, too long, or not a name. */
    FILE_NAME_INVALID(HttpStatus.BAD_REQUEST),
    /** The date the export represents is not a date, or is after today. */
    AS_AT_INVALID(HttpStatus.BAD_REQUEST),
    /** No mapping has that key. */
    MAPPING_NOT_FOUND(HttpStatus.NOT_FOUND),
    /** A mapping definition that cannot be used. The details carry how many problems, and the first of them by path. */
    MAPPING_INVALID(HttpStatus.BAD_REQUEST),
    /** A mapping with that key exists already -- built in, or defined here. */
    MAPPING_KEY_TAKEN(HttpStatus.CONFLICT),
    /** A built-in mapping is never changed: define one of your own from it. */
    MAPPING_READ_ONLY(HttpStatus.CONFLICT),
    /** The mapping was changed by someone else since it was read. The details carry the version on record. */
    MAPPING_CHANGED_SINCE(HttpStatus.CONFLICT),
    /** The mapping reads another format than the file was registered in. The details carry both. */
    MAPPING_NOT_FOR_THIS_FILE(HttpStatus.CONFLICT),
    /** No batch has that number. */
    BATCH_NOT_FOUND(HttpStatus.NOT_FOUND),
    /** The batch is not in a state that allows this. The details carry its status, and the statuses that do. */
    BATCH_NOT_IN_STATE(HttpStatus.CONFLICT),
    /** Another file is being read, or another batch checked, moved or undone: one at a time. */
    MIGRATION_BUSY(HttpStatus.CONFLICT),
    /** The batch has rows with errors and the move was not asked to hold them back. The details carry how many. */
    COMMIT_HAS_ERRORS(HttpStatus.CONFLICT),
    /** Members this batch moved have been changed or used since. The details carry how many. Nothing was undone. */
    ROLLBACK_MEMBERS_CHANGED(HttpStatus.CONFLICT),
    /** A month that cannot be read, or one after the current month. */
    MONTH_INVALID(HttpStatus.BAD_REQUEST),
    /** Month-end figures that cannot be entered. The details carry the first problem, by its position. */
    TOTALS_INVALID(HttpStatus.BAD_REQUEST);

    private final HttpStatus status;

    MigrationRefusalCode(HttpStatus status) {
        this.status = status;
    }

    public HttpStatus status() {
        return status;
    }
}
