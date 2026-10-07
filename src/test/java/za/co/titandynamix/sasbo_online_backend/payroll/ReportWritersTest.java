package za.co.titandynamix.sasbo_online_backend.payroll;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.poifs.crypt.Decryptor;
import org.apache.poi.poifs.crypt.EncryptionInfo;
import org.apache.poi.poifs.filesystem.POIFSFileSystem;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The report writers, read back: the workbook's sheets and cells, the PDF's text -- and the two protections. A cell
 * typed by someone else that a spreadsheet would run ({@code =cmd|...}) is written as the text it is.
 */
@DisplayName("The payroll report writers, read back")
class ReportWritersTest {

    private static ReportDocument document(int rows) {
        List<List<Object>> table = new ArrayList<>();
        table.add(java.util.Arrays.asList("Not a member", 5, "=cmd|' /C calc'!A0", new BigDecimal("174.00"), null));
        for (int i = 1; i < rows; i++) {
            table.add(List.of("Amount differs", i + 10, "Testerson " + i, new BigDecimal("166.65"),
                    "Line " + (i + 10) + ": R166.65 deducted; R174.00 expected: R7.35 short."));
        }
        List<ReportDocument.Column> columns = List.of(
                new ReportDocument.Column("Type", ReportDocument.Kind.TEXT, 1.2f),
                new ReportDocument.Column("Line", ReportDocument.Kind.INTEGER, 0.5f),
                new ReportDocument.Column("Name on file", ReportDocument.Kind.TEXT, 1.4f),
                new ReportDocument.Column("On file", ReportDocument.Kind.MONEY, 0.8f),
                new ReportDocument.Column("What was found", ReportDocument.Kind.TEXT, 2.6f));
        return new ReportDocument("Payroll discrepancies — Absa — September 2026",
                List.of("File: Absa payroll September 2026.xlsx", "Printed 7 October 2026, 08:00 by system"),
                List.of(new ReportDocument.Section("Summary", "Summary", List.<String[]>of(new String[] {"Lines on the "
                                + "employer's file", "23"}), List.of(), List.of(), null, null, false),
                        new ReportDocument.Section("Discrepancies", "Discrepancies", List.of(), columns, table,
                                java.util.Arrays.asList(rows + " discrepancies", null, null, new BigDecimal("999.00"), null),
                                "No discrepancies.", false)));
    }

    @Test
    @DisplayName("the workbook: a sheet per part, the title on top, amounts as numbers, and a formula shown as text")
    void theWorkbook() throws IOException {
        byte[] bytes = ReportXlsx.write(document(3));
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            assertThat(workbook.getSheetName(0)).isEqualTo("Summary");
            assertThat(workbook.getSheetName(1)).isEqualTo("Discrepancies");
            Sheet sheet = workbook.getSheet("Discrepancies");
            assertThat(sheet.getRow(0).getCell(0).getStringCellValue()).startsWith("Payroll discrepancies");
            int heading = -1;
            for (int r = 0; r <= sheet.getLastRowNum(); r++) {
                if (sheet.getRow(r) != null && "Type".equals(sheet.getRow(r).getCell(0).getStringCellValue())) {
                    heading = r;
                }
            }
            assertThat(heading).isPositive();
            var first = sheet.getRow(heading + 1);
            assertThat(first.getCell(2).getCellType()).isEqualTo(CellType.STRING);
            assertThat(first.getCell(2).getStringCellValue()).as("🔒 a formula typed into a name is shown, never run")
                    .isEqualTo("'=cmd|' /C calc'!A0");
            assertThat(first.getCell(3).getCellType()).isEqualTo(CellType.NUMERIC);
            assertThat(first.getCell(3).getNumericCellValue()).isEqualTo(174.0);
            assertThat(first.getCell(1).getNumericCellValue()).isEqualTo(5.0);
            var totals = sheet.getRow(heading + 4);
            assertThat(totals.getCell(0).getStringCellValue()).isEqualTo("3 discrepancies");
            assertThat(first.getCell(4).getStringCellValue()).as("a missing value is a dash, never blank").isEqualTo("—");
        }
    }

    @Test
    @DisplayName("the PDF: the title and the file's details on every page, the rows, and 'Page n of m'")
    void thePdf() throws IOException {
        byte[] bytes = ReportPdf.write(document(120));
        try (PDDocument pdf = Loader.loadPDF(bytes)) {
            assertThat(pdf.getNumberOfPages()).as("120 rows run onto several pages").isGreaterThan(1);
            String text = new PDFTextStripper().getText(pdf);
            assertThat(text).contains("Payroll discrepancies", "Absa", "September 2026", "Lines on the employer's file",
                    "Not a member", "R174.00", "Page 1 of " + pdf.getNumberOfPages(), "120 discrepancies");
            PDFTextStripper lastPage = new PDFTextStripper();
            lastPage.setStartPage(pdf.getNumberOfPages());
            lastPage.setEndPage(pdf.getNumberOfPages());
            assertThat(lastPage.getText(pdf)).as("the head of every page").contains("Payroll discrepancies",
                    "File: Absa payroll September 2026.xlsx", "Type", "On file");
        }
    }

    @Test
    @DisplayName("a character the standard fonts cannot draw prints as '?', never refusing the report")
    void unprintableCharacters() throws IOException {
        ReportDocument doc = new ReportDocument("T", List.of(), List.of(new ReportDocument.Section("S", "S",
                List.<String[]>of(new String[] {"Name", "Łódź 中"}), List.of(), List.of(), null, null,
                false)));
        try (PDDocument pdf = Loader.loadPDF(ReportPdf.write(doc))) {
            assertThat(new PDFTextStripper().getText(pdf)).contains("?");
        }
    }

    @Test
    @DisplayName("🔒 the employer's list, protected: the workbook opens with the password and only with it")
    void aProtectedWorkbook() throws Exception {
        byte[] locked = ProtectedFiles.workbook(ReportXlsx.write(document(2)), "Correct horse 9");
        try (POIFSFileSystem fs = new POIFSFileSystem(new ByteArrayInputStream(locked))) {
            Decryptor decryptor = new EncryptionInfo(fs).getDecryptor();
            assertThat(decryptor.verifyPassword("wrong password")).isFalse();
            assertThat(decryptor.verifyPassword("Correct horse 9")).isTrue();
            try (InputStream data = decryptor.getDataStream(fs); XSSFWorkbook workbook = new XSSFWorkbook(data)) {
                assertThat(workbook.getSheetName(1)).isEqualTo("Discrepancies");
            }
        }
    }

    @Test
    @DisplayName("🔒 the employer's list, protected: the PDF opens with the password and not without")
    void aProtectedPdf() throws IOException {
        byte[] locked = ProtectedFiles.pdf(ReportPdf.write(document(2)), "Correct horse 9");
        assertThatThrownBy(() -> Loader.loadPDF(locked).close()).isInstanceOf(InvalidPasswordException.class);
        try (PDDocument pdf = Loader.loadPDF(locked, "Correct horse 9")) {
            assertThat(pdf.isEncrypted()).isTrue();
            assertThat(new PDFTextStripper().getText(pdf)).contains("Payroll discrepancies");
        }
    }
}
