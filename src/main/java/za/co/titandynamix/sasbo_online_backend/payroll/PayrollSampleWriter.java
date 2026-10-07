package za.co.titandynamix.sasbo_online_backend.payroll;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * Writes an employer's payroll file in its own layout, for the demonstration seed -- the headings verbatim and each
 * layout's habits reproduced, from the fixtures' layouts of SASBO's samples ({@code LAYOUTS.md}, 6-7 Oct): Absa's
 * padded text and negative amounts with a For-period, Capitec's names first-name-first and negative amounts, FNB's
 * apostrophe before the ID number and its adjustment column, Standard Bank's debit, credit and a Total formula with its
 * worked-out value. The people are the seed's invented members; nothing is copied from a file of SASBO's.
 */
public final class PayrollSampleWriter {

    /** One line of an employer's file. {@code amountText} replaces the amount where a line's amount is not a number. */
    public record Line(String staffNumber, String idNumber, String title, String initials, String firstNames,
                       String surname, BigDecimal amount, String amountText, YearMonth forPeriod,
                       BigDecimal adjustment) {

        @Override
        public String toString() {
            return "Line[personal data withheld]";
        }
    }

    private static final DateTimeFormatter YYYYMM = DateTimeFormatter.ofPattern("yyyyMM", Locale.ROOT);

    private PayrollSampleWriter() {
    }

    /** The file in {@code layoutKey}'s layout; the layouts the seed writes are Absa's, Capitec's, FNB's and Standard Bank's. */
    public static byte[] write(String layoutKey, YearMonth period, List<Line> lines) {
        return switch (layoutKey) {
            case "absa" -> workbook(sheet -> absa(sheet, period, lines));
            case "capitec" -> workbook(sheet -> capitec(sheet, lines));
            case "fnb-wesbank" -> workbook(sheet -> fnb(sheet, lines));
            case "standard-bank" -> workbook(sheet -> standardBank(sheet, lines));
            default -> throw new IllegalArgumentException("the seed writes no file in the layout " + layoutKey);
        };
    }

