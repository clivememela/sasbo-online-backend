package za.co.titandynamix.sasbo_online_backend.utils;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import za.co.titandynamix.sasbo_online_backend.config.SasboEnvironment;
import za.co.titandynamix.sasbo_online_backend.config.WorkplaceSwitch;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * The demonstration population's two locks, without a database: the production marker and the switch. Each is proved
 * by deletion -- delete the check and the seeder reads the database, which the mocked template records -- and each has
 * its must-pass twin in {@code DemonstrationPopulationTest}, where the same seeder at {@code all} puts the file on
 * record.
 */
@DisplayName("The demonstration population's locks")
class DemonstrationPopulationSeederTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-14T07:30:00Z"), ZoneOffset.UTC);

    private final NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
    private final PlatformTransactionManager transactions = mock(PlatformTransactionManager.class);

    private DemonstrationPopulationSeeder seeder(String environment, String workplaces) {
        return new DemonstrationPopulationSeeder(jdbc, transactions, new SasboEnvironment(environment),
                new WorkplaceSwitch(workplaces), CLOCK);
    }

    @Test
    @DisplayName("🔒 SD3-A: where the production marker is set nothing is seeded -- nothing is even read -- whatever the switch says")
    void neverInProduction() throws Exception {
        assertThat(seeder("production", "all").seed()).isZero();
        assertThat(seeder("PRODUCTION ", "all").seed()).isZero();
        verifyNoInteractions(jdbc, transactions);
    }

    @Test
    @DisplayName("🔒 MD53: below 'all' nothing is seeded -- the members stand at SASBO's workplaces and are made from their staff numbers")
    void onlyWhereTheSwitchSaysAll() throws Exception {
        assertThat(seeder("", "").seed()).isZero();
        assertThat(seeder("", "none").seed()).isZero();
        assertThat(seeder("", "names").seed()).isZero();
        verifyNoInteractions(jdbc, transactions);
    }

    @Test
    @DisplayName("a joiner is dated by the clock -- the month it names before the clock's, on its day; everyone else on the date the file states")
    void aJoinerIsDatedByTheClock() throws Exception {
        DemonstrationPopulation file = DemonstrationPopulationSeeder.readSeedFile();
        YearMonth october = YearMonth.of(2026, 10);
        DemonstrationPopulation.Member joiner = file.members().stream()
                .filter(DemonstrationPopulation.Member::joinsInThePast).findFirst().orElseThrow();
        DemonstrationPopulation.Member standing = file.members().stream()
                .filter(m -> !m.joinsInThePast()).findFirst().orElseThrow();

        assertThat(DemonstrationPopulationSeeder.joinDate(joiner, october))
                .isEqualTo(october.minusMonths(joiner.joinedMonthsBack()).atDay(joiner.joinDay()))
                .isBefore(LocalDate.of(2026, 10, 1));
        assertThat(DemonstrationPopulationSeeder.joinDate(standing, october)).isEqualTo(standing.joinDate());
    }
}
