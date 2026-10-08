package za.co.titandynamix.sasbo_online_backend.enums;

/**
 * What the amount on a matched line says, compared with the subscription for the month (Stephne, PAY-16, PAY-23).
 */
public enum PayrollAmountStatus {
    /** The month's subscription. */
    MATCHES,
    /**
     * One larger amount that is a whole number of months' subscriptions, each priced at its own month's rate, for a
     * member with months owed: the employer catching up (PAY-25, PAY-26: "the amount next to member will show the
     * arrear amont").
     */
    CATCH_UP,
    /** Anything else. */
    DIFFERS,
    /** One of two or more lines for the same member: deducted twice, or a catch-up printed twice (PAY-18, PAY-26). */
    LISTED_TWICE,
    /**
     * The amount needs the subscription of a month that has no rate on record -- never priced at another month's rate.
     * The match names the month.
     */
    RATE_UNKNOWN
}
