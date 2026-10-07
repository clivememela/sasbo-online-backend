package za.co.titandynamix.sasbo_online_backend.utils;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import za.co.titandynamix.sasbo_online_backend.config.SasboEnvironment;
import za.co.titandynamix.sasbo_online_backend.dto.migration.MigrationBatchDto;
import za.co.titandynamix.sasbo_online_backend.dto.migration.MigrationReconciliationDto;
import za.co.titandynamix.sasbo_online_backend.repository.MemberRepository;
import za.co.titandynamix.sasbo_online_backend.repository.MigrationBatchRepository;
import za.co.titandynamix.sasbo_online_backend.repository.MigrationRowRepository;
import za.co.titandynamix.sasbo_online_backend.services.MigrationBatches;
import za.co.titandynamix.sasbo_online_backend.services.MigrationReconciliations;
import za.co.titandynamix.sasbo_online_backend.services.MigrationValidation;
import za.co.titandynamix.sasbo_online_backend.support.AbstractSwitchedOnIT;
import za.co.titandynamix.sasbo_online_backend.support.MigrationFixture;

import java.time.Clock;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The data migration's demonstration, on PostgreSQL: seeded when absent -- an invented export registered, checked and
 * set beside invented totals for the month before the clock's -- with rows of every outcome and a reconciliation with
 * no zeros; a second start seeds nothing; under the production marker nothing at all; and no member is moved.
 */
@DisplayName("The data migration's demonstration seed, on PostgreSQL")
class MigrationDemoSeederIT extends AbstractSwitchedOnIT {

    @Autowired
    private MigrationDemoSeeder seeder;

    @Autowired
    private MigrationBatches batches;

    @Autowired
    private MigrationValidation validation;

    @Autowired
    private MigrationReconciliations reconciliations;

    @Autowired
    private MigrationBatchRepository batchRepository;

    @Autowired
    private MigrationRowRepository rows;

    @Autowired
    private ReferenceListSeeder lists;

    @Autowired
    private MemberRepository members;

    @Autowired
    private Clock clock;

    @BeforeEach
    void theLists() {
        MigrationFixture.prepare(jdbc, lists, members);
    }

    @Test
    @DisplayName("seeded when absent: 24 invented rows of every outcome, checked, beside its month's totals -- and nobody moved")
    void seeds() {
        long membersBefore = members.count();

        Long id = seeder.seed();

        assertThat(id).isNotNull();
        MigrationBatchDto batch = batches.find(id);
        assertThat(batch.status()).isEqualTo("VALIDATED");
        assertThat(batch.asAt()).as("the month before the clock's, a past period").isEqualTo("2026-08-31");
        assertThat(batch.rowsRead()).isEqualTo(24);
        assertThat(batch.rowsValid()).isPositive();
        assertThat(batch.rowsWarned()).isPositive();
        assertThat(batch.rowsInvalid()).isPositive();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM migration_rows WHERE batch_id = ? AND workplace_id IS NOT NULL",
                Long.class, id)).as("the R/A numbers of workplaces on record in areas L and W").isPositive();
        assertThat(members.count()).as("nothing moved into the register").isEqualTo(membersBefore);

        MigrationReconciliationDto reconciled = reconciliations.reconcile(id);
        assertThat(reconciled.figuresEntered()).isGreaterThan(5);
        assertThat(reconciled.lines()).filteredOn(line -> line.dimension().equals("TOTAL"))
                .allSatisfy(line -> assertThat(line.legacy()).isNotNull());
        assertThat(reconciled.lines()).filteredOn(line -> line.status().equals("DIFFERENT"))
                .allSatisfy(line -> assertThat(-line.difference()).as("each difference is the rows held back")
                        .isEqualTo(line.heldBack()));
        assertThat(reconciled.lines()).anySatisfy(line -> assertThat(line.status()).isEqualTo("MATCH"));
        assertThat(reconciled.lines()).filteredOn(line -> line.measure().equals("JOINED") || line.measure().equals("LEFT"))
                .allSatisfy(line -> assertThat(line.batch()).as("joiners and leavers fall in the month").isPositive());
    }

    @Test
    @DisplayName("a second start seeds nothing: it is absent no longer")
    void once() {
        assertThat(seeder.seed()).isNotNull();

        assertThat(seeder.seed()).isNull();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM migration_batches", Long.class)).isEqualTo(1);
    }

    @Test
    @DisplayName("🔒 never under the production marker")
    void notInProduction() {
        MigrationDemoSeeder production = new MigrationDemoSeeder(batches, validation, reconciliations, batchRepository,
                rows, jdbc, new SasboEnvironment("production"), clock);

        assertThat(production.seed()).isNull();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM migration_batches", Long.class)).isZero();
    }
}
