package za.co.titandynamix.sasbo_online_backend.dto.location;

import lombok.Data;

@Data
public class SuburbDto {
    private Long id;
    private String name;
    private String postalCode;
    private Long townId;
    private String townName;
}
