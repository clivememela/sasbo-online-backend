package za.co.titandynamix.sasbo_online_backend.utils;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.introspect.Annotated;
import com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector;
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
    private final BankSeeder bankSeeder;
    private final JobGradeSeeder jobGradeSeeder;
    private final AreaRepository areaRepository;

    /**
     * The seed members, WITH the fields the request-body rules refuse.
     *
     * <p>🔴 A61. {@code Member.membershipNumber} is {@code @JsonProperty(access = READ_ONLY)} and
     * this file used to read {@code data/members.json} straight into {@code Member} through an
     * ordinary mapper -- so Jackson discarded all 160 numbers on the way in, exactly as that
     * annotation asks it to, and 160 members were persisted with a blank membership number. The
     * register showed an empty column for every seeded row, and {@code V22} then derived the
     * sequence's start from {@code MAX} of the numbers that had survived, of which there were none.
     *
     * <p>⚠️ THE ANNOTATION IS RIGHT AND IS NOT TOUCHED. {@code MemberController} binds
     * {@code @RequestBody Member} -- the entity itself -- on POST, PUT and PATCH, so without
     * READ_ONLY a caller could supply a membership number and aim a write at another member's row.
     * The defect was never the annotation; it was that <b>the seeder is not an API caller</b> and
     * had no business reading the server's own data file through the request-body rules.
     *
     * <p>🔑 AN ALLOW-LIST OF ONE, AND THE FIRST VERSION OF THIS FIX WAS WRONG. It relaxed READ_ONLY to
     * {@code AUTO} for EVERY property, reasoning that the caller nulls {@code id} straight after.
     * {@code SeedMemberReaderTest}'s guard on {@code id} went red against it -- the test written to
     * catch exactly this. {@code Member} carries READ_ONLY on EIGHT properties: {@code id},
     * {@code membershipNumber}, and the entity relations {@code recruitmentRepresentative},
     * {@code recruiter}, {@code role}, {@code structure}, {@code user}, plus {@code jobGradeOutcome}.
     * The seed file carries only the first two today, so a blanket relaxation looked harmless -- and
     * would have let the next edit to {@code members.json} silently bind entity relations from a data
     * file. Relying on a caller to undo what a reader should never have done is the shape this
     * project keeps paying for. So exactly the properties the seed file is ENTITLED to set are named
     * below, and everything else keeps the protection its annotation declares.
     *
     * <p>🔴 AND THE SECOND VERSION WAS WRONG TOO, in the other direction: it keyed the allow-list on
     * {@code annotated.getName()}, which Jackson hands over as the SETTER's name, so it matched
     * nothing and relaxed nothing -- all three A61 tests stayed red. See {@link #propertyNameOf}.
     * Both failures were caught by the red-green proof, which is why that proof is run and not
     * skipped: the first version would have shipped a wider hole, the second a fix that did nothing.
     *
     * <p>The blunter fixes stay rejected: {@code MapperFeature.USE_ANNOTATIONS = false} would drop
     * every annotation at once, and widening the entity would trade a blank column for a
     * write-targeting hole.
     *
     * <p>📌 The deeper shape is still open and is NOT fixed here: an entity annotated for HTTP
     * binding is doing duty as a seed-file DTO, and those two jobs have different rules. A seed DTO
     * is the real answer; this is the narrow repair for a live defect on main.
     */
    /**
     * The READ_ONLY properties a seed file may nonetheless set. Deliberately a list of ONE.
     * Adding to it is a decision about what the server's own data may assert, so it wants a reason
     * written beside it -- and never an entity relation, which a data file must not wire up.
     */
    static final java.util.Set<String> SEED_MAY_SET_DESPITE_READ_ONLY = java.util.Set.of("membershipNumber");

    /**
     * The PROPERTY an accessor belongs to -- {@code setMembershipNumber} → {@code membershipNumber}.
     *
     * <p>⚠️ NOT {@code annotated.getName()}, and the second version of this fix failed because it
     * used that. Jackson merges a field's annotations into its property and then asks for access
     * THROUGH THE MUTATOR, so {@code findPropertyAccess} is handed the setter
     * ({@code AnnotatedMethod name=setMembershipNumber}) even though the annotation is written on the
     * field and the compiled setter carries none. Measured, not assumed: an instrumented run reported
     * all eight READ_ONLY properties on this entity as {@code set*} methods and not one as a field.
     * Comparing that raw name against a property name matched nothing, so the fix silently did
     * nothing -- which is exactly why the allow-list is keyed on the property, derived here.
     */
    private static String propertyNameOf(Annotated annotated) {
        String name = annotated.getName();
        if (annotated instanceof com.fasterxml.jackson.databind.introspect.AnnotatedMethod) {
            for (String prefix : new String[] {"set", "get", "is"}) {
                if (name.length() > prefix.length()
                        && name.startsWith(prefix)
                        && Character.isUpperCase(name.charAt(prefix.length()))) {
                    return Character.toLowerCase(name.charAt(prefix.length())) + name.substring(prefix.length() + 1);
                }
            }
        }
        return name;
    }

    static List<Member> readSeedMembers(InputStream stream) throws java.io.IOException {
        JsonMapper seedMapper = JsonMapper.builder()
                .findAndAddModules()
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .annotationIntrospector(new JacksonAnnotationIntrospector() {
                    @Override
                    public JsonProperty.Access findPropertyAccess(Annotated annotated) {
                        JsonProperty.Access access = super.findPropertyAccess(annotated);
                        return access == JsonProperty.Access.READ_ONLY
                                && SEED_MAY_SET_DESPITE_READ_ONLY.contains(propertyNameOf(annotated))
                                ? JsonProperty.Access.AUTO
                                : access;
                    }
                })
                .build();
        return seedMapper.readValue(stream, new TypeReference<List<Member>>() {});
    }

    @Override
    public void run(@NonNull ApplicationArguments args) throws Exception {
        JsonMapper mapper = JsonMapper.builder()
                .findAndAddModules()
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .build();

        if (this.memberRepository.count() == 0) {
            ClassPathResource membersResource = new ClassPathResource("data/members.json");
            try (InputStream stream = membersResource.getInputStream()) {
                List<Member> members = readSeedMembers(stream);
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

        // Row by row, not behind a table-empty guard: see BankSeeder and BankSeederIT.
        this.bankSeeder.seed();

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
