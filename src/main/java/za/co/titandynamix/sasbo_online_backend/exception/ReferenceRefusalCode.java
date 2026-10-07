package za.co.titandynamix.sasbo_online_backend.exception;

import org.springframework.http.HttpStatus;

/**
 * Why a change to SASBO's reference lists -- banks and their branches, the subscription rows, the recruitment channels
 * -- was refused, each with the status it answers. The browser may branch on the name; the sentence a person reads is
 * the refusal's message. Published in {@code api-vocabulary.json} ({@code reference.refusalCodes}).
 */
public enum ReferenceRefusalCode {

    /** No branch on record has this id: removed since the list was loaded, or never there. */
    BRANCH_NOT_FOUND(HttpStatus.NOT_FOUND),
    /** Another branch of the same bank already has this branch code ({@code bank_branches}' unique key). */
    BRANCH_CODE_TAKEN(HttpStatus.CONFLICT),
    /** A branch names a bank that is not on record; a branch is never the way a bank is created. */
    BANK_UNKNOWN(HttpStatus.UNPROCESSABLE_ENTITY),
    /** The bank already has its subscription row; that row is changed, not added to. */
    SUBSCRIPTION_BANK_TAKEN(HttpStatus.CONFLICT),
    /** No recruitment channel has this id: removed since the list was loaded, or never there. */
    CHANNEL_NOT_FOUND(HttpStatus.NOT_FOUND),
    /** No member with this id is on record within the caller's region or area. */
    MEMBER_NOT_FOUND(HttpStatus.NOT_FOUND),
    /** The member was recruited through a different channel from the one named: the screen's view is stale. */
    MEMBER_NOT_IN_CHANNEL(HttpStatus.CONFLICT),
    /** A page, a page size or a search the routes do not take; the details name the field. */
    REQUEST_INVALID(HttpStatus.BAD_REQUEST);

    private final HttpStatus status;

    ReferenceRefusalCode(HttpStatus status) {
        this.status = status;
    }

    public HttpStatus status() {
        return status;
    }
}
