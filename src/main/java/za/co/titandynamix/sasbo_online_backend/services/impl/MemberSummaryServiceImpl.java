package za.co.titandynamix.sasbo_online_backend.services.impl;

import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.stereotype.Service;
import za.co.titandynamix.sasbo_online_backend.config.SasboEnvironment;
import za.co.titandynamix.sasbo_online_backend.config.WorkplaceSwitch;
import za.co.titandynamix.sasbo_online_backend.dto.summary.MemberSummaryDto;
import za.co.titandynamix.sasbo_online_backend.enums.SummaryGrouping;
import za.co.titandynamix.sasbo_online_backend.exception.MonthEndRefusal;
import za.co.titandynamix.sasbo_online_backend.exception.MonthEndRefusalCode;
import za.co.titandynamix.sasbo_online_backend.repository.MemberSummaryRepository;
import za.co.titandynamix.sasbo_online_backend.services.MemberSummaryService;
import za.co.titandynamix.sasbo_online_backend.services.SummaryGate;
import za.co.titandynamix.sasbo_online_backend.utils.MonthRange;
import za.co.titandynamix.sasbo_online_backend.utils.Penetration;
import za.co.titandynamix.sasbo_online_backend.utils.SeedMemberNumbers;
import za.co.titandynamix.sasbo_online_backend.utils.SmallCounts;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * The figures of a month (02.2): the rules are {@code MemberSummaryRepository}'s one statement; this is what is asked
 * of it, and what is answered from it.
 *
 * <ul>
 *   <li>THE MONTH is the one a request names, or the clock's in SASBO's zone -- never the browser's (MD55, rule M);</li>
 *   <li>THE SWITCH decides whether members are placed through their workplace at all: only at {@code all}; elsewhere
 *       the answer is the totals and one row, with no complement and no penetration (MD62);</li>
 *   <li>AT MOST TWO figure queries run at once, and each is bounded: a third, or one that outlives its timeout, is
 *       {@code SUMMARY_UNAVAILABLE} at once, with {@code Retry-After} (MD47, MD63);</li>
 *   <li>RULE C is applied here, to the rows that name an employer or a table code; the totals are never
 *       suppressed.</li>
 * </ul>
 * A query slower than a second says so in one line at {@code INFO} -- its milliseconds, its grouping, its month, its
 * rows; never a value (MR-PERF-16).
 */
@Slf4j
@Service
public class MemberSummaryServiceImpl implements MemberSummaryService {

    /** The line a slow figure query writes, above this. */
    static final long SLOW_MILLIS = 1_000;

    /** The longest area letter and region code a request may name (MR-SEC-22). */
    static final int AREA_CEILING = 4;
    static final int REGION_CEILING = 8;

    /** The longest unknown status value an answer carries: the value is data, and an answer is not a place for more. */
    static final int STATUS_CEILING = 40;

    private static final Map<String, String> SPECIAL_LABELS = Map.of(
            MemberSummaryRepository.NO_TIER, "All other institutions",
            MemberSummaryRepository.NO_TABLE_CODE, "No table code on record",
            MemberSummaryRepository.NO_EMPLOYER, "No employer on record",
            MemberSummaryRepository.NO_REGION, "No region on record",
            MemberSummaryRepository.NO_WORKPLACE, "No workplace on record");

    private static final List<String> SPECIAL_ORDER = List.of(MemberSummaryRepository.NO_TIER,
            MemberSummaryRepository.NO_TABLE_CODE, MemberSummaryRepository.NO_EMPLOYER, MemberSummaryRepository.NO_REGION,
            MemberSummaryRepository.NO_WORKPLACE);

    private final MemberSummaryRepository repository;
    private final WorkplaceSwitch workplaceSwitch;
    private final SasboEnvironment environment;
    private final SeedMemberNumbers seedNumbers;
    private final SummaryGate gate;
    private final Clock clock;

    public MemberSummaryServiceImpl(MemberSummaryRepository repository, WorkplaceSwitch workplaceSwitch,
                                    SasboEnvironment environment, SeedMemberNumbers seedNumbers, SummaryGate gate,
                                    Clock clock) {
        this.repository = repository;
        this.workplaceSwitch = workplaceSwitch;
        this.environment = environment;
        this.seedNumbers = seedNumbers;
        this.gate = gate;
        this.clock = clock;
    }

