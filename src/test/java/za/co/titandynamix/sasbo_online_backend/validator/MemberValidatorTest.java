package za.co.titandynamix.sasbo_online_backend.validator;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import za.co.titandynamix.sasbo_online_backend.entity.member.Member;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Adopted from the pre-existing suite, with the gap the audit named filled in.
 *
 * <p>Its three original tests are sound: real static logic, no mocks, nothing
 * asserting its own stub. What was missing is the case that actually happens
 * -- the browser sends {@code 'Payroll'}, and the only payroll test covered
 * {@code 'Payroll Deduction'}, a legacy shim.
 *
 * <p><b>A suite that only covers the legacy value encodes the drift as the
 * specification.</b> The shim test is kept and renamed to say what it is; the
 * value the browser really sends now has its own.
 */
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

    @ParameterizedTest
    @ValueSource(ints = {20, 25, 31})
    @DisplayName("should allow every debit day the rule permits, not just one of them")
    void shouldAllowEveryValidDebitDay(int debitDay) {
        // Was a single case at 25. Three values are named in the rule and only
        // one was covered, so two thirds of it rested on nothing.
        Member member = new Member();
        member.setPaymentType("Debit Order");
        member.setBankAccountNumber("1234567890");
        member.setDebitDay(debitDay);

        assertDoesNotThrow(() -> MemberValidator.validatePaymentType(member));
    }

    @Test
    @DisplayName("should require a bank account number for a debit order")
    void shouldRequireABankAccountForDebitOrder() {
        // The other half of the debit-order rule, and it was untested.
        Member member = new Member();
        member.setPaymentType("Debit Order");

        assertThrows(IllegalArgumentException.class,
                () -> MemberValidator.validatePaymentType(member));
    }

    @Test
    @DisplayName("should accept Payroll -- the value the browser actually sends")
    void shouldAcceptPayroll() {
        // The gap the audit named. Payroll needs no bank account, which is the
        // whole reason the rule branches on payment type.
        Member member = new Member();
        member.setPaymentType("Payroll");

        assertDoesNotThrow(() -> MemberValidator.validatePaymentType(member));
    }

    @Test
    @DisplayName("should accept the LEGACY Payroll Deduction value, for stored data only")
    void shouldAcceptLegacyPayrollDeductionPaymentType() {
        // Renamed. This covers a backward-compatibility shim for records
        // captured under an older scheme -- it is not a requirement, and no
        // dropdown offers it.
        Member member = new Member();
        member.setPaymentType("Payroll Deduction");

        assertDoesNotThrow(() -> MemberValidator.validatePaymentType(member));
    }

    @Test
    @DisplayName("should let an unrecognised payment type through, deliberately")
    void shouldLetAnUnknownPaymentTypeThrough() {
        // Current behaviour, and it is intentional: legacy rows carry values
        // such as "Cash" that predate this scheme, and rejecting them here
        // would make an old record unsavable. Asserted so that changing it is a
        // decision rather than an accident.
        Member member = new Member();
        member.setPaymentType("Cash");

        assertDoesNotThrow(() -> MemberValidator.validatePaymentType(member));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    @DisplayName("should skip validation entirely when no payment type is set")
    void shouldSkipWhenPaymentTypeIsAbsent(String paymentType) {
        Member member = new Member();
        member.setPaymentType(paymentType);

        assertDoesNotThrow(() -> MemberValidator.validatePaymentType(member));
    }

    @Test
    @DisplayName("should skip validation when the payment type is null")
    void shouldSkipWhenPaymentTypeIsNull() {
        assertDoesNotThrow(() -> MemberValidator.validatePaymentType(new Member()));
    }

    @Test
    @DisplayName("should not police the debit day when the payment type is Payroll")
    void shouldNotPoliceDebitDayForPayroll() {
        // The negative case that proves the rule is conditional rather than
        // universal. Without it, the validator could reject day 15 for every
        // payment type and every other test here would still pass.
        Member member = new Member();
        member.setPaymentType("Payroll");
        member.setDebitDay(15);

        assertDoesNotThrow(() -> MemberValidator.validatePaymentType(member));
    }
}
