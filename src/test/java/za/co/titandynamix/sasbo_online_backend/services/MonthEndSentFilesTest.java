package za.co.titandynamix.sasbo_online_backend.services;

import net.lingala.zip4j.exception.ZipException;
import net.lingala.zip4j.io.inputstream.ZipInputStream;
import org.apache.poi.poifs.crypt.Decryptor;
import org.apache.poi.poifs.crypt.EncryptionInfo;
import org.apache.poi.poifs.filesystem.POIFSFileSystem;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import za.co.titandynamix.sasbo_online_backend.acb.AcbTime;
import za.co.titandynamix.sasbo_online_backend.dto.monthend.MonthEndDeliveryDto;
import za.co.titandynamix.sasbo_online_backend.dto.monthend.MonthEndFileDto;
import za.co.titandynamix.sasbo_online_backend.dto.monthend.MonthEndFilesDto;
import za.co.titandynamix.sasbo_online_backend.dto.monthend.MonthEndProducedFileDto;
import za.co.titandynamix.sasbo_online_backend.exception.MonthEndRefusal;
import za.co.titandynamix.sasbo_online_backend.exception.MonthEndRefusalCode;
import za.co.titandynamix.sasbo_online_backend.monthend.MonthEndFileLayouts;
import za.co.titandynamix.sasbo_online_backend.repository.MemberRepository;
import za.co.titandynamix.sasbo_online_backend.security.CallerScope;
import za.co.titandynamix.sasbo_online_backend.support.SendFixture;
import za.co.titandynamix.sasbo_online_backend.utils.DataSeeder;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The files that leave SASBO for the banks and the providers (October build), on H2 from {@link SendFixture}: each
 * opened here with the password its answer gave -- and refused with any other -- holding exactly the members its setting
 * says (MEN-32: payroll deductions only; MEN-33: the full list or the month's new and deleted), in its layout; the
 * password kept nowhere; downloads and SFTP recorded; and nothing of it for a caller who does not see every region.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = {
        "sasbo.workplaces=all",
        "spring.datasource.url=jdbc:h2:mem:month-end-sent-files;MODE=PostgreSQL;DB_CLOSE_DELAY=-1"})
@Import(MonthEndSentFilesTest.AtTheFixturesClock.class)
@DisplayName("The files that leave SASBO")
class MonthEndSentFilesTest {

    @TestConfiguration
    static class AtTheFixturesClock {
        @Bean
        @Primary
        Clock theFixturesClock() {
            return Clock.fixed(SendFixture.NOW, AcbTime.ZONE);
        }
    }

    /** Nothing of the seed's: only the fixture is on this database. */
    @MockitoBean
    private DataSeeder noSeed;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private MemberRepository members;

    @Autowired
    private MonthEndTakes takes;

    @Autowired
    private MonthEndSentFiles files;

    static final String MONTH = SendFixture.TAKEN.toString();

    private Map<String, Integer> ids;

    @BeforeEach
    void thePopulation() {
        SendFixture.clear(jdbc);
        ids = SendFixture.insert(jdbc, members);
        SendFixture.settings(jdbc);
        takes.take(SendFixture.TAKEN, "seed");
    }

    // ------------------------------------------------------------------------------------------------ Absa's layout

    @Test
    @DisplayName("🔒 MEN-34, MEN-05: the full list in Absa's layout, in an AES-256 ZIP opened with its password -- every payroll member on record at the bank's table codes, the suspended included, the deleted and the debit-order members not")
    void absaFullList() throws Exception {
        MonthEndProducedFileDto produced = files.produceBank(MONTH, "INVENTED_BANK");

        MonthEndFileDto file = produced.file();
        assertThat(file.fileName()).isEqualTo("INVENTEDBANKFULL_260831.zip");
        assertThat(file.format()).isEqualTo("ZIP");
        assertThat(file.protection()).isEqualTo("AES_256_ZIP");
        assertThat(file.rows()).isEqualTo(6);
        assertThat(file.leftOut()).isZero();
        String expected = absa("a1") + absa("a2") + absa("a3") + absa("c1") + absa("c2") + absa("c3");
        assertThat(unzip(download(file.id()), produced.password(), "INVENTEDBANKFULL_260831.txt"))
                .isEqualTo(expected);
    }

