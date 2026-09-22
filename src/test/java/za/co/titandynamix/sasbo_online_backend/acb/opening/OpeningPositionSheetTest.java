package za.co.titandynamix.sasbo_online_backend.acb.opening;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import za.co.titandynamix.sasbo_online_backend.exception.AcbRefusal;
import za.co.titandynamix.sasbo_online_backend.exception.AcbRefusalCode;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.YearMonth;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

/**
 * The opening-position sheet, read with its limits stated (SD3-B). Every workbook here is built in
 * memory from synthetic values.
 */
@DisplayName("OpeningPositionSheet -- one sheet, stated limits, nothing evaluated, nothing echoed")
public class OpeningPositionSheetTest {

    @Test
    @DisplayName("a good sheet reads row by row -- text or numeric numbers, YYYY-MM or a date cell")
    void aGoodSheet() throws IOException {
        OpeningPositionSheet.Read read = OpeningPositionSheet.read(workbook(sheet -> {
            row(sheet, 1, "M-100045", 2, "2026-06");
            Row numeric = sheet.createRow(2);
            numeric.createCell(0).setCellValue(100046);
            numeric.createCell(1).setCellValue(1);
            numeric.createCell(2).setCellValue("2026-05");
        }));
        assertThat(read.problems()).isEmpty();
        assertThat(read.lines()).extracting(OpeningPositionSheet.Line::membershipNumber).containsExactly("M-100045", "100046");
        assertThat(read.lines()).extracting(OpeningPositionSheet.Line::lastUnpaidMonth)
                .containsExactly(YearMonth.of(2026, 6), YearMonth.of(2026, 5));
    }

    @Test
    @DisplayName("a formula is refused, never evaluated -- and the problem names the row and field, never the value")
    void aFormulaIsRefused() throws IOException {
        OpeningPositionSheet.Read read = OpeningPositionSheet.read(workbook(sheet -> {
            Row row = sheet.createRow(1);
            row.createCell(0).setCellValue("M-SECRET-1");
            row.createCell(1).setCellFormula("1+1");
            row.createCell(2).setCellValue("2026-06");
        }));
        assertThat(read.problems()).singleElement().satisfies(problem -> {
            assertThat(problem.row()).isEqualTo(2);
            assertThat(problem.field()).isEqualTo("unpaidsStanding");
            assertThat(problem.toString()).doesNotContain("M-SECRET-1").doesNotContain("1+1");
        });
    }

    @Test
    @DisplayName("more than one sheet, and more than 500 rows, are problems of the sheet itself")
    void theSheetsOwnLimits() throws IOException {
        byte[] twoSheets;
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            header(workbook.createSheet("one"));
            row(workbook.getSheetAt(0), 1, "M-1", 1, "2026-06");
            workbook.createSheet("two");
            workbook.write(out);
            twoSheets = out.toByteArray();
        }
        assertThat(OpeningPositionSheet.read(twoSheets).problems()).anySatisfy(p -> assertThat(p.row()).isZero());

        OpeningPositionSheet.Read long501 = OpeningPositionSheet.read(workbook(sheet -> {
            for (int r = 1; r <= 501; r++) {
                row(sheet, r, "M-" + r, 1, "2026-06");
            }
        }));
        assertThat(long501.problems()).anySatisfy(p -> assertThat(p.problem()).contains("more than 500 rows"));
        // The must-pass twin: five hundred is within it.
        OpeningPositionSheet.Read long500 = OpeningPositionSheet.read(workbook(sheet -> {
            for (int r = 1; r <= 500; r++) {
                row(sheet, r, "M-" + r, 1, "2026-06");
            }
        }));
        assertThat(long500.problems()).isEmpty();
    }

    @Test
    @DisplayName("anything that is not an .xlsx is UPLOAD_NOT_A_SHEET before POI reads it; over a megabyte is UPLOAD_TOO_LARGE")
    void theFileItself() {
        assertThat(catchThrowableOfType(AcbRefusal.class, () -> OpeningPositionSheet.read("membership,unpaids\n".getBytes()))
                .code()).isEqualTo(AcbRefusalCode.UPLOAD_NOT_A_SHEET);
        assertThat(catchThrowableOfType(AcbRefusal.class, () -> OpeningPositionSheet.read(new byte[] {'P', 'K', 3, 4, 0, 0}))
                .code()).as("a zip header and no workbook").isEqualTo(AcbRefusalCode.UPLOAD_NOT_A_SHEET);
        byte[] big = new byte[OpeningPositionSheet.MAX_BYTES + 1];
        big[0] = 'P';
        big[1] = 'K';
        big[2] = 3;
        big[3] = 4;
        AcbRefusal tooLarge = catchThrowableOfType(AcbRefusal.class, () -> OpeningPositionSheet.read(big));
        assertThat(tooLarge.code()).isEqualTo(AcbRefusalCode.UPLOAD_TOO_LARGE);
        assertThat(tooLarge.details()).containsEntry("limitBytes", String.valueOf(OpeningPositionSheet.MAX_BYTES));
    }

    @Test
    @DisplayName("a missing number, a count that is not a number and a month not written YYYY-MM are each named; a repeated number too")
    void eachFieldIsNamed() throws IOException {
        OpeningPositionSheet.Read read = OpeningPositionSheet.read(workbook(sheet -> {
            row(sheet, 1, "M-7", 1, "2026-06");
            Row bad = sheet.createRow(2);
            bad.createCell(1).setCellValue("two");
            bad.createCell(2).setCellValue("June 2026");
            row(sheet, 3, "M-7", 1, "2026-06");
        }));
        assertThat(read.problems()).extracting(OpeningPositionSheet.Problem::row, OpeningPositionSheet.Problem::field)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple(3, "membershipNumber"),
                        org.assertj.core.groups.Tuple.tuple(3, "unpaidsStanding"),
                        org.assertj.core.groups.Tuple.tuple(3, "lastUnpaidMonth"),
                        org.assertj.core.groups.Tuple.tuple(4, "membershipNumber"));
    }

    // ------------------------------------------------------------------------------------------

    /** A one-sheet workbook with the header row, and whatever {@code rows} adds -- for this class and the IT. */
    public static byte[] workbook(Consumer<Sheet> rows) throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Opening position");
            header(sheet);
            rows.accept(sheet);
            workbook.write(out);
            return out.toByteArray();
        }
    }

    public static void header(Sheet sheet) {
        Row header = sheet.createRow(0);
        header.createCell(0).setCellValue("Membership number");
        header.createCell(1).setCellValue("Unpaids standing");
        header.createCell(2).setCellValue("Last unpaid month");
    }

    public static void row(Sheet sheet, int index, String number, int unpaids, String month) {
        Row row = sheet.createRow(index);
        row.createCell(0).setCellValue(number);
        row.createCell(1).setCellValue(unpaids);
        row.createCell(2).setCellValue(month);
    }
}
