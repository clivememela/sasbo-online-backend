package za.co.titandynamix.sasbo_online_backend.utils;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import za.co.titandynamix.sasbo_online_backend.entity.bank.Bank;
import za.co.titandynamix.sasbo_online_backend.entity.member.JobGrade;
import za.co.titandynamix.sasbo_online_backend.repository.BankRepository;
import za.co.titandynamix.sasbo_online_backend.repository.JobGradeRepository;
import za.co.titandynamix.sasbo_online_backend.support.AbstractWritingIT;
import za.co.titandynamix.sasbo_online_backend.support.JobGradeSeedFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code job_grades} catches up with its seed file on an ALREADY-SEEDED database.
 *
 * <h2>🔴 The defect (R2-F4)</h2>
 * The seeding ran behind {@code if (jobGradeRepository.count() == 0)} — a
 * table-empty guard on a per-row question. The seed file later gained 31 grades
 * for two more banks, and <b>every database that had been started once before
 * that never received them</b>. Nothing failed and nothing was logged, because
 * the block the log line lives in is the block that was skipped. The grades are
 * what the capture form offers, so two banks' members were being captured
 * against a drop-down with nothing in it.
 *
 * <h2>🔑 Why this test could not have existed before the refactor</h2>
 * {@code AbstractPostgresIT} replaces {@code DataSeeder} with a
 * {@code @MockitoBean} — it is an unconditional {@code ApplicationRunner} that
 * would write 160 members before the first assertion of every integration test.
 * So the only code path that seeded job grades was unreachable from this lane,
 * which is precisely why a defect of this size had no test. The upsert is now
 * {@link JobGradeSeeder}, a bean, and this test calls it.
 *
 * <p>⚠️ <b>It is NOT a double boot.</b> Booting Spring twice to prove idempotence
 * would need a second context, and the job grades need {@code bank_id}s that
 * {@code DataSeeder} creates at startup. Calling the bean twice against one real
 * PostgreSQL proves the same property and costs one context.
 *
 * <h2>No count is written down</h2>
 * The seed file is read here too, and every expectation is derived from it. A
 * literal {@code 58} would be wrong the first time a grade is added, and the
 * test would then be edited to match rather than read. What IS asserted is that
 * both halves are non-empty — if the pre-load or the insert ever becomes zero
 * this test proves nothing, and says so instead of passing.
 */
@DisplayName("JobGradeSeeder — an already-seeded database catches up, once")
class JobGradeSeederIT extends AbstractWritingIT {

    /**
     * The two banks whose grades the seed file gained after the first release.
     *
     * <p>Named because they are the FACT the finding is about: these are the rows
     * that never arrived. Everything else is derived. If they were ever removed
     * from the seed file, {@code theCatchUpIsNotEmpty} reddens rather than this
     * test quietly proving nothing.
     */
    private static final Set<String> BANKS_ADDED_AFTER_THE_FIRST_RELEASE =
            Set.of("nedbank limited", "capitec bank limited");

    @Autowired
    private JobGradeSeeder seeder;

    @Autowired
    private JobGradeRepository jobGradeRepository;

    @Autowired
    private BankRepository bankRepository;

    private List<Map<String, Object>> seedRows;

    @BeforeEach
    void readTheSeedFileAndCreateItsBanks() throws IOException {
        this.seedRows = JobGradeSeedFile.rows();
        assertThat(this.seedRows)
                .as("the job-grade seed file was actually read")
                .isNotEmpty();

        // The seeder resolves a grade's bank BY NAME, so the banks have to exist
        // first -- which is the reason this is not a Flyway migration.
        JobGradeSeedFile.createBanksFor(this.bankRepository, this.seedRows);
    }

    @Test
    @DisplayName("🔴 the rows the seed file gained arrive on a database that already had the others")
    void anAlreadySeededDatabaseCatchesUp() throws IOException {
        int preloaded = preloadTheRowsThatWereAlreadyThere();

        JobGradeSeeder.Outcome first = this.seeder.seed();

        assertThat(first.inserted())
                .as("every seed row that was missing is inserted -- the file has %d rows "
                        + "and %d were already present", this.seedRows.size(), preloaded)
                .isEqualTo(this.seedRows.size() - preloaded);
        assertThat(this.jobGradeRepository.count())
                .as("and the table now holds exactly what the seed file describes")
                .isEqualTo(this.seedRows.size());
    }

    @Test
    @DisplayName("...and running it a second time changes nothing")
    void theSecondPassIsAQuietNoOp() throws IOException {
        preloadTheRowsThatWereAlreadyThere();
        this.seeder.seed();
        long afterFirst = this.jobGradeRepository.count();

        JobGradeSeeder.Outcome second = this.seeder.seed();

        assertThat(second.changedNothing())
                .as("a second pass over an unchanged file and an unchanged database "
                        + "must insert nothing and promote nothing, or startup writes "
                        + "a duplicate row every time the service restarts")
                .isTrue();
        assertThat(this.jobGradeRepository.count())
                .as("and the row count is untouched")
                .isEqualTo(afterFirst);
    }

