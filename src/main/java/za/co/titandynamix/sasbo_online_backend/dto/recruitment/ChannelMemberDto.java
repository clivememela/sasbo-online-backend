package za.co.titandynamix.sasbo_online_backend.dto.recruitment;

/**
 * A member as the Recruiters screen lists them: who they are, where they bank, and their status -- and nothing of
 * their identity number or bank account, which no list on that screen shows.
 */
public record ChannelMemberDto(
        Integer id,
        String membershipNumber,
        String title,
        String initials,
        String firstNames,
        String surname,
        String bankName,
        String branch,
        String status
) {
}
