package za.co.titandynamix.sasbo_online_backend.services;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.springframework.stereotype.Component;
import za.co.titandynamix.sasbo_online_backend.config.SasboEnvironment;
import za.co.titandynamix.sasbo_online_backend.dto.monthend.MonthEndStatisticDto;
import za.co.titandynamix.sasbo_online_backend.repository.MonthEndFactsRepository;
import za.co.titandynamix.sasbo_online_backend.repository.MonthEndFactsRepository.Fact;
import za.co.titandynamix.sasbo_online_backend.repository.MonthEndFactsRepository.TakenMonth;
import za.co.titandynamix.sasbo_online_backend.utils.MonthRange;
import za.co.titandynamix.sasbo_online_backend.utils.SeedMemberNumbers;
import za.co.titandynamix.sasbo_online_backend.utils.SpreadsheetText;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Consumer;

/**
 * The statistics pack's two workbooks (month end 04.10) -- counts only, written by a STREAMING writer: the hosted
 * service has about 384 MB of heap, and a workbook built whole in memory is how a report takes the API down.
 *
 * <ul>
 *   <li>{@value #STATISTICS}: every sheet of every statistic of the month -- new and deleted, membership, why members
 *       left, the branches, recruitment, representatives -- AS THE STATISTICS ANSWER THEM, cell for cell, rule C and
 *       the switch applied as they apply them. Laid out as SASBO's printed reports are, which are the reference, and
 *       never checked against the workbooks typed from them (MD23);</li>
 *   <li>{@value #MONTHLY}: the month-on-month tables -- SASBO's <em>Table 1</em> and <em>Table2</em>: membership
 *       excluding the suspended, core and all, by region, with its growth in the month and to date -- over the
 *       year's taken months, each as it was taken, and the open month as it stands.</li>
 * </ul>
 * Every text written passes {@link SpreadsheetText#safe}: a label is data, and a spreadsheet must show it, never run it
 * (MV-13).
 */
@Component
public class MonthEndWorkbooks {

    public static final String STATISTICS = "statistics";
    public static final String MONTHLY = "monthly";

    /** Rows a streaming sheet keeps in memory; the rest are on disk. */
    static final int WINDOW = 100;

    /** Excel's longest sheet name. */
    static final int SHEET_NAME_CEILING = 31;

    private final MonthEndStatistics statistics;
    private final MonthEndTakes takes;
    private final MonthEndFactsRepository facts;
    private final SasboEnvironment environment;
    private final SeedMemberNumbers seedNumbers;
    private final SummaryGate gate;

    public MonthEndWorkbooks(MonthEndStatistics statistics, MonthEndTakes takes, MonthEndFactsRepository facts,
                             SasboEnvironment environment, SeedMemberNumbers seedNumbers, SummaryGate gate) {
        this.statistics = statistics;
        this.takes = takes;
        this.facts = facts;
        this.environment = environment;
        this.seedNumbers = seedNumbers;
        this.gate = gate;
    }

    // ================================================================================================ the statistics

    /** Every sheet of every statistic of {@code month}, one workbook. */
    public byte[] statistics(String month) {
        List<MonthEndStatisticDto> all = statistics.all(month);
        return write(book -> {
            Styles styles = new Styles(book);
            Set<String> names = new HashSet<>();
            for (MonthEndStatisticDto answer : all) {
                for (MonthEndStatisticDto.Sheet sheet : answer.sheets()) {
                    writeSheet(book, styles, names, answer, sheet);
                }
            }
        });
    }