    @Test
    @DisplayName("🗣️ decision ②: an unverified grade that matches a seed row is promoted")
    void aMatchingUnverifiedGradeIsPromoted() throws IOException {
        Map<String, Object> row = this.seedRows.get(0);
        Bank bank = this.bankRepository
                .findByNameIgnoreCase(String.valueOf(row.get("bankName")))
                .orElseThrow();

        // What an administrator typing the grade by hand leaves behind: the right
        // grade, differently spaced and cased, not yet confirmed by anyone.
        JobGrade typed = new JobGrade();
        typed.setBank(bank);
        typed.setGrade("  " + String.valueOf(row.get("grade")).toUpperCase(Locale.ROOT) + "  ");
        typed.setVerified(Boolean.FALSE);
        Integer typedId = this.jobGradeRepository.save(typed).getId();

        JobGradeSeeder.Outcome outcome = this.seeder.seed();

        assertThat(this.jobGradeRepository.findById(typedId).orElseThrow().getVerified())
                .as("the seed is the union's own list, so a typed grade that turns out "
                        + "to be on it was right -- and an unverified row is offered to "
                        + "nobody else until it is confirmed")
                .isTrue();
        assertThat(outcome.promoted())
                .as("and the promotion is reported, not silent")
                .isGreaterThanOrEqualTo(1);
        assertThat(this.jobGradeRepository.count())
                .as("promoting is not inserting: the typed row WAS the seed row, so the "
                        + "table still holds exactly one row per seed row")
                .isEqualTo(this.seedRows.size());
    }

    @Test
    @DisplayName("🔴 grades for a bank that does not exist are SKIPPED and counted, not lost")
    void gradesForAnUnknownBankAreSkippedAndCounted() throws IOException {
        // 🗣️ THE LIVE CASE, 2026-09-10. Stephne Anders' email supplies grades for SARB
        // and SBV; neither employer has a row in data/banks.json, and their universal
        // branch codes are not ours to invent. The grades are carried in the seed file
        // anyway so they are not lost in an inbox — which only works if the seeder
        // treats an unknown bank as a DATA QUESTION rather than a crash or a silent drop.
        //
        // This test removes one bank from the fixture and asserts the three properties
        // that make carrying those rows safe: nothing throws, nothing is inserted for
        // the missing bank, and the count comes back so the gap can be measured.
        Map<String, Object> orphan = this.seedRows.get(0);
        String missingBank = String.valueOf(orphan.get("bankName"));
        long expectedOrphans = this.seedRows.stream()
                .filter(r -> missingBank.equals(String.valueOf(r.get("bankName"))))
                .count();

        this.bankRepository.findByNameIgnoreCase(missingBank)
                .ifPresent(b -> this.bankRepository.delete(b));

        JobGradeSeeder.Outcome outcome = this.seeder.seed();

        assertThat(outcome.skipped())
                .as("every grade for %s is counted as skipped, so the gap is measurable "
                        + "rather than invisible", missingBank)
                .isEqualTo((int) expectedOrphans);
        assertThat(outcome.inserted())
                .as("and the rest still land -- a missing bank must not stop the seed")
                .isEqualTo(this.seedRows.size() - (int) expectedOrphans);
        assertThat(this.jobGradeRepository.count())
                .as("nothing was written for the bank that does not exist")
                .isEqualTo(this.seedRows.size() - expectedOrphans);
    }

    @Test
    @DisplayName("the fixture is non-vacuous — both halves of the catch-up are real")
    void theCatchUpIsNotEmpty() {
        int preloadable = 0;
        int missing = 0;
        for (Map<String, Object> row : this.seedRows) {
            if (wasAlreadyThere(row)) {
                preloadable++;
            } else {
                missing++;
            }
        }

        // 🔑 Without this, both tests above pass just as well when the seed file has
        // changed so that one side is empty: a pre-load of nothing makes the first
        // test an ordinary empty-table seed, and a catch-up of nothing makes it
        // assert that zero rows were inserted correctly.
        assertThat(preloadable)
                .as("rows representing what an older database already held")
                .isGreaterThan(0);
        assertThat(missing)
                .as("rows for %s -- the grades that never arrived, and the whole "
                        + "subject of this test", BANKS_ADDED_AFTER_THE_FIRST_RELEASE)
                .isGreaterThan(0);
    }

    /** Inserts the seed rows an older database would already be holding. */
    private int preloadTheRowsThatWereAlreadyThere() {
        List<JobGrade> existing = new ArrayList<>();
        for (Map<String, Object> row : this.seedRows) {
            if (!wasAlreadyThere(row)) {
                continue;
            }
            Bank bank = this.bankRepository
                    .findByNameIgnoreCase(String.valueOf(row.get("bankName")))
                    .orElseThrow();
            JobGrade grade = new JobGrade();
            grade.setBank(bank);
            grade.setGrade(String.valueOf(row.get("grade")));
            grade.setVerified(Boolean.TRUE);
            existing.add(grade);
        }
        this.jobGradeRepository.saveAll(existing);
        return existing.size();
    }

    private static boolean wasAlreadyThere(Map<String, Object> row) {
        String bank = String.valueOf(row.get("bankName")).toLowerCase(Locale.ROOT);
        return !BANKS_ADDED_AFTER_THE_FIRST_RELEASE.contains(bank);
    }

}