    @Override
    public MemberSummaryDto summary(String month, String by, String area, String region) {
        SummaryGrouping grouping = grouping(by);
        String areaCode = code("area", area, AREA_CEILING);
        String regionCode = code("region", region, REGION_CEILING);
        if (areaCode != null && regionCode != null) {
            throw new MonthEndRefusal(MonthEndRefusalCode.REQUEST_MALFORMED,
                    "Ask for an area or a region, not both.", Map.of("field", "region"));
        }
        YearMonth asked = MonthRange.asked(month, clock);
        boolean grouped = workplaceSwitch.staffNumbersServed();
        boolean courtesy = SmallCounts.appliesToTheCaller();
        // The calendar month: the statement works out the window it counts from the takes (MEN-14), in itself.
        MemberSummaryRepository.Ask ask = new MemberSummaryRepository.Ask(MonthRange.of(asked), grouping, grouped,
                grouped, areaCode, regionCode, environment.isProduction() ? List.of() : seedNumbers.all());

        if (!gate.tryEnter()) {
            throw unavailable();
        }
        long started = System.nanoTime();
        List<MemberSummaryRepository.Line> lines;
        try {
            lines = repository.figures(ask);
        } catch (QueryTimeoutException | DataAccessResourceFailureException tooLong) {
            // A statement cancelled at its timeout (57014) reaches here as either, as the driver words it.
            throw unavailable();
        } finally {
            gate.leave();
        }
        long millis = (System.nanoTime() - started) / 1_000_000;
        if (millis > SLOW_MILLIS) {
            log.info("The figures took {} ms: grouping {}, month {}, {} lines", millis, grouping.wire(), asked,
                    lines.size());
        }
        return answer(asked, month == null || month.isBlank(), grouping, grouped, courtesy, areaCode, regionCode, lines);
    }

    private MemberSummaryDto answer(YearMonth asked, boolean fromTheServer, SummaryGrouping grouping, boolean grouped,
                                    boolean courtesy, String area, String region,
                                    List<MemberSummaryRepository.Line> lines) {
        Map<String, MemberSummaryRepository.Line> members = new LinkedHashMap<>();
        Map<String, MemberSummaryRepository.Line> complements = new LinkedHashMap<>();
        List<MemberSummaryDto.UnknownStatus> unknown = new ArrayList<>();
        MemberSummaryRepository.Line check = null;
        for (MemberSummaryRepository.Line line : lines) {
            switch (line.part()) {
                case MemberSummaryRepository.ROW -> members.put(line.key(), line);
                case MemberSummaryRepository.COMPLEMENT -> complements.put(line.key(), line);
                case MemberSummaryRepository.UNKNOWN_STATUS -> unknown.add(new MemberSummaryDto.UnknownStatus(
                        shortened(line.status()), line.known()));
                case MemberSummaryRepository.CHECK -> check = line;
                default -> throw new IllegalStateException("a part the statement does not write");
            }
        }
        if (check != null && check.known() == 0) {
            throw new MonthEndRefusal(MonthEndRefusalCode.SUMMARY_AREA_UNKNOWN,
                    area != null ? "That area is not on record here." : "That region is not on record here.",
                    Map.of("field", area != null ? "area" : "region"));
        }

        List<String> keys = new ArrayList<>(members.keySet());
        complements.keySet().stream().filter(k -> !members.containsKey(k)).forEach(keys::add);
        keys.sort(rowOrder(members, complements));

        long[] onRecord = new long[keys.size()];
        boolean[] guarded = new boolean[keys.size()];
        List<MemberSummaryDto.Figures> rowFigures = new ArrayList<>();
        long[] sums = new long[10];
        long complementTotal = 0;
        long seeded = 0;
        long noTableCode = 0;
        LocalDate membersFrom = null;
        for (int i = 0; i < keys.size(); i++) {
            MemberSummaryRepository.Line line = members.get(keys.get(i));
            MemberSummaryRepository.Line complementLine = complements.get(keys.get(i));
            Long complement = grouped ? (complementLine == null ? 0L : complementLine.complement()) : null;
            MemberSummaryDto.Figures row = line == null
                    ? figures(0, 0, 0, 0, 0, 0, 0, 0, complement)
                    : figures(line.onRecord(), line.suspended(), line.joined(), line.left(), line.reinstated(),
                            line.opening(), line.joinedYearToDate(), line.leftYearToDate(), complement);
            rowFigures.add(row);
            onRecord[i] = row.onRecord();
            // Rule C holds until sign-in (MD27): a caller signed in with the month's figures is answered them exactly.
            guarded[i] = courtesy && grouping.namesAnEmployer() && grouped && !keys.get(i).startsWith("~");
            long[] parts = {row.onRecord(), row.suspended(), row.joined(), row.left(), row.reinstated(), row.opening(),
                    row.joinedYearToDate(), row.leftYearToDate()};
            for (int p = 0; p < parts.length; p++) {
                sums[p] += parts[p];
            }
            complementTotal += complement == null ? 0 : complement;
            if (line != null) {
                seeded += line.seeded();
                noTableCode += line.noTableCode();
                if (line.membersFrom() != null && (membersFrom == null || line.membersFrom().isBefore(membersFrom))) {
                    membersFrom = line.membersFrom();
                }
            }
        }
        // The rows read only the members a figure can count (MV-26), so the earliest Date Joined of all -- a leaver's, as
        // often as not -- comes on the check line for a month worked out now; a month taken answers its facts' own.
        if (check != null && check.membersFrom() != null
                && (membersFrom == null || check.membersFrom().isBefore(membersFrom))) {
            membersFrom = check.membersFrom();
        }
        MemberSummaryDto.Figures totals = figures(sums[0], sums[1], sums[2], sums[3], sums[4], sums[5], sums[6],
                sums[7], grouped ? complementTotal : null);

        SmallCounts.State[] states = SmallCounts.settle(onRecord, guarded, totals.onRecord());
        List<MemberSummaryDto.Row> rows = new ArrayList<>();
        for (int i = 0; i < keys.size(); i++) {
            String key = keys.get(i);
            MemberSummaryRepository.Line named = members.containsKey(key) ? members.get(key) : complements.get(key);
            boolean fewerThanFive = states[i] == SmallCounts.State.FEWER_THAN_FIVE;
            boolean withheld = states[i] == SmallCounts.State.WITHHELD;
            rows.add(new MemberSummaryDto.Row(key, label(key, named), grouping == SummaryGrouping.GROUP && grouped
                    && !key.startsWith("~") ? Boolean.valueOf(named.core()) : null, fewerThanFive, withheld,
                    fewerThanFive || withheld ? null : rowFigures.get(i)));
        }

        unknown.sort(Comparator.comparing(MemberSummaryDto.UnknownStatus::status));
        Long noWorkplace = grouped ? rows.stream()
                .filter(r -> r.key().equals(MemberSummaryRepository.NO_WORKPLACE))
                .findFirst().map(r -> rowFigures.get(rows.indexOf(r)).onRecord()).orElse(0L) : null;
        // Rule F: a month taken was placed by where its members worked that day, and is read from what was frozen.
        java.time.Instant takenAt = check == null ? null : check.takenAt();
        String groupedAsAt = takenAt != null ? takenAt.atZone(MonthRange.ZONE).toLocalDate().toString()
                : LocalDate.now(clock.withZone(MonthRange.ZONE)).toString();
        // MEN-26: a figure above 100 % is shown and flagged, and the answer says why -- once, whatever is flagged.
        boolean outOfDate = totals.complementOutOfDate()
                || rows.stream().anyMatch(r -> r.figures() != null && r.figures().complementOutOfDate());
        return new MemberSummaryDto(asked.toString(), fromTheServer, grouping.wire(), area, region,
                workplaceSwitch.mode().name(), groupedAsAt, totals, rows,
                new MemberSummaryDto.Unplaced(unknown, noWorkplace, noTableCode),
                environment.isProduction() ? 0 : seeded,
                membersFrom == null ? null : membersFrom.toString(),
                check == null || check.leaversFrom() == null ? null
                        : check.leaversFrom().atZone(MonthRange.ZONE).toLocalDate().toString(),
                takenAt == null ? null : takenAt.toString(), check == null ? null : check.takenBy(),
                outOfDate ? List.of(Penetration.OUT_OF_DATE_NOTE) : List.of());
    }

