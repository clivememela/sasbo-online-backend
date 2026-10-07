package za.co.titandynamix.sasbo_online_backend.payroll;

import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import za.co.titandynamix.sasbo_online_backend.exception.PayrollRefusal;
import za.co.titandynamix.sasbo_online_backend.exception.PayrollRefusalCode;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.YearMonth;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Reading a sheet within hard limits (BRIEF §6): what a file IS by its bytes, never its name; more lines than one file
 * may carry refused before the rest is read; a zip that expands far beyond its size refused before POI holds it; a
 * zip that holds no workbook, or a workbook behind a password, refused with a sentence an administrator can act on.
 */
@DisplayName("Reading a payroll sheet, within limits")
class GridReaderTest {

    @Test
    @DisplayName("what a file is comes from its first bytes: a workbook, an old workbook, text, a PDF, a picture")
    void sniffing() throws IOException {
        assertThat(FileSniffer.sniff(workbook())).isEqualTo(FileSniffer.Kind.XLSX);
        assertThat(FileSniffer.sniff("a,b\n1,2\n".getBytes(StandardCharsets.UTF_8))).isEqualTo(FileSniffer.Kind.CSV);
        assertThat(FileSniffer.sniff("%PDF-1.7\n...".getBytes(StandardCharsets.US_ASCII))).isEqualTo(FileSniffer.Kind.PDF);
        assertThat(FileSniffer.sniff(new byte[] {(byte) 0x89, 'P', 'N', 'G', 13, 10})).isEqualTo(FileSniffer.Kind.PICTURE);
        assertThat(FileSniffer.sniff(new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0})).isEqualTo(FileSniffer.Kind.PICTURE);
        assertThat(FileSniffer.sniff(new byte[] {(byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0, (byte) 0xA1, (byte) 0xB1,
                0x1A, (byte) 0xE1})).isEqualTo(FileSniffer.Kind.XLS);
        assertThat(FileSniffer.sniff(new byte[] {0, 1, 2, 3, 4, 5})).isEqualTo(FileSniffer.Kind.UNKNOWN);
        assertThat(FileSniffer.sniff(new byte[] {1})).isEqualTo(FileSniffer.Kind.UNKNOWN);
    }

    @Test
    @DisplayName("more lines than one file may carry is TOO_MANY_ROWS, with the limit")
    void tooManyRows() {
        StringBuilder csv = new StringBuilder("staffNumber,surname,amount\n");
        for (int i = 0; i <= PayrollLimits.MAX_ROWS + 60; i++) {
            csv.append(i).append(",Testerson,174.00\n");
        }
        byte[] bytes = csv.toString().getBytes(StandardCharsets.UTF_8);
        assertThatThrownBy(() -> PayrollParser.parse(GridReader.read(FileSniffer.Kind.CSV, bytes),
                PayrollLayouts.typed(), Map.of(), YearMonth.of(2026, 9)))
                .isInstanceOfSatisfying(PayrollRefusal.class, refusal -> {
                    assertThat(refusal.code()).isEqualTo(PayrollRefusalCode.TOO_MANY_ROWS);
                    assertThat(refusal.details()).containsEntry("limit", "20000");
                });
    }

    @Test
    @DisplayName("exactly the limit is read")
    void theLimitItself() {
        StringBuilder csv = new StringBuilder("staffNumber,surname,amount\n");
        for (int i = 1; i <= PayrollLimits.MAX_ROWS; i++) {
            csv.append(i).append(",Testerson,174.00\n");
        }
        PayrollParser.Parsed parsed = PayrollParser.parse(GridReader.read(FileSniffer.Kind.CSV,
                csv.toString().getBytes(StandardCharsets.UTF_8)), PayrollLayouts.typed(), Map.of(), YearMonth.of(2026, 9));
        assertThat(parsed.lines()).hasSize(PayrollLimits.MAX_ROWS);
    }

