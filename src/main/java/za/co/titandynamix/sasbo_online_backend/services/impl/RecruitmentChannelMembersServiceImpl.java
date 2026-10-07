package za.co.titandynamix.sasbo_online_backend.services.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.titandynamix.sasbo_online_backend.dto.recruitment.ChannelMemberDto;
import za.co.titandynamix.sasbo_online_backend.dto.recruitment.ChannelMembersPageDto;
import za.co.titandynamix.sasbo_online_backend.dto.recruitment.ChannelSummaryDto;
import za.co.titandynamix.sasbo_online_backend.dto.recruitment.MemberCandidatesDto;
import za.co.titandynamix.sasbo_online_backend.entity.member.Member;
import za.co.titandynamix.sasbo_online_backend.entity.recruitment.RecruitmentChannel;
import za.co.titandynamix.sasbo_online_backend.entity.reference.SasboArea;
import za.co.titandynamix.sasbo_online_backend.exception.ReferenceRefusal;
import za.co.titandynamix.sasbo_online_backend.exception.ReferenceRefusalCode;
import za.co.titandynamix.sasbo_online_backend.repository.MemberRepository;
import za.co.titandynamix.sasbo_online_backend.repository.RecruitmentChannelMembersRepository;
import za.co.titandynamix.sasbo_online_backend.repository.RecruitmentChannelMembersRepository.SummaryRow;
import za.co.titandynamix.sasbo_online_backend.repository.RecruitmentChannelRepository;
import za.co.titandynamix.sasbo_online_backend.security.CallerScope;
import za.co.titandynamix.sasbo_online_backend.services.MemberTrail;
import za.co.titandynamix.sasbo_online_backend.services.RecruitmentChannelMembersService;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 * RC15, the server half: the Recruiters screen's figures and lists, by the member's own recruitment channel.
 *
 * <p>A member's channel is changed here and nowhere else on that screen, one column at a time and recorded in the
 * member's history: the screen used to write the channel's NAME into the member's {@code areasRep} -- the area's
 * representative, a field of its own -- through the whole-record PUT, so "Add Member" changed nothing it showed.
 */
@Service
@RequiredArgsConstructor
public class RecruitmentChannelMembersServiceImpl implements RecruitmentChannelMembersService {

    /** The order a channel's banks and branches are listed in: as a person reads them, ties settled exactly. */
    private static final Comparator<String> READING_ORDER =
            String.CASE_INSENSITIVE_ORDER.thenComparing(Comparator.naturalOrder());

    private final RecruitmentChannelMembersRepository rows;
    private final RecruitmentChannelRepository channels;
    private final MemberRepository members;
    private final MemberTrail trail;

