package za.co.titandynamix.sasbo_online_backend.acb;

import za.co.titandynamix.sasbo_online_backend.enums.AccountType;
import za.co.titandynamix.sasbo_online_backend.enums.AcbExclusionReason;
import za.co.titandynamix.sasbo_online_backend.enums.PaymentType;
import za.co.titandynamix.sasbo_online_backend.utils.DebitDay;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;

/**
 * Who a month's run debits, on which date, and for how much -- the population, date and amount
 * rules, as one pure computation over rows it is handed.
 *
 * <h2>Pure, and fed a member at a time</h2>
 * It reads no repository and no clock. The job pages members through {@link #accept} and asks for
 * the {@link Preparation} at the end, so the rules are testable as a table with no database, and
 * the job's statement count is whatever the paging costs -- never a query per member.
 *
 * <h2>The rules, in the order they are applied -- ONE reason per member, the first it fails</h2>
 * <ol>
 *   <li>payment type is Debit Order -- payroll collection is a different world entirely;</li>
 *   <li>status is one the run debits -- Active only, until SASBO says otherwise; case and stray
 *       spaces do not matter, and the raw value is what the report counts;</li>
 *   <li>the bank name matches exactly one bank on record;</li>
 *   <li>that bank has exactly one ACTIVE subscription row -- none excludes its members by name,
 *       two refuse the whole run before any member is read;</li>
 *   <li>the member's own debit day is in the window, the 20th to the 31st;</li>
 *   <li>the account number is one to eleven digits -- the width the bureau file gives it;</li>
 *   <li>the membership number and surname the file and letters print are present.</li>
 * </ol>
 *
 * <h2>The amount, and re-presentations</h2>
 * A member's own debit is the flat rate in force for the action month -- never the subscription
 * amount stored on the member. An earlier unpaid the union chose to present again becomes a SECOND
 * line for the member, on their own day, at the amount of the line it re-presents, beside the
 * month's own debit: never one combined amount. A member excluded this month has neither line, and
 * their re-presentation waits, unclaimed, for a month they can be debited in.
 *
 * <h2>One re-debit a month, unless the member agreed to more (D32)</h2>
 * The OLDEST unpaid month's re-presentation is automatic. Each further one is taken only when its
 * decision carries the member's permission; without it the unpaid is HELD OVER -- left unclaimed, so
 * it is offered again next month and listed on the run meanwhile -- never dropped, never an exclusion
 * (F46): the member is in the run. Three debits in one month on an account that has just bounced is
 * how a member reaches three unpaids in a fortnight (Q27).
 */
public final class RunPreparer {

    /** The statuses a run debits, compared case-insensitively. Active only, until SASBO answers. */
    public static final Set<String> DEBITED_STATUSES = Set.of("active");

    /** The bureau file's homing-account field is eleven digits wide. */
    public static final int ACCOUNT_DIGITS_MAX = 11;

    private static final Pattern DIGITS = Pattern.compile("[0-9]+");

    /** What a counted reason records for a value that was not there at all. */
    static final String BLANK = "(blank)";

    private final YearMonth month;
    private final DebitWindow window;
    private final BankIndex banks;
    private final BigDecimal rate;
    private final Map<Integer, List<RePresentationCandidate>> candidatesByMember;

    private final List<PreparedLine> lines = new ArrayList<>();
    private final List<Exclusion> exclusions = new ArrayList<>();
    private final Map<AcbExclusionReason, Map<String, Integer>> counted =
            new EnumMap<>(AcbExclusionReason.class);

    public RunPreparer(YearMonth month, DebitWindow window, BankIndex banks, BigDecimal rate,
                       Collection<RePresentationCandidate> candidates) {
        this.month = month;
        this.window = window;
        this.banks = banks;
        this.rate = rate;
        this.candidatesByMember = new HashMap<>();
        for (RePresentationCandidate candidate : candidates) {
            this.candidatesByMember
                    .computeIfAbsent(candidate.memberId(), id -> new ArrayList<>())
                    .add(candidate);
        }
    }

    /** Applies every rule to one member, and records a line, lines, or one exclusion. */
    public void accept(MemberDebitRow member) {
        Optional<Exclusion> failed = firstFailure(member);
        if (failed.isPresent()) {
            Exclusion exclusion = failed.get();
            if (exclusion.reason().listed()) {
                exclusions.add(exclusion);
            } else {
                counted.computeIfAbsent(exclusion.reason(), r -> new TreeMap<>())
                        .merge(exclusion.detail(), 1, Integer::sum);
            }
            return;
        }
        BankRow bank = banks.find(member.bankName()).orElseThrow();
        LocalDate actionDate = window.actionDate(month, member.debitDay());
        lines.add(line(member, bank, actionDate, rate, null));
        List<RePresentationCandidate> theirs = new ArrayList<>(candidatesByMember.getOrDefault(member.id(), List.of()));
        theirs.sort(Comparator.comparing(RePresentationCandidate::month).thenComparing(RePresentationCandidate::lineId));
        for (int i = 0; i < theirs.size(); i++) {
            RePresentationCandidate candidate = theirs.get(i);
            if (i == 0 || candidate.permitted()) {
                lines.add(line(member, bank, actionDate, candidate.amount(), candidate.lineId()));
            }
            // else held over: unclaimed, so offered again next month, and listed on the run (D32, F46)
        }
    }

