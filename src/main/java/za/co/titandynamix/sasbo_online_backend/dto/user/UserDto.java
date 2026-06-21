package za.co.titandynamix.sasbo_online_backend.dto.user;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Set;

@Data
public class UserDto {
    private String id;
    private String title;
    private String firstname;
    private String lastname;
    private String username;
    private LocalDate dob;
    private long tel;
    private String tag;
    private String gender;
    private boolean enabled;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Set<RoleDto> roles;
}
