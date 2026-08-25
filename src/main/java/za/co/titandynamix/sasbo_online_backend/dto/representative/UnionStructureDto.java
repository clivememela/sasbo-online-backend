package za.co.titandynamix.sasbo_online_backend.dto.representative;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UnionStructureDto {
    private Integer id;
    private String name;
    private Integer parentId;  // null for root-level structures

    /** Child structures — only populated in tree responses. */
    private List<UnionStructureDto> children;

    /** Whether a member on this structure must provide an election date. */
    private Boolean requiresElectionDate;
}
