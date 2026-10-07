package za.co.titandynamix.sasbo_online_backend.payroll;

import za.co.titandynamix.sasbo_online_backend.enums.PayrollAmountStatus;
import za.co.titandynamix.sasbo_online_backend.enums.PayrollConfidence;
import za.co.titandynamix.sasbo_online_backend.enums.PayrollDiscrepancyType;
import za.co.titandynamix.sasbo_online_backend.enums.PayrollMatchRule;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

/**
 * The comparison of one employer's file with SASBO's members, in one pass over indexed maps -- never a query per line.
 * Its inputs are read in a handful of statements ({@code PayrollStore}); its output is written in batches.
 *
 * <h2>Who a line is (Stephne, PAY-10: staff number, ID number, "Initials and surname")</h2>
 * <ol>
 *   <li>The staff number, among the employer's members -- a staff number is the employer's, and means nothing at
 *       another. Where several members share it (Nedbank prints a code its staff have in common), the name on the
 *       line picks the one it fits: by initials and surname, else by the surname alone, the initials then flagged.</li>
 *   <li>The ID number, among every member on record.</li>
 *   <li>Initials and surname, among the employer's members, and only where exactly one fits -- preferring, where
 *       several fit, the one member who pays by payroll and has not left.</li>
 *   <li>A name alone with no number on the line (GBS's letter, SA Reserve Bank's picture), whose initials fit nobody:
 *       paired with a member who would otherwise be missing, only where the surname is theirs alone -- no other member
 *       of the employer has it, and no other line left over does -- and the initials flagged for a person to check.</li>
 * </ol>
 * Whatever the rule, the line's other details are set against the member's: a different employee number, or a name
 * that does not match the ID number or the employee number, is a discrepancy of its own (PAY-19), as is a different ID
 * number or employer on record (PAY-17). A detail missing on either side is not a difference.
 *
 * <h2>What a member's lines say (PAY-16, PAY-18, PAY-23, PAY-25, PAY-26)</h2>
 * A member's lines are taken together. One line of the month's subscription matches. One larger line that is a whole
 * number of months, each month priced at its own rate, is a catch-up where the member has months owed -- missed months
 * on earlier files, or arrears on the record -- and the wrong amount where none is owed. Two or more lines are a
 * catch-up where the file marks the earlier months (Absa's and Nedbank's For-period), a POSSIBLE catch-up where the
 * member has months owed (PAY-26 asks how to tell; until Stephne says, a person confirms it), and a member deducted
 * twice -- refunded directly -- where none is. 🔒 A month with no rate on record is never priced at another month's:
 * the line is {@link PayrollAmountStatus#RATE_UNKNOWN}, naming the month.
 *
 * <h2>Who is missing (PAY-14, PAY-15)</h2>
 * Every member of the employer who pays by payroll, has not left, and had joined by the end of the month, and whom no
 * line fits -- with how many months in a row they have been missing.
 */
public final class PayrollMatcher {

    private static final BigDecimal CENT = new BigDecimal("0.01");

    /** What a line paired by the surname alone says of its match. */
    static final String SURNAME_ALONE = "Matched by the surname alone: the only member of this employer with it, and no "
            + "other line of the file has it; the initials differ";

    /** A member as the comparison needs them: who, how they are known, how they pay, and what they owe. */
    public record Member(int id, String membershipNumber, String staffKey, String idKey, String initials,
                         String surname, boolean leaving, boolean payroll, boolean debitOrder, boolean atEmployer,
                         LocalDate joined, BigDecimal arrears) {

        String surnameKey() {
            return PayrollNames.surnameKey(surname);
        }

        /** Initials and surname, as a list shows a member: {@code B.K. Testerson}. */
        public String displayName() {
            return PayrollMatcher.displayName(initials, surname);
        }

        @Override
        public String toString() {
            return "Member[" + id + "]";
        }
    }

