package za.co.titandynamix.sasbo_online_backend.services.impl;

import za.co.titandynamix.sasbo_online_backend.acb.BankIndex;
import za.co.titandynamix.sasbo_online_backend.acb.HolidayCalendar;
import za.co.titandynamix.sasbo_online_backend.entity.acb.AcbRate;
import za.co.titandynamix.sasbo_online_backend.entity.acb.AcbRun;
import za.co.titandynamix.sasbo_online_backend.enums.AcbRunAction;
import za.co.titandynamix.sasbo_online_backend.enums.AcbRunStatus;
import za.co.titandynamix.sasbo_online_backend.enums.SubscriptionsStatus;
import za.co.titandynamix.sasbo_online_backend.exception.AcbRefusal;
import za.co.titandynamix.sasbo_online_backend.exception.AcbRefusalCode;
import za.co.titandynamix.sasbo_online_backend.repository.AcbPopulationRepository;
import za.co.titandynamix.sasbo_online_backend.repository.AcbRateRepository;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The refusals and preconditions the run service and the preparation job share, written once so
 * the request and the job cannot word or decide them differently.
 */
final class AcbRuns {

    /** The states that release their month -- the partial unique index's own predicate. */
    static final Set<AcbRunStatus> RELEASED =
            EnumSet.of(AcbRunStatus.FAILED, AcbRunStatus.CANCELLED, AcbRunStatus.DECLINED);

    /** The runs whose unpaids are real: the file went to the bank. */
    static final Set<AcbRunStatus> SENT =
            EnumSet.of(AcbRunStatus.SUBMITTED, AcbRunStatus.RETURNED, AcbRunStatus.CLOSED);

    /** The file is made by this many days before the first action date -- SASBO's rule (D31, Q37). */
    static final int MAKE_BY_LEAD_DAYS = 3;

    /** The states in which a file can still be made: a proposal, and an approved run. */
    static final Set<AcbRunStatus> FILE_STILL_TO_MAKE = EnumSet.of(AcbRunStatus.PREPARED, AcbRunStatus.APPROVED);

    private AcbRuns() {
    }

    /** The latest day to make the file: three days before the first debit. {@code null} for a run with no lines. */
    static LocalDate makeBy(LocalDate firstActionDate) {
        return firstActionDate == null ? null : firstActionDate.minusDays(MAKE_BY_LEAD_DAYS);
    }

    /**
     * 🧪 Whether the day to make the file by has passed -- shown and flagged, never a refusal: the bank
     * is the judge of a late file (Q29). {@code today} is an argument, never read here (T2-G).
     */
    static boolean makeByPassed(LocalDate makeBy, LocalDate today) {
        return makeBy != null && today.isAfter(makeBy);
    }

    /** The flag for a run: only while its file is still to be made. */
    static boolean makeByPassed(AcbRun run, LocalDate today) {
        return FILE_STILL_TO_MAKE.contains(run.getStatus()) && run.getCreationDate() == null
                && makeByPassed(makeBy(run.getFirstDueDate()), today);
    }

    /** The rate in force for {@code month}, or the refusal that no line can be priced. */
    static AcbRate rateInForce(AcbRateRepository rates, YearMonth month) {
        return rates.findFirstByEffectiveFromLessThanEqualOrderByEffectiveFromDesc(month.atDay(1))
                .orElseThrow(() -> new AcbRefusal(AcbRefusalCode.RATE_NOT_IN_FORCE,
                        "No subscription rate is in force for " + month + ", so no debit can be priced.",
                        Map.of("actionMonth", month.toString())));
    }

    /**
     * The calendar, if it is complete to the end of {@code month} -- or the refusal that the month's
     * dates cannot be computed honestly (D30). A month is never prepared on weekends alone.
     */
    static HolidayCalendar calendarCovering(HolidayCalendar calendar, YearMonth month) {
        if (!calendar.covers(month)) {
            Map<String, String> facts = new LinkedHashMap<>();
            facts.put("actionMonth", month.toString());
            facts.put("calendar", calendar.name());
            facts.put("knownUntil", calendar.knownUntil().map(Object::toString).orElse(""));
            throw new AcbRefusal(AcbRefusalCode.HOLIDAYS_NOT_KNOWN, calendar.knownUntil()
                    .map(until -> "The holiday calendar is known only until " + until + ", so " + month
                            + " cannot be prepared: a debit on an unlisted holiday gets the whole file "
                            + "declined. Extend the calendar first.")
                    .orElse("No holiday calendar is on record, so " + month + " cannot be prepared: a "
                            + "debit on a holiday gets the whole file declined."), facts);
        }
        return calendar;
    }

