package za.co.titandynamix.sasbo_online_backend.dto.recruitment;

import java.util.List;

/**
 * The members a picker offers: at most a short list, and whether there were more -- so the screen can ask for a
 * narrower search instead of presenting fifty as if they were all.
 */
public record MemberCandidatesDto(
        List<ChannelMemberDto> rows,
        boolean more
) {
}