    /** What the comparison knows of the months before this one, per member: missed months, and months caught up. */
    public record History(int missedMonths, int caughtUpMonths, int missingInARow) {

        public static final History NONE = new History(0, 0, 0);

        /** The months still owed, as earlier files showed them. */
        int owed() {
            return Math.max(0, missedMonths - caughtUpMonths);
        }
    }

    /** What the comparison needs besides the lines. */
    public record Inputs(YearMonth period, Collection<Member> employerMembers, Map<String, Member> byIdNumber,
                         Function<YearMonth, Optional<BigDecimal>> rate, Map<Integer, History> history,
                         String employerName) {
    }

    /** One line's outcome: the member (or none), the rule, how sure, why, and what its amount says. */
    public record LineMatch(int lineNumber, Member member, PayrollMatchRule rule, PayrollConfidence confidence,
                            String reason, BigDecimal expectedAmount, PayrollAmountStatus amountStatus,
                            Integer months, YearMonth rateUnknownFor) {
    }

    /**
     * A discrepancy found. {@code subjectKey} is what it is about -- {@code M<member id>}, {@code L<line>} or
     * {@code F} -- so a re-run finds it again. {@code detail} is written from amounts, months and line numbers, never
     * from a person's details.
     */
    public record Found(PayrollDiscrepancyType type, String subjectKey, Integer memberId, Integer lineNumber,
                        BigDecimal amountOnFile, BigDecimal expectedAmount, BigDecimal difference, Integer months,
                        String detail) {
    }

    /** Everything the comparison found, with the figures the file's summary shows. */
    public record Result(List<LineMatch> matches, List<Found> found, int matched, int byStaffNumber, int byIdNumber,
                         int byName, int notMembers, int missing, int catchUps, int listedTwice, int rateUnknown,
                         BigDecimal fileTotal, BigDecimal expectedTotal, Optional<BigDecimal> rate) {
    }

    private PayrollMatcher() {
    }

