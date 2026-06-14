package za.co.titandynamix.sasbo_online_backend;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
import za.co.titandynamix.sasbo_online_backend.repository.BankRepository;
import za.co.titandynamix.sasbo_online_backend.repository.BankBranchRepository;
import za.co.titandynamix.sasbo_online_backend.repository.MemberRepository;

import java.io.InputStream;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class DataSeeder implements ApplicationRunner {

    private final MemberRepository memberRepository;
    private final BankRepository bankRepository;
    private final BankBranchRepository bankBranchRepository;

    @Override
    public void run(ApplicationArguments args) throws Exception {
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
            List<BankBranch> banks = mapper.readValue(stream, new TypeReference<>() {});
            banks.forEach(b -> b.setId(null));
            bankBranchRepository.saveAll(banks);
            log.info("Seeded {} bank branches", banks.size());
        }
    }
}
