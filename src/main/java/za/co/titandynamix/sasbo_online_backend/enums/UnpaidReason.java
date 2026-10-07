package za.co.titandynamix.sasbo_online_backend.enums;

/**
 * Why the bank returned a debit unpaid, in the union's words -- and which of its letters goes out.
 *
 * <p>The bank prints a CODE; this is the reason a code means, looked up in {@code acb_unpaid_codes}
 * (data, never code). Which letter each reason sends is the table the letters review works from (October build):
 * Not Provided For the re-debit letter, Payment Stopped and No Authority their own, Account Closed its own, Account
 * Frozen (the bank's 6) and Invalid Account Number (the bank's 26) the return-debit letter. A deceased account holder
 * is sent nothing (the bank's 18): which of SASBO's codes it records is still asked (Friday item 1). {@link #OTHER} is a
 * code the table does not know: kept with its raw value, counted on the run, never dropped, and sent nothing.
 */
public enum UnpaidReason {
    NOT_PROVIDED_FOR("Not Provided For", LetterTemplate.RE_DEBIT),
    PAYMENT_STOPPED("Payment Stopped", LetterTemplate.PAYMENT_STOPPED),
    ACCOUNT_FROZEN("Account Frozen", LetterTemplate.RETURN_DEBIT),
    INVALID_ACCOUNT_NUMBER("Invalid Account Number", LetterTemplate.RETURN_DEBIT),
    ACCOUNT_CLOSED("Account Closed", LetterTemplate.ACCOUNT_CLOSED),
    NO_AUTHORITY_TO_DEBIT("No Authority To Debit", LetterTemplate.NO_AUTHORITY),
    /** The bureau list's wording for 34, which SASBO reads as no authority (DOD-04); kept for lines recorded under it. */
    AUTHORISATION_CANCELLED("Authorisation Cancelled", LetterTemplate.NO_AUTHORITY),
    /** The bank's 18 on SASBO's sheet, "Deceased": no letter, and no SASBO code on record yet (Friday item 1). */
    ACCOUNT_HOLDER_DECEASED("Deceased", null),
    OTHER("Unrecognised bank code", null);

    private final String wording;
    private final LetterTemplate letter;

    UnpaidReason(String wording, LetterTemplate letter) {
        this.wording = wording;
        this.letter = letter;
    }

    /** The wording the union's letters print. */
    public String wording() {
        return wording;
    }

    /** The letter this reason sends, or {@code null} when it sends none. */
    public LetterTemplate letter() {
        return letter;
    }
}
