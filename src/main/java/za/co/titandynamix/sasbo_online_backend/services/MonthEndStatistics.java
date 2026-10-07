package za.co.titandynamix.sasbo_online_backend.services;

import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.stereotype.Service;
import za.co.titandynamix.sasbo_online_backend.config.SasboEnvironment;
import za.co.titandynamix.sasbo_online_backend.config.WorkplaceSwitch;
import za.co.titandynamix.sasbo_online_backend.dto.monthend.MonthEndMonthDto;
import za.co.titandynamix.sasbo_online_backend.dto.monthend.MonthEndStatisticDto;
import za.co.titandynamix.sasbo_online_backend.dto.monthend.MonthEndStatisticDto.Column;
import za.co.titandynamix.sasbo_online_backend.dto.monthend.MonthEndStatisticDto.Row;
import za.co.titandynamix.sasbo_online_backend.dto.monthend.MonthEndStatisticDto.Sheet;
import za.co.titandynamix.sasbo_online_backend.dto.reference.ReferenceRows;
import za.co.titandynamix.sasbo_online_backend.entity.monthend.MonthEndCount.Measure;
import za.co.titandynamix.sasbo_online_backend.exception.MonthEndRefusal;
import za.co.titandynamix.sasbo_online_backend.exception.MonthEndRefusalCode;
import za.co.titandynamix.sasbo_online_backend.repository.MemberSummaryRepository;
import za.co.titandynamix.sasbo_online_backend.repository.MonthEndFactsRepository;
import za.co.titandynamix.sasbo_online_backend.repository.MonthEndFactsRepository.Count;
import za.co.titandynamix.sasbo_online_backend.repository.MonthEndFactsRepository.Fact;
import za.co.titandynamix.sasbo_online_backend.repository.MonthEndFactsRepository.TakenMonth;
import za.co.titandynamix.sasbo_online_backend.repository.MonthEndFactsRepository.YearMovement;
import za.co.titandynamix.sasbo_online_backend.repository.MonthEndFactsRepository.YearRecruits;
import za.co.titandynamix.sasbo_online_backend.repository.SasboReferenceRepository;
import za.co.titandynamix.sasbo_online_backend.utils.MonthRange;
import za.co.titandynamix.sasbo_online_backend.utils.Penetration;
import za.co.titandynamix.sasbo_online_backend.utils.SeedMemberNumbers;
import za.co.titandynamix.sasbo_online_backend.utils.SmallCounts;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The statistics of a month (month end 04.3 to 04.8): each a set of SHEETS, every one a sum over the month's facts --
 * one row a workplace, with its lineage as it stood -- worked out when it is asked for and never stored (MD43). So two
 * statistics cannot disagree about a figure they share (MV-16), and a sheet nobody thought of is one more sum.
 *
 * <ul>
 *   <li>A TAKEN month is read from what was frozen (MD19); the OPEN month is worked out now by the very statement a
 *       take freezes; a past month not taken is {@code MONTH_NOT_TAKEN};</li>
 *   <li>the year to date is the sum of the year's taken months (rule Y), each as it was taken -- and, for the open
 *       month, the open month as it stands;</li>
 *   <li>every output takes an area or a region: <em>"All regions get a copy of the relevant documents for their
 *       area"</em>;</li>
 *   <li>RULE C where a row names an employer, a table code or a group: a membership of one to four is fewer than
 *       five, and the smallest of five or more beside it is withheld (MD37) -- applied here, when the sheet is read,
 *       never when the facts are stored. A total is never suppressed;</li>
 *   <li>THE SWITCH: a sheet cut by where members work is grouped only where it says {@code all}; elsewhere it is the
 *       totals and one row, and nothing made from a staff number is answered (MD62, MV-55);</li>
 *   <li>bounded as the figures are: a query timeout, and at most {@value SummaryGate#AT_ONCE} at once -- a third is
 *       {@code SUMMARY_UNAVAILABLE} at once (MD47, MD63).</li>
 * </ul>
 * A statistic is counts, and an open read. A LISTING names members, and is {@link MonthEndListings}' -- held by
 * {@code MEMBERS_VIEW} since sign-in.
 */
@Service
public class MonthEndStatistics {

    /** The one row of a sheet below {@code all}. */
    public static final String ALL = "~ALL";
    public static final String NO_WORKPLACE = MemberSummaryRepository.NO_WORKPLACE;
    public static final String NO_TABLE_CODE = MemberSummaryRepository.NO_TABLE_CODE;
    public static final String NO_EMPLOYER = MemberSummaryRepository.NO_EMPLOYER;
    public static final String NO_REGION = MemberSummaryRepository.NO_REGION;
    public static final String NO_AREA = "~NO_AREA";
    public static final String NO_CODE = "~NO_CODE";
    public static final String NO_RECRUITER = "~NO_RECRUITER";
    public static final String NO_CHANNEL = "~NO_CHANNEL";
    public static final String NO_REASON = "~NO_REASON";

    /** The longest area letter and region code a request may name (MR-SEC-22). */
    static final int AREA_CEILING = 4;
    static final int REGION_CEILING = 8;

    private static final Map<String, String> SPECIAL_LABELS = Map.of(
            ALL, "All members",
            NO_WORKPLACE, "No workplace on record",
            NO_TABLE_CODE, "No table code on record",
            NO_EMPLOYER, "No employer on record",
            NO_REGION, "No region on record",
            NO_AREA, "No area on record",
            NO_CODE, "No code on record",
            NO_RECRUITER, "No recruiter on record",
            NO_CHANNEL, "No channel on record",
            NO_REASON, "No reason given");

    private static final List<String> SPECIAL_ORDER = List.of(NO_CODE, NO_REASON, NO_RECRUITER, NO_CHANNEL,
            NO_TABLE_CODE, NO_EMPLOYER, NO_REGION, NO_AREA, NO_WORKPLACE, ALL);

    private final MonthEndTakes takes;
    private final MonthEndFactsRepository facts;
    private final SasboReferenceRepository codes;
    private final WorkplaceSwitch workplaceSwitch;
    private final SasboEnvironment environment;
    private final SeedMemberNumbers seedNumbers;
    private final SummaryGate gate;

    public MonthEndStatistics(MonthEndTakes takes, MonthEndFactsRepository facts, SasboReferenceRepository codes,
                              WorkplaceSwitch workplaceSwitch, SasboEnvironment environment,
                              SeedMemberNumbers seedNumbers, SummaryGate gate) {
        this.takes = takes;
        this.facts = facts;
        this.codes = codes;
        this.workplaceSwitch = workplaceSwitch;
        this.environment = environment;
        this.seedNumbers = seedNumbers;
        this.gate = gate;
    }

    /**
     * May a figure grouped finer than the whole be answered here? Only where the switch says {@code all}
     * (MR-SEC-19). The pack's month-on-month tables ask it as every statistic does (MD62).
     */
    public boolean grouped() {
        return workplaceSwitch.staffNumbersServed();
    }

    // ================================================================================================ the statistics

    /** 04.3: the month's new and deleted members by area, core and other -- and the year to date (prtmemsum). */
    public MonthEndStatisticDto newAndDeleted(String month, String area, String region) {
        return newAndDeleted(read(month, area, region, false));
    }

    MonthEndStatisticDto newAndDeleted(Read read) {
        List<Movement> monthly = read.facts().stream().map(Movement::of).toList();
        List<Movement> yearly = yearMovement(read);
        List<Column> columns = List.of(count("core", "Core"), count("other", "Other"), count("total", "Total"));
        String name = monthName(read.month());
        List<Sheet> sheets = List.of(
                movementSheet(read, "month-joined", "Joined in " + name,
                        "SUMMARY OF NEW AND DELETED MEMBERS FOR " + name.toUpperCase(Locale.ROOT), monthly, true, columns),
                movementSheet(read, "month-left", "Left in " + name,
                        "SUMMARY OF NEW AND DELETED MEMBERS FOR " + name.toUpperCase(Locale.ROOT), monthly, false, columns),
                movementSheet(read, "year-joined", "Joined, " + read.month().getYear() + " to date",
                        "COMPOSITE SUMMARY OF NEW AND DELETED MEMBERS Y.T.D", yearly, true, columns),
                movementSheet(read, "year-left", "Left, " + read.month().getYear() + " to date",
                        "COMPOSITE SUMMARY OF NEW AND DELETED MEMBERS Y.T.D", yearly, false, columns));
        List<String> notes = notes(read);
        notes.add(yearToDate(read));
        notes.add("Core is the core groups' employers; other is every other employer. A member with no workplace on "
                + "record is in the total only.");
        if (read.taken() != null) {
            long now = facts.joinedNow(read.range());
            long taken = read.allFacts().stream().mapToLong(Fact::joined).sum();
            if (now > taken) {
                notes.add((now - taken) + " member(s) with a Date Joined in " + name + " were captured after the month "
                        + "was taken, and are not in these figures.");
            }
        }
        return answer("new-and-deleted", List.of("prtmemsum", "prtmemroll", "prtmemroll2"), read, sheets, notes);
    }

    /** 04.4: membership by group and by table code, how it is collected, and the suspended by code (grlist, mem_count). */
    public MonthEndStatisticDto membership(String month, String area, String region) {
        return membership(read(month, area, region, false));
    }

    MonthEndStatisticDto membership(Read read) {
        boolean staff = read.grouped();
        List<Column> groupColumns = List.of(count("complement", "Complement"), count("members", "Members"),
                count("joined", "Enrolments"), count("left", "Cancellations"), count("suspended", "Suspensions"));
        Function<Agg, List<BigDecimal>> groupCells = a -> cells(staff ? n(a.complement) : null, n(a.paying),
                n(a.joined), n(a.left), n(a.suspended));
        Sheet byGroup = groupSheet(read, "by-group", "Membership by group", "MEMBER SUMMARY BY GROUP", groupColumns,
                groupCells);

        List<Column> codeColumns = List.of(count("onRecord", "On record"), count("members", "Excluding suspensions"),
                count("suspended", "Suspensions"), rand("subscriptions", "Subscriptions"),
                count("debitOrder", "By debit order"), rand("debitOrderRand", "By debit order (R)"),
                count("deduction", "By payroll deduction"), rand("deductionRand", "By payroll deduction (R)"),
                count("other", "Other"), rand("otherRand", "Other (R)"), count("complement", "Complement"));
        Sheet byTableCode = placeSheet(read, "by-table-code", "Membership by table code", "BANK / BRANCH MEMBER COUNT",
                MonthEndStatistics::tableCodeKey, codeColumns, a -> cells(n(a.onRecord), n(a.paying), n(a.suspended),
                        a.subscriptions(), n(a.byDebitOrder), a.debitOrderRand, n(a.byDeduction), a.deductionRand,
                        n(a.byOther), a.otherRand, staff ? n(a.complement) : null), true);

        Map<String, long[]> suspended = new LinkedHashMap<>();
        Map<String, String> labels = new HashMap<>();
        for (Count c : read.counts(Measure.SUSPENDED_BY_CODE)) {
            String key = c.code() == null ? NO_CODE : String.valueOf(c.code());
            labels.putIfAbsent(key, c.code() == null ? (c.name() == null ? SPECIAL_LABELS.get(NO_CODE)
                    : "No code: " + c.name()) : c.code() + " " + c.name());
            suspended.computeIfAbsent(key, k -> new long[1])[0] += c.members();
        }
        Sheet byCode = codeSheet("suspended-by-code", "The suspended, by suspension code",
                "SUMMARY OF SUSPENDED MEMBERS", List.of(count("members", "Suspended")), suspended, labels);

        List<String> notes = notes(read);
        notes.add("Members are the members on record less the suspended -- the figure SASBO's reports print (MD24).");
        notes.add("How a member pays is read as it is stored: by debit order, by payroll deduction, and every other "
                + "way as other. The amounts are the monthly subscriptions on record of the paying members.");
        return answer("membership", List.of("grlist", "mem_count"), read, List.of(byGroup, byTableCode, byCode), notes);
    }

    /** 04.5: why members left -- by SASBO's reasons, by area, by employer and by table code; joined and deleted; the resigned. */
    public MonthEndStatisticDto leavers(String month, String area, String region) {
        return leavers(read(month, area, region, false));
    }

    MonthEndStatisticDto leavers(Read read) {
        List<ReferenceRows.DeletionCode> deletionCodes = codes.deletionCodes();
        List<ReferenceRows.DeletionCode> numbered = deletionCodes.stream().filter(d -> d.code() != null).toList();
        List<Column> reasonColumns = new ArrayList<>();
        for (ReferenceRows.DeletionCode code : numbered) {
            reasonColumns.add(count("code" + code.code(), code.name() + " (" + code.code() + ")"));
        }
        reasonColumns.add(count("noCode", SPECIAL_LABELS.get(NO_CODE)));
        reasonColumns.add(count("total", "Total"));
        List<Integer> order = numbered.stream().map(ReferenceRows.DeletionCode::code).toList();

        Sheet byArea = reasonSheet(read, "by-area", "Why members left, by area",
                "ANALYSIS OF MEMBERS DELETED FOR " + read.month().getMonthValue() + "/" + (read.month().getYear() % 100),
                MonthEndStatistics::areaKey, reasonColumns, order, false);
        // By employer, as the plan asks (04.5: "by area and by employer") -- the totals of SASBO's per-employer files,
        // nedbank_deletions, stb_deletions and insurance_deletions, whose table codes the next sheet lists.
        Sheet byEmployer = reasonSheet(read, "by-employer", "Why members left, by employer",
                "SUMMARY OF MEMBERS DELETED DURING THE PERIOD", MonthEndStatistics::employerKey, reasonColumns, order,
                true);
        Sheet byTableCode = reasonSheet(read, "by-table-code", "Why members left, by table code",
                "SUMMARY OF MEMBERS DELETED DURING THE PERIOD", MonthEndStatistics::tableCodeKey, reasonColumns, order,
                true);
        Sheet joinedDeleted = placeSheet(read, "joined-deleted", "Joined and left, by table code",
                "LISTING OF MEMBERS JOINED/DELETED DURING THE PERIOD", MonthEndStatistics::tableCodeKey,
                List.of(count("joined", "Joined"), count("left", "Left"), count("movement", "Movement")),
                a -> cells(n(a.joined), n(a.left), BigDecimal.valueOf(a.joined - a.left)), true);

        List<String> under = deletionCodes.stream().filter(d -> d.code() == null && Integer.valueOf(2).equals(d.under()))
                .map(ReferenceRows.DeletionCode::name).toList();
        Sheet resigned = resignedSheet(read, under);

        List<String> notes = notes(read);
        notes.add("A member is counted once each time they left in the month. SASBO's reasons are its deletion codes, "
                + "by their numbers; a leaver with no code on record is counted apart.");
        return answer("leavers", List.of("prt_del_summ", "nedbank_deletions", "stb_deletions", "insurance_deletions",
                "nedbank_joined_deleted", "stb_joined_deleted", "insurance_joined_deleted", "resigned"), read,
                List.of(byArea, byEmployer, byTableCode, joinedDeleted, resigned), notes);
    }

    /** 04.6: the branches and their penetration -- by area, and by employer within an area (bank_master_summ). */
    public MonthEndStatisticDto branches(String month, String area, String region) {
        return branches(read(month, area, region, false));
    }

    MonthEndStatisticDto branches(Read read) {
        boolean staff = read.grouped();
        List<Column> columns = List.of(count("complement", "Complement"), count("members", "Members"),
                share("penetration", "Penetration"), rand("subscriptions", "Subscriptions"),
                count("coreComplement", "Core complement"), count("coreMembers", "Core members"));
        Function<Agg, List<BigDecimal>> cells = a -> cells(staff ? n(a.complement) : null, n(a.paying),
                staff ? Penetration.of(a.paying, a.complement) : null, a.subscriptions(),
                staff ? n(a.coreComplement) : null,
                staff ? n(a.corePaying) : null);
        Sheet byArea = placeSheet(read, "by-area", "The branches, by area", "SUMMARY OF ALL AREAS",
                MonthEndStatistics::areaKey, columns, cells, false);
        Sheet byEmployer = placeSheet(read, "by-employer", "The branches, by employer", "S.A.S.B.O. LIST OF BANK BRANCHES",
                MonthEndStatistics::employerKey, columns, cells, true);
        List<String> notes = notes(read);
        notes.add("Members are the members on record less the suspended; penetration is members over complement.");
        notes.add("The branches themselves, with their visits, are the workplaces' own screen.");
        return answer("branches", List.of("bank_master_summ"), read, List.of(byArea, byEmployer), notes);
    }

    /** 04.7: who recruited the month's new members -- by recruiter, by table code, by channel; the month and the year. */
    public MonthEndStatisticDto recruitment(String month, String area, String region) {
        return recruitment(read(month, area, region, false));
    }

    MonthEndStatisticDto recruitment(Read read) {
        Map<Long, Fact> byKey = read.factsByKey();
        List<Recruit> monthly = new ArrayList<>();
        for (Count c : read.counts(Measure.RECRUITED_BY)) {
            Fact at = byKey.get(c.workplaceKey());
            monthly.add(new Recruit(c.code(), c.name(), c.detail(), c.workplaceKey() == 0,
                    at == null ? null : at.groupCore(), at == null ? null : at.tableCode(),
                    at == null ? null : at.tableCodeName(), c.members()));
        }
        List<Recruit> yearly = yearRecruits(read, monthly);

        Map<String, long[]> byRecruiter = new LinkedHashMap<>();
        Map<String, String> recruiterLabels = new HashMap<>();
        for (int pass = 0; pass < 2; pass++) {
            for (Recruit r : pass == 0 ? monthly : yearly) {
                String key = r.code() == null ? NO_RECRUITER : String.valueOf(r.code());
                recruiterLabels.putIfAbsent(key, r.code() == null ? SPECIAL_LABELS.get(NO_RECRUITER)
                        : (r.name() == null ? "Recruiter " + r.code() : r.name()));
                long[] cells = byRecruiter.computeIfAbsent(key, k -> new long[6]);
                int column = pass;
                if (!r.noWorkplace() && read.grouped()) {
                    cells[(Boolean.TRUE.equals(r.core()) ? 0 : 2) + column] += r.members();
                }
                cells[4 + column] += r.members();
            }
        }
        Sheet recruiters = read.grouped()
                ? codeSheet("by-recruiter", "Recruitment, by recruiter",
                        "ANALYSIS OF CORE MEMBERS RECRUITED BY REPRESENTATIVE", List.of(count("coreMonth", "Core, the month"),
                                count("coreYear", "Core, the year"), count("otherMonth", "Other, the month"),
                                count("otherYear", "Other, the year"), count("month", "Total, the month"),
                                count("year", "Total, the year")), byRecruiter, recruiterLabels)
                : codeSheet("by-recruiter", "Recruitment, by recruiter",
                        "ANALYSIS OF CORE MEMBERS RECRUITED BY REPRESENTATIVE", List.of(count("month", "Total, the month"),
                                count("year", "Total, the year")), reorder(byRecruiter, 4, 5), recruiterLabels);

        Map<String, long[]> byChannel = new LinkedHashMap<>();
        Map<String, String> channelLabels = new HashMap<>();
        for (int pass = 0; pass < 2; pass++) {
            for (Recruit r : pass == 0 ? monthly : yearly) {
                String key = r.detail() == null ? NO_CHANNEL : r.detail();
                channelLabels.putIfAbsent(key, r.detail() == null ? SPECIAL_LABELS.get(NO_CHANNEL) : r.detail());
                byChannel.computeIfAbsent(key, k -> new long[2])[pass] += r.members();
            }
        }
        Sheet channels = codeSheet("by-channel", "Recruitment, by channel", "WEBSITE RECRUITING ANALYSIS BY AREA",
                List.of(count("month", "The month"), count("year", "The year")), byChannel, channelLabels);

        Sheet byTableCode = recruitsByTableCode(read, monthly, yearly);

        List<String> notes = notes(read);
        notes.add(yearToDate(read));
        notes.add("A recruiter is who signed the member up, as captured. Which year to date SASBO reads, and the "
                + "competition's rule, are SASBO's to say (MM69, MM71).");
        return answer("recruitment", List.of("rec_analyst", "prt_ra_by_tc", "prt_rec_core", "prt_rec_group",
                "prt_rec_web"), read, List.of(recruiters, byTableCode, channels), notes);
    }

    /** 04.8: representatives and committee members, by table code -- how many, and in how many workplaces. */
    public MonthEndStatisticDto representatives(String month, String area, String region) {
        return representatives(read(month, area, region, false));
    }

    MonthEndStatisticDto representatives(Read read) {
        List<Column> repColumns = List.of(count("representatives", "Representatives"), count("workplaces", "Workplaces"),
                count("with", "With a representative"), count("without", "Without one"),
                share("share", "Share with one"));
        Sheet reps = placeSheet(read, "representatives", "Representatives, by table code",
                "SASBO AREA SUMMARY OF SASBO REPRESENTATIVES", MonthEndStatistics::tableCodeKey, repColumns,
                a -> cells(n(a.representatives), n(a.workplaces), n(a.withRepresentatives),
                        n(a.workplaces - a.withRepresentatives), share(a.withRepresentatives, a.workplaces)), true);
        List<Column> committeeColumns = List.of(count("committeeMembers", "Committee members"),
                count("workplaces", "Workplaces"), count("with", "With a committee member"),
                count("without", "Without one"), share("share", "Share with one"));
        Sheet committees = placeSheet(read, "committee-members", "Committee members, by table code",
                "SASBO AREA SUMMARY OF COMMITTEE MEMBERS", MonthEndStatistics::tableCodeKey, committeeColumns,
                a -> cells(n(a.committeeMembers), n(a.workplaces), n(a.withCommitteeMembers),
                        n(a.workplaces - a.withCommitteeMembers), share(a.withCommitteeMembers, a.workplaces)), true);
        // MM80, SASBO's answer of 1 Oct: a Memnet member is marked so from the nomination form, at capture or on an edit.
        List<Column> memnetColumns = List.of(count("memnetMembers", "Memnet members"), count("workplaces", "Workplaces"),
                count("with", "With a Memnet member"), count("without", "Without one"),
                share("share", "Share with one"));
        Sheet memnet = placeSheet(read, "memnet-members", "Memnet members, by table code",
                "SASBO AREA SUMMARY OF MEMNET MEMBERS", MonthEndStatistics::tableCodeKey, memnetColumns,
                a -> cells(n(a.memnetMembers), n(a.workplaces), n(a.withMemnetMembers),
                        n(a.workplaces - a.withMemnetMembers), share(a.withMemnetMembers, a.workplaces)), true);
        List<String> notes = notes(read);
        notes.add("A representative is a member on record marked as a shop steward, and a committee member one marked "
                + "as a BEC member -- a reading SASBO is asked to confirm.");
        notes.add("A Memnet member is a member on record marked so from the nomination form, at capture or on an edit. "
                + "A month taken before the mark existed counts none.");
        return answer("representatives", List.of("reps_no", "comm_mem", "memnet_area"), read,
                List.of(reps, committees, memnet), notes);
    }

    /**
     * 04.9: the branches due a visit (visits) -- each workplace, its last visit, its members, its complement and the
     * recruits it could still yield, the longest unvisited first. Needs A.1, whose visits it reads.
     */
    public MonthEndStatisticDto visits(String month, String area, String region) {
        return visits(read(month, area, region, false));
    }

    MonthEndStatisticDto visits(Read read) {
        boolean staff = read.grouped();
        Map<Long, MonthEndFactsRepository.LastVisit> lastVisits = read.grouped()
                ? facts.lastVisits(read.range(), read.taken() == null ? null : read.taken().takenAt()) : Map.of();
        Map<String, Agg> byWorkplace = aggregate(read, f -> workplaceKey(f, lastVisits));
        Map<String, LocalDate> lastOf = new HashMap<>();
        for (Fact f : read.facts()) {
            MonthEndFactsRepository.LastVisit visit = lastVisitOf(f, lastVisits);
            if (visit != null && visit.lastVisited() != null) {
                lastOf.put("W" + f.workplaceKey(), visit.lastVisited());
            }
        }
        // The longest unvisited first -- never visited, then the oldest last visit -- then by R/A number; the rows of
        // none last. The order SASBO's own report keeps: BANK BRANCHES REQUIRING ATTENTION.
        List<Agg> ordered = byWorkplace.values().stream().sorted(
                Comparator.<Agg>comparingInt(a -> a.key.startsWith("~") ? 1 + SPECIAL_ORDER.indexOf(a.key) : 0)
                        .thenComparing(a -> lastOf.get(a.key), Comparator.nullsFirst(Comparator.naturalOrder()))
                        .thenComparing(a -> a.order == null ? Integer.MAX_VALUE : a.order)).toList();
        Agg total = new Agg("~TOTAL", "Total", null);
        ordered.forEach(total::absorb);
        List<Column> columns = List.of(count("members", "Members"), count("complement", "Complement"),
                count("couldYield", "Could still yield"));
        Function<Agg, List<BigDecimal>> cells = a -> cells(n(a.paying), staff ? n(a.complement) : null,
                staff ? n(Math.max(a.complement - a.paying, 0)) : null);
        // A row that withholds its members withholds what the branch could still yield with them: the two would give
        // each other back (BR44). Rule C's own rows do exactly that, the whole row at once.
        List<Row> rows = settledRows(read, ordered, true, total.onRecord, cells);
        long yieldAll = ordered.stream().filter(a -> !a.key.startsWith("~"))
                .mapToLong(a -> Math.max(a.complement - a.paying, 0)).sum();
        Row totalRow = new Row(total.key, total.label, Row.TOTAL, false, false,
                cells(n(total.paying), staff ? n(total.complement) : null, staff ? n(yieldAll) : null));
        Sheet sheet = new Sheet("due-a-visit", "The branches due a visit", "BANK BRANCHES REQUIRING ATTENTION",
                columns, rows, totalRow);
        List<String> notes = notes(read);
        notes.add("The last visit is the newest on record by the month's end -- a visit captured here, or a month "
                + "SASBO's branch list prints; " + (read.taken() == null ? "as the visits stand now."
                : "as they stood when the month was taken."));
        notes.add("Members are the members on record less the suspended. A branch could still yield its complement "
                + "less its members, where the complement is the more.");
        notes.add("The branch manager's name and telephone are a person's details, and are shown once administrators "
                + "sign in to this system.");
        return answer("visits", List.of("visits"), read, List.of(sheet), notes);
    }

    /** {key, label, order} of a workplace's row: its R/A number and name, and when it was last visited. */
    private static String[] workplaceKey(Fact f, Map<Long, MonthEndFactsRepository.LastVisit> lastVisits) {
        MonthEndFactsRepository.LastVisit visit = lastVisitOf(f, lastVisits);
        String name = visit == null || visit.name() == null ? "" : " " + visit.name();
        String last = visit == null || visit.lastVisited() == null ? "never visited"
                : "last visited " + visit.lastVisited().getMonth().getDisplayName(TextStyle.SHORT, Locale.ENGLISH) + " "
                        + visit.lastVisited().getYear();
        return new String[] {"W" + f.workplaceKey(), "R/A " + f.raNumber() + name + " · " + last,
                f.raNumber() == null ? null : String.valueOf(f.raNumber())};
    }

    /**
     * A member's workplace's last visit, or none. A member with no workplace on record has none to ask about -- every
     * member, below 'all', where nothing of SASBO's is placed -- and the map below 'all' is {@code Map.of()}, which
     * refuses a null key outright: asked anyway, the statistic answered 500 on the link (1 Oct, switch unset).
     */
    private static MonthEndFactsRepository.LastVisit lastVisitOf(Fact f,
                                                                 Map<Long, MonthEndFactsRepository.LastVisit> lastVisits) {
        return f.workplaceId() == null ? null : lastVisits.get(f.workplaceId());
    }

    /**
     * 04.10: every statistic of a month from ONE read -- the statistics pack's. A job waits up to {@link #JOB_WAIT} for
     * a place at the gate instead of being refused as a screen is.
     */
    public List<MonthEndStatisticDto> all(String month) {
        Read read = read(month, null, null, true);
        return List.of(newAndDeleted(read), membership(read), leavers(read), branches(read), recruitment(read),
                representatives(read));
    }

    /** How long a job waits for a place at the gate. */
    static final java.time.Duration JOB_WAIT = java.time.Duration.ofMinutes(2);

    // ================================================================================================ what is read

    /** What every statistic reads: the month, its rows and counts, in the area or region asked. */
    record Read(YearMonth month, MonthRange range, TakenMonth taken, List<Fact> allFacts, List<Fact> facts,
                List<Count> counts, boolean grouped, String area, String region) {

        List<Count> counts(Measure measure) {
            return counts.stream().filter(c -> c.measure() == measure).toList();
        }

        Map<Long, Fact> factsByKey() {
            Map<Long, Fact> byKey = new HashMap<>();
            facts.forEach(f -> byKey.put(f.workplaceKey(), f));
            return byKey;
        }

        boolean scoped() {
            return area != null || region != null;
        }
    }

    Read read(String month, String area, String region, boolean waitForAPlace) {
        String areaCode = code("area", area, AREA_CEILING);
        String regionCode = code("region", region, REGION_CEILING);
        if (areaCode != null && regionCode != null) {
            throw new MonthEndRefusal(MonthEndRefusalCode.REQUEST_MALFORMED,
                    "Ask for an area or a region, not both.", Map.of("field", "region"));
        }
        YearMonth asked = takes.month(month);
        Optional<TakenMonth> taken = takes.taken(asked);
        YearMonth open = takes.openMonth();
        if (taken.isEmpty() && !asked.equals(open)) {
            throw new MonthEndRefusal(MonthEndRefusalCode.MONTH_NOT_TAKEN,
                    "That month was not taken, and a past month is read only from what was taken.",
                    MonthEndRefusal.details("month", asked.toString(), "open", open.toString()));
        }
        if (areaCode != null && !facts.areaOnRecord(areaCode)) {
            throw new MonthEndRefusal(MonthEndRefusalCode.SUMMARY_AREA_UNKNOWN, "That area is not on record here.",
                    Map.of("field", "area"));
        }
        if (regionCode != null && !facts.regionOnRecord(regionCode)) {
            throw new MonthEndRefusal(MonthEndRefusalCode.SUMMARY_AREA_UNKNOWN, "That region is not on record here.",
                    Map.of("field", "region"));
        }
        boolean grouped = workplaceSwitch.staffNumbersServed();
        MonthRange range = MonthRange.of(asked);
        if (!(waitForAPlace ? gate.enter(JOB_WAIT) : gate.tryEnter())) {
            throw unavailable();
        }
        List<Fact> rows;
        List<Count> counts;
        try {
            rows = taken.isPresent() ? facts.facts(taken.get().id()) : facts.liveFacts(range, seeded());
            counts = taken.isPresent() ? facts.counts(taken.get().id()) : facts.liveCounts(range);
        } catch (QueryTimeoutException | DataAccessResourceFailureException tooLong) {
            throw unavailable();
        } finally {
            gate.leave();
        }
        List<Fact> inScope = rows.stream().filter(f -> inScope(f.areaLetter(), f.regionCode(), f.workplaceKey() == 0,
                areaCode, regionCode, grouped)).toList();
        Set<Long> keys = inScope.stream().map(Fact::workplaceKey).collect(Collectors.toSet());
        List<Count> scopedCounts = counts.stream().filter(c -> keys.contains(c.workplaceKey())
                || (c.workplaceKey() == 0 && areaCode == null && regionCode == null)).toList();
        return new Read(asked, range, taken.orElse(null), rows, inScope, scopedCounts, grouped, areaCode, regionCode);
    }

    /** Below {@code all} nothing is placed by where it works, so a sheet asked for an area or a region holds nobody. */
    private static boolean inScope(String areaLetter, String regionCode, boolean noWorkplace, String area,
                                   String region, boolean grouped) {
        if (area == null && region == null) {
            return true;
        }
        if (!grouped || noWorkplace) {
            return false;
        }
        return area != null ? area.equals(areaLetter) : region.equals(regionCode);
    }

    private Collection<String> seeded() {
        return environment.isProduction() ? List.of() : seedNumbers.all();
    }

    // ================================================================================================ the year to date

    /** The movement of the year to the month: the taken months', each as taken -- and the open month's, as it stands. */
    private List<Movement> yearMovement(Read read) {
        LocalDate until = read.taken() != null ? read.range().firstDayOfNext() : read.range().firstDay();
        List<Movement> year = new ArrayList<>();
        for (YearMovement y : facts.yearMovement(read.range().firstDayOfYear(), until)) {
            if (inScope(y.areaLetter(), y.regionCode(), y.noWorkplace(), read.area(), read.region(), read.grouped())) {
                year.add(new Movement(y.noWorkplace(), y.areaLetter(), y.regionCode(), y.regionName(), y.groupCore(),
                        y.joined(), y.left()));
            }
        }
        if (read.taken() == null) {
            read.facts().forEach(f -> year.add(Movement.of(f)));
        }
        return year;
    }

    private List<Recruit> yearRecruits(Read read, List<Recruit> monthly) {
        LocalDate until = read.taken() != null ? read.range().firstDayOfNext() : read.range().firstDay();
        List<Recruit> year = new ArrayList<>();
        for (YearRecruits y : facts.yearRecruits(read.range().firstDayOfYear(), until)) {
            if (inScope(y.areaLetter(), y.regionCode(), y.noWorkplace(), read.area(), read.region(), read.grouped())) {
                year.add(new Recruit(y.code(), y.name(), y.detail(), y.noWorkplace(), y.groupCore(), y.tableCode(),
                        y.tableCodeName(), y.members()));
            }
        }
        if (read.taken() == null) {
            year.addAll(monthly);
        }
        return year;
    }

    private String yearToDate(Read read) {
        List<String> months = new ArrayList<>();
        for (TakenMonth t : takes.takenMonths()) {
            YearMonth m = YearMonth.from(t.monthStart());
            if (m.getYear() == read.month().getYear() && !m.isAfter(read.month())) {
                months.add(0, monthName(m));
            }
        }
        String taken = months.isEmpty() ? "no month of " + read.month().getYear() + " has been taken"
                : "the months of " + read.month().getYear() + " taken: " + String.join(", ", months);
        return "The year to date is the sum of the months taken, each as it was taken -- " + taken
                + (read.taken() == null ? "; and " + monthName(read.month()) + " as it stands." : ".");
    }

    // ================================================================================================ the sheets

    /** A member's movement, a workplace at a time: joined and left, core or other. */
    record Movement(boolean noWorkplace, String areaLetter, String regionCode, String regionName, Boolean core,
                    long joined, long left) {
        static Movement of(Fact f) {
            return new Movement(f.workplaceKey() == 0, f.areaLetter(), f.regionCode(), f.regionName(), f.groupCore(),
                    f.joined(), f.left());
        }
    }

    /** A month's recruits at one grain. */
    record Recruit(Integer code, String name, String detail, boolean noWorkplace, Boolean core, Integer tableCode,
                   String tableCodeName, long members) {
    }

    /** Joined or left, by area, core and other -- the rows of one of prtmemsum's pages. */
    private Sheet movementSheet(Read read, String key, String title, String legacyTitle, List<Movement> movements,
                                boolean joined, List<Column> columns) {
        Map<String, long[]> byArea = new LinkedHashMap<>();
        Map<String, String> labels = new HashMap<>();
        for (Movement m : movements) {
            String row = !read.grouped() ? ALL : m.noWorkplace() ? NO_WORKPLACE
                    : m.areaLetter() == null ? NO_AREA : m.areaLetter();
            labels.putIfAbsent(row, m.regionName() == null ? row : row + " - " + m.regionName());
            long[] cells = byArea.computeIfAbsent(row, r -> new long[3]);
            long n = joined ? m.joined() : m.left();
            if (read.grouped() && !m.noWorkplace()) {
                cells[Boolean.TRUE.equals(m.core()) ? 0 : 1] += n;
            }
            cells[2] += n;
        }
        List<Row> rows = new ArrayList<>();
        long[] total = new long[3];
        for (String area : byArea.keySet().stream().sorted(areaOrder()).toList()) {
            long[] c = byArea.get(area);
            boolean placed = read.grouped() && !area.startsWith("~");
            rows.add(new Row(area, label(area, labels.get(area)), Row.ROW, false, false,
                    cells(placed ? n(c[0]) : null, placed ? n(c[1]) : null, n(c[2]))));
            for (int i = 0; i < 3; i++) {
                total[i] += c[i];
            }
        }
        Row totalRow = new Row("~TOTAL", "Total", Row.TOTAL, false, false,
                cells(read.grouped() ? n(total[0]) : null, read.grouped() ? n(total[1]) : null, n(total[2])));
        return new Sheet(key, title, legacyTitle, columns, rows, totalRow);
    }

    /**
     * A sheet cut by place -- an area, an employer, a table code: one row a place, summed from the month's rows, rule
     * C applied where the place is named ({@code guardedPlace}), the total never suppressed.
     */
    private Sheet placeSheet(Read read, String key, String title, String legacyTitle, Function<Fact, String[]> place,
                             List<Column> columns, Function<Agg, List<BigDecimal>> cells, boolean guardedPlace) {
        Map<String, Agg> byPlace = aggregate(read, place);
        List<Agg> ordered = byPlace.values().stream().sorted(aggOrder()).toList();
        Agg total = new Agg("~TOTAL", "Total", null);
        ordered.forEach(total::absorb);
        List<Row> rows = settledRows(read, ordered, guardedPlace, total.onRecord, cells);
        return new Sheet(key, title, legacyTitle, columns, rows, row(total, Row.TOTAL, cells));
    }

    /**
     * grlist: the groups, each followed by its employers -- and the core groups' total beside all of them. The GROUPS
     * are guarded here as the employers are: a group's subtotal sits above its employers, so a group of one small
     * employer would print that employer's number. A hidden group hides its employers, and the core groups' line is
     * left out wherever a group is hidden, because it would give a withheld group back.
     */
    private Sheet groupSheet(Read read, String key, String title, String legacyTitle, List<Column> columns,
                             Function<Agg, List<BigDecimal>> cells) {
        Map<String, Agg> byEmployer = aggregate(read, MonthEndStatistics::employerKey);
        Map<String, Agg> byGroup = aggregate(read, MonthEndStatistics::groupKey);
        List<Agg> groups = byGroup.values().stream().sorted(aggOrder()).toList();
        Agg total = new Agg("~TOTAL", "All groups", null);
        Agg core = new Agg("~CORE", "Core groups", null);
        for (Agg g : groups) {
            total.absorb(g);
            if (Boolean.TRUE.equals(g.core)) {
                core.absorb(g);
            }
        }
        List<Row> rows = new ArrayList<>();
        List<Row> groupRows = settledRows(read, groups, true, total.onRecord, cells);
        boolean anyHidden = groupRows.stream().anyMatch(r -> r.fewerThanFive() || r.withheld());
        for (int i = 0; i < groups.size(); i++) {
            Agg group = groups.get(i);
            Row groupRow = groupRows.get(i);
            rows.add(new Row(groupRow.key(), groupRow.label(), group.key.startsWith("~") ? Row.ROW : Row.GROUP,
                    groupRow.fewerThanFive(), groupRow.withheld(), groupRow.cells()));
            if (group.key.startsWith("~") || !read.grouped()) {
                continue;
            }
            List<Agg> employers = byEmployer.values().stream().filter(e -> group.key.equals(e.parent))
                    .sorted(aggOrder()).toList();
            List<Row> employerRows = settledRows(read, employers, true, group.onRecord, cells);
            boolean groupHidden = groupRow.fewerThanFive() || groupRow.withheld();
            for (Row employer : employerRows) {
                rows.add(groupHidden ? new Row(employer.key(), employer.label(), Row.ROW, employer.fewerThanFive(),
                        true, null) : employer);
            }
        }
        if (read.grouped() && !anyHidden) {
            rows.add(row(core, Row.TOTAL, cells));
        }
        return new Sheet(key, title, legacyTitle, columns, rows, row(total, Row.TOTAL, cells));
    }

    /** The month's leavers by SASBO's reasons, one row a place. */
    private Sheet reasonSheet(Read read, String key, String title, String legacyTitle, Function<Fact, String[]> place,
                              List<Column> columns, List<Integer> order, boolean guardedPlace) {
        Map<String, Agg> byPlace = aggregate(read, place);
        Map<Long, String> placeOfKey = new HashMap<>();
        for (Fact f : read.facts()) {
            placeOfKey.put(f.workplaceKey(), placeKey(read, place, f)[0]);
        }
        Map<String, long[]> reasons = new HashMap<>();
        for (Count c : read.counts(Measure.LEFT_BY_CODE)) {
            String at = placeOfKey.getOrDefault(c.workplaceKey(), NO_WORKPLACE);
            long[] cells = reasons.computeIfAbsent(at, k -> new long[order.size() + 2]);
            int column = c.code() == null ? -1 : order.indexOf(c.code());
            cells[column < 0 ? order.size() : column] += c.members();
            cells[order.size() + 1] += c.members();
        }
        List<Agg> ordered = byPlace.values().stream().filter(a -> reasons.containsKey(a.key) || !a.key.startsWith("~"))
                .sorted(aggOrder()).toList();
        Agg total = new Agg("~TOTAL", "Total", null);
        ordered.forEach(total::absorb);
        long[] totalReasons = new long[order.size() + 2];
        reasons.values().forEach(r -> {
            for (int i = 0; i < r.length; i++) {
                totalReasons[i] += r[i];
            }
        });
        Function<Agg, List<BigDecimal>> cells = a -> counts(a.key.equals("~TOTAL") ? totalReasons
                : reasons.getOrDefault(a.key, new long[order.size() + 2]));
        List<Row> rows = settledRows(read, ordered, guardedPlace, total.onRecord, cells);
        return new Sheet(key, title, legacyTitle, columns, rows, row(total, Row.TOTAL, cells));
    }

    /** resigned: the month's resignations by the reason given, one row an employer. */
    private Sheet resignedSheet(Read read, List<String> reasons) {
        List<Column> columns = new ArrayList<>();
        reasons.forEach(r -> columns.add(count("reason" + (columns.size() + 1), r)));
        columns.add(count("noReason", SPECIAL_LABELS.get(NO_REASON)));
        columns.add(count("total", "Total"));
        Map<String, Agg> byEmployer = aggregate(read, MonthEndStatistics::employerKey);
        Map<Long, String> employerOfKey = new HashMap<>();
        for (Fact f : read.facts()) {
            employerOfKey.put(f.workplaceKey(), placeKey(read, MonthEndStatistics::employerKey, f)[0]);
        }
        Map<String, long[]> given = new HashMap<>();
        long[] total = new long[reasons.size() + 2];
        for (Count c : read.counts(Measure.LEFT_BY_CODE)) {
            if (!Integer.valueOf(2).equals(c.code())) {
                continue;
            }
            String at = employerOfKey.getOrDefault(c.workplaceKey(), NO_WORKPLACE);
            long[] cells = given.computeIfAbsent(at, k -> new long[reasons.size() + 2]);
            int column = c.detail() == null ? -1 : reasons.indexOf(c.detail());
            int index = column < 0 ? reasons.size() : column;
            cells[index] += c.members();
            cells[reasons.size() + 1] += c.members();
            total[index] += c.members();
            total[reasons.size() + 1] += c.members();
        }
        List<Agg> ordered = byEmployer.values().stream().filter(a -> given.containsKey(a.key))
                .sorted(aggOrder()).toList();
        Agg sum = new Agg("~TOTAL", "Total", null);
        ordered.forEach(sum::absorb);
        Function<Agg, List<BigDecimal>> cells = a -> counts(a.key.equals("~TOTAL") ? total
                : given.getOrDefault(a.key, new long[reasons.size() + 2]));
        List<Row> rows = settledRows(read, ordered, true, sum.onRecord, cells);
        return new Sheet("resigned", "Who resigned, by the reason they gave",
                "SUMMARY BY BANK OF MEMBERS HAVING RESIGNED SOCIETY", columns, rows, row(sum, Row.TOTAL, cells));
    }

    /** prt_ra_by_tc: the recruits by table code -- the month, its share, the year, its share. */
    private Sheet recruitsByTableCode(Read read, List<Recruit> monthly, List<Recruit> yearly) {
        Map<String, Agg> byTableCode = aggregate(read, MonthEndStatistics::tableCodeKey);
        Map<String, long[]> recruits = new HashMap<>();
        Map<String, String> labels = new HashMap<>();
        long monthTotal = 0;
        long yearTotal = 0;
        for (int pass = 0; pass < 2; pass++) {
            for (Recruit r : pass == 0 ? monthly : yearly) {
                String key = !read.grouped() ? ALL : r.noWorkplace() ? NO_WORKPLACE
                        : r.tableCode() == null ? NO_TABLE_CODE : String.valueOf(r.tableCode());
                labels.putIfAbsent(key, r.tableCode() == null ? null : r.tableCode() + " " + r.tableCodeName());
                recruits.computeIfAbsent(key, k -> new long[2])[pass] += r.members();
                if (pass == 0) {
                    monthTotal += r.members();
                } else {
                    yearTotal += r.members();
                }
            }
        }
        for (String key : recruits.keySet()) {
            byTableCode.computeIfAbsent(key, k -> new Agg(k, labels.get(k), key.startsWith("~") ? null
                    : Integer.valueOf(key)));
        }
        long month = monthTotal;
        long year = yearTotal;
        List<Agg> ordered = byTableCode.values().stream().filter(a -> recruits.containsKey(a.key))
                .sorted(aggOrder()).toList();
        Agg total = new Agg("~TOTAL", "Total", null);
        ordered.forEach(total::absorb);
        Function<Agg, List<BigDecimal>> cells = a -> {
            long[] r = a.key.equals("~TOTAL") ? new long[] {month, year} : recruits.getOrDefault(a.key, new long[2]);
            return cells(n(r[0]), share(r[0], month), n(r[1]), share(r[1], year));
        };
        List<Row> rows = settledRows(read, ordered, true, total.onRecord, cells);
        return new Sheet("by-table-code", "Recruitment, by table code",
                "ANALYSIS OF MEMBERS RECRUITED BY BANK AND REPRESENTATIVE", List.of(count("month", "The month"),
                share("monthShare", "Share of the month"), count("year", "The year"),
                share("yearShare", "Share of the year")), rows, row(total, Row.TOTAL, cells));
    }

    /** A sheet whose rows are codes or people, not places: never guarded, and the same at every switch. */
    private static Sheet codeSheet(String key, String title, String legacyTitle, List<Column> columns,
                                   Map<String, long[]> values, Map<String, String> labels) {
        List<Row> rows = new ArrayList<>();
        long[] total = new long[columns.size()];
        List<String> keys = values.keySet().stream().sorted(codeOrder(labels)).toList();
        for (String row : keys) {
            long[] cells = values.get(row);
            rows.add(new Row(row, labels.getOrDefault(row, label(row, row)), Row.ROW, false, false, counts(cells)));
            for (int i = 0; i < total.length; i++) {
                total[i] += cells[i];
            }
        }
        return new Sheet(key, title, legacyTitle, columns, rows, new Row("~TOTAL", "Total", Row.TOTAL, false, false,
                counts(total)));
    }

    // ================================================================================================ places and sums

    /** A row being summed: a place, what it prints, where it sorts, and the month's figures added up. */
    static final class Agg {
        final String key;
        final String label;
        final Integer order;
        String parent;
        Boolean core;
        long onRecord;
        long suspended;
        long paying;
        long joined;
        long left;
        long byDebitOrder;
        long byDeduction;
        long byOther;
        long representatives;
        long committeeMembers;
        long memnetMembers;
        long workplaces;
        long withRepresentatives;
        long withCommitteeMembers;
        long withMemnetMembers;
        long complement;
        long coreComplement;
        long corePaying;
        BigDecimal debitOrderRand = BigDecimal.ZERO.setScale(2);
        BigDecimal deductionRand = BigDecimal.ZERO.setScale(2);
        BigDecimal otherRand = BigDecimal.ZERO.setScale(2);

        Agg(String key, String label, Integer order) {
            this.key = key;
            this.label = label;
            this.order = order;
        }

        void add(Fact f) {
            onRecord += f.onRecord();
            suspended += f.suspended();
            paying += f.paying();
            joined += f.joined();
            left += f.left();
            byDebitOrder += f.byDebitOrder();
            byDeduction += f.byDeduction();
            byOther += f.byOther();
            representatives += f.representatives();
            committeeMembers += f.committeeMembers();
            memnetMembers += f.memnetMembers();
            if (f.workplaceKey() != 0) {
                workplaces++;
                withRepresentatives += f.representatives() > 0 ? 1 : 0;
                withCommitteeMembers += f.committeeMembers() > 0 ? 1 : 0;
                withMemnetMembers += f.memnetMembers() > 0 ? 1 : 0;
            }
            long staff = f.complement() == null ? 0 : f.complement();
            complement += staff;
            if (Boolean.TRUE.equals(f.groupCore())) {
                coreComplement += staff;
                corePaying += f.paying();
            }
            debitOrderRand = debitOrderRand.add(f.debitOrderRand());
            deductionRand = deductionRand.add(f.deductionRand());
            otherRand = otherRand.add(f.otherRand());
        }

        void absorb(Agg a) {
            onRecord += a.onRecord;
            suspended += a.suspended;
            paying += a.paying;
            joined += a.joined;
            left += a.left;
            byDebitOrder += a.byDebitOrder;
            byDeduction += a.byDeduction;
            byOther += a.byOther;
            representatives += a.representatives;
            committeeMembers += a.committeeMembers;
            memnetMembers += a.memnetMembers;
            workplaces += a.workplaces;
            withRepresentatives += a.withRepresentatives;
            withCommitteeMembers += a.withCommitteeMembers;
            withMemnetMembers += a.withMemnetMembers;
            complement += a.complement;
            coreComplement += a.coreComplement;
            corePaying += a.corePaying;
            debitOrderRand = debitOrderRand.add(a.debitOrderRand);
            deductionRand = deductionRand.add(a.deductionRand);
            otherRand = otherRand.add(a.otherRand);
        }

        BigDecimal subscriptions() {
            return debitOrderRand.add(deductionRand).add(otherRand);
        }
    }

    /** The month's rows summed by place; below {@code all}, one row. */
    private static Map<String, Agg> aggregate(Read read, Function<Fact, String[]> place) {
        Map<String, Agg> byPlace = new LinkedHashMap<>();
        for (Fact f : read.facts()) {
            String[] at = placeKey(read, place, f);
            Agg agg = byPlace.computeIfAbsent(at[0], k -> new Agg(k, at[1], at[2] == null ? null : Integer.valueOf(at[2])));
            agg.parent = at.length > 3 ? at[3] : null;
            agg.core = f.groupCore();
            agg.add(f);
        }
        return byPlace;
    }

    /** {key, label, order, parent} of a row's place -- through the workplace (rule G), or the one row below all. */
    private static String[] placeKey(Read read, Function<Fact, String[]> place, Fact f) {
        if (!read.grouped()) {
            return new String[] {ALL, SPECIAL_LABELS.get(ALL), null};
        }
        if (f.workplaceKey() == 0) {
            return new String[] {NO_WORKPLACE, SPECIAL_LABELS.get(NO_WORKPLACE), null};
        }
        return place.apply(f);
    }

    static String[] areaKey(Fact f) {
        return f.areaLetter() == null ? new String[] {NO_AREA, SPECIAL_LABELS.get(NO_AREA), null}
                : new String[] {f.areaLetter(), f.areaLetter() + (f.regionName() == null ? "" : " - " + f.regionName()),
                        null};
    }

    static String[] tableCodeKey(Fact f) {
        return f.tableCode() == null ? new String[] {NO_TABLE_CODE, SPECIAL_LABELS.get(NO_TABLE_CODE), null}
                : new String[] {String.valueOf(f.tableCode()), f.tableCode() + " " + f.tableCodeName(),
                        String.valueOf(f.tableCode())};
    }

    static String[] employerKey(Fact f) {
        if (f.tableCode() == null) {
            return new String[] {NO_TABLE_CODE, SPECIAL_LABELS.get(NO_TABLE_CODE), null};
        }
        if (f.employerOrdinal() == null) {
            return new String[] {NO_EMPLOYER, SPECIAL_LABELS.get(NO_EMPLOYER), null};
        }
        return new String[] {"E" + f.employerOrdinal(), f.employerName(), String.valueOf(f.employerOrdinal()),
                "G" + f.groupOrdinal()};
    }

    static String[] groupKey(Fact f) {
        if (f.tableCode() == null) {
            return new String[] {NO_TABLE_CODE, SPECIAL_LABELS.get(NO_TABLE_CODE), null};
        }
        if (f.employerOrdinal() == null || f.groupOrdinal() == null) {
            return new String[] {NO_EMPLOYER, SPECIAL_LABELS.get(NO_EMPLOYER), null};
        }
        return new String[] {"G" + f.groupOrdinal(), f.groupName(), String.valueOf(f.groupOrdinal())};
    }

    /** Rule C over one sum: the rows' states, and each row answered as its state allows. */
    private static List<Row> settledRows(Read read, List<Agg> ordered, boolean guardedPlace, long total,
                                         Function<Agg, List<BigDecimal>> cells) {
        long[] members = new long[ordered.size()];
        boolean[] guarded = new boolean[ordered.size()];
        for (int i = 0; i < ordered.size(); i++) {
            members[i] = ordered.get(i).onRecord;
            guarded[i] = guardedPlace && read.grouped() && !ordered.get(i).key.startsWith("~");
        }
        SmallCounts.State[] states = SmallCounts.settle(members, guarded, total);
        List<Row> rows = new ArrayList<>();
        for (int i = 0; i < ordered.size(); i++) {
            Agg a = ordered.get(i);
            boolean fewer = states[i] == SmallCounts.State.FEWER_THAN_FIVE;
            boolean withheld = states[i] == SmallCounts.State.WITHHELD;
            rows.add(new Row(a.key, label(a.key, a.label), Row.ROW, fewer, withheld,
                    fewer || withheld ? null : cells.apply(a)));
        }
        return rows;
    }

    private static Row row(Agg a, String level, Function<Agg, List<BigDecimal>> cells) {
        return new Row(a.key, a.label, level, false, false, cells.apply(a));
    }

    /** The places in their lists' order -- a group's, an employer's, a table code's -- areas A to X, then Y, Z, XX. */
    private static Comparator<Agg> aggOrder() {
        return Comparator.<Agg>comparingInt(a -> a.key.startsWith("~") ? 1 + SPECIAL_ORDER.indexOf(a.key) : 0)
                .thenComparing(a -> a.order == null ? Integer.MAX_VALUE : a.order)
                .thenComparing(a -> a.key.length())
                .thenComparing(a -> a.key);
    }

    private static Comparator<String> areaOrder() {
        return Comparator.<String>comparingInt(k -> k.startsWith("~") ? 1 + SPECIAL_ORDER.indexOf(k) : 0)
                .thenComparingInt(String::length)
                .thenComparing(Comparator.naturalOrder());
    }

    /** Codes by their numbers, people and channels by their names; the kinds of none last. */
    private static Comparator<String> codeOrder(Map<String, String> labels) {
        return Comparator.<String>comparingInt(k -> k.startsWith("~") ? 1 + SPECIAL_ORDER.indexOf(k) : 0)
                .thenComparing(k -> !k.isEmpty() && k.chars().allMatch(Character::isDigit) ? String.format("%012d", Long.parseLong(k))
                        : "~" + labels.getOrDefault(k, k));
    }

    private static Map<String, long[]> reorder(Map<String, long[]> values, int... order) {
        Map<String, long[]> out = new LinkedHashMap<>();
        values.forEach((k, v) -> {
            long[] o = new long[order.length];
            for (int i = 0; i < order.length; i++) {
                o[i] = v[order[i]];
            }
            out.put(k, o);
        });
        return out;
    }

    // ================================================================================================ small helpers

    private MonthEndStatisticDto answer(String statistic, List<String> legacy, Read read, List<Sheet> sheets,
                                        List<String> notes) {
        return new MonthEndStatisticDto(statistic, legacy, read.month().toString(),
                read.taken() == null ? MonthEndMonthDto.OPEN : MonthEndMonthDto.TAKEN,
                read.taken() == null ? null : read.taken().takenAt().toString(),
                read.taken() == null ? null : read.taken().takenBy(), read.area(), read.region(),
                workplaceSwitch.mode().name(), sheets, notes);
    }

    private List<String> notes(Read read) {
        List<String> notes = new ArrayList<>();
        notes.add(read.taken() == null
                ? monthName(read.month()) + " is open: these figures are worked out as they stand now."
                : monthName(read.month()) + " was taken on " + read.taken().takenAt().atZone(MonthRange.ZONE)
                        .toLocalDate() + ": these figures are the month as it stood then.");
        if (!read.grouped()) {
            notes.add("SASBO's workplaces are not served in full here, so no figure is cut by where members work, "
                    + "and nothing made from a staff number is shown.");
        } else {
            notes.add("A membership of one to four at a named employer or table code is shown as fewer than five until "
                    + "sign-in exists, and one row beside it is withheld so it cannot be worked out.");
        }
        return notes;
    }

    private static String monthName(YearMonth month) {
        return month.getMonth().getDisplayName(TextStyle.FULL, Locale.ENGLISH) + " " + month.getYear();
    }

    private static String label(String key, String given) {
        return key.startsWith("~") ? SPECIAL_LABELS.getOrDefault(key, key) : given;
    }

    private static Column count(String key, String label) {
        return new Column(key, label, Column.COUNT);
    }

    private static Column rand(String key, String label) {
        return new Column(key, label, Column.RAND);
    }

    private static Column share(String key, String label) {
        return new Column(key, label, Column.SHARE);
    }

    private static BigDecimal n(long value) {
        return BigDecimal.valueOf(value);
    }

    /** A share of one, to four places; none where there is nothing to share. */
    private static BigDecimal share(long part, long whole) {
        return whole <= 0 ? null : BigDecimal.valueOf(part).divide(BigDecimal.valueOf(whole), 4, RoundingMode.HALF_UP);
    }

    /** Cells in the columns' order; a {@code null} is a cell with no meaning. */
    private static List<BigDecimal> cells(BigDecimal... values) {
        return Arrays.asList(values);
    }

    private static List<BigDecimal> counts(long[] values) {
        List<BigDecimal> out = new ArrayList<>();
        for (long v : values) {
            out.add(BigDecimal.valueOf(v));
        }
        return out;
    }

    /** An area letter or a region code: trimmed, upper-cased, refused above its ceiling naming the field only. */
    private static String code(String field, String value, int ceiling) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim().toUpperCase(Locale.ROOT);
        if (trimmed.length() > ceiling) {
            throw new MonthEndRefusal(MonthEndRefusalCode.REQUEST_MALFORMED,
                    "This request could not be read. Send each field in the form it is expected in.",
                    Map.of("field", field));
        }
        return trimmed;
    }

    private static MonthEndRefusal unavailable() {
        return new MonthEndRefusal(MonthEndRefusalCode.SUMMARY_UNAVAILABLE,
                "The statistics are taking too long to work out just now. Nothing is wrong with the members' records. "
                        + "Try again in a moment.");
    }
}
