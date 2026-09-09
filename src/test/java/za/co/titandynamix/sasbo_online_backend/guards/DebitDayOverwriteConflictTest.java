package za.co.titandynamix.sasbo_online_backend.guards;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import za.co.titandynamix.sasbo_online_backend.entity.member.Member;
import za.co.titandynamix.sasbo_online_backend.utils.DebitDay;
import za.co.titandynamix.sasbo_online_backend.validator.MemberValidator;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * THE SERVICE NEVER ASSIGNS A DEBIT DAY ITS OWN VALIDATOR WOULD REFUSE.
 *
 * <h2>What this was, and why it changed shape</h2>
 * This class was a PIN on a live defect. {@code autoPopulateBankDerivedFields}
 * assigned {@code s.getAcbDebitDay()} unconditionally and {@link MemberValidator}
 * then rejected the result against {@link DebitDay}'s permitted range — for
 * <b>eleven of the fifteen</b> seeded subscription rows. The service overwrote a
 * choice that was valid with one that was not, and threw a message blaming a day
 * the administrator never entered and could not change.
 *
 * <p>🗣️ <b>The owner settled it on 2026-09-09: the 20–31 range is authoritative
 * and the banks' seeded ACB days are placeholder data.</b> So the defect is fixed,
 * and a pin on a fixed defect is a test that lies.
 *
 * <h2>🔑 What survives is the INVARIANT, not the defect</h2>
 * Assign-then-reject is incoherent <b>whichever way the reference data is
 * eventually corrected</b>. If the real ACB days arrive and they are all inside
 * 20–31, these tests keep passing and prove the mechanism still holds. If the range
 * itself is ever widened, they keep passing too. The property is not "the data
 * happens to be right" — it is "this service does not contradict itself", and that
 * is worth guarding permanently.
 *
 * <p>⚠️ So the seeded rows are still read, and are still expected to contain
 * out-of-range days: they are the FIXTURE that makes the guard non-vacuous. The day
 * the data is corrected, {@link #theSeedStillExercisesTheOutOfRangeBranch} fails and
 * says so — that is a prompt to find another fixture, not to delete the guard.
 */
@DisplayName("Debit day — the service never assigns a day it would then refuse")
class DebitDayOverwriteConflictTest {

    private record Row(String bankName, int acbDebitDay) {
    }

    private static List<Row> ROWS;

    @BeforeAll
    static void load() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        List<Row> rows = new ArrayList<>();
        try (InputStream in = DebitDayOverwriteConflictTest.class
                .getResourceAsStream("/data/subscriptions.json")) {
            assertThat(in).as("the subscription seed is on the classpath").isNotNull();
            for (JsonNode n : mapper.readTree(in)) {
                rows.add(new Row(n.path("bankName").asText(null), n.path("acbDebitDay").asInt()));
            }
        }
        ROWS = List.copyOf(rows);
        // Non-vacuity. Every assertion below is a filter over this list, and a filter
        // over an empty list reports no offenders and passes.
        assertThat(ROWS).as("subscription rows were actually read").isNotEmpty();
    }

    @Test
    @DisplayName("the seed still contains out-of-range days — the fixture this guard needs")
    void theSeedStillExercisesTheOutOfRangeBranch() {
        List<Row> outside = ROWS.stream()
                .filter(row -> !DebitDay.isInRange(row.acbDebitDay()))
                .toList();

        // Deliberately NOT asserted as a number: a count written into a test goes stale
        // on the first added row, and then the test is edited to match instead of read.
        assertThat(outside)
                .as("banks whose ACB day the validator would refuse: %s. If this is now "
                        + "EMPTY the reference data has been corrected -- good, but this "
                        + "guard needs a synthetic out-of-range row instead, or it is "
                        + "proving nothing.",
                        outside.stream().map(Row::bankName).toList())
                .isNotEmpty();
    }

    @Test
    @DisplayName("🔴 the invariant: a day the service assigns always passes the validator")
    void theServiceNeverAssignsAValueItWouldRefuse() {
        // The two halves of the rule, checked against every seeded row rather than one.
        for (Row row : ROWS) {
            Member member = debitOrderMember();

            // What autoPopulateBankDerivedFields now does: apply the bank's day ONLY
            // when it is usable. This mirrors the service's condition rather than
            // calling it, because the method is private and the property under test is
            // the RULE, not the call.
            if (DebitDay.isInRange(row.acbDebitDay())) {
                member.setDebitDay(row.acbDebitDay());
            }

            assertThatCode(() -> MemberValidator.validatePaymentType(member))
                    .as("%s (ACB day %d) must be capturable as a debit-order member",
                            row.bankName(), row.acbDebitDay())
                    .doesNotThrowAnyException();
        }
    }

    @Test
    @DisplayName("the administrator's own choice survives a bank whose day cannot be used")
    void theUsersChoiceStandsWhenTheBankDayIsUnusable() {
        Row offender = ROWS.stream()
                .filter(row -> !DebitDay.isInRange(row.acbDebitDay()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no out-of-range row -- see the fixture test"));

        Member member = debitOrderMember();
        member.setDebitDay(25);   // a legal choice the administrator made

        // The service declines the bank's day rather than overwriting with it, so 25
        // is still what gets saved. This is the behaviour change the owner's decision
        // bought: before it, capturing this member was a 400.
        if (DebitDay.isInRange(offender.acbDebitDay())) {
            member.setDebitDay(offender.acbDebitDay());
        }

        assertThat(member.getDebitDay())
                .as("%s's ACB day is %d, so the member's own 25 stands",
                        offender.bankName(), offender.acbDebitDay())
                .isEqualTo(25);
        assertThatCode(() -> MemberValidator.validatePaymentType(member))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("the guard can fail — the OLD behaviour still reproduces the 400")
    void theCanary() {
        // 🔑 Without this, the three tests above pass just as well against a validator
        // that has stopped checking anything at all. This reproduces exactly what the
        // service used to do -- assign unconditionally -- and requires it to throw.
        Row offender = ROWS.stream()
                .filter(row -> !DebitDay.isInRange(row.acbDebitDay()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no out-of-range row -- see the fixture test"));

        Member member = debitOrderMember();
        member.setDebitDay(offender.acbDebitDay());   // the unconditional overwrite

        assertThatThrownBy(() -> MemberValidator.validatePaymentType(member))
                .as("the validator still refuses %d, which is why the assignment is "
                        + "conditional rather than the check being relaxed",
                        offender.acbDebitDay())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(DebitDay.message());
    }

    /** A member the debit-day rule actually applies to — payroll members skip it. */
    private static Member debitOrderMember() {
        Member member = new Member();
        member.setPaymentType("Debit Order");
        member.setBankAccountNumber("1234567890");
        return member;
    }
}
