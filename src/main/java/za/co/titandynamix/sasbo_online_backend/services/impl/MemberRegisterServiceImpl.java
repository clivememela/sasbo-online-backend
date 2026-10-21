package za.co.titandynamix.sasbo_online_backend.services.impl;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.transaction.support.TransactionTemplate;
import za.co.titandynamix.sasbo_online_backend.acb.AcbTime;
import za.co.titandynamix.sasbo_online_backend.dto.member.MemberRegisterOverviewDto;
import za.co.titandynamix.sasbo_online_backend.dto.member.MemberRegisterPageDto;
import za.co.titandynamix.sasbo_online_backend.dto.member.MemberRegisterQuery;
import za.co.titandynamix.sasbo_online_backend.enums.PaymentType;
import za.co.titandynamix.sasbo_online_backend.exception.MonthEndRefusal;
import za.co.titandynamix.sasbo_online_backend.exception.MonthEndRefusalCode;
import za.co.titandynamix.sasbo_online_backend.repository.MemberRegisterRepository;
import za.co.titandynamix.sasbo_online_backend.repository.MemberRegisterRepository.Filter;
import za.co.titandynamix.sasbo_online_backend.repository.MemberRegisterRepository.Order;
import za.co.titandynamix.sasbo_online_backend.security.CallerScope;
import za.co.titandynamix.sasbo_online_backend.services.MemberRegisterService;
import za.co.titandynamix.sasbo_online_backend.utils.MemberStatuses;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The member register, a page at a time (register row A10).
 *
 * <p>WHAT A PAGE ASKS IS CHECKED BEFORE ANYTHING IS READ. A page below nought, or a size outside one to a hundred, is
 * {@code REGISTER_PAGE_INVALID}; an order the register is not read in is {@code REGISTER_SORT_UNKNOWN}; a status the
 * record does not take is {@code STATUS_UNKNOWN}; a text over its ceiling is {@code REQUEST_MALFORMED}, naming the field
 * and giving nothing of it back (ME-14). A page PAST THE LAST is not refused: it is answered with the last page, and the
 * answer says which -- the register shrinks under the pager whenever a save or a change of status moves a member out of
 * a filter, and the screen then lands where the list ends rather than on an empty page.
 *
 * <p>The count and the page are read in one read-only transaction at {@code REPEATABLE READ}: one snapshot, so the
 * total the pager prints and the rows under it cannot disagree.
 */
@Service
public class MemberRegisterServiceImpl implements MemberRegisterService {

    private final MemberRegisterRepository register;
    private final TransactionOperations snapshot;
    private final Clock clock;

    @Autowired
    public MemberRegisterServiceImpl(MemberRegisterRepository register, PlatformTransactionManager transactions,
                                     Clock clock) {
        this(register, snapshotOf(transactions), clock);
    }

    MemberRegisterServiceImpl(MemberRegisterRepository register, TransactionOperations snapshot, Clock clock) {
        this.register = register;
        this.snapshot = snapshot;
        this.clock = clock;
    }

    private static TransactionOperations snapshotOf(PlatformTransactionManager transactions) {
        TransactionTemplate template = new TransactionTemplate(transactions);
        template.setReadOnly(true);
        template.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        return template;
    }

