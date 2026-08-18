package za.co.titandynamix.sasbo_online_backend.dto.representative;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UnionStructureDto {
    private Integer id;
    private String name;
    private Integer parentId;  // null for root-level structures
}