    @Test
    @DisplayName("🔒 the same ZIP refuses every other password")
    void absaRefusesAnotherPassword() {
        MonthEndProducedFileDto produced = files.produceBank(MONTH, "INVENTED_BANK");
        byte[] zip = download(produced.file().id());

        assertThatThrownBy(() -> unzip(zip, "AAAA-BBBB-CCCC-DDDD", "INVENTEDBANKFULL_260831.txt"))
                .isInstanceOf(ZipException.class);
    }

    // ------------------------------------------------------------------------------------------------ workbooks, CSV

    @Test
    @DisplayName("MEN-33: new and deleted in SASBO's standard layout, a workbook under Excel's password -- a payroll member deleted in the month, and not the new debit-order member")
    void newAndDeletedWorkbook() throws Exception {
        MonthEndProducedFileDto produced = files.produceBank(MONTH, "INVENTED_INSURER");

        MonthEndFileDto file = produced.file();
        assertThat(file.fileName()).isEqualTo("INVENTEDINSURER_260831.xlsx");
        assertThat(file.protection()).isEqualTo("EXCEL_AES_256");
        assertThat(List.of(file.rows(), file.newRows(), file.deletedRows())).containsExactly(1, 0, 1);
        List<List<String>> rows = sheet(download(file.id()), produced.password());
        assertThat(rows.get(0)).containsExactly("INVENTED INSURER NEW AND DELETED MEMBERS - 2026/08/31");
        assertThat(rows.get(1)).containsExactlyElementsOf(MonthEndFileLayouts.NEW_AND_DELETED_COLUMNS);
        assertThat(rows.get(2)).containsExactly("DEL", "MR", "M", SendFixture.surname("b2"), "20000002",
                SendFixture.number("b2"), identityOf("b2"), "INVENTED HARBOUR");
        assertThat(rows).hasSize(3);
    }

    @Test
    @DisplayName("a bank set to CSV: the same rows, as a CSV in an AES-256 ZIP")
    void newAndDeletedCsv() throws Exception {
        jdbc.update("UPDATE month_end_banks SET layout = 'CSV' WHERE code = 'INVENTED_INSURER'");

        MonthEndProducedFileDto produced = files.produceBank(MONTH, "INVENTED_INSURER");

        assertThat(produced.file().fileName()).isEqualTo("INVENTEDINSURER_260831.zip");
        assertThat(unzip(download(produced.file().id()), produced.password(), "INVENTEDINSURER_260831.csv"))
                .isEqualTo(String.join(",", MonthEndFileLayouts.NEW_AND_DELETED_COLUMNS) + "\r\n"
                        + "DEL,MR,M," + SendFixture.surname("b2") + ",20000002," + SendFixture.number("b2") + ","
                        + identityOf("b2") + ",INVENTED HARBOUR\r\n");
    }

    // ------------------------------------------------------------------------------------------------ providers

    @Test
    @DisplayName("MEN-35: Old Mutual's list -- her 24 headings, every member paying at the month's end, wherever they work -- under Excel's password")
    void oldMutual() throws Exception {
        MonthEndProducedFileDto produced = files.produceProvider(MONTH, "OM_FUNERAL");

        assertThat(produced.file().rows()).as("a1, a2, b1, b3, c1, c3 and n1: paying; the suspended and the deleted not")
                .isEqualTo(7);
        List<List<String>> rows = sheet(download(produced.file().id()), produced.password());
        assertThat(rows.get(0)).containsExactlyElementsOf(MonthEndFileLayouts.OLD_MUTUAL_COLUMNS);
        assertThat(rows).hasSize(8);
        assertThat(rows.stream().map(r -> r.size() > 17 ? r.get(17) : "").toList())
                .contains(SendFixture.number("n1"), SendFixture.number("b1"))
                .doesNotContain(SendFixture.number("a3"), SendFixture.number("a4"));
    }

    @Test
    @DisplayName("Law Call's provisional list is a CSV in an AES-256 ZIP; a suspended benefit's list is RECIPIENT_INACTIVE")
    void provisionalAndSuspended() throws Exception {
        MonthEndProducedFileDto produced = files.produceProvider(MONTH, "LAW_CALL");

        String csv = unzip(download(produced.file().id()), produced.password(), "LAWCALL_260831.csv");
        assertThat(csv).startsWith(String.join(",", MonthEndFileLayouts.PROVISIONAL_COLUMNS) + "\r\n");
        assertThat(csv.split("\r\n")).hasSize(8);
        assertThatThrownBy(() -> files.produceProvider(MONTH, "CRITICARE"))
                .satisfies(e -> assertThat(((MonthEndRefusal) e).code()).isEqualTo(MonthEndRefusalCode.RECIPIENT_INACTIVE));
    }

