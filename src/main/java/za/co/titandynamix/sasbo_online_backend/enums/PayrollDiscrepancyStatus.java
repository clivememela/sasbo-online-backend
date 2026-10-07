package za.co.titandynamix.sasbo_online_backend.enums;

/**
 * Where a payroll discrepancy stands. Every discrepancy stays open, with its notes, until it is resolved (Stephne,
 * PAY-20); one on a query list sent to the employer is {@link #QUERIED} until then.
 */
public enum PayrollDiscrepancyStatus {
    OPEN,
    QUERIED,
    RESOLVED
}
