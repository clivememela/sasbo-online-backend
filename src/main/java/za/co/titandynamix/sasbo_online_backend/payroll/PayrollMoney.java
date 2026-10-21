package za.co.titandynamix.sasbo_online_backend.payroll;

import za.co.titandynamix.sasbo_online_backend.enums.PayrollPaymentStatus;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * The payment check, worked out in one place (Stephne, PAY-28: "Yes, in this module"): what an employer should pay
 * over is its file's total less the fee it keeps -- Absa keeps 3% (PAY-27) -- and the money received is set against
 * that. Whether Absa's 3% is taken from every deduction is still to be confirmed (PAY.md, clarification 5), so the fee
 * is the employer's setting, a percentage of the file's total, rounded to the cent.
 */
public final class PayrollMoney {

    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private PayrollMoney() {
    }

    /** The fee the employer keeps: the file's total times its percentage, to the cent. */
    public static BigDecimal adminFee(BigDecimal fileTotal, BigDecimal feePercent) {
        if (fileTotal == null) {
            return null;
        }
        BigDecimal percent = feePercent == null ? BigDecimal.ZERO : feePercent;
        return fileTotal.multiply(percent).divide(HUNDRED, 2, RoundingMode.HALF_UP);
    }

    /** What the employer should pay over: the file's total less its fee. */
    public static BigDecimal expectedPayment(BigDecimal fileTotal, BigDecimal feePercent) {
        return fileTotal == null ? null : fileTotal.setScale(2, RoundingMode.HALF_UP)
                .subtract(adminFee(fileTotal, feePercent));
    }

    /** Where the money stands: nothing recorded, short, exact (to the cent) or over. */
    public static PayrollPaymentStatus status(boolean anyPayment, BigDecimal received, BigDecimal expected) {
        if (!anyPayment || received == null || expected == null) {
            return PayrollPaymentStatus.NOT_RECORDED;
        }
        int compared = received.setScale(2, RoundingMode.HALF_UP).compareTo(expected.setScale(2, RoundingMode.HALF_UP));
        return compared == 0 ? PayrollPaymentStatus.EXACT
                : compared < 0 ? PayrollPaymentStatus.SHORT : PayrollPaymentStatus.OVER;
    }
}
