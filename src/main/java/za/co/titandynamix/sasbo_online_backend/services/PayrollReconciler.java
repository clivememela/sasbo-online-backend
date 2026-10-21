package za.co.titandynamix.sasbo_online_backend.services;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import za.co.titandynamix.sasbo_online_backend.enums.PayrollDiscrepancyStatus;
import za.co.titandynamix.sasbo_online_backend.enums.PayrollDiscrepancyType;
import za.co.titandynamix.sasbo_online_backend.enums.PayrollFormat;
import za.co.titandynamix.sasbo_online_backend.enums.PayrollOutcome;
import za.co.titandynamix.sasbo_online_backend.payroll.FileSniffer;
import za.co.titandynamix.sasbo_online_backend.payroll.GridReader;
import za.co.titandynamix.sasbo_online_backend.payroll.PayrollKeys;
import za.co.titandynamix.sasbo_online_backend.payroll.PayrollLayout;
import za.co.titandynamix.sasbo_online_backend.payroll.PayrollLayouts;
import za.co.titandynamix.sasbo_online_backend.payroll.PayrollMatcher;
import za.co.titandynamix.sasbo_online_backend.payroll.PayrollMoney;
import za.co.titandynamix.sasbo_online_backend.payroll.PayrollParser;
import za.co.titandynamix.sasbo_online_backend.repository.PayrollStore;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.Function;

/**
 * One comparison of a payroll file with SASBO's members: read the file, read what it is compared with in a handful of
 * statements, compare in memory ({@link PayrollMatcher}), and write what was found in batches.
 *
 * <p>Reading and comparing take no transaction; {@link #write} runs inside the caller's, after the file is claimed, so
 * a comparison lands whole or not at all. A discrepancy found again keeps its notes and status; one no longer found is
 * resolved by the system, never deleted (PAY-20). The payment check (PAY-28) is worked out again at the end.
 */
@Service
public class PayrollReconciler {

    /** Who the system is, in the record, when it closes a discrepancy itself. */
    public static final String SYSTEM = "system";

    private static final ObjectMapper JSON = new ObjectMapper();

    private final PayrollStore store;
    private final Clock clock;

    public PayrollReconciler(PayrollStore store, Clock clock) {
        this.store = store;
        this.clock = clock;
    }

    /** A file read and compared, ready to be written. */
    public record Compared(PayrollParser.Parsed parsed, PayrollMatcher.Result result,
                           List<PayrollMatcher.Found> fileLevel) {
    }

    /** Reads the file: by the typed layout for typed rows, else the employer's, with a column mapping where one is set. */
    public PayrollParser.Parsed parse(PayrollFormat format, byte[] bytes, PayrollLayout layout,
                                      Map<String, String> mapping, YearMonth period, boolean typed) {
        FileSniffer.Kind kind = switch (format) {
            case XLSX -> FileSniffer.Kind.XLSX;
            case XLS -> FileSniffer.Kind.XLS;
            case CSV -> FileSniffer.Kind.CSV;
        };
        PayrollParser.Parsed parsed = PayrollParser.parse(GridReader.read(kind, bytes),
                typed ? PayrollLayouts.typed() : layout, typed ? Map.of() : mapping, period);
        if (!typed) {
            return parsed;
        }
        // Typed rows are numbered as they were typed: the first typed row is row 1, under the heading the system wrote.
        List<PayrollParser.Line> renumbered = parsed.lines().stream().map(line -> new PayrollParser.Line(
                line.lineNumber() - 1, line.staffNumber(), line.staffKey(), line.idNumber(), line.idKey(), line.name(),
                line.amount(), line.forPeriod(), line.valid(), line.problem(), line.raw())).toList();
        return new PayrollParser.Parsed(parsed.sheet(), parsed.headingRow(), parsed.headingCells(), parsed.headings(),
                renumbered, parsed.statedTotal(), parsed.rowsNotRead());
    }

    /** Compares the lines with the employer's members, the month's subscription and the months before. */
    public Compared compare(long employerId, String employerName, YearMonth period, PayrollParser.Parsed parsed,
                            BigDecimal statedTotal) {
        List<PayrollMatcher.Member> members = store.employerMembers(employerId);
        Map<String, PayrollMatcher.Member> byId = store.membersByIdKey(
                parsed.lines().stream().map(PayrollParser.Line::idKey).filter(k -> k != null).toList(), members);
        Function<YearMonth, Optional<BigDecimal>> rate = rateFunction(store.rates());
        PayrollMatcher.Result result = PayrollMatcher.match(parsed.lines(), new PayrollMatcher.Inputs(period, members,
                byId, rate, store.history(employerId, period), employerName));
        List<PayrollMatcher.Found> fileLevel = new ArrayList<>();
        BigDecimal stated = statedTotal != null ? statedTotal : parsed.statedTotal();
        if (stated != null && stated.subtract(result.fileTotal()).abs().compareTo(new BigDecimal("0.005")) >= 0) {
            BigDecimal difference = stated.subtract(result.fileTotal());
            fileLevel.add(new PayrollMatcher.Found(PayrollDiscrepancyType.FILE_TOTAL_DIFFERS, "F", null, null, stated,
                    result.fileTotal(), difference, null, "The file states a total of R" + PayrollKeys.money(stated)
                    + "; its lines come to R" + PayrollKeys.money(result.fileTotal()) + ": R"
                    + PayrollKeys.money(difference.abs()) + (difference.signum() > 0 ? " more" : " less")
                    + " than the lines."));
        }
        return new Compared(parsed, result, fileLevel);
    }