    private static void writeSheet(SXSSFWorkbook book, Styles styles, Set<String> names, MonthEndStatisticDto answer,
                                   MonthEndStatisticDto.Sheet sheet) {
        Sheet out = book.createSheet(sheetName(names, answer.statistic() + " " + sheet.key()));
        int r = 0;
        text(out.createRow(r++), 0, sheet.title(), styles.title);
        text(out.createRow(r++), 0, sheet.legacyTitle(), styles.quiet);
        text(out.createRow(r++), 0, describe(answer), styles.quiet);
        r++;
        Row header = out.createRow(r++);
        for (int c = 0; c < sheet.columns().size(); c++) {
            text(header, c + 1, sheet.columns().get(c).label(), styles.header);
        }
        for (MonthEndStatisticDto.Row row : sheet.rows()) {
            writeRow(out.createRow(r++), row, sheet.columns(), styles, false);
        }
        if (sheet.total() != null) {
            writeRow(out.createRow(r++), sheet.total(), sheet.columns(), styles, true);
        }
        r++;
        for (String note : answer.notes()) {
            text(out.createRow(r++), 0, note, styles.quiet);
        }
    }

    private static void writeRow(Row line, MonthEndStatisticDto.Row row, List<MonthEndStatisticDto.Column> columns,
                                 Styles styles, boolean total) {
        text(line, 0, row.label(), total ? styles.header : null);
        if (row.cells() == null) {
            text(line, 1, row.fewerThanFive() ? "fewer than 5" : "withheld", styles.quiet);
            return;
        }
        for (int c = 0; c < columns.size(); c++) {
            BigDecimal value = row.cells().get(c);
            if (value != null) {
                number(line, c + 1, value, styles.of(columns.get(c).kind(), total));
            }
        }
    }

    private static String describe(MonthEndStatisticDto answer) {
        StringBuilder line = new StringBuilder(monthName(YearMonth.parse(answer.month())));
        line.append(answer.takenAt() == null ? ", open -- as it stands" : ", taken " + answer.takenAt().substring(0, 10));
        if (answer.area() != null) {
            line.append(", area ").append(answer.area());
        } else if (answer.region() != null) {
            line.append(", region ").append(answer.region());
        }
        return line.toString();
    }

    // ================================================================================================ month on month

    /** One month's membership excluding the suspended: core, all, and core by region. */
    record MonthFigures(YearMonth month, boolean open, long core, long all, Map<String, Long> coreByRegion) {
    }

    /** Table 1 and Table2, over the year's months up to {@code month}. */
    public byte[] monthly(YearMonth month) {
        List<MonthFigures> months = monthsOfTheYear(month);
        Map<String, String> regions = new TreeMap<>();
        return write(book -> {
            Styles styles = new Styles(book);
            for (MonthFigures m : months) {
                m.coreByRegion().keySet().forEach(code -> regions.putIfAbsent(code, code));
            }
            tableOne(book, styles, months, regions);
            tableTwo(book, styles, months);
        });
    }

    private List<MonthFigures> monthsOfTheYear(YearMonth month) {
        List<TakenMonth> taken = new ArrayList<>(takes.takenMonths());
        taken.sort(Comparator.comparing(TakenMonth::monthStart));
        YearMonth december = YearMonth.of(month.getYear() - 1, 12);
        if (!gate.enter(MonthEndStatistics.JOB_WAIT)) {
            throw new IllegalStateException("the figures' gate stayed full for the pack's whole wait");
        }
        try {
            List<MonthFigures> months = new ArrayList<>();
            boolean asked = false;
            for (TakenMonth t : taken) {
                YearMonth m = YearMonth.from(t.monthStart());
                if (m.equals(december) || (m.getYear() == month.getYear() && !m.isAfter(month))) {
                    months.add(figures(m, false, facts.facts(t.id())));
                    asked |= m.equals(month);
                }
            }
            if (!asked && month.equals(takes.openMonth())) {
                months.add(figures(month, true, facts.liveFacts(MonthRange.of(month), seeded())));
            }
            return months;
        } finally {
            gate.leave();
        }
    }

    private Collection<String> seeded() {
        return environment.isProduction() ? List.of() : seedNumbers.all();
    }

