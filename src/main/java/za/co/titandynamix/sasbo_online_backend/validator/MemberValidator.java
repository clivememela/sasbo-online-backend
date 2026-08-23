package za.co.titandynamix.sasbo_online_backend.validator;

import za.co.titandynamix.sasbo_online_backend.entity.member.Member;
import za.co.titandynamix.sasbo_online_backend.enums.PaymentType;

/**
 * Validator for Member entity business rules.
 * Enforces conditional field requirements based on payment type.
 */
public class MemberValidator {

    private static final int DEBIT_DAY_20 = 20;
    private static final int DEBIT_DAY_25 = 25;
    private static final int DEBIT_DAY_31 = 31;

    /**
     * Validate member payment type and banking information.
     * If payment type is Debit Order, bank account number is required.
     * If payment type is Payroll, bank account is not required.
     *
     * @param member The member to validate
     * @throws IllegalArgumentException if validation fails
     */
    public static void validatePaymentType(Member member) {
        String paymentTypeStr = member.getPaymentType();
        
        if (paymentTypeStr == null || paymentTypeStr.isBlank()) {
            // Payment type not set — not validated here
            return;
        }

        // Resolve the payment type; skip validation for unrecognised values
        // (e.g. legacy data such as "Cash" stored before the current scheme)
        PaymentType paymentType;
        try {
            paymentType = PaymentType.fromString(paymentTypeStr);
        } catch (IllegalArgumentException ignored) {
            // Unknown payment type — allow it through; banking rules cannot be enforced
            return;
        }

        if (paymentType.requiresBankAccount()) {
            if (member.getBankAccountNumber() == null || member.getBankAccountNumber().isBlank()) {
                throw new IllegalArgumentException(
                    "Bank account number is required when payment type is '" + paymentType.getDisplayName() + "'"
                );
            }

            Integer debitDay = member.getDebitDay();
            if (debitDay != null
                    && debitDay != DEBIT_DAY_20
                    && debitDay != DEBIT_DAY_25
                    && debitDay != DEBIT_DAY_31) {
                throw new IllegalArgumentException(
                        "Debit day must be one of 20, 25 or 31 when payment type is '"
                                + paymentType.getDisplayName() + "'"
                );
            }
        }
    }
}
