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
import za.co.titandynamix.sasbo_online_backend.entity.member.Member;
import za.co.titandynamix.sasbo_online_backend.entity.location.Area;
import za.co.titandynamix.sasbo_online_backend.entity.location.Province;
import za.co.titandynamix.sasbo_online_backend.entity.recruitment.Recruiter;
import za.co.titandynamix.sasbo_online_backend.entity.recruitment.RecruitmentChannel;
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
    private final RecruitmentChannelRepository recruitmentChannelRepository;
    private final RecruiterRepository recruiterRepository;
    private final SubscriptionsRepository subscriptionsRepository;
    private final JobGradeSeeder jobGradeSeeder;
    private final AreaRepository areaRepository;

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
                    // Legacy seed data carries jobGrade as a plain string; grades are now
                    // bank-specific JobGrade rows (seeded from data/job_grades.json below),
                    // so the transient value is dropped rather than persisted.
                    m.setJobGrade(null);
                    if (m.getPersonalEmail() == null || m.getPersonalEmail().isBlank()) {
                        m.setPersonalEmail(m.getPersonalEmail());
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

        // 🔴 R2-F4. This was `if (this.jobGradeRepository.count() == 0)` with the
        // whole seed inline -- a TABLE-EMPTY guard on a PER-ROW question. A database
        // already holding the original 27 rows never received the 31 Nedbank and
        // Capitec grades the seed file gained afterwards, and said nothing about it.
        // JobGradeSeeder upserts row by row, and is a bean the integration lane can
        // call directly -- which this class, being a @MockitoBean there, cannot.
        this.jobGradeSeeder.seed();

        // 03.3 -- the AREAS. The table has existed since V1 and `members.area_id`
        // points at it, but nothing has ever put a row in it. So "Area Recruited"
        // (RE-3) would have been a drop-down over an empty table: a decorative field,
        // exactly the shape CM-37 had for job grades.
        //
        // The real areas are already on file -- they are the distinct `area` values of
        // the seeded bank branches, which is the same universe the union means by an
        // area. Derived rather than transcribed, so a new branch in a new area brings
        // its area with it instead of needing a second edit somewhere else.
        //
        // ⚠️ Seeded HERE and not in a migration on purpose: `bank_branches` is itself
        // populated by this seeder, and Flyway runs before any ApplicationRunner -- a
        // migration reading that table would find it empty.
        if (this.areaRepository.count() == 0) {
            List<Area> areas = this.bankBranchRepository.findAll().stream()
                    .map(BankBranch::getArea)
                    .filter(name -> name != null && !name.isBlank())
                    .map(String::trim)
                    .distinct()
                    .sorted()
                    .map(name -> {
                        Area area = new Area();
                        area.setName(name);
                        return area;
                    })
                    .toList();
            this.areaRepository.saveAll(areas);
            log.info("Seeded {} areas", areas.size());
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

        ClassPathResource recruiterResource = new ClassPathResource("data/recruiter.json");
        try (InputStream recruiterStream = recruiterResource.getInputStream()) {
            List<RecruitmentChannel> recruitmentChannels = mapper.readValue(recruiterStream, new TypeReference<>() {});
            List<RecruitmentChannel> missingRecruitmentChannels = recruitmentChannels.stream()
                    .filter(type -> !this.recruitmentChannelRepository.existsByNameIgnoreCase(type.getName()))
                    .map(type -> {
                        type.setId(null);
                        return type;
                    })
                    .toList();

            if (!missingRecruitmentChannels.isEmpty()) {
                this.recruitmentChannelRepository.saveAll(missingRecruitmentChannels);
                log.info("Seeded {} missing recruitment types", missingRecruitmentChannels.size());
            } else {
                log.info("Skipping recruitment type seed; all configured recruitment types already exist");
            }
        }

        // Recruiters (SASBO organisers + other recruitment types) are seeded from
        // data/recruiters.json only when the table is empty. The V17 Flyway migration
        // seeds the same rows, so whichever runs first wins and no duplicates occur.
        if (this.recruiterRepository.count() == 0) {
            ClassPathResource recruitersResource = new ClassPathResource("data/recruiters.json");
            try (InputStream stream = recruitersResource.getInputStream()) {
                List<Recruiter> recruiters = mapper.readValue(stream, new TypeReference<>() {});
                recruiters.forEach(r -> r.setId(null));
                this.recruiterRepository.saveAll(recruiters);
                log.info("Seeded {} recruiters", recruiters.size());
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
