package za.co.titandynamix.sasbo_online_backend.enums;

/**
 * The money paid in for a month, set against the file's total less the employer's fee (Stephne, PAY-28, PAY-27).
 */
public enum PayrollPaymentStatus {
    NOT_RECORDED,
    SHORT,
    EXACT,
    OVER
}