    /** What was prepared: the lines, the members listed as excluded, and the counted ones. */
    public Preparation finish() {
        List<PreparedLine> sorted = new ArrayList<>(lines);
        sorted.sort(Comparator.comparing(PreparedLine::actionDate)
                .thenComparing(PreparedLine::memberId)
                .thenComparingLong(l -> l.rePresentsLineId() == null ? 0L : l.rePresentsLineId()));
        Map<AcbExclusionReason, Map<String, Integer>> copy = new EnumMap<>(AcbExclusionReason.class);
        counted.forEach((reason, byValue) -> copy.put(reason, Map.copyOf(byValue)));
        return new Preparation(List.copyOf(sorted), List.copyOf(exclusions), copy);
    }

    /** The first rule {@code member} fails, in the declared order, or empty when they are debited. */
    Optional<Exclusion> firstFailure(MemberDebitRow member) {
        if (!isDebitOrder(member.paymentType())) {
            return exclude(member, AcbExclusionReason.NOT_DEBIT_ORDER, raw(member.paymentType()));
        }
        if (!isDebitedStatus(member.status())) {
            return exclude(member, AcbExclusionReason.STATUS_NOT_ACTIVE, raw(member.status()));
        }
        Optional<BankRow> bank = banks.find(member.bankName());
        if (bank.isEmpty()) {
            String detail = banks.sharedByTwoBanks(member.bankName())
                    ? raw(member.bankName()) + " (matches more than one bank)"
                    : raw(member.bankName());
            return exclude(member, AcbExclusionReason.NO_BANK_ON_RECORD, detail);
        }
        if (banks.activeRowsFor(bank.get()) != 1) {
            // Only none reaches here: two refuse the run before any member is read.
            return exclude(member, AcbExclusionReason.NO_SUBSCRIPTION_ROW, bank.get().name());
        }
        if (!isDayInWindow(member.debitDay())) {
            return exclude(member, AcbExclusionReason.DAY_OUT_OF_WINDOW,
                    member.debitDay() == null ? BLANK : String.valueOf(member.debitDay()));
        }
        Optional<String> accountProblem = accountShapeProblem(member.bankAccountNumber());
        if (accountProblem.isPresent()) {
            return exclude(member, AcbExclusionReason.ACCOUNT_SHAPE_INVALID, accountProblem.get());
        }
        Optional<String> snapshotProblem = snapshotProblem(member);
        if (snapshotProblem.isPresent()) {
            return exclude(member, AcbExclusionReason.SNAPSHOT_INCOMPLETE, snapshotProblem.get());
        }
        return Optional.empty();
    }

    // ------------------------------------------------------------------------------------------
    // The rules, one method each
    // ------------------------------------------------------------------------------------------

    /** Rule 1. "Debit Order" as the member screen stores it, or the enum's own name. */
    static boolean isDebitOrder(String paymentType) {
        if (paymentType == null) {
            return false;
        }
        String value = paymentType.trim();
        return value.equalsIgnoreCase(PaymentType.DEBIT_ORDER.getDisplayName())
                || value.equalsIgnoreCase(PaymentType.DEBIT_ORDER.name());
    }

    /** Rule 2. Case and surrounding spaces do not matter; a misspelling is excluded and counted. */
    static boolean isDebitedStatus(String status) {
        return status != null && DEBITED_STATUSES.contains(status.trim().toLowerCase(Locale.ROOT));
    }

    /** Rule 5. The member's own debit day, the 20th to the 31st. */
    static boolean isDayInWindow(Integer debitDay) {
        return debitDay != null && DebitDay.isInRange(debitDay);
    }

