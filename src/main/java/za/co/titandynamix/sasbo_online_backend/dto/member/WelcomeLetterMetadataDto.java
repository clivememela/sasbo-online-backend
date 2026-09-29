package za.co.titandynamix.sasbo_online_backend.dto.member;

/**
 * Whether this server emails a captured member their welcome letter -- that is, whether mail is
 * configured here. The capture screen reads it so it can say, BEFORE Send is pressed, that the member
 * will be saved and the letter will not go out (A64).
 */
public record WelcomeLetterMetadataDto(boolean emailEnabled) {
}
