package za.co.titandynamix.sasbo_online_backend.guards;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import za.co.titandynamix.sasbo_online_backend.support.KnownDefects;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static za.co.titandynamix.sasbo_online_backend.support.NonVacuousScan.assertNonVacuousScan;

/**
 * The seed data {@code DataSeeder} writes into an EMPTY database -- asserted,
 * because on 2026-09-16 an empty database is what production became (C12), and
 * this data is what it was refilled from.
 *
 * <h2>Why the JSON and not the seeder</h2>
 * Every integration test mocks {@code DataSeeder} out, deliberately, so no lane
 * has ever looked at what it writes. And the seeder's failure mode is silence:
 * a job grade whose bank name matches nothing is logged at WARN and skipped, a
 * duplicate bank name aborts the whole run on a unique constraint. Both are
 * facts about the files, readable in milliseconds with no database.
 *
 * <h2>The rules are the ones the RUNTIME relies on</h2>
 * Not tidiness. Each assertion names the code path that breaks when it fails --
 * {@code DataSeeder}'s name match, the {@code banks.name} constraint,
 * {@code JobGradeServiceImpl.findByBankIdentifier}'s code-first lookup, or
 * {@code MemberServiceImpl}'s by-name subscription and grade resolution.
 */
@DisplayName("The seed data")
class SeedDataTest {

    private static final Path DATA = Path.of("src", "main", "resources", "data");

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private static List<Map<String, Object>> read(String file) {
        try {
            return JSON.readValue(DATA.resolve(file).toFile(), new TypeReference<>() {
            });
        } catch (IOException e) {
            throw new AssertionError("could not read seed file " + file + " -- " + e.getMessage(), e);
        }
    }

    private static String text(Map<String, Object> row, String key) {
        Object value = row.get(key);
        return value == null ? null : value.toString();
    }

    private static String fold(String name) {
        return name == null ? null : name.trim().toLowerCase(Locale.ROOT);
    }

    private static List<Map<String, Object>> banks() {
        List<Map<String, Object>> banks = read("banks_master.json");
        assertNonVacuousScan(banks, 50, "banks_master.json");
        return banks;
    }

    private static List<Map<String, Object>> jobGrades() {
        List<Map<String, Object>> grades = read("job_grades.json");
        assertNonVacuousScan(grades, 30, "job_grades.json");
        return grades;
    }

    private static List<Map<String, Object>> members() {
        List<Map<String, Object>> members = read("members.json");
        assertNonVacuousScan(members, 100, "members.json");
        return members;
    }

    /** Bank names as DataSeeder and BankRepository.findByNameIgnoreCase compare them. */
    private static Set<String> bankNames() {
        return banks().stream().map((b) -> fold(text(b, "name"))).collect(Collectors.toSet());
    }

    /** The values of {@code key} that more than one row carries, mapped to the rows' labels. */
    static Map<String, Set<String>> sharedBy(Collection<Map<String, Object>> rows,
                                             Function<Map<String, Object>, String> key,
                                             Function<Map<String, Object>, String> label) {
        Map<String, Set<String>> byKey = new TreeMap<>();
        rows.forEach((row) -> byKey.computeIfAbsent(key.apply(row), (k) -> new TreeSet<>()).add(label.apply(row)));
        byKey.values().removeIf((labels) -> labels.size() < 2);
        return byKey;
    }

    /**
     * Grades whose description states a bargaining status that their flag
     * contradicts. A description that says nothing about bargaining -- a null,
     * or "Junior Managers" -- is not evidence either way and is not reported.
     */
    static List<String> contradictedBargainingFlags(List<Map<String, Object>> grades) {
        return grades.stream()
                .filter((g) -> {
                    String description = fold(text(g, "description"));
                    if (description == null || !description.contains("bargaining")) {
                        return false;
                    }
                    boolean statedBargaining = !description.startsWith("non");
                    return !Objects.equals(g.get("isBargainingCouncil"), statedBargaining);
                })
                .map((g) -> text(g, "bankName") + " " + text(g, "grade"))
                .toList();
    }

