package za.co.titandynamix.sasbo_online_backend.utils;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import za.co.titandynamix.sasbo_online_backend.acb.AcbTime;
import za.co.titandynamix.sasbo_online_backend.support.ApiSurface;

import java.io.IOException;
import java.nio.file.Files;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code data/demonstration_members.json} is exactly what its generator writes, and is the population it says it is
 * (month end 01.4). Tested AS A FILE, before any database (MV-37): its size is the file's own statement, typed in no
 * test here.
 */
@DisplayName("data/demonstration_members.json -- the invented members, generated")
class DemonstrationMemberGeneratorTest {

    private static DemonstrationMember.SeedFile file() throws IOException {
        return DemonstrationMemberSeeder.readSeedFile();
    }

    @Test
    @DisplayName("the committed file is exactly what the generator writes")
    void theCommittedFileIsWhatTheGeneratorWrites() {
        String live = DemonstrationMemberGenerator.render();
        if (ApiSurface.writeRequested()) {
            ApiSurface.write(DemonstrationMemberGenerator.FILE, live);
        }
        assertThat(ApiSurface.readCommittedNormalised(DemonstrationMemberGenerator.FILE))
                .as("%s is STALE or hand-edited: it is generated. Regenerate with %s and commit the diff",
                        DemonstrationMemberGenerator.FILE, ApiSurface.regenerateCommand())
                .isEqualTo(live);
    }

    @Test
    @DisplayName("BR54: it carries LF endings on every platform -- pinned by name in .gitattributes")
    void theFileHasUnixEndings() throws IOException {
        assertThat(Files.isRegularFile(DemonstrationMemberGenerator.FILE))
                .as("%s does not exist. Run %s", DemonstrationMemberGenerator.FILE, ApiSurface.regenerateCommand())
                .isTrue();
        assertThat(ApiSurface.readCommitted(DemonstrationMemberGenerator.FILE)).doesNotContain("\r");
        assertThat(Files.readString(java.nio.file.Paths.get(".gitattributes")))
                .contains("src/main/resources/data/demonstration_members.json text eol=lf");
    }

    @Test
    @DisplayName("MV-37: the file states how many members it holds -- and holds exactly that many")
    void itHoldsWhatItSays() throws IOException {
        DemonstrationMember.SeedFile file = file();
        assertThat(file.members()).hasSize(file.count());
        assertThat(file.count()).isPositive();
    }

    @Test
    @DisplayName("BR38: numbered downwards from M-099850, one each, every number in SeedMemberNumbers")
    void numberedDownwardsAndKnown() throws IOException {
        List<String> numbers = file().members().stream().map(DemonstrationMember::membershipNumber).toList();
        List<String> expected = new ArrayList<>();
        for (int i = 0; i < numbers.size(); i++) {
            expected.add(String.format("M-%06d", DemonstrationMemberGenerator.FIRST_NUMBER - i));
        }
        assertThat(numbers).isEqualTo(expected);
        SeedMemberNumbers known = new SeedMemberNumbers();
        assertThat(numbers).allSatisfy(n -> assertThat(known.isSeeded(n)).as("%s is not in SeedMemberNumbers", n).isTrue());
    }

    @Test
    @DisplayName("MD34: every member Active and paying by payroll deduction -- none on debit order, no account")
    void everyMemberPaysByPayroll() throws IOException {
        assertThat(file().members()).allSatisfy(member -> {
            assertThat(member.status()).isEqualTo("Active");
            assertThat(member.paymentType()).isEqualTo("Payroll Deduction");
        });
    }