    private static byte[] workbook(Consumer<Sheet> fill) {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            fill.accept(workbook.createSheet("Sheet1"));
            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("a demonstration payroll file could not be written", e);
        }
    }

    // --------------------------------------------------------------------------------------------------- Absa

    private static void absa(Sheet sheet, YearMonth period, List<Line> lines) {
        headings(sheet, false, " Pers.No.", "Last name First name          ", "PY Area, FP", "WT  ",
                "Wage Type Long Text      ", "               Number", "               Amount", "For-period",
                "In-Period");
        CellStyle money = format(sheet, "#,##0.00");
        int r = 1;
        for (Line line : lines) {
            Row row = sheet.createRow(r++);
            staff(row, 0, line.staffNumber());
            String name = line.surname().toUpperCase(Locale.ROOT) + " , "
                    + (line.firstNames() == null ? line.initials() : line.firstNames()).toUpperCase(Locale.ROOT);
            row.createCell(1).setCellValue(pad(name, 30));
            row.createCell(2).setCellValue(pad("Z1", 11));
            row.createCell(3).setCellValue(9001);
            row.createCell(4).setCellValue(pad("Sasbo", 25));
            row.createCell(5).setCellValue(0);
            amount(row, 6, line, true, money);
            row.createCell(7).setCellValue(Long.parseLong((line.forPeriod() == null ? period : line.forPeriod())
                    .format(YYYYMM)));
            row.createCell(8).setCellValue(Long.parseLong(period.format(YYYYMM)));
        }
        sheet.setColumnWidth(1, 44 * 256);
        sheet.setColumnWidth(6, 16 * 256);
    }

    // ------------------------------------------------------------------------------------------------ Capitec

    private static void capitec(Sheet sheet, List<Line> lines) {
        headings(sheet, true, "Personnel Number", "Name of Employee or Applicant", "Company Name",
                "Sum of 9001-SASBO Union");
        int r = 1;
        for (Line line : lines) {
            Row row = sheet.createRow(r++);
            row.createCell(0).setCellValue(line.staffNumber() == null ? "" : line.staffNumber());
            row.createCell(1).setCellValue((line.firstNames() == null ? line.initials() : line.firstNames()) + " "
                    + line.surname());
            row.createCell(2).setCellValue("Capitec Bank Limited");
            amount(row, 3, line, true, null);
        }
    }

    // --------------------------------------------------------------------------------------------- FNB & Wesbank

    private static void fnb(Sheet sheet, List<Line> lines) {
        headings(sheet, false, "Payroll Run", "Employee Number", "First Name", "Surname", "IDNumber", "Home Company",
                "Home CostCentre", "SASBO Membership Fees", "SASBO Membership Fees Adjustment");
        CellStyle money = format(sheet, "0.00");
        int r = 1;
        for (Line line : lines) {
            Row row = sheet.createRow(r++);
            row.createCell(0).setCellValue("Main Run");
            if (line.staffNumber() != null) {
                row.createCell(1).setCellValue(line.staffNumber());
            }
            row.createCell(2).setCellValue(line.firstNames() == null ? line.initials() : line.firstNames());
            row.createCell(3).setCellValue(line.surname());
            if (line.idNumber() != null) {
                // FNB's ID numbers carry a leading apostrophe as part of the value.
                row.createCell(4).setCellValue("'" + line.idNumber());
            }
            row.createCell(5).setCellValue(r % 3 == 0 ? "0002" : "0001");
            row.createCell(6).setCellValue("10" + String.format(Locale.ROOT, "%08d", 4_000_000 + r * 97));
            amount(row, 7, line, false, money);
            Cell adjustment = row.createCell(8);
            adjustment.setCellValue(line.adjustment() == null ? 0 : line.adjustment().doubleValue());
            adjustment.setCellStyle(money);
        }
    }

    // ------------------------------------------------------------------------------------------- Standard Bank

    private static void standardBank(Sheet sheet, List<Line> lines) {
        headings(sheet, true, "PerNr", "Name", "Inits", "W.Type", "WT Text", "G/L Acc", "G/L Acc. Text",
                "Debit Amnt", "Credit Amnt", "Total");
        int r = 1;
        for (Line line : lines) {
            Row row = sheet.createRow(r++);
            staff(row, 0, line.staffNumber());
            row.createCell(1).setCellValue(line.surname());
            row.createCell(2).setCellValue(line.initials() == null ? "" : line.initials());
            row.createCell(3).setCellValue("9UF1");
            row.createCell(4).setCellValue("Union Fees");
            row.createCell(5).setCellValue(4_400_123);
            row.createCell(6).setCellValue("SASBO UNION FEES");
            row.createCell(7).setCellValue(0);
            double credit = line.amount() == null ? 0 : -line.amount().doubleValue();
            row.createCell(8).setCellValue(credit);
            Cell total = row.createCell(9);
            total.setCellFormula("H" + r + "+I" + r);
            // The value the formula last worked out to, as Excel stores it: the reader takes this, never recalculates.
            total.setCellValue(credit);
        }
        sheet.setColumnWidth(0, 11 * 256);
    }

    // ---------------------------------------------------------------------------------------------- helpers

    private static void headings(Sheet sheet, boolean bold, String... headings) {
        CellStyle style = sheet.getWorkbook().createCellStyle();
        if (bold) {
            Font font = sheet.getWorkbook().createFont();
            font.setBold(true);
            style.setFont(font);
        }
        Row row = sheet.createRow(0);
        for (int c = 0; c < headings.length; c++) {
            Cell cell = row.createCell(c);
            cell.setCellValue(headings[c]);
            cell.setCellStyle(style);
        }
    }

    private static CellStyle format(Sheet sheet, String pattern) {
        CellStyle style = sheet.getWorkbook().createCellStyle();
        style.setDataFormat(sheet.getWorkbook().createDataFormat().getFormat(pattern));
        return style;
    }

    /** A staff number as a number where it is all digits (as Absa and Standard Bank store it), else as text. */
    private static void staff(Row row, int column, String staffNumber) {
        if (staffNumber == null) {
            return;
        }
        if (staffNumber.matches("[1-9]\\d{0,14}")) {
            row.createCell(column).setCellValue(Long.parseLong(staffNumber));
        } else {
            row.createCell(column).setCellValue(staffNumber);
        }
    }

    private static void amount(Row row, int column, Line line, boolean negative, CellStyle style) {
        Cell cell = row.createCell(column);
        if (line.amountText() != null) {
            cell.setCellValue(line.amountText());
            return;
        }
        if (line.amount() == null) {
            row.removeCell(cell);
            return;
        }
        cell.setCellValue(negative ? -line.amount().doubleValue() : line.amount().doubleValue());
        if (style != null) {
            cell.setCellStyle(style);
        }
    }

    private static String pad(String text, int width) {
        return text.length() >= width ? text : text + " ".repeat(width - text.length());
    }
}
