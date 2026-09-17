package za.co.titandynamix.sasbo_online_backend.guards;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import za.co.titandynamix.sasbo_online_backend.enums.BargainingUnit;
import za.co.titandynamix.sasbo_online_backend.enums.GradingSystem;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The bargaining-unit reference data, and the two rules that make it awkward (02.6).
 *
 * <h2>What this guards, and why it is a guard rather than a service test</h2>
 * The classification is REFERENCE DATA supplied by the union, not a rule we compute.
 * So the thing that can go wrong is not a bad algorithm, it is a bad transcription --
 * and a bad transcription is invisible: it produces a perfectly well-formed answer
 * that happens to be wrong about a real person's union membership.
 *
 * <h2>🔴 The Paterson C│D pattern is a CROSS-CHECK, never the implementation</h2>
 * All three Paterson employers on file cut BU/NBU at the same line -- A–C is the
 * work, D–F is the management of it, which is why SBV labels its D–F list
 * "(Managerial)". That is a genuinely useful sanity check and it is asserted below.
 *
 * <p>It is emphatically NOT how the classification is derived, and the two
 * exceptions are the reason:
 *
 * <ul>
 *   <li><b>Capitec grade C</b> is bargaining unit only for Assistant Branch
 *       Managers -- a job title this system does not store.
 *   <li><b>Every Nedbank grade</b> depends on a guaranteed-package threshold as well
 *       as the grade, and that table is missing from the source email.
 * </ul>
 *
 * <p>Deriving from the letter would be confidently wrong in exactly those two
 * places -- which are the places anybody would actually check.
 */
@DisplayName("Bargaining unit -- the seeded reference data")
class BargainingUnitSeedTest {

    private record Seed(String bankName, String grade, String description,
                        String bargainingUnit, String condition, String gradingSystem, String band) {
    }

    private static List<Seed> SEEDS;

