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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import za.co.titandynamix.sasbo_online_backend.acb.AcbTime;
import za.co.titandynamix.sasbo_online_backend.exception.MonthEndRefusalCode;
import za.co.titandynamix.sasbo_online_backend.fixtures.MemberFixture;
import za.co.titandynamix.sasbo_online_backend.security.CallerScope;
import za.co.titandynamix.sasbo_online_backend.security.Permission;
import za.co.titandynamix.sasbo_online_backend.services.MonthEndTakes;
import za.co.titandynamix.sasbo_online_backend.support.ApiSurface;
import za.co.titandynamix.sasbo_online_backend.support.SignInTestUsers;
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
                   boolean packInHand) {
        Example(String method, String path, String body) {
            this(method, path, body, false, false, false);
        }

        Example(String method, String path, String body, boolean gateHeld) {
            this(method, path, body, gateHeld, false, false);
        }

        Example(String method, String path, String body, boolean gateHeld, boolean withoutWorkplaces) {
            this(method, path, body, gateHeld, withoutWorkplaces, false);
        }

        String key() {
            return method + " " + path.replaceAll("\\?.*$", "").replace("{member}", "{id}").replace("{pack}", "{id}")
                    .replace("{workplace}", "{id}")
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
        // 04.3 (sign-in): a listing of the invented August -- held by MEMBERS_VIEW, asked by a caller who holds it.
        routes.add(new Example("GET", "/api/month-end/" + TAKEN + "/listings/new-members", null));
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
        return routes;
    }

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
        // 04.2: the take says what it knows of the month before it writes -- and a past month not taken.
        String take = "/api/month-end/%s/take";
        provoked.put(MonthEndRefusalCode.MONTH_NOT_BEGUN, new Example("POST", take.formatted("2026-10"), null));
        provoked.put(MonthEndRefusalCode.MONTH_ALREADY_TAKEN, new Example("POST", take.formatted(TAKEN), null));
        // 04.11: a take again that names a take which is not the month's newest -- said before the take refuses.
        provoked.put(MonthEndRefusalCode.MONTH_TAKEN_SINCE, new Example("POST", take.formatted(TAKEN),
                "{\"replaces\": 999999}"));
        provoked.put(MonthEndRefusalCode.MONTH_NOT_READY, new Example("POST", take.formatted("2026-07"), null, false,
                true));
        provoked.put(MonthEndRefusalCode.MONTH_NOT_TAKEN, new Example("GET", "/api/month-end/2026-07", null));
        // 04.10: a pack asked for while another month's is in hand -- there is no queue -- and a pack that is not there.
        provoked.put(MonthEndRefusalCode.PACK_BUSY, new Example("POST", "/api/month-end/" + TAKEN + "/pack", null, false,
                false, true));
        provoked.put(MonthEndRefusalCode.PACK_NOT_FOUND, new Example("GET", "/api/month-end/packs/999999", null));
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
        MockHttpServletRequestBuilder request = switch (example.method()) {
            case "POST" -> example.body() == null ? post(path)
                    : post(path).contentType(MediaType.APPLICATION_JSON).content(example.body());
            case "PUT" -> put(path).contentType(MediaType.APPLICATION_JSON).content(example.body());
            default -> get(path);
        };
        Permission held = heldBy(example);
        if (held != null) {
            // A route held by a permission (October build) is asked by a caller holding it -- this class runs without
            // the filters, so the caller is set on the thread, as the filters would have set it.
            return SignInTestUsers.actingAs(SignInTestUsers.user("demo.headoffice", CallerScope.all(), held),
                    () -> ask(example, request));
        }
        return ask(example, request);
    }

    /** Asks {@code request} as {@code example} says: in a rolled-back transaction, with the gate held, or plainly. */
    private MvcResult ask(Example example, MockHttpServletRequestBuilder request) throws Exception {
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

    /** The permission a route of the arc is held by (October build): the take, a listing; {@code null} for the rest. */
    private static Permission heldBy(Example example) {
        if (example.path().matches("/api/month-end/[^/]+/take")) {
            return Permission.MONTH_END_TAKE;
        }
        return example.path().matches("/api/month-end/[^/]+/listings/.*") ? Permission.MEMBERS_VIEW : null;
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
        if (body instanceof ObjectNode object && "PACK_BUSY".equals(object.path("code").asText())
                && object.path("details") instanceof ObjectNode details && details.has("pack")) {
            details.put("pack", "1");
        }
        return body;
    }

    private ObjectNode recorded(ObjectNode into, String key, Example example, MvcResult result) throws IOException {
        ObjectNode entry = into.putObject(key);
        entry.put("request", example.method() + " " + example.path().replace("{member}", "{id}")
                .replace("{pack}", "{id}"));
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
