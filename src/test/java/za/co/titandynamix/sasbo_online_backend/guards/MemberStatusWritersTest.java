package za.co.titandynamix.sasbo_online_backend.guards;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import za.co.titandynamix.sasbo_online_backend.support.MainSourceInventory;
import za.co.titandynamix.sasbo_online_backend.support.MainSourceInventory.MainClassSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static za.co.titandynamix.sasbo_online_backend.support.NonVacuousScan.assertNonVacuousScan;

/**
 * Month end 03.4's invariant: <b>no member's status is written anywhere but through {@code changeStatus}</b> -- a
 * source scan of the main sources that names every writer of a status, by class and by count, with a canary that
 * proves the scan can fail (P3's invariants; MR-SEC-13's way of holding a rule to the code).
 *
 * <p>The edit's own door is shut in the service ({@code MemberServiceImpl.update} refuses
 * {@code STATUS_VIA_CHANGE_STATUS}); this is what keeps a NEW door from opening. A {@code setStatus(} anywhere not
 * listed below -- or one more in a class that is -- is red until someone reads it and lists it with its reason. SQL
 * that writes the members table's status is red outright: none is allowed.
 */
@DisplayName("Who writes a status")
class MemberStatusWritersTest {

    /** A call of a status setter, whatever the receiver is named. Comments are blanked before it is looked for. */
    static final Pattern SETTER = Pattern.compile("\\.setStatus\\(");

    /** SQL that writes the members table's status. */
    static final Pattern MEMBERS_STATUS_SQL = Pattern.compile("(?is)UPDATE\\s+members\\s+SET\\b[^;\"]*\\bstatus\\s*=");

    /**
     * Every class in the main sources that calls a status setter, with how many times -- and why. Four of them set a
     * MEMBER's status; the rest set the status of something else, and are listed so that a new call anywhere is seen.
     */
    static final Map<String, Integer> ALLOWED = new TreeMap<>(Map.ofEntries(
            // A member's: changeStatus, both of its forms -- the one writer of a status once 03.4 lands (03.1).
            Map.entry("MemberServiceImpl", 2),
            // A member's, at capture only: an invented member's status as generated (01.4), A.C.B.'s seed debit-order
            // members' (D33), and payroll's invented members' (October build). None changes a status; each gives one
            // to a member being created.
            Map.entry("DemonstrationMember", 1),
            Map.entry("SeedDebitOrderMember", 1),
            Map.entry("PayrollSeedMember", 1),
            // Not a member's: A.C.B.'s runs and letters, a subscription, and payroll's files (reconciled, superseded)
            // and discrepancies (resolved).
            Map.entry("AcbHistorySeeder", 2),
            Map.entry("AcbPreparationJob", 2),
            Map.entry("AcbReturnsServiceImpl", 5),
            Map.entry("AcbRunServiceImpl", 2),
            Map.entry("SubscriptionSeeder", 1),
            Map.entry("PayrollFiles", 2),
            Map.entry("PayrollDiscrepancies", 1),
            // Not a member's: the HTTP status of a sign-in refusal, written on the response (October build).
            Map.entry("SignInChallenge", 1)));

    @Test
    @DisplayName("🔒 03.4: every writer of a status in the main sources is named, by class and count -- a member's only in changeStatus and at capture")
    void everyWriterOfAStatusIsNamed() {
        List<MainClassSource> sources = MainSourceInventory.all();
        assertNonVacuousScan(sources, 100, "the main sources");
        Map<String, Integer> found = new TreeMap<>();
        for (MainClassSource source : sources) {
            int calls = count(SETTER, source.code());
            if (calls > 0) {
                found.merge(source.name(), calls, Integer::sum);
            }
        }
        assertThat(found)
                .as("a status setter called where none is listed, or more often than listed: list it with its reason, "
                        + "or write the status through changeStatus")
                .isEqualTo(ALLOWED);
    }

    @Test
    @DisplayName("🔒 03.4: no SQL in the main sources writes the members table's status")
    void noSqlWritesAMembersStatus() {
        List<String> writers = new ArrayList<>();
        for (MainClassSource source : MainSourceInventory.all()) {
            if (MEMBERS_STATUS_SQL.matcher(source.code()).find()) {
                writers.add(source.name());
            }
        }
        assertThat(writers).isEmpty();
    }

    @Test
    @DisplayName("the canary: both scans find what they look for, and pass what they do not")
    void theScansCanFail() {
        assertThat(count(SETTER, "        member.setStatus(\"Resigned\");")).isEqualTo(1);
        assertThat(count(SETTER, "        member.setStatusLabel(\"x\");")).isZero();
        assertThat(MEMBERS_STATUS_SQL.matcher(
                "jdbc.update(\"UPDATE members SET status = 'Resigned' WHERE id = ?\", id)").find()).isTrue();
        assertThat(MEMBERS_STATUS_SQL.matcher(
                "jdbc.update(\"UPDATE members SET recruitment_channel_id = NULL WHERE recruitment_channel_id = ?\", id)")
                .find()).as("the channel's removal (MO21) writes no status").isFalse();
    }

    private static int count(Pattern pattern, String code) {
        Matcher matcher = pattern.matcher(code);
        int n = 0;
        while (matcher.find()) {
            n++;
        }
        return n;
    }
}