    @BeforeAll
    static void load() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        List<Seed> seeds = new ArrayList<>();
        try (InputStream in = BargainingUnitSeedTest.class.getResourceAsStream("/data/job_grades.json")) {
            assertThat(in).as("the job-grade seed is on the classpath").isNotNull();
            for (JsonNode n : mapper.readTree(in)) {
                seeds.add(new Seed(
                        n.path("bankName").asText(null),
                        n.path("grade").asText(null),
                        n.path("description").asText(null),
                        n.path("bargainingUnit").asText(null),
                        n.path("bargainingUnitCondition").asText(null),
                        n.path("gradingSystem").asText(null),
                        n.path("band").asText(null)));
            }
        }
        SEEDS = List.copyOf(seeds);
    }

    private static List<Seed> forBank(String bank) {
        return SEEDS.stream().filter(s -> bank.equals(s.bankName())).toList();
    }

    @Test
    @DisplayName("the seed parses, and is not empty")
    void nonVacuityFloor() {
        // Without this, every assertion below would pass over an empty list.
        assertThat(SEEDS).hasSizeGreaterThan(40);
    }

    @Test
    @DisplayName("every classification is a value the enum actually has")
    void classificationsAreValidEnumValues() {
        // A typo in the seed would otherwise reach DataSeeder as an
        // IllegalArgumentException at application start, which is a worse place to
        // find it than here.
        for (Seed s : SEEDS) {
            if (s.bargainingUnit() != null) {
                assertThat(BargainingUnit.valueOf(s.bargainingUnit())).isNotNull();
            }
            assertThat(GradingSystem.valueOf(s.gradingSystem())).isNotNull();
        }
    }

    @Test
    @DisplayName("🔴 a CONDITIONAL grade always says what the condition is")
    void conditionalAlwaysCarriesItsCondition() {
        // The entire justification for the third enum value is that the condition is
        // stated. A CONDITIONAL with no text is worse than a boolean: it tells the
        // user their answer is incomplete and then refuses to say how.
        List<String> silent = SEEDS.stream()
                .filter(s -> "CONDITIONAL".equals(s.bargainingUnit()))
                .filter(s -> s.condition() == null || s.condition().isBlank())
                .map(s -> s.bankName() + " " + s.grade())
                .toList();

        assertThat(silent).as("CONDITIONAL grades with no stated condition").isEmpty();
    }

    @Test
    @DisplayName("a condition is only present where the classification is CONDITIONAL")
    void conditionsDoNotLeakOntoSettledGrades() {
        List<String> confusing = SEEDS.stream()
                .filter(s -> s.condition() != null && !s.condition().isBlank())
                .filter(s -> !"CONDITIONAL".equals(s.bargainingUnit()))
                .map(s -> s.bankName() + " " + s.grade())
                .toList();

        assertThat(confusing)
                .as("a settled grade carrying a condition reads as if it were unsettled")
                .isEmpty();
    }

    @Nested
    @DisplayName("the two rules a boolean cannot hold")
    class TheTwoExceptions {

        @Test
        @DisplayName("Nedbank: every grade is conditional on the threshold that is missing")
        void everyNedbankGradeIsConditional() {
            List<Seed> nedbank = forBank("Nedbank Limited");

            // 25 grades: B1..F5. Nedbank states it has no A-level jobs, so an A grade
            // arriving for Nedbank would be bad data rather than a gap.
            assertThat(nedbank).hasSize(25);
            assertThat(nedbank).extracting(Seed::band).doesNotContain("A");

            assertThat(nedbank)
                    .as("the email gives the grade half of a two-part rule and the "
                            + "threshold table it promises is not in the document. Storing "
                            + "the table alone and calling a C3 'BU' is silently wrong for "
                            + "anyone above the threshold -- in a field an organiser would "
                            + "reasonably trust")
                    .allMatch(s -> "CONDITIONAL".equals(s.bargainingUnit()));

            assertThat(nedbank)
                    .allMatch(s -> s.condition().contains("threshold"));
        }

        @Test
        @DisplayName("Nedbank: the grade half is recorded, so nothing is lost while we wait")
        void theGradeHalfIsPreserved() {
            // The information from the email is kept even though it cannot be applied
            // on its own -- otherwise asking Stephne for the threshold would also mean
            // re-transcribing 25 rows.
            List<Seed> nedbank = forBank("Nedbank Limited");

            assertThat(nedbank.stream().filter(s -> List.of("B", "C").contains(s.band())))
                    .hasSize(10)
                    .allMatch(s -> s.condition().contains("Grade half: BU"));
            assertThat(nedbank.stream().filter(s -> List.of("D", "E", "F").contains(s.band())))
                    .hasSize(15)
                    .allMatch(s -> s.condition().contains("Grade half: NBU"));
        }

        @Test
        @DisplayName("Capitec: grade C is conditional on a job title we do not store")
        void capitecGradeCIsConditional() {
            List<Seed> capitec = forBank("Capitec Bank Limited");
            assertThat(capitec).hasSize(6);

            Seed gradeC = capitec.stream().filter(s -> "C".equals(s.grade())).findFirst().orElseThrow();

            assertThat(gradeC.bargainingUnit())
                    .as("grade C is bargaining unit ONLY for Assistant Branch Managers. A "
                            + "flag on C would mis-classify every Capitec C who is not one")
                    .isEqualTo("CONDITIONAL");
            assertThat(gradeC.condition()).contains("Assistant Branch Manager");

            // The rest of Capitec is settled, and must not be dragged into the exception.
            assertThat(capitec.stream().filter(s -> List.of("A", "B").contains(s.grade())))
                    .allMatch(s -> "BU".equals(s.bargainingUnit()));
            assertThat(capitec.stream().filter(s -> List.of("D", "E", "F").contains(s.grade())))
                    .allMatch(s -> "NBU".equals(s.bargainingUnit()));
        }
    }

    @Test
    @DisplayName("🧭 CROSS-CHECK (not the rule): Paterson employers cut BU/NBU between C and D")
    void thePatersonBandPatternHoldsWhereItIsSettled() {
        // A useful sanity check on a transcription -- if a future D grade arrived
        // marked BU, that is worth a second look at the source before believing it.
        //
        // 🔴 It is applied ONLY to grades whose classification is settled. Every
        // CONDITIONAL row is excluded by construction, and those rows are exactly the
        // ones where deriving from the band letter gives the wrong answer. That
        // exclusion IS the point: the pattern is allowed to check the easy cases and
        // is never allowed to decide the hard ones.
        List<String> against = new ArrayList<>();
        int checked = 0;

        for (Seed s : SEEDS) {
            if (!"PATERSON".equals(s.gradingSystem()) || s.band() == null) continue;
            if (!List.of("BU", "NBU").contains(String.valueOf(s.bargainingUnit()))) continue;

            checked++;
            boolean workBand = List.of("A", "B", "C").contains(s.band());
            String expected = workBand ? "BU" : "NBU";
            if (!expected.equals(s.bargainingUnit())) {
                against.add(s.bankName() + " " + s.grade() + " band=" + s.band()
                        + " is " + s.bargainingUnit());
            }
        }

        assertThat(checked)
                .as("the cross-check compared nothing at all -- it would pass over any data")
                .isGreaterThan(0);
        assertThat(against)
                .as("a settled Paterson grade classified against the C|D line. This is a "
                        + "prompt to re-read the source, NOT a licence to change the data to "
                        + "fit the pattern")
                .isEmpty();
    }

    @Test
    @DisplayName("description no longer carries the classification")
    void descriptionIsFreed() {
        // The defect this unit exists for: one column meaning two things. Once the
        // classification has a typed column, a description repeating it is the same
        // fact in two places, waiting to drift.
        List<String> stillClassifying = SEEDS.stream()
                .filter(s -> s.description() != null)
                .filter(s -> s.description().toLowerCase().contains("bargaining"))
                .map(s -> s.bankName() + " " + s.grade() + ": " + s.description())
                .toList();

        assertThat(stillClassifying).isEmpty();
    }

    @Test
    @DisplayName("a description that was a seniority band is KEPT -- it was never a classification")
    void seniorityBandDescriptionsSurvive() {
        // Standard Bank's SBG11/12/13 said 'Junior Managers' / 'Middle Managers' /
        // 'Senior Managers' -- the same column used for something else entirely. Those
        // rows keep their text and their classification stays NULL, because the union
        // has not told us, and NULL says so honestly.
        List<Seed> managerRows = SEEDS.stream()
                .filter(s -> s.description() != null && s.description().contains("Managers"))
                .toList();

        assertThat(managerRows).hasSize(3);
        assertThat(managerRows)
                .as("inferring NBU for these from the word 'Manager' would be a guess "
                        + "wearing a fact's clothing")
                .allMatch(s -> s.bargainingUnit() == null);
    }
}