    public static Result match(List<PayrollParser.Line> lines, Inputs inputs) {
        Index index = new Index(inputs.employerMembers());
        Optional<BigDecimal> rate = inputs.rate().apply(inputs.period());
        List<LineMatch> matches = new ArrayList<>();
        List<Found> found = new ArrayList<>();
        Map<Integer, List<PayrollParser.Line>> linesOf = new LinkedHashMap<>();
        Map<Integer, Member> matchedMembers = new HashMap<>();
        Map<Integer, Identified> identified = new HashMap<>();
        int byStaff = 0;
        int byId = 0;
        int byName = 0;
        int notMembers = 0;
        BigDecimal fileTotal = BigDecimal.ZERO.setScale(2);
        List<PayrollParser.Line> leftOver = new ArrayList<>();

        for (PayrollParser.Line line : lines) {
            if (!line.valid()) {
                found.add(new Found(PayrollDiscrepancyType.UNREADABLE_ROW, "L" + line.lineNumber(), null,
                        line.lineNumber(), line.amount(), null, null, null,
                        "Line " + line.lineNumber() + ": " + line.problem()));
                continue;
            }
            fileTotal = fileTotal.add(line.amount());
            Identified who = identify(line, index, inputs.byIdNumber());
            if (who == null) {
                leftOver.add(line);
                continue;
            }
            switch (who.rule()) {
                case STAFF_NUMBER -> byStaff++;
                case ID_NUMBER -> byId++;
                default -> byName++;
            }
            identified.put(line.lineNumber(), who);
            linesOf.computeIfAbsent(who.member().id(), id -> new ArrayList<>()).add(line);
            matchedMembers.put(who.member().id(), who.member());
        }

        Map<Integer, Member> bySurnameAlone = pairBySurnameAlone(leftOver, inputs, matchedMembers);
        for (PayrollParser.Line line : leftOver) {
            Member alone = bySurnameAlone.get(line.lineNumber());
            if (alone != null) {
                byName++;
                identified.put(line.lineNumber(), new Identified(alone, PayrollMatchRule.NAME, PayrollConfidence.LOW,
                        SURNAME_ALONE, false, false, !PayrollNames.fits(line.name(), alone.initials(), alone.surname())));
                linesOf.computeIfAbsent(alone.id(), id -> new ArrayList<>()).add(line);
                matchedMembers.put(alone.id(), alone);
                continue;
            }
            notMembers++;
            found.add(new Found(PayrollDiscrepancyType.NOT_A_MEMBER, "L" + line.lineNumber(), null,
                    line.lineNumber(), line.amount(), null, null, null,
                    "Line " + line.lineNumber() + ": " + money(line.amount())
                            + " deducted from someone who is not a member of SASBO."));
            matches.add(new LineMatch(line.lineNumber(), null, PayrollMatchRule.NONE, null,
                    index.ambiguousName(line) ? "More than one member of this employer has this name, and "
                            + "nothing else on the line tells them apart." : "No member has this staff number, ID "
                            + "number, or initials and surname.", null, null, null, null));
        }

        int catchUps = 0;
        int listedTwice = 0;
        int rateUnknown = 0;
        BigDecimal expectedTotal = rate.isPresent() ? BigDecimal.ZERO.setScale(2) : null;
        for (Map.Entry<Integer, List<PayrollParser.Line>> entry : linesOf.entrySet()) {
            Member member = matchedMembers.get(entry.getKey());
            List<PayrollParser.Line> own = entry.getValue();
            PayrollParser.Line first = own.get(0);
            Identified who = identified.get(first.lineNumber());
            found.addAll(detailsDiffer(member, who, first, inputs.employerName()));
            if (member.leaving()) {
                found.add(found(PayrollDiscrepancyType.MEMBERSHIP_ENDED, member, first, total(own), null,
                        "Line " + first.lineNumber() + ": still deducted although the membership has ended."));
            }
            if (member.debitOrder()) {
                found.add(found(PayrollDiscrepancyType.DEBIT_ORDER_MEMBER, member, first, total(own), null,
                        "Line " + first.lineNumber() + ": deducted by the employer, but the member pays by debit "
                                + "order."));
            }
            AmountCheck check = checkAmount(member, own, inputs);
            switch (check.status()) {
                case CATCH_UP -> catchUps++;
                case LISTED_TWICE -> listedTwice++;
                case RATE_UNKNOWN -> rateUnknown++;
                default -> { }
            }
            if (check.found() != null) {
                found.add(check.found());
            }
            if (expectedTotal != null && member.payroll() && !member.leaving()) {
                expectedTotal = expectedTotal.add(check.expected() == null ? rate.get() : check.expected());
            }
            for (PayrollParser.Line line : own) {
                Identified lineWho = identified.get(line.lineNumber());
                matches.add(new LineMatch(line.lineNumber(), member, lineWho.rule(), lineWho.confidence(),
                        lineWho.reason(), check.expected(), check.status(), check.months(), check.rateUnknownFor()));
            }
        }

        int missing = 0;
        for (Member member : inputs.employerMembers()) {
            if (!member.payroll() || member.leaving() || matchedMembers.containsKey(member.id())
                    || (member.joined() != null && member.joined().isAfter(inputs.period().atEndOfMonth()))) {
                continue;
            }
            missing++;
            History history = inputs.history().getOrDefault(member.id(), History.NONE);
            int inARow = history.missingInARow() + 1;
            if (expectedTotal != null) {
                expectedTotal = expectedTotal.add(rate.get());
            }
            String months = inARow == 1 ? "" : " Missing " + inARow + " months in a row" + (inARow >= 3
                    ? ": due for deletion after three months without a deduction (PAY-15)." : ".");
            found.add(new Found(PayrollDiscrepancyType.MISSING_FROM_FILE, "M" + member.id(), member.id(), null, null,
                    rate.orElse(null), rate.map(BigDecimal::negate).orElse(null), inARow,
                    "Not on the file for " + monthName(inputs.period()) + "." + months));
        }

        matches.sort(java.util.Comparator.comparingInt(LineMatch::lineNumber));
        int matched = byStaff + byId + byName;
        return new Result(matches, found, matched, byStaff, byId, byName, notMembers, missing, catchUps, listedTwice,
                rateUnknown, fileTotal, expectedTotal, rate);
    }

