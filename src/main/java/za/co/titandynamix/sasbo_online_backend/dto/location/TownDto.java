package za.co.titandynamix.sasbo_online_backend.dto.location;

import lombok.Data;

@Data
public class TownDto {
    private Long id;
    private String name;
    private Long cityId;
    private String cityName;
}
