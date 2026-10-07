package za.co.titandynamix.sasbo_online_backend.utils;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import za.co.titandynamix.sasbo_online_backend.support.ApiSurface;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code data/demonstration_population.json} is exactly what its generator writes, and is the population the contract
 * says it is. Tested AS A FILE, before any database (MV-37): every size is the file's own statement or the generator's
 * constant, typed in no test here.
 */
@DisplayName("data/demonstration_population.json -- the October demonstration population, generated")
class DemonstrationPopulationGeneratorTest {

    private static DemonstrationPopulation file() throws IOException {
        return DemonstrationPopulationSeeder.readSeedFile();
    }

    @Test
    @DisplayName("the committed file is exactly what the generator writes")
    void theCommittedFileIsWhatTheGeneratorWrites() {
        String live = DemonstrationPopulationGenerator.render();
        if (ApiSurface.writeRequested()) {
            ApiSurface.write(DemonstrationPopulationGenerator.FILE, live);
        }
        assertThat(ApiSurface.readCommittedNormalised(DemonstrationPopulationGenerator.FILE))
                .as("%s is STALE or hand-edited: it is generated. Regenerate with %s and commit the diff",
                        DemonstrationPopulationGenerator.FILE, ApiSurface.regenerateCommand())
                .isEqualTo(live);
    }

    @Test
    @DisplayName("BR54: it carries LF endings on every platform -- pinned by name in .gitattributes")
    void theFileHasUnixEndings() throws IOException {
        assertThat(ApiSurface.readCommitted(DemonstrationPopulationGenerator.FILE)).doesNotContain("\r");
        assertThat(Files.readString(Paths.get(".gitattributes")))
                .contains("src/main/resources/data/demonstration_population.json text eol=lf");
    }

    @Test
    @DisplayName("MV-37: the file states how many members it holds -- and holds exactly that many")
    void itHoldsWhatItSays() throws IOException {
        assertThat(file().members()).hasSize(file().count());
        assertThat(file().count()).isPositive();
    }

    @Test
    @DisplayName("🔒 its block: numbered downwards from M-098500, above M-096001, every number known to SeedMemberNumbers, below the capture sequence and in no other seed file")
    void numberedInItsBlock() throws IOException {
        List<String> numbers = file().members().stream().map(DemonstrationPopulation.Member::membershipNumber).toList();
        List<String> expected = new ArrayList<>();
        for (int i = 0; i < numbers.size(); i++) {
            expected.add(String.format("M-%06d", DemonstrationPopulationGenerator.FIRST_NUMBER - i));
        }
        assertThat(numbers).isEqualTo(expected);
        assertThat(Integer.parseInt(numbers.get(numbers.size() - 1).substring(2)))
                .as("the floor of the block").isGreaterThanOrEqualTo(DemonstrationPopulationGenerator.FLOOR);
        // V22's membership_number_seq starts at 100161: a capture is numbered from there up, never down here.
        assertThat(numbers).allSatisfy(n -> assertThat(Integer.parseInt(n.substring(2))).isLessThan(100_001));
        SeedMemberNumbers known = new SeedMemberNumbers();
        assertThat(numbers).allSatisfy(n -> assertThat(known.isSeeded(n)).as("%s is not in SeedMemberNumbers", n).isTrue());
        List<String> others = new ArrayList<>(DemonstrationMemberGenerator.seedMembershipNumbers());
        DemonstrationMemberSeeder.readSeedFile().members().forEach(m -> others.add(m.membershipNumber()));
        assertThat(numbers).doesNotContainAnyElementsOf(others);
    }

    @Test
    @DisplayName("every pack has members -- each new area between 40 and 150, each area of the existing population topped up -- so no region's pack is empty")
    void everyPackHasMembers() throws IOException {
        Map<String, Long> byArea = file().members().stream()
                .collect(Collectors.groupingBy(DemonstrationPopulation.Member::area, Collectors.counting()));
        DemonstrationPopulationGenerator.PACKS.forEach((pack, areas) -> areas.keySet().forEach(area -> {
            if (DemonstrationPopulationGenerator.EXISTING_AREAS.contains(area)) {
                assertThat(byArea.getOrDefault(area, 0L)).as("pack %s, area %s, topped up", pack, area)
                        .isBetween((long) DemonstrationPopulationGenerator.ROOMY, 40L);
            } else {
                assertThat(byArea.getOrDefault(area, 0L)).as("pack %s, area %s", pack, area).isBetween(40L, 150L);
            }
        }));
        // Every lettered area of SASBO's that has a region is one of them: no area of a pack reads nought.
        Set<String> withRegion = new HashSet<>(List.of("A", "B", "C", "D", "E", "F", "G", "H", "I", "J", "K", "L", "M",
                "N", "O", "P", "R", "S", "T", "U", "V", "W", "X"));
        assertThat(byArea.keySet()).containsAll(withRegion);
        assertThat(file().members()).allSatisfy(m -> assertThat(m.pack())
                .isEqualTo(DemonstrationPopulationGenerator.packOf(m.area())));
    }

