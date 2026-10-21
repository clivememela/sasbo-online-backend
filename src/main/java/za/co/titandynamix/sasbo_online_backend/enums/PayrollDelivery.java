package za.co.titandynamix.sasbo_online_backend.enums;

/**
 * How an employer's file reaches SASBO (Stephne, PAY-04): by e-mail, or from the employer's secure server -- Standard
 * Bank's, "SB". Either way an administrator loads it here; the system does not collect it itself.
 */
public enum PayrollDelivery {
    EMAIL,
    SFTP
}
