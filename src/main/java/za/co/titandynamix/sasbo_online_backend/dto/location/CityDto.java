package za.co.titandynamix.sasbo_online_backend.dto.location;

import lombok.Data;

@Data
public class CityDto {
    private Long id;
    private String name;
    private Long provinceId;
    private String provinceName;
}
