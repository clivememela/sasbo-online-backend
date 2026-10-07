package za.co.titandynamix.sasbo_online_backend.utils;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import za.co.titandynamix.sasbo_online_backend.config.SasboEnvironment;

import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * The seed's members on SASBO's recruitment channels (RC15): no channel at 0 on a fresh stack, never under the
 * production marker, and the same channel for the same member on every start.
 */
@DisplayName("RecruitmentChannelSeeder")
class RecruitmentChannelSeederTest {

    @Test
    @DisplayName("🔒 under the production marker it does nothing -- not even a read")
    void neverUnderTheProductionMarker() {
        NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
        SasboEnvironment production = new SasboEnvironment(SasboEnvironment.PRODUCTION);

        int given = new RecruitmentChannelSeeder(jdbc, new SeedMemberNumbers(), production).seed();

        assertThat(given).isZero();
        verifyNoInteractions(jdbc);
    }

    @Test
    @DisplayName("every one of the six channels is given some of the seed's members -- none reads 0 -- and unequally")
    void everyChannelIsGivenMembers() {
        Map<String, Long> perChannel = new SeedMemberNumbers().all().stream()
                .collect(Collectors.groupingBy(RecruitmentChannelSeeder::channelFor, TreeMap::new,
                        Collectors.counting()));

        assertThat(perChannel.keySet()).containsExactlyInAnyOrder("BEC", "Website", "EORO", "RO", "MSO", "Youth");
        assertThat(perChannel.values()).allSatisfy(count -> assertThat(count).isPositive());
        assertThat(perChannel.get("BEC")).as("the shares are unequal by design").isGreaterThan(perChannel.get("Youth"));
    }

    @Test
    @DisplayName("the channel follows from the membership number: the same member, the same channel, every start")
    void theSameMemberTheSameChannel() {
        Map<String, String> first = new SeedMemberNumbers().all().stream()
                .collect(Collectors.toMap(Function.identity(), RecruitmentChannelSeeder::channelFor));
        Map<String, String> again = new SeedMemberNumbers().all().stream()
                .collect(Collectors.toMap(Function.identity(), RecruitmentChannelSeeder::channelFor));

        assertThat(again).isEqualTo(first);
        assertThat(RecruitmentChannelSeeder.channelFor("M-099850")).isEqualTo(RecruitmentChannelSeeder.channelFor("M-099850"));
    }

    @Test
    @DisplayName("the shares add up to the whole")
    void theSharesAddUp() {
        assertThat(RecruitmentChannelSeeder.SHARES.stream().mapToInt(RecruitmentChannelSeeder.Share::parts).sum())
                .isEqualTo(RecruitmentChannelSeeder.OUT_OF);
    }
}
