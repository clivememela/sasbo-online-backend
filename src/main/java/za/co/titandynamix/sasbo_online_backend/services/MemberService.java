package za.co.titandynamix.sasbo_online_backend.services;

import za.co.titandynamix.sasbo_online_backend.entity.Member;

import java.util.List;
import java.util.Optional;

public interface MemberService {
    List<Member> findAll();
    Optional<Member> findById(Integer id);
    Member create(Member member);
    Optional<Member> update(Integer id, Member updated);
    Optional<Member> updateBanking(Integer id, Member banking);
}
