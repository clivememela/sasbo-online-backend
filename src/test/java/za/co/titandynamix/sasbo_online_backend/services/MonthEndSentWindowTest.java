package za.co.titandynamix.sasbo_online_backend.services;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.AfterEach;
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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import za.co.titandynamix.sasbo_online_backend.acb.AcbTime;
import za.co.titandynamix.sasbo_online_backend.dto.monthend.MonthEndProducedFileDto;
import za.co.titandynamix.sasbo_online_backend.dto.monthend.MonthEndRegionPackDto;
import za.co.titandynamix.sasbo_online_backend.dto.monthend.MonthEndStatisticDto;
import za.co.titandynamix.sasbo_online_backend.entity.member.Member;
import za.co.titandynamix.sasbo_online_backend.fixtures.MemberFixture;
import za.co.titandynamix.sasbo_online_backend.repository.MemberRepository;
import za.co.titandynamix.sasbo_online_backend.support.SendFixture;
import za.co.titandynamix.sasbo_online_backend.utils.DataSeeder;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * What leaves SASBO counts the month as its figures count it (October build, the review of 8 Oct): from the cut-off of
 * the month before up to the month's own take -- Stephne, MEN-14: "After month end is done - all capturing and changes
 * move to the next month" -- and a leaving undone by a rejoin in the same window is no leaving (MEN-23: "We would like
 * to retain current membership as it happened in the same month").
 *
 * <p>On {@link SendFixture}'s invented population, with the months taken as SASBO takes them: August at 07:10 SAST on its
 * last day, September at 07:10 SAST on Wed 30 Sep. A change saved after a take, on the month's last day, is the next
 * month's -- in the next month's bank file and the next month's region pack -- never this month's, and never in neither:
 * a bank told nothing keeps deducting a member who resigned.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = {
        "sasbo.workplaces=all",
        "spring.datasource.url=jdbc:h2:mem:month-end-sent-window;MODE=PostgreSQL;DB_CLOSE_DELAY=-1"})
@Import(MonthEndSentWindowTest.TheMonthsAsSasboTakesThem.class)
@DisplayName("What leaves SASBO counts the month's window")
class MonthEndSentWindowTest {

    /** 07:10 SAST on Mon 31 Aug 2026: August taken on its last business day. */
    static final Instant AUGUST_TAKEN = Instant.parse("2026-08-31T05:10:00Z");

    /** 07:10 SAST on Wed 30 Sep 2026: September taken on its last business day. */
    static final Instant SEPTEMBER_TAKEN = Instant.parse("2026-09-30T05:10:00Z");

    /** Where the files and packs are made: the morning after. */
    static final Instant MORNING_AFTER = Instant.parse("2026-10-01T06:00:00Z");

    static final YearMonth AUGUST = SendFixture.TAKEN;
    static final YearMonth SEPTEMBER = YearMonth.of(2026, 9);

    /** The bank whose file holds the month's new and deleted only: table code 902, area B (NORTH2's). */
    static final String CHANGES_ONLY_BANK = "INVENTED_INSURER";

    @TestConfiguration
    static class TheMonthsAsSasboTakesThem {
        @Bean
        @Primary
        MovableClock theMovableClock() {
            return new MovableClock();
        }
    }

    /** The injected clock, moved by each test to the instant a person would act. */
    static final class MovableClock extends Clock {
        private volatile Instant now = AUGUST_TAKEN;

        void at(Instant instant) {
            this.now = instant;
        }

        @Override
        public ZoneId getZone() {
            return AcbTime.ZONE;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return Clock.fixed(this.now, zone);
        }

        @Override
        public Instant instant() {
            return this.now;
        }
    }

    /** Nothing of the seed's: only the fixture is on this database. */
    @MockitoBean
    private DataSeeder noSeed;

    @Autowired
    private MovableClock clock;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private MemberRepository members;

    @Autowired
    private MonthEndTakes takes;

    @Autowired
    private MonthEndSentFiles files;

    @Autowired
    private MonthEndRegionPacks packs;

    @Autowired
    private MonthEndStatistics statistics;

    private Map<String, Integer> ids;

    @BeforeEach
    void augustTakenOnItsLastDay() {
        clock.at(AUGUST_TAKEN);
        SendFixture.clear(jdbc);
        ids = SendFixture.insert(jdbc, members);
        SendFixture.settings(jdbc);
        takes.take(AUGUST, "seed");
    }

    @AfterEach
    void nobodySignedIn() {
        SecurityContextHolder.clearContext();
    }

    // ------------------------------------------------------------------------------------------------ MEN-14

