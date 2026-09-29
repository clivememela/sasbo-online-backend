package za.co.titandynamix.sasbo_online_backend.services.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.FieldSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import za.co.titandynamix.sasbo_online_backend.acb.AcbTime;
import za.co.titandynamix.sasbo_online_backend.dto.summary.MemberSummaryDto;
import za.co.titandynamix.sasbo_online_backend.exception.MonthEndRefusal;
import za.co.titandynamix.sasbo_online_backend.exception.MonthEndRefusalCode;
import za.co.titandynamix.sasbo_online_backend.repository.MemberRepository;
import za.co.titandynamix.sasbo_online_backend.services.MemberSummaryService;
import za.co.titandynamix.sasbo_online_backend.support.ConnectionStatements;
import za.co.titandynamix.sasbo_online_backend.support.SummaryFixture;
import za.co.titandynamix.sasbo_online_backend.utils.DataSeeder;

import java.time.Clock;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The figures of September 2026, on H2 (02.2): {@link SummaryFixture}'s one population and one expected answer --
 * the same the integration lane holds PostgreSQL to (MV-49) -- through the real service and its one statement.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = {
        "sasbo.workplaces=all",
        "spring.datasource.url=jdbc:h2:mem:member-summary;MODE=PostgreSQL;DB_CLOSE_DELAY=-1"})
@Import({MemberSummaryTest.AtTheFixturesClock.class, MemberSummaryTest.CountedAtTheConnection.class})
@DisplayName("The figures of a month, on H2")
class MemberSummaryTest {

    @TestConfiguration
    static class AtTheFixturesClock {
        @Bean
        @Primary
        Clock theFixturesClock() {
            return Clock.fixed(SummaryFixture.NOW, AcbTime.ZONE);
        }
    }

    @TestConfiguration
    static class CountedAtTheConnection {
        @Bean
        static BeanPostProcessor countStatementsAtTheConnection() {
            return ConnectionStatements.register();
        }
    }

    /** Nothing of the seed's: only the fixture is on this database. */
    @MockitoBean
    private DataSeeder noSeed;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private MemberRepository members;

    @Autowired
    private MemberSummaryService summaries;

    static final List<String> GROUPINGS = SummaryFixture.GROUPINGS;

    @BeforeEach
    void thePopulation() {
        Long present = jdbc.queryForObject("SELECT count(*) FROM workplaces WHERE ra_number = 900001", Long.class);
        if (present == null || present == 0) {
            SummaryFixture.insert(jdbc, members);
        }
    }

    @ParameterizedTest(name = "by {0}")
    @FieldSource("GROUPINGS")
    @DisplayName("every figure of every row, for each of the six groupings -- and the rows sum to the whole (MV-16)")
    void everyGrouping(String by) {
        SummaryFixture.assertAnswer(summaries.summary("2026-09", by, null, null), by);
    }

    @Test
    @DisplayName("🔒 MD55: a request that names no month is answered for the server's, and says so")
    void theServersMonth() {
        MemberSummaryDto answer = summaries.summary(null, null, null, null);

        assertThat(answer.month()).isEqualTo("2026-09");
        assertThat(answer.monthFromTheServer()).isTrue();
        assertThat(answer.by()).isEqualTo("group");
        assertThat(answer.groupedAsAt()).isEqualTo("2026-09-15");
        assertThat(summaries.summary("2026-09", null, null, null).monthFromTheServer()).isFalse();
    }

    @Test
    @DisplayName("🔒 MR-ROB-18, MV-24: a request is ONE statement, counted at the connection")
    void oneStatement() {
        assertThat(ConnectionStatements.during(() -> summaries.summary("2026-09", "employer", null, null)))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("an area and a region each sum, with the others and those with no workplace, to the whole")
    void areasAndRegionsSumToTheWhole() {
        long a = summaries.summary("2026-09", "group", "A", null).totals().onRecord();
        long b = summaries.summary("2026-09", "group", "b", null).totals().onRecord();
        assertThat(a).isEqualTo(12);
        assertThat(b).isEqualTo(3);
        assertThat(a + b + 2).as("the two with no workplace are in no area").isEqualTo(SummaryFixture.WHOLE.onRecord());
        assertThat(summaries.summary("2026-09", "area", null, "NORTH").totals().onRecord()).isEqualTo(a);
        assertThat(summaries.summary("2026-09", "area", null, "SOUTH").totals().onRecord()).isEqualTo(b);
        assertThat(summaries.summary("2026-09", "group", "A", null).rows())
                .extracting(MemberSummaryDto.Row::key).containsExactly("INVENTED GROUP ONE", "INVENTED GROUP TWO");
    }

    @Test
    @DisplayName("an area or a region not on record is SUMMARY_AREA_UNKNOWN -- the same statement says so")
    void anAreaNotOnRecord() {
        assertThatThrownBy(() -> summaries.summary("2026-09", "group", "QQ", null))
                .isInstanceOfSatisfying(MonthEndRefusal.class,
                        refusal -> assertThat(refusal.code()).isEqualTo(MonthEndRefusalCode.SUMMARY_AREA_UNKNOWN));
        assertThatThrownBy(() -> summaries.summary("2026-09", "group", null, "EAST"))
                .isInstanceOfSatisfying(MonthEndRefusal.class,
                        refusal -> assertThat(refusal.code()).isEqualTo(MonthEndRefusalCode.SUMMARY_AREA_UNKNOWN));
    }

    @Test
    @DisplayName("a past month is read as the members stood then: August has the leaver of August, and September's are still on record")
    void aPastMonth() {
        MemberSummaryDto august = summaries.summary("2026-08", "group", null, null);

        // At the end of August: m15 and m17's leaving is September's (m17 had not joined), m16 left in August, m9 was a
        // leaver since June. So on record: the 17 of September, less the two who joined in it (m8; m17 never was),
        // less m9 who was reinstated in it, plus m15 who left in it.
        assertThat(august.totals().onRecord()).isEqualTo(17 - 1 - 1 + 1);
        assertThat(august.totals().left()).as("m16, on 20 August").isEqualTo(1);
        assertThat(august.totals().joined()).isZero();
        assertThat(august.totals().unexplained()).isZero();
        assertThat(august.monthFromTheServer()).isFalse();
    }
}