    /** The rate in force for a month: the latest dated rate on or before it, or none (never another month's). */
    static Function<YearMonth, Optional<BigDecimal>> rateFunction(Map<YearMonth, BigDecimal> rates) {
        TreeMap<YearMonth, BigDecimal> sorted = new TreeMap<>(rates);
        return month -> Optional.ofNullable(sorted.floorEntry(month)).map(Map.Entry::getValue);
    }

    /**
     * Writes a comparison onto the file -- inside the caller's transaction, the file already claimed. Lines and matches
     * are written afresh; discrepancies are found again by what they are about.
     */
    public void write(long fileId, long employerId, YearMonth period, Compared compared, BigDecimal feePercent,
                      String mappingJson, BigDecimal statedTotal, String actor) {
        Instant now = clock.instant();
        PayrollParser.Parsed parsed = compared.parsed();
        PayrollMatcher.Result result = compared.result();
        store.deleteLines(fileId);
        store.insertRows(fileId, parsed.lines());
        Map<Integer, Long> rowIds = store.rowIds(fileId);
        store.insertMatches(fileId, result.matches(), rowIds);

        List<PayrollMatcher.Found> found = new ArrayList<>(result.found());
        found.addAll(compared.fileLevel());
        upsert(fileId, employerId, period, found, rowIds, now, false);

        Map<String, Object> figures = new LinkedHashMap<>();
        figures.put("sheet", parsed.sheet());
        figures.put("headingRow", parsed.headingRow());
        figures.put("headings", json(parsed.headings()));
        figures.put("headingCells", cap(PayrollLayouts.listJson(parsed.headingCells()), 4000));
        figures.put("mapping", mappingJson);
        figures.put("statedTotal", statedTotal != null ? statedTotal : parsed.statedTotal());
        figures.put("rows", parsed.lines().size());
        figures.put("unreadable", (int) parsed.lines().stream().filter(l -> !l.valid()).count());
        figures.put("notRead", parsed.rowsNotRead());
        figures.put("counts", counts(parsed, result));
        figures.put("matched", result.matched());
        figures.put("fileTotal", result.fileTotal());
        figures.put("rate", result.rate().orElse(null));
        figures.put("rateFrom", rateFrom(period));
        figures.put("expectedTotal", result.expectedTotal());
        figures.put("fee", feePercent);
        figures.put("at", Timestamp.from(now));
        figures.put("by", actor);
        store.writeFigures(fileId, figures);
        checkPayment(fileId, employerId, period, result.fileTotal(), feePercent, actor);
        store.recount(fileId);
    }

    /** The effective-from day of the rate in force for the month, or null. */
    private Date rateFrom(YearMonth period) {
        TreeMap<YearMonth, BigDecimal> rates = new TreeMap<>(store.rates());
        Map.Entry<YearMonth, BigDecimal> entry = rates.floorEntry(period);
        return entry == null ? null : Date.valueOf(entry.getKey().atDay(1));
    }

