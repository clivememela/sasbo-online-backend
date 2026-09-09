package za.co.titandynamix.sasbo_online_backend.dto.member;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.stream.Collectors;
import java.util.stream.Stream;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class JobGradeResponseDto {

    @JsonProperty("value")
    private String value;

    @JsonProperty("label")
    private String label;

    /**
     * The bargaining-unit classification, so the screen can show it beside the grade
     * (02.6). Null means the union has not told us, which is a real answer for twelve
     * of the seeded grades and must not be rendered as "no".
     */
    @JsonProperty("bargainingUnit")
    private String bargainingUnit;

    /**
     * What else has to be true when the classification is CONDITIONAL, in words.
     * Carried to the browser deliberately: a rule nobody can read is a rule nobody
     * applies, and the two conditional cases here -- Capitec's job title and
     * Nedbank's missing package threshold -- are exactly the ones an organiser needs
     * to see rather than trust.
     */
    @JsonProperty("bargainingUnitCondition")
    private String bargainingUnitCondition;

    public static JobGradeResponseDto from(JobGradeDto jobGrade) {
        if (jobGrade == null || jobGrade.getGrade() == null) {
            return null;
        }

        // Drop the absent parts BEFORE joining. Joining first and then trying to
        // scrub the leftover separators cannot work: a grade with no description
        // and no contract type came out as "BA1 -", and one with a contract type
        // but no description came out with a doubled space. Filtering first has
        // no such cases -- the separator is only ever placed between two parts
        // that exist.
        String label = Stream.of(
                        jobGrade.getGrade(),
                        jobGrade.getDescription(),
                        jobGrade.getContractType())
                .filter(part -> part != null && !part.isBlank())
                .collect(Collectors.joining(" - "));

        return new JobGradeResponseDto(
                jobGrade.getGrade(),
                label,
                jobGrade.getBargainingUnit(),
                jobGrade.getBargainingUnitCondition());
    }
}

