package za.co.titandynamix.sasbo_online_backend.enums;

/**
 * What a member's status MEANS to the figures -- the only thing a figure reads of it (month end 03.2, PA-4). A status
 * is renamed at the table with SASBO, or not; either way no figure moves, because a figure never reads a status's
 * spelling, only its kind.
 *
 * <p>Published with each status in {@code api-vocabulary.json} ({@code memberStatuses}): the browser reads the kind
 * from there, and the partial index of the figures (02.2) is held to the leaving kind by a test.
 */
public enum MemberStatusKind {
    /** Off the record for every figure but <i>left</i>: deleted, in SASBO's word, with a deletion code (rule L). */
    LEAVING,
    /** On record and not paying: suspended, with a suspension code (rule U). */
    SUSPENDED,
    /** On record and paying. */
    NEITHER
}
