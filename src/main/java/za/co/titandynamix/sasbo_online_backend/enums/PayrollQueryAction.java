package za.co.titandynamix.sasbo_online_backend.enums;

/**
 * What a line of an employer's query list asks of the employer. Stephne, PAY-21: "We send them the discrepancy queries -
 * To add or delete." -- and PAY-16, "Ask the employer to correct it", for an amount.
 */
public enum PayrollQueryAction {

    /** Start or resume deducting this member. */
    ADD("Add", "Please start deducting this member's subscription."),
    /** Stop deducting this person. */
    DELETE("Delete", "Please stop this deduction."),
    /** Correct the amount deducted. */
    CORRECT("Correct", "Please correct the amount deducted.");

    private final String label;
    private final String request;

    PayrollQueryAction(String label, String request) {
        this.label = label;
        this.request = request;
    }

    public String label() {
        return label;
    }

    /** The sentence the employer reads beside the line. */
    public String request() {
        return request;
    }
}
