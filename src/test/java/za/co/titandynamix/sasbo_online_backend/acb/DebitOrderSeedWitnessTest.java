package za.co.titandynamix.sasbo_online_backend.acb;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import za.co.titandynamix.sasbo_online_backend.acb.RunPreparer.Exclusion;
import za.co.titandynamix.sasbo_online_backend.acb.RunPreparer.Preparation;
import za.co.titandynamix.sasbo_online_backend.acb.RunPreparer.PreparedLine;
import za.co.titandynamix.sasbo_online_backend.utils.DebitOrderMemberSeeder;
import za.co.titandynamix.sasbo_online_backend.utils.SeedDebitOrderMember;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The seed prepares to a real-looking run, and says why it leaves anyone out (R0-8, D33) -- asserted as
 * a POPULATION, never a count.
 *
 * <p>The whole seed, as a database built from it holds it: the banks from {@code banks_master.json}, each
 * subscription row attached to the bank its name matches, the 160 of {@code members.json} and the 150 of
 * {@code debit_order_members.json}. The generated file says, per member, what the population rule must
 * make of them; this prepares a month and holds the rule to every one of those statements. The 160 keep
 * their own witness ({@link RunPreparerSeedWitnessTest}: since {@code 00.5} none of them is debited).
 *
 * <p>No database: the same rule the job runs, over the same rows, read from the files. The integration
 * lane proves the database half -- that the seeder puts these rows there, once.
 */
