package za.co.titandynamix.sasbo_online_backend.services;

import org.springframework.stereotype.Service;
import za.co.titandynamix.sasbo_online_backend.dto.payroll.PayrollDiscrepancyDto;
import za.co.titandynamix.sasbo_online_backend.dto.payroll.PayrollFileSummaryDto;
import za.co.titandynamix.sasbo_online_backend.dto.payroll.PayrollQueryDto;
import za.co.titandynamix.sasbo_online_backend.dto.payroll.PayrollRowDto;
import za.co.titandynamix.sasbo_online_backend.enums.PayrollDiscrepancyType;
import za.co.titandynamix.sasbo_online_backend.enums.PayrollQueryAction;
import za.co.titandynamix.sasbo_online_backend.exception.PayrollRefusal;
import za.co.titandynamix.sasbo_online_backend.exception.PayrollRefusalCode;
import za.co.titandynamix.sasbo_online_backend.payroll.PayrollLimits;
import za.co.titandynamix.sasbo_online_backend.payroll.PayrollMatcher;
import za.co.titandynamix.sasbo_online_backend.payroll.ReportDocument;
import za.co.titandynamix.sasbo_online_backend.payroll.ReportPdf;
import za.co.titandynamix.sasbo_online_backend.payroll.ReportXlsx;
import za.co.titandynamix.sasbo_online_backend.repository.PayrollReads;
import za.co.titandynamix.sasbo_online_backend.utils.MonthRange;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The payroll reports, in IT's Reconciliation Report shape and beyond it (fixtures' {@code RECON-REPORT.md}): the
 * discrepancies list and the full report (Stephne, PAY-35: "The discrepancies only; A full report with matched,
 * unmatched and totals"), each as a spreadsheet or a PDF (PAY-37), and the employer's list of queries (PAY-21).
 *
 * <p>IT's report keys on the employee number alone and never compares an amount; these say how each line was matched,
 * by staff number, ID number or name, and set every amount against the subscription and the money paid in.
 */
@Service
public class PayrollReports {

    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("d MMMM yyyy, HH:mm", Locale.ENGLISH);

    private final PayrollFiles files;
    private final PayrollReads reads;
    private final Clock clock;

    public PayrollReports(PayrollFiles files, PayrollReads reads, Clock clock) {
        this.files = files;
        this.reads = reads;
        this.clock = clock;
    }

    /** A report as a download: its name, what it is, its bytes. */
    public record Report(String fileName, String mediaType, byte[] bytes) {
    }

    /** The two forms a report takes. */
    public enum Format {
        XLSX("xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
        PDF("pdf", "application/pdf");

        private final String extension;
        private final String mediaType;

        Format(String extension, String mediaType) {
            this.extension = extension;
            this.mediaType = mediaType;
        }

        public String extension() {
            return extension;
        }

        public String mediaType() {
            return mediaType;
        }
    }

    public static Format format(String written) {
        if (written == null || written.isBlank()) {
            return Format.XLSX;
        }
        return switch (written.trim().toLowerCase(Locale.ROOT)) {
            case "xlsx" -> Format.XLSX;
            case "pdf" -> Format.PDF;
            default -> throw new PayrollRefusal(PayrollRefusalCode.REPORT_FORMAT_UNKNOWN, "A report is a spreadsheet "
                    + "(xlsx) or a PDF (pdf).", Map.of("accepted", "xlsx, pdf"));
        };
    }

    /** The discrepancies list: the summary, and every discrepancy of the file, by kind. */
    public Report discrepancies(long fileId, String format, String actor) {
        Format chosen = format(format);
        PayrollFileSummaryDto summary = files.summary(fileId);
        List<PayrollDiscrepancyDto> found = reads.allOfFile(fileId);
        ReportDocument document = new ReportDocument(title("Payroll discrepancies", summary), header(summary, actor),
                List.of(summarySection(summary), discrepancySection(found, false)));
        return report("Payroll discrepancies", summary, chosen, document);
    }

    /**
     * The full report: the summary, the discrepancies, every line of the file in its order with how it was matched,
     * the members on record not on the file, and the queries raised.
     */
    public Report full(long fileId, String format, String actor) {
        Format chosen = format(format);
        PayrollFileSummaryDto summary = files.summary(fileId);
        if ("PURGED".equals(summary.file().status())) {
            throw new PayrollRefusal(PayrollRefusalCode.FILE_PURGED, "This file's lines were deleted a year after it "
                    + "was loaded, so its full report cannot be made. Its discrepancies list can.");
        }
        List<PayrollDiscrepancyDto> found = reads.allOfFile(fileId);
        List<PayrollRowDto> rows = reads.rows(fileId, "ALL", 0, PayrollLimits.MAX_ROWS + 1).content();
        List<ReportDocument.Section> sections = new ArrayList<>();
        sections.add(summarySection(summary));
        sections.add(discrepancySection(found, true));
        sections.add(linesSection(rows, found, summary));
        sections.add(missingSection(found));
        sections.add(queriesSection(summary));
        ReportDocument document = new ReportDocument(title("Payroll reconciliation", summary), header(summary, actor),
                sections);
        return report("Payroll reconciliation", summary, chosen, document);
    }

    /** The employer's list of queries: what SASBO asks it to add, delete and correct, as the employer reads it. */
    public Report queryList(PayrollQueryDto query, Format chosen) {
        YearMonth month = YearMonth.parse(query.period());
        String title = "Payroll queries for " + query.employer().name() + " — " + PayrollMatcher.monthName(month);
        List<String> header = List.of("From SASBO, The Finance Union · raised " + when(Instant.parse(
                query.raisedAt())) + " · " + query.lines().size() + " lines",
                "Please act on each line and reply to SASBO with what was done.");
        List<ReportDocument.Section> sections = new ArrayList<>();
        for (PayrollQueryAction action : PayrollQueryAction.values()) {
            List<List<Object>> rows = new ArrayList<>();
            for (PayrollQueryDto.Line line : query.lines()) {
                if (line.action().equals(action.name())) {
                    rows.add(List.of(text(line.staffNumber()), text(line.name()), text(line.idNumber()),
                            text(line.membershipNumber()), money(line.amountOnFile()), money(line.expectedAmount()),
                            line.reason()));
                }
            }
            sections.add(new ReportDocument.Section(action.label(), heading(action), List.of(), List.of(
                    col("Employee no.", ReportDocument.Kind.TEXT, 1f), col("Name", ReportDocument.Kind.TEXT, 1.6f),
                    col("ID number", ReportDocument.Kind.TEXT, 1f), col("SASBO member no.", ReportDocument.Kind.TEXT, 1f),
                    col("Amount deducted", ReportDocument.Kind.MONEY, 1f),
                    col("Subscription", ReportDocument.Kind.MONEY, 1f), col("What we ask", ReportDocument.Kind.TEXT, 3f)),
                    rows, null, "Nothing to " + action.label().toLowerCase(Locale.ROOT) + " this month.", false));
        }
        ReportDocument document = new ReportDocument(title, header, sections);
        String name = "Payroll queries - " + query.employer().name() + " - " + PayrollMatcher.monthName(month) + "."
                + chosen.extension();
        byte[] bytes = chosen == Format.XLSX ? ReportXlsx.write(document) : ReportPdf.write(document);
        return new Report(name, chosen.mediaType(), bytes);
    }

    private static String heading(PayrollQueryAction action) {
        return switch (action) {
            case ADD -> "Add: please start deducting these members";
            case DELETE -> "Delete: please stop these deductions";
            case CORRECT -> "Correct: please correct these amounts";
        };
    }

    // --------------------------------------------------------------------------------------------------- sections

    private ReportDocument.Section summarySection(PayrollFileSummaryDto s) {
        PayrollFileSummaryDto.Counts c = s.counts();
        PayrollFileSummaryDto.Totals t = s.totals();
        List<String[]> facts = new ArrayList<>();
        facts.add(fact("Lines on the employer's file", String.valueOf(c.rowsRead())));
        facts.add(fact("Matched to members", c.matched() + " (by staff number " + c.matchedByStaffNumber()
                + ", by ID number " + c.matchedByIdNumber() + ", by initials and surname " + c.matchedByName() + ")"));
        facts.add(fact("On the file but not members", String.valueOf(c.notMembers())));
        facts.add(fact("Members missing from the file", String.valueOf(c.missingMembers())));
        facts.add(fact("Catch-ups recognised", String.valueOf(c.catchUps())));
        if (c.rowsUnreadable() > 0) {
            facts.add(fact("Lines that could not be read", String.valueOf(c.rowsUnreadable())));
        }
        if (c.linesNotRead() > 0) {
            facts.add(fact("Rows below the first table, not compared", String.valueOf(c.linesNotRead())));
        }
        if (c.rateUnknown() > 0) {
            facts.add(fact("Lines not checked: no subscription on record for a month", String.valueOf(c.rateUnknown())));
        }
        int open = 0;
        int all = 0;
        for (PayrollFileSummaryDto.TypeCount type : s.discrepanciesByType()) {
            int n = type.open() + type.queried() + type.resolved();
            all += n;
            open += type.open() + type.queried();
        }
        facts.add(fact("Discrepancies", all + " (" + open + " still open)"));
        for (PayrollFileSummaryDto.TypeCount type : s.discrepanciesByType()) {
            int n = type.open() + type.queried() + type.resolved();
            if (n > 0) {
                facts.add(fact("   " + PayrollDiscrepancyType.valueOf(type.type()).label(), n + " (" + type.open()
                        + " open, " + type.queried() + " queried, " + type.resolved() + " resolved)"));
            }
        }
        facts.add(fact("File total", rands(t.fileTotal())));
        if (t.statedTotal() != null) {
            facts.add(fact("Total stated on the file", rands(t.statedTotal())));
        }
        facts.add(fact("Subscription for the month", t.subscription() == null ? "Not on record"
                : rands(t.subscription().amount()) + " (from " + t.subscription().effectiveFrom() + ")"));
        facts.add(fact("Expected total", t.expectedTotal() == null ? "—" : rands(t.expectedTotal())));
        facts.add(fact("Employer's fee", t.adminFeePercent() + "%: " + rands(t.adminFee())));
        facts.add(fact("Expected payment", rands(t.expectedPayment())));
        facts.add(fact("Money received", t.received() == null ? "Not recorded yet" : rands(t.received())));
        facts.add(fact("Difference", t.paymentDifference() == null ? "—" : rands(t.paymentDifference())));
        facts.add(fact("Payment", paymentWords(t.paymentStatus())));
        return new ReportDocument.Section("Summary", "Summary", facts, List.of(), List.of(), null, null, false);
    }

    private static String paymentWords(String status) {
        return switch (status) {
            case "EXACT" -> "Received in full";
            case "SHORT" -> "Short";
            case "OVER" -> "More than expected";
            default -> "Not recorded yet";
        };
    }

    private static ReportDocument.Section discrepancySection(List<PayrollDiscrepancyDto> found, boolean newPage) {
        List<List<Object>> rows = new ArrayList<>();
        BigDecimal onFile = BigDecimal.ZERO;
        BigDecimal difference = BigDecimal.ZERO;
        for (PayrollDiscrepancyDto d : found) {
            PayrollDiscrepancyDto.Row row = d.row();
            PayrollDiscrepancyDto.Member member = d.member();
            rows.add(List.of(d.label(), row == null ? text(null) : (Object) row.rowNumber(),
                    text(row == null ? null : row.staffNumber()), text(member == null ? null : member.staffNumber()),
                    text(row == null ? null : PayrollMatcher.displayName(row.initials(), row.surname())),
                    text(member == null ? null : member.name()), text(row == null ? null : row.idNumber()),
                    text(member == null ? null : member.membershipNumber()), money(d.amountOnFile()),
                    money(d.expectedAmount()), money(d.difference()), statusWords(d), d.detail(),
                    d.lastNote() == null ? text(null) : d.lastNote().text()));
            onFile = d.amountOnFile() == null ? onFile : onFile.add(new BigDecimal(d.amountOnFile()));
            difference = d.difference() == null ? difference : difference.add(new BigDecimal(d.difference()));
        }
        List<Object> totals = new ArrayList<>(java.util.Collections.nCopies(14, null));
        totals.set(0, found.size() + " discrepancies");
        totals.set(8, onFile);
        totals.set(10, difference);
        return new ReportDocument.Section("Discrepancies", "Discrepancies", List.of(), List.of(
                col("Type", ReportDocument.Kind.TEXT, 1.2f), col("Line", ReportDocument.Kind.INTEGER, 0.45f),
                col("Employee no. (file)", ReportDocument.Kind.TEXT, 0.9f),
                col("Employee no. (SASBO)", ReportDocument.Kind.TEXT, 0.9f),
                col("Name on file", ReportDocument.Kind.TEXT, 1.2f), col("Name in SASBO", ReportDocument.Kind.TEXT, 1.2f),
                col("ID number", ReportDocument.Kind.TEXT, 0.75f), col("Member no.", ReportDocument.Kind.TEXT, 0.8f),
                col("On file", ReportDocument.Kind.MONEY, 0.75f), col("Expected", ReportDocument.Kind.MONEY, 0.75f),
                col("Difference", ReportDocument.Kind.MONEY, 0.75f), col("Status", ReportDocument.Kind.TEXT, 0.7f),
                col("What was found", ReportDocument.Kind.TEXT, 2.6f), col("Last note", ReportDocument.Kind.TEXT, 1.6f)),
                rows, totals, "No discrepancies.", newPage);
    }

    private static String statusWords(PayrollDiscrepancyDto d) {
        return switch (d.status()) {
            case "RESOLVED" -> "Resolved: " + (d.resolution() == null ? "" : d.resolution().label());
            case "QUERIED" -> "Queried";
            default -> "Open";
        };
    }

    private static ReportDocument.Section linesSection(List<PayrollRowDto> rows, List<PayrollDiscrepancyDto> found,
                                                       PayrollFileSummaryDto summary) {
        Map<Integer, List<String>> byLine = new HashMap<>();
        for (PayrollDiscrepancyDto d : found) {
            if (d.row() != null) {
                byLine.computeIfAbsent(d.row().rowNumber(), line -> new ArrayList<>()).add(d.label());
            }
        }
        List<List<Object>> table = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        int matched = 0;
        for (PayrollRowDto row : rows) {
            PayrollRowDto.Match match = row.match();
            List<String> results = byLine.getOrDefault(row.rowNumber(), List.of());
            String result = !row.valid() ? "Could not be read: " + row.problem()
                    : results.isEmpty() ? (match == null ? "—" : amountWords(match)) : String.join("; ", results);
            table.add(List.of(row.rowNumber(), text(row.staffNumber()),
                    text(PayrollMatcher.displayName(row.initials(), row.surname())), text(row.idNumber()),
                    money(row.amount()), text(match == null ? null : match.membershipNumber()),
                    match == null ? text(null) : ruleWords(match.rule()),
                    match == null || match.confidence() == null ? text(null) : capital(match.confidence()), result));
            if (row.amount() != null) {
                total = total.add(new BigDecimal(row.amount()));
            }
            matched += match == null ? 0 : 1;
        }
        List<Object> totals = new ArrayList<>(java.util.Collections.nCopies(9, null));
        totals.set(1, rows.size() + " lines");
        totals.set(4, total);
        totals.set(5, matched + " matched");
        return new ReportDocument.Section("Full report", "Every line of the file", List.of(), List.of(
                col("Line", ReportDocument.Kind.INTEGER, 0.45f), col("Employee no.", ReportDocument.Kind.TEXT, 0.9f),
                col("Name on file", ReportDocument.Kind.TEXT, 1.4f), col("ID number", ReportDocument.Kind.TEXT, 0.8f),
                col("Amount", ReportDocument.Kind.MONEY, 0.8f), col("Matched to", ReportDocument.Kind.TEXT, 0.9f),
                col("Matched by", ReportDocument.Kind.TEXT, 1.1f), col("Confidence", ReportDocument.Kind.TEXT, 0.7f),
                col("Result", ReportDocument.Kind.TEXT, 2.6f)), table, totals, "No lines.", true);
    }

    private static String amountWords(PayrollRowDto.Match match) {
        if (match.amountStatus() == null) {
            return "Matched";
        }
        return switch (match.amountStatus()) {
            case "CATCH_UP" -> "Matched: a catch-up of " + match.months() + " months";
            case "RATE_UNKNOWN" -> "Matched; the amount not checked: no subscription on record for "
                    + (match.rateUnknownFor() == null ? "the month" : PayrollMatcher.monthName(
                    YearMonth.parse(match.rateUnknownFor())));
            default -> "Matched";
        };
    }

    private static ReportDocument.Section missingSection(List<PayrollDiscrepancyDto> found) {
        List<List<Object>> rows = new ArrayList<>();
        for (PayrollDiscrepancyDto d : found) {
            if (PayrollDiscrepancyType.MISSING_FROM_FILE.name().equals(d.type()) && d.member() != null) {
                rows.add(List.of(text(d.member().membershipNumber()), text(d.member().name()),
                        text(d.member().staffNumber()), text(d.member().status()),
                        d.months() == null ? text(null) : (Object) d.months(), statusWords(d)));
            }
        }
        return new ReportDocument.Section("Not on the file", "Members on record who are not on the file", List.of(),
                List.of(col("Member no.", ReportDocument.Kind.TEXT, 1f), col("Name in SASBO", ReportDocument.Kind.TEXT, 1.6f),
                        col("Employee no.", ReportDocument.Kind.TEXT, 1f), col("Status", ReportDocument.Kind.TEXT, 1f),
                        col("Months missing in a row", ReportDocument.Kind.INTEGER, 1f),
                        col("Discrepancy", ReportDocument.Kind.TEXT, 1.6f)),
                rows, null, "Every payroll member of this employer is on the file.", true);
    }

    private ReportDocument.Section queriesSection(PayrollFileSummaryDto summary) {
        List<List<Object>> rows = new ArrayList<>();
        for (PayrollFileSummaryDto.QueryRef ref : summary.queries()) {
            reads.query(ref.id()).ifPresent(query -> {
                for (PayrollQueryDto.Line line : query.lines()) {
                    rows.add(List.of(when(Instant.parse(query.raisedAt())), capital(line.action()),
                            text(line.staffNumber()), text(line.name()), text(line.membershipNumber()), line.reason()));
                }
            });
        }
        return new ReportDocument.Section("Queries", "Queries raised with the employer", List.of(), List.of(
                col("Raised", ReportDocument.Kind.TEXT, 1.1f), col("Ask", ReportDocument.Kind.TEXT, 0.6f),
                col("Employee no.", ReportDocument.Kind.TEXT, 0.9f), col("Name", ReportDocument.Kind.TEXT, 1.4f),
                col("Member no.", ReportDocument.Kind.TEXT, 0.9f), col("What was asked", ReportDocument.Kind.TEXT, 3f)),
                rows, null, "No query has been raised for this file.", true);
    }

    // --------------------------------------------------------------------------------------------------- helpers

    private Report report(String what, PayrollFileSummaryDto summary, Format format, ReportDocument document) {
        YearMonth month = YearMonth.parse(summary.file().period());
        String name = what + " - " + summary.file().employer().name() + " - " + PayrollMatcher.monthName(month) + "."
                + format.extension();
        byte[] bytes = format == Format.XLSX ? ReportXlsx.write(document) : ReportPdf.write(document);
        return new Report(name, format.mediaType(), bytes);
    }

    private static String title(String what, PayrollFileSummaryDto summary) {
        return what + " — " + summary.file().employer().name() + " — "
                + PayrollMatcher.monthName(YearMonth.parse(summary.file().period()));
    }

    private List<String> header(PayrollFileSummaryDto summary, String actor) {
        return List.of("File: " + summary.file().originalName() + " · loaded " + when(Instant.parse(
                        summary.file().uploadedAt())) + " by " + summary.file().uploadedBy()
                        + (summary.file().entry().equals("TYPED") ? " (typed in)" : ""),
                "Printed " + when(clock.instant()) + " by " + actor);
    }

    private static String when(Instant instant) {
        return LocalDateTime.ofInstant(instant, MonthRange.ZONE).format(WHEN);
    }

    private static String ruleWords(String rule) {
        return switch (rule) {
            case "STAFF_NUMBER" -> "Staff number";
            case "ID_NUMBER" -> "ID number";
            case "NAME" -> "Initials and surname";
            default -> "—";
        };
    }

    private static String capital(String word) {
        String lower = word.toLowerCase(Locale.ROOT).replace('_', ' ');
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }

    private static ReportDocument.Column col(String heading, ReportDocument.Kind kind, float weight) {
        return new ReportDocument.Column(heading, kind, weight);
    }

    private static String[] fact(String label, String value) {
        return new String[] {label, value};
    }

    private static String rands(String amount) {
        return amount == null ? "—" : "R" + amount;
    }

    /** A text value, or a dash where there is none -- {@code List.of} refuses a null. */
    private static Object text(String value) {
        return value == null || value.isBlank() ? "—" : value;
    }

    private static Object money(String amount) {
        return amount == null ? "—" : new BigDecimal(amount);
    }
}
