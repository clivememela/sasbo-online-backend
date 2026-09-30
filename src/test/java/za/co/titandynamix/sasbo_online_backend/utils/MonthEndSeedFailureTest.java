package za.co.titandynamix.sasbo_online_backend.utils;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import za.co.titandynamix.sasbo_online_backend.repository.MemberRepository;
import za.co.titandynamix.sasbo_online_backend.services.ReferenceListService;

import java.io.IOException;
import java.io.InputStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * MV-58, MD65: a seeder of month end's block that throws, through a whole start with the switch at {@code all} -- the
 * lists' seeder, the first of the block, made to throw. The start goes on: what is seeded before the block (the
 * seed's own members and the 150 on debit order) and after it (ACB's history) is there, and the status read says the
 * lists are not loaded here rather than answering an empty list as if SASBO had none. A context of its own, on a
 * database of its own.
 *
 * <p>The block's catch is proved by deletion in {@link DataSeederBlockTest} (DL-70): deleted, this context would not
 * start at all.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = {
        "sasbo.workplaces=all",
        "spring.datasource.url=jdbc:h2:mem:month-end-seed-throws;MODE=PostgreSQL;DB_CLOSE_DELAY=-1"})
@Import({SeedStartupTest.MigrationRowsAndAFrozenClock.class, MonthEndSeedFailureTest.TheListsSeederThrows.class})
@DisplayName("Month end's block, with the lists' seeder made to throw")
class MonthEndSeedFailureTest {

    @TestConfiguration
    static class TheListsSeederThrows {
        /** The application's own lists' seeder, answering every call as it does -- but seed(), which throws. */
        @Bean
        static BeanPostProcessor theListsSeederThrows() {
            return new BeanPostProcessor() {
                @Override
                public Object postProcessAfterInitialization(Object bean, String name) {
                    if (bean instanceof ReferenceListSeeder lists) {
                        ReferenceListSeeder planted = Mockito.spy(lists);
                        Mockito.doThrow(new IllegalStateException("planted: the lists' seeder throws (MV-58)"))
                                .when(planted).seed();
                        return planted;
                    }
                    return bean;
                }
            };
        }
    }

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private MemberRepository members;

    @Autowired
    private ReferenceListService lists;

    private long rows(String sql) {
        Long n = jdbc.queryForObject(sql, Long.class);
        return n == null ? 0 : n;
    }

    @Test
    @DisplayName("🔒 MV-58: the start goes on -- the seed's members and the 150 before the block, ACB's history after it")
    void theStartGoesOn() throws IOException {
        long seedMembers;
        try (InputStream stream = new ClassPathResource("data/members.json").getInputStream()) {
            seedMembers = DataSeeder.readSeedMembers(stream).size();
        }
        long debitOrder = DebitOrderMemberSeeder.readSeedFile().members().size();

        assertThat(members.count()).as("the seed's members and the 150 on debit order, seeded before the block")
                .isGreaterThanOrEqualTo(seedMembers + debitOrder);
        assertThat(rows("SELECT count(*) FROM acb_run_lines")).as("ACB's history, seeded after the block").isPositive();
    }

    @Test
    @DisplayName("MV-58: the status read says the lists are not loaded here -- never an empty list said as SASBO's")
    void theStatusSaysNotLoaded() {
        // The workplaces' seeder, which runs after it, records its own outcome beside the lists'.
        assertThat(rows("SELECT count(*) FROM reference_lists WHERE list_key <> 'workplaces'"))
                .as("the lists' seeder threw before it recorded anything").isZero();
        assertThat(lists.status().lists()).filteredOn(list -> list.rows() == 0).isNotEmpty()
                .allSatisfy(list -> assertThat(list.reason()).as(list.list()).isEqualTo("NOT_LOADED"));
    }
}
