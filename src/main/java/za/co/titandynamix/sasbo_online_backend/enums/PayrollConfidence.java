package za.co.titandynamix.sasbo_online_backend.enums;

/**
 * How sure a match is. {@link #HIGH}: two details agree. {@link #MEDIUM}: the detail the match was made on agrees and
 * nothing else on the line contradicts it, or one other detail differs. {@link #LOW}: a name alone, or a match whose
 * other details all differ -- which is why each of those raises its own discrepancy for a person to check.
 */
public enum PayrollConfidence {
    HIGH,
    MEDIUM,
    LOW
}
