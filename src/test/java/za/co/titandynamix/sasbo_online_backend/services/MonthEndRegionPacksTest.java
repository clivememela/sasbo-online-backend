package za.co.titandynamix.sasbo_online_backend.services;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.ss.usermodel.Cell;
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
import org.springframework.mail.MailSendException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import za.co.titandynamix.sasbo_online_backend.acb.AcbTime;
import za.co.titandynamix.sasbo_online_backend.dto.monthend.MonthEndBulkEmailDto;
import za.co.titandynamix.sasbo_online_backend.dto.monthend.MonthEndDeliveriesPageDto;
import za.co.titandynamix.sasbo_online_backend.dto.monthend.MonthEndDeliveryDto;
import za.co.titandynamix.sasbo_online_backend.dto.monthend.MonthEndRegionPackDto;
import za.co.titandynamix.sasbo_online_backend.dto.monthend.MonthEndRegionPacksDto;
import za.co.titandynamix.sasbo_online_backend.exception.MonthEndRefusal;
import za.co.titandynamix.sasbo_online_backend.exception.MonthEndRefusalCode;
import za.co.titandynamix.sasbo_online_backend.monthend.MonthEndMailer;
import za.co.titandynamix.sasbo_online_backend.repository.MemberRepository;
import za.co.titandynamix.sasbo_online_backend.security.CallerScope;
import za.co.titandynamix.sasbo_online_backend.support.SendFixture;
import za.co.titandynamix.sasbo_online_backend.utils.DataSeeder;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The regions' packs (October build), on H2 from {@link SendFixture}: each region's pack holds ITS OWN MEMBERS AND NO
 * OTHER REGION'S (Stephne MEN-08) -- in the listings, the PDF and the workbook; a pack is made once a take and a set;
 * downloading and e-mailing are recorded, sent or not; an e-mail is refused, cleanly and on the record, where mail is off
 * or the recipient has no address, and said where the mail server refuses it; a region's user sees their region alone.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = {
        "sasbo.workplaces=all",
        "spring.datasource.url=jdbc:h2:mem:month-end-region-packs;MODE=PostgreSQL;DB_CLOSE_DELAY=-1"})
@Import(MonthEndRegionPacksTest.AtTheFixturesClock.class)
@DisplayName("The regions' packs")
class MonthEndRegionPacksTest {

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

    /** Mail is off unless a test turns it on. */
    @MockitoBean
    private MonthEndMailer mailer;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private MemberRepository members;

    @Autowired
    private MonthEndTakes takes;

    @Autowired
    private MonthEndRegionPacks packs;

    static final String MONTH = SendFixture.TAKEN.toString();

    @BeforeEach
    void thePopulation() {
        SendFixture.clear(jdbc);
        SendFixture.insert(jdbc, members);
        SendFixture.settings(jdbc);
        takes.take(SendFixture.TAKEN, "seed");
        reset(mailer);
        when(mailer.configured()).thenReturn(false);
    }

    // ------------------------------------------------------------------------------------------------ MEN-08

    @Test
    @DisplayName("🔒 MEN-08: area A's pack names area A's joined, deleted and suspended -- and nobody of area B, of the south, or with no workplace")
    void aRegionsPackHoldsItsOwnMembersOnly() throws Exception {
        MonthEndRegionPackDto north1 = packs.produce(MONTH, "NORTH1", false).pack();

        assertThat(north1.counts()).isEqualTo(new MonthEndRegionPackDto.Counts(1, 1, 1));
        String pdf = text(pdfOf(north1.id()));
        String xlsx = cells(xlsxOf(north1.id()));
        for (String own : List.of("a1", "a3", "a4")) {
            assertThat(pdf).contains(SendFixture.number(own));
            assertThat(xlsx).contains(SendFixture.number(own));
        }
        for (String other : List.of("b1", "b2", "b3", "c1", "c2", "c3", "n1")) {
            assertThat(pdf).as("another region's member in area A's PDF").doesNotContain(SendFixture.number(other));
            assertThat(xlsx).as("another region's member in area A's workbook").doesNotContain(SendFixture.number(other));
        }
    }

