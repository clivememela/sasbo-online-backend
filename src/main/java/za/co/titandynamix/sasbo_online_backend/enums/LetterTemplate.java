package za.co.titandynamix.sasbo_online_backend.enums;

/** The union's three letters for an unpaid debit, each a resource in the union's own words. */
public enum LetterTemplate {
    /** Payment Stopped, Account Frozen, Invalid Account Number: "please contact us". */
    RETURN_DEBIT("acb/letters/return-debit.txt"),
    /** Account Closed: "tell us whether you have left the bank, or give us new banking details". */
    ACCOUNT_CLOSED("acb/letters/account-closed.txt"),
    /** Not Provided For: the letter that promises the debit will be presented again. */
    RE_DEBIT("acb/letters/re-debit.txt");

    private final String resource;

    LetterTemplate(String resource) {
        this.resource = resource;
    }

    public String resource() {
        return resource;
    }
}
