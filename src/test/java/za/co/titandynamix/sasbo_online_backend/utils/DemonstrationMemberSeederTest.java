package za.co.titandynamix.sasbo_online_backend.utils;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import za.co.titandynamix.sasbo_online_backend.config.SasboEnvironment;
import za.co.titandynamix.sasbo_online_backend.config.WorkplaceSwitch;
import za.co.titandynamix.sasbo_online_backend.repository.MemberRepository;
import za.co.titandynamix.sasbo_online_backend.repository.SeedMemberRepository;
import za.co.titandynamix.sasbo_online_backend.repository.WorkplaceEntityRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Where the invented members may be (month end 01.4): never where the production marker is set, whatever the switch
 * says (SD3-A); and only where the switch says {@code all} -- made from the staff numbers, they stand at workplaces
 * (MD53, MD62). Asked BEFORE anything is read: neither the database nor the members' table is touched.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("DemonstrationMemberSeeder -- where the invented members may be")
class DemonstrationMemberSeederTest {

    @Mock
    private SeedMemberRepository seedMembers;

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private WorkplaceEntityRepository workplaces;

    @Mock
    private NamedParameterJdbcTemplate jdbc;

    @Mock
    private PlatformTransactionManager transactions;

    @Mock
    private za.co.titandynamix.sasbo_online_backend.services.MemberService memberService;

    @Mock
    private za.co.titandynamix.sasbo_online_backend.repository.SasboReferenceRepository codes;

    private DemonstrationMemberSeeder at(String environment, String workplaceSwitch) {
        return new DemonstrationMemberSeeder(seedMembers, memberRepository, workplaces, jdbc, transactions,
                new SasboEnvironment(environment), new WorkplaceSwitch(workplaceSwitch), memberService, codes,
                java.time.Clock.systemUTC());
    }

    @Test
    @DisplayName("🔒 SD3-A: where the production marker is set, none is seeded and none is placed -- even with the switch at all")
    void neverInProduction() throws Exception {
        DemonstrationMemberSeeder seeder = at("production", "all");

        assertThat(seeder.seed()).isZero();
        assertThat(seeder.place()).isZero();
        assertThat(seeder.past()).isZero();
        verifyNoInteractions(seedMembers, memberRepository, workplaces, jdbc, transactions, memberService, codes);
    }

    @ParameterizedTest(name = "switch \"{0}\"")
    @ValueSource(strings = {"", "none", "names"})
    @DisplayName("🔒 MD53, MD62: anywhere the switch is not at all, none is seeded and none is placed")
    void onlyWhereTheSwitchSaysAll(String workplaceSwitch) throws Exception {
        DemonstrationMemberSeeder seeder = at(null, workplaceSwitch);

        assertThat(seeder.seed()).isZero();
        assertThat(seeder.place()).isZero();
        assertThat(seeder.past()).isZero();
        verifyNoInteractions(seedMembers, memberRepository, workplaces, jdbc, transactions, memberService, codes);
    }
}