    /**
     * Whether the calendar still says of the run's month what it said when the lines were computed.
     * A run with no lines written yet has nothing to disagree with.
     */
    static boolean calendarAgrees(AcbRun run, HolidayCalendar calendar) {
        return run.getCalendarFingerprint() == null
                || run.getCalendarFingerprint().equals(calendar.fingerprint(YearMonth.from(run.getActionMonth())));
    }

    /**
     * 🔒 Refuses a run whose month's holidays changed after its dates were computed (SD3-F): a line
     * may now fall on a holiday, and the bank declines such a file whole.
     */
    static void requireCalendarAgrees(AcbRun run, HolidayCalendar calendar) {
        if (!calendarAgrees(run, calendar)) {
            YearMonth month = YearMonth.from(run.getActionMonth());
            Map<String, String> facts = facts(run);
            facts.put("changedDates", String.join(",",
                    changedDates(run.getCalendarFingerprint(), calendar.fingerprint(month))));
            throw new AcbRefusal(AcbRefusalCode.RUN_CALENDAR_CHANGED,
                    "The holiday calendar for " + month + " changed after this run was prepared, so a debit "
                            + "may now fall on a holiday. Cancel it and prepare the month again.",
                    facts);
        }
    }

    /** The days in one fingerprint and not the other -- added or removed since -- in date order. */
    static java.util.SortedSet<String> changedDates(String then, String now) {
        java.util.SortedSet<String> before = datesOf(then);
        java.util.SortedSet<String> after = datesOf(now);
        java.util.SortedSet<String> changed = new java.util.TreeSet<>(before);
        changed.addAll(after);
        java.util.Set<String> both = new java.util.TreeSet<>(before);
        both.retainAll(after);
        changed.removeAll(both);
        return changed;
    }

    private static java.util.SortedSet<String> datesOf(String fingerprint) {
        java.util.SortedSet<String> dates = new java.util.TreeSet<>();
        String[] parts = fingerprint == null ? new String[0] : fingerprint.split("\\|", -1);
        if (parts.length == 3 && !parts[2].isEmpty()) {
            dates.addAll(List.of(parts[2].split(",")));
        }
        return dates;
    }

    /** The banks and which of them SASBO collects from, refusing if any bank is ambiguous. */
    static BankIndex unambiguousBanks(AcbPopulationRepository population) {
        BankIndex banks = BankIndex.of(population.findBanks(),
                population.findBankIdsOfSubscriptionRows(SubscriptionsStatus.ACTIVE));
        List<String> ambiguous = banks.ambiguousBanks();
        if (!ambiguous.isEmpty()) {
            throw new AcbRefusal(AcbRefusalCode.BANK_SUBSCRIPTION_AMBIGUOUS,
                    "These banks have more than one active subscription, so the run cannot tell "
                            + "which applies: " + String.join(", ", ambiguous)
                            + ". Leave one active row per bank on the subscriptions screen.",
                    Map.of("banks", String.join(", ", ambiguous)));
        }
        return banks;
    }

    /**
     * The month is held. The advice follows the state: only a proposal can be cancelled, a run
     * still preparing settles by itself, and a month whose file went to the bank is not prepared
     * again.
     */
    static AcbRefusal alreadyExists(AcbRun live) {
        String next = AcbRunAction.CANCEL.from(live.getStatus()).isPresent()
                ? " Cancel it first to prepare the month again."
                : live.getStatus() == AcbRunStatus.PREPARING ? " It is still being prepared." : "";
        return new AcbRefusal(AcbRefusalCode.RUN_ALREADY_EXISTS,
                "The run for " + YearMonth.from(live.getActionMonth()) + " already exists, and it is "
                        + live.getStatus() + "." + next,
                facts(live));
    }

    static AcbRefusal notFound(long runId) {
        return new AcbRefusal(AcbRefusalCode.RUN_NOT_FOUND, "There is no run " + runId + ".",
                Map.of("runId", String.valueOf(runId)));
    }

    static AcbRefusal notInState(AcbRun run, AcbRunAction action) {
        return new AcbRefusal(AcbRefusalCode.RUN_NOT_IN_STATE,
                "A run that is " + run.getStatus() + " cannot be taken through " + action + ".",
                facts(run));
    }

    static AcbRefusal versionConflict(AcbRun current) {
        return new AcbRefusal(AcbRefusalCode.RUN_VERSION_CONFLICT,
                "Someone else changed this run first. It is now " + current.getStatus() + ".",
                facts(current));
    }

    /** What a screen needs to say what happened without asking again: the run as it stands. */
    static Map<String, String> facts(AcbRun run) {
        Map<String, String> facts = new LinkedHashMap<>();
        facts.put("runId", String.valueOf(run.getId()));
        facts.put("actionMonth", YearMonth.from(run.getActionMonth()).toString());
        facts.put("status", run.getStatus().name());
        facts.put("version", String.valueOf(run.getVersion()));
        return facts;
    }
}
