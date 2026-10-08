package za.co.titandynamix.sasbo_online_backend.legacy.read;

import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static za.co.titandynamix.sasbo_online_backend.legacy.LegacyFixtures.limits;
import static za.co.titandynamix.sasbo_online_backend.legacy.LegacyFixtures.read;

/**
 * A workbook, read from its sheet's XML: dates as ISO, an identity number typed as a number as its thirteen digits, an
 * amount as its format shows it, missing cells as blanks -- and anything that is not a workbook refused by its bytes.
 * Every workbook here is built by the test, of invented values.
 */
@DisplayName("XlsxSourceReader")
class XlsxSourceReaderTest {

    /** A workbook of SASBO's spreadsheet shape: a title row, a header row, then members. */
    private static byte[] workbook(String sheetName) throws IOException {
        try (XSSFWorkbook book = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            book.createSheet("Notes").createRow(0).createCell(0).setCellValue("not this sheet");
            Sheet sheet = book.createSheet(sheetName);
            CellStyle date = book.createCellStyle();
            date.setDataFormat(book.getCreationHelper().createDataFormat().getFormat("yyyy/mm/dd"));
            CellStyle amount = book.createCellStyle();
            amount.setDataFormat(book.getCreationHelper().createDataFormat().getFormat("0.00"));

            sheet.createRow(0).createCell(0).setCellValue("MISCELLANEOUS EXTRACT - 2026/08/31");
            Row header = sheet.createRow(1);
            String[] names = {"Name", "ID Number", "Date Joined", "SUB", "Membership Number"};
            for (int i = 0; i < names.length; i++) {
                header.createCell(i).setCellValue(names[i]);
            }
            Row ann = sheet.createRow(2);
            ann.createCell(0).setCellValue("Ann Example");
            ann.createCell(1).setCellValue(9404275800086d);
            ann.createCell(2).setCellValue(LocalDate.of(2015, 3, 1));
            ann.getCell(2).setCellStyle(date);
            ann.createCell(3).setCellValue(174);
            ann.getCell(3).setCellStyle(amount);
            ann.createCell(4).setCellValue(700002);
            Row ben = sheet.createRow(3);
            ben.createCell(0).setCellValue("Ben Sample");
            ben.createCell(4).setCellValue(700003);
            book.write(out);
            return out.toByteArray();
        }
    }

    @Test
    @DisplayName("reads the named sheet past its title row: a date as ISO, a 13-digit number whole, an amount as formatted")
    void readsTheSheet() throws IOException {
        List<SourceRow> rows = read(new XlsxSourceReader(), workbook("Members"), ReaderSettings.xlsx("Members", true)
                .skipping(1), limits());

        assertThat(rows).hasSize(2);
        assertThat(rows.get(0).values())
                .containsEntry("Name", "Ann Example")
                .containsEntry("ID Number", "9404275800086")
                .containsEntry("Date Joined", "2015-03-01")
                .containsEntry("SUB", "174.00")
                .containsEntry("Membership Number", "700002");
        assertThat(rows.get(0).rowNumber()).as("the sheet's own row number").isEqualTo(3);
        assertThat(rows.get(0).problems()).isEmpty();
    }

    @Test
    @DisplayName("a cell a row leaves out is blank -- in a workbook that is not a short row")
    void missingCellsAreBlank() throws IOException {
        List<SourceRow> rows = read(new XlsxSourceReader(), workbook("Members"), ReaderSettings.xlsx("Members", true)
                .skipping(1), limits());

        assertThat(rows.get(1).values()).containsEntry("ID Number", "").containsEntry("Membership Number", "700003");
        assertThat(rows.get(1).problems()).isEmpty();
    }

    @Test
    @DisplayName("a sheet the workbook does not have is refused, NOT_READABLE")
    void missingSheet() {
        assertThatThrownBy(() -> read(new XlsxSourceReader(), workbook("Members"), ReaderSettings.xlsx("Absent", true),
                limits()))
                .isInstanceOf(SourceRefused.class)
                .extracting(refused -> ((SourceRefused) refused).reason()).isEqualTo(SourceRefused.Reason.NOT_READABLE);
    }

    @Test
    @DisplayName("never trusts a name: text is not a workbook, and neither is a zip with no workbook in it -- NOT_READABLE")
    void notAWorkbook() throws IOException {
        ByteArrayOutputStream zip = new ByteArrayOutputStream();
        try (ZipOutputStream out = new ZipOutputStream(zip)) {
            out.putNextEntry(new ZipEntry("readme.txt"));
            out.write("not a workbook".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            out.closeEntry();
        }
        for (byte[] bytes : List.of("Name\nAnn\n".getBytes(java.nio.charset.StandardCharsets.UTF_8), zip.toByteArray())) {
            assertThatThrownBy(() -> read(new XlsxSourceReader(), bytes, ReaderSettings.xlsx(null, true), limits()))
                    .isInstanceOf(SourceRefused.class)
                    .extracting(refused -> ((SourceRefused) refused).reason())
                    .isEqualTo(SourceRefused.Reason.NOT_READABLE);
        }
    }

    @Test
    @DisplayName("the first sheet when the mapping names none")
    void firstSheet() throws IOException {
        List<SourceRow> rows = read(new XlsxSourceReader(), workbook("Members"), ReaderSettings.xlsx(null, false),
                limits());

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).values()).containsEntry("#1", "not this sheet");
    }
}
