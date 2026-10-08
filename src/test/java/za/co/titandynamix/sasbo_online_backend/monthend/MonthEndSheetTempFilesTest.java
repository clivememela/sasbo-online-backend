package za.co.titandynamix.sasbo_online_backend.monthend;

import org.apache.poi.util.DefaultTempFileCreationStrategy;
import org.apache.poi.util.TempFile;
import org.apache.poi.util.TempFileCreationStrategy;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import za.co.titandynamix.sasbo_online_backend.monthend.MonthEndFileLayouts.SheetFile;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 🔒 A bank's or a provider's workbook never puts a member on the server's disk in the clear (October build; Stephne
 * MEN-05: "a requirement for POPIA"). A streaming workbook keeps the rows past its window in temporary files while it
 * is written -- a bank's full list carries ID numbers, Old Mutual's list addresses -- and a process stopped mid-write
 * leaves them behind. So they are written encrypted, with a key the process alone holds: read here, while the workbook
 * is still open, every temporary file it made holds no row of it -- not as text, and not once unzipped.
 *
 * <p>@Isolated: POI's temporary-file strategy is the process's own, set and restored here; no other class may make a
 * temporary file while this one records them. The files are made under {@code build/}, never in a system temp folder.
 */
@Isolated
@DisplayName("A file's workbook keeps no member on disk in the clear")
class MonthEndSheetTempFilesTest {

    /** What no temporary file may show: a row's identity number, invented. */
    static final String PLANTED = "QXPLANTED-INVENTED-IDENTITY-";

    /** Rows enough to overflow any window a streaming workbook keeps in memory. */
    static final int ROWS = 2_000;

    private Path folder;
    private Recording recording;

    @BeforeEach
    void recordEveryTemporaryFile() throws IOException {
        folder = Files.createDirectories(Path.of("build", "sheet-temp-files", UUID.randomUUID().toString()));
        recording = new Recording(folder.toFile());
        TempFile.setTempFileCreationStrategy(recording);
    }

    @AfterEach
    void restore() throws IOException {
        TempFile.setTempFileCreationStrategy(new DefaultTempFileCreationStrategy());
        try (Stream<Path> made = Files.walk(folder)) {
            for (Path path : made.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }

    @Test
    @DisplayName("🔒 the rows a workbook keeps on disk while it is written are unreadable -- and the workbook it writes is whole")
    void theRowsOnDiskAreNeverClear() throws IOException {
        byte[] written;
        try (SheetFile sheet = new SheetFile("Full list", MonthEndFileLayouts.fullListTitle(java.time.LocalDate.of(2026,
                9, 30)), MonthEndFileLayouts.FULL_LIST_COLUMNS)) {
            for (int i = 0; i < ROWS; i++) {
                sheet.row(List.of("MR", "I", "INVENTED", "M-" + i, "EMP-" + i, "INVENTED BANK", PLANTED + i, "174.00"));
            }

            // Read while the workbook is open: the rows past its window are on disk now.
            assertThat(recording.made).as("the workbook keeps rows in temporary files -- so the scan below reads them")
                    .isNotEmpty();
            long scanned = 0;
            for (File file : recording.made) {
                if (!file.isFile()) {
                    continue;
                }
                byte[] bytes = Files.readAllBytes(file.toPath());
                scanned += bytes.length;
                assertThat(new String(bytes, StandardCharsets.ISO_8859_1)).as("%s, as it is on disk", file.getName())
                        .doesNotContain(PLANTED);
                assertThat(unzipped(bytes)).as("%s, unzipped", file.getName()).doesNotContain(PLANTED);
            }
            assertThat(scanned).as("the temporary files hold the rows past the window -- not nothing").isPositive();

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            sheet.writeTo(out);
            written = out.toByteArray();
        }

        try (XSSFWorkbook book = new XSSFWorkbook(new ByteArrayInputStream(written))) {
            assertThat(book.getSheetAt(0).getLastRowNum()).as("the title, the headings and every row").isEqualTo(ROWS + 1);
            assertThat(book.getSheetAt(0).getRow(2).getCell(6).getStringCellValue()).isEqualTo(PLANTED + 0);
            assertThat(book.getSheetAt(0).getRow(ROWS + 1).getCell(6).getStringCellValue())
                    .isEqualTo(PLANTED + (ROWS - 1));
        }
    }

    /**
     * The bytes as text once gunzipped -- a compressed temporary file is not a protected one -- as far as they go: a file
     * still being written ends early, and what came before the end is kept. Nothing where the bytes are not gzip at all.
     */
    private static String unzipped(byte[] bytes) throws IOException {
        ByteArrayOutputStream text = new ByteArrayOutputStream();
        try (InputStream in = new GZIPInputStream(new ByteArrayInputStream(bytes))) {
            byte[] buffer = new byte[8192];
            for (int n = in.read(buffer); n > 0; n = in.read(buffer)) {
                text.write(buffer, 0, n);
            }
        } catch (ZipException | EOFException endedOrNotGzip) {
            // Not gzip (nothing read), or cut short where the writer has not finished (what was read is kept).
        }
        return text.toString(StandardCharsets.ISO_8859_1);
    }

    /** POI's own strategy, in a folder of the test's, noting every file it makes. */
    static final class Recording implements TempFileCreationStrategy {

        final List<File> made = new CopyOnWriteArrayList<>();
        private final DefaultTempFileCreationStrategy inner;

        Recording(File folder) {
            this.inner = new DefaultTempFileCreationStrategy(folder);
        }

        @Override
        public File createTempFile(String prefix, String suffix) throws IOException {
            File file = inner.createTempFile(prefix, suffix);
            made.add(file);
            return file;
        }

        @Override
        public File createTempDirectory(String prefix) throws IOException {
            return inner.createTempDirectory(prefix);
        }
    }
}
