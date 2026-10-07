package za.co.titandynamix.sasbo_online_backend.services;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.titandynamix.sasbo_online_backend.acb.AcbActor;
import za.co.titandynamix.sasbo_online_backend.dto.reference.NoDeductionReasonDto;
import za.co.titandynamix.sasbo_online_backend.dto.reference.NoDeductionReasonRequest;
import za.co.titandynamix.sasbo_online_backend.dto.reference.NoDeductionReasonsDto;
import za.co.titandynamix.sasbo_online_backend.dto.reference.ReferenceRows;
import za.co.titandynamix.sasbo_online_backend.entity.monthend.NoDeductionReason;
import za.co.titandynamix.sasbo_online_backend.exception.MonthEndRefusal;
import za.co.titandynamix.sasbo_online_backend.exception.MonthEndRefusalCode;
import za.co.titandynamix.sasbo_online_backend.repository.NoDeductionReasonRepository;
import za.co.titandynamix.sasbo_online_backend.repository.SasboReferenceRepository;
import za.co.titandynamix.sasbo_online_backend.utils.FreeText;

import java.time.Clock;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * The reasons for no payroll deduction, kept as data (V79) -- Stephne, MEN-17: "Can we have a dropdown sayin a reason
 * for no payroll deduction. Also have the opporenity to type a reason". Read by anyone, as every reference list is;
 * added to and edited by an administrator who maintains SASBO's lists ({@code REFERENCE_MAINTAIN}, on the route).
 *
 * <ul>
 *   <li>A reason is never deleted: a status change names it. It is set inactive, and offered to nobody;</li>
 *   <li>a name is 1 to {@value #NAME_LIMIT} characters of ordinary text, one reason a name whatever its case -- looked
 *       for first, and held by {@code no_deduction_reasons_name_unique} where two administrators meet;</li>
 *   <li>it stands under a suspension code on record here, by SASBO's number;</li>
 *   <li>an edit names the version it read, and is refused where another came first ({@code @Version});</li>
 *   <li>an administrator's reason, or an edit, is SASBO's own word: not provisional.</li>
 * </ul>
 */
@Service
public class NoDeductionReasons {

    /** The most reasons the list may hold: a drop-down, not a register. */
    public static final int CEILING = 50;

    /** The longest name, the column's own. */
    public static final int NAME_LIMIT = 60;

    /** The last place a reason may take. */
    public static final int ORDINAL_LIMIT = 999;

    private final NoDeductionReasonRepository reasons;
    private final SasboReferenceRepository codes;
    private final Clock clock;

    public NoDeductionReasons(NoDeductionReasonRepository reasons, SasboReferenceRepository codes, Clock clock) {
        this.reasons = reasons;
        this.codes = codes;
        this.clock = clock;
    }

    /** Every reason, active or not, in the drop-down's order, with its code's words where the code is on record. */
    @Transactional(readOnly = true)
    public NoDeductionReasonsDto list() {
        Map<Integer, String> names = codeNames();
        return new NoDeductionReasonsDto(reasons.findAllByOrderByOrdinalAscNameAsc().stream()
                .map(r -> dto(r, names)).toList(), CEILING, NAME_LIMIT, FreeText.NOTE_LIMIT);
    }

    /** A reason added by an administrator: last on the list unless given a place, active unless said otherwise. */
    @Transactional
    public NoDeductionReasonDto create(NoDeductionReasonRequest request) {
        String name = name(request.name());
        int code = code(request.suspensionCode());
        Integer ordinal = request.ordinal() == null ? null : ordinal(request.ordinal());
        if (reasons.count() >= CEILING) {
            throw invalid("count", CEILING, "The list holds " + CEILING + " reasons already. Set one inactive and "
                    + "rename it instead.");
        }
        refuseTaken(name, null);
        NoDeductionReason reason = new NoDeductionReason();
        reason.setName(name);
        reason.setSuspensionCode(code);
        reason.setOrdinal(ordinal == null ? reasons.lastOrdinal() + 1 : ordinal);
        reason.setActive(request.active() == null || request.active());
        reason.setProvisional(false);
        stamp(reason);
        try {
            return dto(reasons.saveAndFlush(reason), codeNames());
        } catch (DataIntegrityViolationException meanwhile) {
            throw takenMeanwhile();
        }
    }

    /** An edit of a reason, on the version the screen read. What the request leaves out stays as it was. */
    @Transactional
    public NoDeductionReasonDto update(long id, NoDeductionReasonRequest request) {
        NoDeductionReason reason = reasons.findById(id).orElseThrow(() -> notFound(id));
        if (request.version() == null) {
            throw new MonthEndRefusal(MonthEndRefusalCode.REQUEST_MALFORMED,
                    "Send the version of the reason you are editing, as the list answered it. Nothing was changed.",
                    Map.of("field", "version"));
        }
        if (!request.version().equals(reason.getVersion())) {
            throw changedSince(reason);
        }
        String name = name(request.name());
        int code = code(request.suspensionCode());
        refuseTaken(name, id);
        reason.setName(name);
        reason.setSuspensionCode(code);
        if (request.ordinal() != null) {
            reason.setOrdinal(ordinal(request.ordinal()));
        }
        if (request.active() != null) {
            reason.setActive(request.active());
        }
        reason.setProvisional(false);
        stamp(reason);
        try {
            return dto(reasons.saveAndFlush(reason), codeNames());
        } catch (ObjectOptimisticLockingFailureException overtaken) {
            throw changedSince(reasons.findById(id).orElse(reason));
        } catch (DataIntegrityViolationException meanwhile) {
            throw takenMeanwhile();
        }
    }

    // ------------------------------------------------------------------------------------------------ the rules

    private static String name(String given) {
        String name = FreeText.clean(given);
        if (name == null) {
            throw invalid("name", NAME_LIMIT, "Give the reason a name of up to " + NAME_LIMIT + " characters.");
        }
        if (FreeText.problem(name, NAME_LIMIT, false) != null) {
            throw invalid("name", NAME_LIMIT, "A reason's name may be at most " + NAME_LIMIT + " characters of ordinary "
                    + "text, on one line.");
        }
        return name;
    }

    private int code(Integer given) {
        if (given == null || codes.suspensionCode(given).isEmpty()) {
            throw new MonthEndRefusal(MonthEndRefusalCode.NO_DEDUCTION_REASON_INVALID,
                    "A reason stands under one of SASBO's suspension codes on record here. Choose one. Nothing was "
                            + "changed.", MonthEndRefusal.details("field", "suspensionCode", "known",
                    codes.suspensionCodeNumbers().stream().map(String::valueOf).collect(Collectors.joining(","))));
        }
        return given;
    }

    private static int ordinal(int given) {
        if (given < 1 || given > ORDINAL_LIMIT) {
            throw invalid("ordinal", ORDINAL_LIMIT, "A reason's place on the list is from 1 to " + ORDINAL_LIMIT + ".");
        }
        return given;
    }

    private void refuseTaken(String name, Long self) {
        reasons.findByNameInAnyCase(name).stream().filter(other -> !other.getId().equals(self)).findFirst()
                .ifPresent(other -> {
                    throw new MonthEndRefusal(MonthEndRefusalCode.NO_DEDUCTION_REASON_TAKEN,
                            "Another reason has that name already. Nothing was changed.",
                            Map.of("id", String.valueOf(other.getId())));
                });
    }

    private void stamp(NoDeductionReason reason) {
        reason.setUpdatedBy(AcbActor.current());
        reason.setUpdatedAt(clock.instant());
    }

    private static MonthEndRefusal invalid(String field, int limit, String sentence) {
        return new MonthEndRefusal(MonthEndRefusalCode.NO_DEDUCTION_REASON_INVALID, sentence + " Nothing was changed.",
                MonthEndRefusal.details("field", field, "limit", String.valueOf(limit)));
    }

    private static MonthEndRefusal takenMeanwhile() {
        return new MonthEndRefusal(MonthEndRefusalCode.NO_DEDUCTION_REASON_TAKEN,
                "Another reason was given that name a moment ago. Nothing was changed.");
    }

    private static MonthEndRefusal notFound(long id) {
        return new MonthEndRefusal(MonthEndRefusalCode.NO_DEDUCTION_REASON_NOT_FOUND,
                "That reason is not on the list.", Map.of("id", String.valueOf(id)));
    }

    private static MonthEndRefusal changedSince(NoDeductionReason now) {
        return new MonthEndRefusal(MonthEndRefusalCode.NO_DEDUCTION_REASON_CHANGED_SINCE,
                "Someone else changed this reason since you opened it. Nothing was changed: look at theirs, then edit "
                        + "it again.", MonthEndRefusal.details("version", String.valueOf(now.getVersion()),
                "updatedBy", now.getUpdatedBy(), "updatedAt", String.valueOf(now.getUpdatedAt())));
    }

    private Map<Integer, String> codeNames() {
        return codes.suspensionCodes().stream().collect(Collectors.toMap(ReferenceRows.SuspensionCode::code,
                ReferenceRows.SuspensionCode::name, (a, b) -> a));
    }

    private static NoDeductionReasonDto dto(NoDeductionReason r, Map<Integer, String> names) {
        return new NoDeductionReasonDto(r.getId(), r.getName(), r.getSuspensionCode(), names.get(r.getSuspensionCode()),
                r.getOrdinal(), r.isActive(), r.isProvisional(), r.getVersion() == null ? 0 : r.getVersion(),
                r.getUpdatedBy(), r.getUpdatedAt() == null ? null : r.getUpdatedAt().toString());
    }
}
