package za.co.titandynamix.sasbo_online_backend.services.impl;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import za.co.titandynamix.sasbo_online_backend.entity.member.Member;
import za.co.titandynamix.sasbo_online_backend.entity.recruitment.RecruitmentChannel;
import za.co.titandynamix.sasbo_online_backend.fixtures.MemberFixture;
import za.co.titandynamix.sasbo_online_backend.repository.MemberRepository;
import za.co.titandynamix.sasbo_online_backend.repository.RecruitmentChannelRepository;
import za.co.titandynamix.sasbo_online_backend.services.RecruitmentChannelService;
import za.co.titandynamix.sasbo_online_backend.support.AbstractWritingIT;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Removing a recruitment channel, on PostgreSQL -- register A49, corrected on the owner's decision of 2026-10-01
 * (MO21): the channel goes, and its members stay as they were, with only their channel cleared.
 *
 * <p>On PostgreSQL because the database is what refused it: {@code fk_members_recruitment_channel} (V23) will not let a
 * channel any member names be deleted, and the fast lane builds its schema from the entities, not the migrations.
 */
@DisplayName("Removing a recruitment channel, on PostgreSQL")
class RecruitmentChannelRemovalIT extends AbstractWritingIT {

    @Autowired
    private RecruitmentChannelService channels;

    @Autowired
    private RecruitmentChannelRepository channelRepository;

    @Autowired
    private MemberRepository members;

    @Test
    @DisplayName("🔒 MO21, A49: the channel goes and its members stay -- each status as it was, only the channel cleared, no status change written")
    void theChannelGoesAndItsMembersStay() {
        RecruitmentChannel channel = channelRepository.save(RecruitmentChannel.builder().name("INVENTED CHANNEL").build());
        RecruitmentChannel other = channelRepository.save(RecruitmentChannel.builder().name("INVENTED OTHER").build());
        Integer active = member("Active", channel);
        Integer suspended = member("Suspended", channel);
        Integer elsewhere = member("Active", other);

        assertThat(channels.delete(channel.getId())).isTrue();

        assertThat(channelRepository.existsById(channel.getId())).as("the channel the screen listed is gone").isFalse();
        assertThat(jdbc.queryForList("SELECT status FROM members WHERE id IN (?, ?) ORDER BY id", String.class,
                active, suspended)).as("each status as it was -- never Inactive").containsExactly("Active", "Suspended");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM members WHERE id IN (?, ?) AND recruitment_channel_id IS NULL",
                Long.class, active, suspended)).as("only their channel cleared").isEqualTo(2L);
        assertThat(jdbc.queryForObject("SELECT recruitment_channel_id FROM members WHERE id = ?", Long.class, elsewhere))
                .as("a member of another channel is untouched").isEqualTo(other.getId());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM member_status_changes", Long.class))
                .as("no status change is written").isZero();
    }

    @Test
    @DisplayName("a channel no member names goes as before; one that is not there is not found, and nothing moves")
    void aChannelNobodyNamesAndOneThatIsNotThere() {
        RecruitmentChannel unused = channelRepository.save(RecruitmentChannel.builder().name("INVENTED UNUSED").build());
        RecruitmentChannel kept = channelRepository.save(RecruitmentChannel.builder().name("INVENTED KEPT").build());
        Integer member = member("Active", kept);

        assertThat(channels.delete(unused.getId())).isTrue();
        assertThat(channels.delete(unused.getId())).as("asked again: not found").isFalse();
        assertThat(jdbc.queryForObject("SELECT recruitment_channel_id FROM members WHERE id = ?", Long.class, member))
                .isEqualTo(kept.getId());
    }

    private Integer member(String status, RecruitmentChannel channel) {
        Member member = MemberFixture.aMember();
        member.setStatus(status);
        member.setRecruitmentChannel(channel);
        return members.save(member).getId();
    }
}