    @Test
    @DisplayName("🔒 MEN-08: the south's pack is the whole region -- its own joined and suspended, a returned debit read as code 13 -- and none of the north")
    void aWholeRegionsPack() throws Exception {
        MonthEndRegionPackDto south = packs.produce(MONTH, "SOUTH", false).pack();

        assertThat(south.counts()).isEqualTo(new MonthEndRegionPackDto.Counts(1, 0, 1));
        assertThat(south.areaLetters()).isEmpty();
        String pdf = text(pdfOf(south.id()));
        assertThat(pdf).contains(SendFixture.number("c1")).contains(SendFixture.number("c2")).contains("13 DEBIT RETURNED");
        for (String other : List.of("a1", "a3", "a4", "b1", "b2", "n1")) {
            assertThat(pdf).doesNotContain(SendFixture.number(other));
        }
    }

    @Test
    @DisplayName("must-pass: the pack's PDF and workbook say what they are -- the month, the set, the figures and SASBO's statistics")
    void whatAPackSays() throws Exception {
        MonthEndRegionPackDto north2 = packs.produce(MONTH, "NORTH2", false).pack();
        String pdf = text(pdfOf(north2.id()));

        assertThat(pdf).contains("August 2026").contains("Invented North (second set)").contains("Region NORTH, areas B")
                .contains("The month in figures").contains("Members deleted").contains("1 Left service")
                .contains("The statistics").contains("Page 1 of");
        assertThat(north2.files()).extracting(MonthEndRegionPackDto.PackFile::format).containsExactly("PDF", "XLSX");
        assertThat(north2.files().get(0).name()).isEqualTo("sasbo-month-end-2026-08-NORTH2.pdf");
        try (XSSFWorkbook book = new XSSFWorkbook(new ByteArrayInputStream(xlsxOf(north2.id())))) {
            assertThat(book.getSheetName(0)).isEqualTo("Summary");
            assertThat(book.getSheet("Deleted members")).isNotNull();
            assertThat(book.getNumberOfSheets()).isGreaterThan(10);
        }
    }

    // ------------------------------------------------------------------------------------------------ once

    @Test
    @DisplayName("a pack is made once a take and a set: asked again it is the same pack; asked again on purpose, a new one")
    void onceATakeAndASet() {
        MonthEndRegionPacks.Produced first = packs.produce(MONTH, "NORTH1", false);
        MonthEndRegionPacks.Produced second = packs.produce(MONTH, "NORTH1", false);
        MonthEndRegionPacks.Produced again = packs.produce(MONTH, "NORTH1", true);

        assertThat(first.created()).isTrue();
        assertThat(second.created()).isFalse();
        assertThat(second.pack().id()).isEqualTo(first.pack().id());
        assertThat(again.created()).isTrue();
        assertThat(again.pack().id()).isNotEqualTo(first.pack().id());
        assertThat(jdbc.queryForList("SELECT generation FROM month_end_region_packs ORDER BY id", Integer.class))
                .containsExactly(1, 2);
    }

    @Test
    @DisplayName("a pack is made only from a month taken: the open month is MONTH_NOT_TAKEN; a switched-off recipient is RECIPIENT_INACTIVE")
    void onlyFromATakenMonth() {
        assertThatThrownBy(() -> packs.produce("2026-09", "NORTH1", false))
                .satisfies(e -> assertThat(((MonthEndRefusal) e).code()).isEqualTo(MonthEndRefusalCode.MONTH_NOT_TAKEN));
        jdbc.update("UPDATE month_end_pack_sets SET active = FALSE WHERE code = 'NORTH2'");
        assertThatThrownBy(() -> packs.produce(MONTH, "NORTH2", false))
                .satisfies(e -> assertThat(((MonthEndRefusal) e).code()).isEqualTo(MonthEndRefusalCode.RECIPIENT_INACTIVE));
        assertThatThrownBy(() -> packs.produce(MONTH, "NOWHERE", false))
                .satisfies(e -> assertThat(((MonthEndRefusal) e).code()).isEqualTo(MonthEndRefusalCode.PACK_SET_NOT_FOUND));
    }