    @Test
    @DisplayName("🔒 MR-SEC-15: every identity number and every membership number distinct across the THREE seed files, each passing the check")
    void distinctAcrossTheThreeSeedFiles() throws IOException {
        List<String> identities = new ArrayList<>();
        identities.addAll(DemonstrationMemberGenerator.identityNumbersIn("data/members.json"));
        identities.addAll(DemonstrationMemberGenerator.identityNumbersIn(DebitOrderMemberSeeder.SEED_FILE));
        identities.addAll(file().members().stream().map(DemonstrationMember::identityNumber).toList());
        assertThat(identities).doesNotHaveDuplicates();

        List<String> numbers = new ArrayList<>(DemonstrationMemberGenerator.seedMembershipNumbers());
        numbers.addAll(file().members().stream().map(DemonstrationMember::membershipNumber).toList());
        assertThat(numbers).doesNotHaveDuplicates();

        assertThat(file().members()).allSatisfy(member -> {
            String id = member.identityNumber();
            assertThat(id).matches("\\d{13}");
            assertThat(DebitOrderSeedGenerator.luhnCheckDigit(id.substring(0, 12)))
                    .isEqualTo(id.charAt(12) - '0');
        });
    }

    @Test
    @DisplayName("🔒 MR-SEC-14: contact details that reach nobody -- mail at example.org, telephones in the seed's range")
    void contactDetailsReachNobody() throws IOException {
        assertThat(file().members()).allSatisfy(member -> {
            assertThat(member.personalEmail()).endsWith("@" + DemonstrationMemberGenerator.MAIL_DOMAIN);
            assertThat(member.phone()).startsWith("+27 60 555 ");
            assertThat(member.cellTel()).isEqualTo(member.phone());
        });
    }

    @Test
    @DisplayName("BR40: no joinDate and no dateLastChanged in the clock's month, nor after it")
    void datedInAPastMonth() throws IOException {
        YearMonth now = YearMonth.now(AcbTime.ZONE);
        LocalDate firstOfThisMonth = now.atDay(1);
        assertThat(file().members()).allSatisfy(member -> {
            assertThat(member.joinDate()).isBefore(firstOfThisMonth);
            assertThat(member.dateLastChanged()).isBefore(firstOfThisMonth).isAfterOrEqualTo(member.joinDate());
        });
    }

    @Test
    @DisplayName("MD35: each workplace of the areas holds its group's penetration of its complement -- the invented and the placed together")
    void eachWorkplaceAtItsGroupsPenetration() throws IOException {
        DemonstrationMember.SeedFile file = file();
        Map<Integer, Long> invented = file.members().stream()
                .collect(Collectors.groupingBy(DemonstrationMember::workplaceRaNumber, Collectors.counting()));
        Map<Integer, Long> placed = file.placements().stream()
                .collect(Collectors.groupingBy(DemonstrationMember.Placement::workplaceRaNumber, Collectors.counting()));
        List<DemonstrationMemberGenerator.Workplace> workplaces = DemonstrationMemberGenerator.workplacesInTheAreas();
        assertThat(workplaces).isNotEmpty();
        Map<Integer, DemonstrationMemberGenerator.Workplace> byRa = workplaces.stream()
                .collect(Collectors.toMap(DemonstrationMemberGenerator.Workplace::raNumber, Function.identity()));

        assertThat(invented.keySet()).as("an invented member stands outside the areas").isSubsetOf(byRa.keySet());
        assertThat(placed.keySet()).as("a seed member is placed outside the areas").isSubsetOf(byRa.keySet());
        for (DemonstrationMemberGenerator.Workplace workplace : workplaces) {
            long members = invented.getOrDefault(workplace.raNumber(), 0L) + placed.getOrDefault(workplace.raNumber(), 0L);
            assertThat(members).as("workplace %d", workplace.raNumber()).isEqualTo(workplace.target());
        }

        // The stated tolerance: a group's penetration, over the areas, within two points of the rate it was given --
        // where the group has at least a hundred staff there; a smaller group is only rounding.
        Map<String, long[]> byGroup = new HashMap<>();
        for (DemonstrationMemberGenerator.Workplace workplace : workplaces) {
            if (workplace.group() == null) {
                continue;
            }
            long[] sums = byGroup.computeIfAbsent(workplace.group(), g -> new long[2]);
            sums[0] += invented.getOrDefault(workplace.raNumber(), 0L) + placed.getOrDefault(workplace.raNumber(), 0L);
            sums[1] += workplace.complement();
        }
        byGroup.forEach((group, sums) -> {
            if (sums[1] >= 100) {
                double given = DemonstrationMemberGenerator.PER_THOUSAND.get(group) / 1000.0;
                assertThat((double) sums[0] / sums[1]).as(group).isCloseTo(given, org.assertj.core.data.Offset.offset(0.02));
            }
        });
    }

