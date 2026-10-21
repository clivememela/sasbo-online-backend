package za.co.titandynamix.sasbo_online_backend.dto.workplace;

import java.util.List;

/**
 * The members at each branch of a batch print, in ONE answer (October build review: one read for the batch, not one a
 * branch). Answered only to a caller holding {@code MEMBERS_VIEW}; a branch outside the caller's region or area is
 * answered as such, with no member.
 *
 * @param branches  the workplaces asked for that are on record, in the order asked
 * @param unknown   the ids asked for that are no workplace on record here
 * @param perBranch the most members answered for one branch; a branch with more says how many it has in all
 */
public record WorkplaceMembersDto(List<Branch> branches, List<Long> unknown, int perBranch) {

    /**
     * One branch's members.
     *
     * @param outOfScope    the branch is outside the caller's region or area: no member is answered for it
     * @param members       its members on record, in a listing's order, {@code perBranch} at most
     * @param totalElements how many members are on record there in all
     */
    public record Branch(long workplaceId, boolean outOfScope, List<WorkplaceMemberPageDto.Member> members,
                         long totalElements) {
    }
}
