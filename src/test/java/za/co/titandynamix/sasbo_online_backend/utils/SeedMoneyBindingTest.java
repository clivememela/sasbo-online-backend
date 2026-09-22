package za.co.titandynamix.sasbo_online_backend.utils;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import za.co.titandynamix.sasbo_online_backend.entity.member.Member;

import java.io.InputStream;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.SoftAssertions.assertSoftly;

/**
 * The seed's money reaches the member's {@code BigDecimal} fields through the A61 reader.
 *
 * <h2>Why this exists -- an obligation between two branches</h2>
 * The ACB arc's V40 moved the four money fields to {@code BigDecimal} (mapped to the numeric
 * twins); the capture arc's A61 fix replaced the seeder's JSON reader with one that relaxes
 * {@code READ_ONLY} for the membership number only. The two were built apart, and each should have
 * left the other's behaviour alone -- SHOULD, not does. They merged on 2026-09-18, A61 first and
 * ACB second, and the branch that landed second owed this check against the merged tree.
 *
 * <p>This is the parse half: every one of the 640 money values in the seed file arrives as the
 * number the file says. {@code SeedMoneyIT} is the persistence half.
 */
@DisplayName("The seed's money, read by the A61 reader into BigDecimal")
class SeedMoneyBindingTest {

    private static final Map<String, Function<Member, BigDecimal>> MONEY = Map.of(
            "monthlySubscription", Member::getMonthlySubscription,
            "currentBalance", Member::getCurrentBalance,
            "arrearsBalance", Member::getArrearsBalance,
            "lastMonthBalance", Member::getLastMonthBalance);

    @Test
    @DisplayName("every money value in the seed file arrives as the number the file says")
    void everyValueBinds() throws Exception {
        List<Member> members;
        try (InputStream stream = resource()) {
            members = DataSeeder.readSeedMembers(stream);
        }
        List<Map<String, Object>> raw;
        try (InputStream stream = resource()) {
            raw = new ObjectMapper().readValue(stream, new TypeReference<>() { });
        }
        assertThat(members).hasSameSizeAs(raw).hasSize(160);

        int[] compared = {0};
        assertSoftly(softly -> {
            for (int i = 0; i < members.size(); i++) {
                for (Map.Entry<String, Function<Member, BigDecimal>> field : MONEY.entrySet()) {
                    Object inFile = raw.get(i).get(field.getKey());
                    if (inFile == null) {
                        continue;
                    }
                    BigDecimal read = field.getValue().apply(members.get(i));
                    softly.assertThat(read).as("member %d %s", i, field.getKey()).isNotNull();
                    if (read != null) {
                        softly.assertThat(read).as("member %d %s", i, field.getKey())
                                .isEqualByComparingTo(new BigDecimal(String.valueOf(inFile)));
                    }
                    compared[0]++;
                }
            }
        });
        // The planning pack counts 640 seeded money values; a reader that bound nothing would
        // compare nothing and pass, so the comparison count is asserted too.
        assertThat(compared[0]).isEqualTo(640);
    }

    private static InputStream resource() {
        InputStream stream = SeedMoneyBindingTest.class.getClassLoader().getResourceAsStream("data/members.json");
        assertThat(stream).as("data/members.json on the test classpath").isNotNull();
        return stream;
    }
}
