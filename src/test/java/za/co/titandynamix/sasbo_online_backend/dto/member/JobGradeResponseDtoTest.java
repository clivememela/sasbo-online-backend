package za.co.titandynamix.sasbo_online_backend.dto.member;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The job-grade label: what the drop-down actually reads (CM-33).
 *
 * <h2>Why this test exists when the defect is already fixed</h2>
 * The label builder regressed once. A refactor replaced
 *
 * <pre>{@code Stream.of(grade, description, contractType)
 *       .filter(s -> s != null && !s.isBlank())
 *       .collect(joining(" - "))}</pre>
 *
 * with a version that joined FIRST and then tried to repair the result with two
 * {@code replaceAll}s -- and the patterns did not match what the join produced
 * ({@code " -  - "} has two spaces, the pattern {@code " - - "} has one). Nine of
 * twenty-seven labels came out malformed: {@code "Grade M -"} with a trailing
 * separator, {@code "Grade c -  - PFA/DFA Sales"} with an empty middle.
 *
 * <p>🔴 <b>It was repaired upstream, and nothing pinned it.</b> A fix with no test
 * behind it is the half that rots -- the next person to reach for
 * "join then tidy up" has nothing telling them it was already tried and already
 * failed. That is the whole reason this file exists, and it is why the cases below
 * are the SHAPES that broke rather than a transcription of today's output.
 *
 * <h2>The separator is inside the data</h2>
 * Note {@code "Non - Bargaining"}: several descriptions contain the joiner as
 * ordinary text. Any approach that repairs a joined string by pattern-matching the
 * separator is therefore not merely fragile, it is unfixable -- it cannot tell a
 * separator it inserted from one that was always part of the value. Filtering
 * before the join is not a tidier way to get the same answer; it is the only
 * correct one.
 */
@DisplayName("JobGradeResponseDto -- the label the drop-down reads")
class JobGradeResponseDtoTest {

    private static JobGradeDto grade(String grade, String description, String contractType) {
        JobGradeDto dto = new JobGradeDto();
        dto.setGrade(grade);
        dto.setDescription(description);
        dto.setContractType(contractType);
        return dto;
    }

    private static String labelOf(String grade, String description, String contractType) {
        return JobGradeResponseDto.from(grade(grade, description, contractType)).getLabel();
    }

    @Nested
    @DisplayName("the nine shapes that broke")
    class TheShapesThatBroke {

        @Test
        @DisplayName("a grade with NOTHING else carries no trailing separator")
        void gradeAloneHasNoTrailingSeparator() {
            // Was "Grade M -".
            assertThat(labelOf("Grade M", null, null)).isEqualTo("Grade M");
            assertThat(labelOf("Grade M", "", "")).isEqualTo("Grade M");
            assertThat(labelOf("Grade M", "   ", "   ")).isEqualTo("Grade M");
        }

        @Test
        @DisplayName("a missing MIDDLE part does not leave an empty slot")
        void missingDescriptionLeavesNoEmptySlot() {
            // Was "Grade c -  - PFA/DFA Sales" -- two spaces, which is precisely
            // what the repair pattern failed to match.
            assertThat(labelOf("Grade c", null, "PFA/DFA Sales"))
                    .isEqualTo("Grade c - PFA/DFA Sales");
            assertThat(labelOf("Grade c", "  ", "PFA/DFA Sales"))
                    .isEqualTo("Grade c - PFA/DFA Sales");
        }

        @Test
        @DisplayName("a missing contract type does not leave a trailing separator")
        void missingContractTypeLeavesNoTrailingSeparator() {
            assertThat(labelOf("BA1", "Bargaining unit", null)).isEqualTo("BA1 - Bargaining unit");
        }

        @Test
        @DisplayName("all three parts present join in order")
        void allThreePartsJoin() {
            assertThat(labelOf("C3", "Bargaining Unit", "Permanent"))
                    .isEqualTo("C3 - Bargaining Unit - Permanent");
        }

