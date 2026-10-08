package za.co.titandynamix.sasbo_online_backend.payroll;

import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.streaming.SXSSFSheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import za.co.titandynamix.sasbo_online_backend.utils.SpreadsheetText;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * A {@link ReportDocument} as a workbook: one sheet per part, the title and the file's details at the top, a heading
 * row in bold with filters on and the rows below it frozen, amounts as numbers in {@code #,##0.00}. Streamed (POI's
 * SXSSF keeps a hundred rows in memory), so twenty thousand lines cost what a hundred do.
 *
 * <p>🔒 Every text cell passes through {@link SpreadsheetText#safe}: a name or a note on an employer's file is typed by
 * someone else, and {@code =cmd|...} in a cell must be shown as the text it is, never run (month end's rule, MV-13).
 */
public final class ReportXlsx {

    private static final String DASH = "—";
    private static final int WINDOW = 100;

    private ReportXlsx() {
    }

    public static byte[] write(ReportDocument document) {
        SXSSFWorkbook workbook = new SXSSFWorkbook(WINDOW);
        workbook.setCompressTempFiles(true);
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Styles styles = new Styles(workbook);
            Set<String> names = new HashSet<>();
            for (ReportDocument.Section section : document.sections()) {
                writeSection(workbook, styles, document, section, uniqueName(section.sheetName(), names));
            }
            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("a payroll workbook could not be written", e);
        } finally {
            try {
                workbook.close();
            } catch (IOException ignored) {
                // Closing removes the streamed rows' temporary files; a failure there loses nothing written.
            }
        }
    }

    private static void writeSection(SXSSFWorkbook workbook, Styles styles, ReportDocument document,
                                     ReportDocument.Section section, String name) {
        SXSSFSheet sheet = workbook.createSheet(name);
        int r = 0;
        text(sheet.createRow(r++), 0, document.title(), styles.title);
        for (String line : document.header()) {
            text(sheet.createRow(r++), 0, line, styles.plain);
        }
        r++;
        text(sheet.createRow(r++), 0, section.heading(), styles.heading);
        for (String[] fact : section.facts()) {
            Row row = sheet.createRow(r++);
            text(row, 0, fact[0], styles.bold);
            text(row, 1, fact[1], styles.plain);
        }
        List<ReportDocument.Column> columns = section.columns();
        if (columns == null || columns.isEmpty()) {
            return;
        }
        r++;
        int headingRow = r;
        Row heading = sheet.createRow(r++);
        for (int c = 0; c < columns.size(); c++) {
            text(heading, c, columns.get(c).heading(), styles.columnHeading);
            sheet.setColumnWidth(c, Math.min(255 * 60, Math.max(255 * 10, (int) (columns.get(c).weight() * 255 * 14))));
        }
        if (section.rows().isEmpty() && section.empty() != null) {
            text(sheet.createRow(r++), 0, section.empty(), styles.plain);
        }
        for (List<Object> values : section.rows()) {
            Row row = sheet.createRow(r++);
            for (int c = 0; c < columns.size(); c++) {
                cell(row, c, columns.get(c).kind(), c < values.size() ? values.get(c) : null, styles, false);
            }
        }
        if (section.totals() != null) {
            Row row = sheet.createRow(r++);
            for (int c = 0; c < columns.size(); c++) {
                Object value = c < section.totals().size() ? section.totals().get(c) : null;
                if (value != null) {
                    cell(row, c, columns.get(c).kind(), value, styles, true);
                }
            }
        }
        sheet.createFreezePane(0, headingRow + 1);
        if (!section.rows().isEmpty()) {
            sheet.setAutoFilter(new CellRangeAddress(headingRow, headingRow + section.rows().size(), 0,
                    columns.size() - 1));
        }
    }

    private static void cell(Row row, int column, ReportDocument.Kind kind, Object value, Styles styles, boolean bold) {
        if (value == null) {
            text(row, column, DASH, bold ? styles.bold : styles.plain);
            return;
        }
        if (kind == ReportDocument.Kind.MONEY && value instanceof BigDecimal amount) {
            Cell cell = row.createCell(column);
            cell.setCellValue(amount.doubleValue());
            cell.setCellStyle(bold ? styles.moneyBold : styles.money);
            return;
        }
        if (kind == ReportDocument.Kind.INTEGER && value instanceof Number number) {
            Cell cell = row.createCell(column);
            cell.setCellValue(number.longValue());
            cell.setCellStyle(bold ? styles.integerBold : styles.integer);
            return;
        }
        text(row, column, String.valueOf(value), bold ? styles.bold : styles.plain);
    }

    private static void text(Row row, int column, String value, CellStyle style) {
        Cell cell = row.createCell(column);
        cell.setCellValue(SpreadsheetText.safe(value == null ? DASH : value));
        cell.setCellStyle(style);
    }

    private static String uniqueName(String wanted, Set<String> taken) {
        String base = wanted.replaceAll("[\\\\/?*\\[\\]:]", " ").trim();
        base = base.isEmpty() ? "Sheet" : base.length() > 28 ? base.substring(0, 28) : base;
        String name = base;
        for (int i = 2; !taken.add(name.toLowerCase(java.util.Locale.ROOT)); i++) {
            name = base + " " + i;
        }
        return name;
    }

    /** The few styles a report uses, made once per workbook. */
    private static final class Styles {
        final CellStyle title;
        final CellStyle heading;
        final CellStyle plain;
        final CellStyle bold;
        final CellStyle columnHeading;
        final CellStyle money;
        final CellStyle moneyBold;
        final CellStyle integer;
        final CellStyle integerBold;

        Styles(SXSSFWorkbook workbook) {
            Font titleFont = workbook.createFont();
            titleFont.setBold(true);
            titleFont.setFontHeightInPoints((short) 14);
            Font headingFont = workbook.createFont();
            headingFont.setBold(true);
            headingFont.setFontHeightInPoints((short) 12);
            Font boldFont = workbook.createFont();
            boldFont.setBold(true);
            title = workbook.createCellStyle();
            title.setFont(titleFont);
            heading = workbook.createCellStyle();
            heading.setFont(headingFont);
            plain = workbook.createCellStyle();
            bold = workbook.createCellStyle();
            bold.setFont(boldFont);
            columnHeading = workbook.createCellStyle();
            columnHeading.setFont(boldFont);
            columnHeading.setFillForegroundColor(IndexedColors.LIGHT_TURQUOISE.getIndex());
            columnHeading.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            columnHeading.setBorderBottom(BorderStyle.THIN);
            short moneyFormat = workbook.createDataFormat().getFormat("#,##0.00");
            short integerFormat = workbook.createDataFormat().getFormat("0");
            money = workbook.createCellStyle();
            money.setDataFormat(moneyFormat);
            moneyBold = workbook.createCellStyle();
            moneyBold.setDataFormat(moneyFormat);
            moneyBold.setFont(boldFont);
            integer = workbook.createCellStyle();
            integer.setDataFormat(integerFormat);
            integerBold = workbook.createCellStyle();
            integerBold.setDataFormat(integerFormat);
            integerBold.setFont(boldFont);
        }
    }
}
