package za.co.titandynamix.sasbo_online_backend.legacy.rules;

/** A checked row: VALID moves; WARNING moves and is listed; ERROR is held back and listed. */
public enum Outcome {
    VALID,
    WARNING,
    ERROR;

    /** Whether a row of this outcome is moved. */
    public boolean moves() {
        return this != ERROR;
    }
}