    @Test
    @DisplayName("rule C hides nothing of it: every employer group has members, and no workplace holds fewer than five or more than its complement")
    void ruleCHidesNothing() throws IOException {
        DemonstrationPopulationGenerator.Lists lists = DemonstrationPopulationGenerator.lists();
        Map<Integer, DemonstrationPopulationGenerator.Workplace> byRa = DemonstrationPopulationGenerator.workplaces(lists)
                .stream().collect(Collectors.toMap(DemonstrationPopulationGenerator.Workplace::raNumber,
                        Function.identity()));
        Map<Integer, Long> atWorkplace = file().members().stream()
                .collect(Collectors.groupingBy(DemonstrationPopulation.Member::workplaceRaNumber, Collectors.counting()));
        Map<String, Long> byGroup = new HashMap<>();
        atWorkplace.forEach((ra, members) -> {
            DemonstrationPopulationGenerator.Workplace w = byRa.get(ra);
            assertThat(w).as("workplace %d reaches an employer group", ra).isNotNull();
            // Beside the existing population's target where it stands there too: never over the complement together.
            long beside = DemonstrationPopulationGenerator.EXISTING_AREAS.contains(w.area()) ? w.target() : 0;
            assertThat(members).as("workplace %d", ra).isBetween((long) DemonstrationPopulationGenerator.AT_LEAST,
                    w.complement() - beside);
            byGroup.merge(w.group(), members, Long::sum);
        });
        assertThat(byGroup.keySet()).containsExactlyInAnyOrderElementsOf(DemonstrationMemberGenerator.PER_THOUSAND.keySet());
        assertThat(byGroup.values()).allSatisfy(n -> assertThat(n).isGreaterThanOrEqualTo(5L));
    }

    @Test
    @DisplayName("MD34: everybody pays R174.00 by payroll deduction at their workplace's employer -- no debit order, no account")
    void everybodyPaysByPayroll() throws IOException {
        assertThat(file().members()).allSatisfy(m -> {
            assertThat(m.paymentType()).isEqualTo("Payroll Deduction");
            assertThat(m.monthlySubscription()).isEqualTo("174.00");
            assertThat(m.bankName()).isNotBlank();
            assertThat(m.employeeNumber()).isEqualTo("EMP-" + Integer.parseInt(m.membershipNumber().substring(2)));
        });
    }

    @Test
    @DisplayName("🔒 no real data: every first name and surname from the generator's invented lists, mail at example.org, telephones in the seed's range")
    void inventedPeopleOnly() throws IOException {
        Set<String> firstNames = new HashSet<>(DemonstrationPopulationGenerator.FEMALE);
        firstNames.addAll(DemonstrationPopulationGenerator.MALE);
        assertThat(file().members()).allSatisfy(m -> {
            assertThat(DemonstrationPopulationGenerator.SURNAMES).as(m.membershipNumber()).contains(m.surname());
            assertThat(firstNames).as(m.membershipNumber()).contains(m.firstNames());
            assertThat(m.personalEmail()).endsWith("@" + DemonstrationPopulationGenerator.MAIL_DOMAIN);
            assertThat(m.phone()).startsWith(DemonstrationPopulationGenerator.TELEPHONE);
        });
    }

    @Test
    @DisplayName("🔒 MR-SEC-15: every identity number distinct across all four seed files, each passing the capture screen's check")
    void identityNumbersDistinctAndValid() throws IOException {
        List<String> identities = new ArrayList<>();
        identities.addAll(DemonstrationMemberGenerator.identityNumbersIn("data/members.json"));
        identities.addAll(DemonstrationMemberGenerator.identityNumbersIn(DebitOrderMemberSeeder.SEED_FILE));
        identities.addAll(DemonstrationMemberGenerator.identityNumbersIn(DemonstrationMemberSeeder.SEED_FILE));
        identities.addAll(file().members().stream().map(DemonstrationPopulation.Member::identityNumber).toList());
        assertThat(identities).doesNotHaveDuplicates();
        assertThat(file().members()).allSatisfy(m -> {
            assertThat(m.identityNumber()).matches("\\d{13}");
            assertThat(DebitOrderSeedGenerator.luhnCheckDigit(m.identityNumber().substring(0, 12)))
                    .isEqualTo(m.identityNumber().charAt(12) - '0');
        });
    }