    /**
     * Writes discrepancies found: new ones OPEN; ones found again brought up to date with their notes and status kept
     * (one the system had closed opens again); and -- unless {@code paymentOnly} -- every other one the comparison no
     * longer finds resolved NO_LONGER_APPLIES. The payment check's own discrepancy is left to {@link #checkPayment}.
     */
    void upsert(long fileId, long employerId, YearMonth period, List<PayrollMatcher.Found> found,
                Map<Integer, Long> rowIds, Instant now, boolean paymentOnly) {
        Map<String, PayrollStore.Existing> existing = new HashMap<>();
        for (PayrollStore.Existing d : store.discrepanciesOf(fileId)) {
            existing.put(d.type() + "|" + d.subjectKey(), d);
        }
        List<PayrollMatcher.Found> inserts = new ArrayList<>();
        Map<Long, PayrollMatcher.Found> updates = new LinkedHashMap<>();
        Map<Long, Boolean> reopen = new HashMap<>();
        Map<Long, Long> queryOf = new HashMap<>();
        for (PayrollMatcher.Found f : found) {
            PayrollStore.Existing d = existing.remove(f.type().name() + "|" + f.subjectKey());
            if (d == null) {
                inserts.add(f);
                continue;
            }
            updates.put(d.id(), f);
            queryOf.put(d.id(), d.queryId());
            boolean closedBySystem = PayrollDiscrepancyStatus.RESOLVED.name().equals(d.status())
                    && (PayrollOutcome.NO_LONGER_APPLIES.name().equals(d.outcome())
                    || PayrollOutcome.SUPERSEDED.name().equals(d.outcome()));
            reopen.put(d.id(), closedBySystem);
        }
        store.insertDiscrepancies(fileId, employerId, period.atDay(1), inserts, rowIds, now);
        store.updateDiscrepancies(updates, reopen, queryOf, rowIds);
        if (!paymentOnly) {
            List<Long> gone = existing.values().stream()
                    .filter(d -> !PayrollDiscrepancyType.PAYMENT_DIFFERS.name().equals(d.type()))
                    .filter(d -> !PayrollDiscrepancyStatus.RESOLVED.name().equals(d.status()))
                    .map(PayrollStore.Existing::id).toList();
            store.resolveBySystem(gone, PayrollOutcome.NO_LONGER_APPLIES.name(), SYSTEM, now);
        }
    }

    /**
     * The payment check (PAY-28, PAY-27): the money received for the employer's month against the file's total less
     * the fee. A difference opens -- or brings up to date -- the file's PAYMENT_DIFFERS; the money exactly right
     * resolves it BALANCE_RECEIVED. Nothing is said until a payment is recorded.
     */
    public void checkPayment(long fileId, long employerId, YearMonth period, BigDecimal fileTotal,
                             BigDecimal feePercent, String actor) {
        Map.Entry<BigDecimal, Integer> received = store.received(employerId, period.atDay(1));
        if (received.getValue() == 0) {
            return;
        }
        BigDecimal expected = PayrollMoney.expectedPayment(fileTotal, feePercent);
        BigDecimal difference = received.getKey().subtract(expected);
        Instant now = clock.instant();
        if (difference.abs().compareTo(new BigDecimal("0.005")) < 0) {
            List<Long> open = store.discrepanciesOf(fileId).stream()
                    .filter(d -> PayrollDiscrepancyType.PAYMENT_DIFFERS.name().equals(d.type()))
                    .filter(d -> !PayrollDiscrepancyStatus.RESOLVED.name().equals(d.status()))
                    .map(PayrollStore.Existing::id).toList();
            store.resolveBySystem(open, PayrollOutcome.BALANCE_RECEIVED.name(), SYSTEM, now);
            return;
        }
        String fee = feePercent == null || feePercent.signum() == 0 ? ""
                : " after the employer's " + feePercent.stripTrailingZeros().toPlainString() + "% fee";
        PayrollMatcher.Found payment = new PayrollMatcher.Found(PayrollDiscrepancyType.PAYMENT_DIFFERS, "F", null, null,
                received.getKey(), expected, difference, null, "R" + PayrollKeys.money(received.getKey())
                + " received; R" + PayrollKeys.money(expected) + " expected" + fee + ": R"
                + PayrollKeys.money(difference.abs()) + (difference.signum() < 0 ? " short." : " over."));
        upsert(fileId, employerId, period, List.of(payment), Map.of(), now, true);
    }

    /** The comparison's own tally, as stored on the file. */
    private static String counts(PayrollParser.Parsed parsed, PayrollMatcher.Result result) {
        Map<String, Object> counts = new LinkedHashMap<>();
        counts.put("rowsRead", parsed.lines().size());
        counts.put("rowsUnreadable", parsed.lines().stream().filter(l -> !l.valid()).count());
        counts.put("matched", result.matched());
        counts.put("matchedByStaffNumber", result.byStaffNumber());
        counts.put("matchedByIdNumber", result.byIdNumber());
        counts.put("matchedByName", result.byName());
        counts.put("notMembers", result.notMembers());
        counts.put("missingMembers", result.missing());
        counts.put("catchUps", result.catchUps());
        counts.put("listedTwice", result.listedTwice());
        counts.put("rateUnknown", result.rateUnknown());
        counts.put("linesNotRead", parsed.rowsNotRead());
        return json(counts);
    }

    private static String json(Object value) {
        try {
            return JSON.writeValueAsString(value);
        } catch (JsonProcessingException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private static String cap(String text, int length) {
        return text == null || text.length() <= length ? text : text.substring(0, length);
    }

    /** For a caller outside a comparison: today in SASBO's zone. */
    LocalDate today() {
        return LocalDate.now(clock.withZone(za.co.titandynamix.sasbo_online_backend.utils.MonthRange.ZONE));
    }
}
