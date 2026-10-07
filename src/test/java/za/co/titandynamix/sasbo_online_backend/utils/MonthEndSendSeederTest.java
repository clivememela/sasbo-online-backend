package za.co.titandynamix.sasbo_online_backend.utils;

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
import org.springframework.transaction.PlatformTransactionManager;
import za.co.titandynamix.sasbo_online_backend.acb.AcbTime;
import za.co.titandynamix.sasbo_online_backend.config.SasboEnvironment;
import za.co.titandynamix.sasbo_online_backend.config.WorkplaceSwitch;
import za.co.titandynamix.sasbo_online_backend.entity.member.Member;
import za.co.titandynamix.sasbo_online_backend.fixtures.MemberFixture;
import za.co.titandynamix.sasbo_online_backend.repository.MemberRepository;
import za.co.titandynamix.sasbo_online_backend.repository.MonthEndSendSettingsRepository;
import za.co.titandynamix.sasbo_online_backend.repository.MonthEndSentRepository;
import za.co.titandynamix.sasbo_online_backend.services.MonthEndSendSeeding;
import za.co.titandynamix.sasbo_online_backend.services.MonthEndTakes;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * The demonstration's files that leave SASBO (October build), on H2 over SASBO's own lists: the seven pack sets of her
 * region folders, the banks' files with MEN-33's lists, the providers of MEN-35 -- then, for the newest month taken, every
 * recipient's pack made, downloaded and e-mailed (one refused for want of mail), and the banks' and providers' files made
 * -- and a second start writes nothing. Never where the production marker is set; no pack below the switch's {@code all}.
 * The clock is the deck's: Wednesday 14 October 2026, September taken, October open.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = {
        "sasbo.workplaces=all",
        "spring.datasource.url=jdbc:h2:mem:month-end-send-seed;MODE=PostgreSQL;DB_CLOSE_DELAY=-1"})
@Import(MonthEndSendSeederTest.AtTheDecksClock.class)
@DisplayName("The demonstration's files that leave SASBO")
class MonthEndSendSeederTest {

    static final Instant NOW = Instant.parse("2026-10-14T07:30:00Z");

    @TestConfiguration
    static class AtTheDecksClock {
        @Bean
        @Primary
        Clock theDecksClock() {
            return Clock.fixed(NOW, AcbTime.ZONE);
        }
    }

    /** The start's seeding stood down: this class calls the lists and its own seeder itself. */
    @MockitoBean
    private DataSeeder noSeed;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private MemberRepository members;

    @Autowired
    private ReferenceListSeeder lists;

    @Autowired
    private MonthEndTakes takes;

    @Autowired
    private MonthEndSendSeeder seeder;

    @Autowired
    private MonthEndSendSettingsRepository settings;

    @Autowired
    private MonthEndSentRepository sent;

    @Autowired
    private MonthEndSendSeeding seeding;

    @Autowired
    private za.co.titandynamix.sasbo_online_backend.services.MonthEndRegionPacks regionPacks;

    @Autowired
    private za.co.titandynamix.sasbo_online_backend.services.MonthEndSentFiles sentFiles;

    @Autowired
    private PlatformTransactionManager transactions;

    @Autowired
    private Clock clock;

    @BeforeEach
    void sasbosListsAndSeptemberTaken() {
        for (String table : List.of("month_end_deliveries", "month_end_region_packs", "month_end_files",
                "month_end_bank_table_codes", "month_end_banks", "month_end_pack_sets", "month_end_providers",
                "month_end_counts", "month_end_workplaces", "month_end_months", "member_status_changes", "members",
                "workplaces")) {
            jdbc.update("DELETE FROM " + table);
        }
        lists.seedOrRecord();
        workplace(991001, "INVENTED TEST BRANCH", 24, "A");
        workplace(991002, "INVENTED TEST OFFICE", 13, "L");
        member("M-091001", "91000001", 991001);
        member("M-091002", "91000002", 991001);
        member("M-091003", "91000003", 991002);
        takes.take(YearMonth.of(2026, 9), DemonstrationMemberSeeder.SEED_ACTOR);
    }

