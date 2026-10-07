package za.co.titandynamix.sasbo_online_backend.dto.recruitment;

import java.util.List;

/** A page of a recruitment channel's members, in the shape every paged list of this API answers with. */
public record ChannelMembersPageDto(
        List<ChannelMemberDto> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
}