    @Test
    @DisplayName("names every bank once, because banks.name is UNIQUE and one repeat aborts the seed")
    void everyBankNameIsUnique() {
        assertThat(sharedBy(banks(), (b) -> fold(text(b, "name")), (b) -> text(b, "name")))
                .as("a bank name appears twice, ignoring case. banks.name is UNIQUE, so "
                        + "DataSeeder's saveAll fails and an empty database gets NO banks")
                .isEmpty();
    }

    @Test
    @DisplayName("gives every bank a six-digit universal branch code, which is what the column holds")
    void everyBranchCodeFitsTheColumn() {
        assertThat(banks())
                .filteredOn((b) -> text(b, "universalBranchCode") == null
                        || !text(b, "universalBranchCode").matches("\\d{6}"))
                .as("banks.universal_branch_code is VARCHAR(6) NOT NULL")
                .isEmpty();
    }

    @Test
    @DisplayName("gives every job grade a bank on record, or DataSeeder skips it with a WARN")
    void everyJobGradeNamesABankOnRecord() {
        Set<String> banks = bankNames();

        assertThat(jobGrades())
                .filteredOn((g) -> !banks.contains(fold(text(g, "bankName"))))
                .as("a job grade names a bank that is not in banks_master.json. DataSeeder "
                        + "logs 'Skipping job grade' and the grade never reaches the database")
                .isEmpty();
    }

    @Test
    @DisplayName("lists each grade once per bank")
    void noGradeIsListedTwiceForOneBank() {
        assertThat(sharedBy(jobGrades(),
                (g) -> fold(text(g, "bankName")) + " / " + fold(text(g, "grade")),
                (g) -> text(g, "grade") + " (" + text(g, "description") + ")"))
                .as("the same grade is seeded twice for one bank, so the Job Grade dropdown "
                        + "offers it twice and the member save resolves whichever comes first")
                .isEmpty();
    }

    @Test
    @Disabled(KnownDefects.BANKS_WITH_GRADES_SHARE_A_BRANCH_CODE)
    @DisplayName("🔴 A44: gives every bank with job grades a branch code no other bank carries")
    void everyBankWithGradesHasItsOwnBranchCode() {
        Set<String> banksWithGrades = jobGrades().stream()
                .map((g) -> fold(text(g, "bankName"))).collect(Collectors.toSet());

        assertThat(sharedBy(banks(), (b) -> text(b, "universalBranchCode"), (b) -> text(b, "name")))
                .as("two banks share a universal branch code and at least one has grades. "
                        + "The client asks for grades BY CODE, and findByBankIdentifier tries the "
                        + "code first, so choosing either bank offers both banks' grades mixed")
                .allSatisfy((code, names) -> assertThat(names)
                        .filteredOn((name) -> banksWithGrades.contains(fold(name)))
                        .isEmpty());
    }

    @Test
    @DisplayName("🔴 A44 still reproduces: SARB and SBV share 000000, and both have grades")
    void sarbAndSbvStillShareAPlaceholderCode() {
        Set<String> banksWithGrades = jobGrades().stream()
                .map((g) -> fold(text(g, "bankName"))).collect(Collectors.toSet());
        Map<String, Set<String>> shared = sharedBy(banks(),
                (b) -> text(b, "universalBranchCode"), (b) -> text(b, "name"));
        shared.values().removeIf((names) -> names.stream().noneMatch((n) -> banksWithGrades.contains(fold(n))));

        assertThat(shared)
                .as("A44 has changed. If this is empty, A44 is fixed: enable "
                        + "everyBankWithGradesHasItsOwnBranchCode and delete the KnownDefects row. "
                        + "If another code appears, a second pair of banks now mixes its grades")
                .containsExactly(Map.entry("000000",
                        Set.of("SBV Services (Pty) Ltd", "South African Reserve Bank")));
    }

