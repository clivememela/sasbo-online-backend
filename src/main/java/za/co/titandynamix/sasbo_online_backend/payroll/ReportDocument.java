package za.co.titandynamix.sasbo_online_backend.payroll;

import java.util.List;

/**
 * A payroll report as data -- written as a workbook ({@link ReportXlsx}) and as a PDF ({@link ReportPdf}) from the
 * same content, so the two can only differ in form (Stephne, PAY-37: "Spreadsheet; PDF; Both").
 *
 * <p>A cell is a {@code String}, a {@code BigDecimal} (an amount), an {@code Integer} or a {@code Long}, or
 * {@code null} -- written as a dash, never left blank (fixtures' {@code RECON-REPORT.md}: "a missing side shown as
 * '—', never blank").
 */
public record ReportDocument(String title, List<String> header, List<Section> sections) {

    /** What a column holds, and so how it is written and aligned. */
    public enum Kind { TEXT, MONEY, INTEGER }

    /** A column: its heading, what it holds, and its share of the page's width in the PDF. */
    public record Column(String heading, Kind kind, float weight) {
    }

    /**
     * A part of the report: a sheet of the workbook, and a run of pages in the PDF. {@code facts} are label-value
     * lines above the table; {@code totals}, when given, is a closing row in bold; {@code empty} is said where the
     * table has no rows.
     */
    public record Section(String sheetName, String heading, List<String[]> facts, List<Column> columns,
                          List<List<Object>> rows, List<Object> totals, String empty, boolean newPage) {
    }
}
