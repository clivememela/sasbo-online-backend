package za.co.titandynamix.sasbo_online_backend.utils;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import za.co.titandynamix.sasbo_online_backend.support.ApiSurface;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code data/month_end_rules_members.json} is exactly what its generator writes, and shows what it says it shows
 * (October build) -- tested AS A FILE, before any database. The rules each change shows are checked against the deck's
 * clock, Wednesday 14 October 2026 at 09:30 in SASBO's zone; the file itself is dated by months back from any clock.
 */
@DisplayName("data/month_end_rules_members.json -- month end's rules, seeded")
class MonthEndRulesSeedGeneratorTest {

    /** The deck's world: its clock's month, and its day. */
    private static final YearMonth DECK_MONTH = YearMonth.of(2026, 10);
    private static final LocalDate DECK_DAY = LocalDate.of(2026, 10, 14);

    private static MonthEndRulesSeeder.SeedFile file() throws IOException {
        return MonthEndRulesSeeder.readSeedFile();
    }

    @Test
    @DisplayName("the committed file is exactly what the generator writes")
    void theCommittedFileIsWhatTheGeneratorWrites() {
        String live = MonthEndRulesSeedGenerator.render();
        if (ApiSurface.writeRequested()) {
            ApiSurface.write(MonthEndRulesSeedGenerator.FILE, live);
        }
        assertThat(ApiSurface.readCommittedNormalised(MonthEndRulesSeedGenerator.FILE))
                .as("%s is STALE or hand-edited: it is generated. Regenerate with %s and commit the diff",
                        MonthEndRulesSeedGenerator.FILE, ApiSurface.regenerateCommand())
                .isEqualTo(live);
    }

    @Test
    @DisplayName("BR54: it carries LF endings on every platform -- pinned by name in .gitattributes")
    void theFileHasUnixEndings() throws IOException {
        assertThat(ApiSurface.readCommitted(MonthEndRulesSeedGenerator.FILE)).doesNotContain("\r");
        assertThat(Files.readString(Paths.get(".gitattributes")))
                .contains("src/main/resources/data/month_end_rules_members.json text eol=lf");
    }

    @Test
    @DisplayName("numbered downwards from M-096000 inside the block M-095801 to M-096000, every number in SeedMemberNumbers")
    void numberedInTheBlock() throws IOException {
        MonthEndRulesSeeder.SeedFile file = file();
        assertThat(file.members()).hasSize(file.count());
        List<String> numbers = file.members().stream().map(DemonstrationMember::membershipNumber).toList();
        List<String> expected = new ArrayList<>();
        for (int i = 0; i < numbers.size(); i++) {
            expected.add(String.format("M-%06d", MonthEndRulesSeedGenerator.FIRST_NUMBER - i));
        }
        assertThat(numbers).isEqualTo(expected);
        assertThat(Integer.parseInt(numbers.get(numbers.size() - 1).substring(2)))
                .isGreaterThanOrEqualTo(MonthEndRulesSeedGenerator.LAST_NUMBER);
        SeedMemberNumbers known = new SeedMemberNumbers();
        assertThat(numbers).allSatisfy(n -> assertThat(known.isSeeded(n)).as("%s in SeedMemberNumbers", n).isTrue());
    }

    @Test
    @DisplayName("🔒 MR-SEC-15: identity and membership numbers distinct across every seed file, each passing the check")
    void distinctAcrossTheSeedFiles() throws IOException {
        List<String> identities = new ArrayList<>();
        identities.addAll(DemonstrationMemberGenerator.identityNumbersIn("data/members.json"));
        identities.addAll(DemonstrationMemberGenerator.identityNumbersIn(DebitOrderMemberSeeder.SEED_FILE));
        identities.addAll(DemonstrationMemberGenerator.identityNumbersIn(DemonstrationMemberSeeder.SEED_FILE));
        identities.addAll(file().members().stream().map(DemonstrationMember::identityNumber).toList());
        assertThat(identities).doesNotHaveDuplicates();
        assertThat(file().members()).allSatisfy(member -> {
            assertThat(member.identityNumber()).matches("\\d{13}");
            assertThat(DebitOrderSeedGenerator.luhnCheckDigit(member.identityNumber().substring(0, 12)))
                    .isEqualTo(member.identityNumber().charAt(12) - '0');
        });
        List<String> numbers = new ArrayList<>(DemonstrationMemberGenerator.seedMembershipNumbers());
        DemonstrationMemberSeeder.readSeedFile().members().forEach(m -> numbers.add(m.membershipNumber()));
        file().members().forEach(m -> numbers.add(m.membershipNumber()));
        assertThat(numbers).doesNotHaveDuplicates();
    }

    @Test
    @DisplayName("🔒 MR-SEC-14, MD34: invented people who reach nobody -- Active, paying by payroll deduction")
    void invented() throws IOException {
        assertThat(file().members()).allSatisfy(member -> {
            assertThat(member.personalEmail()).endsWith("@" + MonthEndRulesSeedGenerator.MAIL_DOMAIN);
            assertThat(member.phone()).startsWith("+27 60 555 ");
            assertThat(member.status()).isEqualTo("Active");
            assertThat(member.paymentType()).isEqualTo("Payroll Deduction");
            assertThat(member.joinDate()).isBefore(LocalDate.of(2025, 1, 1));
        });
    }

