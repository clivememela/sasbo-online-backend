package za.co.titandynamix.sasbo_online_backend.services;

import za.co.titandynamix.sasbo_online_backend.dto.recruitment.ChannelMemberDto;
import za.co.titandynamix.sasbo_online_backend.dto.recruitment.ChannelMembersPageDto;
import za.co.titandynamix.sasbo_online_backend.dto.recruitment.ChannelSummaryDto;
import za.co.titandynamix.sasbo_online_backend.dto.recruitment.MemberCandidatesDto;

import java.util.List;

/**
 * The recruitment channels and the members recruited through them, as the Recruiters screen reads and changes them
 * (RC15): the figures, a channel's list, the pickers, and putting a member on a channel or taking them off it.
 */
public interface RecruitmentChannelMembersService {

    /** The most members a page of a channel's list carries. */
    int MAX_PAGE_SIZE = 100;

    /** The most members a picker offers before it asks for a narrower search. */
    int PICKER_ROWS = 50;

    /** The longest search term taken. */
    int MAX_TERM_LENGTH = 100;

    /** Every channel with its figures, within the caller's scope. */
    List<ChannelSummaryDto> summary();

    /** One page of a channel's members, narrowed by any of the five criteria (each optional). */
    ChannelMembersPageDto members(long channelId, String membershipNumber, String name, String bank, String branch,
                                  String status, int page, int size);

    /** The members a picker offers, not recruited through {@code excludeChannel} when one is named. */
    MemberCandidatesDto candidates(String query, Long excludeChannel);

    /** Records the member as recruited through the channel; the same channel again is a no-op. */
    ChannelMemberDto assign(long channelId, int memberId);

    /** Takes the member off the channel; a member on no channel is a no-op, so a retry is safe. */
    void unassign(long channelId, int memberId);
}