    @Test
    @DisplayName("MEN-02, MEN-33, MEN-35: seven pack sets -- Johannesburg twice, provisionally split -- the banks' files and the providers, by the seed")
    void whoGetsWhat() {
        seeder.seed();

        assertThat(settings.packSets()).extracting(MonthEndSendSettingsRepository.PackSet::code)
                .containsExactly("BLM", "CPT", "DBN", "JHB", "JHB2", "PE", "PTA");
        assertThat(settings.packSet("JHB").orElseThrow().areaLetters()).containsExactly("A", "F", "K", "X");
        assertThat(settings.packSet("JHB2").orElseThrow().areaLetters()).containsExactly("H", "N", "P");
        assertThat(settings.packSet("DBN").orElseThrow().email()).isEqualTo("regional.secretary.dbn@example.org");
        assertThat(settings.packSets()).allSatisfy(s -> {
            assertThat(s.position()).isEqualTo("Regional Secretary");
            assertThat(s.updatedBy()).isEqualTo("seed");
        });
        MonthEndSendSettingsRepository.Bank absa = settings.bank("ABSA").orElseThrow();
        assertThat(absa.tableCodes()).containsExactly(24, 78, 90);
        assertThat(absa.fullList()).isTrue();
        assertThat(absa.layout()).isEqualTo("ABSA_FIXED_WIDTH");
        assertThat(settings.bank("NEDBANK").orElseThrow().fullList()).as("MEN-33 leaves Nedbank out of the full list")
                .isFalse();
        assertThat(settings.banks()).filteredOn(MonthEndSendSettingsRepository.Bank::fullList)
                .extracting(MonthEndSendSettingsRepository.Bank::code)
                .containsExactlyInAnyOrder("ABSA", "ACCESS", "NATIONAL_HOUSING", "SBV", "STANDARD_BANK");
        assertThat(settings.providers()).extracting(MonthEndSendSettingsRepository.Provider::code,
                MonthEndSendSettingsRepository.Provider::active).containsExactly(
                org.assertj.core.groups.Tuple.tuple("CRITICARE", false), org.assertj.core.groups.Tuple.tuple("HOLIDAY", false),
                org.assertj.core.groups.Tuple.tuple("LAW_CALL", true), org.assertj.core.groups.Tuple.tuple("OM_FUNERAL", true));
    }

