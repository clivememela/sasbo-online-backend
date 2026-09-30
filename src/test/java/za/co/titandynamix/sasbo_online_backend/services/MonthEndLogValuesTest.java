package za.co.titandynamix.sasbo_online_backend.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import za.co.titandynamix.sasbo_online_backend.acb.AcbTime;
import za.co.titandynamix.sasbo_online_backend.dto.member.MemberStatusRequest;
import za.co.titandynamix.sasbo_online_backend.entity.member.Member;
import za.co.titandynamix.sasbo_online_backend.fixtures.MemberFixture;
import za.co.titandynamix.sasbo_online_backend.repository.MemberRepository;
import za.co.titandynamix.sasbo_online_backend.support.LogCapture;
import za.co.titandynamix.sasbo_online_backend.support.SummaryFixture;
import za.co.titandynamix.sasbo_online_backend.utils.DataSeeder;

import java.time.Clock;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * MV-57, MD68: no log line of this arc carries a value. Under a capture of the whole application package at DEBUG and
 * finer -- the level every host runs it at (MR-SEC-20) -- a save that names a workplace, a status change with a note,
 * a history read and a search are each given values planted that occur nowhere else: a member's surname and staff
 * number, a workplace's name, a note, a search term. None of them is in what was captured. {@code MonthEndGuardsTest}
 * holds the same rule over the source (MD68); this holds it over what runs.
 *
 * <p>@Isolated: it captures a Logback logger, and a context started beside it resets Logback (see SeedStartupTest).
 */
@Isolated
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = {
        "sasbo.workplaces=all",
        "spring.datasource.url=jdbc:h2:mem:month-end-log-values;MODE=PostgreSQL;DB_CLOSE_DELAY=-1"})
@Import(MonthEndLogValuesTest.AtTheFixturesClock.class)
@DisplayName("Month end's log lines carry no value")
class MonthEndLogValuesTest {

    /** Every value planted begins so, and nothing else in the application or its fixtures does. */
    static final String PLANTED = "QXPLANTED";

    /** The application's own package: everything under it is captured. */
    static final String PACKAGE = "za.co.titandynamix.sasbo_online_backend";

    @TestConfiguration
    static class AtTheFixturesClock {
        @Bean
        @Primary
        Clock theFixturesClock() {
            return Clock.fixed(SummaryFixture.NOW, AcbTime.ZONE);
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
    private MemberService memberService;

    @Autowired
    private MemberStatusService statusService;

    @Autowired
    private MemberHistoryService history;

    @Autowired
    private WorkplaceService workplaces;

    @BeforeEach
    void thePopulation() {
        Long present = jdbc.queryForObject("SELECT count(*) FROM workplaces WHERE ra_number = 900001", Long.class);
        if (present == null || present == 0) {
            SummaryFixture.insert(jdbc, members);
        }
    }

    @Test
    @DisplayName("🔒 MV-57: a save naming a workplace, a status change with a note, a history read and a search -- no value planted in any of them is logged")
    void noValueIsLogged() {
        jdbc.update("UPDATE workplaces SET name = ? WHERE ra_number = 900002", PLANTED + " WORKPLACE");
        Long workplace = jdbc.queryForObject("SELECT id FROM workplaces WHERE ra_number = 900002", Long.class);

        try (LogCapture log = LogCapture.under(PACKAGE)) {
            // The capture's save, as MemberWorkplaceIT makes one: a whole member, naming a workplace.
            Member member = MemberFixture.withSurname(PLANTED + "SURNAME");
            member.setStatus("Active");
            member.setEmployeeNumber(PLANTED + "STAFF");
            member.setWorkplaceId(workplace);
            Integer id = memberService.create(member).getId();

            statusService.change(id, new MemberStatusRequest("Resigned", "Active", "DELETION_CODE", 2, null,
                    PLANTED + " NOTE", null));

            assertThat(history.page(id, 0, 20).content()).as("the history read, which holds the values").isNotEmpty();

            workplaces.page(0, 20, null, null, null, null, PLANTED + " SEARCH");

            assertThat(log.rendered()).as("what the application logged at DEBUG and finer").doesNotContain(PLANTED);
        }
    }

    @Test
    @DisplayName("canary: a line under the package that does carry a planted value is found")
    void aLineWithAValueIsFound() {
        try (LogCapture log = LogCapture.under(PACKAGE)) {
            org.slf4j.LoggerFactory.getLogger(MonthEndLogValuesTest.class).debug("a line with {}", PLANTED + " VALUE");
            assertThat(log.rendered()).contains(PLANTED);
        }
    }
}
