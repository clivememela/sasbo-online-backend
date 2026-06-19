package za.co.titandynamix.sasbo_online_backend.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class RepresentativeDto {
    private Integer id;
    private String name;
    private String email;
    private String phone;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
