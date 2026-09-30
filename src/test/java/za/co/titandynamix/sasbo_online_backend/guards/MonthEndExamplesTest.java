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
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import za.co.titandynamix.sasbo_online_backend.exception.MonthEndRefusalCode;
import za.co.titandynamix.sasbo_online_backend.support.ApiSurface;
import za.co.titandynamix.sasbo_online_backend.utils.DataSeeder;
import za.co.titandynamix.sasbo_online_backend.utils.WorkplaceSeeder;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * THE GOLDEN ANSWERS (month end, MD48, MV-32): {@value #FILE_NAME} -- a real answer for each route of the arc and an
 * envelope for each refusal, made by the running server and copied to the client byte for byte. The client's test
 * handlers are built from it, and the deck's fixtures are cut from it, so the three agree on the server's day and not
 * on the day somebody typed a fixture.
 *
 * <h2>🔒 Invented, and nothing of SASBO's (MD57, MV-52)</h2>
 * The answers are made from invented lists and invented workplaces written here -- the start's own seeding is stood
 * down -- because the file leaves the server's repository: it is copied into the client, and from there onto slides.
 * A scan holds it to that: no R/A number and no workplace name of the resource appears in it, and its canary reddens.
 *
 * <p>Written by {@code ./gradlew generateApiSurface}, which runs this class with {@code -DapiSurface.write=true};
 * checked in the fast lane, like the other contract artefacts.
 */
@SpringBootTest(properties = {
        "sasbo.workplaces=all",
        "spring.datasource.url=jdbc:h2:mem:month-end-examples;MODE=PostgreSQL;DB_CLOSE_DELAY=-1"})
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("Month end's golden answers")
class MonthEndExamplesTest {

    static final String FILE_NAME = "month-end-examples.json";
    static final Path FILE = Paths.get(FILE_NAME);

    /** What every envelope's {@code timestamp} is written as: the one value of the file that is the clock's. */
    static final String TIMESTAMP = "2026-09-29T08:00:00";

    /** Every route of the arc that answers, and the request that shows it. */
    static final Map<String, String> ANSWERS = answers();

    /** Every refusal code, and a request that provokes it through its route (MV-33). */
    static final Map<MonthEndRefusalCode, String> REFUSALS = refusals();

    private static Map<String, String> answers() {
        Map<String, String> routes = new LinkedHashMap<>();
        for (String list : List.of("regions", "areas", "employer-groups", "employers", "table-codes",
                "committee-provinces", "committees", "suspension-codes", "status")) {
            routes.put("GET /api/reference/" + list, "/api/reference/" + list);
        }
        routes.put("GET /api/workplaces", "/api/workplaces?page=0&size=2");
        return routes;
    }

    private static Map<MonthEndRefusalCode, String> refusals() {
        Map<MonthEndRefusalCode, String> provoked = new LinkedHashMap<>();
        provoked.put(MonthEndRefusalCode.REQUEST_MALFORMED, "/api/workplaces?page=first");
        provoked.put(MonthEndRefusalCode.WORKPLACE_PAGE_INVALID, "/api/workplaces?page=9&size=2");
        return provoked;
    }

    /** Stands the start's seeding down: nothing of SASBO's is on this database, only what {@link #invented} writes. */
    @MockitoBean
    private DataSeeder noSeedOfSasbos;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void invented() {
        Long present = jdbc.queryForObject("SELECT count(*) FROM sasbo_regions WHERE code = 'NORTH'", Long.class);
        if (present != null && present > 0) {
            return;
        }
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
    }

    private void workplace(int ra, String name, String employer, Integer tableCode, String by, String area,
                           String committee, int complement) {
        jdbc.update("INSERT INTO workplaces (ra_number, name, employer_printed, table_code_id, table_code_by, area_id, "
                + "committee_code, committee_id, staff_complement, cl_code) VALUES (?, ?, ?, "
                + "(SELECT id FROM table_codes WHERE code = ?), ?, (SELECT id FROM sasbo_areas WHERE letter = ?), ?, "
                + "(SELECT id FROM committees WHERE code = ?), ?, '00000000')",
                ra, name, employer, tableCode, by, area, committee, committee, complement);
    }

    private JsonNode body(MvcResult result) throws IOException {
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        if (body instanceof ObjectNode object && object.has("timestamp")) {
            object.put("timestamp", TIMESTAMP);
        }
        return body;
    }

    /** The file, as the running server answers today. */
    private String live() throws Exception {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("_generated", ApiSurface.regenerateCommand() + " -- do not edit by hand");
        root.put("_holds", "invented lists and invented workplaces only -- nothing of SASBO's (MD57)");
        ObjectNode answers = root.putObject("answers");
        for (Map.Entry<String, String> route : ANSWERS.entrySet()) {
            MvcResult result = mockMvc.perform(get(route.getValue())).andReturn();
            ObjectNode example = answers.putObject(route.getKey());
            example.put("request", "GET " + route.getValue());
            example.put("status", result.getResponse().getStatus());
            example.set("body", body(result));
        }
        ObjectNode refusals = root.putObject("refusals");
        for (Map.Entry<MonthEndRefusalCode, String> refusal : REFUSALS.entrySet()) {
            MvcResult result = mockMvc.perform(get(refusal.getValue())).andReturn();
            ObjectNode example = refusals.putObject(refusal.getKey().name());
            example.put("request", "GET " + refusal.getValue());
            example.put("status", result.getResponse().getStatus());
            example.set("body", body(result));
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
    @DisplayName("MV-33: every refusal of the arc is provoked through its route, and answers its own code and status")
    void everyRefusalIsProvoked() throws Exception {
        assertThat(REFUSALS.keySet())
                .as("a refusal code with no request that provokes it through its route")
                .containsExactlyInAnyOrder(MonthEndRefusalCode.values());
        for (Map.Entry<MonthEndRefusalCode, String> refusal : REFUSALS.entrySet()) {
            MvcResult result = mockMvc.perform(get(refusal.getValue())).andReturn();
            assertThat(result.getResponse().getStatus()).as(refusal.getKey().name())
                    .isEqualTo(refusal.getKey().status().value());
            assertThat(body(result).path("code").asText()).isEqualTo(refusal.getKey().name());
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
