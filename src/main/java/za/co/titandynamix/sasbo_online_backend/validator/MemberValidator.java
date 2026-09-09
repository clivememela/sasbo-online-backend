package za.co.titandynamix.sasbo_online_backend.validator;

import za.co.titandynamix.sasbo_online_backend.utils.DebitDay;
import za.co.titandynamix.sasbo_online_backend.entity.member.Member;
import za.co.titandynamix.sasbo_online_backend.enums.PaymentType;

/**
 * Validator for Member entity business rules.
 * Enforces conditional field requirements based on payment type.
 */
public class MemberValidator {

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

            // The permitted range lives in DebitDay, once. It was {20, 25, 31}
            // here AND in three client files -- and it rejected the server's OWN
            // reference data: the service sets debitDay from the bank's
            // subscription row, and 13 of the 15 rows fall outside that set. This
            // validator runs on create, update AND updateBanking, so writing a
            // debit-order member back UNCHANGED was a 400 for most of them.
            Integer debitDay = member.getDebitDay();
            if (debitDay != null && !DebitDay.isInRange(debitDay)) {
                throw new IllegalArgumentException(DebitDay.message());
            }
        }
    }
}