    // ------------------------------------------------------------------------------------------------ identifying

    private record Identified(Member member, PayrollMatchRule rule, PayrollConfidence confidence, String reason,
                              boolean staffDiffers, boolean idDiffers, boolean nameDiffers) {
    }

    /** The employer's members, indexed by staff number and by surname. */
    private static final class Index {

        private final Map<String, List<Member>> byStaff = new HashMap<>();
        private final Map<String, List<Member>> bySurname = new HashMap<>();

        Index(Collection<Member> members) {
            for (Member member : members) {
                if (member.staffKey() != null) {
                    byStaff.computeIfAbsent(member.staffKey(), key -> new ArrayList<>()).add(member);
                }
                String surname = member.surnameKey();
                if (!surname.isEmpty()) {
                    bySurname.computeIfAbsent(surname, key -> new ArrayList<>()).add(member);
                }
            }
        }

        Member byStaff(PayrollParser.Line line) {
            List<Member> found = line.staffKey() == null ? List.of() : byStaff.getOrDefault(line.staffKey(), List.of());
            Member one = single(found, line);
            if (one != null || found.size() < 2 || line.name().isEmpty()) {
                return one;
            }
            // A number several members share: the name on the line picks the one it fits, by initials and surname --
            // else by the surname alone, when one alone has it; the initials are then flagged as differing (PAY-19).
            List<Member> fitting = found.stream()
                    .filter(m -> PayrollNames.fits(line.name(), m.initials(), m.surname())).toList();
            if (!fitting.isEmpty()) {
                return fitting.size() == 1 ? fitting.get(0) : null;
            }
            List<Member> sameSurname = found.stream().filter(m -> !m.surnameKey().isEmpty() && line.name().readings()
                    .stream().anyMatch(reading -> reading.surnameKey().equals(m.surnameKey()))).toList();
            return sameSurname.size() == 1 ? sameSurname.get(0) : null;
        }

        Member byName(PayrollParser.Line line) {
            if (line.name().isEmpty()) {
                return null;
            }
            return single(fittingName(line), line);
        }

        boolean ambiguousName(PayrollParser.Line line) {
            return !line.name().isEmpty() && fittingName(line).size() > 1 && single(fittingName(line), line) == null;
        }

        private List<Member> fittingName(PayrollParser.Line line) {
            Set<Member> fits = new java.util.LinkedHashSet<>();
            for (PayrollNames.Reading reading : line.name().readings()) {
                if (reading.initials() == null || reading.initials().isEmpty()) {
                    continue;
                }
                for (Member member : bySurname.getOrDefault(reading.surnameKey(), List.of())) {
                    if (PayrollNames.initialsFit(reading.initials(), member.initials())) {
                        fits.add(member);
                    }
                }
            }
            return new ArrayList<>(fits);
        }

        /** One member of several: the only one, or the only one who pays by payroll and has not left. */
        private static Member single(List<Member> found, PayrollParser.Line line) {
            if (found.size() == 1) {
                return found.get(0);
            }
            if (found.size() > 1) {
                List<Member> current = found.stream().filter(m -> m.payroll() && !m.leaving()).toList();
                if (current.size() == 1) {
                    return current.get(0);
                }
                List<Member> sameId = line.idKey() == null ? List.of()
                        : found.stream().filter(m -> line.idKey().equals(m.idKey())).toList();
                if (sameId.size() == 1) {
                    return sameId.get(0);
                }
            }
            return null;
        }
    }

