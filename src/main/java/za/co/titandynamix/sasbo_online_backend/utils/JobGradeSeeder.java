package za.co.titandynamix.sasbo_online_backend.utils;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import za.co.titandynamix.sasbo_online_backend.entity.bank.Bank;
import za.co.titandynamix.sasbo_online_backend.entity.member.JobGrade;
import za.co.titandynamix.sasbo_online_backend.enums.BargainingUnit;
import za.co.titandynamix.sasbo_online_backend.enums.GradingSystem;
import za.co.titandynamix.sasbo_online_backend.repository.BankRepository;
import za.co.titandynamix.sasbo_online_backend.repository.JobGradeRepository;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Brings {@code job_grades} up to date with {@code data/job_grades.json}, row by row.
 *
 * <h2>🔴 What this replaces, and why it is a separate bean (R2-F4)</h2>
 * The seeding lived inside {@link DataSeeder} behind
 * {@code if (jobGradeRepository.count() == 0)}. That guard asks "is this table
 * empty?" when the question is "is this row present?" — so <b>a database that
 * already held the 27 original rows never received the 31 Nedbank and Capitec
 * grades the seed file gained</b>. Every environment that had been started once
 * before those rows were written is still missing them, silently, and the log
 * line that would have said so never runs. Production is such an environment.
 *
 * <p>🔑 <b>It is NOT a migration.</b> A job grade needs a {@code bank_id}, and the
 * banks are themselves seeded from JSON by {@code DataSeeder} at startup — so a
 * Flyway script has no stable ids to point at and would have to re-resolve the
 * names itself, in SQL, duplicating the matching rule this class shares with the
 * service. The upsert belongs where the bank lookup already lives.
 *
 * <p>🔑 <b>And it is a bean of its own so that a test can CALL it.</b>
 * {@code AbstractPostgresIT} replaces {@code DataSeeder} with a
 * {@code @MockitoBean}, because as an unconditional {@code ApplicationRunner} it
 * would write 160 members before the first assertion of every integration test.
 * That mock also made the job-grade block unreachable from the integration lane,
 * which is why a defect this size had no test. Extracted, it is autowired and
 * invoked directly, twice, against a real PostgreSQL — see
 * {@code JobGradeSeederIT}.
 *
 * <h2>The match key</h2>
 * {@code (bank, JobGradeMatcher.comparisonKey(grade))} — the same rule the capture
 * service matches a typed grade with, not a second spelling of it. Four registered
 * defects in this project are one rule implemented twice; this is not the fifth.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JobGradeSeeder {

    private final BankRepository bankRepository;
    private final JobGradeRepository jobGradeRepository;

    /** What one pass did, so the caller can log it and a test can assert it. */
    public record Outcome(int inserted, int promoted, int unchanged, int skipped) {

        /** Whether this pass changed anything — the idempotence question. */
        public boolean changedNothing() {
            return this.inserted == 0 && this.promoted == 0;
        }
    }

    /**
     * Upserts every row of the seed file.
     *
     * <p>Idempotent by construction: a second pass over an unchanged file and an
     * unchanged database inserts nothing and promotes nothing.
     *
     * @return what this pass did
     */
    public Outcome seed() throws IOException {
        List<JobGradeSeed> seeds = readSeedFile();
        Map<String, Bank> banksByName = new HashMap<>();
        for (Bank bank : this.bankRepository.findAll()) {
            if (bank.getName() != null) {
                banksByName.put(bank.getName().toLowerCase(Locale.ROOT), bank);
            }
        }

        int inserted = 0;
        int promoted = 0;
        int unchanged = 0;
        int skipped = 0;
        Map<String, Integer> skippedByBank = new java.util.TreeMap<>();
        List<JobGrade> toSave = new ArrayList<>();

        for (JobGradeSeed seed : seeds) {
            Bank bank = seed.bankName() == null
                    ? null
                    : banksByName.get(seed.bankName().toLowerCase(Locale.ROOT));
            if (bank == null) {
                // A seed row for a bank the bank seed does not contain is a DATA
                // QUESTION, not a crash — and it is currently the state of 24 rows.
                //
                // ⚠️ Counted per bank and reported ONCE below, rather than a warning
                // per row. SARB and SBV between them are 24 grades; twenty-four
                // near-identical WARN lines at every boot is how a real signal becomes
                // scenery, and this one is a live gap somebody has to close.
                skippedByBank.merge(String.valueOf(seed.bankName()), 1, Integer::sum);
                skipped++;
                continue;
            }

            JobGrade existing = findByMatchKey(bank, seed.grade());
            if (existing == null) {
                toSave.add(buildFrom(seed, bank));
                inserted++;
            } else if (!Boolean.TRUE.equals(existing.getVerified())) {
                // 🗣️ DECISION ②, owner, 2026-09-10: PROMOTE. The seed is the union's
                // own list, so a grade an administrator typed that turns out to be on
                // it was right, and leaving it unverified means it is never offered to
                // the next person who needs it.
                //
                // 🔑 Only the flag. The other columns are left exactly as stored: the
                // decision was about verification, and overwriting a description
                // somebody may have corrected by hand is a different change that
                // nobody has asked for.
                existing.setVerified(Boolean.TRUE);
                toSave.add(existing);
                promoted++;
            } else {
                unchanged++;
            }
        }

        if (!toSave.isEmpty()) {
            this.jobGradeRepository.saveAll(toSave);
        }
        if (!skippedByBank.isEmpty()) {
            // 🔴 NOT noise, and not a failure either. These are grades the union has
            // GIVEN US whose employer has no row in data/banks.json, so there is no
            // bank_id to hang them on. Stephne Anders' email of 2026-09-09 supplies
            // SARB (7 levels) and SBV (17: TASK Grade 1-11 in the bargaining unit,
            // Paterson D/E/F Lower and Upper out of it); neither employer is in the
            // bank seed, and their universal branch codes are not ours to invent.
            //
            // Adding those two banks makes all of them land with no further change
            // here — which is the whole reason the rows are carried rather than left
            // in an inbox. See planning/branches/capture-member/jobGrades.md.
            log.warn("{} job grade(s) are waiting for a bank that does not exist in the "
                            + "bank seed, by employer: {}. They are NOT lost — add the "
                            + "bank and they seed themselves.",
                    skipped, skippedByBank);
        }
        Outcome outcome = new Outcome(inserted, promoted, unchanged, skipped);
        if (outcome.changedNothing()) {
            log.info("Job grades already up to date ({} rows matched, {} skipped)",
                    unchanged, skipped);
        } else {
            log.info("Job grades: {} inserted, {} promoted to verified, {} already "
                            + "current, {} skipped for an unknown bank",
                    inserted, promoted, unchanged, skipped);
        }
        return outcome;
    }

    /**
     * The stored row for this bank and grade, or {@code null}.
     *
     * <p>Scanned in memory rather than queried: the comparison collapses internal
     * whitespace and case, which no index on {@code grade} can express, and the
     * per-bank row count is in the tens. {@code V28}'s partial unique index (planned) makes
     * the database agree with this rule; it does not replace it.
     */
    private JobGrade findByMatchKey(Bank bank, String grade) {
        String key = JobGradeMatcher.comparisonKey(grade);
        if (key.isEmpty()) {
            return null;
        }
        return this.jobGradeRepository.findByBank_Id(bank.getId()).stream()
                .filter(row -> key.equals(JobGradeMatcher.comparisonKey(row.getGrade())))
                .findFirst()
                .orElse(null);
    }

    private JobGrade buildFrom(JobGradeSeed seed, Bank bank) {
        JobGrade jobGrade = new JobGrade();
        jobGrade.setBank(bank);
        jobGrade.setGrade(seed.grade());
        jobGrade.setContractType(seed.contractType());
        jobGrade.setDescription(seed.description());
        // Seeded rows ARE the master list, so they are verified. A grade an
        // administrator types later is captured with verified=false and is not offered
        // to anyone else until it is confirmed (02.5, V20).
        jobGrade.setVerified(Boolean.TRUE);
        // 02.6. Absent in the seed means "the union has not told us", which is a real
        // answer and is why the column is nullable.
        jobGrade.setBargainingUnit(seed.bargainingUnit() == null
                ? null
                : BargainingUnit.valueOf(seed.bargainingUnit()));
        jobGrade.setBargainingUnitCondition(seed.bargainingUnitCondition());
        // main's is_bargaining_council (74fdd09), DERIVED rather than read from the file:
        // one fact stored twice is how A45 happened. A yes/no cannot say CONDITIONAL.
        jobGrade.setIsBargainingCouncil(isBargainingCouncil(jobGrade.getBargainingUnit()));
        jobGrade.setGradingSystem(seed.gradingSystem() == null
                ? GradingSystem.BANK_SPECIFIC
                : GradingSystem.valueOf(seed.gradingSystem()));
        jobGrade.setBand(seed.band());
        return jobGrade;
    }

    /** BU true, NBU false, CONDITIONAL or unknown null -- the same rule as V27's backfill. */
    static Boolean isBargainingCouncil(BargainingUnit unit) {
        if (unit == null) {
            return null;
        }
        return switch (unit) {
            case BU -> Boolean.TRUE;
            case NBU -> Boolean.FALSE;
            case CONDITIONAL -> null;
        };
    }

    private List<JobGradeSeed> readSeedFile() throws IOException {
        JsonMapper mapper = JsonMapper.builder()
                .findAndAddModules()
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .build();
        try (InputStream stream = new ClassPathResource("data/job_grades.json").getInputStream()) {
            return mapper.readValue(stream, new TypeReference<>() {});
        }
    }

    /** Shape of the entries in data/job_grades.json; the bank is resolved by name. */
    private record JobGradeSeed(String bankName, String grade, String contractType, String description,
                                String bargainingUnit, String bargainingUnitCondition,
                                String gradingSystem, String band) {
    }
}
