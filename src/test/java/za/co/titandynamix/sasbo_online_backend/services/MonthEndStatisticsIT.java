package za.co.titandynamix.sasbo_online_backend.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import za.co.titandynamix.sasbo_online_backend.dto.monthend.MonthEndStatisticDto;
import za.co.titandynamix.sasbo_online_backend.repository.MemberRepository;
import za.co.titandynamix.sasbo_online_backend.support.AbstractSwitchedOnIT;
import za.co.titandynamix.sasbo_online_backend.support.StatisticsExpectations;
import za.co.titandynamix.sasbo_online_backend.support.SummaryFixture;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The statistics of a month, on PostgreSQL (month end 04.3 to 04.8): {@link SummaryFixture}'s September, open and
 * taken, held to the very table H2 is held to (MV-49, BR65) -- the open month by the statement a take freezes, the
 * taken one from what was frozen.
 */
@DisplayName("The statistics of a month, on PostgreSQL")
class MonthEndStatisticsIT extends AbstractSwitchedOnIT {

    @Autowired
    private MemberRepository members;

    @Autowired
    private MonthEndStatistics statistics;

    @Autowired
    private MonthEndTakes takes;

    @BeforeEach
    void thePopulation() {
        SummaryFixture.insert(jdbc, members);
    }

    @Test
    @DisplayName("🔒 MV-49: every statistic of the open September -- what H2 answers")
    void theOpenMonth() {
        StatisticsExpectations.assertNewAndDeleted(statistics.newAndDeleted("2026-09", null, null));
        StatisticsExpectations.assertMembership(statistics.membership("2026-09", null, null));
        StatisticsExpectations.assertLeavers(statistics.leavers("2026-09", null, null));
        StatisticsExpectations.assertBranches(statistics.branches("2026-09", null, null));
        StatisticsExpectations.assertRecruitment(statistics.recruitment("2026-09", null, null), 2);
        StatisticsExpectations.assertRepresentatives(statistics.representatives("2026-09", null, null));
        StatisticsExpectations.assertOneFigure(statistics.all("2026-09"));
    }

    @Test
    @DisplayName("🔒 MV-49, MD19: every statistic of September taken -- what H2 answers, read from what was frozen")
    void theTakenMonth() {
        MonthEndStatisticDto open = statistics.leavers("2026-09", null, null);
        takes.take(SummaryFixture.MONTH, "seed");

        MonthEndStatisticDto taken = statistics.leavers("2026-09", null, null);
        assertThat(taken.state()).isEqualTo("TAKEN");
        assertThat(taken.sheets()).isEqualTo(open.sheets());
        StatisticsExpectations.assertNewAndDeleted(statistics.newAndDeleted("2026-09", null, null));
        StatisticsExpectations.assertMembership(statistics.membership("2026-09", null, null));
        StatisticsExpectations.assertBranches(statistics.branches("2026-09", null, null));
        StatisticsExpectations.assertRecruitment(statistics.recruitment("2026-09", null, null), 2);
        StatisticsExpectations.assertRepresentatives(statistics.representatives("2026-09", null, null));
        StatisticsExpectations.assertOneFigure(statistics.all("2026-09"));
    }
}
