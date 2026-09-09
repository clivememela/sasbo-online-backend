package za.co.titandynamix.sasbo_online_backend.guards;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import za.co.titandynamix.sasbo_online_backend.entity.member.Member;
import za.co.titandynamix.sasbo_online_backend.support.MainSourceInventory;
import za.co.titandynamix.sasbo_online_backend.utils.DebitDay;
import za.co.titandynamix.sasbo_online_backend.validator.MemberValidator;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 🔴 THE SERVER SETS A DEBIT DAY AND THEN REJECTS ITS OWN VALUE — pinned by 04.1,
 * NOT fixed by it.
 *
 * <h2>The shape of it</h2>
 * {@code MemberServiceImpl.create} runs, in this order:
 *
 * <pre>
 *   autoPopulateBankDerivedFields(member);   // debitDay := the bank's acbDebitDay
 *   ...
 *   MemberValidator.validatePaymentType(member);   // rejects it unless 20..31
 * </pre>
 *
 * So for a debit-order member at a bank whose ACB day is outside the permitted
 * range, the service overwrites whatever the administrator chose — a value that
 * WAS valid — with one that is not, and then throws. The message the user sees
 * blames a debit day they did not enter and cannot change.
 *
 * <h2>Why this is a PIN and not a repair</h2>
 * There are two candidate truths and choosing between them is a business decision:
 *
 * <ul>
 *   <li>the union's range is right and eleven banks' reference data is stale, or
 *   <li>the banks' data is right and the 20–31 range is wrong.
 * </ul>
 *
 * <p>A developer picking one silently is how the two came to disagree in the first
 * place — the range itself was {@code {20, 25, 31}} in four separate files until
 * 02.1, and it already rejected the server's own seed data then. So 04.1 shows the
 * conflict to the user before they save (see {@code CaptureMember.autoPopulate.test.jsx})
 * and records its size here.
 *
 * <p>🔑 <b>This test asserts the CURRENT, WRONG behaviour on purpose.</b> When the
 * conflict is settled it goes red — and that is the signal to delete it along with
 * the divergence, not to adjust it.
 */
@DisplayName("Debit day — the server overwrites, then rejects its own value (open)")
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
    @DisplayName("🔴 a debit day taken from the bank's own record is rejected by the validator")
    void theServerRejectsTheValueItJustAssigned() {
        Row offender = ROWS.stream()
                .filter(row -> !DebitDay.isInRange(row.acbDebitDay()))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "No seeded subscription row falls outside " + DebitDay.message()
                                + ". If the reference data was corrected, this conflict is "
                                + "CLOSED: delete this test and the on-screen warning in "
                                + "CaptureMember with it."));

        // Exactly what autoPopulateBankDerivedFields leaves on the entity: a debit-order
        // member carrying the bank's own ACB day. Nothing here is invented -- the day
        // comes from the seed file the seeder loads.
        Member member = new Member();
        member.setPaymentType("Debit Order");
        member.setBankAccountNumber("1234567890");
        member.setDebitDay(offender.acbDebitDay());

        assertThatThrownBy(() -> MemberValidator.validatePaymentType(member))
                .as("%s's ACB day is the %d, and the validator refuses it",
                        offender.bankName(), offender.acbDebitDay())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(DebitDay.message());
    }

    @Test
    @DisplayName("the size of it — most seeded banks are in this state, not a handful")
    void mostSeededBanksAreAffected() {
        List<Row> outside = ROWS.stream()
                .filter(row -> !DebitDay.isInRange(row.acbDebitDay()))
                .toList();

        // Deliberately NOT asserted as a number. A count written into a test is a
        // count that goes stale the first time a row is added, and then the test is
        // edited to match rather than read -- this arc has a rule about it. What
        // matters is that it is the MAJORITY case: this is the normal path through
        // capture for a debit-order member, not an edge nobody hits.
        assertThat(outside)
                .as("banks whose ACB debit day the validator will refuse: %s",
                        outside.stream().map(Row::bankName).toList())
                .hasSizeGreaterThan(ROWS.size() / 2);
    }

    @Test
    @DisplayName("the overwrite is still there, and create() still runs it before validating")
    void theOverwriteStillPrecedesTheValidation() {
        // A source scan, because the ORDER is the defect: assigning and then validating
        // is incoherent in a way that neither statement is on its own.
        //
        // ⚠️ THE ORDER MUST BE READ FROM create()'s BODY, not from the file. The first
        // draft of this test compared `indexOf` positions across the whole class and
        // failed -- because `setDebitDay` lives in a private helper declared BELOW
        // create(), so the text order is the reverse of the call order. It would have
        // been "fixed" by flipping the comparison, and would then have been asserting
        // that a helper is declared after its caller. Guards that measure the wrong
        // thing are this suite's most expensive recurring failure; the fix was to
        // narrow the window to the method whose order actually matters.
        String code = MainSourceInventory.all().stream()
                .filter(c -> c.name().equals("MemberServiceImpl"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("MemberServiceImpl not found"))
                .code();

        assertThat(code)
                .as("the service still overwrites debitDay from the bank's subscription row")
                .contains("member.setDebitDay(s.getAcbDebitDay())");

        String createBody = bodyOf(code, "public Member create(Member member)");
        int populates = createBody.indexOf("autoPopulateBankDerivedFields(member)");
        int validates = createBody.indexOf("MemberValidator.validatePaymentType(member)");

        assertThat(populates).as("create() still derives the bank fields").isGreaterThan(-1);
        assertThat(validates).as("create() still validates").isGreaterThan(-1);
        assertThat(validates)
                .as("and the validation still runs AFTER the overwrite, which is the defect")
                .isGreaterThan(populates);
    }

    /** The text of one method, from its signature to the first line that closes it. */
    private static String bodyOf(String code, String signature) {
        int start = code.indexOf(signature);
        assertThat(start).as("found %s", signature).isGreaterThan(-1);
        int end = code.indexOf("\n    }", start);
        assertThat(end).as("found the end of %s", signature).isGreaterThan(start);
        return code.substring(start, end);
    }
}