    @Test
    @DisplayName("MR-ROB-32: every one of the seed's other members is placed once, at a workplace of the areas")
    void theSeedsMembersArePlacedOnce() throws IOException {
        List<String> placed = file().placements().stream().map(DemonstrationMember.Placement::membershipNumber).toList();
        assertThat(placed).doesNotHaveDuplicates();
        assertThat(placed).containsExactlyInAnyOrderElementsOf(DemonstrationMemberGenerator.seedMembershipNumbers());
    }

    @Test
    @DisplayName("03.7: the past -- invented members who joined before 2026, each change in one of the three months, with SASBO's code")
    void thePast() throws IOException {
        DemonstrationMember.SeedFile file = file();
        Map<String, DemonstrationMember> invented = file.members().stream()
                .collect(Collectors.toMap(DemonstrationMember::membershipNumber, Function.identity()));
        List<Integer> deletionCodes = java.util.Arrays.stream(DemonstrationMemberGenerator.DELETION_WEIGHTS)
                .map(weight -> weight[0]).toList();
        List<Integer> suspensionCodes = java.util.Arrays.stream(DemonstrationMemberGenerator.SUSPENSION_WEIGHTS)
                .map(weight -> weight[0]).toList();
        Map<String, Integer> leftIn = new HashMap<>();

        assertThat(file.past()).isNotEmpty();
        for (DemonstrationMember.PastChange change : file.past()) {
            DemonstrationMember member = invented.get(change.membershipNumber());
            assertThat(member).as("%s is an invented member", change.membershipNumber()).isNotNull();
            assertThat(member.joinDate()).isBefore(DemonstrationMemberGenerator.JOINED_BEFORE);
            assertThat(change.monthsBack()).isBetween(1, 3);
            assertThat(change.day()).isBetween(1, 28);
            switch (change.reason()) {
                case "DELETION_CODE" -> {
                    assertThat(deletionCodes).contains(change.deletionCode());
                    assertThat(change.toStatus()).isEqualTo(DemonstrationMemberGenerator.leavingStatus(change.deletionCode()));
                    assertThat(change.suspensionCode()).isNull();
                    assertThat(leftIn.put(change.membershipNumber(), change.monthsBack())).as("no member leaves twice").isNull();
                }
                case "SUSPENSION_CODE" -> {
                    assertThat(suspensionCodes).contains(change.suspensionCode());
                    assertThat(change.toStatus()).isEqualTo("Suspended");
                    assertThat(change.deletionCode()).isNull();
                }
                case "REINSTATED" -> {
                    assertThat(change.toStatus()).isEqualTo("Active");
                    assertThat(leftIn.get(change.membershipNumber())).as("a reinstatement follows a leaving in an earlier month")
                            .isNotNull().isGreaterThan(change.monthsBack());
                }
                default -> throw new AssertionError("a reason the past does not write: " + change.reason());
            }
        }
        for (int monthsBack = 1; monthsBack <= 3; monthsBack++) {
            int month = monthsBack;
            assertThat(file.past()).as("month %d has its leavers", month)
                    .anyMatch(c -> c.monthsBack() == month && c.reason().equals("DELETION_CODE"));
        }
    }

    @Test
    @DisplayName("MD35: the file carries the nine proportions it was given and no count of SASBO's members")
    void itStandsOnProportionsOnly() throws IOException {
        Map<String, Object> from = file().generatedFrom();
        assertThat(from).containsOnlyKeys("areas", "workplaces", "penetrationsPerThousand", "holds");
        assertThat(from.get("penetrationsPerThousand")).isEqualTo(new HashMap<>(DemonstrationMemberGenerator.PER_THOUSAND));
        assertThat(Set.copyOf(DemonstrationMemberGenerator.PER_THOUSAND.keySet())).hasSize(9);
    }
}
