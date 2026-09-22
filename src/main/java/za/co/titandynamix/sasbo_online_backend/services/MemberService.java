package za.co.titandynamix.sasbo_online_backend.services;

import za.co.titandynamix.sasbo_online_backend.dto.member.MemberStatusChangeRequest;
import za.co.titandynamix.sasbo_online_backend.entity.member.Member;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface MemberService {
    List<Member> findAll();
    Optional<Member> findById(Integer id);
    Member create(Member member);
    Optional<Member> update(Integer id, Member updated);
    Optional<Member> updateBanking(Integer id, Member banking);
    Optional<Member> updateMonthlySubscription(Integer id, BigDecimal monthlySubscription);
    void resolveStructureAndRole(Member member);

    /**
     * ACB 03.5 -- moves a member's status, and nothing else, recording who, when and why ({@code
     * member_status_changes}, V50). NARROW BY DESIGN: it never runs the whole-record validation {@code update}
     * runs, because a member captured before a rule existed would be refused a status change over a field on
     * another tab (R-ROB-I). Empty when there is no such member; a change to the status the member already has
     * records nothing and answers the member as they are.
     */
    Optional<Member> changeStatus(Integer memberId, MemberStatusChangeRequest change);
}
