package za.co.titandynamix.sasbo_online_backend.utils;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import za.co.titandynamix.sasbo_online_backend.entity.bank.Bank;
import za.co.titandynamix.sasbo_online_backend.entity.bank.BankBranch;
import za.co.titandynamix.sasbo_online_backend.entity.Member;
import za.co.titandynamix.sasbo_online_backend.entity.location.Province;
import za.co.titandynamix.sasbo_online_backend.entity.recruiter.RecruiterType;
import za.co.titandynamix.sasbo_online_backend.entity.representative.Representative;
import za.co.titandynamix.sasbo_online_backend.entity.subscription.Subscriptions;
import za.co.titandynamix.sasbo_online_backend.dto.subscription.SubscriptionsDto;
import za.co.titandynamix.sasbo_online_backend.enums.SubscriptionsStatus;
import za.co.titandynamix.sasbo_online_backend.repository.*;

import java.io.InputStream;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class DataSeeder implements ApplicationRunner {

    private final MemberRepository memberRepository;
    private final BankRepository bankRepository;
    private final BankBranchRepository bankBranchRepository;
    private final RepresentativeRepository representativeRepository;
    private final ProvinceRepository provinceRepository;
    private final RecruiterTypeRepository recruiterTypeRepository;
    private final SubscriptionsRepository subscriptionsRepository;

    @Override
    public void run(@NonNull ApplicationArguments args) throws Exception {
        JsonMapper mapper = JsonMapper.builder()
                .findAndAddModules()
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .build();

        if (this.memberRepository.count() == 0) {
            ClassPathResource membersResource = new ClassPathResource("data/members.json");
            try (InputStream stream = membersResource.getInputStream()) {
                List<Member> members = mapper.readValue(stream, new TypeReference<>() {});
                members.forEach(m -> {
                    m.setId(null);
                    if (m.getPersonalEmail() == null || m.getPersonalEmail().isBlank()) {
                        m.setPersonalEmail(m.getEmail());
                    }
                    if (m.getPreferredEmail() == null || m.getPreferredEmail().isBlank()) {
                        m.setPreferredEmail("personal");
                    }
                });
                this.memberRepository.saveAll(members);
                log.info("Seeded {} members", members.size());
            }
        }

        if (this.bankRepository.count() == 0) {
            ClassPathResource banksMasterResource = new ClassPathResource("data/banks_master.json");
            try (InputStream bankStream = banksMasterResource.getInputStream()) {
                List<Bank> banks = mapper.readValue(bankStream, new TypeReference<>() {});
                banks.forEach(b -> b.setId(null));
                this.bankRepository.saveAll(banks);
                log.info("Seeded {} banks", banks.size());
            }
        }

        if (this.bankBranchRepository.count() == 0) {
            ClassPathResource banksBranchResource = new ClassPathResource("data/banks.json");
            try (InputStream stream = banksBranchResource.getInputStream()) {
                List<BankBranch> branches = mapper.readValue(stream, new TypeReference<>() {});
                List<Bank> allBanks = this.bankRepository.findAll();
                branches.forEach(branch -> {
                    branch.setId(null);
                    if (branch.getBankName() != null) {
                        allBanks.stream()
                                .filter(b -> b.getName().equalsIgnoreCase(branch.getBankName()))
                                .findFirst()
                                .ifPresent(branch::setBank);
                    }
                });
                this.bankBranchRepository.saveAll(branches);
                log.info("Seeded {} bank branches", branches.size());
            }
        }

        ClassPathResource subscriptionsResource = new ClassPathResource("data/subscriptions.json");
        try (InputStream subsStream = subscriptionsResource.getInputStream()) {
            List<SubscriptionsDto> subsDtos = mapper.readValue(subsStream, new TypeReference<>() {});
            int desired = subsDtos.size();
            long existing = this.subscriptionsRepository.count();
            log.info("subscriptionsRepository.count() = {}, desired seed size = {}", existing, desired);
            List<Bank> allBanks = this.bankRepository.findAll();
            if (existing < desired) {
                List<Subscriptions> subs = subsDtos.stream().map(dto -> {
                    Subscriptions s = new Subscriptions();
                    s.setId(null);
                    if (dto.bankName() != null) {
                        allBanks.stream()
                                .filter(b -> b.getName().equalsIgnoreCase(dto.bankName()))
                                .findFirst()
                                .ifPresent(s::setBank);
                    }
                    s.setAcbDebitDay(dto.acbDebitDay());
                    s.setSubscriptionAmount(dto.subscriptionAmount());
                    s.setIsCoreBanking(dto.isCoreBanking());
                    s.setStatus(dto.status() == null ? SubscriptionsStatus.PENDING : dto.status());
                    s.setRegistrationDate(dto.registrationDate());
                    return s;
                }).toList();
                this.subscriptionsRepository.saveAll(subs);
                log.info("Seeded {} subscriptions", subs.size());
            } else {
                log.info("Skipping subscriptions seed; existing ({} ) >= desired ({})", existing, desired);
            }
        }

        // Representatives are created manually via API; no seeding needed
        if (this.representativeRepository.count() == 0) {
            log.info("No representatives seeded; manage via API");
        }

        if (this.recruiterTypeRepository.count() == 0) {
            ClassPathResource recruiterResource = new ClassPathResource("data/recruiter.json");
            try (InputStream recruiterStream = recruiterResource.getInputStream()) {
                List<RecruiterType> recruiterTypes = mapper.readValue(recruiterStream, new TypeReference<>() {});
                recruiterTypes.forEach(r -> r.setId(null));
                this.recruiterTypeRepository.saveAll(recruiterTypes);
                log.info("Seeded {} recruiter types", recruiterTypes.size());
            }
        }

        if (this.provinceRepository.count() == 0) {
            ClassPathResource provincesResource = new ClassPathResource("data/provinces.json");
            try (InputStream provinceStream = provincesResource.getInputStream()) {
                List<Province> provinces = mapper.readValue(provinceStream, new TypeReference<>() {});
                provinces.forEach(p -> p.setId(null));
                this.provinceRepository.saveAll(provinces);
                log.info("Seeded {} provinces", provinces.size());
            }
        }

    }
}
