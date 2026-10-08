package za.co.titandynamix.sasbo_online_backend.services;

import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.stereotype.Service;
import za.co.titandynamix.sasbo_online_backend.dto.monthend.SuspendedMemberDto;
import za.co.titandynamix.sasbo_online_backend.dto.monthend.SuspendedMembersPageDto;
import za.co.titandynamix.sasbo_online_backend.exception.MonthEndRefusal;
import za.co.titandynamix.sasbo_online_backend.exception.MonthEndRefusalCode;
import za.co.titandynamix.sasbo_online_backend.repository.SasboReferenceRepository;
import za.co.titandynamix.sasbo_online_backend.repository.SuspendedMembersRepository;
import za.co.titandynamix.sasbo_online_backend.security.CallerScope;
import za.co.titandynamix.sasbo_online_backend.utils.MonthRange;
import za.co.titandynamix.sasbo_online_backend.utils.SuspensionLapse;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

/**
 * The members suspended now, and those whose suspension has lapsed (Stephne, MEN-18): "We should also know who the
 * suspended members are name, mem number etc. The system should also remind us when the suspension period for a member
 * have lapsed, so we can delete. (suspensions is only for 3months)".
 *
 * <ul>
 *   <li>A LISTING that names members, so it is held on the route -- {@code MEMBERS_VIEW} and {@code MONTH_END_VIEW}
 *       (Stephne, DOD-14: organisers do not see suspensions) -- and every row is in the caller's reach, filtered in
 *       SQL ({@code CallerScope}): a member with no workplace is in reach only for ALL;</li>
 *   <li>paged, at most {@value #CEILING} a page, and bounded by the figures' gate and timeout;</li>
 *   <li>the lapse is {@link SuspensionLapse}'s, judged on today in SASBO's zone from the injected clock.</li>
 * </ul>
 */
@Service
public class SuspendedMembers {

    /** The largest page. */
    public static final int CEILING = 100;

    private final SuspendedMembersRepository repository;
    private final SasboReferenceRepository codes;
    private final SummaryGate gate;
    private final Clock clock;

    public SuspendedMembers(SuspendedMembersRepository repository, SasboReferenceRepository codes, SummaryGate gate,
                            Clock clock) {
        this.repository = repository;
        this.codes = codes;
        this.gate = gate;
        this.clock = clock;
    }

    /** A page of every member suspended now, in the caller's reach. */
    public SuspendedMembersPageDto members(Integer page, Integer size, Long employer, Integer code) {
        return read(page, size, employer, code, false);
    }

    /** A page of the members whose suspension has lapsed, in the caller's reach -- the oldest lapse first. */
    public SuspendedMembersPageDto lapsed(Integer page, Integer size, Long employer, Integer code) {
        return read(page, size, employer, code, true);
    }

    private SuspendedMembersPageDto read(Integer page, Integer size, Long employer, Integer code, boolean lapsedOnly) {
        int p = page == null ? 0 : page;
        int s = size == null ? 50 : size;
        if (p < 0 || s < 1 || s > CEILING) {
            throw new MonthEndRefusal(MonthEndRefusalCode.SUSPENDED_PAGE_INVALID,
                    "A page of the suspended members is from page 0, of 1 to " + CEILING + " members.",
                    MonthEndRefusal.details("page", String.valueOf(p), "size", String.valueOf(s),
                            "ceiling", String.valueOf(CEILING)));
        }
        if (code != null && codes.suspensionCode(code).isEmpty()) {
            throw new MonthEndRefusal(MonthEndRefusalCode.SUSPENSION_CODE_UNKNOWN,
                    "That suspension code is not on the list.",
                    MonthEndRefusal.details("suspensionCode", String.valueOf(code), "known",
                            codes.suspensionCodeNumbers().stream().map(String::valueOf)
                                    .collect(Collectors.joining(","))));
        }
        LocalDate asAt = LocalDate.now(clock.withZone(MonthRange.ZONE));
        SuspendedMembersRepository.Ask ask = new SuspendedMembersRepository.Ask(CallerScope.current(), employer, code,
                lapsedOnly, SuspensionLapse.lapsedIfBegunBefore(asAt));
        if (!gate.tryEnter()) {
            throw unavailable();
        }
        List<SuspendedMembersRepository.Row> rows;
        SuspendedMembersRepository.Counts counts;
        try {
            counts = repository.counts(ask);
            rows = counts.total() == 0 ? List.of() : repository.page(ask, p, s);
        } catch (QueryTimeoutException | DataAccessResourceFailureException tooLong) {
            throw unavailable();
        } finally {
            gate.leave();
        }
        return new SuspendedMembersPageDto(asAt.toString(), SuspensionLapse.MONTHS, p, s, counts.total(),
                (int) ((counts.total() + s - 1) / s), counts.lapsed(), counts.undated(),
                rows.stream().map(row -> item(row, asAt)).toList());
    }

    private static SuspendedMemberDto item(SuspendedMembersRepository.Row row, LocalDate asAt) {
        LocalDate suspendedOn = row.suspendedAt() == null ? null : row.suspendedAt().atZone(MonthRange.ZONE).toLocalDate();
        LocalDate lapsesOn = suspendedOn == null ? null : SuspensionLapse.lapsesOn(suspendedOn);
        String name = ((row.initials() == null ? "" : row.initials().trim()) + " "
                + (row.surname() == null ? "" : row.surname().trim())).trim();
        return new SuspendedMemberDto(row.memberId(), row.membershipNumber(), name.isEmpty() ? null : name,
                row.employerName() != null ? row.employerName() : row.bankName(),
                row.raNumber() == null ? null : "R/A " + row.raNumber() + (row.workplaceName() == null ? ""
                        : " " + row.workplaceName()),
                row.areaLetter(), row.paymentType(), row.suspensionCode(), row.suspensionName(),
                row.noDeductionReason(), suspendedOn == null ? null : suspendedOn.toString(),
                lapsesOn == null ? null : lapsesOn.toString(),
                suspendedOn != null && SuspensionLapse.lapsedBy(suspendedOn, asAt));
    }

    private static MonthEndRefusal unavailable() {
        return new MonthEndRefusal(MonthEndRefusalCode.SUMMARY_UNAVAILABLE,
                "The list is taking too long to work out just now. Nothing is wrong with the members' records. Try "
                        + "again in a moment.");
    }
}
