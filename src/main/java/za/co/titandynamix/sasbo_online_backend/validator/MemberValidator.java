package za.co.titandynamix.sasbo_online_backend.validator;

import za.co.titandynamix.sasbo_online_backend.entity.Member;
import za.co.titandynamix.sasbo_online_backend.entity.PaymentType;

/**
 * Validator for Member entity business rules.
 * Enforces conditional field requirements based on payment type.
 */
public class MemberValidator {

    /**
     * Validate member payment type and banking information.
     * If payment type is Debit Order, bank account number is required.
     * If payment type is Payroll Deduction, bank account is not required.
     *
     * @param member The member to validate
     * @throws IllegalArgumentException if validation fails
     */
    public static void validatePaymentType(Member member) {
        String paymentTypeStr = member.getPaymentType();
        
        if (paymentTypeStr == null || paymentTypeStr.isBlank()) {
            // Payment type not set - not validated here, will be caught by @NotNull if configured
            return;
        }

        try {
            PaymentType paymentType = PaymentType.fromString(paymentTypeStr);
            
            if (paymentType.requiresBankAccount()) {
                if (member.getBankAccountNumber() == null || member.getBankAccountNumber().isBlank()) {
                    throw new IllegalArgumentException(
                        "Bank account number is required when payment type is 'Debit Order'"
                    );
                }
            } else if (paymentType == PaymentType.PAYROLL_DEDUCTION) {
                // For payroll deduction, bank account is optional
                // No validation needed
            }
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid payment type: " + paymentTypeStr);
        }
    }
}
