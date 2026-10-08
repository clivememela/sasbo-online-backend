package za.co.titandynamix.sasbo_online_backend.guards;

import com.fasterxml.jackson.core.util.DefaultIndenter;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import za.co.titandynamix.sasbo_online_backend.acb.AcbTime;
import za.co.titandynamix.sasbo_online_backend.fixtures.AcbRunFixture;
import za.co.titandynamix.sasbo_online_backend.support.ApiSurface;
import za.co.titandynamix.sasbo_online_backend.support.ConnectionStatements;
import za.co.titandynamix.sasbo_online_backend.utils.DemonstrationMemberSeeder;
import za.co.titandynamix.sasbo_online_backend.utils.DemonstrationPopulation;
import za.co.titandynamix.sasbo_online_backend.utils.DemonstrationPopulationGenerator;
import za.co.titandynamix.sasbo_online_backend.utils.DemonstrationPopulationSeeder;
import za.co.titandynamix.sasbo_online_backend.utils.ReferenceListSeeder;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * THE DEMONSTRATION ON THE DAY OF THE SHOW (the October build, demo-data): {@value #FILE_NAME} -- what the screens the
 * seed is for answer over a freshly seeded demonstration database at Wednesday 14 October 2026, 09:30 in Johannesburg,
 * the next Show &amp; Tell. The presentation's slides run on fixtures, not on a live stack; this is where they are cut
 * from, so a slide shows what the server answers and not what somebody typed.
 *
 * <p>And the proof of the ask itself -- the owner, 5 Oct: <em>"we don't want to be seeing zeros"</em>: every figure
 * those screens show is listed below and none reads nought -- except October's own joined and left, which the seed
 * leaves free on purpose for a live capture and for the suite, and which are pinned at nought so a slide knows it.
 *
 * <h2>🔒 Nothing of SASBO's workplaces, nobody of SASBO's staff (MD57; BRIEF section 3)</h2>
 * The members are invented, but they stand at SASBO's real workplaces. So an answer that names a workplace is either
 * left out (the visits sheet, the workplaces' pages) or masked (each register row's branch, workplace and R/A number
 * become an invented label), and the recruiters -- SASBO's own organisers -- are numbered, never named. A scan holds
 * the file to that, and its canary reddens. Nor does it carry an identity number's digits (POPIA): an invented one is
 * still a valid one, and the client refuses one in any committed file -- counted here, with a canary of its own.
 *
 * <p>Written by {@code ./gradlew generateApiSurface}, which runs this class with {@code -DapiSurface.write=true};
 * checked in the fast lane like the other contract artefacts. The clock is frozen, and the only wall-clock values of
 * an answer (a member's {@code createdAt}/{@code updatedAt}, stamped by JPA) are written as the clock's.
 */
// @Isolated: it seeds a whole start of its own and pins statements at the connection, as MonthEndSeedTest does.
@Isolated
@SpringBootTest(properties = {
        "sasbo.workplaces=all",
        "spring.datasource.url=jdbc:h2:mem:demo-answers;MODE=PostgreSQL;DB_CLOSE_DELAY=-1"})
@AutoConfigureMockMvc(addFilters = false)
@Import(DemoAnswersTest.TheShowAndTell.class)
@DisplayName("The demonstration's answers on 14 Oct 2026 -- and no figure reading nought")
class DemoAnswersTest {

    static final String FILE_NAME = "demo-answers-2026-10-14.json";
    static final Path FILE = Paths.get("src", "test", "resources", "demo", FILE_NAME);

    /** Wednesday 14 October 2026, 09:30 in Johannesburg. */
    static final Instant NOW = Instant.parse("2026-10-14T07:30:00Z");

    /** What a JPA-stamped instant of an answer is written as: the clock's, in SASBO's zone. */
    static final String STAMPED = "2026-10-14T09:30:00";

    /** How many register rows are recorded: its first page. The paged route arrives with register-paging (A10). */
    static final int REGISTER_PAGE = 25;

    /**
     * What a register row's identity number is recorded as. An invented identity number is still a valid one, check
     * digit and all, and the client refuses one in any committed file (POPIA, noCommittedMemberData): the answer says
     * a number is held, never its digits -- as month end's golden answers do (MonthEndExamplesTest).
     */
    static final String AN_IDENTITY_NUMBER = "<an invented identity number>";
    static final String AN_ACCOUNT_NUMBER = "<an invented account number>";

    /**
     * The clock, what Flyway would have seeded before any runner on a real database (as SeedStartupTest's: the rate,
     * the unpaid codes, the calendar), and the count of statements at the connection.
     */
    @TestConfiguration
    static class TheShowAndTell {

        @Bean
        @Primary
        Clock theShowAndTellsClock() {
            return Clock.fixed(NOW, AcbTime.ZONE);
        }

        @Bean
        @Order(Ordered.HIGHEST_PRECEDENCE)
        ApplicationRunner whatFlywayWouldHaveSeeded(JdbcTemplate jdbc) {
            return args -> {
                AcbRunFixture.insertRate(jdbc, LocalDate.of(2026, 1, 1), new BigDecimal("174.00"));
                jdbc.update("INSERT INTO acb_unpaid_codes (code, reason, wording, inferred, source) VALUES "
                        + "('2', 'NOT_PROVIDED_FOR', 'Not Provided For', FALSE, 'V45, on H2'), "
                        + "('4', 'PAYMENT_STOPPED', 'Payment Stopped', FALSE, 'V45, on H2')");
                AcbRunFixture.insertCalendar(jdbc);
            };
        }

        @Bean
        static BeanPostProcessor countStatementsAtTheConnection() {
            return ConnectionStatements.register();
        }

        /**
         * The figures' and the statistics' four-second bound lifted, in this context only. The bound is production's,
         * on PostgreSQL (MD47), and holds there by the plans MemberSummaryPlanIT proves. On H2 the figures' statement
         * runs its live half even for a taken month, where PostgreSQL skips it, so on a busy machine the same request
         * answers 200 one run and 503 the next (7 Oct, September by region). A golden answer is what the server
         * answers, never how fast the machine was. MonthEndSeedTest's MV-16 still runs the statement under its bound.
         */
        @Bean
        static BeanPostProcessor withoutQueryTimeouts() {
            return new BeanPostProcessor() {
                @Override
                public Object postProcessAfterInitialization(Object bean, String name) {
                    return bean instanceof javax.sql.DataSource dataSource && !(bean instanceof WithoutQueryTimeouts)
                            ? new WithoutQueryTimeouts(dataSource) : bean;
                }
            };
        }
    }

    /**
     * A data source whose statements ignore {@code setQueryTimeout}. A delegate, not a JDK proxy, so that
     * {@link ConnectionStatements} -- which wraps any data source that is not one -- still counts every statement,
     * whichever of the two post-processors runs first.
     */
    static final class WithoutQueryTimeouts extends org.springframework.jdbc.datasource.DelegatingDataSource {

        WithoutQueryTimeouts(javax.sql.DataSource target) {
            super(target);
        }

        @Override
        public java.sql.Connection getConnection() throws java.sql.SQLException {
            return untimed(super.getConnection());
        }

        @Override
        public java.sql.Connection getConnection(String username, String password) throws java.sql.SQLException {
            return untimed(super.getConnection(username, password));
        }

        private static java.sql.Connection untimed(java.sql.Connection connection) {
            return (java.sql.Connection) java.lang.reflect.Proxy.newProxyInstance(
                    java.sql.Connection.class.getClassLoader(), new Class<?>[] {java.sql.Connection.class},
                    (proxy, method, args) -> {
                        Object result = forward(connection, method, args);
                        return result instanceof java.sql.Statement statement ? untimed(statement) : result;
                    });
        }

        private static java.sql.Statement untimed(java.sql.Statement statement) {
            Class<?>[] types = statement instanceof java.sql.CallableStatement
                    ? new Class<?>[] {java.sql.CallableStatement.class}
                    : statement instanceof java.sql.PreparedStatement
                            ? new Class<?>[] {java.sql.PreparedStatement.class}
                            : new Class<?>[] {java.sql.Statement.class};
            return (java.sql.Statement) java.lang.reflect.Proxy.newProxyInstance(
                    java.sql.Statement.class.getClassLoader(), types, (proxy, method, args) ->
                            method.getName().equals("setQueryTimeout") ? null : forward(statement, method, args));
        }

        private static Object forward(Object target, java.lang.reflect.Method method, Object[] args) throws Throwable {
            if (method.getName().equals("unwrap") && args != null && args.length == 1
                    && args[0] instanceof Class<?> type && type.isInstance(target)) {
                return target;
            }
            try {
                return method.invoke(target, args);
            } catch (java.lang.reflect.InvocationTargetException thrown) {
                throw thrown.getCause();
            }
        }
    }

    /** The months of the demonstration's past, as the seed takes them: 07:10 SAST on each last working day. */
    static final Map<String, String> TAKEN = Map.of(
            "2026-07", "2026-07-31T05:10:00Z",
            "2026-08", "2026-08-31T05:10:00Z",
            "2026-09", "2026-09-30T05:10:00Z");

    /** What is recorded: each a screen's GET, the request as the client sends it. */
    static final List<String> ASKED = asked();

    private static List<String> asked() {
        List<String> asked = new ArrayList<>();
        // Month end: the months, September's record, the first screen's figures -- October as it stands and September
        // as taken, by group, by region (the packs) and by area.
        asked.add("/api/month-end/months");
        asked.add("/api/month-end/2026-09");
        asked.add("/api/members/summary");
        asked.add("/api/members/summary?month=2026-09");
        asked.add("/api/members/summary?month=2026-09&by=region");
        asked.add("/api/members/summary?month=2026-09&by=area");
        // September's statistics, read from what was frozen when it was taken -- all but the visits sheet, which names
        // SASBO's workplaces (MD57). September is the month the Month end page opens on (the newest taken) and the one
        // the deck shows; the open month's statistics are the slowest statement on H2, and are left to the live link.
        for (String statistic : List.of("new-and-deleted", "membership", "leavers", "branches", "recruitment",
                "representatives")) {
            asked.add("/api/month-end/2026-09/" + statistic);
        }
        // The register's first page, and the debit order runs.
        asked.add("/api/members");
        asked.add("/api/acb/runs?page=0&size=50");
        return List.copyOf(asked);
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private DemonstrationPopulationSeeder population;

    @Autowired
    private PlatformTransactionManager transactions;

    /** The answers, asked once for the class: the database is the same for every test of it. */
    private static ObjectNode answers;

    private synchronized ObjectNode answers() throws Exception {
        if (answers == null) {
            answers = live();
        }
        return answers;
    }

    // ================================================================================================ the file

    /** The file, as the running server answers on the day. */
    private ObjectNode live() throws Exception {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("_generated", ApiSurface.regenerateCommand() + " -- do not edit by hand");
        root.put("_holds", "the screens' answers over a freshly seeded demonstration database: invented members only, "
                + "SASBO's workplaces masked (MD57), SASBO's recruiters numbered");
        root.put("_clock", NOW + " -- Wednesday 14 October 2026, 09:30 in Johannesburg");
        ArrayNode masked = root.putArray("_masked");
        masked.add("GET /api/members: the first " + REGISTER_PAGE + " members by membership number (the paged register "
                + "arrives with register-paging); each row's branch, workplaceName, workplaceRaNumber and workplaceId "
                + "are an invented label, number and id; identityNumber and bankAccountNumber say a number is held, "
                + "never its digits (POPIA); createdAt and updatedAt are the clock's");
        masked.add("recruitment, sheet by-recruiter: each recruiter is 'Recruiter n', in the sheet's order");
        masked.add("left out: the visits statistic and the workplaces' pages, which name SASBO's workplaces");
        ObjectNode recorded = root.putObject("answers");
        for (String path : ASKED) {
            MvcResult result = mockMvc.perform(get(path)).andReturn();
            JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
            if (path.equals("/api/members")) {
                body = registerPage((ArrayNode) body);
            }
            if (path.endsWith("/recruitment")) {
                numberTheRecruiters(body);
            }
            ObjectNode entry = recorded.putObject("GET " + path);
            entry.put("status", result.getResponse().getStatus());
            entry.set("body", body);
        }
        return root;
    }

    private String rendered(ObjectNode root) throws IOException {
        DefaultPrettyPrinter printer = new DefaultPrettyPrinter()
                .withObjectIndenter(new DefaultIndenter("  ", "\n"))
                .withArrayIndenter(new DefaultIndenter("  ", "\n"));
        return objectMapper.writer(printer).writeValueAsString(root) + "\n";
    }

    /** The register's first page by membership number, each row's workplace an invented label (MD57). */
    private ArrayNode registerPage(ArrayNode all) {
        List<JsonNode> rows = new ArrayList<>();
        all.forEach(rows::add);
        rows.sort((a, b) -> a.path("membershipNumber").asText().compareTo(b.path("membershipNumber").asText()));
        Map<String, Integer> labels = new LinkedHashMap<>();
        ArrayNode page = objectMapper.createArrayNode();
        for (JsonNode row : rows.subList(0, Math.min(REGISTER_PAGE, rows.size()))) {
            ObjectNode copy = row.deepCopy();
            String workplace = copy.path("workplaceId").isNull() || copy.path("workplaceId").isMissingNode() ? null
                    : copy.path("workplaceId").asText();
            if (workplace != null) {
                int n = labels.computeIfAbsent(workplace, w -> labels.size() + 1);
                copy.put("workplaceId", n);
                copy.put("workplaceName", "INVENTED BRANCH " + n);
                copy.put("workplaceRaNumber", 900_000 + n);
                copy.put("branch", "INVENTED BRANCH " + n);
            } else {
                copy.put("branch", "INVENTED BRANCH");
            }
            if (copy.has("workplace") && !copy.path("workplace").isNull()) {
                copy.putNull("workplace");
            }
            if (copy.hasNonNull("recruitmentRepresentativeName")) {
                copy.put("recruitmentRepresentativeName", "INVENTED REPRESENTATIVE");
            }
            if (copy.hasNonNull("identityNumber")) {
                copy.put("identityNumber", AN_IDENTITY_NUMBER);
            }
            if (copy.hasNonNull("bankAccountNumber")) {
                copy.put("bankAccountNumber", AN_ACCOUNT_NUMBER);
            }
            copy.put("createdAt", STAMPED);
            copy.put("updatedAt", STAMPED);
            page.add(copy);
        }
        return page;
    }

    /** SASBO's recruiters are its own staff: numbered in the sheet's order, never named. */
    private static void numberTheRecruiters(JsonNode statistic) {
        for (JsonNode sheet : statistic.path("sheets")) {
            if (!sheet.path("key").asText().equals("by-recruiter")) {
                continue;
            }
            int n = 0;
            for (JsonNode row : sheet.path("rows")) {
                if (!row.path("key").asText().startsWith("~")) {
                    n++;
                    ((ObjectNode) row).put("key", "R" + n);
                    ((ObjectNode) row).put("label", "Recruiter " + n);
                }
            }
        }
    }

    @Test
    @DisplayName("the committed answers are exactly what the server answers over the seeded demonstration on the day")
    void theCommittedAnswersAreTheServers() throws Exception {
        String live = rendered(answers());
        if (ApiSurface.writeRequested()) {
            ApiSurface.write(FILE, live);
        }
        assertThat(ApiSurface.readCommittedNormalised(FILE))
                .as("%s is STALE: the presentation's slides are cut from it. Regenerate: %s", FILE_NAME,
                        ApiSurface.regenerateCommand())
                .isEqualTo(live);
        for (String path : ASKED) {
            assertThat(answers().path("answers").path("GET " + path).path("status").asInt()).as(path).isEqualTo(200);
        }
    }

    // ================================================================================================ no zeros

    private JsonNode body(String path) throws Exception {
        return answers().path("answers").path("GET " + path).path("body");
    }

    private static void positive(JsonNode figures, String what, String... keys) {
        for (String key : keys) {
            assertThat(figures.path(key).decimalValue()).as("%s: %s", what, key).isPositive();
        }
    }

    @Test
    @DisplayName("5 Oct, \"we don't want to be seeing zeros\": every figure of the first screen, the months and the debit order runs reads more than nought -- October's joined and left nought on purpose")
    void theFiguresReadNoNought() throws Exception {
        JsonNode months = body("/api/month-end/months");
        assertThat(months.path("openMonth").asText()).isEqualTo("2026-10");
        Map<String, String> taken = new LinkedHashMap<>();
        months.path("taken").forEach(m -> taken.put(m.path("month").asText(), m.path("takenAt").asText()));
        assertThat(taken).as("the three past months, each taken at 07:10 on its last working day").containsAllEntriesOf(TAKEN);

        JsonNode october = body("/api/members/summary").path("totals");
        positive(october, "October, as it stands", "onRecord", "suspended", "paying", "complement", "penetration",
                "joinedYearToDate", "leftYearToDate");
        assertThat(october.path("joined").asLong()).as("October's joiners: the clock's month is left free for a live "
                + "capture and for the suite, so a slide shows September's").isZero();
        assertThat(october.path("left").asLong()).as("October's leavers, likewise").isZero();

        JsonNode september = body("/api/members/summary?month=2026-09");
        positive(september.path("totals"), "September", "onRecord", "suspended", "paying", "joined", "left",
                "reinstated", "complement", "penetration", "joinedYearToDate", "leftYearToDate");
        assertThat(september.path("takenAt").asText()).isEqualTo(TAKEN.get("2026-09"));

        // Every pack's region and every lettered area with members, joiners and leavers in September.
        for (JsonNode row : body("/api/members/summary?month=2026-09&by=region").path("rows")) {
            String key = row.path("key").asText();
            if (key.startsWith("~") || key.equals("MISC")) {
                continue;
            }
            positive(row.path("figures"), "September, region " + key, "onRecord", "paying", "joined", "left");
        }
        Set<String> areas = new TreeSet<>();
        for (JsonNode row : body("/api/members/summary?month=2026-09&by=area").path("rows")) {
            String key = row.path("key").asText();
            if (key.startsWith("~") || key.equals("Q")) {
                continue;
            }
            areas.add(key);
            positive(row.path("figures"), "September, area " + key, "onRecord", "paying", "joined", "left");
        }
        assertThat(areas).as("every lettered area with a region").hasSize(23);

        assertThat(body("/api/acb/runs?page=0&size=50").path("content")).as("the debit order runs").isNotEmpty();
    }

    @Test
    @DisplayName("every statistic of September has movement in it: joiners and leavers, the suspended under every code of Stephne's sheet, leavers under deletion codes, recruits, representatives")
    void septembersStatisticsReadNoNought() throws Exception {
        assertThat(total("new-and-deleted", "month-joined")).as("joined in September").isPositive();
        assertThat(total("new-and-deleted", "month-left")).as("left in September").isPositive();
        assertThat(total("new-and-deleted", "year-joined")).as("joined, the year to date").isPositive();
        assertThat(total("new-and-deleted", "year-left")).as("left, the year to date").isPositive();
        assertThat(total("leavers", "by-area")).as("why members left").isPositive();
        assertThat(total("leavers", "joined-deleted")).isPositive();
        assertThat(total("branches", "by-area")).as("members at the branches").isPositive();
        assertThat(total("recruitment", "by-channel")).as("recruits").isPositive();
        assertThat(total("representatives", "representatives")).as("representatives").isPositive();
        assertThat(total("representatives", "committee-members")).as("committee members").isPositive();
        assertThat(total("representatives", "memnet-members")).as("Memnet members").isPositive();

        Set<String> codes = new TreeSet<>();
        for (JsonNode row : sheet("membership", "suspended-by-code").path("rows")) {
            if (row.path("cells").get(0).asLong() > 0) {
                codes.add(row.path("key").asText());
            }
        }
        assertThat(codes).as("the suspended at September's end, by code -- every code of Stephne's sheet")
                .containsAll(List.of("11", "12", "13", "15", "16", "18", "19", "21", "30", "31", "32", "33"));

        JsonNode recruiters = sheet("recruitment", "by-recruiter");
        assertThat(recruiters.path("total").path("cells").get(recruiters.path("total").path("cells").size() - 2)
                .asLong()).as("recruits credited to recruiters in September").isPositive();
    }

    @Test
    @DisplayName("the 5 Oct sheet that came out short -- Membership by group (complement, members, enrolments, cancellations, suspensions): every group shown, none 'fewer than five', none withheld, the core groups' line there")
    void theFifthOfOctobersSheetHidesNothing() throws Exception {
        JsonNode byGroup = sheet("membership", "by-group");
        List<String> hidden = new ArrayList<>();
        Set<String> groups = new HashSet<>();
        boolean coreLine = false;
        for (JsonNode row : byGroup.path("rows")) {
            if (row.path("fewerThanFive").asBoolean() || row.path("withheld").asBoolean()) {
                hidden.add(row.path("label").asText());
            }
            if (row.path("level").asText().equals("GROUP")) {
                groups.add(row.path("label").asText());
                assertThat(row.path("cells").get(1).asLong()).as("%s: members", row.path("label").asText())
                        .isPositive();
            }
            coreLine |= row.path("key").asText().equals("~CORE");
        }
        assertThat(hidden).as("rows rule C hid -- on 5 Oct a group of one member hid itself and the next group").isEmpty();
        assertThat(groups).as("all nine employer groups").hasSize(9);
        assertThat(coreLine).as("the core groups' line, left out wherever a group is hidden").isTrue();
        List<Long> totals = new ArrayList<>();
        byGroup.path("total").path("cells").forEach(c -> totals.add(c.asLong()));
        assertThat(totals).as("complement, members, enrolments, cancellations, suspensions -- every total")
                .allSatisfy(n -> assertThat(n).isPositive());
    }

    private JsonNode sheet(String statistic, String key) throws Exception {
        for (JsonNode sheet : body("/api/month-end/2026-09/" + statistic).path("sheets")) {
            if (sheet.path("key").asText().equals(key)) {
                return sheet;
            }
        }
        throw new AssertionError("no sheet " + key + " in " + statistic);
    }

    private long total(String statistic, String key) throws Exception {
        JsonNode cells = sheet(statistic, key).path("total").path("cells");
        long sum = 0;
        for (JsonNode cell : cells) {
            sum += cell.isNull() ? 0 : cell.asLong();
        }
        return sum;
    }

    // ================================================================================================ the seed

    @Test
    @DisplayName("the population is on record as its file says: every member at its workplace, every past change by the seed, Stephne's codes on record")
    void thePopulationIsOnRecord() throws IOException {
        DemonstrationPopulation file = DemonstrationPopulationSeeder.readSeedFile();
        List<String> numbers = file.members().stream().map(DemonstrationPopulation.Member::membershipNumber).toList();
        Map<String, Integer> standing = new LinkedHashMap<>();
        jdbc.query("SELECT m.membership_number, w.ra_number FROM members m JOIN workplaces w ON w.id = m.workplace_id "
                + "WHERE m.membership_number BETWEEN 'M-096001' AND 'M-098500'", rs -> {
                    standing.put(rs.getString(1), rs.getInt(2));
                });
        assertThat(standing).hasSize(file.count());
        file.members().forEach(m -> assertThat(standing).containsEntry(m.membershipNumber(), m.workplaceRaNumber()));
        Long changes = jdbc.queryForObject("SELECT count(*) FROM member_status_changes c JOIN members m ON m.id = "
                + "c.member_id WHERE c.changed_by = ? AND m.membership_number BETWEEN 'M-096001' AND 'M-098500'",
                Long.class, DemonstrationMemberSeeder.SEED_ACTOR);
        assertThat(changes).isEqualTo(file.past().size());
        assertThat(jdbc.queryForList("SELECT code FROM suspension_codes", Integer.class))
                .containsAll(file.suspensionCodes().stream().map(DemonstrationPopulation.Code::code).toList());
        assertThat(numbers).allSatisfy(n -> assertThat(n).isBetween("M-096001", "M-098500"));
    }

    @Test
    @DisplayName("🔒 no real data: every surname and first name on record in the block comes from the generator's invented lists")
    void everySeededNameIsInvented() {
        Set<String> firstNames = new HashSet<>(DemonstrationPopulationGenerator.FEMALE);
        firstNames.addAll(DemonstrationPopulationGenerator.MALE);
        List<Map<String, Object>> names = jdbc.queryForList("SELECT first_names, surname FROM members "
                + "WHERE membership_number BETWEEN 'M-096001' AND 'M-098500'");
        assertThat(names).isNotEmpty().allSatisfy(row -> {
            assertThat(DemonstrationPopulationGenerator.SURNAMES).contains((String) row.get("SURNAME"));
            assertThat(firstNames).contains((String) row.get("FIRST_NAMES"));
        });
    }

    @Test
    @DisplayName("🔒 MD44, MR-PERF-19: a second start is two statements, counted at the connection, and writes nothing")
    void aSecondStartWritesNothing() throws IOException {
        Long before = jdbc.queryForObject("SELECT count(*) FROM members", Long.class);
        int[] wrote = new int[1];
        long statements = ConnectionStatements.during(() -> {
            try {
                wrote[0] = population.seed();
            } catch (IOException unreadable) {
                throw new java.io.UncheckedIOException(unreadable);
            }
        });
        assertThat(wrote[0]).isZero();
        assertThat(statements).as("one read of the file's numbers on record, one of whose past the seed has written")
                .isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM members", Long.class)).isEqualTo(before);
    }

    @Test
    @DisplayName("🔒 MD44, 03.4: a first start puts the members on record in one batch and writes the past through changeStatus -- never a round trip a member")
    void aFirstStartIsBatched() {
        DemonstrationPopulation file = readQuietly();
        // In a transaction rolled back after -- H2 has no trigger -- the population taken away, then seeded again.
        Long[] measured = new TransactionTemplate(transactions).execute(status -> {
            status.setRollbackOnly();
            jdbc.update("DELETE FROM member_status_changes WHERE member_id IN (SELECT id FROM members "
                    + "WHERE membership_number BETWEEN 'M-096001' AND 'M-098500')");
            jdbc.update("DELETE FROM members WHERE membership_number BETWEEN 'M-096001' AND 'M-098500'");
            int[] wrote = new int[1];
            long statements = ConnectionStatements.during(() -> {
                try {
                    wrote[0] = population.seed();
                } catch (IOException unreadable) {
                    throw new java.io.UncheckedIOException(unreadable);
                }
            });
            return new Long[] {statements, (long) wrote[0]};
        });
        assertThat(measured[1]).as("members and changes written").isEqualTo(file.count() + file.past().size());
        // The members: one batch (and a handful of reads around it). The past: through changeStatus, the one writer of
        // a member's status (03.4) -- a lock, the change and the status, a few statements a change.
        assertThat(measured[0]).as("statements for %d members and %d changes", file.count(), file.past().size())
                .isLessThanOrEqualTo(12L + 4L * file.past().size())
                .isLessThan((long) file.count());
    }

    private static DemonstrationPopulation readQuietly() {
        try {
            return DemonstrationPopulationSeeder.readSeedFile();
        } catch (IOException unreadable) {
            throw new java.io.UncheckedIOException(unreadable);
        }
    }

    // ================================================================================================ MD57

    @Test
    @DisplayName("🔒 MD57, BRIEF 3: the answers name no workplace of SASBO's and no recruiter of SASBO's")
    void theAnswersNameNothingOfSasbos() throws IOException {
        assertThat(java.nio.file.Files.isRegularFile(FILE)).as("%s does not exist. Run %s", FILE,
                ApiSurface.regenerateCommand()).isTrue();
        String answersFile = ApiSurface.readCommitted(FILE);
        assertThat(sasbosIn(answersFile)).as("SASBO's workplaces and recruiters in %s", FILE_NAME).isEmpty();
    }

    @Test
    @DisplayName("canary: the scan finds a workplace's name, its R/A number and a recruiter's name planted in an answer")
    void theScanCanFail() throws IOException {
        List<String[]> workplaces = MonthEndExamplesTest.resourceWorkplaces();
        Set<String> vocabulary = vocabulary();
        String[] planted = workplaces.stream().filter(w -> !vocabulary.contains(w[1].toUpperCase(Locale.ROOT)))
                .skip(workplaces.size() / 3).findFirst().orElseThrow();
        String recruiter = recruiterNames().iterator().next();
        String answer = "{\"rows\": [{\"workplaceRaNumber\": " + planted[0] + ", \"branch\": \"" + planted[1]
                + "\", \"label\": \"" + recruiter + "\"}]}";
        assertThat(sasbosIn(answer)).contains(planted[0], planted[1].toUpperCase(Locale.ROOT),
                recruiter.toUpperCase(Locale.ROOT));
    }

    @Test
    @DisplayName("🔒 POPIA: the answers carry no South African identity number -- the client refuses one in any committed file")
    void theAnswersCarryNoIdentityNumber() throws IOException {
        assertThat(identityNumbersIn(ApiSurface.readCommitted(FILE)))
                .as("identity numbers in %s -- counted, never printed", FILE_NAME).isZero();
    }

    @Test
    @DisplayName("canary: the count finds one of the population's own identity numbers planted in an answer, and no other thirteen digits")
    void theIdentityNumberCountCanFail() throws IOException {
        String planted = DemonstrationPopulationSeeder.readSeedFile().members().get(0).identityNumber();
        assertThat(identityNumbersIn("{\"rows\": [{\"identityNumber\": \"" + planted + "\"}]}")).isEqualTo(1);
        assertThat(identityNumbersIn("{\"onRecord\": 1234567890123}")).as("thirteen digits, no identity number").isZero();
    }

    /** As the client's guard counts them: each run of thirteen digits that is a valid South African identity number. */
    static int identityNumbersIn(String text) {
        java.util.regex.Matcher thirteen = java.util.regex.Pattern.compile("\\d{13}").matcher(text);
        int found = 0;
        while (thirteen.find()) {
            found += MonthEndExamplesTest.isSaIdNumber(thirteen.group()) ? 1 : 0;
        }
        return found;
    }

    /**
     * What of SASBO's a text names: an R/A number of the resource under a key that holds one; a workplace's name of the
     * resource as ANY string value -- unless the same word is also a name of SASBO's lists (an employer, a table code,
     * a group, a committee), which a figure names anyway; and a recruiter's name, unless it is also a channel's.
     */
    static Set<String> sasbosIn(String text) throws IOException {
        JsonNode tree = new ObjectMapper().readTree(text);
        Set<String> numbers = new TreeSet<>();
        Set<String> strings = new TreeSet<>();
        collect(tree, numbers, strings);
        Set<String> found = new TreeSet<>();
        Set<String> vocabulary = vocabulary();
        for (String[] workplace : MonthEndExamplesTest.resourceWorkplaces()) {
            String name = workplace[1].toUpperCase(Locale.ROOT);
            if (numbers.contains(workplace[0])) {
                found.add(workplace[0]);
            }
            if (strings.contains(name) && !vocabulary.contains(name)) {
                found.add(name);
            }
        }
        for (String recruiter : recruiterNames()) {
            if (strings.contains(recruiter.toUpperCase(Locale.ROOT))) {
                found.add(recruiter.toUpperCase(Locale.ROOT));
            }
        }
        return found;
    }

    private static void collect(JsonNode node, Set<String> numbers, Set<String> strings) {
        if (node.isObject()) {
            node.fields().forEachRemaining(field -> {
                String key = field.getKey().toLowerCase(Locale.ROOT);
                if ((key.equals("ranumber") || key.equals("workplaceranumber")) && field.getValue().isIntegralNumber()) {
                    numbers.add(field.getValue().asText());
                }
            });
        }
        if (node.isTextual()) {
            strings.add(node.asText().trim().toUpperCase(Locale.ROOT));
        }
        node.forEach(child -> collect(child, numbers, strings));
    }

    /** The names of SASBO's lists: a word that is one of them is named by a figure, and is no workplace's identity. */
    private static Set<String> vocabulary() throws IOException {
        Set<String> words = new HashSet<>();
        try (InputStream stream = new ClassPathResource(ReferenceListSeeder.RESOURCE).getInputStream()) {
            for (JsonNode list : new ObjectMapper().readTree(stream).path("lists")) {
                for (JsonNode row : list.path("rows")) {
                    for (String key : List.of("name", "code", "letter", "part")) {
                        if (row.path(key).isTextual()) {
                            words.add(row.path(key).asText().trim().toUpperCase(Locale.ROOT));
                        }
                    }
                }
            }
        }
        return words;
    }

    /** SASBO's recruiters, less any name that is also a recruitment channel's (a channel is no person). */
    private static Set<String> recruiterNames() throws IOException {
        Set<String> channels = new HashSet<>();
        try (InputStream stream = new ClassPathResource("data/recruiter.json").getInputStream()) {
            new ObjectMapper().readTree(stream).forEach(c -> channels.add(c.path("name").asText().toUpperCase(Locale.ROOT)));
        }
        Set<String> names = new TreeSet<>();
        try (InputStream stream = new ClassPathResource("data/recruiters.json").getInputStream()) {
            new ObjectMapper().readTree(stream).forEach(r -> {
                String name = r.path("name").asText().trim();
                if (!name.isEmpty() && !channels.contains(name.toUpperCase(Locale.ROOT))) {
                    names.add(name);
                }
            });
        }
        return names;
    }
}
