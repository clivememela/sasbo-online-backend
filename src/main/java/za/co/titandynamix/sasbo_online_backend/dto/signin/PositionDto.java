package za.co.titandynamix.sasbo_online_backend.dto.signin;

/**
 * A position (signin.md): its code, name, default role, whether it is in use, and its place in SASBO's order -- and its
 * version, which an edit may send back to be sure nobody changed it since.
 */
public record PositionDto(String code, String name, String defaultRoleCode, boolean active, int ordinal, long version) {
}
