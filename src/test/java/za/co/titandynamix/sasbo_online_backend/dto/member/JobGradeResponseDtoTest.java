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
 * The job-grade label: what the drop-down actually reads.
 *
 * <h2>The contract changed, deliberately</h2>
 * The label used to join grade, description and contract type (CM-33). That builder
 * regressed once: a refactor joined first and repaired the result with
 * {@code replaceAll}, which cannot work because the joiner {@code " - "} occurs inside
 * the data ("Non - Bargaining"), and nine of twenty-seven labels came out malformed
 * ({@code "Grade M -"}, {@code "Grade c -  - PFA/DFA Sales"}).
 *
 * <p>main (Clive, 1206c76, with client a23fb81) replaced the join with the <b>grade
 * alone</b>, and this arc adopts it on merge. The bargaining classification that the
 * description used to smuggle into the label now travels in its own fields
 * ({@code bargainingUnit}, {@code bargainingUnitCondition}). So there is no join left
 * to break, and what is pinned below is the new property: the label is the trimmed
 * grade, nothing else, whatever the other columns hold -- including the shapes that
 * broke the old builder, so a return to joining fails here immediately.
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
    @DisplayName("the label is the grade alone")
    class GradeOnly {

        @Test
        @DisplayName("a grade with nothing else is itself")
        void gradeAlone() {
            assertThat(labelOf("Grade M", null, null)).isEqualTo("Grade M");
            assertThat(labelOf("Grade M", "", "")).isEqualTo("Grade M");
        }

        @Test
        @DisplayName("description and contract type never reach the label")
        void otherColumnsAreNotJoined() {
            // The shapes that broke the old join, kept as inputs: if anyone brings the
            // join back, every one of these fails.
            assertThat(labelOf("Grade c", null, "PFA/DFA Sales")).isEqualTo("Grade c");
            assertThat(labelOf("BA1", "Bargaining unit", null)).isEqualTo("BA1");
            assertThat(labelOf("C3", "Bargaining Unit", "Permanent")).isEqualTo("C3");
            assertThat(labelOf("AVP", "Non - Bargaining", null)).isEqualTo("AVP");
        }

        @Test
        @DisplayName("surrounding whitespace is trimmed; internal spacing is kept")
        void trimmed() {
            assertThat(labelOf("  D Upper ", null, null)).isEqualTo("D Upper");
        }
    }

    @Test
    @DisplayName("the value is the trimmed grade, the same text as the label")
    void valueIsTheGrade() {
        // The client submits `value`, and the server matches it back to a stored row
        // with JobGradeMatcher, which folds whitespace -- so trimming loses nothing.
        JobGradeResponseDto dto = JobGradeResponseDto.from(grade(" BA1 ", "Bargaining unit", null));
        assertThat(dto.getValue()).isEqualTo("BA1");
        assertThat(dto.getLabel()).isEqualTo(dto.getValue());
    }

    @Test
    @DisplayName("the bargaining classification travels beside the label, not inside it")
    void bargainingFieldsAreCarried() {
        JobGradeDto capitecC = grade("C", null, null);
        capitecC.setBargainingUnit("CONDITIONAL");
        capitecC.setBargainingUnitCondition("Bargaining unit ONLY for Assistant Branch Managers");

        JobGradeResponseDto dto = JobGradeResponseDto.from(capitecC);

        assertThat(dto.getLabel()).isEqualTo("C");
        assertThat(dto.getBargainingUnit()).isEqualTo("CONDITIONAL");
        assertThat(dto.getBargainingUnitCondition()).contains("Assistant Branch Managers");
    }

    @Test
    @DisplayName("a row with no grade is not a label")
    void noGradeIsNotALabel() {
        assertThat(JobGradeResponseDto.from(grade(null, "Bargaining unit", "Permanent"))).isNull();
        assertThat(JobGradeResponseDto.from(null)).isNull();
    }

    /**
     * THE CANARY: the seed the application actually ships. The floor is a
     * non-vacuity guard -- a seed that parsed into nothing would otherwise satisfy
     * the assertion by having nothing to violate it.
     */
    @Nested
    @DisplayName("canary -- the real seed data")
    class Canary {

        @Test
        @DisplayName("every seeded label is its own trimmed grade, and none is blank")
        void everySeededLabelIsTheGrade() throws Exception {
            ObjectMapper mapper = new ObjectMapper();
            List<String> wrong = new ArrayList<>();
            int seen = 0;
            try (InputStream in = getClass().getResourceAsStream("/data/job_grades.json")) {
                assertThat(in).as("the seed file /data/job_grades.json is on the classpath").isNotNull();
                for (JsonNode node : mapper.readTree(in)) {
                    JobGradeResponseDto dto = JobGradeResponseDto.from(grade(
                            node.path("grade").asText(null),
                            node.path("description").asText(null),
                            node.path("contractType").asText(null)));
                    if (dto == null) continue;
                    seen++;
                    String expected = node.path("grade").asText().trim();
                    if (expected.isEmpty() || !expected.equals(dto.getLabel())) {
                        wrong.add(node.toString());
                    }
                }
            }
            assertThat(seen).as("non-vacuity floor -- the seed parsed into something").isGreaterThan(20);
            assertThat(wrong).as("seeded rows whose label is not their grade").isEmpty();
        }
    }
}
