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

        String label = String.join(" - ",
                jobGrade.getGrade(),
                jobGrade.getDescription() != null ? jobGrade.getDescription() : "",
                jobGrade.getContractType() != null ? jobGrade.getContractType() : ""
        ).replaceAll(" - $", "").replaceAll(" - - ", " - ").trim();

        return new JobGradeResponseDto(jobGrade.getGrade(), label);
    }
}

