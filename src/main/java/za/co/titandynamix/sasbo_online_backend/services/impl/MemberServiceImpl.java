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

    @Override
    public Optional<Member> updateBanking(Integer id, Member banking) {
        return this.memberRepository.findById(id).map(existing -> {
            existing.setBankName(banking.getBankName());
            existing.setBankAccountNumber(banking.getBankAccountNumber());
            existing.setBranch(banking.getBranch());
            existing.setIbtNo(banking.getIbtNo());
            existing.setWorkIbtNo(banking.getWorkIbtNo());
            existing.setBankAddress(banking.getBankAddress());
            existing.setWorkBankAddress(banking.getWorkBankAddress());
            existing.setPaymentType(banking.getPaymentType());
            existing.setDebitDay(banking.getDebitDay());
            return this.memberRepository.save(existing);
        });
    }
}
