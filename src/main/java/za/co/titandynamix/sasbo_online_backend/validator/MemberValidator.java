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
     * @param previousDebitDay the debit day this member was STORED with before this
     *        request, or {@code null} on create. 🗣️ Decision ⑥, 2026-09-10: the
     *        20–31 range is enforced on a day the caller CHANGED, never on one it
     *        merely sent back unchanged — see {@link #debitDayWasChanged}.
     * @throws IllegalArgumentException if validation fails
     */
    public static void validatePaymentType(Member member, Integer previousDebitDay) {
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
            if (debitDay != null && !DebitDay.isInRange(debitDay)
                    && debitDayWasChanged(debitDay, previousDebitDay)) {
                throw new IllegalArgumentException(DebitDay.message());
            }
        }
    }

    /**
     * Whether this request CHOSE the debit day it is carrying.
     *
     * <p>🔴 R7-F2, and the reason this parameter exists. 38 of the 57 seeded
     * debit-order members are stored with a day outside the range, because the
     * seed took the banks' ACB days and those are placeholder data. The range was
     * checked on every write, so opening one of those members and saving it
     * <b>unchanged</b> was a 400 — naming a day the administrator never entered
     * and cannot correct from the screen. Two thirds of the population was
     * uneditable, and the error blamed the user for the seed.
     *
     * <p>🗣️ Settled by the owner on 2026-09-10: leave the seed, change the rule.
     * A value sent back unchanged was not chosen by this caller, so there is
     * nothing to refuse; the first deliberate change still has to land in range,
     * which is what the rule is for.
     *
     * <p>A {@code null} {@code previousDebitDay} means create: nothing was stored,
     * so every day is a new choice and the range binds all of them.
     */
    private static boolean debitDayWasChanged(Integer submitted, Integer previousDebitDay) {
        return !submitted.equals(previousDebitDay);
    }

    /**
     * The SASBO-structure answers must agree with each other.
     *
     * <p>🗣️ Stakeholder, 2026-09-17: <i>"For a member to participate in COSATU
     * structures, they have to be within the BEC structure."</i> And a COSATU Local
     * or Regional structure is only something a COSATU member sits on.
     *
     * <p>🔑 An explicit {@code isBec = false} beside COSATU is a contradiction and is
     * always refused. A {@code null} BEC answer is different: rows stored before V29
     * hold {@code is_cosatu = 'Y'} and no BEC answer at all, because the column did
     * not exist. Refusing those would make every such member unsaveable over a
     * question nobody could have answered -- decision ⑥'s lesson, one rule over. So
     * a null BEC answer is refused only when THIS request turns COSATU on.
     *
     * @param previousIsCosatu the stored {@code isCosatu} before this request, or
     *        {@code null} on create
     * @throws IllegalArgumentException naming the rule, for the screen to show
     */
    public static void validateStructures(Member member, String previousIsCosatu) {
        boolean cosatu = isYes(member.getIsCosatu());
        if (cosatu && !Boolean.TRUE.equals(member.getIsBec())) {
            boolean contradicted = Boolean.FALSE.equals(member.getIsBec());
            if (contradicted || !isYes(previousIsCosatu)) {
                throw new IllegalArgumentException(
                        "A member must be a BEC member to take part in COSATU structures");
            }
        }
        if (!cosatu && (Boolean.TRUE.equals(member.getIsCosatuLocal())
                || Boolean.TRUE.equals(member.getIsCosatuRegional()))) {
            throw new IllegalArgumentException(
                    "A COSATU Local or Regional structure can only be recorded for a COSATU member");
        }
    }

    /** {@code isCosatu} is stored as Y/N (V19); older clients sent "true". */
    private static boolean isYes(String flag) {
        return flag != null && (flag.equalsIgnoreCase("Y") || flag.equalsIgnoreCase("true")
                || flag.equalsIgnoreCase("Yes"));
    }
}
