package za.co.titandynamix.sasbo_online_backend.validator;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import za.co.titandynamix.sasbo_online_backend.entity.member.Member;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MemberValidatorTest {

    @Test
    @DisplayName("should reject debit order debit day outside 20, 25, and 31")
    void shouldRejectInvalidDebitDayForDebitOrder() {
        Member member = new Member();
        member.setPaymentType("Debit Order");
        member.setBankAccountNumber("1234567890");
        member.setDebitDay(15);

        assertThrows(IllegalArgumentException.class, () -> MemberValidator.validatePaymentType(member));
    }

    @Test
    @DisplayName("should allow debit order debit day when it is 20, 25, or 31")
    void shouldAllowValidDebitDayForDebitOrder() {
        Member member = new Member();
        member.setPaymentType("Debit Order");
        member.setBankAccountNumber("1234567890");
        member.setDebitDay(25);

        assertDoesNotThrow(() -> MemberValidator.validatePaymentType(member));
    }

    @Test
    @DisplayName("should accept legacy payroll deduction value for backward compatibility")
    void shouldAcceptLegacyPayrollDeductionPaymentType() {
        Member member = new Member();
        member.setPaymentType("Payroll Deduction");

        assertDoesNotThrow(() -> MemberValidator.validatePaymentType(member));
    }
}
