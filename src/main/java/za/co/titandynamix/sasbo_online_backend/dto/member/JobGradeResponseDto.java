package za.co.titandynamix.sasbo_online_backend.dto.member;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

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

        // The label is the grade alone (Clive, 1206c76 / client a23fb81): the description
        // and contract type made the drop-down unreadable, and the bargaining unit now
        // travels in its own fields rather than inside the label text.
        String grade = jobGrade.getGrade().trim();
        return new JobGradeResponseDto(
                grade,
                grade,
                jobGrade.getBargainingUnit(),
                jobGrade.getBargainingUnitCondition());
    }
}

