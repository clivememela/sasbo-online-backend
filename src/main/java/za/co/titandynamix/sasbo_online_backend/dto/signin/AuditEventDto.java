package za.co.titandynamix.sasbo_online_backend.dto.signin;

/** One line of the record of who did what (signin.md); {@code at} is an ISO-8601 instant. */
public record AuditEventDto(String at, String username, String displayName, String action, String entityType,
                            String entityId, String summary) {
}