    @Test
    @DisplayName("no zeros: September's packs made for every recipient, downloaded, e-mailed and one refused; the banks' and providers' files made")
    void septembersPacksAndFiles() {
        int wrote = seeder.seed();

        long take = takes.taken(YearMonth.of(2026, 9)).orElseThrow().id();
        assertThat(sent.regionPacksOf(take)).hasSize(7);
        assertThat(jdbc.queryForObject("SELECT joined FROM month_end_region_packs WHERE set_code = 'JHB'", Integer.class))
                .as("the two invented members of area A").isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM month_end_deliveries WHERE channel = 'DOWNLOAD' "
                + "AND kind = 'REGION_PACK'", Long.class)).isEqualTo(14);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM month_end_deliveries WHERE channel = 'EMAIL' "
                + "AND outcome = 'SENT'", Long.class)).isEqualTo(6);
        assertThat(jdbc.queryForObject("SELECT code FROM month_end_deliveries WHERE channel = 'EMAIL' "
                + "AND outcome = 'REFUSED'", String.class)).isEqualTo("MAIL_NOT_CONFIGURED");
        assertThat(sent.filesOf(take)).extracting(MonthEndSentRepository.SentFile::recipientCode)
                .containsExactlyInAnyOrder("ABSA", "CAPITEC", "NEDBANK", "SBV", "STANDARD_BANK", "LAW_CALL",
                        "OM_FUNERAL");
        assertThat(sent.filesOf(take)).filteredOn(f -> f.recipientCode().equals("ABSA")).singleElement()
                .satisfies(f -> assertThat(f.rows()).isEqualTo(2));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM month_end_deliveries WHERE channel = 'SFTP' "
                + "AND code = 'SFTP_NOT_CONFIGURED'", Long.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM month_end_deliveries WHERE delivered_by <> 'seed'",
                Long.class)).as("the record says it was the seed").isZero();
        assertThat(wrote).isPositive();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM month_end_region_packs WHERE month_start "
                + ">= DATE '2026-10-01'", Long.class)).as("nothing for October, the open month").isZero();
        java.time.OffsetDateTime clockNow = java.time.OffsetDateTime.ofInstant(NOW, java.time.ZoneOffset.UTC);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM month_end_region_packs WHERE produced_at > ?", Long.class,
                clockNow)).as("a pack dated after the clock").isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM month_end_deliveries WHERE delivered_at > ?", Long.class,
                clockNow)).as("a delivery dated after the clock").isZero();
    }

    @Test
    @DisplayName("the deck's world: a month taken at 07:10 on its last working day has its packs made at 07:30 and e-mailed at 07:40 -- and a month taken now, now")
    void datedByTheTake() {
        Instant september = Instant.parse("2026-09-30T05:10:00Z");

        assertThat(seeding.after(september, java.time.Duration.ofMinutes(20))).isEqualTo(Instant.parse("2026-09-30T05:30:00Z"));
        assertThat(seeding.after(september, java.time.Duration.ofMinutes(30))).isEqualTo(Instant.parse("2026-09-30T05:40:00Z"));
        assertThat(seeding.after(NOW, java.time.Duration.ofMinutes(20))).as("never after the clock").isEqualTo(NOW);
    }

    @Test
    @DisplayName("a second start writes nothing: every recipient, bank and provider by its code, the packs and files by their take")
    void aSecondStartWritesNothing() {
        seeder.seed();
        long packs = jdbc.queryForObject("SELECT count(*) FROM month_end_region_packs", Long.class);
        long files = jdbc.queryForObject("SELECT count(*) FROM month_end_files", Long.class);

        assertThat(seeder.seed()).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM month_end_region_packs", Long.class)).isEqualTo(packs);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM month_end_files", Long.class)).isEqualTo(files);
    }

    @Test
    @DisplayName("below the switch's all, the settings and no pack: nothing is placed in a region there")
    void belowAll() {
        MonthEndSendSeeder unswitched = new MonthEndSendSeeder(new SasboEnvironment(""), new WorkplaceSwitch(""),
                settings, sent, takes, seeding, transactions, clock);

        unswitched.seed();

        assertThat(settings.packSets()).hasSize(7);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM month_end_region_packs", Long.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM month_end_files", Long.class)).isZero();
    }

    @Test
    @DisplayName("🔒 where the production marker is set, nothing at all -- not a recipient, not an address")
    void neverInProduction() {
        MonthEndSendSettingsRepository untouched = mock(MonthEndSendSettingsRepository.class);
        MonthEndSentRepository alsoUntouched = mock(MonthEndSentRepository.class);
        MonthEndSendSeeder production = new MonthEndSendSeeder(new SasboEnvironment("production"),
                new WorkplaceSwitch("all"), untouched, alsoUntouched, takes, seeding, transactions, clock);

        assertThat(production.seed()).isZero();
        verifyNoInteractions(untouched, alsoUntouched);
    }

    // ------------------------------------------------------------------------------------------------ the deck

    /** Where the deck's sample files are written, when this is set -- on a developer's machine, never in CI. */
    static final String SAMPLES_DIR = "MONTH_END_SAMPLES_DIR";

    @Test
    @DisplayName("the deck's samples: Johannesburg's first set's pack, Absa's file and Nedbank's workbook -- invented members only, each file opened and checked")
    void theDecksSamples() throws Exception {
        inventJohannesburg();
        long first = takes.taken(YearMonth.of(2026, 9)).orElseThrow().id();
        takes.take(YearMonth.of(2026, 9), DemonstrationMemberSeeder.SEED_ACTOR, first);
        seeder.seed();
        long take = takes.taken(YearMonth.of(2026, 9)).orElseThrow().id();

        MonthEndSentRepository.RegionPack jhb = sent.newestRegionPack(take, "JHB").orElseThrow();
        java.io.ByteArrayOutputStream pdf = new java.io.ByteArrayOutputStream();
        regionPacks.download(jhb.id(), "pdf").body().accept(pdf);
        java.io.ByteArrayOutputStream xlsx = new java.io.ByteArrayOutputStream();
        regionPacks.download(jhb.id(), "xlsx").body().accept(xlsx);
        za.co.titandynamix.sasbo_online_backend.dto.monthend.MonthEndProducedFileDto absa =
                sentFiles.produceBank("2026-09", "ABSA");
        java.io.ByteArrayOutputStream absaZip = new java.io.ByteArrayOutputStream();
        sentFiles.download(absa.file().id()).body().accept(absaZip);
        za.co.titandynamix.sasbo_online_backend.dto.monthend.MonthEndProducedFileDto nedbank =
                sentFiles.produceBank("2026-09", "NEDBANK");
        java.io.ByteArrayOutputStream nedbankXlsx = new java.io.ByteArrayOutputStream();
        sentFiles.download(nedbank.file().id()).body().accept(nedbankXlsx);

        String text;
        try (org.apache.pdfbox.pdmodel.PDDocument document = org.apache.pdfbox.Loader.loadPDF(pdf.toByteArray())) {
            text = new org.apache.pdfbox.text.PDFTextStripper().getText(document);
        }
        assertThat(text).contains("Johannesburg (first set)").contains("M-092001").doesNotContain("M-091003");
        String absaText;
        try (net.lingala.zip4j.io.inputstream.ZipInputStream in = new net.lingala.zip4j.io.inputstream.ZipInputStream(
                new java.io.ByteArrayInputStream(absaZip.toByteArray()), absa.password().toCharArray())) {
            in.getNextEntry();
            absaText = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.US_ASCII);
        }
        assertThat(absaText.split("\r\n")).allSatisfy(record -> {
            assertThat(record).hasSize(39);
            assertThat(record.charAt(35)).isEqualTo('4');
        });
        assertThat(absaText).contains("SPECIMEN");
        assertThat(nedbank.file().protection()).isEqualTo("EXCEL_AES_256");

        String dir = System.getenv(SAMPLES_DIR);
        if (dir != null && !dir.isBlank()) {
            java.nio.file.Path out = java.nio.file.Paths.get(dir);
            java.nio.file.Files.createDirectories(out);
            java.nio.file.Files.write(out.resolve(jhb.pdfName()), pdf.toByteArray());
            java.nio.file.Files.write(out.resolve(jhb.xlsxName()), xlsx.toByteArray());
            java.nio.file.Files.write(out.resolve(absa.file().fileName()), absaZip.toByteArray());
            java.nio.file.Files.writeString(out.resolve(absa.file().fileName().replace(".zip", ".txt")), absaText,
                    java.nio.charset.StandardCharsets.US_ASCII);
            java.nio.file.Files.write(out.resolve(nedbank.file().fileName()), nedbankXlsx.toByteArray());
            java.nio.file.Files.writeString(out.resolve("PASSWORDS-invented-data.txt"), "Invented data only. "
                    + "Each file's one-time password, as its answer showed it once:\r\n"
                    + absa.file().fileName() + "  " + absa.password() + "\r\n"
                    + nedbank.file().fileName() + "  " + nedbank.password() + "\r\n");
        }
    }

    /**
     * Johannesburg's first set, invented: a workplace in each of its areas -- A and K at Absa's table code, F at
     * Nedbank's, X at Standard Bank's -- and six members at each, who joined, left and were suspended in September.
     */
    private void inventJohannesburg() {
        String[] areas = {"A", "F", "K", "X"};
        int[] tableCodes = {24, 13, 24, 5};
        String[] surnames = {"Specimen", "Fictitious", "Madeup", "Imaginary", "Sampleton", "Placeholder"};
        String[] firstNames = {"Alex", "Sam", "Jordan", "Robin", "Kim", "Lee"};
        for (int w = 0; w < areas.length; w++) {
            workplace(992001 + w, "INVENTED " + areas[w] + " BRANCH", tableCodes[w], areas[w]);
            for (int m = 0; m < surnames.length; m++) {
                int n = w * surnames.length + m + 1;
                Member member = MemberFixture.withSurname(m == 0 ? "Spécimen" : surnames[m]);
                member.setFirstNames(firstNames[(m + w) % firstNames.length]);
                member.setInitials(firstNames[(m + w) % firstNames.length].substring(0, 1));
                member.setTitle(m % 2 == 0 ? "Mr" : "Ms");
                member.setMembershipNumber(String.format("M-092%03d", n));
                member.setEmployeeNumber(String.format("92%06d", n));
                member.setStatus(m == 4 ? "Resigned" : m == 5 ? "Suspended" : "Active");
                member.setJoinDate(m < 3 ? LocalDate.of(2026, 9, 2 + n % 20) : LocalDate.of(2018, 3, 1));
                member.setPaymentType(m == 2 ? "Debit Order" : "Payroll Deduction");
                member.setMonthlySubscription(new BigDecimal("174.00"));
                Integer id = members.save(member).getId();
                jdbc.update("UPDATE members SET workplace_id = (SELECT id FROM workplaces WHERE ra_number = ?) "
                        + "WHERE id = ?", 992001 + w, id);
                if (m == 4) {
                    jdbc.update("INSERT INTO member_status_changes (member_id, from_status, to_status, reason, "
                            + "changed_by, changed_at, deletion_code_id) VALUES (?, 'Active', 'Resigned', 'DELETION_CODE', "
                            + "'seed', TIMESTAMP WITH TIME ZONE '2026-09-21 08:00:00+00:00', (SELECT id FROM "
                            + "deletion_codes WHERE sasbo_code = ?))", id, 1 + w);
                }
                if (m == 5) {
                    jdbc.update("INSERT INTO member_status_changes (member_id, from_status, to_status, reason, "
                            + "changed_by, changed_at, suspension_code_id) VALUES (?, 'Active', 'Suspended', "
                            + "'SUSPENSION_CODE', 'seed', TIMESTAMP WITH TIME ZONE '2026-09-15 08:00:00+00:00', "
                            + "(SELECT id FROM suspension_codes WHERE code = ?))", id, w % 2 == 0 ? 11 : 19);
                }
            }
        }
    }

    private void workplace(int ra, String name, int tableCode, String area) {
        jdbc.update("INSERT INTO workplaces (ra_number, name, employer_printed, table_code_id, table_code_by, area_id, "
                + "committee_code, committee_id, staff_complement, cl_code) VALUES (?, ?, 'INVENTED EMPLOYER', "
                + "(SELECT id FROM table_codes WHERE code = ?), 'NAME', (SELECT id FROM sasbo_areas WHERE letter = ?), "
                + "'Z99', NULL, 40, '00000000')", ra, name, tableCode, area);
    }

    private void member(String number, String employee, int ra) {
        Member member = MemberFixture.withSurname("Inventedseed");
        member.setMembershipNumber(number);
        member.setEmployeeNumber(employee);
        member.setInitials("S");
        member.setTitle("Ms");
        member.setStatus("Active");
        member.setJoinDate(LocalDate.of(2026, 9, 7));
        member.setPaymentType("Payroll Deduction");
        member.setMonthlySubscription(new BigDecimal("174.00"));
        Integer id = members.save(member).getId();
        jdbc.update("UPDATE members SET workplace_id = (SELECT id FROM workplaces WHERE ra_number = ?) WHERE id = ?", ra,
                id);
    }
}