    @Test
    @DisplayName("the bargaining-flag check reports a contradiction, and passes a silent description")
    void theBargainingCheckCanFail() {
        assertThat(contradictedBargainingFlags(List.of(
                Map.of("bankName", "B", "grade", "1", "description", "Bargaining Unit", "isBargainingCouncil", false),
                Map.of("bankName", "B", "grade", "2", "description", "Non - Bargaining", "isBargainingCouncil", true))))
                .as("both directions of contradiction").containsExactly("B 1", "B 2");

        java.util.HashMap<String, Object> absent = new java.util.HashMap<>(Map.of(
                "bankName", "B", "grade", "3", "description", "Bargaining unit"));
        assertThat(contradictedBargainingFlags(List.of(absent)))
                .as("a stated status with NO flag contradicts it -- a null is not an answer").containsExactly("B 3");

        java.util.HashMap<String, Object> silent = new java.util.HashMap<>(Map.of("bankName", "B", "grade", "4"));
        silent.put("description", null);
        assertThat(contradictedBargainingFlags(List.of(silent,
                Map.of("bankName", "B", "grade", "5", "description", "Junior Managers"),
                Map.of("bankName", "B", "grade", "6", "description", "Non Bargaining Unit", "isBargainingCouncil", false))))
                .as("must pass: no stated status, or a flag that agrees").isEmpty();
    }

    @Test
    @Disabled(KnownDefects.BARGAINING_FLAG_CONTRADICTS_ITS_DESCRIPTION)
    @DisplayName("🟠 A45: flags every grade the way its own description classifies it")
    void everyBargainingFlagAgreesWithItsDescription() {
        assertThat(contradictedBargainingFlags(jobGrades()))
                .as("a grade described as bargaining (or non-bargaining) carries a "
                        + "different or missing isBargainingCouncil flag")
                .isEmpty();
    }

    @Test
    @DisplayName("🟠 A45 still reproduces: two Absa grades state a status and carry no flag")
    void twoAbsaGradesStillContradictTheirDescriptions() {
        assertThat(contradictedBargainingFlags(jobGrades()))
                .as("A45 has changed. If this is empty, A45 is fixed: enable "
                        + "everyBargainingFlagAgreesWithItsDescription and delete the KnownDefects "
                        + "row. If it grew, the new rows are the finding")
                .containsExactlyInAnyOrder("Absa Bank Limited AVP", "Absa Bank Limited BA3");
    }

    @Test
    @Disabled(KnownDefects.SEEDED_MEMBERS_NAME_BANKS_NOT_ON_RECORD)
    @DisplayName("🟠 A46: gives every seeded member a bank on record")
    void everySeededMemberNamesABankOnRecord() {
        Set<String> banks = bankNames();

        assertThat(members())
                .filteredOn((m) -> text(m, "bankName") != null && !banks.contains(fold(text(m, "bankName"))))
                .extracting((m) -> text(m, "bankName"))
                .as("a seeded member's bank matches no bank on record. MemberServiceImpl "
                        + "resolves the subscription, the debit day and the job grade by bank NAME, "
                        + "so all three miss for that member, and the Bank dropdown cannot show it")
                .isEmpty();
    }

    @Test
    @DisplayName("🟠 A46 still reproduces: 12 seeded members use 7 bank names not on record")
    void sevenMemberBankNamesAreStillUnknown() {
        Set<String> banks = bankNames();

        Map<String, Long> unknown = members().stream()
                .map((m) -> text(m, "bankName"))
                .filter((name) -> name != null && !banks.contains(fold(name)))
                .collect(Collectors.groupingBy(Function.identity(), TreeMap::new, Collectors.counting()));

        assertThat(unknown)
                .as("A46 has changed. If this is empty, A46 is fixed: enable "
                        + "everySeededMemberNamesABankOnRecord and delete the KnownDefects row. "
                        + "Main's 'Capitec Bank' -> 'Capitec Bank Limited' rename was one of these")
                .isEqualTo(Map.of(
                        "Absa Bank", 2L,
                        "First National Bank", 2L,
                        "FirstRand Limited", 1L,
                        "Investec Bank", 1L,
                        "Nedbank", 2L,
                        "Standard Bank", 2L,
                        "Standard Bank South Africa", 2L));
    }
}