    /**
     * Lines left over that carry a name alone -- no staff number, no ID number -- paired with members who would otherwise
     * be missing, where the surname is theirs alone on both sides: one member of the employer has it, and one line left
     * over claims that member. A line or a member with any rival is left as it was: not a member, and missing.
     */
    private static Map<Integer, Member> pairBySurnameAlone(List<PayrollParser.Line> leftOver, Inputs inputs,
                                                           Map<Integer, Member> matchedMembers) {
        Map<String, List<Member>> namesakes = new HashMap<>();
        for (Member member : inputs.employerMembers()) {
            if (!member.surnameKey().isEmpty()) {
                namesakes.computeIfAbsent(member.surnameKey(), key -> new ArrayList<>()).add(member);
            }
        }
        Map<Integer, Member> candidates = new LinkedHashMap<>();
        Map<Integer, Integer> claims = new HashMap<>();
        for (PayrollParser.Line line : leftOver) {
            if (line.staffKey() != null || line.idKey() != null || line.name().isEmpty()) {
                continue;
            }
            Set<Member> members = new HashSet<>();
            for (PayrollNames.Reading reading : line.name().readings()) {
                List<Member> same = namesakes.getOrDefault(reading.surnameKey(), List.of());
                if (same.size() == 1) {
                    members.add(same.get(0));
                }
            }
            members.forEach(member -> claims.merge(member.id(), 1, Integer::sum));
            boolean hasInitials = line.name().readings().stream()
                    .anyMatch(reading -> reading.initials() != null && !reading.initials().isEmpty());
            if (members.size() == 1 && hasInitials) {
                candidates.put(line.lineNumber(), members.iterator().next());
            }
        }
        LocalDate monthEnd = inputs.period().atEndOfMonth();
        Map<Integer, Member> paired = new HashMap<>();
        candidates.forEach((lineNumber, member) -> {
            boolean expected = member.payroll() && !member.leaving()
                    && (member.joined() == null || !member.joined().isAfter(monthEnd));
            if (expected && !matchedMembers.containsKey(member.id()) && claims.get(member.id()) == 1) {
                paired.put(lineNumber, member);
            }
        });
        return paired;
    }

    private static Identified identify(PayrollParser.Line line, Index index, Map<String, Member> byIdNumber) {
        Member member = index.byStaff(line);
        PayrollMatchRule rule = PayrollMatchRule.STAFF_NUMBER;
        if (member == null && line.idKey() != null) {
            member = byIdNumber.get(line.idKey());
            rule = PayrollMatchRule.ID_NUMBER;
        }
        if (member == null) {
            member = index.byName(line);
            rule = PayrollMatchRule.NAME;
        }
        if (member == null) {
            return null;
        }
        boolean staffAgrees = line.staffKey() != null && line.staffKey().equals(member.staffKey());
        boolean staffDiffers = line.staffKey() != null && member.staffKey() != null && !staffAgrees;
        boolean idAgrees = line.idKey() != null && line.idKey().equals(member.idKey());
        boolean idDiffers = line.idKey() != null && member.idKey() != null && !idAgrees;
        boolean nameAgrees = !line.name().isEmpty() && PayrollNames.fits(line.name(), member.initials(),
                member.surname());
        boolean nameDiffers = !line.name().isEmpty() && member.surname() != null && !nameAgrees;
        int agree = (staffAgrees ? 1 : 0) + (idAgrees ? 1 : 0) + (nameAgrees ? 1 : 0);
        int differ = (staffDiffers ? 1 : 0) + (idDiffers ? 1 : 0) + (nameDiffers ? 1 : 0);
        PayrollConfidence confidence;
        if (agree >= 2 && differ == 0) {
            confidence = PayrollConfidence.HIGH;
        } else if (agree >= 2 || (agree == 1 && differ == 0 && rule != PayrollMatchRule.NAME)) {
            confidence = PayrollConfidence.MEDIUM;
        } else {
            confidence = PayrollConfidence.LOW;
        }
        return new Identified(member, rule, confidence, reason(rule, staffAgrees, idAgrees, nameAgrees, staffDiffers,
                idDiffers, nameDiffers), staffDiffers, idDiffers, nameDiffers);
    }

