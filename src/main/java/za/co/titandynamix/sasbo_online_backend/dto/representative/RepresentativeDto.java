package za.co.titandynamix.sasbo_online_backend.dto.representative;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RepresentativeDto {
    private Integer id;
    private String firstNames;
    private String surname;
    /** Combined display name (firstNames + surname). Read-only on the API response. */
    private String name;
    private String email;
    private String phone;
    private Integer memberId;              // the member who is this representative
    private Integer areaId;                // the area this representative belongs to
    private Integer roleId;                // the union role (RepresentativeRole)
    private Integer structureId;           // the union structure (if applicable)
    private LocalDate electionDate;        // election date (if applicable)
    private Integer membersCount;          // number of members recruited by this representative (read-only)
}
