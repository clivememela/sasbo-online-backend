package za.co.titandynamix.sasbo_online_backend.validator;

import za.co.titandynamix.sasbo_online_backend.utils.DebitDay;
import za.co.titandynamix.sasbo_online_backend.entity.member.Member;
import za.co.titandynamix.sasbo_online_backend.enums.AccountType;
import za.co.titandynamix.sasbo_online_backend.enums.BecExecutivePosition;
import za.co.titandynamix.sasbo_online_backend.enums.BecStructure;
import za.co.titandynamix.sasbo_online_backend.enums.CosatuPosition;
import za.co.titandynamix.sasbo_online_backend.enums.PaymentType;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/**
 * Validator for Member entity business rules.
 * Enforces conditional field requirements based on payment type.
 */
public class MemberValidator {

    /**
     * ACB {@code 00.5}: the account type, which the bureau file prints on every debit (Q16).
     *
     * <p>A value given must name one of the three, and is stored as its display name whatever
     * spelling arrived. It is REQUIRED only where the save could have supplied it -- a new
     * debit-order member, or banking details saved as a debit order -- and never on an edit of
     * something else: every member captured before V49 has none, and refusing a phone-number
     * correction over a field on another tab is R-ROB-I's defect. The run names such a member
     * instead ({@code SNAPSHOT_INCOMPLETE}, "no account type").
     *
     * @param required whether this save must carry one when the member pays by debit order
     * @throws IllegalArgumentException for a value that is not an account type, or a missing one
     *         where {@code required}
     */
    public static void validateAccountType(Member member, boolean required) {
        AccountType type = AccountType.fromString(member.getAccountType());
        if (type != null) {
            member.setAccountType(type.getDisplayName());
            return;
        }
        member.setAccountType(null);
        if (required && paysByDebitOrder(member)) {
            throw new IllegalArgumentException("Account type is required when payment type is '"
                    + PaymentType.DEBIT_ORDER.getDisplayName() + "'");
        }
    }

    private static boolean paysByDebitOrder(Member member) {
        try {
            return PaymentType.fromString(member.getPaymentType()) == PaymentType.DEBIT_ORDER;
        } catch (IllegalArgumentException unknown) {
            return false;
        }
    }

    /**
     * Validate member payment type and banking information.
     * If payment type is Debit Order, bank account number is required.
     * If payment type is Payroll, bank account is not required.
     *
     * @param member The member to validate
     * @param previousDebitDay the debit day this member was STORED with before this
     *        request, or {@code null} on create. 🗣️ Decision ⑥, 2026-09-10: the
     *        20–31 range is enforced on a day the caller CHANGED, never on one it
     *        merely sent back unchanged — see {@link #debitDayWasChanged}.
     * @throws IllegalArgumentException if validation fails
     */
    public static void validatePaymentType(Member member, Integer previousDebitDay) {
        String paymentTypeStr = member.getPaymentType();
        
        if (paymentTypeStr == null || paymentTypeStr.isBlank()) {
            // Payment type not set — not validated here
            return;
        }

        // Resolve the payment type; skip validation for unrecognised values
        // (e.g. legacy data such as "Cash" stored before the current scheme)
        PaymentType paymentType;
        try {
            paymentType = PaymentType.fromString(paymentTypeStr);
        } catch (IllegalArgumentException ignored) {
            // Unknown payment type — allow it through; banking rules cannot be enforced
            return;
        }

        if (paymentType.requiresBankAccount()) {
            if (member.getBankAccountNumber() == null || member.getBankAccountNumber().isBlank()) {
                throw new IllegalArgumentException(
                    "Bank account number is required when payment type is '" + paymentType.getDisplayName() + "'"
                );
            }

            // The permitted range lives in DebitDay, once. It was {20, 25, 31}
            // here AND in three client files -- and it rejected the server's OWN
            // reference data: the service sets debitDay from the bank's
            // subscription row, and 13 of the 15 rows fall outside that set. This
            // validator runs on create, update AND updateBanking, so writing a
            // debit-order member back UNCHANGED was a 400 for most of them.
            Integer debitDay = member.getDebitDay();
            if (debitDay != null && !DebitDay.isInRange(debitDay)
                    && debitDayWasChanged(debitDay, previousDebitDay)) {
                throw new IllegalArgumentException(DebitDay.message());
            }
        }
    }