    // ------------------------------------------------------------------------------------------------ the password

    @Test
    @DisplayName("🔒 the password is in the answer alone: in no row of the files or the deliveries, and nowhere in the file's bytes")
    void thePasswordIsKeptNowhere() {
        MonthEndProducedFileDto produced = files.produceBank(MONTH, "INVENTED_BANK");
        files.download(produced.file().id());
        String shown = produced.password();

        assertThat(shown).matches("[A-HJKMNP-Z2-9]{4}(-[A-HJKMNP-Z2-9]{4}){3}");
        assertThat(produced.passwordNote()).contains("shown once").contains("another channel");
        assertThat(produced.toString()).doesNotContain(shown);
        for (String table : List.of("month_end_files", "month_end_deliveries")) {
            for (Map<String, Object> row : jdbc.queryForList("SELECT * FROM " + table)) {
                for (Object value : row.values()) {
                    String text = value instanceof byte[] bytes ? new String(bytes, StandardCharsets.ISO_8859_1)
                            : String.valueOf(value);
                    assertThat(text).as("a column of %s", table).doesNotContain(shown);
                }
            }
        }
        assertThat(files.produceBank(MONTH, "INVENTED_BANK").password()).as("a new file, a new password")
                .isNotEqualTo(shown);
    }

    // ------------------------------------------------------------------------------------------------ the month's

    @Test
    @DisplayName("the month's files: a row a bank and a provider, what a file made now would hold, the files made, and who is in no bank's file")
    void theMonthsFiles() {
        files.produceBank(MONTH, "INVENTED_BANK");

        MonthEndFilesDto month = files.list(MONTH);

        assertThat(month.banks()).extracting(r -> r.bank().code()).containsExactly("INVENTED_BANK", "INVENTED_INSURER");
        assertThat(month.banks().get(0).preview()).isEqualTo(new MonthEndFilesDto.Preview(6, 0, 0));
        assertThat(month.banks().get(1).preview()).isEqualTo(new MonthEndFilesDto.Preview(1, 0, 1));
        assertThat(month.banks().get(0).files()).hasSize(1);
        assertThat(month.providers()).extracting(r -> r.provider().code()).containsExactly("CRITICARE", "LAW_CALL",
                "OM_FUNERAL");
        assertThat(month.providers().get(2).preview().rows()).isEqualTo(7);
        assertThat(month.noTableCode()).as("n1: on payroll, joined in August, with no workplace")
                .isEqualTo(new MonthEndFilesDto.Preview(1, 1, 0));
        assertThat(month.sftp().state()).isEqualTo("NOT_CONFIGURED");
    }

    // ------------------------------------------------------------------------------------------------ handing over

    @Test
    @DisplayName("a download is recorded, and counted on the file")
    void aDownloadIsRecorded() {
        MonthEndFileDto file = files.produceBank(MONTH, "INVENTED_BANK").file();
        download(file.id());

        MonthEndFileDto after = files.list(MONTH).banks().get(0).files().get(0);
        assertThat(after.downloads()).isEqualTo(1);
        assertThat(after.lastDeliveredAt()).isEqualTo(SendFixture.NOW.toString());
    }

    @Test
    @DisplayName("SFTP: refused SFTP_NOT_CONFIGURED where no server is set up -- recorded -- and SFTP_NOT_FOR_THIS_RECIPIENT for a bank's file")
    void sftp() {
        MonthEndFileDto lawCall = files.produceProvider(MONTH, "LAW_CALL").file();
        MonthEndFileDto bank = files.produceBank(MONTH, "INVENTED_BANK").file();

        assertThatThrownBy(() -> files.upload(lawCall.id()))
                .satisfies(e -> assertThat(((MonthEndRefusal) e).code()).isEqualTo(MonthEndRefusalCode.SFTP_NOT_CONFIGURED));
        assertThatThrownBy(() -> files.upload(bank.id()))
                .satisfies(e -> assertThat(((MonthEndRefusal) e).code())
                        .isEqualTo(MonthEndRefusalCode.SFTP_NOT_FOR_THIS_RECIPIENT));
        assertThat(jdbc.queryForList("SELECT channel, outcome, code FROM month_end_deliveries"))
                .singleElement().satisfies(row -> {
                    assertThat(row.get("CHANNEL")).isEqualTo("SFTP");
                    assertThat(row.get("OUTCOME")).isEqualTo("REFUSED");
                    assertThat(row.get("CODE")).isEqualTo("SFTP_NOT_CONFIGURED");
                });
    }