    /** Why a line was matched, in words: the rule, what else agrees, and what differs. */
    static String reason(PayrollMatchRule rule, boolean staffAgrees, boolean idAgrees, boolean nameAgrees,
                         boolean staffDiffers, boolean idDiffers, boolean nameDiffers) {
        String by = switch (rule) {
            case STAFF_NUMBER -> "the staff number";
            case ID_NUMBER -> "the ID number";
            default -> "initials and surname: the only member of this employer with that name";
        };
        List<String> agrees = new ArrayList<>();
        if (rule != PayrollMatchRule.ID_NUMBER && idAgrees) {
            agrees.add("the ID number");
        }
        if (rule != PayrollMatchRule.STAFF_NUMBER && staffAgrees) {
            agrees.add("the staff number");
        }
        if (rule != PayrollMatchRule.NAME && nameAgrees) {
            agrees.add("the name");
        }
        List<String> differs = new ArrayList<>();
        if (staffDiffers) {
            differs.add("the employee number");
        }
        if (idDiffers) {
            differs.add("the ID number");
        }
        if (nameDiffers) {
            differs.add("the name");
        }
        StringBuilder text = new StringBuilder("Matched by ").append(by);
        if (!agrees.isEmpty()) {
            text.append("; ").append(String.join(" and ", agrees)).append(agrees.size() == 1 ? " agrees" : " agree");
        }
        if (!differs.isEmpty()) {
            text.append("; ").append(String.join(" and ", differs)).append(differs.size() == 1 ? " differs" : " differ");
        }
        return text.append('.').toString();
    }

    /** PAY-19 and PAY-17: a different employee number, a name that does not match, a different ID or employer. */
    private static List<Found> detailsDiffer(Member member, Identified who, PayrollParser.Line line, String employer) {
        List<Found> found = new ArrayList<>();
        String at = "Line " + line.lineNumber() + ": ";
        if (who.staffDiffers()) {
            found.add(found(PayrollDiscrepancyType.EMPLOYEE_NUMBER_DIFFERS, member, line, null, null,
                    at + "the employee number on the file differs from the one on record (matched by "
                            + (who.rule() == PayrollMatchRule.ID_NUMBER ? "ID number" : "initials and surname") + ")."));
        }
        if (who.nameDiffers()) {
            found.add(found(PayrollDiscrepancyType.NAME_DIFFERS, member, line, null, null,
                    at + (who.rule() == PayrollMatchRule.NAME
                            ? "the initials on the file differ from the member's on record (matched by the surname "
                                    + "alone: the only member of this employer with it)."
                            : "the name on the file does not match the "
                                    + (who.rule() == PayrollMatchRule.ID_NUMBER ? "ID number" : "employee number")
                                    + " on record.")));
        }
        List<String> details = new ArrayList<>();
        if (who.idDiffers()) {
            details.add("the ID number differs from the one on record");
        }
        // A debit-order member's employer text is the bank that holds their account (ACB's key), not their employer.
        if (!member.atEmployer() && !member.debitOrder()) {
            details.add("the member's employer on record is not " + employer);
        }
        if (!details.isEmpty()) {
            found.add(found(PayrollDiscrepancyType.DETAILS_DIFFER, member, line, null, null,
                    at + String.join("; ", details) + "."));
        }
        return found;
    }

    // --------------------------------------------------------------------------------------------------- amounts

    private record AmountCheck(PayrollAmountStatus status, BigDecimal expected, Integer months, YearMonth rateUnknownFor,
                               Found found) {
    }

