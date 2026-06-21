package za.co.titandynamix.sasbo_online_backend.dto.representative;

import lombok.Data;
import za.co.titandynamix.sasbo_online_backend.dto.user.RoleDto;

import java.time.LocalDateTime;
import java.util.Set;

@Data
public class RepresentativeDto {
    private Integer id;
    private String name;
    private String email;
    private String phone;
    private Set<RepresentativeTypeDto> representativeTypeDtos;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
