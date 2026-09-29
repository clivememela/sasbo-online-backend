package za.co.titandynamix.sasbo_online_backend.utils;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import za.co.titandynamix.sasbo_online_backend.dto.reference.ReferenceStatusDto;
import za.co.titandynamix.sasbo_online_backend.dto.workplace.WorkplacePageDto;
import za.co.titandynamix.sasbo_online_backend.services.ReferenceListService;
import za.co.titandynamix.sasbo_online_backend.services.WorkplaceService;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * What the production marker and the switch keep off a stack (month end 01.1, 01.2; MD28, MD45, MD50, MD62). Each
 * nested class is a whole start on a database of its own.
 */
@DisplayName("Month end's seed, under the marker and the switch")
class MonthEndSeedMarkerTest {

    private static long rows(JdbcTemplate jdbc, String sql) {
        Long n = jdbc.queryForObject(sql, Long.class);
        return n == null ? 0 : n;
    }

    @Nested
    @SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = {
            "sasbo.environment=production",
            "sasbo.workplaces=all",
            "spring.datasource.url=jdbc:h2:mem:month-end-production;MODE=PostgreSQL;DB_CLOSE_DELAY=-1"})
    @Import(SeedStartupTest.MigrationRowsAndAFrozenClock.class)
    @DisplayName("🔒 where the production marker is set -- even with the switch at all")
    class WithTheProductionMarker {

        @Autowired
        private JdbcTemplate jdbc;

        @Autowired
        private ReferenceListSeeder lists;

        @Autowired
        private WorkplaceSeeder workplaces;

        @Autowired
        private ReferenceListService status;

        @Test
        @DisplayName("🔒 MD28: nothing read off SASBO's reports is loaded as their truth before they confirm it -- on a second start neither")
        void nothingOfSasbosIsLoaded() {
            lists.seed();
            workplaces.seed();

            for (String table : new String[] {"sasbo_regions", "sasbo_areas", "employer_groups", "employers",
                    "table_codes", "committee_provinces", "committees", "suspension_codes", "workplaces"}) {
                assertThat(rows(jdbc, "SELECT count(*) FROM " + table)).as(table).isZero();
            }
            assertThat(rows(jdbc, "SELECT count(*) FROM deletion_codes WHERE sasbo_code IS NOT NULL")).isZero();
            assertThat(rows(jdbc, "SELECT count(*) FROM reference_lists")).as("nothing is even recorded").isZero();
        }

        @Test
        @DisplayName("ME-19: the status read says each list waits for SASBO to confirm it")
        void theStatusSaysWhy() {
            ReferenceStatusDto answer = status.status();

            assertThat(answer.lists()).allSatisfy(list -> assertThat(list.reason()).isEqualTo("NOT_CONFIRMED"));
            assertThat(answer.workplaces().reason()).isEqualTo("NOT_CONFIRMED");
        }
    }

    @Nested
    @SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, properties =
            "spring.datasource.url=jdbc:h2:mem:month-end-switch-off;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
    @Import(SeedStartupTest.MigrationRowsAndAFrozenClock.class)
    @DisplayName("🔒 in a demo with the switch unset -- which is a hosted stack until SASBO answers MM79")
    class WithTheSwitchOff {

        @Autowired
        private JdbcTemplate jdbc;

        @Autowired
        private WorkplaceService workplaceRoute;

        @Autowired
        private ReferenceListService status;

        @Test
        @DisplayName("🔒 MD45, MD50: SASBO's lists are there and no workplace is -- off unless it is set")
        void theListsAndNoWorkplace() {
            assertThat(rows(jdbc, "SELECT count(*) FROM table_codes")).isPositive();
            assertThat(rows(jdbc, "SELECT count(*) FROM workplaces")).isZero();
        }

        @Test
        @DisplayName("the workplaces' route and the status read say the workplaces are not loaded here")
        void theRouteSaysWhy() {
            WorkplacePageDto page = workplaceRoute.page(0, 50, null, null, null, null, null);

            assertThat(page.content()).isEmpty();
            assertThat(page.reason()).isEqualTo("NOT_LOADED");
            assertThat(status.status().workplaces().reason()).isEqualTo("NOT_LOADED");
            assertThat(status.status().workplacesServed()).isEqualTo("NONE");
        }
    }

    @Nested
    @SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = {
            "sasbo.workplaces=names",
            "spring.datasource.url=jdbc:h2:mem:month-end-switch-names;MODE=PostgreSQL;DB_CLOSE_DELAY=-1"})
    @Import(SeedStartupTest.MigrationRowsAndAFrozenClock.class)
    @DisplayName("🔒 with the switch at names -- SASBO's third possible answer, yes without the staff numbers")
    class WithTheSwitchAtNames {

        @Autowired
        private WorkplaceService workplaceRoute;

        @Test
        @DisplayName("🔒 MR-SEC-19: the workplaces are answered by name, and no staff complement leaves the server")
        void byNameWithoutTheComplement() {
            WorkplacePageDto page = workplaceRoute.page(0, 200, null, null, null, null, null);

            assertThat(page.content()).isNotEmpty();
            assertThat(page.content()).allSatisfy(w -> assertThat(w.staffComplement()).isNull());
        }
    }
}
