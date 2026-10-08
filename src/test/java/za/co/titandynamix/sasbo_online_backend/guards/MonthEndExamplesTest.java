package za.co.titandynamix.sasbo_online_backend.guards;

import com.fasterxml.jackson.core.util.DefaultIndenter;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.MailSendException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import za.co.titandynamix.sasbo_online_backend.acb.AcbTime;
import za.co.titandynamix.sasbo_online_backend.exception.MonthEndRefusalCode;
import za.co.titandynamix.sasbo_online_backend.fixtures.MemberFixture;
import za.co.titandynamix.sasbo_online_backend.monthend.MonthEndMailer;
import za.co.titandynamix.sasbo_online_backend.monthend.MonthEndSftp;
import za.co.titandynamix.sasbo_online_backend.security.CallerScope;
import za.co.titandynamix.sasbo_online_backend.security.ScopedPrincipal;
import za.co.titandynamix.sasbo_online_backend.services.MonthEndRegionPacks;
import za.co.titandynamix.sasbo_online_backend.services.MonthEndSentFiles;
import za.co.titandynamix.sasbo_online_backend.services.MonthEndStatistics;
import za.co.titandynamix.sasbo_online_backend.services.MonthEndTakes;
import za.co.titandynamix.sasbo_online_backend.support.ApiSurface;
import za.co.titandynamix.sasbo_online_backend.utils.DataSeeder;
import za.co.titandynamix.sasbo_online_backend.utils.DemonstrationMemberSeeder;
import za.co.titandynamix.sasbo_online_backend.utils.WorkplaceSeeder;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Clock;
import java.time.Instant;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * THE GOLDEN ANSWERS (month end, MD48, MV-32): {@value #FILE_NAME} -- a real answer for each route of the arc and an
 * envelope for each refusal, made by the running server and copied to the client byte for byte. The client's test
 * handlers are built from it, and the deck's fixtures are cut from it, so the three agree on the server's day and not
 * on the day somebody typed a fixture.
 *
 * <h2>🔒 Invented, and nothing of SASBO's (MD57, MV-52)</h2>
 * The answers are made from invented lists, invented workplaces and an invented member written here -- the start's
 * own seeding is stood down -- because the file leaves the server's repository: it is copied into the client, and from
 * there onto slides. A scan holds it to that: no R/A number and no workplace name of the resource appears in it, and
 * its canary reddens.
 *
 * <p>Written by {@code ./gradlew generateApiSurface}, which runs this class with {@code -DapiSurface.write=true};
 * checked in the fast lane, like the other contract artefacts. The clock is frozen, and the one value of the file
 * that is the database's -- a written change's id -- is written as the first, so the file is the same on every run.
 */
@SpringBootTest(properties = {
        "sasbo.workplaces=all",
        "spring.datasource.url=jdbc:h2:mem:month-end-examples;MODE=PostgreSQL;DB_CLOSE_DELAY=-1"})
@AutoConfigureMockMvc(addFilters = false)
@Import(MonthEndExamplesTest.AFrozenClock.class)
// October build: the routes held by MONTH_END_SEND are asked as a holder of it -- method security runs with the filters
// off, and every route that holds no permission answers as it always has.
@WithMockUser(authorities = "MONTH_END_SEND")
@DisplayName("Month end's golden answers")
class MonthEndExamplesTest {

    static final String FILE_NAME = "month-end-examples.json";
    static final Path FILE = Paths.get(FILE_NAME);

    /** What every envelope's {@code timestamp} is written as: the one value of the file that is the wall clock's. */
    static final String TIMESTAMP = "2026-09-29T08:00:00";

    /** The instant every change is dated with. */
    static final Instant NOW = Instant.parse("2026-09-29T08:00:00Z");

    /** The invented member the status route is shown on. */
    static final String MEMBER = "M-900001";

    @TestConfiguration
    static class AFrozenClock {
        @Bean
        @Primary
        Clock frozenClockForTheExamples() {
            return Clock.fixed(NOW, AcbTime.ZONE);
        }
    }

    /**
     * A request of an example: a method, an address, and a body where it has one. {@code {member}} is the invented
     * member. {@code gateHeld}: asked with both of the figures' places taken. {@code withoutWorkplaces}: asked in a
     * transaction that has emptied the workplaces and is rolled back after -- what a deployment with none on record
     * answers (04.2, MD54). {@code packInHand}: asked in a rolled-back transaction holding another month's pack in
     * preparation (04.10). {@code {pack}} is a pack prepared for the example.
     */
    record Example(String method, String path, String body, boolean gateHeld, boolean withoutWorkplaces,
                   boolean packInHand, String setup) {
        Example(String method, String path, String body) {
            this(method, path, body, false, false, false, "");
        }

        Example(String method, String path, String body, boolean gateHeld) {
            this(method, path, body, gateHeld, false, false, "");
        }

        Example(String method, String path, String body, boolean gateHeld, boolean withoutWorkplaces) {
            this(method, path, body, gateHeld, withoutWorkplaces, false, "");
        }

        Example(String method, String path, String body, boolean gateHeld, boolean withoutWorkplaces,
                boolean packInHand) {
            this(method, path, body, gateHeld, withoutWorkplaces, packInHand, "");
        }

        /** October build: an example asked in a setting of its own -- one of {@link MonthEndExamplesTest#SETUPS}. */
        Example(String method, String path, String body, String setup) {
            this(method, path, body, false, false, false, setup);
        }

        String key() {
            return method + " " + path.replaceAll("\\?.*$", "").replace("{member}", "{id}").replace("{pack}", "{id}")
                    .replace("{workplace}", "{id}").replace("{regionpack}", "{id}").replace("{southpack}", "{id}")
                    .replace("{bankfile}", "{id}").replace("{lawcallfile}", "{id}")
                    .replaceAll("/\\d{4}-\\d{2}(/|$)", "/{month}$1")
                    .replaceAll("/\\d+/", "/{id}/");
        }
    }

    /** Every route of the arc that answers, and the request that shows it. */
    static final List<Example> ANSWERS = answers();

    /** Every refusal code, and a request that provokes it through its route (MV-33). */
    static final Map<MonthEndRefusalCode, Example> REFUSALS = refusals();

    private static List<Example> answers() {
        List<Example> routes = new ArrayList<>();
        for (String list : List.of("regions", "areas", "employer-groups", "employers", "table-codes",
                "committee-provinces", "committees", "suspension-codes", "deletion-codes", "status")) {
            routes.add(new Example("GET", "/api/reference/" + list, null));
        }
        routes.add(new Example("GET", "/api/workplaces?page=0&size=2", null));
        // A.1: the invented main street's visits -- one of SASBO's own months and one captured here, newest first -- and
        // a visit recorded, the complement it found the one on record, so no other answer moves.
        routes.add(new Example("GET", "/api/workplaces/{workplace}/visits", null));
        routes.add(new Example("POST", "/api/workplaces/{workplace}/visits", "{\"visitedOn\": \"2026-09-10\", "
                + "\"organiser\": \"INVENTED ORGANISER\", \"complementFound\": 40, \"membershipFound\": 12}"));
        // 02.2: the figures of the clock's month, by employer group -- the first screen's one request (MD69).
        routes.add(new Example("GET", "/api/members/summary?by=group", null));
        // 03.6: the invented member's history -- a capture and a field changed, merged with its status changes.
        routes.add(new Example("GET", "/api/members/{member}/history", null));
        routes.add(new Example("POST", "/api/members/{member}/status", "{\"toStatus\": \"Resigned\", "
                + "\"expectedStatus\": \"Active\", \"reason\": \"DELETION_CODE\", \"deletionCode\": 2, "
                + "\"reasonUnderCode\": \"INVENTED: MOVED AWAY\", \"note\": \"Invented: moved away\"}"));
        // 04.2: the months taken -- the invented August, taken as the seed takes a month, at the frozen clock -- and
        // that month's record.
        routes.add(new Example("GET", "/api/month-end/months", null));
        routes.add(new Example("GET", "/api/month-end/" + TAKEN, null));
        // 04.3 to 04.8: each statistic of the invented August, read from what was frozen.
        for (String statistic : List.of("new-and-deleted", "membership", "leavers", "branches", "recruitment",
                "representatives", "visits")) {
            routes.add(new Example("GET", "/api/month-end/" + TAKEN + "/" + statistic, null));
        }
        // 04.10: the invented August's statistics pack -- asked for, prepared on the caller's thread under test, READY --
        // and a pack as the screen polls it.
        routes.add(new Example("POST", "/api/month-end/" + TAKEN + "/pack", null));
        routes.add(new Example("GET", "/api/month-end/packs/{pack}", null));
        // October build, the files that leave SASBO (monthend-packs): who gets what, and every change to it.
        routes.add(new Example("GET", "/api/month-end/settings", null));
        routes.add(new Example("POST", "/api/month-end/settings/recipients", "{\"code\": \"NORTH2\", "
                + "\"label\": \"Invented North (second set)\", \"regionCode\": \"NORTH\", \"areaLetters\": [\"A\"], "
                + "\"position\": \"Regional Secretary\", \"email\": \"regional.secretary.north2@example.org\", "
                + "\"active\": false}"));
        routes.add(new Example("PUT", "/api/month-end/settings/recipients/NORTH", "{\"label\": \"Invented North\", "
                + "\"regionCode\": \"NORTH\", \"areaLetters\": [], \"position\": \"Regional Secretary\", "
                + "\"email\": \"regional.secretary.north@example.org\", \"active\": true, \"version\": 0}"));
        routes.add(new Example("POST", "/api/month-end/settings/banks", "{\"code\": \"INVENTED_LENDER\", "
                + "\"name\": \"Invented Lender\", \"tableCodes\": [904], \"fullList\": false, \"layout\": \"CSV\"}"));
        routes.add(new Example("PUT", "/api/month-end/settings/banks/INVENTED_INSURER", "{\"name\": \"Invented Insurer\", "
                + "\"tableCodes\": [903], \"fullList\": true, \"layout\": \"SHEET\", \"active\": true, \"version\": 0}"));
        routes.add(new Example("PUT", "/api/month-end/settings/providers/CRITICARE", "{\"active\": true, "
                + "\"version\": 0}"));
        // The regions' packs: the month's, one made, the month's sending, one e-mailed, the record of what was sent.
        routes.add(new Example("GET", "/api/month-end/" + TAKEN + "/region-packs", null, "PACK_DOWNLOADED"));
        routes.add(new Example("POST", "/api/month-end/" + TAKEN + "/region-packs/NORTH", null));
        routes.add(new Example("POST", "/api/month-end/" + TAKEN + "/region-packs/send", "{\"formats\": [\"PDF\"]}",
                "PACK_MADE"));
        routes.add(new Example("POST", "/api/month-end/region-packs/{regionpack}/email",
                "{\"formats\": [\"PDF\", \"XLSX\"]}", "MAIL_ON"));
        routes.add(new Example("GET", "/api/month-end/" + TAKEN + "/deliveries?page=0&size=10", null,
                "PACK_DOWNLOADED"));
        // The banks' and providers' files: the month's, a bank's and a provider's made (the password shown once,
        // written here as a placeholder), and Law Call's put onto its SFTP server where one is set up.
        routes.add(new Example("GET", "/api/month-end/" + TAKEN + "/files", null, "FILE_MADE"));
        routes.add(new Example("POST", "/api/month-end/" + TAKEN + "/files/banks/INVENTED_BANK", null));
        routes.add(new Example("POST", "/api/month-end/" + TAKEN + "/files/providers/OM_FUNERAL", null));
        routes.add(new Example("POST", "/api/month-end/files/{lawcallfile}/sftp", null, "SFTP_ON"));
        return routes;
    }

    /**
     * The settings an October build example is asked in: mail on, or on and failing; SFTP set up, or set up and
     * failing; a caller who sees one region; the switch below all; a pack made (and downloaded); a bank's file made.
     */
    static final Set<String> SETUPS = Set.of("", "MAIL_ON", "MAIL_FAILS", "SFTP_ON", "SFTP_FAILS", "REGION_USER",
            "BELOW_ALL", "PACK_MADE", "PACK_DOWNLOADED", "FILE_MADE");

    /** The month the invented rows have taken. */
    static final String TAKEN = "2026-08";

    private static Map<MonthEndRefusalCode, Example> refusals() {
        Map<MonthEndRefusalCode, Example> provoked = new EnumMap<>(MonthEndRefusalCode.class);
        String status = "/api/members/{member}/status";
        provoked.put(MonthEndRefusalCode.REQUEST_MALFORMED, new Example("GET", "/api/workplaces?page=first", null));
        provoked.put(MonthEndRefusalCode.WORKPLACE_PAGE_INVALID, new Example("GET", "/api/workplaces?page=9&size=2", null));
        provoked.put(MonthEndRefusalCode.MEMBER_NOT_FOUND, new Example("POST", "/api/members/999999/status",
                "{\"toStatus\": \"Resigned\", \"reason\": \"DELETION_CODE\", \"deletionCode\": 2}"));
        provoked.put(MonthEndRefusalCode.STATUS_UNKNOWN, new Example("POST", status,
                "{\"toStatus\": \"Gone\", \"reason\": \"DELETION_CODE\", \"deletionCode\": 2}"));
        provoked.put(MonthEndRefusalCode.STATUS_REASON_REQUIRED, new Example("POST", status,
                "{\"toStatus\": \"Resigned\"}"));
        provoked.put(MonthEndRefusalCode.STATUS_REASON_MISMATCH, new Example("POST", status,
                "{\"toStatus\": \"Resigned\", \"reason\": \"SUSPENSION_CODE\", \"suspensionCode\": 11}"));
        provoked.put(MonthEndRefusalCode.DELETION_CODE_UNKNOWN, new Example("POST", status,
                "{\"toStatus\": \"Resigned\", \"reason\": \"DELETION_CODE\", \"deletionCode\": 77}"));
        provoked.put(MonthEndRefusalCode.SUSPENSION_CODE_UNKNOWN, new Example("POST", status,
                "{\"toStatus\": \"Suspended\", \"reason\": \"SUSPENSION_CODE\", \"suspensionCode\": 77}"));
        provoked.put(MonthEndRefusalCode.STATUS_CHANGED_SINCE, new Example("POST", status,
                "{\"toStatus\": \"Resigned\", \"expectedStatus\": \"Pending\", \"reason\": \"DELETION_CODE\", "
                        + "\"deletionCode\": 2}"));
        // 01.3: thrown under MemberController and answered by the global handler (ME-12) -- a save that names a
        // workplace not on record here.
        provoked.put(MonthEndRefusalCode.WORKPLACE_UNKNOWN, new Example("PUT", "/api/members/{member}",
                "{\"workplaceId\": 999999}"));
        // A.1: a visit with no day.
        provoked.put(MonthEndRefusalCode.VISIT_INVALID, new Example("POST", "/api/workplaces/{workplace}/visits",
                "{\"complementFound\": 40}"));
        // 03.4: thrown there too -- an edit that would change the status, which Change status alone changes (BR77).
        provoked.put(MonthEndRefusalCode.STATUS_VIA_CHANGE_STATUS, new Example("PUT", "/api/members/{member}",
                "{\"status\": \"Resigned\"}"));
        // 02.2: the figures' refusals -- the last with both of the gate's places taken, as two slow queries would.
        provoked.put(MonthEndRefusalCode.SUMMARY_GROUPING_UNKNOWN, new Example("GET", "/api/members/summary?by=bank", null));
        provoked.put(MonthEndRefusalCode.SUMMARY_AREA_UNKNOWN, new Example("GET", "/api/members/summary?area=QQ", null));
        provoked.put(MonthEndRefusalCode.SUMMARY_MONTH_INVALID, new Example("GET",
                "/api/members/summary?month=last-month", null));
        provoked.put(MonthEndRefusalCode.SUMMARY_MONTH_IN_FUTURE, new Example("GET",
                "/api/members/summary?month=2027-01", null));
        provoked.put(MonthEndRefusalCode.SUMMARY_UNAVAILABLE, new Example("GET", "/api/members/summary", null, true));
        provoked.put(MonthEndRefusalCode.HISTORY_PAGE_INVALID, new Example("GET", "/api/members/{member}/history?page=99",
                null));
        // 04.2: the take says what it knows of the month, then refuses every caller -- and a past month not taken.
        String take = "/api/month-end/%s/take";
        provoked.put(MonthEndRefusalCode.MONTH_NOT_BEGUN, new Example("POST", take.formatted("2026-10"), null));
        provoked.put(MonthEndRefusalCode.MONTH_ALREADY_TAKEN, new Example("POST", take.formatted(TAKEN), null));
        // 04.11: a take again that names a take which is not the month's newest -- said before the take refuses.
        provoked.put(MonthEndRefusalCode.MONTH_TAKEN_SINCE, new Example("POST", take.formatted(TAKEN),
                "{\"replaces\": 999999}"));
        provoked.put(MonthEndRefusalCode.MONTH_NOT_READY, new Example("POST", take.formatted("2026-07"), null, false,
                true));
        provoked.put(MonthEndRefusalCode.IDENTITY_REQUIRED, new Example("POST", take.formatted("2026-07"), null));
        provoked.put(MonthEndRefusalCode.MONTH_NOT_TAKEN, new Example("GET", "/api/month-end/2026-07", null));
        // 04.10: a pack asked for while another month's is in hand -- there is no queue -- and a pack that is not there.
        provoked.put(MonthEndRefusalCode.PACK_BUSY, new Example("POST", "/api/month-end/" + TAKEN + "/pack", null, false,
                false, true));
        provoked.put(MonthEndRefusalCode.PACK_NOT_FOUND, new Example("GET", "/api/month-end/packs/999999", null));
        // October build, the files that leave SASBO (monthend-packs): each refusal through its route.
        String recipients = "/api/month-end/settings/recipients";
        String banks = "/api/month-end/settings/banks";
        provoked.put(MonthEndRefusalCode.PACK_SET_NOT_FOUND, new Example("PUT", recipients + "/NOWHERE",
                "{\"version\": 0}"));
        provoked.put(MonthEndRefusalCode.PACK_SET_INVALID, new Example("POST", recipients, "{\"code\": \"bad code!\"}"));
        provoked.put(MonthEndRefusalCode.PACK_SET_TAKEN, new Example("POST", recipients, "{\"code\": \"NORTH\", "
                + "\"label\": \"Again\", \"regionCode\": \"NORTH\", \"position\": \"Regional Secretary\", "
                + "\"active\": false}"));
        provoked.put(MonthEndRefusalCode.PACK_SET_OVERLAP, new Example("POST", recipients, "{\"code\": \"NORTH2\", "
                + "\"label\": \"Invented North (second set)\", \"regionCode\": \"NORTH\", \"areaLetters\": [\"A\"], "
                + "\"position\": \"Regional Secretary\", \"active\": true}"));
        provoked.put(MonthEndRefusalCode.SETTING_CHANGED_SINCE, new Example("PUT", recipients + "/NORTH",
                "{\"label\": \"Lost\", \"regionCode\": \"NORTH\", \"position\": \"Regional Secretary\", "
                        + "\"version\": 7}"));
        provoked.put(MonthEndRefusalCode.BANK_NOT_FOUND, new Example("PUT", banks + "/NOWHERE", "{\"version\": 0}"));
        provoked.put(MonthEndRefusalCode.BANK_SETTING_INVALID, new Example("POST", banks, "{\"code\": \"OTHER\", "
                + "\"name\": \"Other\", \"tableCodes\": [999], \"layout\": \"SHEET\"}"));
        provoked.put(MonthEndRefusalCode.BANK_TAKEN, new Example("POST", banks, "{\"code\": \"INVENTED_BANK\", "
                + "\"name\": \"Again\", \"tableCodes\": [904], \"fullList\": false, \"layout\": \"SHEET\"}"));
        provoked.put(MonthEndRefusalCode.TABLE_CODE_TAKEN, new Example("POST", banks, "{\"code\": \"OTHER\", "
                + "\"name\": \"Other\", \"tableCodes\": [901], \"fullList\": false, \"layout\": \"SHEET\"}"));
        provoked.put(MonthEndRefusalCode.PROVIDER_NOT_FOUND, new Example("PUT",
                "/api/month-end/settings/providers/NOWHERE", "{\"active\": true, \"version\": 0}"));
        provoked.put(MonthEndRefusalCode.RECIPIENT_INACTIVE, new Example("POST",
                "/api/month-end/" + TAKEN + "/files/providers/CRITICARE", null));
        provoked.put(MonthEndRefusalCode.REGION_PACKS_UNAVAILABLE, new Example("POST",
                "/api/month-end/" + TAKEN + "/region-packs/NORTH", null, "BELOW_ALL"));
        provoked.put(MonthEndRefusalCode.REGION_PACK_NOT_FOUND, new Example("GET",
                "/api/month-end/region-packs/999999/pdf", null));
        provoked.put(MonthEndRefusalCode.PACK_NOT_PRODUCED, new Example("POST",
                "/api/month-end/" + TAKEN + "/region-packs/send", "{\"formats\": [\"PDF\"]}"));
        provoked.put(MonthEndRefusalCode.FILE_NOT_FOUND, new Example("GET", "/api/month-end/files/999999", null));
        provoked.put(MonthEndRefusalCode.SCOPE_TOO_NARROW, new Example("GET", "/api/month-end/" + TAKEN + "/files",
                null, "REGION_USER"));
        provoked.put(MonthEndRefusalCode.MAIL_NOT_CONFIGURED, new Example("POST",
                "/api/month-end/region-packs/{regionpack}/email", "{\"formats\": [\"PDF\"]}"));
        provoked.put(MonthEndRefusalCode.RECIPIENT_HAS_NO_ADDRESS, new Example("POST",
                "/api/month-end/region-packs/{southpack}/email", "{\"formats\": [\"PDF\"]}", "MAIL_ON"));
        provoked.put(MonthEndRefusalCode.MAIL_FAILED, new Example("POST",
                "/api/month-end/region-packs/{regionpack}/email", "{\"formats\": [\"XLSX\"]}", "MAIL_FAILS"));
        provoked.put(MonthEndRefusalCode.SFTP_NOT_CONFIGURED, new Example("POST",
                "/api/month-end/files/{lawcallfile}/sftp", null));
        provoked.put(MonthEndRefusalCode.SFTP_NOT_FOR_THIS_RECIPIENT, new Example("POST",
                "/api/month-end/files/{bankfile}/sftp", null));
        provoked.put(MonthEndRefusalCode.SFTP_FAILED, new Example("POST", "/api/month-end/files/{lawcallfile}/sftp",
                null, "SFTP_FAILS"));
        return provoked;
    }

    /** Stands the start's seeding down: nothing of SASBO's is on this database, only what {@link #invented} writes. */
    @MockitoBean
    private DataSeeder noSeedOfSasbos;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private za.co.titandynamix.sasbo_online_backend.services.SummaryGate gate;

    @Autowired
    private MonthEndTakes takes;

    @Autowired
    private za.co.titandynamix.sasbo_online_backend.services.MonthEndPacks packs;

    /** Mail is off on the examples' server unless an example turns it on. */
    @MockitoBean
    private MonthEndMailer mailer;

    /** SFTP is not set up on the examples' server unless an example sets it up. */
    @MockitoBean
    private MonthEndSftp sftp;

    /** The real statistics, but for the one example asked where the switch is below all. */
    @MockitoSpyBean
    private MonthEndStatistics statistics;

    @Autowired
    private MonthEndRegionPacks regionPacks;

    @Autowired
    private MonthEndSentFiles sentFiles;

    @Autowired
    private PlatformTransactionManager transactions;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void invented() {
        Long present = jdbc.queryForObject("SELECT count(*) FROM sasbo_regions WHERE code = 'NORTH'", Long.class);
        if (present == null || present == 0) {
            lists();
        }
        // The invented member is Active before every example, and has no change on record: an example that moves
        // them is answered the same on every run.
        jdbc.update("DELETE FROM member_status_changes WHERE member_id = (SELECT id FROM members WHERE "
                + "membership_number = ?)", MEMBER);
        jdbc.update("UPDATE members SET status = 'Active' WHERE membership_number = ?", MEMBER);
        // No pack before any example: each asks from nothing, and is answered the same on every run.
        jdbc.update("DELETE FROM month_end_packs");
        // October build: who gets what as invented, and nothing made or sent -- every row numbered from one, so an
        // example's answer is the same on every run.
        sendSettings();
        reset(mailer, sftp, statistics);
        when(mailer.configured()).thenReturn(false);
        when(sftp.configured()).thenReturn(false);
        when(sftp.state()).thenReturn(MonthEndSftp.NOT_CONFIGURED);
        when(sftp.destination()).thenReturn("sftp.example.org");
        // A.1: the invented main street's two visits and no other, numbered from one -- the visit an example records is
        // answered with the same number on every run. (The fast lane's schema is the entities': no trigger here.)
        jdbc.update("DELETE FROM workplace_visits");
        jdbc.execute("ALTER TABLE workplace_visits ALTER COLUMN id RESTART WITH 1");
        jdbc.update("INSERT INTO workplace_visits (workplace_id, visited_on, day_known, seeded, captured_at) "
                + "SELECT id, DATE '2026-07-01', FALSE, TRUE, TIMESTAMP WITH TIME ZONE '2026-09-01 08:00:00+00:00' "
                + "FROM workplaces WHERE ra_number = 900001");
        jdbc.update("INSERT INTO workplace_visits (workplace_id, visited_on, day_known, organiser, complement_found, "
                + "complement_before, membership_found, seeded, captured_at) SELECT id, DATE '2026-08-20', TRUE, "
                + "'INVENTED ORGANISER', 40, 38, 11, FALSE, TIMESTAMP WITH TIME ZONE '2026-08-20 14:00:00+00:00' "
                + "FROM workplaces WHERE ra_number = 900001");
    }

    private void lists() {
        jdbc.update("INSERT INTO sasbo_regions (code, name) VALUES ('NORTH', 'NORTH'), ('SOUTH', 'SOUTH')");
        jdbc.update("INSERT INTO sasbo_areas (letter, region_id) SELECT 'A', id FROM sasbo_regions WHERE code = 'NORTH'");
        jdbc.update("INSERT INTO sasbo_areas (letter, region_id) SELECT 'B', id FROM sasbo_regions WHERE code = 'SOUTH'");
        jdbc.update("INSERT INTO sasbo_areas (letter, region_id) VALUES ('Y', NULL)");
        jdbc.update("INSERT INTO employer_groups (name, core, ordinal) VALUES ('INVENTED GROUP CORE', TRUE, 1), "
                + "('INVENTED GROUP OTHER', FALSE, 2)");
        jdbc.update("INSERT INTO employers (ordinal, name, group_id) SELECT 1, 'INVENTED BANK', id FROM employer_groups "
                + "WHERE name = 'INVENTED GROUP CORE'");
        jdbc.update("INSERT INTO employers (ordinal, name, group_id) SELECT 2, 'INVENTED INSURER', id FROM employer_groups "
                + "WHERE name = 'INVENTED GROUP OTHER'");
        jdbc.update("INSERT INTO table_codes (code, name, employer_id, part) SELECT 901, 'INVENTED BANK', id, NULL "
                + "FROM employers WHERE ordinal = 1");
        jdbc.update("INSERT INTO table_codes (code, name, employer_id, part) SELECT 902, 'INVENTED BANK DEPARTMENTS', id, "
                + "'DEPARTMENTS' FROM employers WHERE ordinal = 1");
        jdbc.update("INSERT INTO table_codes (code, name, employer_id, part) SELECT 903, 'INVENTED INSURER CASH', id, "
                + "'CASH' FROM employers WHERE ordinal = 2");
        jdbc.update("INSERT INTO table_codes (code, name, employer_id, part) VALUES (904, 'INVENTED LENDER', NULL, NULL)");
        jdbc.update("INSERT INTO committee_provinces (letter, name) VALUES ('Q', NULL), ('R', NULL)");
        jdbc.update("INSERT INTO committees (code, province_id, name, recruiting_number) SELECT 'Q01', id, "
                + "'INVENTED NORTH COMMITTEE', 91 FROM committee_provinces WHERE letter = 'Q'");
        jdbc.update("INSERT INTO committees (code, province_id, name, recruiting_number) SELECT 'R01', id, "
                + "'INVENTED SOUTH COMMITTEE', NULL FROM committee_provinces WHERE letter = 'R'");
        jdbc.update("INSERT INTO suspension_codes (code, name) VALUES (11, 'NOT ON PAYROLL'), (13, 'DEBIT RETURNED'), "
                + "(19, 'INVALID ACCOUNT')");
        jdbc.update("INSERT INTO deletion_codes (sasbo_code, name) VALUES (2, 'Resigned Society'), "
                + "(5, 'Subscription in Arrears')");
        // 04.5: a reason SASBO keeps under code 2 -- the row a resignation names by `reasonUnderCode`.
        jdbc.update("INSERT INTO deletion_codes (sasbo_code, name, parent_id) SELECT NULL, 'INVENTED: MOVED AWAY', id "
                + "FROM deletion_codes WHERE sasbo_code = 2");
        workplace(900001, "INVENTED MAIN STREET", "INVENTED BANK", 901, "NAME", "A", "Q01", 40);
        workplace(900002, "INVENTED HEAD OFFICE", "INVENTED BANK DEPTS", 902, "SUMS", "A", "Q01", 120);
        workplace(900003, "INVENTED HARBOUR", "INVENTED INSURER", 903, "NAME", "B", "R01", 15);
        workplace(900004, "INVENTED OUTPOST", "INVENTED UNKNOWN", null, null, "Y", "Z99", 3);
        for (String list : List.of("regions", "areas", "employerGroups", "employers", "tableCodes",
                "committeeProvinces", "committees", "suspensionCodes", "deletionCodes", "workplaces")) {
            jdbc.update("INSERT INTO reference_lists (list_key, report, printed_on, confirmed_on, outcome, failure, "
                    + "row_count, recorded_at) VALUES (?, 'invented', DATE '2026-08-31', NULL, 'SEEDED', NULL, 1, "
                    + "TIMESTAMP WITH TIME ZONE '2026-09-29 08:00:00+00:00')", list);
        }
        jdbc.update("INSERT INTO members (membership_number, first_names, surname, identity_number, status, "
                + "payment_type, is_representative, created_at, updated_at) VALUES (?, 'Invented', 'Example', ?, "
                + "'Active', 'Payroll', FALSE, TIMESTAMP '2026-09-29 08:00:00', TIMESTAMP '2026-09-29 08:00:00')",
                MEMBER, MemberFixture.syntheticIdFor(900));
        // 03.5: the invented member's history, at fixed instants -- a capture, and a telephone number changed.
        jdbc.update("INSERT INTO member_changes (member_id, save_id, field, old_value, new_value, changed_by, changed_at) "
                + "SELECT id, 'invented-save-1', 'captured', NULL, membership_number, 'system', "
                + "TIMESTAMP WITH TIME ZONE '2026-09-01 08:00:00+00:00' FROM members WHERE membership_number = ?", MEMBER);
        jdbc.update("INSERT INTO member_changes (member_id, save_id, field, old_value, new_value, changed_by, changed_at) "
                + "SELECT id, 'invented-save-2', 'cellTel', '+27 60 555 0001', '+27 60 555 0002', 'system', "
                + "TIMESTAMP WITH TIME ZONE '2026-09-10 08:00:00+00:00' FROM members WHERE membership_number = ?", MEMBER);
        // 04.2: August taken, as the seed takes a month -- through the real take, at the frozen clock.
        takes.take(YearMonth.parse(TAKEN), DemonstrationMemberSeeder.SEED_ACTOR);
    }

    /** The invented recipients, banks and providers, as an administrator left them -- and nothing made or sent. */
    private void sendSettings() {
        for (String table : List.of("month_end_deliveries", "month_end_region_packs", "month_end_files",
                "month_end_bank_table_codes", "month_end_banks", "month_end_pack_sets", "month_end_providers")) {
            jdbc.update("DELETE FROM " + table);
            jdbc.execute("ALTER TABLE " + table + " ALTER COLUMN id RESTART WITH 1");
        }
        String at = "TIMESTAMP WITH TIME ZONE '2026-09-01 08:00:00+00:00'";
        jdbc.update("INSERT INTO month_end_pack_sets (code, label, region_code, area_letters, position, email, active, "
                + "version, updated_by, updated_at) VALUES ('NORTH', 'Invented North', 'NORTH', NULL, "
                + "'Regional Secretary', 'regional.secretary.north@example.org', TRUE, 0, 'seed', " + at + "), "
                + "('SOUTH', 'Invented South', 'SOUTH', NULL, 'Regional Secretary', NULL, TRUE, 0, 'seed', " + at + ")");
        jdbc.update("INSERT INTO month_end_banks (code, name, full_list, layout, active, version, updated_by, updated_at) "
                + "VALUES ('INVENTED_BANK', 'Invented Bank', TRUE, 'ABSA_FIXED_WIDTH', TRUE, 0, 'seed', " + at + "), "
                + "('INVENTED_INSURER', 'Invented Insurer', FALSE, 'SHEET', TRUE, 0, 'seed', " + at + ")");
        jdbc.update("INSERT INTO month_end_bank_table_codes (bank_id, table_code) VALUES (1, 901), (1, 902), (2, 903)");
        jdbc.update("INSERT INTO month_end_providers (code, name, layout, delivery, active, version, updated_by, "
                + "updated_at) VALUES ('OM_FUNERAL', 'Invented funeral benefit', 'OLD_MUTUAL_FUNERAL', 'DOWNLOAD', TRUE, "
                + "0, 'seed', " + at + "), ('LAW_CALL', 'Invented legal cover', 'PROVISIONAL_MEMBER_LIST', 'SFTP', "
                + "TRUE, 0, 'seed', " + at + "), ('CRITICARE', 'Invented suspended benefit', 'PROVISIONAL_MEMBER_LIST', "
                + "'DOWNLOAD', FALSE, 0, 'seed', " + at + ")");
    }

    /** An October build example's setting, made before it is asked. */
    private void setUp(String setup) throws Exception {
        assertThat(SETUPS).contains(setup);
        switch (setup) {
            case "MAIL_ON" -> when(mailer.configured()).thenReturn(true);
            case "MAIL_FAILS" -> {
                when(mailer.configured()).thenReturn(true);
                doThrow(new MailSendException("the invented mail server refused")).when(mailer)
                        .send(anyString(), anyString(), anyString(), anyList());
            }
            case "SFTP_ON" -> when(sftp.configured()).thenReturn(true);
            case "SFTP_FAILS" -> {
                when(sftp.configured()).thenReturn(true);
                doThrow(new IOException("JSchException")).when(sftp).upload(anyString(), any());
            }
            case "BELOW_ALL" -> doReturn(false).when(statistics).grouped();
            case "PACK_MADE" -> regionPacks.produce(TAKEN, "NORTH", false);
            case "PACK_DOWNLOADED" -> regionPacks.download(regionPacks.produce(TAKEN, "NORTH", false).pack().id(), "pdf")
                    .body().accept(java.io.OutputStream.nullOutputStream());
            case "FILE_MADE" -> sentFiles.produceBank(TAKEN, "INVENTED_BANK");
            default -> {
                // No setting of its own, or one made as the request is asked (REGION_USER).
            }
        }
    }

    /** A caller who sees one region, holding MONTH_END_SEND. */
    private static Authentication aNorthUser() {
        ScopedPrincipal north = new ScopedPrincipal() {
            @Override
            public String username() {
                return "invented.regional";
            }

            @Override
            public String displayName() {
                return "Invented Regional";
            }

            @Override
            public CallerScope scope() {
                return CallerScope.regions(Set.of("NORTH"));
            }
        };
        return new UsernamePasswordAuthenticationToken(north, null, List.of(new SimpleGrantedAuthority("MONTH_END_SEND")));
    }

    private void workplace(int ra, String name, String employer, Integer tableCode, String by, String area,
                           String committee, int complement) {
        jdbc.update("INSERT INTO workplaces (ra_number, name, employer_printed, table_code_id, table_code_by, area_id, "
                + "committee_code, committee_id, staff_complement, cl_code) VALUES (?, ?, ?, "
                + "(SELECT id FROM table_codes WHERE code = ?), ?, (SELECT id FROM sasbo_areas WHERE letter = ?), ?, "
                + "(SELECT id FROM committees WHERE code = ?), ?, '00000000')",
                ra, name, employer, tableCode, by, area, committee, committee, complement);
    }

    private MvcResult perform(Example example) throws Exception {
        Integer member = jdbc.queryForObject("SELECT id FROM members WHERE membership_number = ?", Integer.class, MEMBER);
        Long workplace = jdbc.queryForObject("SELECT id FROM workplaces WHERE ra_number = 900001", Long.class);
        String path = example.path().replace("{member}", String.valueOf(member))
                .replace("{workplace}", String.valueOf(workplace));
        if (path.contains("{pack}")) {
            path = path.replace("{pack}", String.valueOf(packs.ask(TAKEN).pack().id()));
        }
        setUp(example.setup());
        if (path.contains("{regionpack}")) {
            path = path.replace("{regionpack}", String.valueOf(regionPacks.produce(TAKEN, "NORTH", false).pack().id()));
        }
        if (path.contains("{southpack}")) {
            path = path.replace("{southpack}", String.valueOf(regionPacks.produce(TAKEN, "SOUTH", false).pack().id()));
        }
        if (path.contains("{bankfile}")) {
            path = path.replace("{bankfile}", String.valueOf(sentFiles.produceBank(TAKEN, "INVENTED_BANK").file().id()));
        }
        if (path.contains("{lawcallfile}")) {
            path = path.replace("{lawcallfile}", String.valueOf(sentFiles.produceProvider(TAKEN, "LAW_CALL").file().id()));
        }
        MockHttpServletRequestBuilder request = switch (example.method()) {
            case "POST" -> example.body() == null ? post(path)
                    : post(path).contentType(MediaType.APPLICATION_JSON).content(example.body());
            case "PUT" -> put(path).contentType(MediaType.APPLICATION_JSON).content(example.body());
            default -> get(path);
        };
        if (example.packInHand()) {
            // Asked in a transaction holding July's pack in preparation, and rolled back.
            return new TransactionTemplate(transactions).execute(status -> {
                status.setRollbackOnly();
                jdbc.update("INSERT INTO month_end_packs (month_start, status, asked_by, started_at, heartbeat_at) "
                        + "VALUES (DATE '2026-07-01', 'PREPARING', 'system', TIMESTAMP WITH TIME ZONE "
                        + "'2026-09-29 08:00:00+00:00', TIMESTAMP WITH TIME ZONE '2026-09-29 08:00:00+00:00')");
                try {
                    return mockMvc.perform(request).andReturn();
                } catch (Exception failed) {
                    throw new IllegalStateException(failed);
                }
            });
        }
        if (example.withoutWorkplaces()) {
            // Asked in a transaction that has emptied the workplaces, and rolled back: the request runs on this
            // thread, so the route reads what this transaction sees.
            return new TransactionTemplate(transactions).execute(status -> {
                status.setRollbackOnly();
                jdbc.update("DELETE FROM workplaces");
                try {
                    return mockMvc.perform(request).andReturn();
                } catch (Exception failed) {
                    throw new IllegalStateException(failed);
                }
            });
        }
        if ("REGION_USER".equals(example.setup())) {
            Authentication everyRegion = SecurityContextHolder.getContext().getAuthentication();
            SecurityContextHolder.getContext().setAuthentication(aNorthUser());
            try {
                return mockMvc.perform(request).andReturn();
            } finally {
                SecurityContextHolder.getContext().setAuthentication(everyRegion);
            }
        }
        if (!example.gateHeld()) {
            return mockMvc.perform(request).andReturn();
        }
        assertThat(gate.tryEnter() && gate.tryEnter()).as("both of the gate's places, taken for the example").isTrue();
        try {
            return mockMvc.perform(request).andReturn();
        } finally {
            gate.leave();
            gate.leave();
        }
    }

    private JsonNode body(MvcResult result) throws IOException {
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        if (body instanceof ObjectNode object && object.has("timestamp")) {
            object.put("timestamp", TIMESTAMP);
        }
        if (body instanceof ObjectNode object && object.has("path")) {
            object.put("path", object.path("path").asText()
                    .replaceAll("/api/members/\\d+(/|$)", "/api/members/{id}$1"));
        }
        if (body instanceof ObjectNode object && object.path("change") instanceof ObjectNode change) {
            change.put("id", 1);
        }
        if (body instanceof ObjectNode object && object.has("memberId")) {
            object.put("memberId", 1);
        }
        // A pack's number is the database's, and differs by how many packs the class asked for before.
        if (body instanceof ObjectNode object && object.has("workbooks") && object.has("id")) {
            object.put("id", 1);
        }
        // October build: a file's size and hash are a cipher's or a writer's of the moment, and a password is made for
        // each file -- written as placeholders, wherever they stand in an answer.
        normaliseFiles(body);
        if (body instanceof ObjectNode object && "PACK_BUSY".equals(object.path("code").asText())
                && object.path("details") instanceof ObjectNode details && details.has("pack")) {
            details.put("pack", "1");
        }
        return body;
    }

    /** Every {@code sha256}, {@code bytes} and {@code password} in an answer, as a placeholder. */
    private static void normaliseFiles(JsonNode node) {
        if (node instanceof ObjectNode object) {
            if (object.has("sha256")) {
                object.put("sha256", "<sha256 of the file>");
            }
            if (object.has("bytes") && object.get("bytes").isNumber()) {
                object.put("bytes", 0);
            }
            if (object.has("password")) {
                object.put("password", "<shown once>");
            }
        }
        node.forEach(MonthEndExamplesTest::normaliseFiles);
    }

    private ObjectNode recorded(ObjectNode into, String key, Example example, MvcResult result) throws IOException {
        ObjectNode entry = into.putObject(key);
        entry.put("request", example.method() + " " + example.path().replace("{member}", "{id}")
                .replace("{pack}", "{id}").replace("{regionpack}", "{id}").replace("{southpack}", "{id}")
                .replace("{bankfile}", "{id}").replace("{lawcallfile}", "{id}"));
        if (!example.setup().isEmpty()) {
            entry.put("setting", example.setup());
        }
        if (example.body() != null) {
            entry.set("requestBody", objectMapper.readTree(example.body()));
        }
        entry.put("status", result.getResponse().getStatus());
        entry.set("body", body(result));
        return entry;
    }

    /** The file, as the running server answers today. */
    private String live() throws Exception {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("_generated", ApiSurface.regenerateCommand() + " -- do not edit by hand");
        root.put("_holds", "invented lists, invented workplaces and an invented member only -- nothing of SASBO's (MD57)");
        ObjectNode answers = root.putObject("answers");
        for (Example example : ANSWERS) {
            invented();
            recorded(answers, example.key(), example, perform(example));
        }
        ObjectNode refusals = root.putObject("refusals");
        for (Map.Entry<MonthEndRefusalCode, Example> refusal : REFUSALS.entrySet()) {
            invented();
            recorded(refusals, refusal.getKey().name(), refusal.getValue(), perform(refusal.getValue()));
        }
        DefaultPrettyPrinter printer = new DefaultPrettyPrinter()
                .withObjectIndenter(new DefaultIndenter("  ", "\n"))
                .withArrayIndenter(new DefaultIndenter("  ", "\n"));
        return objectMapper.writer(printer).writeValueAsString(root) + "\n";
    }

    @Test
    @DisplayName("the committed golden answers are exactly what the server answers from the invented rows")
    void theCommittedExamplesAreTheServers() throws Exception {
        String live = live();
        if (ApiSurface.writeRequested()) {
            ApiSurface.write(FILE, live);
        }
        assertThat(ApiSurface.readCommittedNormalised(FILE))
                .as("%s is STALE. The client's handlers and the deck's fixtures are built from it, so while it is "
                        + "stale they answer as this server no longer does. Regenerate: %s, and copy it to the client",
                        FILE_NAME, ApiSurface.regenerateCommand())
                .isEqualTo(live);
    }

    @Test
    @DisplayName("MV-33, ME-12: every refusal of the arc is provoked through its route, and answers its own code and status -- the identity sentence nowhere")
    void everyRefusalIsProvoked() throws Exception {
        assertThat(REFUSALS.keySet())
                .as("a refusal code with no request that provokes it through its route")
                .containsExactlyInAnyOrder(MonthEndRefusalCode.values());
        for (Map.Entry<MonthEndRefusalCode, Example> refusal : REFUSALS.entrySet()) {
            invented();
            MvcResult result = perform(refusal.getValue());
            assertThat(result.getResponse().getStatus()).as(refusal.getKey().name())
                    .isEqualTo(refusal.getKey().status().value());
            assertThat(body(result).path("code").asText()).isEqualTo(refusal.getKey().name());
            // ME-12: the global handler answers every broken constraint "most often a member with the same identity
            // number"; a constraint this arc can break is a refusal of its own, and that sentence is never one.
            assertThat(result.getResponse().getContentAsString()).as(refusal.getKey().name())
                    .doesNotContainIgnoringCase("identity number");
        }
    }

    @Test
    @DisplayName("🔒 MD57, MV-52: the golden answers name no workplace of SASBO's -- no R/A number, no name of the resource")
    void theExamplesNameNothingOfSasbos() throws IOException {
        String examples = ApiSurface.readCommitted(FILE);
        Set<String> found = sasbosIn(examples, resourceWorkplaces());
        assertThat(found).as("names and R/A numbers of SASBO's workplaces in %s", FILE_NAME).isEmpty();
    }

    @Test
    @DisplayName("canary: the scan finds a workplace of the resource planted in an answer")
    void theScanCanFail() throws IOException {
        List<String[]> resource = resourceWorkplaces();
        String[] planted = resource.get(resource.size() / 2);
        String answer = "{\"raNumber\": " + planted[0] + ", \"name\": \"" + planted[1] + "\"}";
        assertThat(sasbosIn(answer, resource)).contains(planted[0], planted[1].toUpperCase(Locale.ROOT));
    }

    /** Every workplace of the resource as {R/A number, name}. */
    static List<String[]> resourceWorkplaces() throws IOException {
        List<String[]> found = new ArrayList<>();
        try (InputStream stream = new ClassPathResource(WorkplaceSeeder.RESOURCE).getInputStream()) {
            for (JsonNode w : new ObjectMapper().readTree(stream).path("workplaces")) {
                found.add(new String[] {w.path("raNumber").asText(), w.path("name").asText()});
            }
        }
        return found;
    }

    /**
     * The R/A numbers and names of the resource that appear in a text -- where a workplace's identity can stand: the
     * value of an {@code raNumber}, and the value of a {@code name}. Not every word: a vocabulary word such as a table
     * code's part, {@code CASH}, is also the whole name of one of SASBO's workplaces, and is no workplace of theirs.
     */
    static Set<String> sasbosIn(String text, List<String[]> resource) throws IOException {
        Set<String> found = new TreeSet<>();
        JsonNode tree = new ObjectMapper().readTree(text);
        Set<String> numbers = new TreeSet<>();
        Set<String> strings = new TreeSet<>();
        collect(tree, numbers, strings);
        for (String[] workplace : resource) {
            if (numbers.contains(workplace[0])) {
                found.add(workplace[0]);
            }
            if (strings.contains(workplace[1].toUpperCase(Locale.ROOT))) {
                found.add(workplace[1].toUpperCase(Locale.ROOT));
            }
        }
        return found;
    }

    private static void collect(JsonNode node, Set<String> numbers, Set<String> strings) {
        if (node.isObject()) {
            node.fields().forEachRemaining(field -> {
                if (field.getKey().equals("raNumber") && field.getValue().isIntegralNumber()) {
                    numbers.add(field.getValue().asText());
                } else if (field.getKey().toLowerCase(Locale.ROOT).endsWith("name") && field.getValue().isTextual()) {
                    strings.add(field.getValue().asText().toUpperCase(Locale.ROOT));
                }
            });
        }
        node.forEach(child -> collect(child, numbers, strings));
    }
}
