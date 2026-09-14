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

        return new JobGradeResponseDto(jobGrade.getGrade(), label);
    }
}

