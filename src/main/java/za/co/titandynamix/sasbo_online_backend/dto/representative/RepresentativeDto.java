package za.co.titandynamix.sasbo_online_backend.dto.representative;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RepresentativeDto {
    private Integer id;
    private String name;
    private String email;
    private String phone;
    private Integer memberId;  // the member who is this representative
}
