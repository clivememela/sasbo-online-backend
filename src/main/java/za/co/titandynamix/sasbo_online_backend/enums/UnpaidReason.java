package za.co.titandynamix.sasbo_online_backend.enums;

/**
 * Why the bank returned a debit unpaid, in the union's words -- and which of its letters goes out.
 *
 * <p>The bank prints a CODE; this is the reason a code means, looked up in {@code acb_unpaid_codes}
 * (data, never code). The first five are the reasons the union's letters name; the two after them
 * appeared on the bank's August report and are inferred from the bureau list, with no letter of
 * their own until SASBO says which applies. {@link #OTHER} is a code the table does not know: kept
 * with its raw value, counted on the run, never dropped.
 */
public enum UnpaidReason {
    NOT_PROVIDED_FOR("Not Provided For", LetterTemplate.RE_DEBIT),
    PAYMENT_STOPPED("Payment Stopped", LetterTemplate.RETURN_DEBIT),
    ACCOUNT_FROZEN("Account Frozen", LetterTemplate.RETURN_DEBIT),
    INVALID_ACCOUNT_NUMBER("Invalid Account Number", LetterTemplate.RETURN_DEBIT),
    ACCOUNT_CLOSED("Account Closed", LetterTemplate.ACCOUNT_CLOSED),
    NO_AUTHORITY_TO_DEBIT("No Authority To Debit", null),
    AUTHORISATION_CANCELLED("Authorisation Cancelled", null),
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

    /** The letter this reason sends, or {@code null} when no letter is known for it yet. */
    public LetterTemplate letter() {
        return letter;
    }
}
