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

        assertThrows(IllegalArgumentException.class, () -> MemberValidator.validatePaymentType(member, null));
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

        assertDoesNotThrow(() -> MemberValidator.validatePaymentType(member, null));
    }

    @Test
    @DisplayName("should require a bank account number for a debit order")
    void shouldRequireABankAccountForDebitOrder() {
        // The other half of the debit-order rule, and it was untested.
        Member member = new Member();
        member.setPaymentType("Debit Order");

        assertThrows(IllegalArgumentException.class,
                () -> MemberValidator.validatePaymentType(member, null));
    }

    @Test
    @DisplayName("should accept Payroll -- the value the browser actually sends")
    void shouldAcceptPayroll() {
        // The gap the audit named. Payroll needs no bank account, which is the
        // whole reason the rule branches on payment type.
        Member member = new Member();
        member.setPaymentType("Payroll");

        assertDoesNotThrow(() -> MemberValidator.validatePaymentType(member, null));
    }

    @Test
    @DisplayName("should accept the LEGACY Payroll Deduction value, for stored data only")
    void shouldAcceptLegacyPayrollDeductionPaymentType() {
        // Renamed. This covers a backward-compatibility shim for records
        // captured under an older scheme -- it is not a requirement, and no
        // dropdown offers it.
        Member member = new Member();
        member.setPaymentType("Payroll Deduction");

        assertDoesNotThrow(() -> MemberValidator.validatePaymentType(member, null));
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

        assertDoesNotThrow(() -> MemberValidator.validatePaymentType(member, null));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    @DisplayName("should skip validation entirely when no payment type is set")
    void shouldSkipWhenPaymentTypeIsAbsent(String paymentType) {
        Member member = new Member();
        member.setPaymentType(paymentType);

        assertDoesNotThrow(() -> MemberValidator.validatePaymentType(member, null));
    }

    @Test
    @DisplayName("should skip validation when the payment type is null")
    void shouldSkipWhenPaymentTypeIsNull() {
        assertDoesNotThrow(() -> MemberValidator.validatePaymentType(new Member(), null));
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

        assertDoesNotThrow(() -> MemberValidator.validatePaymentType(member, null));
    }

    // ============================================================
    // R7-F2 / 🗣️ decision ⑥ — the range binds a CHANGE, not a stored value.
    //
    // 🔴 THE DEFECT THIS CLOSES. 38 of the 57 seeded debit-order members carry a
    // debit day outside 20-31, because the seed took the banks' ACB days and those
    // are placeholder data. The validator runs on `update` and `updateBanking` as
    // well as `create`, so opening one of those members and pressing save -- changing
    // nothing -- was a 400 naming a day the administrator never chose and could not
    // fix from the screen. Two thirds of the debit-order population was uneditable.
    //
    // 🗣️ The owner's decision, 2026-09-10: leave the seed, change the rule. Editing
    // the seed would be inventing the union's banking data; and a range that binds
    // only a deliberate CHANGE still guarantees every new day is legal, while making
    // every existing member editable either way.
    //
    // 🔑 Both directions are here. Without the second and fourth cases the rule is
    // satisfied by a validator that has stopped checking the range at all.
    // ============================================================

    @Test
    @DisplayName("🔴 an UNCHANGED out-of-range day is accepted — the member stays editable")
    void anUnchangedOutOfRangeDayIsAccepted() {
        Member member = debitOrderMemberOn(5);

        assertDoesNotThrow(() -> MemberValidator.validatePaymentType(member, 5),
                "the 5th was already stored; the caller did not change it, so saving "
                        + "this member must not be refused for a value it did not choose");
    }

    @Test
    @DisplayName("...but CHANGING it to another out-of-range day is still refused")
    void aChangedOutOfRangeDayIsRefused() {
        Member member = debitOrderMemberOn(5);

        assertThrows(IllegalArgumentException.class,
                () -> MemberValidator.validatePaymentType(member, 25),
                "the stored day was the legal 25th and the caller asked for the 5th -- "
                        + "that is a new choice and the range binds it");
    }

    @Test
    @DisplayName("...and an out-of-range day on CREATE is refused, with no previous day")
    void createStillRefusesAnOutOfRangeDay() {
        Member member = debitOrderMemberOn(5);

        assertThrows(IllegalArgumentException.class,
                () -> MemberValidator.validatePaymentType(member, null),
                "nothing was stored, so every day is a new choice");
    }

    @Test
    @DisplayName("...and changing an out-of-range day to a legal one is accepted")
    void aChangeIntoTheRangeIsAccepted() {
        // The non-vacuity half in the other direction: a validator that refused every
        // change would satisfy the two tests above and break the repair path.
        Member member = debitOrderMemberOn(31);

        assertDoesNotThrow(() -> MemberValidator.validatePaymentType(member, 5),
                "moving a member off an illegal day onto a legal one is exactly the "
                        + "correction this rule exists to encourage");
    }

    /** A debit-order member on a given day — the rule only applies to this shape. */
    private static Member debitOrderMemberOn(int debitDay) {
        Member member = new Member();
        member.setPaymentType("Debit Order");
        member.setBankAccountNumber("1234567890");
        member.setDebitDay(debitDay);
        return member;
    }
}