    // ------------------------------------------------------------------------------------------------ the month's

    @Test
    @DisplayName("the month's packs: a row an active recipient, its pack and deliveries; the unplaced counted; an area no recipient covers named")
    void theMonthsPacks() {
        packs.produce(MONTH, "NORTH1", false);
        jdbc.update("UPDATE month_end_pack_sets SET active = FALSE WHERE code = 'NORTH2'");

        MonthEndRegionPacksDto month = packs.list(MONTH);

        assertThat(month.sets()).extracting(r -> r.set().code()).containsExactly("NORTH1", "SOUTH");
        assertThat(month.sets().get(0).pack()).isNotNull();
        assertThat(month.sets().get(1).pack()).isNull();
        assertThat(month.unplaced()).as("n1, with no workplace").isEqualTo(1);
        assertThat(month.uncovered().areas()).containsExactly("B");
        assertThat(month.uncovered().members()).as("b1 and b3 on record at August's end; b2 deleted").isEqualTo(2);
        assertThat(month.available()).isTrue();
        assertThat(month.mail().configured()).isFalse();
        assertThat(month.take().takeNumber()).isEqualTo(1);
    }

    // ------------------------------------------------------------------------------------------------ handing over

    @Test
    @DisplayName("a download hands over the file as stored, and is recorded: DOWNLOAD, SENT, by whom and when")
    void aDownloadIsRecorded() {
        MonthEndRegionPackDto pack = packs.produce(MONTH, "NORTH1", false).pack();

        byte[] pdf = pdfOf(pack.id());

        assertThat(pdf).startsWith('%', 'P', 'D', 'F');
        assertThat(MonthEndRegionPacks.sha256(pdf)).isEqualTo(pack.files().get(0).sha256());
        MonthEndDeliveriesPageDto record = packs.deliveries(MONTH, 0, 10);
        assertThat(record.total()).isEqualTo(1);
        MonthEndDeliveryDto delivery = record.items().get(0);
        assertThat(delivery.channel()).isEqualTo("DOWNLOAD");
        assertThat(delivery.outcome()).isEqualTo("SENT");
        assertThat(delivery.formats()).containsExactly("PDF");
        assertThat(delivery.by()).isEqualTo("system");
        assertThat(delivery.at()).isEqualTo(SendFixture.NOW.toString());
        assertThat(delivery.address()).isNull();
    }

    @Test
    @DisplayName("🔒 mail off: an e-mail is refused MAIL_NOT_CONFIGURED, recorded REFUSED -- and downloading still works")
    void mailOff() throws Exception {
        MonthEndRegionPackDto pack = packs.produce(MONTH, "NORTH1", false).pack();

        assertThatThrownBy(() -> packs.email(pack.id(), List.of("PDF", "XLSX")))
                .satisfies(e -> assertThat(((MonthEndRefusal) e).code()).isEqualTo(MonthEndRefusalCode.MAIL_NOT_CONFIGURED));
        verify(mailer, never()).send(anyString(), anyString(), anyString(), anyList());
        MonthEndDeliveryDto refused = packs.deliveries(MONTH, 0, 10).items().get(0);
        assertThat(refused.channel()).isEqualTo("EMAIL");
        assertThat(refused.outcome()).isEqualTo("REFUSED");
        assertThat(refused.code()).isEqualTo("MAIL_NOT_CONFIGURED");
        assertThat(pdfOf(pack.id())).startsWith('%', 'P', 'D', 'F');
    }

