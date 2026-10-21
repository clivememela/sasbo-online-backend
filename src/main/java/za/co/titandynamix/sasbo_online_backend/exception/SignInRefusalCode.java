package za.co.titandynamix.sasbo_online_backend.exception;

import org.springframework.http.HttpStatus;

/**
 * Every way sign-in says no, as a stable code with its HTTP status (signin.md).
 *
 * <p>The browser branches on the code, never on the sentence; the codes are published in {@code api-vocabulary.json}
 * under {@code auth}, and the browser's parity test lists the ones it acts on. Like month end's codes, a code arrives
 * in the commit that first uses it, and two guards hold the list to the code: every constant is published with its
 * status, and every constant is used somewhere ({@code SignInGuardsTest}).
 */
public enum SignInRefusalCode {
    /** A request that could not be read: not JSON, a word where a number is wanted, a parameter missing. */
    REQUEST_MALFORMED(HttpStatus.BAD_REQUEST),
    /** A field that breaks its rule; {@code validationErrors} says which, field by field, in a sentence each. */
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST),
    /** Wrong username or password -- the same answer for both, so nobody learns which usernames exist. */
    SIGN_IN_FAILED(HttpStatus.UNAUTHORIZED),
    /** Too many sign-ins from one address, or from everyone, in a minute; {@code Retry-After} says when to try again. */
    SIGN_IN_THROTTLED(HttpStatus.TOO_MANY_REQUESTS),
    /** Five failed attempts: locked for fifteen minutes. The details say until when. */
    ACCOUNT_LOCKED(HttpStatus.LOCKED),
    /** The right password for an account that has been closed. Only said to somebody who knew the password. */
    ACCOUNT_INACTIVE(HttpStatus.FORBIDDEN),
    /** Nobody is signed in, and the route needs somebody. */
    SIGN_IN_REQUIRED(HttpStatus.UNAUTHORIZED),
    /** The request came with a token that opens no session any more -- idle, expired, revoked or unknown. */
    SESSION_EXPIRED(HttpStatus.UNAUTHORIZED),
    /** Signed in, without the permission the route names; the answer names it. */
    PERMISSION_REQUIRED(HttpStatus.FORBIDDEN),
    /** Signed in with a temporary password, which opens nothing until it is changed. */
    PASSWORD_CHANGE_REQUIRED(HttpStatus.FORBIDDEN),
    /** A new password that breaks a rule; the details name the rule. */
    PASSWORD_RULES(HttpStatus.BAD_REQUEST),
    /** Another user already signs in with that username. */
    USERNAME_TAKEN(HttpStatus.CONFLICT),
    /** A scope that cannot be: everything with codes, a region or area without one, a code not on record. */
    SCOPE_INVALID(HttpStatus.BAD_REQUEST),
    /** The record changed since the screen read it. The details carry the version on record. Nothing was written. */
    STALE_VERSION(HttpStatus.CONFLICT),
    /** The change would leave no active user able to administer users -- SASBO would be locked out. */
    LAST_ADMINISTRATOR(HttpStatus.CONFLICT),
    /** A role holding a permission the administrator does not hold -- granted, edited, or held by the user changed. */
    ROLE_ABOVE_YOUR_OWN(HttpStatus.FORBIDDEN),
    /** A scope wider than the administrator's own -- granted, or held by the user changed. */
    SCOPE_ABOVE_YOUR_OWN(HttpStatus.FORBIDDEN),
    /** No user has that id. */
    USER_NOT_FOUND(HttpStatus.NOT_FOUND),
    /** No position has that code. */
    POSITION_NOT_FOUND(HttpStatus.NOT_FOUND),
    /** No role has that code. */
    ROLE_NOT_FOUND(HttpStatus.NOT_FOUND),
    /** A user or position names a position that is not on record. */
    POSITION_UNKNOWN(HttpStatus.BAD_REQUEST),
    /** A new user's position is no longer in use. */
    POSITION_INACTIVE(HttpStatus.BAD_REQUEST),
    /** A user or position names a role that is not on record. */
    ROLE_UNKNOWN(HttpStatus.BAD_REQUEST),
    /** A role names a permission the catalogue does not have. The details name the first. */
    PERMISSION_UNKNOWN(HttpStatus.BAD_REQUEST),
    /** Another position already has that code. */
    POSITION_CODE_TAKEN(HttpStatus.CONFLICT),
    /** Another role already has that code. */
    ROLE_CODE_TAKEN(HttpStatus.CONFLICT),
    /** The System Administrator's role holds every permission, and its permissions are not edited. */
    ROLE_FIXED(HttpStatus.CONFLICT),
    /** A page that does not exist: below nought, or a size below one. The details carry the ceiling. */
    PAGE_INVALID(HttpStatus.BAD_REQUEST),
    /** A filter of the record that cannot be read, or a range that ends before it begins. The details name it. */
    AUDIT_FILTER_INVALID(HttpStatus.BAD_REQUEST),
    /** More positions or roles than an administrator keeps -- the ceiling is in the details. */
    TOO_MANY(HttpStatus.CONFLICT),
    /** The change conflicts with a record already there, in a way no rule above names. Nothing was written. */
    RECORD_CONFLICT(HttpStatus.CONFLICT);

    private final HttpStatus status;

    SignInRefusalCode(HttpStatus status) {
        this.status = status;
    }

    public HttpStatus status() {
        return status;
    }
}