    @Test
    @DisplayName("BR40: a joiner names one of the three past months and a day; everyone else joined before 2026 and last changed in a past month")
    void datedInThePast() throws IOException {
        assertThat(file().members()).allSatisfy(m -> {
            if (m.joinsInThePast()) {
                assertThat(m.joinedMonthsBack()).isBetween(1, DemonstrationPopulationGenerator.MONTHS);
                assertThat(m.joinDay()).isBetween(1, 28);
                assertThat(m.joinDate()).isNull();
                assertThat(m.dateLastChanged()).isNull();
                assertThat(m.recruiter()).isBetween(1, DemonstrationPopulationGenerator.RECRUITERS);
            } else {
                assertThat(m.joinDate()).isBefore(LocalDate.of(2026, 1, 1));
                assertThat(m.dateLastChanged()).isAfterOrEqualTo(m.joinDate())
                        .isBeforeOrEqualTo(DemonstrationPopulationGenerator.LAST_CHANGE);
                assertThat(m.recruiter()).isNull();
            }
        });
    }

    @Test
    @DisplayName("every planned area has joiners in each of the three past months, and a leaver in each")
    void movementInEveryAreaEveryMonth() throws IOException {
        Map<String, DemonstrationPopulation.Member> byNumber = file().members().stream()
                .collect(Collectors.toMap(DemonstrationPopulation.Member::membershipNumber, Function.identity()));
        Map<String, Set<Integer>> joinedIn = new HashMap<>();
        file().members().stream().filter(DemonstrationPopulation.Member::joinsInThePast)
                .forEach(m -> joinedIn.computeIfAbsent(m.area(), a -> new HashSet<>()).add(m.joinedMonthsBack()));
        Map<String, Set<Integer>> leftIn = new HashMap<>();
        file().past().stream().filter(c -> c.reason().equals("DELETION_CODE"))
                .forEach(c -> leftIn.computeIfAbsent(byNumber.get(c.membershipNumber()).area(), a -> new HashSet<>())
                        .add(c.monthsBack()));
        DemonstrationPopulationGenerator.PACKS.values().forEach(areas -> areas.keySet().forEach(area -> {
            assertThat(joinedIn.get(area)).as("joiners in area %s", area).containsExactlyInAnyOrder(1, 2, 3);
            assertThat(leftIn.get(area)).as("leavers in area %s", area).containsExactlyInAnyOrder(1, 2, 3);
        }));
    }

    @Test
    @DisplayName("leavers under every one of SASBO's deletion codes, at least three each, and every reason under code 2 given")
    void everyDeletionCode() throws IOException {
        Map<Integer, Long> byCode = file().past().stream().filter(c -> c.reason().equals("DELETION_CODE"))
                .collect(Collectors.groupingBy(DemonstrationPopulation.Change::deletionCode, Collectors.counting()));
        for (int code = 1; code <= 10; code++) {
            assertThat(byCode.getOrDefault(code, 0L)).as("deletion code %d", code).isGreaterThanOrEqualTo(3L);
        }
        Set<String> reasons = file().past().stream().map(DemonstrationPopulation.Change::reasonUnderCode)
                .filter(java.util.Objects::nonNull).collect(Collectors.toSet());
        assertThat(reasons).containsExactlyInAnyOrderElementsOf(DemonstrationPopulationGenerator.lists().reasonsUnderTwo());
        assertThat(file().past()).filteredOn(c -> c.reasonUnderCode() != null)
                .allSatisfy(c -> assertThat(c.deletionCode()).isEqualTo(2));
    }

    @Test
    @DisplayName("the suspended under every code of Stephne's sheet, at least three still suspended each -- none suspended long enough to lapse")
    void everySuspensionCode() throws IOException {
        Map<String, DemonstrationPopulation.Change> last = new HashMap<>();
        file().past().forEach(c -> last.merge(c.membershipNumber(), c,
                (a, b) -> a.monthsBack() > b.monthsBack() || (a.monthsBack() == b.monthsBack() && a.day() <= b.day()) ? b : a));
        Map<Integer, Long> suspended = last.values().stream().filter(c -> c.reason().equals("SUSPENSION_CODE"))
                .collect(Collectors.groupingBy(DemonstrationPopulation.Change::suspensionCode, Collectors.counting()));
        for (int[] code : DemonstrationPopulationGenerator.SUSPENDED) {
            assertThat(suspended.getOrDefault(code[0], 0L)).as("suspension code %d", code[0]).isGreaterThanOrEqualTo(3L);
        }
        // Three months is the most a suspension lasts (MEN-18): still suspended means suspended in the first or second
        // month back, so it has not lapsed on any day of the clock's month.
        assertThat(last.values()).filteredOn(c -> c.reason().equals("SUSPENSION_CODE"))
                .allSatisfy(c -> assertThat(c.monthsBack()).isLessThanOrEqualTo(2));
    }

