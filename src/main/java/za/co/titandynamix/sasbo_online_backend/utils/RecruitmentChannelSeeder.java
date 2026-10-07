package za.co.titandynamix.sasbo_online_backend.utils;

import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import za.co.titandynamix.sasbo_online_backend.config.SasboEnvironment;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The seed's members, each recorded as recruited through one of SASBO's recruitment channels (RC15).
 *
 * <h2>Why</h2>
 * The Recruiters screen counts each channel's members, and no seeded member had a channel -- so every row read 0 on a
 * fresh stack and on the demonstration link (the owner, 6 Oct: "good seed data that reflect all of this work").
 *
 * <h2>The rules every seeder here keeps</h2>
 * WHEN ABSENT, per member: only a seeded member (by the seed files' membership numbers) whose channel is empty is
 * given one, so a channel chosen on screen is never replaced. NEVER under the production marker -- the seeder checks it
 * itself. Deterministic: the channel follows from the membership number, so every start gives every member the same.
 * A channel no longer on record (renamed, or removed on screen) is skipped, and its members stay without.
 */
@Slf4j
@Component
public class RecruitmentChannelSeeder {

    /** One channel's share of the seed's members, out of {@link #OUT_OF}. */
    record Share(String channel, int parts) {
    }

    /** The parts add up to this. */
    static final int OUT_OF = 20;

    /** Unequal on purpose -- six equal bars say nothing: a third each through the BEC and RO, fewer by the rest. */
    static final List<Share> SHARES = List.of(
            new Share("BEC", 6), new Share("RO", 6), new Share("Website", 3),
            new Share("EORO", 2), new Share("MSO", 2), new Share("Youth", 1));

    private final NamedParameterJdbcTemplate jdbc;
    private final SeedMemberNumbers seedNumbers;
    private final SasboEnvironment environment;

    public RecruitmentChannelSeeder(NamedParameterJdbcTemplate jdbc, SeedMemberNumbers seedNumbers,
                                    SasboEnvironment environment) {
        this.jdbc = jdbc;
        this.seedNumbers = seedNumbers;
        this.environment = environment;
    }

    /** @return how many members were given a channel by this call */
    public int seed() {
        if (environment.isProduction()) {
            return 0;
        }
        Map<String, Long> channels = new HashMap<>();
        jdbc.query("SELECT id, name FROM recruitment_channel", new MapSqlParameterSource(),
                rs -> {
                    channels.put(rs.getString("name").trim().toLowerCase(Locale.ROOT), rs.getLong("id"));
                });
        List<String> waiting = jdbc.queryForList("SELECT membership_number FROM members"
                        + " WHERE recruitment_channel_id IS NULL AND membership_number IN (:numbers)",
                new MapSqlParameterSource("numbers", List.copyOf(seedNumbers.all())), String.class);
        List<MapSqlParameterSource> batch = new ArrayList<>();
        for (String number : waiting) {
            Long channel = channels.get(channelFor(number).toLowerCase(Locale.ROOT));
            if (channel != null) {
                batch.add(new MapSqlParameterSource().addValue("number", number).addValue("channel", channel));
            }
        }
        if (batch.isEmpty()) {
            return 0;
        }
        int[] updated = jdbc.batchUpdate("UPDATE members SET recruitment_channel_id = :channel"
                + " WHERE membership_number = :number AND recruitment_channel_id IS NULL",
                batch.toArray(MapSqlParameterSource[]::new));
        int given = 0;
        for (int rows : updated) {
            given += Math.max(rows, 0);
        }
        log.info("Gave {} of the seed's member(s) a recruitment channel", given);
        return given;
    }

    /** The channel a seeded member is recorded as recruited through, by the digits of their membership number. */
    public static String channelFor(String membershipNumber) {
        String digits = membershipNumber == null ? "" : membershipNumber.replaceAll("\\D", "");
        long key = digits.isEmpty() ? Math.abs((long) String.valueOf(membershipNumber).hashCode())
                : Long.parseLong(digits.length() > 18 ? digits.substring(digits.length() - 18) : digits);
        int part = (int) (key % OUT_OF);
        for (Share share : SHARES) {
            if (part < share.parts()) {
                return share.channel();
            }
            part -= share.parts();
        }
        throw new IllegalStateException("the shares add up to less than " + OUT_OF);
    }
}