    @Test
    @DisplayName("🔒 a zip bomb -- a part expanding far beyond the file -- is FILE_UNREADABLE, before POI holds it")
    void aZipBomb() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(out)) {
            zip.putNextEntry(new ZipEntry("[Content_Types].xml"));
            byte[] zeros = new byte[1024 * 1024];
            for (int i = 0; i < 170; i++) {
                zip.write(zeros);
            }
            zip.closeEntry();
        }
        byte[] bomb = out.toByteArray();
        assertThat(bomb.length).as("small on the wire").isLessThan(PayrollLimits.MAX_BYTES);
        assertThatThrownBy(() -> GridReader.read(FileSniffer.sniff(bomb), bomb))
                .isInstanceOfSatisfying(PayrollRefusal.class,
                        refusal -> assertThat(refusal.code()).isEqualTo(PayrollRefusalCode.FILE_UNREADABLE));
    }

    @Test
    @DisplayName("a zip that holds no workbook -- a Word document, say -- is FILE_TYPE_NOT_ACCEPTED")
    void aZipThatIsNoWorkbook() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(out)) {
            zip.putNextEntry(new ZipEntry("[Content_Types].xml"));
            zip.write(("<?xml version=\"1.0\"?><Types xmlns=\"http://schemas.openxmlformats.org/package/2006/"
                    + "content-types\"><Default Extension=\"xml\" ContentType=\"application/xml\"/></Types>")
                    .getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        byte[] notAWorkbook = out.toByteArray();
        assertThatThrownBy(() -> GridReader.read(FileSniffer.Kind.XLSX, notAWorkbook))
                .isInstanceOfSatisfying(PayrollRefusal.class, refusal -> assertThat(refusal.code())
                        .isIn(PayrollRefusalCode.FILE_TYPE_NOT_ACCEPTED, PayrollRefusalCode.FILE_UNREADABLE));
    }

    @Test
    @DisplayName("a workbook behind a password is FILE_UNREADABLE, asking for an unprotected copy")
    void aProtectedWorkbook() throws IOException {
        byte[] locked = ProtectedFiles.workbook(workbook(), "a password of eight");
        assertThat(FileSniffer.sniff(locked)).as("an encrypted workbook is an OLE2 container").isEqualTo(FileSniffer.Kind.XLS);
        assertThatThrownBy(() -> GridReader.read(FileSniffer.sniff(locked), locked))
                .isInstanceOfSatisfying(PayrollRefusal.class, refusal -> {
                    assertThat(refusal.code()).isEqualTo(PayrollRefusalCode.FILE_UNREADABLE);
                    assertThat(refusal.getMessage()).contains("password");
                });
    }

    @Test
    @DisplayName("a CSV in Windows-1252 or with a byte-order mark, separated by semicolons, reads the same")
    void csvVariants() {
        byte[] semicolons = "﻿staffNumber;surname;amount\n7;Démoleng;174,00\n".getBytes(StandardCharsets.UTF_8);
        SheetGrid grid = GridReader.read(FileSniffer.Kind.CSV, semicolons);
        assertThat(grid.rows().get(1).cells()).containsExactly("7", "Démoleng", "174,00");
        byte[] latin = "staffNumber,surname,amount\n7,Démoleng,174.00\n".getBytes(java.nio.charset.Charset.forName("windows-1252"));
        assertThat(GridReader.read(FileSniffer.Kind.CSV, latin).rows().get(1).cell(1)).isEqualTo("Démoleng");
    }

    @Test
    @DisplayName("invisible marks and padding are stripped from every cell")
    void cellsAreCleaned() {
        assertThat(GridReader.clean("‭990005‬")).isEqualTo("990005");
        assertThat(GridReader.clean("TRIALISO , PILOTO             ")).isEqualTo("TRIALISO , PILOTO");
        assertThat(GridReader.clean("   ")).isNull();
        assertThat(GridReader.plain(-174.0)).isEqualTo("-174");
        assertThat(GridReader.plain(8106145689086.0)).isEqualTo("8106145689086");
    }

    private static byte[] workbook() throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            workbook.createSheet("Sheet1").createRow(0).createCell(0).setCellValue("Amount");
            workbook.write(out);
            return out.toByteArray();
        }
    }
}