    /**
     * Whether this request CHOSE the debit day it is carrying.
     *
     * <p>🔴 R7-F2, and the reason this parameter exists. 38 of the 57 seeded
     * debit-order members are stored with a day outside the range, because the
     * seed took the banks' ACB days and those are placeholder data. The range was
     * checked on every write, so opening one of those members and saving it
     * <b>unchanged</b> was a 400 — naming a day the administrator never entered
     * and cannot correct from the screen. Two thirds of the population was
     * uneditable, and the error blamed the user for the seed.
     *
     * <p>🗣️ Settled by the owner on 2026-09-10: leave the seed, change the rule.
     * A value sent back unchanged was not chosen by this caller, so there is
     * nothing to refuse; the first deliberate change still has to land in range,
     * which is what the rule is for.
     *
     * <p>A {@code null} {@code previousDebitDay} means create: nothing was stored,
     * so every day is a new choice and the range binds all of them.
     */
    private static boolean debitDayWasChanged(Integer submitted, Integer previousDebitDay) {
        return !submitted.equals(previousDebitDay);
    }

    /**
     * The SASBO-structure answers must agree with each other.
     *
     * <p>🗣️ Stakeholder, 2026-09-17: <i>"For a member to participate in COSATU
     * structures, they have to be within the BEC structure."</i> And a COSATU Local
     * or Regional structure is only something a COSATU member sits on.
     *
     * <p>🔑 An explicit {@code isBec = false} beside COSATU is a contradiction and is
     * always refused. A {@code null} BEC answer is different: rows stored before V29
     * hold {@code is_cosatu = 'Y'} and no BEC answer at all, because the column did
     * not exist. Refusing those would make every such member unsaveable over a
     * question nobody could have answered -- decision ⑥'s lesson, one rule over. So
     * a null BEC answer is refused only when THIS request turns COSATU on.
     *
     * @param previousIsCosatu the stored {@code isCosatu} before this request, or
     *        {@code null} on create
     * @throws IllegalArgumentException naming the rule, for the screen to show
     */
    public static void validateStructures(Member member, String previousIsCosatu) {
        boolean cosatu = isYes(member.getIsCosatu());
        if (cosatu && !Boolean.TRUE.equals(member.getIsBec())) {
            boolean contradicted = Boolean.FALSE.equals(member.getIsBec());
            if (contradicted || !isYes(previousIsCosatu)) {
                throw new IllegalArgumentException(
                        "A member must be a BEC member to take part in COSATU structures");
            }
        }
        if (!cosatu && (Boolean.TRUE.equals(member.getIsCosatuLocal())
                || Boolean.TRUE.equals(member.getIsCosatuRegional()))) {
            throw new IllegalArgumentException(
                    "A COSATU Local or Regional structure can only be recorded for a COSATU member");
        }
    }

    /** The union's day, for "not in the future": SASBO works in South African time, as ACB does. */
    private static final ZoneId SASBO_ZONE = ZoneId.of("Africa/Johannesburg");

    /**
     * What a "Yes" to a seat needs beside it (V55). 🗣️ SASBO, through the owner, 2026-09-30:
     * <i>"Shop Steward (Yes) > Select Election Date. BEC Member (Yes) > Select Branch Committee."</i>
     *
     * <p>🔑 REQUIRED WHILE THE ANSWER IS YES -- except for a Yes already on record with nothing beside
     * it. Every member stored before V55 who is a shop steward or a BEC member has no date and no
     * committee, because there was no column to hold them. Refusing those would make each such member
     * unsaveable over a question nobody could answer -- {@link #validateStructures}' lesson, applied to
     * its two new neighbours. Once a follow-up is on record it can be changed, but not removed while the
     * answer stays Yes. An election date given is never in the future, wherever it came from.
     *
     * <p>The client's {@code structureErrors} ({@code utils/structures.js}) holds the same rule.
     * Clearing a follow-up when its answer is No is the service's, not this method's: this one only
     * refuses.
     *
     * @param legacyShopSteward a shop steward before this request, with no election date on record --
     *                          {@code false} on create
     * @param legacyBec         a BEC member before this request, with no committee on record -- likewise
     * @throws IllegalArgumentException naming what is missing, for the screen to show
     */
    public static void validateSeats(Member member, boolean legacyShopSteward, boolean legacyBec) {
        if (Boolean.TRUE.equals(member.getIsShopSteward())) {
            LocalDate elected = member.getShopStewardElectionDate();
            if (elected == null && !legacyShopSteward) {
                throw new IllegalArgumentException("A shop steward needs the date they were elected");
            }
            if (elected != null && elected.isAfter(LocalDate.now(SASBO_ZONE))) {
                throw new IllegalArgumentException("The shop steward election date cannot be in the future");
            }
        }
        if (Boolean.TRUE.equals(member.getIsBec()) && member.getBecCommittee() == null && !legacyBec) {
            throw new IllegalArgumentException("A BEC member needs the Branch Executive Committee they sit on");
        }
    }