@DisplayName("RunPreparer over the whole seed -- the debit-order population as a witness")
class DebitOrderSeedWitnessTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    /**
     * The one member of the original 160 the rule debited until 00.5 (RunPreparerSeedWitnessTest) -- left out
     * now for the account type {@code members.json} never carried, which is not touched (D33).
     */
    private static final String THE_ORIGINAL_SEEDS_ONE = "M-100116";

    @Test
    @DisplayName("every member the generator marked QUALIFIES is debited, and nobody else")
    void theDebitedAreExactlyTheMarked() throws IOException {
        Preparation prepared = prepareTheWholeSeed(YearMonth.of(2026, 10));
        Set<String> expected = new TreeSet<>();
        for (SeedDebitOrderMember m : generated()) {
            if (m.expectedOutcome().equals("QUALIFIES")) {
                expected.add(m.membershipNumber());
            }
        }
        Set<String> debited = prepared.lines().stream().map(PreparedLine::membershipNumber)
                .collect(Collectors.toCollection(TreeSet::new));
        assertThat(debited).as("the run's members, as a set of membership numbers").isEqualTo(expected);
        assertThat(debited).as("the original seed's one, untyped since 00.5").doesNotContain(THE_ORIGINAL_SEEDS_ONE);
        // Each member is debited once: no re-presentations exist in a seed with no history.
        assertThat(prepared.lineCount()).isEqualTo(expected.size());
    }

    @Test
    @DisplayName("every generated member left out is left out for the reason the generator states")
    void everyExclusionIsTheStatedOne() throws IOException {
        Preparation prepared = prepareTheWholeSeed(YearMonth.of(2026, 10));
        Map<Integer, Exclusion> byMember = new HashMap<>();
        prepared.exclusions().forEach(e -> byMember.put(e.memberId(), e));
        List<SeedDebitOrderMember> generated = generated();
        int checked = 0;
        for (int i = 0; i < generated.size(); i++) {
            SeedDebitOrderMember m = generated.get(i);
            if (m.expectedOutcome().equals("QUALIFIES")) {
                continue;
            }
            Exclusion exclusion = byMember.get(generatedId(i));
            assertThat(exclusion).as("%s should be excluded %s", m.membershipNumber(), m.expectedOutcome()).isNotNull();
            assertThat(exclusion.reason().name()).as(m.membershipNumber()).isEqualTo(m.expectedOutcome());
            checked++;
        }
        assertThat(checked).as("the generated exclusions were actually compared").isGreaterThan(5);
    }

    @Test
    @DisplayName("the run spans six or more banks and all six of SASBO's due dates")
    void theRunLooksLikeSasbos() throws IOException {
        Preparation prepared = prepareTheWholeSeed(YearMonth.of(2026, 10));
        assertThat(prepared.lines().stream().map(PreparedLine::bankName).distinct().count()).isGreaterThanOrEqualTo(6);
        assertThat(prepared.lines().stream().map(PreparedLine::debitDay).collect(Collectors.toSet()))
                .containsAll(List.of(20, 25, 27, 29, 30, 31));
        assertThat(prepared.lines()).allSatisfy(l -> assertThat(YearMonth.from(l.actionDate())).isEqualTo(YearMonth.of(2026, 10)));
    }

    @Test
    @DisplayName("no seeded membership number is at or above 100161, where the capture sequence starts (F48)")
    void noSeededNumberReachesTheSequence() throws IOException {
        List<String> numbers = new ArrayList<>();
        read("data/members.json").forEach(m -> numbers.add((String) m.get("membershipNumber")));
        generated().forEach(m -> numbers.add(m.membershipNumber()));
        assertThat(numbers).hasSizeGreaterThan(300)
                .allSatisfy(n -> assertThat(Integer.parseInt(n.substring(2))).isLessThan(100_161));
    }

    // ------------------------------------------------------------------------------------------

    /** Generated members take ids after the 160, in file order -- as a database would number them. */
    private static int generatedId(int index) {
        return 10_000 + index;
    }

    private static List<SeedDebitOrderMember> generated() throws IOException {
        return DebitOrderMemberSeeder.readSeedFile().members();
    }

    static Preparation prepareTheWholeSeed(YearMonth month) throws IOException {
        List<BankRow> banks = new ArrayList<>();
        Map<String, Integer> idByName = new HashMap<>();
        List<Map<String, Object>> master = read("data/banks_master.json");
        for (int i = 0; i < master.size(); i++) {
            String name = (String) master.get(i).get("name");
            BankRow row = new BankRow(i + 1, name, (String) master.get(i).get("universalBranchCode"));
            banks.add(row);
            idByName.put(BankIndex.normalise(name), row.id());
        }
        List<Integer> activeBankIds = new ArrayList<>();
        for (Map<String, Object> subscription : read("data/subscriptions.json")) {
            if ("ACTIVE".equals(subscription.get("status"))) {
                activeBankIds.add(idByName.get(BankIndex.normalise((String) subscription.get("bankName"))));
            }
        }
        RunPreparer preparer = new RunPreparer(month, new DebitWindow(HolidayCalendar.WEEKENDS_ONLY),
                BankIndex.of(banks, activeBankIds), new BigDecimal("174.00"), List.of());
        for (Map<String, Object> m : read("data/members.json")) {
            preparer.accept(new MemberDebitRow((Integer) m.get("id"), (String) m.get("membershipNumber"),
                    (String) m.get("surname"), (String) m.get("initials"), (String) m.get("status"),
                    (String) m.get("paymentType"), (String) m.get("bankName"), (String) m.get("bankAccountNumber"),
                    (Integer) m.get("debitDay"), (String) m.get("accountType")));
        }
        List<SeedDebitOrderMember> generated = generated();
        for (int i = 0; i < generated.size(); i++) {
            SeedDebitOrderMember m = generated.get(i);
            preparer.accept(new MemberDebitRow(generatedId(i), m.membershipNumber(), m.surname(), m.initials(),
                    m.status(), m.paymentType(), m.bankName(), m.bankAccountNumber(), m.debitDay(),
                    m.accountType()));
        }
        return preparer.finish();
    }

    private static List<Map<String, Object>> read(String resource) throws IOException {
        try (InputStream stream = DebitOrderSeedWitnessTest.class.getClassLoader().getResourceAsStream(resource)) {
            assertThat(stream).as("%s on the test classpath", resource).isNotNull();
            return JSON.readValue(stream, new TypeReference<>() { });
        }
    }
}