        @Test
        @DisplayName("a description that CONTAINS the separator survives intact")
        void separatorInsideTheDataSurvives() {
            // The case that makes repair-after-join unfixable rather than merely
            // fragile: nine seeded rows carry "Non - Bargaining" as their description.
            assertThat(labelOf("AVP", "Non - Bargaining", null))
                    .isEqualTo("AVP - Non - Bargaining");
        }
    }

    @Test
    @DisplayName("the value is the raw grade, never the label")
    void valueIsTheRawGrade() {
        // The client submits `value`. If it ever became the label, every captured
        // job grade would be stored as a display string.
        JobGradeResponseDto dto = JobGradeResponseDto.from(grade("BA1", "Bargaining unit", null));
        assertThat(dto.getValue()).isEqualTo("BA1");
        assertThat(dto.getLabel()).isEqualTo("BA1 - Bargaining unit");
    }

    @Test
    @DisplayName("a row with no grade is not a label")
    void noGradeIsNotALabel() {
        assertThat(JobGradeResponseDto.from(grade(null, "Bargaining unit", "Permanent"))).isNull();
        assertThat(JobGradeResponseDto.from(null)).isNull();
    }

    /**
     * THE CANARY. Everything above runs on hand-written input, so all of it would
     * keep passing if the seed file changed shape and the real drop-down filled
     * with malformed labels. This reads the seed the application actually ships
     * and asserts the property directly.
     *
     * <p>The floor is a non-vacuity guard, not a coverage target: without it, a
     * seed file that failed to parse into an empty list would satisfy every
     * assertion below by having nothing to violate them.
     */
    @Nested
    @DisplayName("canary -- the real seed data")
    class Canary {

        private List<JobGradeDto> seededGrades() throws Exception {
            ObjectMapper mapper = new ObjectMapper();
            List<JobGradeDto> out = new ArrayList<>();
            try (InputStream in = getClass().getResourceAsStream("/data/job_grades.json")) {
                assertThat(in).as("the seed file /data/job_grades.json is on the classpath").isNotNull();
                for (JsonNode node : mapper.readTree(in)) {
                    out.add(grade(
                            node.path("grade").asText(null),
                            node.path("description").asText(null),
                            node.path("contractType").asText(null)));
                }
            }
            return out;
        }

        @Test
        @DisplayName("no seeded label is malformed")
        void noSeededLabelIsMalformed() throws Exception {
            List<JobGradeDto> grades = seededGrades();
            assertThat(grades)
                    .as("non-vacuity floor -- the seed parsed into something")
                    .hasSizeGreaterThan(20);

            List<String> malformed = new ArrayList<>();
            for (JobGradeDto g : grades) {
                JobGradeResponseDto dto = JobGradeResponseDto.from(g);
                if (dto == null) continue;
                String label = dto.getLabel();
                // The three signatures of the regression, and only those: a
                // trailing separator, a leading one, and a doubled space left
                // where an absent part used to sit.
                if (label.endsWith("-") || label.endsWith("- ")
                        || label.startsWith("-") || label.startsWith(" ")
                        || label.contains("  ")) {
                    malformed.add(label);
                }
            }
            assertThat(malformed).as("malformed seeded job-grade labels").isEmpty();
        }

        @Test
        @DisplayName("the seed really does exercise the broken shapes")
        void theSeedExercisesTheBrokenShapes() throws Exception {
            List<JobGradeDto> grades = seededGrades();

            // If the seed ever stopped containing rows with an absent description,
            // the test above would go green over a builder that had regressed --
            // it would simply have no case left to fail on. So assert the input
            // still contains the shape, not only that the output is clean.
            long blankDescription = grades.stream()
                    .filter(g -> g.getDescription() == null || g.getDescription().isBlank())
                    .count();
            assertThat(blankDescription)
                    .as("seeded rows with no description -- the shape that produced 'Grade M -'")
                    .isGreaterThan(0);

            long separatorInsideDescription = grades.stream()
                    .filter(g -> g.getDescription() != null && g.getDescription().contains(" - "))
                    .count();
            assertThat(separatorInsideDescription)
                    .as("seeded rows whose description contains the joiner itself")
                    .isGreaterThan(0);
        }
    }
}
