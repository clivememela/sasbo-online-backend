package za.co.titandynamix.sasbo_online_backend.acb;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import za.co.titandynamix.sasbo_online_backend.acb.RunPreparer.Preparation;
import za.co.titandynamix.sasbo_online_backend.acb.RunPreparer.PreparedLine;
import za.co.titandynamix.sasbo_online_backend.enums.AcbExclusionReason;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The seed, as a witness: what the population rule makes of the 160 members the application ships
 * with.
 *
 * <p>The planning pack measures the same thing with its own script, independently of this code, and
 * the two agree: <b>one</b> member of 160 is debited, and the other 159 are excluded for five
 * different reasons. Almost every run on the seed is nearly empty -- which is why the run fixture
 * exists, and why this is a WITNESS and not a fixture. The day the seed is corrected this test goes
 * red; the answer then is to find the new numbers, not to delete the test.
 *
 * <p>Built the way the seeder builds the database: banks by name, each subscription row attached to
 * the bank its name matches. The file's own bank ids are not used -- 68 banks share 65 of them.
 */
@DisplayName("RunPreparer over the seed -- the witness")
class RunPreparerSeedWitnessTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    @DisplayName("1 of 160 is debited; 159 are excluded, each for the first rule they fail")
    void theSeedIsAWitness() throws IOException {
        Preparation prepared = prepareTheSeed(YearMonth.of(2026, 10));

        Map<AcbExclusionReason, Integer> byReason = new EnumMap<>(AcbExclusionReason.class);
        for (AcbExclusionReason reason : AcbExclusionReason.values()) {
            if (prepared.excludedFor(reason) > 0) {
                byReason.put(reason, prepared.excludedFor(reason));
            }
        }

        assertThat(prepared.lineCount())
                .as("the seed's debited members. If this moved, the seed changed: find the new "
                        + "witness and update it here AND in the planning pack's facts script -- do not "
                        + "delete the test")
                .isEqualTo(1);
        assertThat(byReason).containsExactlyInAnyOrderEntriesOf(Map.of(
                AcbExclusionReason.NOT_DEBIT_ORDER, 103,
                AcbExclusionReason.STATUS_NOT_ACTIVE, 40,
                AcbExclusionReason.NO_BANK_ON_RECORD, 6,
                AcbExclusionReason.NO_SUBSCRIPTION_ROW, 9,
                AcbExclusionReason.DAY_OUT_OF_WINDOW, 1));
        // The listed reasons among the Active debit-order members: the report an administrator reads.
        assertThat(prepared.exclusions()).hasSize(16);
        assertThat(prepared.lineCount() + byReason.values().stream().mapToInt(Integer::intValue).sum())
                .as("every seeded member accounted for exactly once").isEqualTo(160);
    }

    @Test
    @DisplayName("the one debited member is the one the planning pack names")
    void theDebitedMember() throws IOException {
        PreparedLine line = prepareTheSeed(YearMonth.of(2026, 10)).lines().get(0);
        assertThat(line.membershipNumber()).isEqualTo("M-100116");
        assertThat(line.debitDay()).isEqualTo(28);
    }

    @Test
    @DisplayName("seeded debit days outside the window are excluded, never bent into it")
    void outOfRangeDaysAreExcludedNotMoved() throws IOException {
        // Most seeded debit days are the 1st -- a placeholder. None of them may produce a date: a
        // member on the 1st is named in the report, and every line that IS made is in the window.
        for (YearMonth month : AcbCalendars.months()) {
            Preparation prepared = prepareTheSeed(month);
            assertThat(prepared.lines()).allSatisfy(l -> {
                assertThat(YearMonth.from(l.actionDate())).isEqualTo(month);
                assertThat(l.actionDate().getDayOfMonth()).isGreaterThanOrEqualTo(20);
            });
        }
    }

    // ------------------------------------------------------------------------------------------

    static Preparation prepareTheSeed(YearMonth month) throws IOException {
        List<Map<String, Object>> banks = read("data/banks.json");
        List<Map<String, Object>> subscriptions = read("data/subscriptions.json");
        List<Map<String, Object>> members = read("data/members.json");

        List<BankRow> bankRows = new ArrayList<>();
        Map<String, Integer> idByName = new HashMap<>();
        for (int i = 0; i < banks.size(); i++) {
            String name = (String) banks.get(i).get("bankName");
            BankRow row = new BankRow(i + 1, name, (String) banks.get(i).get("branchCode"));
            bankRows.add(row);
            idByName.put(BankIndex.normalise(name), row.id());
        }
        List<Integer> activeBankIds = new ArrayList<>();
        for (Map<String, Object> subscription : subscriptions) {
            if ("ACTIVE".equals(subscription.get("status"))) {
                // A row naming no bank on record attaches to nothing, exactly as the seeder does.
                activeBankIds.add(idByName.get(BankIndex.normalise((String) subscription.get("bankName"))));
            }
        }

        RunPreparer preparer = new RunPreparer(month, new DebitWindow(HolidayCalendar.WEEKENDS_ONLY),
                BankIndex.of(bankRows, activeBankIds), new BigDecimal("174.00"), List.of());
        for (Map<String, Object> m : members) {
            preparer.accept(new MemberDebitRow(
                    (Integer) m.get("id"),
                    (String) m.get("membershipNumber"),
                    (String) m.get("surname"),
                    (String) m.get("initials"),
                    (String) m.get("status"),
                    (String) m.get("paymentType"),
                    (String) m.get("bankName"),
                    (String) m.get("bankAccountNumber"),
                    (Integer) m.get("debitDay")));
        }
        return preparer.finish();
    }

    private static List<Map<String, Object>> read(String resource) throws IOException {
        try (InputStream stream = RunPreparerSeedWitnessTest.class.getClassLoader().getResourceAsStream(resource)) {
            assertThat(stream).as("%s on the test classpath", resource).isNotNull();
            return JSON.readValue(stream, new TypeReference<>() { });
        }
    }
}
