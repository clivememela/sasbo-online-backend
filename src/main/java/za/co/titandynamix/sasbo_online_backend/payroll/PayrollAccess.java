package za.co.titandynamix.sasbo_online_backend.payroll;

import za.co.titandynamix.sasbo_online_backend.exception.PayrollRefusal;
import za.co.titandynamix.sasbo_online_backend.exception.PayrollRefusalCode;
import za.co.titandynamix.sasbo_online_backend.security.CallerScope;

/**
 * Payroll is head office's: an employer's file covers members in every region, so a caller limited to a region or an
 * area is refused, whatever permission their role was given (Stephne, PAY-32: "Administrators at head office"; PAY-33:
 * organisers do not see discrepancies). The permission itself is the route's {@code @PreAuthorize}; this is the scope.
 */
public final class PayrollAccess {

    private PayrollAccess() {
    }

    /** @throws PayrollRefusal {@code HEAD_OFFICE_ONLY} for a caller whose scope is not everything */
    public static void headOffice() {
        if (!CallerScope.current().isAll()) {
            throw new PayrollRefusal(PayrollRefusalCode.HEAD_OFFICE_ONLY, "Payroll reconciliation covers every "
                    + "region, so it is open to head-office users only.");
        }
    }
}
