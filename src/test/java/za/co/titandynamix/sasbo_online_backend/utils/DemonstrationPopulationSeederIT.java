package za.co.titandynamix.sasbo_online_backend.utils;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import za.co.titandynamix.sasbo_online_backend.dto.summary.MemberSummaryDto;
import za.co.titandynamix.sasbo_online_backend.services.MemberSummaryService;
import za.co.titandynamix.sasbo_online_backend.support.AbstractSwitchedOnIT;
import za.co.titandynamix.sasbo_online_backend.support.SummaryFixture;

import java.io.IOException;
import java.time.YearMonth;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The demonstration population on PostgreSQL (the October build, demo-data): its SQL as the real database reads it --
 * the batch, the casts, the constraints V50, V56 and V57 hold -- and how long it takes. The seed runs inside the
 * stack's 180-second health budget (readiness holds until every runner returns), which on the hosted service already
 * spends about 90 seconds on SASBO's lists, workplaces and visits; so the population's own time is measured here, where
 * nothing else is writing, and bounded well below what the budget leaves.
 *
 * <p>The clock is the switched-on lane's, the middle of September 2026: the population's past falls in June, July and
 * August, and the figures of those months are worked out live by the statement the screens use, within its own bound.
 */
@DisplayName("The demonstration population on PostgreSQL")
class DemonstrationPopulationSeederIT extends AbstractSwitchedOnIT {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(DemonstrationPopulationSeederIT.class);

    /**
     * The population's own share of the start. Measured on the runner's PostgreSQL at about a second; the bound leaves
     * room for a slower runner and none for a seeder that writes a round trip a member, which takes tens of seconds.
     */
    static final long BUDGET_MILLIS = 15_000;

    @Autowired
    private ReferenceListSeeder lists;

    @Autowired
    private WorkplaceSeeder workplaces;

    @Autowired
    private DemonstrationPopulationSeeder population;

    @Autowired
    private MemberSummaryService summary;

    @BeforeEach
    void theWorkplacesTheyStandAt() {
        lists.seed();
        workplaces.seed();
    }

    @Test
    @DisplayName("🔒 the 180-second health budget: the population is on record, its past written, in well under its share -- and a second start writes nothing")
    void seedsWithinItsBudget() throws IOException {
        DemonstrationPopulation file = DemonstrationPopulationSeeder.readSeedFile();

        long started = System.nanoTime();
        int wrote = population.seed();
        long millis = (System.nanoTime() - started) / 1_000_000;
        log.info("The demonstration population seeded on PostgreSQL in {} ms ({} rows)", millis, wrote);

        assertThat(millis).as("the population's share of the start, in ms").isLessThan(BUDGET_MILLIS);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM members WHERE membership_number BETWEEN 'M-096001' "
                + "AND 'M-098500' AND workplace_id IS NOT NULL", Long.class)).isEqualTo(file.count());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM member_status_changes c JOIN members m ON m.id = c.member_id "
                + "WHERE c.changed_by = 'seed' AND m.membership_number BETWEEN 'M-096001' AND 'M-098500'", Long.class))
                .isEqualTo(file.past().size());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM suspension_codes WHERE code IN (12, 15, 16, 18, 21, 30, 31, "
                + "32, 33)", Long.class)).as("Stephne's codes the August printouts lack").isEqualTo(9);
        assertThat(population.seed()).as("a second start").isZero();
    }

    @Test
    @DisplayName("the figures of each past month, worked out live on PostgreSQL within their bound, have joiners, leavers and the suspended")
    void thePastMonthsFiguresMove() throws IOException {
        population.seed();
        YearMonth open = YearMonth.from(SummaryFixture.NOW.atZone(MonthRange.ZONE));
        for (int back = 1; back <= MonthEndTakeSeeder.MONTHS; back++) {
            YearMonth month = open.minusMonths(back);
            MemberSummaryDto figures = summary.summary(month.toString(), "group", null, null);
            assertThat(figures.totals().joined()).as("%s joined", month).isPositive();
            assertThat(figures.totals().left()).as("%s left", month).isPositive();
            assertThat(figures.totals().suspended()).as("%s suspended", month).isPositive();
            assertThat(figures.rows()).as("%s: no group hidden by rule C", month)
                    .noneMatch(row -> row.fewerThanFive() || row.withheld());
        }
    }
}
