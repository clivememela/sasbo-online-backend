package za.co.titandynamix.sasbo_online_backend.dto.recruitment;

import java.util.List;

/**
 * One recruitment channel and what the Recruiters screen reads of its members: how many were recruited through it,
 * and the banks and branches they work at (RC15). Worked out by the server in one grouped statement; the screen used to
 * load every member and count them itself.
 *
 * @param members  the members recruited through the channel, within the caller's region or area
 * @param banks    the distinct banks of those members, sorted
 * @param branches the distinct branches of those members, sorted
 */
public record ChannelSummaryDto(
        Long channelId,
        String name,
        boolean enabled,
        long members,
        List<String> banks,
        List<String> branches
) {
}