    private static AmountCheck checkAmount(Member member, List<PayrollParser.Line> own, Inputs inputs) {
        YearMonth period = inputs.period();
        Optional<BigDecimal> rate = inputs.rate().apply(period);
        PayrollParser.Line first = own.get(0);
        BigDecimal total = total(own);
        if (rate.isEmpty()) {
            return new AmountCheck(PayrollAmountStatus.RATE_UNKNOWN, null, null, period, null);
        }
        History history = inputs.history().getOrDefault(member.id(), History.NONE);
        int owed = Math.max(history.owed(), arrearsMonths(member.arrears(), rate.get()));
        if (own.size() > 1) {
            return listedMoreThanOnce(member, own, inputs, total, owed);
        }
        BigDecimal amount = first.amount();
        if (sameMoney(amount, rate.get())) {
            return new AmountCheck(PayrollAmountStatus.MATCHES, rate.get(), 1, null, null);
        }
        if (amount.compareTo(rate.get()) > 0) {
            BigDecimal sum = rate.get();
            for (int k = 2; k <= PayrollLimits.MAX_CATCH_UP_MONTHS; k++) {
                YearMonth month = period.minusMonths(k - 1L);
                Optional<BigDecimal> monthRate = inputs.rate().apply(month);
                if (monthRate.isEmpty()) {
                    // 🔒 Never priced at another month's rate: what the earlier months come to is not known.
                    return new AmountCheck(PayrollAmountStatus.RATE_UNKNOWN, null, null, month, null);
                }
                sum = sum.add(monthRate.get());
                if (sameMoney(amount, sum)) {
                    if (k - 1 <= owed) {
                        return new AmountCheck(PayrollAmountStatus.CATCH_UP, sum, k, null, null);
                    }
                    return new AmountCheck(PayrollAmountStatus.DIFFERS, rate.get(), k, null,
                            found(PayrollDiscrepancyType.AMOUNT_DIFFERS, member, first, amount, rate.get(),
                                    "Line " + first.lineNumber() + ": " + money(amount) + " is " + k + " months' "
                                            + "subscription, but no missed month is on record; " + money(rate.get())
                                            + " expected."));
                }
                if (amount.compareTo(sum) < 0) {
                    break;
                }
            }
        }
        return new AmountCheck(PayrollAmountStatus.DIFFERS, rate.get(), 1, null,
                found(PayrollDiscrepancyType.AMOUNT_DIFFERS, member, first, amount, rate.get(),
                        "Line " + first.lineNumber() + ": " + money(amount) + " deducted; " + money(rate.get())
                                + " expected: " + money(amount.subtract(rate.get()).abs()) + " "
                                + (amount.compareTo(rate.get()) < 0 ? "short." : "over.")));
    }