    /**
     * Rule 6. What is wrong with an account number's SHAPE, or empty when nothing is. Says what
     * kind of problem it is and how long the value is -- 🔒 never the number itself, because this
     * detail is stored in the report and shown on screen.
     *
     * <p>Shape only. Whether the number passes the bank's own check-digit rule is a question for
     * the banks, and the bank's returns report is where an invalid account is confirmed today.
     */
    static Optional<String> accountShapeProblem(String accountNumber) {
        String value = accountNumber == null ? "" : accountNumber.trim();
        if (value.isEmpty()) {
            return Optional.of("blank");
        }
        if (!DIGITS.matcher(value).matches()) {
            return Optional.of("contains characters other than digits");
        }
        if (value.length() > ACCOUNT_DIGITS_MAX) {
            return Optional.of(value.length() + " digits -- the file carries at most "
                    + ACCOUNT_DIGITS_MAX);
        }
        return Optional.empty();
    }

    /**
     * Rule 7. The file and the letters print the membership number and the surname, and the file
     * prints the account type (00.5); a line without them cannot be written. A member captured
     * before V49 has no type, and is named here rather than guessed at.
     */
    static Optional<String> snapshotProblem(MemberDebitRow member) {
        if (isBlank(member.membershipNumber())) {
            return Optional.of("no membership number");
        }
        if (isBlank(member.surname())) {
            return Optional.of("no surname");
        }
        if (isBlank(member.accountType())) {
            return Optional.of("no account type");
        }
        try {
            AccountType.fromString(member.accountType());
        } catch (IllegalArgumentException unknown) {
            return Optional.of("account type " + raw(member.accountType())
                    + " is not Cheque, Savings or Transmission");
        }
        return Optional.empty();
    }

    // ------------------------------------------------------------------------------------------

    private PreparedLine line(MemberDebitRow member, BankRow bank, LocalDate actionDate,
                              BigDecimal amount, Long rePresentsLineId) {
        return new PreparedLine(
                member.id(),
                rePresentsLineId,
                member.membershipNumber().trim(),
                member.surname().trim(),
                isBlank(member.initials()) ? null : member.initials().trim(),
                bank.name(),
                bank.universalBranchCode(),
                member.bankAccountNumber().trim(),
                // The digit the file prints (00.5). Rule 7 has already refused a member without one.
                AccountType.fromString(member.accountType()).getCode(),
                member.debitDay(),
                actionDate,
                amount);
    }

    private static Optional<Exclusion> exclude(MemberDebitRow member, AcbExclusionReason reason,
                                               String detail) {
        return Optional.of(new Exclusion(member.id(), reason, detail));
    }

    private static String raw(String value) {
        if (isBlank(value)) {
            return BLANK;
        }
        String trimmed = value.trim();
        return trimmed.length() > 200 ? trimmed.substring(0, 200) : trimmed;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    // ------------------------------------------------------------------------------------------
    // What comes out
    // ------------------------------------------------------------------------------------------

    /**
     * One debit to write. 🔒 Carries an account number, so {@code toString()} is written by hand.
     *
     * @param rePresentsLineId the earlier unpaid this line presents again, or {@code null} for the
     *                         member's own debit this month
     * @param accountType      1, 2 or 3 as the file prints it, from the member's account type (00.5)
     */
    public record PreparedLine(
            Integer memberId,
            Long rePresentsLineId,
            String membershipNumber,
            String surname,
            String initials,
            String bankName,
            String branchCode,
            String accountNumber,
            Integer accountType,
            int debitDay,
            LocalDate actionDate,
            BigDecimal amount) {

        @Override
        public String toString() {
            return "PreparedLine[member=" + memberId + ", rePresents=" + rePresentsLineId
                    + ", actionDate=" + actionDate + ", amount=" + amount + "]";
        }
    }

    /** One member not debited, with the first rule they failed and what made it fail. */
    public record Exclusion(Integer memberId, AcbExclusionReason reason, String detail) {
    }

    /**
     * The run's content, before it is written.
     *
     * @param counted for the two counted reasons, how many members each raw value excluded
     */
    public record Preparation(
            List<PreparedLine> lines,
            List<Exclusion> exclusions,
            Map<AcbExclusionReason, Map<String, Integer>> counted) {

        public int lineCount() {
            return lines.size();
        }

        /** The control total, derived from the lines -- never carried separately. */
        public BigDecimal total() {
            return lines.stream()
                    .map(PreparedLine::amount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add)
                    .setScale(2);
        }

        public Optional<LocalDate> firstActionDate() {
            return lines.stream().map(PreparedLine::actionDate).min(Comparator.naturalOrder());
        }

        public Optional<LocalDate> lastActionDate() {
            return lines.stream().map(PreparedLine::actionDate).max(Comparator.naturalOrder());
        }

        /** How many members one reason excluded, listed or counted. */
        public int excludedFor(AcbExclusionReason reason) {
            if (reason.listed()) {
                return (int) exclusions.stream().filter(e -> e.reason() == reason).count();
            }
            return counted.getOrDefault(reason, Map.of()).values().stream()
                    .mapToInt(Integer::intValue).sum();
        }
    }
}