    @Test
    @DisplayName("🔒 these files carry every region's members: a region's user is refused SCOPE_TOO_NARROW, and nothing is made")
    void aRegionsUserIsRefused() {
        CallerScope north = CallerScope.regions(Set.of("NORTH"));
        assertThatThrownBy(() -> SendFixture.as(north, () -> files.list(MONTH)))
                .satisfies(e -> assertThat(((MonthEndRefusal) e).code()).isEqualTo(MonthEndRefusalCode.SCOPE_TOO_NARROW));
        assertThatThrownBy(() -> SendFixture.as(north, () -> files.produceBank(MONTH, "INVENTED_BANK")))
                .satisfies(e -> assertThat(((MonthEndRefusal) e).code()).isEqualTo(MonthEndRefusalCode.SCOPE_TOO_NARROW));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM month_end_files", Long.class)).isZero();
    }

    @Test
    @DisplayName("an unknown bank, provider or file is said so; the open month has no files")
    void unknowns() {
        assertThatThrownBy(() -> files.produceBank(MONTH, "NOWHERE"))
                .satisfies(e -> assertThat(((MonthEndRefusal) e).code()).isEqualTo(MonthEndRefusalCode.BANK_NOT_FOUND));
        assertThatThrownBy(() -> files.produceProvider(MONTH, "NOWHERE"))
                .satisfies(e -> assertThat(((MonthEndRefusal) e).code()).isEqualTo(MonthEndRefusalCode.PROVIDER_NOT_FOUND));
        assertThatThrownBy(() -> files.download(999_999L))
                .satisfies(e -> assertThat(((MonthEndRefusal) e).code()).isEqualTo(MonthEndRefusalCode.FILE_NOT_FOUND));
        assertThatThrownBy(() -> files.produceBank("2026-09", "INVENTED_BANK"))
                .satisfies(e -> assertThat(((MonthEndRefusal) e).code()).isEqualTo(MonthEndRefusalCode.MONTH_NOT_TAKEN));
    }

    // ------------------------------------------------------------------------------------------------ helpers

    /** A member's Absa record and its line ending, as the layout's golden test pins it. */
    private static String absa(String key) {
        return SendFixture.EMPLOYEE.get(key) + MonthEndFileLayouts.field(SendFixture.surname(key), 20) + "M  " + "MR  "
                + "4" + "   " + "\r\n";
    }

    private String identityOf(String key) {
        return jdbc.queryForObject("SELECT identity_number FROM members WHERE id = ?", String.class, ids.get(key));
    }

    private byte[] download(long id) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        files.download(id).body().accept(out);
        return out.toByteArray();
    }

    static String unzip(byte[] zip, String password, String entry) throws Exception {
        try (ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(zip), password.toCharArray())) {
            assertThat(in.getNextEntry().getFileName()).isEqualTo(entry);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    static List<List<String>> sheet(byte[] encrypted, String password) throws Exception {
        try (POIFSFileSystem container = new POIFSFileSystem(new ByteArrayInputStream(encrypted))) {
            Decryptor decryptor = Decryptor.getInstance(new EncryptionInfo(container));
            assertThat(decryptor.verifyPassword(password)).isTrue();
            try (InputStream clear = decryptor.getDataStream(container); XSSFWorkbook book = new XSSFWorkbook(clear)) {
                Sheet sheet = book.getSheetAt(0);
                List<List<String>> rows = new ArrayList<>();
                for (Row row : sheet) {
                    List<String> cells = new ArrayList<>();
                    row.forEach(cell -> cells.add(cell.getStringCellValue().strip()));
                    rows.add(cells);
                }
                return rows;
            }
        }
    }
}
