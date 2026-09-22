package za.co.titandynamix.sasbo_online_backend.utils;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import za.co.titandynamix.sasbo_online_backend.entity.member.Member;
import za.co.titandynamix.sasbo_online_backend.repository.MemberRepository;
import za.co.titandynamix.sasbo_online_backend.support.AbstractWritingIT;

import java.io.InputStream;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A seeded member, saved the way the seeder saves it, reads back with its subscription in the
 * NUMERIC column -- on the merged tree, where A61's reader and V40's twins meet for the first time.
 *
 * <p>The persistence half of {@code SeedMoneyBindingTest}: the reader binds the value; this proves
 * the entity writes it to {@code monthly_subscription_numeric}, and that V40's keep-in-step trigger
 * copies it to the text column an older build still reads.
 */
@DisplayName("A seeded member's money, written and read back on PostgreSQL")
class SeedMoneyIT extends AbstractWritingIT {

    @Autowired
    private MemberRepository members;

    @Test
    @DisplayName("the seeded subscription lands in the numeric twin -- the text twin is gone since V52 (00.2 step two)")
    void theSeededSubscriptionIsNumeric() throws Exception {
        List<Member> seeded;
        try (InputStream stream = getClass().getClassLoader().getResourceAsStream("data/members.json")) {
            seeded = DataSeeder.readSeedMembers(stream);
        }
        Member first = seeded.get(0);
        BigDecimal expected = first.getMonthlySubscription();
        assertThat(expected).as("the seed file's first member has a subscription to check").isNotNull();

        // What DataSeeder.run does to each member before saveAll.
        first.setId(null);
        first.setJobGrade(null);
        first.setPreferredEmail("personal");
        members.saveAndFlush(first);

        Map<String, Object> row = jdbc.queryForMap(
                "SELECT monthly_subscription_numeric FROM members WHERE membership_number = ?",
                first.getMembershipNumber());
        assertThat((BigDecimal) row.get("monthly_subscription_numeric")).isEqualByComparingTo(expected);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM information_schema.columns "
                + "WHERE table_name = 'members' AND column_name = 'monthly_subscription'", Integer.class))
                .as("V52 dropped the text twin, which only an old build ever read").isZero();
    }
}
