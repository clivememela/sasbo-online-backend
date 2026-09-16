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

    public static JobGradeResponseDto from(JobGradeDto jobGrade) {
        if (jobGrade == null || jobGrade.getGrade() == null) {
            return null;
        }

        String grade = jobGrade.getGrade().trim();
        return new JobGradeResponseDto(grade, grade);
    }
}