    @Test
    @DisplayName("🔒 MEN-14: a resignation saved after August was taken goes to the bank in September's file -- not in August's, and never in neither")
    void aLeavingAfterTheTakeIsTheNextMonths() throws Exception {
        clock.at(Instant.parse("2026-08-31T10:00:00Z"));
        leave("b3", "2026-08-31T10:00:00Z");
        septemberTaken();

        assertThat(rows(AUGUST, "DEL")).as("August's file was the month as taken at 07:10")
                .containsExactly(SendFixture.number("b2"));
        assertThat(rows(SEPTEMBER, "DEL")).as("the 12:00 resignation is September's")
                .containsExactly(SendFixture.number("b3"));
        assertThat(packListing(AUGUST, "Deleted members")).containsExactly(SendFixture.number("b2"));
        assertThat(packListing(SEPTEMBER, "Deleted members")).containsExactly(SendFixture.number("b3"));
    }

    @Test
    @DisplayName("🔒 MEN-14: a member captured after August was taken, with an August Date Joined, is new in September's file and pack -- not August's")
    void aCaptureAfterTheTakeIsTheNextMonths() throws Exception {
        clock.at(Instant.parse("2026-08-31T10:30:00Z"));
        String late = captured("2026-08-31T10:30:00Z", LocalDate.of(2026, 8, 31));
        septemberTaken();

        assertThat(rows(AUGUST, "NEW")).as("captured after August was taken").doesNotContain(late);
        assertThat(rows(SEPTEMBER, "NEW")).as("counted where the figures count it").containsExactly(late);
        assertThat(packListing(AUGUST, "New members")).doesNotContain(late);
        assertThat(packListing(SEPTEMBER, "New members")).containsExactly(late);
    }

    // ------------------------------------------------------------------------------------------------ MEN-23

    @Test
    @DisplayName("🔒 MEN-23: a leaving undone by a rejoin in the same month is sent to no bank and listed as deleted nowhere -- the member kept their membership")
    void aLeavingUndoneInTheSameMonthIsNoLeaving() throws Exception {
        clock.at(Instant.parse("2026-09-10T08:00:00Z"));
        long leaving = leave("b3", "2026-09-10T08:00:00Z");
        clock.at(Instant.parse("2026-09-12T08:00:00Z"));
        rejoin("b3", "Resigned", "2026-09-12T08:00:00Z", leaving);
        septemberTaken();

        assertThat(rows(SEPTEMBER, "DEL")).as("the leaving was undone").doesNotContain(SendFixture.number("b3"));
        assertThat(rows(SEPTEMBER, "NEW")).as("nor is the member new: they kept their membership")
                .doesNotContain(SendFixture.number("b3"));
        assertThat(packListing(SEPTEMBER, "Deleted members")).doesNotContain(SendFixture.number("b3"));
    }

    // ------------------------------------------------------------------------------------------------ PROVISIONAL

    @Test
    @DisplayName("PROVISIONAL (the review's interim, 8 Oct; SASBO's Friday list, with MEN-33): a member re-instated after their leaving went to the bank is sent to it again as NEW")
    void aReinstatementAfterItsDeletionIsSentAsNew() throws Exception {
        // b2 retired on 25 Aug, and August's file told the bank to stop deducting (DEL).
        clock.at(Instant.parse("2026-09-05T08:00:00Z"));
        rejoin("b2", "Retired", "2026-09-05T08:00:00Z", null);
        septemberTaken();

        assertThat(rows(AUGUST, "DEL")).containsExactly(SendFixture.number("b2"));
        assertThat(rows(SEPTEMBER, "NEW")).as("an ordinary reinstatement: August was taken, so the bank deducts again")
                .containsExactly(SendFixture.number("b2"));
        assertThat(rows(SEPTEMBER, "DEL")).isEmpty();
    }

    // ------------------------------------------------------------------------------------------------ rule C

    /** What a statistic's notes say where rule C applies, and where the caller is shown every figure (OB28). */
    static final String RULE_C = "A membership of one to four at a named employer or table code is shown as fewer than five";
    static final String EVERY_FIGURE = "You are signed in to see the month's figures, so every figure is shown in full.";

    @Test
    @DisplayName("🔒 a region's pack keeps rule C whoever makes it -- the packs' decision 13, until MEN-27 and MEN-28 are answered -- though its maker sees every figure on screen")
    void aPacksSmallCountsDoNotDependOnWhoMadeIt() {
        clock.at(MORNING_AFTER);
        signedInSeeingEveryFigure();

        assertThat(statistics.membership(AUGUST.toString(), "A", null).notes())
                .as("the control: on screen, this caller is shown every figure (OB28)").contains(EVERY_FIGURE);
        List<String> notes = notesOf(statistics.forPack(AUGUST.toString(), "NORTH", List.of("A", "B"), true));
        assertThat(notes).as("a pack goes to a Regional Secretary by e-mail: rule C, whoever pressed Produce")
                .anyMatch(note -> note.startsWith(RULE_C)).doesNotContain(EVERY_FIGURE);
    }

    // ------------------------------------------------------------------------------------------------ helpers

    /** September taken at 07:10 on its last day, then the clock at the morning after, where the files are made. */
    private void septemberTaken() {
        clock.at(SEPTEMBER_TAKEN);
        takes.take(SEPTEMBER, "seed");
        clock.at(MORNING_AFTER);
    }

