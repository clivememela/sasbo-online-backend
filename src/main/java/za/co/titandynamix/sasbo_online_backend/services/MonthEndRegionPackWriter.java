package za.co.titandynamix.sasbo_online_backend.services;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.springframework.stereotype.Component;
import za.co.titandynamix.sasbo_online_backend.dto.monthend.MonthEndStatisticDto;
import za.co.titandynamix.sasbo_online_backend.monthend.MonthEndPackPdf;
import za.co.titandynamix.sasbo_online_backend.monthend.MonthEndPackPdf.Column;
import za.co.titandynamix.sasbo_online_backend.repository.MonthEndFactsRepository.Fact;
import za.co.titandynamix.sasbo_online_backend.repository.MonthEndListingsRepository.MemberLine;
import za.co.titandynamix.sasbo_online_backend.utils.MonthRange;
import za.co.titandynamix.sasbo_online_backend.utils.Penetration;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * A region's month-end pack, written twice -- as a PDF and as a workbook (October build; Stephne MEN-05: "Both") -- from
 * the same content, so the two never disagree. What it holds is the region's own information only (MEN-08): the month in
 * figures for each of the set's areas, the members who joined, who were deleted and who stood suspended at the month's
 * end (MEN-18: "We should also know who the suspended members are"), and month end's statistics, area by area, as the
 * statistics answer them.
 *
 * <p>A member is named by number, name, workplace and employee number -- what a Regional Secretary needs to find them.
 * No identity number, telephone number or account number is printed: the pack travels by e-mail.
 */
@Component
public class MonthEndRegionPackWriter {

    /** The plain name of each statistic a pack holds, in the pack's order. */
    static final Map<String, String> STATISTIC_NAMES = statisticNames();