    @Test
    @DisplayName("mail on: the pack is e-mailed to the recipient's address in the formats chosen, and recorded SENT with the address")
    void mailOn() throws Exception {
        when(mailer.configured()).thenReturn(true);
        MonthEndRegionPackDto pack = packs.produce(MONTH, "NORTH1", false).pack();

        MonthEndDeliveryDto sent = packs.email(pack.id(), List.of("pdf"));

        verify(mailer).send(eq("regional.secretary.north1@example.org"), any(), any(),
                org.mockito.ArgumentMatchers.argThat(attachments -> attachments.size() == 1
                        && attachments.get(0).name().equals("sasbo-month-end-2026-08-NORTH1.pdf")));
        assertThat(sent.outcome()).isEqualTo("SENT");
        assertThat(sent.address()).isEqualTo("regional.secretary.north1@example.org");
        assertThat(sent.formats()).containsExactly("PDF");
    }

    @Test
    @DisplayName("a recipient with no address is refused RECIPIENT_HAS_NO_ADDRESS; a mail server that refuses is MAIL_FAILED -- each recorded")
    void noAddressAndAFailingServer() throws Exception {
        when(mailer.configured()).thenReturn(true);
        MonthEndRegionPackDto south = packs.produce(MONTH, "SOUTH", false).pack();
        MonthEndRegionPackDto north1 = packs.produce(MONTH, "NORTH1", false).pack();
        doThrow(new MailSendException("the invented server refused")).when(mailer).send(anyString(), anyString(),
                anyString(), anyList());

        assertThatThrownBy(() -> packs.email(south.id(), List.of("PDF")))
                .satisfies(e -> assertThat(((MonthEndRefusal) e).code())
                        .isEqualTo(MonthEndRefusalCode.RECIPIENT_HAS_NO_ADDRESS));
        assertThatThrownBy(() -> packs.email(north1.id(), List.of("XLSX")))
                .satisfies(e -> assertThat(((MonthEndRefusal) e).code()).isEqualTo(MonthEndRefusalCode.MAIL_FAILED));
        assertThat(packs.deliveries(MONTH, 0, 10).items()).extracting(MonthEndDeliveryDto::outcome,
                MonthEndDeliveryDto::code).containsExactly(org.assertj.core.groups.Tuple.tuple("FAILED", "MAIL_FAILED"),
                org.assertj.core.groups.Tuple.tuple("REFUSED", "RECIPIENT_HAS_NO_ADDRESS"));
    }

