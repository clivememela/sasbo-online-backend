package za.co.titandynamix.sasbo_online_backend.utils;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;
import za.co.titandynamix.sasbo_online_backend.entity.bank.Bank;
import za.co.titandynamix.sasbo_online_backend.entity.bank.BankBranch;
import za.co.titandynamix.sasbo_online_backend.entity.Member;
import za.co.titandynamix.sasbo_online_backend.entity.Representative;
import za.co.titandynamix.sasbo_online_backend.repository.BankRepository;
import za.co.titandynamix.sasbo_online_backend.repository.BankBranchRepository;
import za.co.titandynamix.sasbo_online_backend.repository.MemberRepository;
import za.co.titandynamix.sasbo_online_backend.repository.RepresentativeRepository;

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

    @Override
    public void run(@NonNull ApplicationArguments args) throws Exception {
        JsonMapper mapper = JsonMapper.builder()
                .findAndAddModules()
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .build();

        if (memberRepository.count() == 0) {
            InputStream stream = new ClassPathResource("data/members.json").getInputStream();
            List<Member> members = mapper.readValue(stream, new TypeReference<>() {});
            members.forEach(m -> m.setId(null));
            memberRepository.saveAll(members);
            log.info("Seeded {} members", members.size());
        }

        if (bankRepository.count() == 0) {
            InputStream bankStream = new ClassPathResource("data/banks_master.json").getInputStream();
            List<Bank> banks = mapper.readValue(bankStream, new TypeReference<>() {});
            banks.forEach(b -> b.setId(null));
            bankRepository.saveAll(banks);
            log.info("Seeded {} banks", banks.size());
        }

        if (bankBranchRepository.count() == 0) {
            InputStream stream = new ClassPathResource("data/banks.json").getInputStream();
            List<BankBranch> branches = mapper.readValue(stream, new TypeReference<>() {});
            List<Bank> allBanks = bankRepository.findAll();

            branches.forEach(branch -> {
                branch.setId(null);
                if (branch.getBankName() != null) {
                    allBanks.stream()
                            .filter(b -> b.getName().equalsIgnoreCase(branch.getBankName()))
                            .findFirst()
                            .ifPresent(branch::setBank);
                }
            });
            bankBranchRepository.saveAll(branches);
            log.info("Seeded {} bank branches", branches.size());
        }

        if (representativeRepository.count() == 0) {
            List<Member> allMembers = memberRepository.findAll();
            List<Representative> reps = allMembers.stream()
                    .map(Member::getAreasRep)
                    .filter(name -> name != null && !name.isBlank())
                    .map(String::trim)
                    .distinct()
                    .sorted()
                    .map(name -> { Representative r = new Representative(); r.setName(name); return r; })
                    .toList();
            representativeRepository.saveAll(reps);
            log.info("Seeded {} representatives", reps.size());
        }
    }
}
