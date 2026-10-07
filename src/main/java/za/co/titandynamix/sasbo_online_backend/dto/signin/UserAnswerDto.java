package za.co.titandynamix.sasbo_online_backend.dto.signin;

/** {@code GET /api/auth/me} and the answer to an edit: {@code {user}}. */
public record UserAnswerDto(SignInUserDto user) {
}
