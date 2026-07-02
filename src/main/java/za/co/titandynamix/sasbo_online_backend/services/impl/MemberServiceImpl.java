package za.co.titandynamix.sasbo_online_backend.services.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import za.co.titandynamix.sasbo_online_backend.entity.Member;
import za.co.titandynamix.sasbo_online_backend.mapper.MemberMapper;
import za.co.titandynamix.sasbo_online_backend.repository.MemberRepository;
import za.co.titandynamix.sasbo_online_backend.services.MemberService;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class MemberServiceImpl implements MemberService {

    private final MemberRepository memberRepository;
    private final MemberMapper memberMapper;

    @Override
    public List<Member> findAll() {
        return this.memberRepository.findAll();
    }

    @Override
    public Optional<Member> findById(Integer id) {
        return this.memberRepository.findById(id);
    }

    @Override
    public Member create(Member member) {
        member.setId(null);
        return this.memberRepository.save(member);
    }

    @Override
    public Optional<Member> update(Integer id, Member updated) {
        return this.memberRepository.findById(id).map(existing -> {
            this.memberMapper.updateFromMember(updated, existing);
            return this.memberRepository.save(existing);
        });
    }
}
