package za.co.titandynamix.sasbo_online_backend.utils;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import za.co.titandynamix.sasbo_online_backend.entity.bank.Bank;
import za.co.titandynamix.sasbo_online_backend.repository.BankRepository;
import za.co.titandynamix.sasbo_online_backend.support.AbstractWritingIT;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code banks} catches up with its seed file on an ALREADY-SEEDED database.
 *
 * <h2>🔴 The defect: R2-F4, one table over</h2>
 * Banks were seeded behind {@code if (bankRepository.count() == 0)}, the same
 * table-empty guard on a per-row question that {@code JobGradeSeederIT} exists for.
 * It was found live, 2026-09-16: two banks were added to the seed file (the South
 * African Reserve Bank and SBV Services, whose grades Stephne Anders' email
 * supplies), and a database that already held the other banks never received them.
 * The grade seeder then had no bank to attach 24 grades to and skipped them all, so
 * both employers' Job Grade drop-downs stayed empty. Nothing failed and nothing was
 * logged.
 *
 * <p>Like {@code JobGradeSeeder}, the upsert is a bean so this lane can call it:
 * {@code AbstractPostgresIT} mocks {@code DataSeeder} away.
 *
 * <p>No count is written down: every expectation is derived from the seed file.
 */
@DisplayName("BankSeeder — an already-seeded database catches up, once")
class BankSeederIT extends AbstractWritingIT {

    /** How many seed banks the "old" database is missing. Two, as in the live case. */
    private static final int MISSING = 2;

    @Autowired
    private BankSeeder seeder;

    @Autowired
    private BankRepository bankRepository;

    private List<Bank> seedBanks;

    @BeforeEach
    void readTheSeedFile() throws IOException {
        this.seedBanks = BankSeeder.readSeedFile();
        assertThat(this.seedBanks)
                .as("the bank seed file was read, and holds more banks than the preload leaves out")
                .hasSizeGreaterThan(MISSING);
    }

    @Test
    @DisplayName("🔴 banks the seed file gained arrive on a database that already had the others")
    void anAlreadySeededDatabaseCatchesUp() throws IOException {
        preloadAllButTheLast(MISSING);

        int inserted = this.seeder.seed();

        assertThat(inserted)
                .as("the %d banks the database was missing are inserted", MISSING)
                .isEqualTo(MISSING);
        assertThat(namesInTheTable())
                .as("and the table now holds exactly the seed file's banks")
                .isEqualTo(namesIn(this.seedBanks));
    }

    @Test
    @DisplayName("...and running it a second time changes nothing")
    void theSecondPassIsAQuietNoOp() throws IOException {
        preloadAllButTheLast(MISSING);
        this.seeder.seed();
        long afterFirst = this.bankRepository.count();

        int second = this.seeder.seed();

        assertThat(second)
                .as("a second pass must insert nothing, or every restart duplicates a bank "
                        + "(and banks.name is UNIQUE, so it would crash startup instead)")
                .isZero();
        assertThat(this.bankRepository.count()).isEqualTo(afterFirst);
    }

    @Test
    @DisplayName("a bank already present under different case or spacing is not inserted twice")
    void theNameMatchIgnoresCaseAndSpacing() throws IOException {
        // The must-pass half: a seeder that inserts by exact string would pass the
        // catch-up test and still duplicate a bank somebody typed differently.
        List<Bank> preloaded = new ArrayList<>();
        for (Bank seed : this.seedBanks) {
            Bank copy = copyOf(seed);
            copy.setName("  " + seed.getName().toUpperCase(Locale.ROOT) + " ");
            preloaded.add(copy);
        }
        this.bankRepository.saveAll(preloaded);

        assertThat(this.seeder.seed())
                .as("every seed bank is already there, spelled differently")
                .isZero();
    }

    @Test
    @DisplayName("an empty database still receives every bank")
    void anEmptyDatabaseIsSeededInFull() throws IOException {
        assertThat(this.bankRepository.count()).as("the reset left banks empty").isZero();

        assertThat(this.seeder.seed()).isEqualTo(this.seedBanks.size());
    }

    private void preloadAllButTheLast(int missing) {
        List<Bank> older = this.seedBanks.subList(0, this.seedBanks.size() - missing)
                .stream().map(BankSeederIT::copyOf).toList();
        this.bankRepository.saveAll(older);
        assertThat(this.bankRepository.count())
                .as("the preload is real, so the catch-up is not an empty-table seed in disguise")
                .isEqualTo(this.seedBanks.size() - missing);
    }

    private static Bank copyOf(Bank seed) {
        Bank bank = new Bank();
        bank.setName(seed.getName());
        bank.setUniversalBranchCode(seed.getUniversalBranchCode());
        bank.setBranches(new ArrayList<>());
        return bank;
    }

    private Set<String> namesInTheTable() {
        return namesIn(this.bankRepository.findAll());
    }

    private static Set<String> namesIn(List<Bank> banks) {
        return banks.stream().map(Bank::getName).collect(Collectors.toSet());
    }
}