    @Test
    @DisplayName("the past: each change of a member of the file, in one of the three months, with SASBO's code -- never ACB's reasons; a return follows a leaving or a suspension")
    void thePast() throws IOException {
        Map<String, DemonstrationPopulation.Member> byNumber = file().members().stream()
                .collect(Collectors.toMap(DemonstrationPopulation.Member::membershipNumber, Function.identity()));
        Map<String, List<DemonstrationPopulation.Change>> byMember = new TreeMap<>();
        for (DemonstrationPopulation.Change c : file().past()) {
            DemonstrationPopulation.Member m = byNumber.get(c.membershipNumber());
            assertThat(m).as("%s is a member of the file", c.membershipNumber()).isNotNull();
            assertThat(m.joinsInThePast()).as("a joiner has no past").isFalse();
            assertThat(c.monthsBack()).isBetween(1, DemonstrationPopulationGenerator.MONTHS);
            assertThat(c.day()).isBetween(1, 28);
            switch (c.reason()) {
                case "DELETION_CODE" -> {
                    assertThat(c.toStatus()).isEqualTo(DemonstrationMemberGenerator.leavingStatus(c.deletionCode()));
                    assertThat(c.suspensionCode()).isNull();
                }
                case "SUSPENSION_CODE" -> {
                    assertThat(c.toStatus()).isEqualTo("Suspended");
                    assertThat(c.deletionCode()).isNull();
                    assertThat(c.reasonUnderCode()).isNull();
                }
                case "REINSTATED" -> {
                    assertThat(c.toStatus()).isEqualTo("Active");
                    assertThat(c.deletionCode()).isNull();
                    assertThat(c.suspensionCode()).isNull();
                }
                default -> throw new AssertionError("a reason the past does not write: " + c.reason());
            }
            byMember.computeIfAbsent(c.membershipNumber(), n -> new ArrayList<>()).add(c);
        }
        byMember.forEach((number, changes) -> {
            for (DemonstrationPopulation.Change c : changes) {
                if (c.reason().equals("REINSTATED")) {
                    assertThat(changes).as("%s returns after leaving or a suspension, in an earlier month", number)
                            .anyMatch(e -> !e.reason().equals("REINSTATED") && e.monthsBack() > c.monthsBack());
                }
            }
            assertThat(changes.stream().filter(c -> c.reason().equals("DELETION_CODE")).count())
                    .as("%s leaves once at most", number).isLessThanOrEqualTo(1);
        });
    }

    @Test
    @DisplayName("the suspension codes it brings are Stephne's, in her words -- the codes the August printouts lack, and no other")
    void theCodesItBrings() throws IOException {
        Map<Integer, String> brought = new TreeMap<>();
        file().suspensionCodes().forEach(code -> brought.put(code.code(), code.name()));
        assertThat(brought).isEqualTo(new TreeMap<>(DemonstrationPopulationGenerator.NEW_SUSPENSION_CODES));
        assertThat(brought).containsEntry(12, "MATERNITY LEAVE").containsEntry(21, "SICK LEAVE")
                .containsEntry(30, "NO AUTHORITY TO DEBIT ACCOUNT").containsEntry(33, "NO AUTHORITY TO DEBIT ACCOUNT");
        Set<Integer> printed = Set.of(11, 13, 19);
        assertThat(brought.keySet()).doesNotContainAnyElementsOf(printed);
        Set<Integer> used = file().past().stream().map(DemonstrationPopulation.Change::suspensionCode)
                .filter(java.util.Objects::nonNull).collect(Collectors.toSet());
        Set<Integer> available = new HashSet<>(printed);
        available.addAll(brought.keySet());
        assertThat(available).containsAll(used);
    }

    @Test
    @DisplayName("the file's summary is the population counted -- the figures the contract quotes")
    void theSummaryIsThePopulation() throws IOException {
        DemonstrationPopulation file = file();
        assertThat(((Number) file.summary().get("members")).intValue()).isEqualTo(file.count());
        Map<String, Object> byArea = castMap(file.summary().get("byArea"));
        long summed = byArea.values().stream().mapToLong(v -> ((Number) v).longValue()).sum();
        assertThat(summed).isEqualTo(file.count());
        Map<String, Object> byPack = castMap(file.summary().get("byPack"));
        Map<String, Long> counted = file.members().stream()
                .collect(Collectors.groupingBy(DemonstrationPopulation.Member::pack, Collectors.counting()));
        byPack.forEach((pack, n) -> assertThat(((Number) n).longValue()).as(pack).isEqualTo(counted.getOrDefault(pack, 0L)));
        Map<String, Object> joined = castMap(file.summary().get("joinedByMonthsBack"));
        assertThat(joined.values().stream().mapToLong(v -> ((Number) v).longValue()).sum())
                .isEqualTo(file.members().stream().filter(DemonstrationPopulation.Member::joinsInThePast).count());
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castMap(Object value) {
        return (Map<String, Object>) value;
    }
}