    @Test
    @DisplayName("the month's packs e-mailed: a result a recipient -- never a failure of the whole; one not made is PACK_NOT_PRODUCED")
    void emailingTheMonth() {
        packs.produce(MONTH, "NORTH1", false);
        packs.produce(MONTH, "SOUTH", false);

        MonthEndBulkEmailDto results = packs.emailAll(MONTH, null, List.of("PDF"));

        assertThat(results.results()).extracting(MonthEndBulkEmailDto.Result::setCode, MonthEndBulkEmailDto.Result::code)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("NORTH1", "MAIL_NOT_CONFIGURED"),
                        org.assertj.core.groups.Tuple.tuple("NORTH2", "PACK_NOT_PRODUCED"),
                        org.assertj.core.groups.Tuple.tuple("SOUTH", "MAIL_NOT_CONFIGURED"));
        assertThat(results.results().get(0).delivery()).isNotNull();
        assertThat(results.results().get(1).delivery()).isNull();
    }

    @Test
    @DisplayName("the month's sending with not one pack made is refused whole, PACK_NOT_PRODUCED -- nothing recorded")
    void sendingWithNoPackMade() {
        assertThatThrownBy(() -> packs.emailAll(MONTH, List.of("NORTH1", "SOUTH"), List.of("PDF")))
                .satisfies(e -> assertThat(((MonthEndRefusal) e).code()).isEqualTo(MonthEndRefusalCode.PACK_NOT_PRODUCED));
        assertThat(packs.deliveries(MONTH, 0, 10).total()).isZero();
    }

    @Test
    @DisplayName("what cannot be read is REQUEST_MALFORMED: no format, an unknown one, a page past its ceiling")
    void malformed() {
        MonthEndRegionPackDto pack = packs.produce(MONTH, "NORTH1", false).pack();
        for (List<String> formats : new ArrayList<List<String>>(List.of(List.of(), List.of("WORD"),
                List.of("PDF", "XLSX", "PDF")))) {
            assertThatThrownBy(() -> packs.email(pack.id(), formats))
                    .satisfies(e -> assertThat(((MonthEndRefusal) e).code()).isEqualTo(MonthEndRefusalCode.REQUEST_MALFORMED));
        }
        assertThatThrownBy(() -> packs.deliveries(MONTH, 0, 101))
                .satisfies(e -> assertThat(((MonthEndRefusal) e).code()).isEqualTo(MonthEndRefusalCode.REQUEST_MALFORMED));
        assertThatThrownBy(() -> packs.download(pack.id(), "docx"))
                .satisfies(e -> assertThat(((MonthEndRefusal) e).code()).isEqualTo(MonthEndRefusalCode.REGION_PACK_NOT_FOUND));
    }

    // ------------------------------------------------------------------------------------------------ scope

    @Test
    @DisplayName("🔒 a north user sees the north's recipients and packs alone: the south's pack is not there, nor its deliveries")
    void aRegionsUserSeesTheirRegion() {
        MonthEndRegionPackDto south = packs.produce(MONTH, "SOUTH", false).pack();
        packs.download(south.id(), "pdf");
        CallerScope north = CallerScope.regions(Set.of("NORTH"));

        MonthEndRegionPacksDto month = SendFixture.as(north, () -> packs.list(MONTH));
        assertThat(month.sets()).extracting(r -> r.set().code()).containsExactly("NORTH1", "NORTH2");
        assertThat(month.unplaced()).as("the unplaced are head office's to see").isZero();
        assertThatThrownBy(() -> SendFixture.as(north, () -> packs.download(south.id(), "pdf")))
                .satisfies(e -> assertThat(((MonthEndRefusal) e).code()).isEqualTo(MonthEndRefusalCode.REGION_PACK_NOT_FOUND));
        assertThatThrownBy(() -> SendFixture.as(north, () -> packs.produce(MONTH, "SOUTH", false)))
                .satisfies(e -> assertThat(((MonthEndRefusal) e).code()).isEqualTo(MonthEndRefusalCode.PACK_SET_NOT_FOUND));
        assertThat(SendFixture.as(north, () -> packs.deliveries(MONTH, 0, 10)).total()).isZero();
        assertThat(packs.deliveries(MONTH, 0, 10).total()).as("head office sees it").isEqualTo(1);
    }

    @Test
    @DisplayName("an area's user sees a recipient wholly within their areas, never a whole region's")
    void anAreasUser() {
        MonthEndRegionPacksDto month = SendFixture.as(CallerScope.areas(Set.of("A")), () -> packs.list(MONTH));
        assertThat(month.sets()).extracting(r -> r.set().code()).containsExactly("NORTH1");
    }

    // ------------------------------------------------------------------------------------------------ helpers

    private byte[] pdfOf(long id) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        packs.download(id, "pdf").body().accept(out);
        return out.toByteArray();
    }

    private byte[] xlsxOf(long id) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        packs.download(id, "xlsx").body().accept(out);
        return out.toByteArray();
    }

    static String text(byte[] pdf) throws Exception {
        try (PDDocument document = Loader.loadPDF(pdf)) {
            return new PDFTextStripper().getText(document);
        }
    }

    static String cells(byte[] xlsx) throws Exception {
        StringBuilder all = new StringBuilder();
        try (XSSFWorkbook book = new XSSFWorkbook(new ByteArrayInputStream(xlsx))) {
            for (Sheet sheet : book) {
                for (Row row : sheet) {
                    for (Cell cell : row) {
                        all.append(cell.toString()).append('|');
                    }
                    all.append('\n');
                }
            }
        }
        return all.toString();
    }
}
