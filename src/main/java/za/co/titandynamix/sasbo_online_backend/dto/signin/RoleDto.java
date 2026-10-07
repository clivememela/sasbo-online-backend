package za.co.titandynamix.sasbo_online_backend.dto.signin;

import java.util.List;

/**
 * A role (signin.md): its code, name and permissions -- and whether it is the fixed System Administrator's, whose
 * permissions are not edited, and its version.
 */
public record RoleDto(String code, String name, List<String> permissions, boolean fixed, long version) {
}