    @Override
    public MemberRegisterPageDto page(MemberRegisterQuery query, CallerScope scope) {
        MemberRegisterQuery asked = query == null ? MemberRegisterQuery.everyone() : query;
        int page = asked.page() == null ? 0 : asked.page();
        int size = asked.size() == null ? DEFAULT_PAGE_SIZE : asked.size();
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw pageInvalid(page, size);
        }
        Sorting sorting = sorting(asked.sort());
        Filter filter = filterOf(asked);
        return snapshot.execute(status -> {
            long total = register.count(filter, scope);
            int pages = (int) ((total + size - 1) / size);
            int answered = pages == 0 ? 0 : Math.min(page, pages - 1);
            List<MemberRegisterPageDto.Row> rows = total == 0 ? List.of()
                    : register.page(filter, scope, sorting.order(), sorting.descending(), answered, size);
            return new MemberRegisterPageDto(rows, answered, size, total, pages, sorting.wire());
        });
    }

    @Override
    public MemberRegisterOverviewDto overview(CallerScope scope) {
        LocalDate today = AcbTime.today(clock);
        LocalDate firstOfMonth = today.withDayOfMonth(1);
        // `members.updated_at` is a TIMESTAMP without a zone, written by the entity as the JVM's local time; South
        // Africa's day is turned into that zone's terms, so "today" is SASBO's day on a host that runs in UTC.
        ZoneId stored = ZoneId.systemDefault();
        LocalDateTime dayStart = today.atStartOfDay(AcbTime.ZONE).withZoneSameInstant(stored).toLocalDateTime();
        LocalDateTime nextDay = today.plusDays(1).atStartOfDay(AcbTime.ZONE).withZoneSameInstant(stored)
                .toLocalDateTime();
        return snapshot.execute(status -> {
            Map<String, Long> byStatus = register.countsByStatus(scope);
            long[] joined = register.joined(scope, firstOfMonth, firstOfMonth.plusMonths(1),
                    PaymentType.DEBIT_ORDER.getDisplayName());
            return new MemberRegisterOverviewDto(
                    byStatus.values().stream().mapToLong(Long::longValue).sum(),
                    byStatus.getOrDefault(MemberStatuses.ACTIVE, 0L),
                    byStatus.getOrDefault(MemberStatuses.PENDING, 0L),
                    byStatus.getOrDefault(MemberStatuses.SUSPENDED, 0L),
                    joined[0],
                    joined[1],
                    register.updated(scope, dayStart, nextDay),
                    register.branches(scope, MAX_BRANCHES),
                    today.toString());
        });
    }

    /** An order, and which way: {@code key} or {@code key,asc|desc}. */
    record Sorting(Order order, boolean descending) {
        String wire() {
            return order.key() + (descending ? ",desc" : ",asc");
        }
    }

    static Sorting sorting(String sort) {
        if (sort == null || sort.isBlank()) {
            return new Sorting(Order.SURNAME, false);
        }
        String[] parts = sort.trim().split(",", -1);
        String direction = parts.length > 1 ? parts[1].trim().toLowerCase(Locale.ROOT) : "asc";
        Order order = parts.length <= 2 ? Order.byKey(parts[0].trim()).orElse(null) : null;
        if (order == null || !(direction.equals("asc") || direction.equals("desc"))) {
            throw new MonthEndRefusal(MonthEndRefusalCode.REGISTER_SORT_UNKNOWN,
                    "The register cannot be put in that order. It can be sorted by membership number, member, bank, "
                            + "branch or status.",
                    MonthEndRefusal.details("sort", MonthEndRefusal.echo(sort.trim()),
                            "sorts", String.join(",", SORTS), "directions", "asc,desc"));
        }
        return new Sorting(order, direction.equals("desc"));
    }

    /** Each criterion trimmed -- blank is not asked -- and held to its ceiling and, for a status, the vocabulary. */
    static Filter filterOf(MemberRegisterQuery asked) {
        String status = text("status", asked.status());
        if (status != null && MemberStatuses.kindOf(status) == null) {
            throw new MonthEndRefusal(MonthEndRefusalCode.STATUS_UNKNOWN,
                    MonthEndRefusal.echo(status) == null ? "That is not a membership status."
                            : "\"" + status + "\" is not a membership status.",
                    MonthEndRefusal.details("status", MonthEndRefusal.echo(status),
                            "known", String.join(",", MemberStatuses.KINDS.keySet())));
        }
        return new Filter(
                text("q", asked.q()),
                status,
                text("bank", asked.bank()),
                text("branch", asked.branch()),
                text("title", asked.title()),
                text("initials", asked.initials()),
                text("surname", asked.surname()),
                text("employeeNumber", asked.employeeNumber()),
                text("membershipNumber", asked.membershipNumber()),
                text("identityType", asked.identityType()),
                text("identityNumber", asked.identityNumber()),
                text("bankAccountNumber", asked.bankAccountNumber()),
                text("phone", asked.phone()),
                text("personalEmail", asked.personalEmail()),
                text("workEmail", asked.workEmail()),
                text("race", asked.race()));
    }

    private static String text(String field, String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.length() > MAX_TERM_LENGTH) {
            throw new MonthEndRefusal(MonthEndRefusalCode.REQUEST_MALFORMED,
                    "This request could not be read. Send each field in the form it is expected in.",
                    Map.of("field", field));
        }
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static MonthEndRefusal pageInvalid(int page, int size) {
        return new MonthEndRefusal(MonthEndRefusalCode.REGISTER_PAGE_INVALID,
                "That page of the register cannot be shown. A page holds from 1 to " + MAX_PAGE_SIZE + " members.",
                MonthEndRefusal.details("page", String.valueOf(page), "size", String.valueOf(size),
                        "maxSize", String.valueOf(MAX_PAGE_SIZE)));
    }
}
