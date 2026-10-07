package za.co.titandynamix.sasbo_online_backend.monthend;

import net.lingala.zip4j.exception.ZipException;
import net.lingala.zip4j.io.inputstream.ZipInputStream;
import net.lingala.zip4j.model.LocalFileHeader;
import net.lingala.zip4j.model.enums.AesKeyStrength;
import net.lingala.zip4j.model.enums.EncryptionMethod;
import org.apache.poi.poifs.crypt.CipherAlgorithm;
import org.apache.poi.poifs.crypt.Decryptor;
import org.apache.poi.poifs.crypt.EncryptionInfo;
import org.apache.poi.poifs.crypt.EncryptionMode;
import org.apache.poi.poifs.crypt.HashAlgorithm;
import org.apache.poi.poifs.filesystem.POIFSFileSystem;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import za.co.titandynamix.sasbo_online_backend.monthend.MonthEndFileLayouts.SheetFile;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Files that leave SASBO are password-protected (October build; Stephne MEN-05: "a requirement for POPIA"): each opened
 * here with the password it was made with -- and refused with any other -- and none carries a line of its own in the
 * clear. A text file in an AES-256 ZIP; a workbook under Excel's own agile encryption, AES-256 with SHA-512.
 */
@DisplayName("The files' protection")
class MonthEndFileProtectionTest {

    /** A line no protected file may show in the clear. */
    static final String PLANTED = "QXPLANTED-INVENTED-MEMBER";

    @Test
    @DisplayName("a password is sixteen readable capitals and digits in four groups -- no I, L, O, 0 or 1 -- and never repeats")
    void passwords() {
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 2_000; i++) {
            String password = MonthEndFileProtection.newPassword();
            assertThat(password).matches("[A-HJKMNP-Z2-9]{4}(-[A-HJKMNP-Z2-9]{4}){3}");
            assertThat(seen.add(password)).as("a password made twice").isTrue();
        }
    }

    @Test
    @DisplayName("an AES-256 ZIP opens with its password and shows the file as written")
    void zipOpensWithItsPassword() throws IOException {
        String shown = MonthEndFileProtection.newPassword();
        byte[] zip = zipOf(shown, PLANTED + "\r\n");

        try (ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(zip), shown.toCharArray())) {
            LocalFileHeader entry = in.getNextEntry();
            assertThat(entry.getFileName()).isEqualTo("ABSA_260930.txt");
            assertThat(entry.isEncrypted()).isTrue();
            assertThat(entry.getEncryptionMethod()).isEqualTo(EncryptionMethod.AES);
            assertThat(entry.getAesExtraDataRecord().getAesKeyStrength()).isEqualTo(AesKeyStrength.KEY_STRENGTH_256);
            assertThat(new String(in.readAllBytes(), StandardCharsets.US_ASCII)).isEqualTo(PLANTED + "\r\n");
        }
    }

    @Test
    @DisplayName("the same ZIP refuses every other password, and shows nothing of the file in its bytes")
    void zipRefusesAnotherPassword() {
        String shown = MonthEndFileProtection.newPassword();
        byte[] zip = zipOf(shown, PLANTED + "\r\n");

        assertThat(new String(zip, StandardCharsets.ISO_8859_1)).doesNotContain(PLANTED);
        assertThatThrownBy(() -> {
            try (ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(zip),
                    "AAAA-BBBB-CCCC-DDDD".toCharArray())) {
                in.getNextEntry();
                in.readAllBytes();
            }
        }).isInstanceOf(ZipException.class);
    }

    @Test
    @DisplayName("a workbook is encrypted by Excel's agile scheme -- AES-256, SHA-512 -- and opens with its password")
    void workbookOpensWithItsPassword() throws Exception {
        String shown = MonthEndFileProtection.newPassword();
        byte[] encrypted = workbookOf(shown);

        assertThat(new String(encrypted, StandardCharsets.ISO_8859_1)).doesNotContain(PLANTED);
        try (POIFSFileSystem container = new POIFSFileSystem(new ByteArrayInputStream(encrypted))) {
            EncryptionInfo info = new EncryptionInfo(container);
            assertThat(info.getEncryptionMode()).isEqualTo(EncryptionMode.agile);
            assertThat(info.getHeader().getCipherAlgorithm()).isEqualTo(CipherAlgorithm.aes256);
            assertThat(info.getHeader().getHashAlgorithm()).isEqualTo(HashAlgorithm.sha512);
            Decryptor decryptor = Decryptor.getInstance(info);
            assertThat(decryptor.verifyPassword(shown)).isTrue();
            try (InputStream clear = decryptor.getDataStream(container);
                 XSSFWorkbook book = new XSSFWorkbook(clear)) {
                assertThat(book.getSheetAt(0).getRow(1).getCell(0).getStringCellValue()).isEqualTo(PLANTED);
            }
        }
    }

    @Test
    @DisplayName("the same workbook refuses every other password")
    void workbookRefusesAnotherPassword() throws Exception {
        byte[] encrypted = workbookOf(MonthEndFileProtection.newPassword());

        try (POIFSFileSystem container = new POIFSFileSystem(new ByteArrayInputStream(encrypted))) {
            Decryptor decryptor = Decryptor.getInstance(new EncryptionInfo(container));
            assertThat(decryptor.verifyPassword("AAAA-BBBB-CCCC-DDDD")).isFalse();
        }
    }

    static byte[] zipOf(String password, String content) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        MonthEndFileProtection.aesZip(out, password, "ABSA_260930.txt", 1_790_000_000_000L,
                zip -> zip.write(content.getBytes(StandardCharsets.US_ASCII)));
        return out.toByteArray();
    }

    static byte[] workbookOf(String password) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (SheetFile sheet = new SheetFile("Full list", null, List.of("Name"))) {
            sheet.row(List.of(PLANTED));
            MonthEndFileProtection.excelEncrypted(out, password, sheet::writeTo);
        }
        return out.toByteArray();
    }
}