    @Test
    @DisplayName("MEN-18: suspended within six banks, at least five members a bank so rule C shows each")
    void suspendedWithinSixBanks() throws IOException {
        MonthEndRulesSeedGenerator.Plan plan = MonthEndRulesSeedGenerator.plan();
        Map<String, String> bankOf = file().members().stream().collect(Collectors.toMap(
                DemonstrationMember::membershipNumber, DemonstrationMember::bankName));
        Set<String> banksSuspended = file().changes().stream().filter(c -> c.toStatus().equals("Suspended"))
                .map(c -> bankOf.get(c.membershipNumber())).collect(Collectors.toSet());
        assertThat(banksSuspended).hasSize(MonthEndRulesSeedGenerator.BANKS.size());
        plan.byBank().values().forEach(numbers -> assertThat(numbers).hasSizeGreaterThanOrEqualTo(5));
    }

    @Test
    @DisplayName("MEN-17: her codes 11, 12 and 21 each with a reason from her list, a reason typed alone, and the debit codes beside them")
    void theReasonsForNoDeduction() throws IOException {
        List<MonthEndRulesSeeder.Change> suspended = file().changes().stream()
                .filter(c -> c.toStatus().equals("Suspended")).toList();
        Set<String> hers = MonthEndRulesSeeder.REASONS.stream().map(r -> (String) r[0]).collect(Collectors.toSet());
        for (int code : List.of(11, 12, 21)) {
            assertThat(suspended).as("code %d with a reason from her list", code)
                    .anyMatch(c -> c.suspensionCode() == code && hers.contains(c.noDeductionReason()));
        }
        assertThat(suspended).as("a reason typed, none chosen")
                .anyMatch(c -> c.suspensionCode() == 11 && c.noDeductionReason() == null && c.note() != null);
        assertThat(suspended).as("the debit codes").anyMatch(c -> c.suspensionCode() == 13)
                .anyMatch(c -> c.suspensionCode() == 30);
        assertThat(suspended).allSatisfy(c -> assertThat(MonthEndRulesSeeder.CODES).containsKey(c.suspensionCode()));
        assertThat(suspended).filteredOn(c -> c.noDeductionReason() != null).allSatisfy(c -> assertThat(hers)
                .contains(c.noDeductionReason()));
    }

    @Test
    @DisplayName("MEN-18: on the deck's clock some suspensions have lapsed and some have not -- the reminder has members either side")
    void lapsedAndNotOnTheDecksClock() throws IOException {
        List<LocalDate> began = file().changes().stream().filter(c -> c.toStatus().equals("Suspended"))
                .map(c -> DECK_MONTH.minusMonths(c.monthsBack()).atDay(c.day())).toList();
        long lapsed = began.stream().filter(day -> SuspensionLapse.lapsedBy(day, DECK_DAY)).count();
        assertThat(lapsed).as("lapsed by 14 October").isGreaterThanOrEqualTo(5);
        assertThat(began.size() - lapsed).as("not lapsed by 14 October").isGreaterThanOrEqualTo(5);
        assertThat(began).as("one that lapses later in the clock's month")
                .anyMatch(day -> SuspensionLapse.lapsesOn(day).getMonth() == DECK_MONTH.getMonth()
                        && SuspensionLapse.lapsesOn(day).isAfter(DECK_DAY));
        assertThat(file().changes()).as("none in the clock's own month").allSatisfy(c ->
                assertThat(c.monthsBack()).isPositive());
    }

    @Test
    @DisplayName("MEN-23: a member leaves and rejoins in one month, the rejoin undoing the leaving -- and one rejoins a month later")
    void theRejoins() throws IOException {
        List<MonthEndRulesSeeder.Change> changes = file().changes();
        assertThat(changes).anySatisfy(rejoin -> {
            assertThat(rejoin.reason()).isEqualTo("REINSTATED");
            assertThat(rejoin.undoesTheLeaving()).isTrue();
            assertThat(changes).anyMatch(left -> left.membershipNumber().equals(rejoin.membershipNumber())
                    && left.reason().equals("DELETION_CODE") && left.monthsBack() == rejoin.monthsBack()
                    && left.day() < rejoin.day());
        });
        assertThat(changes).anySatisfy(rejoin -> {
            assertThat(rejoin.reason()).isEqualTo("REINSTATED");
            assertThat(rejoin.undoesTheLeaving()).isFalse();
            assertThat(changes).anyMatch(left -> left.membershipNumber().equals(rejoin.membershipNumber())
                    && left.reason().equals("DELETION_CODE") && left.monthsBack() > rejoin.monthsBack());
        });
    }

    @Test
    @DisplayName("MEN-26: more members stand at the one workplace of table code 155 than its complement counts")
    void moreMembersThanStaffAtOneWorkplace() throws IOException {
        MonthEndRulesSeedGenerator.Workplace overFull = MonthEndRulesSeedGenerator
                .workplacesOf(MonthEndRulesSeedGenerator.OVER_COMPLEMENT_TABLE_CODE, null, 1).get(0);
        long there = file().members().stream().filter(m -> m.workplaceRaNumber() == overFull.raNumber()).count();
        assertThat(there).isGreaterThan(overFull.complement()).isGreaterThanOrEqualTo(5);
    }
}