    @Override
    @Transactional(readOnly = true)
    public List<ChannelSummaryDto> summary() {
        Map<Long, Channel> byChannel = new LinkedHashMap<>();
        for (SummaryRow row : rows.summary(CallerScope.current())) {
            byChannel.computeIfAbsent(row.channelId(), id -> new Channel(row)).add(row);
        }
        return byChannel.values().stream().map(Channel::dto).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public ChannelMembersPageDto members(long channelId, String membershipNumber, String name, String bank,
                                         String branch, String status, int page, int size) {
        if (page < 0) {
            throw invalid("page", "A page number is 0 or more.");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw invalid("size", "A page holds 1 to " + MAX_PAGE_SIZE + " members.");
        }
        channel(channelId);
        RecruitmentChannelMembersRepository.Slice slice = rows.members(channelId, term("membershipNumber",
                membershipNumber), term("name", name), term("bank", bank), term("branch", branch),
                term("status", status), CallerScope.current(), page, size);
        int pages = (int) ((slice.total() + size - 1) / size);
        return new ChannelMembersPageDto(slice.rows(), page, size, slice.total(), pages);
    }

    @Override
    @Transactional(readOnly = true)
    public MemberCandidatesDto candidates(String query, Long excludeChannel) {
        List<ChannelMemberDto> found = rows.candidates(term("q", query), excludeChannel, CallerScope.current(),
                PICKER_ROWS + 1);
        return found.size() > PICKER_ROWS
                ? new MemberCandidatesDto(List.copyOf(found.subList(0, PICKER_ROWS)), true)
                : new MemberCandidatesDto(found, false);
    }

    @Override
    @Transactional
    public ChannelMemberDto assign(long channelId, int memberId) {
        RecruitmentChannel channel = channel(channelId);
        Member member = memberInScope(memberId);
        RecruitmentChannel current = member.getRecruitmentChannel();
        if (current != null && Objects.equals(current.getId(), channel.getId())) {
            return row(member);
        }
        return row(change(member, channel));
    }

    @Override
    @Transactional
    public void unassign(long channelId, int memberId) {
        RecruitmentChannel channel = channel(channelId);
        Member member = memberInScope(memberId);
        RecruitmentChannel current = member.getRecruitmentChannel();
        if (current == null) {
            return;
        }
        if (!Objects.equals(current.getId(), channel.getId())) {
            throw new ReferenceRefusal(ReferenceRefusalCode.MEMBER_NOT_IN_CHANNEL,
                    "That member is recorded as recruited through " + current.getName() + ", not " + channel.getName()
                            + ". Reload the list to see where they are now.",
                    Map.of("channel", current.getName()));
        }
        change(member, null);
    }

    /** The one column, and the history row that says who changed it and from what. */
    private Member change(Member member, RecruitmentChannel channel) {
        Map<String, String> before = trail.snapshot(member);
        member.setRecruitmentChannel(channel);
        Member saved = members.save(member);
        trail.changed(saved.getId(), before, trail.snapshot(saved));
        return saved;
    }

    private RecruitmentChannel channel(long channelId) {
        return channels.findById(channelId).orElseThrow(() -> new ReferenceRefusal(
                ReferenceRefusalCode.CHANNEL_NOT_FOUND,
                "That recruitment channel is no longer on record. Reload the list, then try again.",
                Map.of("channelId", String.valueOf(channelId))));
    }

    /**
     * The member, where the caller may see them. A limited caller is told of a member outside their region or area
     * exactly as of one not on record at all: the answer must not say who exists elsewhere.
     */
    private Member memberInScope(int memberId) {
        Member member = members.findById(memberId).orElse(null);
        CallerScope scope = CallerScope.current();
        if (member != null && !scope.isAll()) {
            SasboArea area = member.getWorkplace() == null ? null : member.getWorkplace().getArea();
            String letter = area == null ? null : area.getLetter();
            String region = area == null || area.getRegion() == null ? null : area.getRegion().getCode();
            if (!scope.allows(letter, region)) {
                member = null;
            }
        }
        if (member == null) {
            throw new ReferenceRefusal(ReferenceRefusalCode.MEMBER_NOT_FOUND,
                    "That member is not on record. Reload the list, then try again.",
                    Map.of("memberId", String.valueOf(memberId)));
        }
        return member;
    }

    /** A search term: trimmed, a blank one as none, and never longer than the limit. */
    private static String term(String field, String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.length() > MAX_TERM_LENGTH) {
            throw invalid(field, "A search is at most " + MAX_TERM_LENGTH + " characters.");
        }
        return trimmed;
    }

    private static ReferenceRefusal invalid(String field, String message) {
        return new ReferenceRefusal(ReferenceRefusalCode.REQUEST_INVALID, message, Map.of("field", field));
    }

    private static ChannelMemberDto row(Member m) {
        return new ChannelMemberDto(m.getId(), m.getMembershipNumber(), m.getTitle(), m.getInitials(),
                m.getFirstNames(), m.getSurname(), m.getBankName(), m.getBranch(), m.getStatus());
    }

    /** One channel's rows folded into its figures: the members summed, the banks and branches gathered once each. */
    private static final class Channel {

        private final SummaryRow first;
        private final SortedSet<String> banks = new TreeSet<>(READING_ORDER);
        private final SortedSet<String> branches = new TreeSet<>(READING_ORDER);
        private long members;

        Channel(SummaryRow first) {
            this.first = first;
        }

        void add(SummaryRow row) {
            members += row.members();
            // A blank name is no bank -- the screen dropped it the same way (filter(Boolean)).
            if (row.members() > 0 && row.bankName() != null && !row.bankName().isEmpty()) {
                banks.add(row.bankName());
            }
            if (row.members() > 0 && row.branch() != null && !row.branch().isEmpty()) {
                branches.add(row.branch());
            }
        }

        ChannelSummaryDto dto() {
            return new ChannelSummaryDto(first.channelId(), first.name(), first.enabled(), members, List.copyOf(banks),
                    List.copyOf(branches));
        }
    }
}
