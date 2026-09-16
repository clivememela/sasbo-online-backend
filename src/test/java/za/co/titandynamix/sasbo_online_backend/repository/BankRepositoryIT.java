package za.co.titandynamix.sasbo_online_backend.repository;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;
import za.co.titandynamix.sasbo_online_backend.entity.bank.Bank;
import za.co.titandynamix.sasbo_online_backend.fixtures.BankFixture;
import za.co.titandynamix.sasbo_online_backend.support.AbstractWritingIT;
import za.co.titandynamix.sasbo_online_backend.support.DatabaseReset;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The reference repository {@code *IT}. The other fourteen copy this file.
 *
 * <h2>What a repository IT is for -- and what it is not</h2>
 * Not CRUD round-trips on generated methods: Spring Data wrote those and they
 * are its tests to fail, not ours. These exist for the places where <b>the
 * database decides the answer</b> -- the declared query methods, the
 * {@code @Query} bodies, constraints, ordering and collation. That is precisely
 * the layer H2 cannot stand in for, and it is where the known
 * {@code /api/job-grades} defect lives: a query filtering on the wrong column,
 * invisible to every mocked service test above it.
 *
 * <h2>It leaves the database as it found it</h2>
 * One container is shared by every {@code *IT} in the JVM and classes run
 * concurrently, so each test resets first rather than trusting what came
 * before. {@link DatabaseReset} restores the migration seed rather than merely
 * sparing it, so a test cannot come to depend on rows a sibling happened to
 * leave.
 */
@DisplayName("BankRepository against the real migration chain")
class BankRepositoryIT extends AbstractWritingIT {

    @Autowired
    private BankRepository bankRepository;

    @Test
    @DisplayName("findByNameIgnoreCase matches whatever case the caller sent")
    void findByNameIgnoreCaseMatchesAnyCase() {
        // The declared query method, and the reason this class exists. It
        // compiles to an UPPER(...) comparison that PostgreSQL evaluates -- H2
        // in PostgreSQL mode would answer this differently for a non-ASCII
        // name, which is why the claim is only made here.
        this.bankRepository.save(BankFixture.named("Absa"));

        assertThat(this.bankRepository.findByNameIgnoreCase("absa")).isPresent();
        assertThat(this.bankRepository.findByNameIgnoreCase("ABSA")).isPresent();
        assertThat(this.bankRepository.findByNameIgnoreCase("AbSa")).isPresent();
    }

    @Test
    @DisplayName("findByNameIgnoreCase is exact about everything except case")
    void findByNameIgnoreCaseIsNotASubstringMatch() {
        // The negative half. Without it, the method could be a LIKE '%..%' and
        // every assertion above would still pass.
        this.bankRepository.save(BankFixture.named("Absa"));

        assertThat(this.bankRepository.findByNameIgnoreCase("Abs")).isEmpty();
        assertThat(this.bankRepository.findByNameIgnoreCase("Absa Bank")).isEmpty();
        assertThat(this.bankRepository.findByNameIgnoreCase(" Absa")).isEmpty();
    }

    @Test
    @DisplayName("orders by name ascending, in this container's collation")
    void ordersByNameAscending() {
        // The ordering BankServiceImpl asks for, asserted where it is actually
        // decided. The service test deliberately makes no claim about order,
        // because a mocked repository cannot support one.
        //
        // Every name here is collation-neutral: C, en_ZA.UTF-8 and en_US.UTF-8
        // all agree on this sequence, so a failure can only be the ORDER BY.
        for (String name : shuffledFixtureNames()) {
            this.bankRepository.save(BankFixture.named(name));
        }

        List<Bank> ordered = this.bankRepository.findAll(Sort.by(Sort.Direction.ASC, "name"));

        assertThat(ordered)
                .extracting(Bank::getName)
                .containsExactly(BankFixture.COLLATION_NEUTRAL_NAMES);
    }

    @Test
    @DisplayName("refuses a second bank with the same name")
    void nameIsUnique() {
        // A constraint the schema declares and nothing was asserting. It is
        // asserted here and not in the fast lane because H2's schema is one
        // Hibernate invents at startup, so a constraint test there proves the
        // entity annotation, not the migration chain.
        this.bankRepository.saveAndFlush(BankFixture.named("Absa"));

        assertThatThrownBy(() -> this.bankRepository.saveAndFlush(BankFixture.named("Absa")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("refuses a bank with no universal branch code")
    void universalBranchCodeIsRequired() {
        Bank withoutCode = BankFixture.named("Capitec");
        withoutCode.setUniversalBranchCode(null);

        assertThatThrownBy(() -> this.bankRepository.saveAndFlush(withoutCode))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("starts from the migration chain's own state, not a sibling's leftovers")
    void startsFromTheMigrationSeed() {
        // Makes the reset visible. A test that silently inherits rows is a test
        // whose result depends on which sibling ran first, and the suite runs
        // shuffled.
        assertThat(this.bankRepository.count()).isZero();
    }

    /**
     * The fixture names in an order that is not the expected one, so that a
     * repository returning insertion order fails.
     */
    private static List<String> shuffledFixtureNames() {
        // Fixed, not shuffled at runtime: an unseeded shuffle is a test whose
        // input changes between runs. This order is simply not sorted.
        return List.of("Nedbank", "Absa", "Standard", "Capitec");
    }
}