    static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ENGLISH);

    /** What a pack is written from. {@code places}: the statistics by area -- or by the region, for a whole region. */
    public record Content(YearMonth month, int takeNumber, Instant takenAt, String takenBy, String setCode,
                          String setLabel, String regionCode, List<String> areaLetters, String position,
                          Map<String, List<MonthEndStatisticDto>> places, List<Fact> facts, List<MemberLine> joined,
                          List<MemberLine> left, List<MemberLine> suspended, String producedBy, Instant producedAt) {

        /** Never the members: a pack names them. */
        @Override
        public String toString() {
            return "Content[" + setCode + ", " + month + "]";
        }
    }

    // ================================================================================================ the PDF

    /** The pack as a PDF. */
    public byte[] pdf(Content c) {
        try (MonthEndPackPdf pdf = new MonthEndPackPdf(title(c), "SASBO Online · Month-end pack · " + monthName(c.month())
                + " · " + c.setLabel() + " · names members: keep within SASBO")) {
            pdf.title(title(c), headLines(c));

            pdf.heading("The month in figures");
            pdf.table(figureColumns(), figureRows(c));
            pdf.totalRow(figureColumns(), figureTotal(c));
            pdf.note("On record: members not deleted. Paying: on record less the suspended. Penetration: paying over the "
                    + "staff complement. As the month was taken.");

            pdf.heading("New members enrolled -- " + c.joined().size());
            listing(pdf, joinedColumns(), c.joined().stream().map(MonthEndRegionPackWriter::joinedRow).toList());
            pdf.heading("Members deleted -- " + c.left().size());
            listing(pdf, leftColumns(), c.left().stream().map(MonthEndRegionPackWriter::leftRow).toList());
            pdf.heading("Members suspended at the month's end -- " + c.suspended().size());
            listing(pdf, suspendedColumns(), c.suspended().stream().map(MonthEndRegionPackWriter::suspendedRow).toList());
            pdf.note("The listings name each member where they work now; the figures and statistics are the month as it "
                    + "was taken.");

            // A note the statistics repeat -- the month's take, rule C -- is printed the first time it is said.
            Set<String> noted = new HashSet<>();
            for (Map.Entry<String, List<MonthEndStatisticDto>> place : c.places().entrySet()) {
                pdf.heading("The statistics -- " + placeName(c, place.getKey()));
                for (MonthEndStatisticDto statistic : place.getValue()) {
                    for (MonthEndStatisticDto.Sheet sheet : statistic.sheets()) {
                        pdf.subheading(STATISTIC_NAMES.getOrDefault(statistic.statistic(), statistic.statistic())
                                + ": " + sheet.title(), sheet.legacyTitle());
                        List<Column> columns = statisticColumns(sheet);
                        pdf.table(columns, sheet.rows().stream().map(r -> statisticRow(r, sheet)).toList());
                        if (sheet.total() != null) {
                            pdf.totalRow(columns, statisticRow(sheet.total(), sheet));
                        }
                    }
                    for (String note : statistic.notes()) {
                        if (noted.add(note)) {
                            pdf.note(note);
                        }
                    }
                    pdf.gap(6f);
                }
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            pdf.writeTo(out);
            return out.toByteArray();
        }
    }

    private static void listing(MonthEndPackPdf pdf, List<Column> columns, List<List<String>> rows) {
        if (rows.isEmpty()) {
            pdf.note("None this month.");
            pdf.gap(4f);
            return;
        }
        pdf.table(columns, rows);
    }

    // ================================================================================================ the workbook

    /** The pack as a workbook: the figures, the three listings, then each statistic's sheets, area by area. */
    public byte[] xlsx(Content c) {
        return MonthEndWorkbooks.write(book -> {
            MonthEndWorkbooks.Styles styles = new MonthEndWorkbooks.Styles(book);
            Set<String> names = new HashSet<>();
            Sheet summary = book.createSheet(MonthEndWorkbooks.sheetName(names, "Summary"));
            int r = 0;
            MonthEndWorkbooks.text(summary.createRow(r++), 0, title(c), styles.title);
            for (String line : headLines(c)) {
                MonthEndWorkbooks.text(summary.createRow(r++), 0, line, styles.quiet);
            }
            r++;
            figures(summary, r, c, styles);

            listingSheet(book, names, styles, "New members", joinedColumns(),
                    c.joined().stream().map(MonthEndRegionPackWriter::joinedRow).toList());
            listingSheet(book, names, styles, "Deleted members", leftColumns(),
                    c.left().stream().map(MonthEndRegionPackWriter::leftRow).toList());
            listingSheet(book, names, styles, "Suspended members", suspendedColumns(),
                    c.suspended().stream().map(MonthEndRegionPackWriter::suspendedRow).toList());

            for (Map.Entry<String, List<MonthEndStatisticDto>> place : c.places().entrySet()) {
                for (MonthEndStatisticDto statistic : place.getValue()) {
                    for (MonthEndStatisticDto.Sheet sheet : statistic.sheets()) {
                        MonthEndWorkbooks.writeSheet(book, styles, names, place.getKey() + " " + statistic.statistic()
                                + " " + sheet.key(), statistic, sheet);
                    }
                }
            }
        });
    }

    private static void listingSheet(SXSSFWorkbook book, Set<String> names, MonthEndWorkbooks.Styles styles,
                                     String name, List<Column> columns, List<List<String>> rows) {
        Sheet sheet = book.createSheet(MonthEndWorkbooks.sheetName(names, name));
        int r = table(sheet, 0, columns, rows, styles, false);
        if (rows.isEmpty()) {
            MonthEndWorkbooks.text(sheet.createRow(r), 0, "None this month.", styles.quiet);
        }
    }

    /** The month in figures as numbers a spreadsheet can add: an area a row, then the total in bold. */
    private static void figures(Sheet sheet, int first, Content c, MonthEndWorkbooks.Styles styles) {
        Row heading = sheet.createRow(first);
        List<Column> columns = figureColumns();
        for (int col = 0; col < columns.size(); col++) {
            MonthEndWorkbooks.text(heading, col, columns.get(col).heading(), styles.header);
        }
        int r = first + 1;
        Map<String, long[]> byArea = sums(c);
        long[] total = new long[7];
        for (Map.Entry<String, long[]> area : byArea.entrySet()) {
            figureRow(sheet.createRow(r++), "Area " + area.getKey(), area.getValue(), styles, false);
            for (int i = 0; i < total.length; i++) {
                total[i] += area.getValue()[i];
            }
        }
        figureRow(sheet.createRow(r), "Total", total, styles, true);
    }

    private static void figureRow(Row row, String label, long[] s, MonthEndWorkbooks.Styles styles, boolean total) {
        MonthEndWorkbooks.text(row, 0, label, total ? styles.header : null);
        for (int i = 0; i < s.length; i++) {
            org.apache.poi.ss.usermodel.Cell cell = row.createCell(i + 1);
            cell.setCellValue(s[i]);
            cell.setCellStyle(total ? styles.countTotal : styles.count);
        }
        BigDecimal penetration = Penetration.of(s[2], s[6]);
        if (penetration != null) {
            org.apache.poi.ss.usermodel.Cell cell = row.createCell(s.length + 1);
            cell.setCellValue(penetration.doubleValue());
            cell.setCellStyle(total ? styles.shareTotal : styles.share);
        }
    }

    private static int table(Sheet sheet, int first, List<Column> columns, List<List<String>> rows,
                             MonthEndWorkbooks.Styles styles, boolean total) {
        int r = first;
        if (!total) {
            Row heading = sheet.createRow(r++);
            for (int c = 0; c < columns.size(); c++) {
                MonthEndWorkbooks.text(heading, c, columns.get(c).heading(), styles.header);
            }
        }
        for (List<String> row : rows) {
            Row line = sheet.createRow(r++);
            for (int c = 0; c < row.size(); c++) {
                MonthEndWorkbooks.text(line, c, row.get(c), total ? styles.header : null);
            }
        }
        return r;
    }

    // ================================================================================================ the content

    static String title(Content c) {
        return "SASBO month-end pack -- " + monthName(c.month()) + " -- " + c.setLabel();
    }

    static List<String> headLines(Content c) {
        List<String> lines = new ArrayList<>();
        lines.add((c.areaLetters().isEmpty() ? "Region " + c.regionCode() + ", every area"
                : "Region " + c.regionCode() + ", areas " + String.join(", ", c.areaLetters())) + " -- for the "
                + c.position() + ".");
        lines.add("Taken " + day(c.takenAt()) + " by " + c.takenBy()
                + (c.takeNumber() > 1 ? " (taken again: take " + c.takeNumber() + ")" : "") + "; produced "
                + day(c.producedAt()) + " by " + c.producedBy() + ".");
        lines.add("This pack holds this region's information only. It names members: keep it within SASBO -- never "
                + "forward it, and never post it on WhatsApp.");
        return lines;
    }

    private static String placeName(Content c, String key) {
        return c.areaLetters().isEmpty() ? "region " + key : "area " + key;
    }

    static List<Column> figureColumns() {
        return List.of(Column.text("Area", 1.2f), Column.figures("On record", 1f), Column.figures("Suspended", 1f),
                Column.figures("Paying", 1f), Column.figures("Joined", 1f), Column.figures("Left", 1f),
                Column.figures("Reinstated", 1f), Column.figures("Complement", 1f), Column.figures("Penetration", 1f));
    }

    /** One row an area of the set, summed over its workplaces' facts. */
    static List<List<String>> figureRows(Content c) {
        List<List<String>> rows = new ArrayList<>();
        sums(c).forEach((area, s) -> rows.add(figureCells("Area " + area, s)));
        return rows;
    }

    /** Each area's sums -- on record, suspended, paying, joined, left, reinstated, complement -- in the set's order. */
    static Map<String, long[]> sums(Content c) {
        Map<String, long[]> byArea = new LinkedHashMap<>();
        for (String area : areasOf(c)) {
            byArea.put(area, new long[7]);
        }
        for (Fact f : c.facts()) {
            long[] sums = byArea.computeIfAbsent(f.areaLetter() == null ? "?" : f.areaLetter(), k -> new long[7]);
            add(sums, f);
        }
        return byArea;
    }

    static List<String> figureTotal(Content c) {
        long[] sums = new long[7];
        c.facts().forEach(f -> add(sums, f));
        return figureCells("Total", sums);
    }

    private static List<String> areasOf(Content c) {
        if (!c.areaLetters().isEmpty()) {
            return c.areaLetters();
        }
        return c.facts().stream().map(Fact::areaLetter).filter(a -> a != null).distinct().sorted().toList();
    }

    private static void add(long[] sums, Fact f) {
        sums[0] += f.onRecord();
        sums[1] += f.suspended();
        sums[2] += f.paying();
        sums[3] += f.joined();
        sums[4] += f.left();
        sums[5] += f.reinstated();
        sums[6] += f.complement() == null ? 0 : f.complement();
    }

    private static List<String> figureCells(String label, long[] s) {
        BigDecimal penetration = Penetration.of(s[2], s[6]);
        return List.of(label, count(s[0]), count(s[1]), count(s[2]), count(s[3]), count(s[4]), count(s[5]), count(s[6]),
                penetration == null ? "" : share(penetration));
    }

    static List<Column> joinedColumns() {
        return List.of(Column.text("Area", 0.5f), Column.text("Membership no.", 1.1f), Column.text("Name", 2.4f),
                Column.text("Employer · workplace", 3.2f), Column.text("Employee no.", 1.1f),
                Column.text("Pays by", 1.3f), Column.text("Date joined", 1f));
    }

    static List<String> joinedRow(MemberLine m) {
        return List.of(nz(m.areaLetter()), nz(m.membershipNumber()), name(m), workplace(m), nz(m.employeeNumber()),
                nz(m.paymentType()), m.joinDate() == null ? "" : m.joinDate().toString());
    }

    static List<Column> leftColumns() {
        return List.of(Column.text("Area", 0.5f), Column.text("Membership no.", 1.1f), Column.text("Name", 2.2f),
                Column.text("Employer · workplace", 2.8f), Column.text("Reason deleted", 2.4f),
                Column.text("Deleted on", 0.9f), Column.text("Date joined", 0.9f));
    }

    static List<String> leftRow(MemberLine m) {
        String reason = m.code() == null ? nz(m.codeName()) : m.code() + " " + nz(m.codeName());
        if (m.codeDetail() != null) {
            reason = reason + " -- " + m.codeDetail();
        }
        return List.of(nz(m.areaLetter()), nz(m.membershipNumber()), name(m), workplace(m), reason, isoDay(m.changedAt()),
                m.joinDate() == null ? "" : m.joinDate().toString());
    }

    static List<Column> suspendedColumns() {
        return List.of(Column.text("Area", 0.5f), Column.text("Membership no.", 1.1f), Column.text("Name", 2.2f),
                Column.text("Employer · workplace", 2.8f), Column.text("Suspension code", 2.4f),
                Column.text("Suspended on", 0.9f), Column.text("Date joined", 0.9f));
    }

    static List<String> suspendedRow(MemberLine m) {
        String code = m.code() == null ? (m.codeName() == null ? "No code on record" : m.codeName())
                : m.code() + " " + nz(m.codeName());
        return List.of(nz(m.areaLetter()), nz(m.membershipNumber()), name(m), workplace(m), code, isoDay(m.changedAt()),
                m.joinDate() == null ? "" : m.joinDate().toString());
    }

    static List<Column> statisticColumns(MonthEndStatisticDto.Sheet sheet) {
        List<Column> columns = new ArrayList<>();
        columns.add(Column.text("", Math.max(3f, sheet.columns().size() * 0.45f)));
        for (MonthEndStatisticDto.Column column : sheet.columns()) {
            columns.add(Column.figures(column.label(), 1f));
        }
        return columns;
    }

    static List<String> statisticRow(MonthEndStatisticDto.Row row, MonthEndStatisticDto.Sheet sheet) {
        List<String> cells = new ArrayList<>();
        cells.add(row.label());
        if (row.cells() == null) {
            cells.add(row.fewerThanFive() ? "fewer than 5" : "withheld");
            return cells;
        }
        for (int c = 0; c < sheet.columns().size(); c++) {
            BigDecimal value = c < row.cells().size() ? row.cells().get(c) : null;
            cells.add(value == null ? "" : switch (sheet.columns().get(c).kind()) {
                case MonthEndStatisticDto.Column.RAND -> value.setScale(2, RoundingMode.HALF_UP).toPlainString();
                case MonthEndStatisticDto.Column.SHARE -> share(value);
                default -> count(value.longValue());
            });
        }
        return cells;
    }

    // ================================================================================================ text

    private static String name(MemberLine m) {
        String first = m.firstNames() != null && !m.firstNames().isBlank() ? m.firstNames().trim() : nz(m.initials());
        String title = m.title() == null || m.title().isBlank() ? "" : " (" + m.title().trim() + ")";
        return nz(m.surname()).toUpperCase(Locale.ROOT) + (first.isEmpty() ? "" : ", " + first) + title;
    }

    private static String workplace(MemberLine m) {
        String employer = nz(m.employerName());
        String place = m.workplaceName() == null ? "" : m.workplaceName()
                + (m.raNumber() == null ? "" : " (R/A " + m.raNumber() + ")");
        return employer.isEmpty() ? place : place.isEmpty() ? employer : employer + " · " + place;
    }

    private static String nz(String value) {
        return value == null ? "" : value;
    }

    private static String count(long value) {
        return String.format(Locale.ENGLISH, "%,d", value);
    }

    private static String share(BigDecimal value) {
        return value.multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP).toPlainString() + "%";
    }

    static String day(Instant instant) {
        return instant == null ? "" : LocalDate.ofInstant(instant, MonthRange.ZONE).format(DAY);
    }

    /** A listing's date as the listings print a date joined: 2026-09-21, in SASBO's zone. */
    static String isoDay(Instant instant) {
        return instant == null ? "" : LocalDate.ofInstant(instant, MonthRange.ZONE).toString();
    }

    static String monthName(YearMonth month) {
        return month.getMonth().getDisplayName(TextStyle.FULL, Locale.ENGLISH) + " " + month.getYear();
    }

    private static Map<String, String> statisticNames() {
        Map<String, String> names = new LinkedHashMap<>();
        names.put("new-and-deleted", "New and deleted");
        names.put("membership", "Membership");
        names.put("leavers", "Why members left");
        names.put("branches", "The branches");
        names.put("recruitment", "Recruitment");
        names.put("representatives", "Representatives");
        names.put("visits", "Branches due a visit");
        return names;
    }
}
