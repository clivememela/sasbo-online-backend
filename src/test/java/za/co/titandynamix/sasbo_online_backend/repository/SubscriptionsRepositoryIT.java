package za.co.titandynamix.sasbo_online_backend.repository;

import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import za.co.titandynamix.sasbo_online_backend.entity.bank.Bank;
import za.co.titandynamix.sasbo_online_backend.fixtures.BankFixture;
import za.co.titandynamix.sasbo_online_backend.fixtures.SubscriptionsFixture;
import za.co.titandynamix.sasbo_online_backend.support.AbstractWritingIT;
import za.co.titandynamix.sasbo_online_backend.support.DatabaseReset;
import za.co.titandynamix.sasbo_online_backend.support.QueryCount;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The three hand-written {@code @Query} methods on
 * {@code SubscriptionsRepository}, each of which exists to avoid N+1 -- they
 * are the application's only {@code LEFT JOIN FETCH}es.
 *
 * <p>So this is where the query-count assertion belongs. A fetch join that
 * silently stops working still returns the right rows; the only visible symptom
 * is the statement count, and only when the table is large enough for anyone to
 * notice.
 */
@DisplayName("SubscriptionsRepository against the real migration chain")
class SubscriptionsRepositoryIT extends AbstractWritingIT {

    @Autowired
    private SubscriptionsRepository subscriptionsRepository;

    @Autowired
    private BankRepository bankRepository;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Test
    @DisplayName("findAllWithBank returns every subscription with its bank attached")
    void findAllWithBank() {
        Bank absa = this.bankRepository.save(BankFixture.named("Absa"));
        this.save(1L, absa);
        this.save(2L, absa);

        assertThat(this.subscriptionsRepository.findAllWithBank()).hasSize(2);
    }

    @Test
    @DisplayName("findAllWithBank keeps a subscription that has no bank")
    void findAllWithBankKeepsBanklessRows() {
        // It is a LEFT join, and that matters: an INNER join here would silently
        // drop every subscription whose bank name never matched -- which is
        // exactly the population the known silent-drop defect creates. The
        // listing would look complete and be short.
        this.save(1L, null);

        assertThat(this.subscriptionsRepository.findAllWithBank()).hasSize(1);
    }

    @Test
    @DisplayName("findByIdWithBank returns the one row, and nothing for a missing id")
    void findByIdWithBank() {
        Bank absa = this.bankRepository.save(BankFixture.named("Absa"));
        this.save(1L, absa);

        assertThat(this.subscriptionsRepository.findByIdWithBank(1L)).isPresent();
        assertThat(this.subscriptionsRepository.findByIdWithBank(99L)).isEmpty();
    }

    @Test
    @DisplayName("findByBankIdWithBank returns only that bank's subscriptions")
    void findByBankIdWithBank() {
        Bank absa = this.bankRepository.save(BankFixture.named("Absa"));
        Bank capitec = this.bankRepository.save(BankFixture.named("Capitec"));
        this.save(1L, absa);
        this.save(2L, capitec);

        assertThat(this.subscriptionsRepository
                .findByBankIdWithBank(absa.getId().longValue()))
                .hasSize(1);
    }

    @Test
    @DisplayName("findAllWithBank costs the same number of queries at 5 rows and at 50")
    void findAllWithBankDoesNotGrowWithTheData() {
        // The fetch join's whole purpose, asserted as a number rather than
        // assumed from the JPQL. Measured at two sizes because a single
        // measurement cannot tell a constant cost from a growing one.
        //
        // Measured 2026-09-04: constant across both sizes. If this reddens,
        // something removed the LEFT JOIN FETCH and every subscriptions listing
        // now issues one extra statement per row.
        QueryCount.assertConstantAcrossSizes(
                this.entityManagerFactory,
                rows -> this.seedTo(rows),
                () -> this.subscriptionsRepository.findAllWithBank()
                        .forEach(subscription -> {
                            // Touch the relation. Without this the lazy proxy is
                            // never resolved and a broken fetch join would look
                            // identical to a working one.
                            if (subscription.getBank() != null) {
                                subscription.getBank().getName();
                            }
                        }),
                5, 50);
    }

    /** Rebuilds the table with exactly {@code rows} subscriptions, all on one bank. */
    private void seedTo(int rows) {
        DatabaseReset.reset(this.jdbc);
        Bank bank = this.bankRepository.save(BankFixture.named("Absa"));
        for (int i = 1; i <= rows; i++) {
            this.save((long) i, bank);
        }
    }

    /**
     * Inserts a subscription with a chosen id, through SQL rather than
     * {@code repository.save()} -- {@link SubscriptionsFixture} says why, and owns
     * the column list so that this test and the controller round-trip lane cannot
     * disagree about what a valid subscription is.
     */
    private void save(Long id, Bank bank) {
        SubscriptionsFixture.insert(this.jdbc, id, bank);
    }
}