    private static MonthFigures figures(YearMonth month, boolean open, List<Fact> rows) {
        long core = 0;
        long all = 0;
        Map<String, Long> byRegion = new TreeMap<>();
        for (Fact f : rows) {
            all += f.paying();
            if (Boolean.TRUE.equals(f.groupCore())) {
                core += f.paying();
                String region = f.regionName() == null ? "No region on record" : f.regionName();
                byRegion.merge(region, f.paying(), Long::sum);
            }
        }
        return new MonthFigures(month, open, core, all, byRegion);
    }

    private static void tableOne(SXSSFWorkbook book, Styles styles, List<MonthFigures> months,
                                 Map<String, String> regions) {
        Sheet out = book.createSheet("Table 1");
        int r = 0;
        text(out.createRow(r++), 0, "Core membership, by month and by region", styles.title);
        text(out.createRow(r++), 0, "Table 1 -- members on record less the suspended, core groups only", styles.quiet);
        r++;
        Row header = out.createRow(r++);
        text(header, 0, "Month", styles.header);
        text(header, 1, "Core", styles.header);
        int c = 2;
        for (String region : regions.keySet()) {
            text(header, c++, region, styles.header);
        }
        text(header, c++, "Growth in the month", styles.header);
        text(header, c, "Growth to date", styles.header);
        MonthFigures first = months.isEmpty() ? null : months.get(0);
        MonthFigures previous = null;
        for (MonthFigures m : months) {
            Row line = out.createRow(r++);
            text(line, 0, monthName(m.month()) + (m.open() ? " (open)" : ""), null);
            number(line, 1, BigDecimal.valueOf(m.core()), styles.count);
            int column = 2;
            for (String region : regions.keySet()) {
                number(line, column++, BigDecimal.valueOf(m.coreByRegion().getOrDefault(region, 0L)), styles.count);
            }
            if (previous != null) {
                number(line, column, BigDecimal.valueOf(m.core() - previous.core()), styles.count);
                number(line, column + 1, BigDecimal.valueOf(m.core() - first.core()), styles.count);
            }
            previous = m;
        }
        r++;
        text(out.createRow(r), 0, "The months are those taken, each as it was taken; an open month is as it stands.",
                styles.quiet);
    }

    private static void tableTwo(SXSSFWorkbook book, Styles styles, List<MonthFigures> months) {
        Sheet out = book.createSheet("Table2");
        int r = 0;
        text(out.createRow(r++), 0, "Membership excluding suspensions, month on month", styles.title);
        text(out.createRow(r++), 0, "Table2 -- core and all banks: current, previous, growth, growth to date",
                styles.quiet);
        r++;
        Row header = out.createRow(r++);
        String[] headings = {"Month", "Core", "Core, previous", "Core growth", "Core growth to date", "All",
                "All, previous", "All growth", "All growth to date"};
        for (int c = 0; c < headings.length; c++) {
            text(header, c, headings[c], styles.header);
        }
        MonthFigures first = months.isEmpty() ? null : months.get(0);
        MonthFigures previous = null;
        for (MonthFigures m : months) {
            Row line = out.createRow(r++);
            text(line, 0, monthName(m.month()) + (m.open() ? " (open)" : ""), null);
            number(line, 1, BigDecimal.valueOf(m.core()), styles.count);
            number(line, 5, BigDecimal.valueOf(m.all()), styles.count);
            if (previous != null) {
                number(line, 2, BigDecimal.valueOf(previous.core()), styles.count);
                number(line, 3, BigDecimal.valueOf(m.core() - previous.core()), styles.count);
                number(line, 4, BigDecimal.valueOf(m.core() - first.core()), styles.count);
                number(line, 6, BigDecimal.valueOf(previous.all()), styles.count);
                number(line, 7, BigDecimal.valueOf(m.all() - previous.all()), styles.count);
                number(line, 8, BigDecimal.valueOf(m.all() - first.all()), styles.count);
            }
            previous = m;
        }
    }

