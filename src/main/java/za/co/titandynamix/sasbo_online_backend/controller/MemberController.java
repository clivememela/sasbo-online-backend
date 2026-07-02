package za.co.titandynamix.sasbo_online_backend.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import za.co.titandynamix.sasbo_online_backend.entity.Member;
import za.co.titandynamix.sasbo_online_backend.repository.MemberRepository;

import java.util.List;

@RestController
@RequestMapping("/api/members")
@RequiredArgsConstructor
public class MemberController {

    private final MemberRepository memberRepository;

    @GetMapping
    public List<Member> getAll() {
        return memberRepository.findAll();
    }

    @GetMapping("/{id}")
    public ResponseEntity<Member> getById(@PathVariable Integer id) {
        return memberRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}")
    public ResponseEntity<Member> update(@PathVariable Integer id, @RequestBody Member updated) {
        return memberRepository.findById(id).map(existing -> {
            existing.setTitle(updated.getTitle());
            existing.setInitials(updated.getInitials());
            existing.setSurname(updated.getSurname());
            existing.setFirstNames(updated.getFirstNames());
            existing.setIdentityNumber(updated.getIdentityNumber());
            existing.setPassportNumber(updated.getPassportNumber());
            existing.setGender(updated.getGender());
            existing.setRace(updated.getRace());
            existing.setStatus(updated.getStatus());
            existing.setJoinDate(updated.getJoinDate());
            existing.setEmail(updated.getEmail());
            existing.setPhone(updated.getPhone());
            existing.setCellTel(updated.getCellTel());
            existing.setWorkTel(updated.getWorkTel());
            existing.setHomeTel(updated.getHomeTel());
            existing.setFaxNumber(updated.getFaxNumber());
            existing.setAddress(updated.getAddress());
            existing.setBankName(updated.getBankName());
            existing.setBankAccountNumber(updated.getBankAccountNumber());
            existing.setBranch(updated.getBranch());
            existing.setIbtNo(updated.getIbtNo());
            existing.setWorkIbtNo(updated.getWorkIbtNo());
            existing.setBankAddress(updated.getBankAddress());
            existing.setWorkBankAddress(updated.getWorkBankAddress());
            existing.setPaymentType(updated.getPaymentType());
            existing.setDebitDay(updated.getDebitDay());
            existing.setJobGrade(updated.getJobGrade());
            existing.setMonthlySubscription(updated.getMonthlySubscription());
            existing.setSubscriptionFrequency(updated.getSubscriptionFrequency());
            existing.setDeathBenefitSubs(updated.getDeathBenefitSubs());
            existing.setDeathBenefitFrequency(updated.getDeathBenefitFrequency());
            existing.setDeathBenefitPolicyNo(updated.getDeathBenefitPolicyNo());
            existing.setEndowmentSubs(updated.getEndowmentSubs());
            existing.setEndowmentFrequency(updated.getEndowmentFrequency());
            existing.setEndowmentPolicyNo(updated.getEndowmentPolicyNo());
            existing.setBenevolentSubs(updated.getBenevolentSubs());
            existing.setBenevolentFrequency(updated.getBenevolentFrequency());
            existing.setBenevolentPolicyNo(updated.getBenevolentPolicyNo());
            existing.setRemarks(updated.getRemarks());
            existing.setRemarks2(updated.getRemarks2());
            existing.setRemarks3(updated.getRemarks3());
            existing.setRemarks4(updated.getRemarks4());
            existing.setManagersCode(updated.getManagersCode());
            existing.setMemnetMember(updated.getMemnetMember());
            existing.setPrintLabelForPostage(updated.getPrintLabelForPostage());
            existing.setSuspensionCode(updated.getSuspensionCode());
            existing.setUseAltAddress(updated.getUseAltAddress());
            existing.setDeductACB(updated.getDeductACB());
            existing.setPrintMembershipCards(updated.getPrintMembershipCards());
            existing.setHomeLoanReducedRateOption(updated.getHomeLoanReducedRateOption());
            existing.setRecruitmentRep(updated.getRecruitmentRep());
            existing.setAreasRep(updated.getAreasRep());
            existing.setRepElectionDay(updated.getRepElectionDay());
            existing.setRepElectionMonth(updated.getRepElectionMonth());
            existing.setRepElectionYear(updated.getRepElectionYear());
            existing.setElectedRep(updated.getElectedRep());
            existing.setNationalCouncillor(updated.getNationalCouncillor());
            existing.setBranchCommitteeMember(updated.getBranchCommitteeMember());
            return ResponseEntity.ok(memberRepository.save(existing));
        }).orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<Member> create(@RequestBody Member member) {
        member.setId(null);
        return ResponseEntity.ok(memberRepository.save(member));
    }
}
