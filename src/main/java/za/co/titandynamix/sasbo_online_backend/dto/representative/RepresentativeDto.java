package za.co.titandynamix.sasbo_online_backend.dto.representative;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

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
    private String personalEmail;
    private String workEmail;
    private String preferredEmail;
    private String phone;
    private Integer memberId;              // the member who is this representative
    private Integer areaId;                // the area this representative belongs to
    private Integer roleId;                // the union role (RepresentativeRole)
    private List<Integer> structureIds;    // the union structures/positions they sit on (multi-select)
    private Boolean isElectedRep;          // whether this is an elected representative (Y/N)
    private LocalDate electionDate;        // election date (only when isElectedRep = true)
    private Integer membersCount;          // number of members recruited by this representative (read-only)
}
