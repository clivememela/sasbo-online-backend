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
import za.co.titandynamix.sasbo_online_backend.acb.AcbLogging;
import za.co.titandynamix.sasbo_online_backend.config.SasboEnvironment;
import za.co.titandynamix.sasbo_online_backend.services.impl.AcbHistorySeeder;
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
    private final BankSeeder bankSeeder;
    private final JobGradeSeeder jobGradeSeeder;
    private final AreaRepository areaRepository;
    private final SubscriptionSeeder subscriptionSeeder;
    private final DebitOrderMemberSeeder debitOrderMemberSeeder;
    private final SeedMemberNumbers seedMemberNumbers;
    private final SeedMemberRepository seedMembers;
    private final SasboEnvironment environment;
    private final AcbHistorySeeder acbHistorySeeder;
    private final za.co.titandynamix.sasbo_online_backend.services.impl.DodScenarioSeeder dodScenarioSeeder;
    private final ReferenceListSeeder referenceListSeeder;
    private final WorkplaceSeeder workplaceSeeder;
    private final WorkplaceVisitSeeder workplaceVisitSeeder;
    private final DemonstrationMemberSeeder demonstrationMemberSeeder;
    private final MonthEndTakeSeeder monthEndTakeSeeder;
    private final MonthEndRulesSeeder monthEndRulesSeeder;
    private final SignInSeeder signInSeeder;
    private final RecruitmentChannelSeeder recruitmentChannelSeeder;
    private final VisitDraftSeeder visitDraftSeeder;
    private final MonthEndSendSeeder monthEndSendSeeder;
    private final PayrollSeeder payrollSeeder;

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

        // SIGN-IN'S BLOCK (October build), FIRST -- so every seeder after it, any stream's, finds the positions and the
        // demonstration users it names -- and EACH CALL CAUGHT: a fault here must stop nothing else. The positions and
        // roles are configuration and are seeded everywhere; the first administrator only while nobody can sign in and
        // the host names one; the demonstration users only where asked for, never under the production marker (the
        // seeder checks it itself, so no caller can forget to).
        seedMonthEnd("the sign-in positions and roles", this.signInSeeder::seedPositionsAndRoles);
        seedMonthEnd("the first administrator", this.signInSeeder::seedBootstrapAdministrator);
        seedMonthEnd("the demonstration users", this.signInSeeder::seedDemoUsers);

        // 🔒 SYNTHETIC PEOPLE ARE NEVER SEEDED WHERE THE PRODUCTION MARKER IS SET (SD3-A). A brand-new
        // production database is empty at its first boot -- exactly when the table-empty guard below
        // opens -- and the debit-order members are at real banks, with account numbers that pass the
        // shape gate: a bureau file is an instruction to take money. So the marker is the lock, not
        // the emptiness of a table. The reference data further down (banks, grades, areas, the
        // subscription rows, provinces) is real and is seeded everywhere.
        if (this.environment.isProduction()) {
            long seeded = this.seedMembers.countMembershipNumbersIn(this.seedMemberNumbers.all());
            if (seeded > 0) {
                log.warn("{} seeded (synthetic) member(s) are on this PRODUCTION database. No bureau file will "
                        + "include them (FILE_HAS_SEED_MEMBERS); they should be removed", seeded);
            }
        } else if (this.memberRepository.count() == 0) {
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

        // The debit-order population a run is prepared from (D33): WHEN ABSENT, keyed by membership
        // number, so it also reaches a database that was started long ago. Never in production -- the
        // seeder checks the marker itself, so no caller can forget to.
        this.debitOrderMemberSeeder.seed();

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

        // 🔴 F54. This was BY COUNT and all at once -- `if (existing < desired)` then every row -- so a
        // corrected row never reached a database that already held fifteen, and a sixteenth would have
        // doubled all of them. Per row, keyed by bank, and Standard Bank's orphan repaired: see
        // SubscriptionSeeder.
        this.subscriptionSeeder.seed();

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

        // MONTH END'S BLOCK (MD65): after everything seeded above and before ACB's history, EACH CALL CAUGHT -- a
        // fault of this arc's must not stop the 160, the banks or the grades from being seeded, and must not hide
        // either: each seeder records what happened beside its list, and the status read answers it (ME-18). Within
        // the block the order is the order the rows refer to each other: SASBO's lists, then the workplaces that
        // stand on them, which are seeded only where the deployment's switch is set (MD45, MD53).
        seedMonthEnd("SASBO's lists", this.referenceListSeeder::seedOrRecord);
        seedMonthEnd("SASBO's workplaces", this.workplaceSeeder::seed);
        // A.1: their history of visits, the months the branch list prints -- on the workplaces they stand on, only where
        // the switch is set (MD45, MD53).
        seedMonthEnd("SASBO's branch visits", this.workplaceVisitSeeder::seed);
        // Then the members who stand at them (01.4): the invented, and the seed's own 310 given a workplace in the
        // same areas -- both only where the switch says all, never where the production marker is set (MD53).
        seedMonthEnd("the invented members", () -> seedQuietly(this.demonstrationMemberSeeder::seed));
        seedMonthEnd("the seed members' workplaces", () -> seedQuietly(this.demonstrationMemberSeeder::place));
        // 03.7: their past -- the three months before the clock's, written through the real status change.
        seedMonthEnd("the invented members' past", () -> seedQuietly(this.demonstrationMemberSeeder::past));
        // October build: invented visits saved as drafts and submitted -- only where the switch says all. Before the
        // months are taken, so what a taken month freezes of its visits and what it reads of them agree.
        seedMonthEnd("the invented visit drafts", this.visitDraftSeeder::seed);
        // October build: month end's rules -- her codes, reasons and tiers where absent; members suspended within
        // several banks, some lapsed; a rejoin in the same month; a workplace over its complement. Before the take, so
        // the seed's months are taken with them.
        seedMonthEnd("month end's rules", () -> seedQuietly(this.monthEndRulesSeeder::seed));
        // Payroll (October build): the employers -- reference data, everywhere -- and the demonstration's members, placed
        // at their employers' workplaces where those are served: BEFORE the months are taken, so the takes hold them.
        seedMonthEnd("the payroll employers", this.payrollSeeder::seedEmployers);
        seedMonthEnd("the payroll members", () -> seedQuietly(this.payrollSeeder::seedMembers));
        // 04.2: those three months TAKEN -- after their past, which the take freezes, and only where the workplaces
        // are on record (MD54).
        seedMonthEnd("the demonstration's past months, taken", this.monthEndTakeSeeder::seed);
        // Payroll's demonstration files, built from whoever is on record by now; never under the marker.
        seedMonthEnd("the payroll demonstration", () -> seedQuietly(this.payrollSeeder::seedFiles));
        // RC15: every seeded member -- the invented ones included, so after the block above -- recruited through one of
        // SASBO's channels where none is recorded, so the Recruiters screen's figures are not all 0. Caught like the
        // block's own: a fault here must not stop the start, and is logged with its cause.
        seedMonthEnd("the seed members' recruitment channels", this.recruitmentChannelSeeder::seed);
        // October build: who gets what -- the regions' recipients, the banks' files, the providers -- and the newest taken
        // month's packs and files, made and delivered; after the takes, which the packs are made from.
        seedMonthEnd("the files that leave SASBO", this.monthEndSendSeeder::seed);

        // The seed's A.C.B. history (D33, 03.8): LAST, because it prepares runs from the members, banks
        // and subscription rows seeded above -- there is no @Order to sequence a second runner (F49).
        // 🔒 Its failure is caught and logged with its cause, never fatal: a demo must start, and must not
        // hide the fault either (SD3-I). The production marker was validated at binding, outside this
        // catch (F59), and the seeder itself never runs where it is set (SD3-A).
        try {
            this.acbHistorySeeder.seed();
        } catch (RuntimeException seedingFailed) {
            log.error("The A.C.B. history could not be seeded; the application starts without it",
                    AcbLogging.scrubbed(seedingFailed));
        }
        // The Debit Order Deductions demonstration (Stephne's answers, 6 October 2026): AFTER the history it works on,
        // all or nothing in one transaction, never in production. Caught like the history: a demo must start.
        try {
            this.dodScenarioSeeder.seed();
        } catch (RuntimeException seedingFailed) {
            log.error("The Debit Order demonstration could not be seeded; the application starts without it",
                    AcbLogging.scrubbed(seedingFailed));
        }
    }

    /** A seeder that reads its file: an unreadable file is a failure of the seeder, caught like any other. */
    private static void seedQuietly(IoSeeder seeder) {
        try {
            seeder.seed();
        } catch (java.io.IOException unreadable) {
            throw new java.io.UncheckedIOException(unreadable);
        }
    }

    @FunctionalInterface
    private interface IoSeeder {
        int seed() throws java.io.IOException;
    }

    /**
     * One call of month end's block, caught (MD65, MV-58): logged scrubbed -- the class and the stack, never the
     * message, which may quote a row -- and the start goes on to the next. Package-private for its own test (DL-70).
     */
    static void seedMonthEnd(String what, Runnable seeder) {
        try {
            seeder.run();
        } catch (RuntimeException seedingFailed) {
            log.error("{} could not be seeded; the application starts without them", what,
                    AcbLogging.scrubbed(seedingFailed));
        }
    }

}