    /** One row's figures, or the whole's: paying and the movement's leftover worked out, the penetration with them. */
    static MemberSummaryDto.Figures figures(long onRecord, long suspended, long joined, long left, long reinstated,
                                            long opening, long joinedYearToDate, long leftYearToDate, Long complement) {
        long paying = onRecord - suspended;
        long unexplained = onRecord - (opening + joined - left + reinstated);
        BigDecimal penetration = Penetration.of(paying, complement);
        return new MemberSummaryDto.Figures(onRecord, suspended, paying, joined, left, reinstated, opening,
                unexplained, joinedYearToDate, leftYearToDate, complement, penetration,
                Penetration.complementOutOfDate(paying, complement));
    }

    /** Rows in their list's order -- a group's, an employer's, a table code's -- then by key; the kinds of none last. */
    private static Comparator<String> rowOrder(Map<String, MemberSummaryRepository.Line> members,
                                               Map<String, MemberSummaryRepository.Line> complements) {
        return Comparator.<String>comparingInt(key -> key.startsWith("~") ? 1 + SPECIAL_ORDER.indexOf(key) : 0)
                .thenComparing(key -> {
                    MemberSummaryRepository.Line line = members.containsKey(key) ? members.get(key) : complements.get(key);
                    return line == null || line.order() == null ? Integer.MAX_VALUE : line.order();
                })
                .thenComparing(Comparator.naturalOrder());
    }

    private static String label(String key, MemberSummaryRepository.Line named) {
        if (key.startsWith("~")) {
            return SPECIAL_LABELS.getOrDefault(key, key);
        }
        return named == null || named.label() == null ? key : named.label();
    }

    private static String shortened(String status) {
        return status.length() > STATUS_CEILING ? status.substring(0, STATUS_CEILING) : status;
    }

    private static SummaryGrouping grouping(String by) {
        if (by == null || by.isBlank()) {
            return SummaryGrouping.GROUP;
        }
        return SummaryGrouping.fromWire(by.trim()).orElseThrow(() -> new MonthEndRefusal(
                MonthEndRefusalCode.SUMMARY_GROUPING_UNKNOWN,
                "The figures are grouped by group, employer, table code, area, region or committee.",
                Map.of("known", Arrays.stream(SummaryGrouping.values()).map(SummaryGrouping::wire)
                        .collect(Collectors.joining(",")))));
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
                "The figures are taking too long to work out just now. Nothing is wrong with the members' records. "
                        + "Try again in a moment.");
    }
}