    /**
     * A BEC member's roles (V55), from SASBO's structures tree, 2026-09-30: a Structure
     * Representative represents at least one {@link BecStructure}, and an Executive holds one
     * {@link BecExecutivePosition}. A value is matched by its name or SASBO's spelling, case and
     * surrounding spaces ignored, and written back in SASBO's spelling -- the structures in the
     * tree's order -- so the record holds one form of each.
     *
     * <p>No legacy exception: the columns are V55's own, so no member on record holds either Yes
     * without what it needs. Clearing a role when its answer is No is the service's.
     *
     * @throws IllegalArgumentException naming what is missing or not recognised, for the screen to show
     */
    public static void validateBecRoles(Member member) {
        if (Boolean.TRUE.equals(member.getIsBecStructureRep())) {
            List<String> named = inVocabulary(member.getBecStructures(), BecStructure.values(),
                    BecStructure::getDisplayName, List.of(), "one of SASBO's BEC structures");
            if (named.isEmpty()) {
                throw new IllegalArgumentException("A BEC structure representative needs the structures they represent");
            }
            member.setBecStructures(String.join(",", named));
        }
        if (Boolean.TRUE.equals(member.getIsBecExecutive())) {
            String position = member.getBecExecutivePosition();
            if (position == null || position.isBlank()) {
                throw new IllegalArgumentException("A BEC executive needs the position they hold");
            }
            member.setBecExecutivePosition(entryOf(position, BecExecutivePosition.values(),
                    BecExecutivePosition::getDisplayName)
                    .map(BecExecutivePosition::getDisplayName)
                    .orElseThrow(() -> new IllegalArgumentException(
                            "'" + position.trim() + "' is not a BEC executive position")));
        }
    }

    /**
     * COSATU positions -- V19's column, offered on Edit Member again with V55 and held to
     * {@link CosatuPosition}. A position given is stored in SASBO's spelling, in the tree's order.
     * One the vocabulary does not hold is refused only when THIS request adds it: the old record
     * wrote free text into this column (BR-14), and a member stored with it must stay saveable
     * unchanged -- decision ⑥'s rule, as for the debit day.
     *
     * @param previousPositions the stored positions before this request, or {@code null} on create
     * @throws IllegalArgumentException naming the value not recognised
     */
    public static void validateCosatuPositions(Member member, String previousPositions) {
        String submitted = member.getCosatuPositions();
        if (submitted == null || submitted.isBlank()) {
            return;
        }
        member.setCosatuPositions(String.join(",", inVocabulary(submitted, CosatuPosition.values(),
                CosatuPosition::getDisplayName, split(previousPositions), "a COSATU position")));
    }

    /** A comma-separated list's values, trimmed, blanks dropped. */
    private static List<String> split(String joined) {
        if (joined == null) {
            return List.of();
        }
        return Arrays.stream(joined.split(",")).map(String::trim).filter(value -> !value.isEmpty()).toList();
    }

    /**
     * A comma-separated list in the vocabulary's spelling and order, each value once, followed by
     * any value {@code kept} that the vocabulary does not hold, as it was.
     *
     * @throws IllegalArgumentException for a value neither the vocabulary nor {@code kept} holds
     */
    private static <E extends Enum<E>> List<String> inVocabulary(String joined, E[] vocabulary,
            Function<E, String> displayName, List<String> kept, String what) {
        List<E> named = new ArrayList<>();
        List<String> leftover = new ArrayList<>();
        for (String value : split(joined)) {
            Optional<E> entry = entryOf(value, vocabulary, displayName);
            if (entry.isPresent()) {
                named.add(entry.get());
            } else if (kept.contains(value)) {
                if (!leftover.contains(value)) {
                    leftover.add(value);
                }
            } else {
                throw new IllegalArgumentException("'" + value + "' is not " + what);
            }
        }
        List<String> ordered = new ArrayList<>();
        for (E entry : vocabulary) {
            if (named.contains(entry)) {
                ordered.add(displayName.apply(entry));
            }
        }
        ordered.addAll(leftover);
        return ordered;
    }

    /** The entry a value names -- its constant's name or its display name, case and surrounding spaces ignored. */
    private static <E extends Enum<E>> Optional<E> entryOf(String value, E[] vocabulary, Function<E, String> displayName) {
        String trimmed = value.trim();
        for (E entry : vocabulary) {
            if (entry.name().equalsIgnoreCase(trimmed) || displayName.apply(entry).equalsIgnoreCase(trimmed)) {
                return Optional.of(entry);
            }
        }
        return Optional.empty();
    }

    /** {@code isCosatu} is stored as Y/N (V19); older clients sent "true". */
    private static boolean isYes(String flag) {
        return flag != null && (flag.equalsIgnoreCase("Y") || flag.equalsIgnoreCase("true")
                || flag.equalsIgnoreCase("Yes"));
    }
}