    /** Two or more lines for one member: marked by the file as earlier months, possibly a catch-up, or twice. */
    private static AmountCheck listedMoreThanOnce(Member member, List<PayrollParser.Line> own, Inputs inputs,
                                                  BigDecimal total, int owed) {
        YearMonth period = inputs.period();
        PayrollParser.Line first = own.get(0);
        Set<YearMonth> months = new HashSet<>();
        boolean marked = true;
        for (PayrollParser.Line line : own) {
            YearMonth month = line.forPeriod() == null ? period : line.forPeriod();
            marked &= !month.isAfter(period) && months.add(month);
        }
        String lineNumbers = lineNumbers(own);
        if (marked && months.size() == own.size() && months.stream().anyMatch(m -> m.isBefore(period))) {
            BigDecimal expected = BigDecimal.ZERO.setScale(2);
            for (YearMonth month : months) {
                Optional<BigDecimal> monthRate = inputs.rate().apply(month);
                if (monthRate.isEmpty()) {
                    return new AmountCheck(PayrollAmountStatus.RATE_UNKNOWN, null, own.size(), month, null);
                }
                expected = expected.add(monthRate.get());
            }
            if (sameMoney(total, expected)) {
                return new AmountCheck(PayrollAmountStatus.CATCH_UP, expected, own.size(), null, null);
            }
            return new AmountCheck(PayrollAmountStatus.DIFFERS, expected, own.size(), null,
                    found(PayrollDiscrepancyType.AMOUNT_DIFFERS, member, first, total, expected,
                            "Lines " + lineNumbers + ": " + money(total) + " for " + own.size() + " months marked on "
                                    + "the file; " + money(expected) + " expected."));
        }
        BigDecimal rate = inputs.rate().apply(period).orElseThrow();
        PayrollDiscrepancyType type = owed >= own.size() - 1 ? PayrollDiscrepancyType.POSSIBLE_CATCH_UP
                : PayrollDiscrepancyType.DEDUCTED_TWICE;
        String detail = type == PayrollDiscrepancyType.POSSIBLE_CATCH_UP
                ? "Lines " + lineNumbers + ": listed " + times(own.size()) + " (" + money(total) + "), and "
                        + owed + (owed == 1 ? " month is" : " months are") + " owed: confirm it is a catch-up."
                : "Lines " + lineNumbers + ": deducted " + times(own.size()) + " (" + money(total) + ") with no "
                        + "missed month on record: refund " + money(total.subtract(rate)) + " to the member.";
        Found twice = new Found(type, "M" + member.id(), member.id(), first.lineNumber(), total, rate,
                total.subtract(rate), own.size(), detail);
        return new AmountCheck(PayrollAmountStatus.LISTED_TWICE, rate, own.size(), null, twice);
    }

    // --------------------------------------------------------------------------------------------------- helpers

    private static Found found(PayrollDiscrepancyType type, Member member, PayrollParser.Line line,
                               BigDecimal amountOnFile, BigDecimal expected, String detail) {
        BigDecimal difference = amountOnFile != null && expected != null ? amountOnFile.subtract(expected) : null;
        return new Found(type, "M" + member.id(), member.id(), line.lineNumber(), amountOnFile, expected, difference,
                null, detail);
    }

    private static BigDecimal total(List<PayrollParser.Line> lines) {
        return lines.stream().map(PayrollParser.Line::amount).reduce(BigDecimal.ZERO.setScale(2), BigDecimal::add);
    }

    /** Whole months of the subscription the arrears on the record come to. */
    static int arrearsMonths(BigDecimal arrears, BigDecimal rate) {
        if (arrears == null || rate == null || arrears.signum() <= 0 || rate.signum() <= 0) {
            return 0;
        }
        return arrears.add(CENT).divide(rate, 0, RoundingMode.DOWN).intValue();
    }

    static boolean sameMoney(BigDecimal a, BigDecimal b) {
        return a.subtract(b).abs().compareTo(new BigDecimal("0.005")) < 0;
    }

    static String money(BigDecimal amount) {
        return "R" + PayrollKeys.money(amount);
    }

    private static String times(int count) {
        return count == 2 ? "twice" : count + " times";
    }

    private static String lineNumbers(List<PayrollParser.Line> lines) {
        List<String> numbers = lines.stream().map(l -> String.valueOf(l.lineNumber())).toList();
        return numbers.size() <= 2 ? String.join(" and ", numbers)
                : String.join(", ", numbers.subList(0, numbers.size() - 1)) + " and " + numbers.getLast();
    }

    /** A month as a person reads it: {@code September 2026}. */
    public static String monthName(YearMonth month) {
        return month.getMonth().getDisplayName(TextStyle.FULL, Locale.ENGLISH) + " " + month.getYear();
    }

    /** Initials and surname as a list shows a member: {@code B.K. Testerson}; the surname alone without initials. */
    public static String displayName(String initials, String surname) {
        String letters = PayrollNames.initialsKey(initials);
        StringBuilder shown = new StringBuilder();
        for (char letter : letters.toCharArray()) {
            shown.append(letter).append('.');
        }
        String last = surname == null ? "" : surname.trim();
        return (shown.isEmpty() ? last : shown + " " + last).trim();
    }
}