    // ================================================================================================ writing

    /** The styles a workbook's cells are written in -- made once a workbook, as a workbook's styles are few. */
    static final class Styles {
        final CellStyle title;
        final CellStyle quiet;
        final CellStyle header;
        final CellStyle count;
        final CellStyle rand;
        final CellStyle share;
        final CellStyle countTotal;
        final CellStyle randTotal;
        final CellStyle shareTotal;

        Styles(SXSSFWorkbook book) {
            Font bold = book.createFont();
            bold.setBold(true);
            Font large = book.createFont();
            large.setBold(true);
            large.setFontHeightInPoints((short) 13);
            Font italic = book.createFont();
            italic.setItalic(true);
            title = style(book, large, null);
            quiet = style(book, italic, null);
            header = style(book, bold, null);
            count = style(book, null, "#,##0");
            rand = style(book, null, "#,##0.00");
            share = style(book, null, "0.00%");
            countTotal = style(book, bold, "#,##0");
            randTotal = style(book, bold, "#,##0.00");
            shareTotal = style(book, bold, "0.00%");
        }

        CellStyle of(String kind, boolean total) {
            return switch (kind) {
                case MonthEndStatisticDto.Column.RAND -> total ? randTotal : rand;
                case MonthEndStatisticDto.Column.SHARE -> total ? shareTotal : share;
                default -> total ? countTotal : count;
            };
        }

        private static CellStyle style(SXSSFWorkbook book, Font font, String format) {
            CellStyle style = book.createCellStyle();
            if (font != null) {
                style.setFont(font);
            }
            if (format != null) {
                style.setDataFormat(book.createDataFormat().getFormat(format));
            }
            return style;
        }
    }

    /** A text cell -- never a formula, whatever the text begins with (MV-13). */
    static void text(Row row, int column, String value, CellStyle style) {
        Cell cell = row.createCell(column);
        cell.setCellValue(SpreadsheetText.safe(value == null ? "" : value));
        if (style != null) {
            cell.setCellStyle(style);
        }
    }

    private static void number(Row row, int column, BigDecimal value, CellStyle style) {
        Cell cell = row.createCell(column);
        cell.setCellValue(value.doubleValue());
        cell.setCellStyle(style);
    }

    /**
     * A sheet's name as a spreadsheet accepts it: none of {@code \ / ? * [ ] :}, at most
     * {@value #SHEET_NAME_CEILING} characters, and not one the workbook already has.
     */
    static String sheetName(Set<String> taken, String wanted) {
        String clean = wanted.replaceAll("[\\\\/?*\\[\\]:]", " ").trim();
        String name = clean.length() > SHEET_NAME_CEILING ? clean.substring(0, SHEET_NAME_CEILING) : clean;
        int n = 2;
        while (taken.contains(name.toLowerCase(Locale.ROOT))) {
            String suffix = " " + n++;
            name = (clean.length() > SHEET_NAME_CEILING - suffix.length()
                    ? clean.substring(0, SHEET_NAME_CEILING - suffix.length()) : clean) + suffix;
        }
        taken.add(name.toLowerCase(Locale.ROOT));
        return name;
    }

    private static String monthName(YearMonth month) {
        return month.getMonth().getDisplayName(TextStyle.FULL, Locale.ENGLISH) + " " + month.getYear();
    }

    /** A workbook written through the streaming writer, and its temporary files gone after. */
    static byte[] write(Consumer<SXSSFWorkbook> body) {
        SXSSFWorkbook book = new SXSSFWorkbook(WINDOW);
        try {
            body.accept(book);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            book.write(out);
            return out.toByteArray();
        } catch (IOException unwritable) {
            throw new UncheckedIOException(unwritable);
        } finally {
            book.dispose();
            try {
                book.close();
            } catch (IOException ignored) {
                // Nothing to do: the bytes are written or the failure above says why.
            }
        }
    }
}
