package za.co.titandynamix.sasbo_online_backend.enums;

/**
 * The union's letters for an unpaid debit, and the one for a membership ended after three unpaids -- each a
 * resource in the union's own words. V71's {@code acb_letters_template_known} CHECK names exactly these.
 *
 * <p>Which reason sends which is {@link UnpaidReason#letter()}. The three added for the October build
 * ({@link #PAYMENT_STOPPED}, {@link #NO_AUTHORITY}, {@link #MEMBERSHIP_ENDED}) start as copies of the return-debit
 * text, PROVISIONAL: the letters' words and layout are rewritten for the letters review SASBO asked for ("show it to
 * us", DOD-33), and nothing is sent to a member before it.
 */
public enum LetterTemplate {
    /** Account Frozen, Invalid Account Number: "please contact us". */
    RETURN_DEBIT("acb/letters/return-debit.txt"),
    /** Account Closed: "tell us whether you have left the bank, or give us new banking details". */
    ACCOUNT_CLOSED("acb/letters/account-closed.txt"),
    /** Not Provided For: the letter that promises the debit will be presented again. */
    RE_DEBIT("acb/letters/re-debit.txt"),
    /** Payment Stopped by the member: never re-debited (Stephne, DOD-03). */
    PAYMENT_STOPPED("acb/letters/payment-stopped.txt"),
    /** No Authority To Debit -- the bank's 30, 31, 32 and 34, "all the same" (Stephne, DOD-04). */
    NO_AUTHORITY("acb/letters/no-authority.txt"),
    /** The short letter a member gets when a staff member confirms their ending after the third unpaid (DOD-24). */
    MEMBERSHIP_ENDED("acb/letters/membership-ended.txt");

    private final String resource;

    LetterTemplate(String resource) {
        this.resource = resource;
    }

    public String resource() {
        return resource;
    }
}
