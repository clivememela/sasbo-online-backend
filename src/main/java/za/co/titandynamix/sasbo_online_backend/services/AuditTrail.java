package za.co.titandynamix.sasbo_online_backend.services;

import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import za.co.titandynamix.sasbo_online_backend.dto.signin.AuditEventDto;
import za.co.titandynamix.sasbo_online_backend.dto.signin.SignInPage;
import za.co.titandynamix.sasbo_online_backend.entity.signin.AuditEvent;
import za.co.titandynamix.sasbo_online_backend.entity.signin.StaffUser;
import za.co.titandynamix.sasbo_online_backend.exception.SignInRefusal;
import za.co.titandynamix.sasbo_online_backend.exception.SignInRefusalCode;
import za.co.titandynamix.sasbo_online_backend.repository.AuditEventRepository;
import za.co.titandynamix.sasbo_online_backend.security.ScopedPrincipal;
import za.co.titandynamix.sasbo_online_backend.security.SignedInUser;
import za.co.titandynamix.sasbo_online_backend.utils.MonthRange;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * The record of who did what (V67) -- the one place it is written and read.
 *
 * <h2>Writing it, from any stream</h2>
 * {@code auditTrail.record("DOD_FILE_DOWNLOADED", "DOD_RUN", String.valueOf(runId), "The bank's file, 412 lines")}
 * records the caller on this thread -- the signed-in user, or {@code system} for the seed and for work no request
 * started -- IN THE CALLER'S TRANSACTION, so an action that rolls back leaves no line behind. A refusal worth keeping
 * (a failed sign-in, a lock-out) is recorded with {@link #recordAlone}, in a transaction of its own, because the
 * refusal rolls back whatever it is thrown in. The summary names fields and counts -- never a member's values.
 *
 * <h2>Reading it</h2>
 * A page at a time, newest first, filtered by the thing, the person and the days -- days in SASBO's zone, both ends
 * included ({@code from}/{@code to}, ISO dates, as the client sends them).
 */
@Service
public class AuditTrail {

    // The actions sign-in records; other streams name their own the same way.
    public static final String SIGN_IN = "SIGN_IN";
    public static final String SIGN_OUT = "SIGN_OUT";
    public static final String SIGN_IN_FAILED = "SIGN_IN_FAILED";
    public static final String ACCOUNT_LOCKED = "ACCOUNT_LOCKED";
    public static final String PASSWORD_CHANGED = "PASSWORD_CHANGED";
    public static final String PASSWORD_RESET = "PASSWORD_RESET";
    public static final String USER_CREATED = "USER_CREATED";
    public static final String USER_UPDATED = "USER_UPDATED";
    public static final String USER_DEACTIVATED = "USER_DEACTIVATED";
    public static final String USER_REACTIVATED = "USER_REACTIVATED";
    public static final String POSITION_CREATED = "POSITION_CREATED";
    public static final String POSITION_UPDATED = "POSITION_UPDATED";
    public static final String ROLE_CREATED = "ROLE_CREATED";
    public static final String ROLE_UPDATED = "ROLE_UPDATED";

    /** The things sign-in's actions are done to. */
    public static final String USER = "USER";
    public static final String POSITION = "POSITION";
    public static final String ROLE = "ROLE";

    /** Who acted where nobody signed in did: the seed, the start-up, a job. */
    public static final String SYSTEM = "system";

    static final int DEFAULT_PAGE_SIZE = 50;
    static final int MAX_PAGE_SIZE = 100;
    static final int SUMMARY_CEILING = 500;

    private static final Pattern ACTION = Pattern.compile("[A-Z][A-Z0-9_]{2,59}");
    private static final Pattern ENTITY_TYPE = Pattern.compile("[A-Z][A-Z0-9_]{1,39}");

    private final AuditEventRepository events;
    private final Clock clock;
    private final TransactionTemplate alone;

    public AuditTrail(AuditEventRepository events, Clock clock, PlatformTransactionManager transactions) {
        this.events = events;
        this.clock = clock;
        TransactionTemplate template = new TransactionTemplate(transactions);
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.alone = template;
    }

    /** Who did it, as the line keeps them. */
    record Who(Long userId, String username, String displayName) {
    }

    /** Records an action by the caller on this thread, in the caller's transaction. */
    @Transactional
    public void record(String action, String entityType, String entityId, String summary) {
        events.save(line(current(), action, entityType, entityId, summary));
    }

    /** Records an action by the caller on this thread, in a transaction of its own -- for a refusal. */
    public void recordAlone(String action, String entityType, String entityId, String summary) {
        Who who = current();
        alone.executeWithoutResult(status -> events.save(line(who, action, entityType, entityId, summary)));
    }

    /**
     * Records an action ABOUT {@code subject} -- a sign-in, its failure, a lock-out -- naming them, never the caller on
     * the thread, who is nobody yet. {@code subject} null records no person at all: a failed sign-in for a username
     * nobody holds keeps nothing of what was typed, which may be somebody's password.
     */
    public void recordFor(StaffUser subject, boolean inItsOwnTransaction, String action, String summary) {
        Who who = subject == null ? new Who(null, null, null)
                : new Who(subject.getId(), subject.getUsername(), subject.getDisplayName());
        String entityId = subject == null ? null : String.valueOf(subject.getId());
        String entityType = subject == null ? null : USER;
        if (inItsOwnTransaction) {
            alone.executeWithoutResult(status -> events.save(line(who, action, entityType, entityId, summary)));
        } else {
            events.save(line(who, action, entityType, entityId, summary));
        }
    }

    private AuditEvent line(Who who, String action, String entityType, String entityId, String summary) {
        if (action == null || !ACTION.matcher(action).matches()) {
            throw new IllegalArgumentException("an audit action is 3 to 60 capitals, digits or underscores");
        }
        if (entityType != null && !ENTITY_TYPE.matcher(entityType).matches()) {
            throw new IllegalArgumentException("an audit entity type is 2 to 40 capitals, digits or underscores");
        }
        return new AuditEvent(clock.instant(), who.userId(), cut(who.username(), 60), cut(who.displayName(), 80),
                action, entityType, cut(entityId, 64), cut(summary == null || summary.isBlank() ? action : summary,
                SUMMARY_CEILING));
    }

    /** The caller on this thread. */
    static Who current() {
        Authentication caller = SecurityContextHolder.getContext().getAuthentication();
        if (caller == null || !caller.isAuthenticated() || caller instanceof AnonymousAuthenticationToken) {
            return new Who(null, SYSTEM, SYSTEM);
        }
        if (caller.getPrincipal() instanceof SignedInUser user) {
            return new Who(user.userId(), user.username(), user.displayName());
        }
        if (caller.getPrincipal() instanceof ScopedPrincipal principal) {
            return new Who(null, principal.username(), principal.displayName());
        }
        return new Who(null, caller.getName(), caller.getName());
    }

    // ------------------------------------------------------------------------------------------------ reading

    /**
     * A page of the record, newest first.
     *
     * @throws SignInRefusal {@code AUDIT_FILTER_INVALID} for a filter that cannot be read or a range that ends before it
     *                       begins; {@code PAGE_INVALID} for a page below nought or a size below one
     */
    @Transactional(readOnly = true)
    public SignInPage<AuditEventDto> search(String entityType, String entityId, String username, String from,
                                            String to, Integer page, Integer size) {
        int pageNumber = page == null ? 0 : page;
        int pageSize = size == null ? DEFAULT_PAGE_SIZE : size;
        if (pageNumber < 0 || pageSize < 1) {
            throw new SignInRefusal(SignInRefusalCode.PAGE_INVALID, "That page does not exist. Pages are counted "
                    + "from 0, and hold at least one line.", Map.of("maxSize", String.valueOf(MAX_PAGE_SIZE)));
        }
        pageSize = Math.min(pageSize, MAX_PAGE_SIZE);
        String type = filter("entityType", entityType, 40);
        String id = filter("entityId", entityId, 64);
        String who = filter("username", username, 60);
        LocalDate fromDay = day("from", from);
        LocalDate toDay = day("to", to);
        if (fromDay != null && toDay != null && toDay.isBefore(fromDay)) {
            throw new SignInRefusal(SignInRefusalCode.AUDIT_FILTER_INVALID, "The range ends before it begins. Choose "
                    + "a 'to' day on or after the 'from' day.", Map.of("field", "to"));
        }
        Instant start = fromDay == null ? null : fromDay.atStartOfDay(MonthRange.ZONE).toInstant();
        Instant end = toDay == null ? null : toDay.plusDays(1).atStartOfDay(MonthRange.ZONE).toInstant();
        Specification<AuditEvent> spec = (root, query, cb) -> {
            List<Predicate> where = new ArrayList<>();
            if (type != null) {
                where.add(cb.equal(root.get("entityType"), type.toUpperCase(Locale.ROOT)));
            }
            if (id != null) {
                where.add(cb.equal(root.get("entityId"), id));
            }
            if (who != null) {
                where.add(cb.equal(root.get("username"), who.toLowerCase(Locale.ROOT)));
            }
            if (start != null) {
                where.add(cb.greaterThanOrEqualTo(root.get("at"), start));
            }
            if (end != null) {
                where.add(cb.lessThan(root.get("at"), end));
            }
            return cb.and(where.toArray(Predicate[]::new));
        };
        Page<AuditEvent> found = events.findAll(spec, PageRequest.of(pageNumber, pageSize,
                Sort.by(Sort.Order.desc("at"), Sort.Order.desc("id"))));
        return new SignInPage<>(found.getContent().stream().map(AuditTrail::dto).toList(), found.getNumber(),
                found.getSize(), found.getTotalElements(), found.getTotalPages());
    }

    static AuditEventDto dto(AuditEvent event) {
        return new AuditEventDto(event.getAt().toString(), event.getUsername(), event.getDisplayName(),
                event.getAction(), event.getEntityType(), event.getEntityId(), event.getSummary());
    }

    private static String filter(String field, String value, int ceiling) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.length() > ceiling) {
            throw new SignInRefusal(SignInRefusalCode.AUDIT_FILTER_INVALID, "That filter is longer than anything the "
                    + "record holds.", Map.of("field", field, "maxLength", String.valueOf(ceiling)));
        }
        return trimmed;
    }

    private static LocalDate day(String field, String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(value.trim());
        } catch (DateTimeParseException unreadable) {
            throw new SignInRefusal(SignInRefusalCode.AUDIT_FILTER_INVALID, "That day could not be read. Write it as "
                    + "year, month and day -- 2026-10-07.", Map.of("field", field));
        }
    }

    private static String cut(String value, int ceiling) {
        return value == null || value.length() <= ceiling ? value : value.substring(0, ceiling);
    }
}