    /** The membership numbers of a changes-only bank's file of {@code month}, of one class -- NEW or DEL. */
    private List<String> rows(YearMonth month, String className) throws Exception {
        MonthEndProducedFileDto produced = files.produceBank(month.toString(), CHANGES_ONLY_BANK);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        files.download(produced.file().id()).body().accept(out);
        List<String> numbers = new ArrayList<>();
        for (List<String> row : MonthEndSentFilesTest.sheet(out.toByteArray(), produced.password())) {
            if (row.size() > 5 && className.equals(row.get(0))) {
                numbers.add(row.get(5));
            }
        }
        return numbers;
    }

    /** The membership numbers on one listing sheet of NORTH2's pack of {@code month} (area B), made again for it. */
    private List<String> packListing(YearMonth month, String sheetName) throws Exception {
        MonthEndRegionPackDto pack = packs.produce(month.toString(), "NORTH2", true).pack();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        packs.download(pack.id(), "xlsx").body().accept(out);
        List<String> numbers = new ArrayList<>();
        try (XSSFWorkbook book = new XSSFWorkbook(new ByteArrayInputStream(out.toByteArray()))) {
            Sheet sheet = book.getSheet(sheetName);
            for (Row row : sheet) {
                if (row.getRowNum() == 0 || row.getCell(1) == null) {
                    continue;
                }
                String number = row.getCell(1).getStringCellValue();
                if (number.startsWith("M-")) {
                    numbers.add(number);
                }
            }
        }
        return numbers;
    }

    /** {@code key} resigns at {@code at} (SASBO's code 2), as Change status records it; the change's id. */
    private long leave(String key, String at) {
        jdbc.update("INSERT INTO member_status_changes (member_id, from_status, to_status, reason, changed_by, "
                        + "changed_at, deletion_code_id) VALUES (?, 'Active', 'Resigned', 'DELETION_CODE', 'system', ?, "
                        + "(SELECT id FROM deletion_codes WHERE sasbo_code = 2))",
                ids.get(key), OffsetDateTime.ofInstant(Instant.parse(at), ZoneOffset.UTC));
        jdbc.update("UPDATE members SET status = 'Resigned' WHERE id = ?", ids.get(key));
        return changeAt(key, at);
    }

    /**
     * {@code key} reinstated at {@code at} out of {@code from} -- undoing {@code undoes}, where a rejoin in the same month
     * undid its leaving (MEN-23), or {@code null} for an ordinary reinstatement -- as Change status records it.
     */
    private void rejoin(String key, String from, String at, Long undoes) {
        jdbc.update("INSERT INTO member_status_changes (member_id, from_status, to_status, reason, changed_by, "
                        + "changed_at, undoes_id) VALUES (?, ?, 'Active', 'REINSTATED', 'system', ?, ?)",
                ids.get(key), from, OffsetDateTime.ofInstant(Instant.parse(at), ZoneOffset.UTC), undoes);
        jdbc.update("UPDATE members SET status = 'Active' WHERE id = ?", ids.get(key));
    }

    private long changeAt(String key, String at) {
        Long id = jdbc.queryForObject("SELECT id FROM member_status_changes WHERE member_id = ? AND changed_at = ?",
                Long.class, ids.get(key), OffsetDateTime.ofInstant(Instant.parse(at), ZoneOffset.UTC));
        return id == null ? -1 : id;
    }

    /**
     * An invented member captured at {@code at}, who joined on {@code joined}: paying by payroll deduction at area B's
     * workplace (table code 902) -- the capture's instant as the capture stamps it (MEN-14). Their membership number.
     */
    private String captured(String at, LocalDate joined) {
        Member member = MemberFixture.withSurname("Inventedlate");
        member.setFirstNames("Madeup");
        member.setInitials("M");
        member.setTitle("Ms");
        member.setMembershipNumber("M-720000009");
        member.setEmployeeNumber("20000009");
        member.setStatus("Active");
        member.setJoinDate(joined);
        member.setPaymentType("Payroll Deduction");
        member.setMonthlySubscription(new java.math.BigDecimal("174.00"));
        Integer id = members.save(member).getId();
        jdbc.update("UPDATE members SET workplace_id = (SELECT id FROM workplaces WHERE ra_number = 990002), "
                + "captured_at = ? WHERE id = ?", OffsetDateTime.ofInstant(Instant.parse(at), ZoneOffset.UTC), id);
        return "M-720000009";
    }

    /** Signed in as head office would be: the month's figures exactly (MONTH_END_VIEW), and the packs to send. */
    static void signedInSeeingEveryFigure() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("invented.head",
                null, List.of(new SimpleGrantedAuthority("MONTH_END_VIEW"),
                new SimpleGrantedAuthority("MONTH_END_SEND"))));
    }

    /** Every note of every statistic of a pack's places. */
    static List<String> notesOf(Map<String, List<MonthEndStatisticDto>> places) {
        List<String> notes = new ArrayList<>();
        places.values().forEach(statistics -> statistics.forEach(s -> notes.addAll(s.notes())));
        return notes;
    }
}
