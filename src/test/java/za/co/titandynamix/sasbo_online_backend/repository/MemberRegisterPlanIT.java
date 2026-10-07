package za.co.titandynamix.sasbo_online_backend.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import za.co.titandynamix.sasbo_online_backend.repository.MemberRegisterRepository.Filter;
import za.co.titandynamix.sasbo_online_backend.repository.MemberRegisterRepository.Order;
import za.co.titandynamix.sasbo_online_backend.repository.MemberRegisterRepository.Statement;
import za.co.titandynamix.sasbo_online_backend.security.CallerScope;
import za.co.titandynamix.sasbo_online_backend.support.AbstractPostgresIT;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 🔴 <b>Register row A10 -- the register's plan on 120,000 generated members: the shape, never a time.</b>
 *
 * <h2>What is proved</h2>
 * Every statement the register issues -- a page in each of its orders either way, a page twenty deep, the count, the
 * search box's every branch, each indexed criterion, a caller limited to an area and to a region, and the figure strip's
 * reads -- is planned by PostgreSQL, after {@code VACUUM ANALYZE}, with <b>no sequential scan of {@code members}</b>; and
 * where one index of V68 is the answer, the plan reads it. So a page costs the same at 120,000 members as at 300.
 *
 * <h2>The shape, never a time (MR-PERF-1)</h2>
 * A time on a shared runner is a flake by construction, so nothing here asserts a millisecond. Each statement's time is
 * MEASURED all the same -- {@code EXPLAIN ANALYZE}, printed beside its plan -- for the record a report quotes, never for
 * a verdict.
 *
 * <h2>The statements are the repository's, bound as it binds them</h2>
 * {@link MemberRegisterRepository#pageStatement} and its siblings -- never the SQL typed out again, which would prove a
 * copy. The values are bound, so PostgreSQL plans with them as it does for a request.
 *
 * <h2>Not proved here, and why</h2>
 * A page deep in the register -- thousands of rows down -- is read by OFFSET, and past a point PostgreSQL rightly reads
 * the table and sorts it instead: the screen pages forward and back and has no jump to the end, so a deep page costs a
 * clerk hundreds of presses first. Title, race, identity type and initials have no index (V68 says why).
 *
 * <h2>In a database of its own (BR68)</h2>
 * As {@code MemberSummaryPlanIT}'s: created beside the shared one in the lane's container, built by the whole migration
 * chain, and dropped {@code WITH (FORCE)} at the end whatever happened -- so no sibling ever reads these rows.
 *
 * <h2>The population, invented</h2>
 * 120,000 members: membership numbers in order; surnames whose first two letters spread them over 676 starts, each
 * ending {@code invented}; seven statuses in SASBO's proportions after a few years (seven in ten Active); twelve banks,
 * forty branches; 900 workplaces in thirty areas of six regions, one member in fifty at none; joiners every day for
 * thirty years; saves spread over the year. Every value is invented -- no row of anyone's.
 */
@DisplayName("A10: the register's plan on 120,000 generated members, in a probe database of its own")
class MemberRegisterPlanIT extends AbstractPostgresIT {

    private static final String PROBE_DATABASE = "a10_register_probe";

    static final int MEMBERS = 120_000;

    /** The one table no read of the register may scan in sequence. */
    private static final Set<String> WATCHED = Set.of("members");

    private static final ObjectMapper JSON = new ObjectMapper();

    private static final CallerScope ALL = CallerScope.all();
    private static final CallerScope AREA = CallerScope.areas(Set.of("P7"));
    private static final CallerScope REGION = CallerScope.regions(Set.of("PR3"));

    /** A member of the population, by number, and what the generator gave them. */
    private static final int N = 4242;
    private static final String WHOLE_ID = "9" + String.format("%012d", N);
    private static final String LAST_FOUR = String.format("%04d", N % 10_000);
    private static final String PHONE_LAST_FOUR = String.format("%04d", hashOf(N) % 10_000);

    private static SingleConnectionDataSource source;
    private static JdbcTemplate probe;

    /**
     * One statement the register issues, the indexes of V68 any of which its plan may read (none named: any plan without a
     * sequential scan), and its name. Most have one answer; where two indexes answer at near the same cost both are
     * named -- PostgreSQL chooses by statistics ANALYZE samples, and naming one of two would be a coin toss (run
     * 37541928372 chose members_register_scope for the whole register's branches, an index-only read of it whole).
     */
    record Case(String name, Statement statement, Set<String> indexes) {
        Case(String name, Statement statement, String index) {
            this(name, statement, Set.of(index));
        }

        @Override
        public String toString() {
            return this.name;
        }
    }

    private static int hashOf(int n) {
        return (int) ((n * 2654435761L) % 1_000_003L);
    }

    private static Filter q(String q) {
        return new Filter(q, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);
    }

    private static Filter criterion(String name, String value) {
        Map<String, String> asked = new HashMap<>();
        asked.put(name, value);
        return new Filter(null, asked.get("status"), asked.get("bank"), asked.get("branch"), null, null,
                asked.get("surname"), asked.get("employeeNumber"), asked.get("membershipNumber"), null,
                asked.get("identityNumber"), asked.get("bankAccountNumber"), asked.get("phone"),
                asked.get("personalEmail"), asked.get("workEmail"), null);
    }

    private static String indexOf(Order order) {
        return switch (order) {
            case SURNAME -> "members_register_surname";
            case MEMBERSHIP_NUMBER -> "members_register_number";
            case BANK -> "members_register_bank";
            case BRANCH -> "members_register_branch";
            case STATUS -> "members_register_status";
        };
    }

    /** Every statement the register can issue, as a request issues it. */
    static Stream<Case> everyStatementTheRegisterIssues() {
        List<Case> cases = new ArrayList<>();
        for (Order order : Order.values()) {
            for (boolean descending : List.of(false, true)) {
                cases.add(new Case("the first page by " + order.key() + (descending ? ", backwards" : ""),
                        MemberRegisterRepository.pageStatement(Filter.NONE, ALL, order, descending, 0, 50),
                        indexOf(order)));
            }
        }
        cases.add(new Case("a page twenty deep, by surname",
                MemberRegisterRepository.pageStatement(Filter.NONE, ALL, Order.SURNAME, false, 20, 50),
                "members_register_surname"));
        cases.add(new Case("the register's count", MemberRegisterRepository.countStatement(Filter.NONE, ALL), Set.of()));

        // The search box: each of its branches, a page and its count.
        for (String[] search : List.of(
                new String[] {"the start of a surname", "mq", "members_search_surname"},
                new String[] {"the start of a membership number", "m-00012", "members_search_number"},
                new String[] {"a membership number without its M-", "000123", "members_search_number"},
                new String[] {"the start of an employee number", "e1234", "members_search_employee"},
                new String[] {"an identity number's last four", LAST_FOUR, "members_search_identity"},
                new String[] {"a whole identity number", WHOLE_ID, "members_search_identity"})) {
            cases.add(new Case("search: " + search[0], MemberRegisterRepository.pageStatement(q(search[1]), ALL,
                    Order.SURNAME, false, 0, 50), search[2]));
            cases.add(new Case("search: " + search[0] + ", its count",
                    MemberRegisterRepository.countStatement(q(search[1]), ALL), search[2]));
        }

        // The criteria with an index, a page in surname order and -- for the three the criteria row shows -- the count.
        for (String[] criterion : List.of(
                new String[] {"status", "Suspended", "members_register_status"},
                new String[] {"bank", "INVENTED BANK 07", "members_register_bank"},
                new String[] {"branch", "INVENTED BRANCH 17", "members_register_branch"})) {
            Filter filter = criterion(criterion[0], criterion[1]);
            cases.add(new Case("criterion: " + criterion[0], MemberRegisterRepository.pageStatement(filter, ALL,
                    Order.SURNAME, false, 0, 50), criterion[2]));
            cases.add(new Case("criterion: " + criterion[0] + ", its count",
                    MemberRegisterRepository.countStatement(filter, ALL), criterion[2]));
        }
        for (String[] criterion : List.of(
                new String[] {"surname", "mq", "members_search_surname"},
                new String[] {"membershipNumber", "000123", "members_search_number"},
                new String[] {"employeeNumber", "e1234", "members_search_employee"},
                new String[] {"identityNumber", LAST_FOUR, "members_search_identity"},
                new String[] {"identityNumber", WHOLE_ID, "members_search_identity"},
                new String[] {"bankAccountNumber", LAST_FOUR, "members_search_account"},
                new String[] {"bankAccountNumber", String.format("%11d", N).replace(' ', '7'), "members_search_account"},
                new String[] {"phone", PHONE_LAST_FOUR, "members_search_phone"},
                new String[] {"personalEmail", "member" + N + "@", "members_search_personal_email"},
                new String[] {"workEmail", "member" + N + "@w", "members_search_work_email"})) {
            cases.add(new Case("criterion: " + criterion[0] + " " + (criterion[1].length() == 4 ? "by its last four"
                    : "by its start or whole"), MemberRegisterRepository.pageStatement(
                    criterion(criterion[0], criterion[1]), ALL, Order.SURNAME, false, 0, 50), criterion[2]));
        }

        // A caller limited to an area, and to a region.
        cases.add(new Case("an area's first page", MemberRegisterRepository.pageStatement(Filter.NONE, AREA,
                Order.SURNAME, false, 0, 50), Set.of()));
        cases.add(new Case("an area's count", MemberRegisterRepository.countStatement(Filter.NONE, AREA), Set.of()));
        cases.add(new Case("a region's first page", MemberRegisterRepository.pageStatement(Filter.NONE, REGION,
                Order.SURNAME, false, 0, 50), Set.of()));
        cases.add(new Case("a region's count", MemberRegisterRepository.countStatement(Filter.NONE, REGION), Set.of()));
        cases.add(new Case("a region's search", MemberRegisterRepository.pageStatement(q("mq"), REGION,
                Order.SURNAME, false, 0, 50), "members_search_surname"));

        // The figure strip.
        cases.add(new Case("figures: by status", MemberRegisterRepository.statusCountsStatement(ALL), Set.of()));
        cases.add(new Case("figures: a region's by status", MemberRegisterRepository.statusCountsStatement(REGION),
                "members_register_scope"));
        cases.add(new Case("figures: the month's joiners", MemberRegisterRepository.joinedStatement(ALL,
                LocalDate.of(2025, 9, 1), LocalDate.of(2025, 10, 1), "Debit Order"), "members_join_date"));
        cases.add(new Case("figures: a region's joiners", MemberRegisterRepository.joinedStatement(REGION,
                LocalDate.of(2025, 9, 1), LocalDate.of(2025, 10, 1), "Debit Order"), "members_join_date"));
        cases.add(new Case("figures: the day's saves", MemberRegisterRepository.updatedStatement(ALL,
                LocalDateTime.of(2026, 10, 6, 0, 0), LocalDateTime.of(2026, 10, 7, 0, 0)), "members_updated_at"));
        cases.add(new Case("figures: the branches", MemberRegisterRepository.branchesStatement(ALL, 500),
                Set.of("members_register_branch", "members_register_scope")));
        cases.add(new Case("figures: a region's branches", MemberRegisterRepository.branchesStatement(REGION, 500),
                "members_register_scope"));
        return cases.stream();
    }

    // ── the probe ─────────────────────────────────────────────────────────────────────────────────────────────

    @BeforeAll
    static void aGeneratedRegister() {
        JdbcTemplate shared = jdbcFor(POSTGRES.getJdbcUrl());
        shared.execute("DROP DATABASE IF EXISTS " + PROBE_DATABASE + " WITH (FORCE)");
        shared.execute("CREATE DATABASE " + PROBE_DATABASE);
        Flyway.configure()
                .dataSource(probeUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .load()
                .migrate();

        // ONE connection, so that the session's settings hold for every statement that follows.
        source = new SingleConnectionDataSource(probeUrl(), POSTGRES.getUsername(), POSTGRES.getPassword(), true);
        probe = new JdbcTemplate(source);
        // The server's thirty seconds fail a runaway test fast; building the register is not one, and it is not what
        // is measured. Bounded still: a build that hangs fails in minutes, not at the job's ceiling.
        probe.execute("SET statement_timeout = '5min'");

        long started = System.nanoTime();
        for (String step : POPULATION) {
            probe.execute(step);
        }
        long built = System.nanoTime();
        probe.execute("VACUUM ANALYZE");
        long analysed = System.nanoTime();
        System.out.printf("A10 probe: %d members built in %d ms, VACUUM ANALYZE in %d ms; members %s with its indexes%n",
                MEMBERS, (built - started) / 1_000_000, (analysed - built) / 1_000_000,
                probe.queryForObject("SELECT pg_size_pretty(pg_total_relation_size('members'))", String.class));
    }

    @AfterAll
    static void dropIt() {
        try {
            if (source != null) {
                source.destroy();
            }
        } finally {
            jdbcFor(POSTGRES.getJdbcUrl()).execute("DROP DATABASE IF EXISTS " + PROBE_DATABASE + " WITH (FORCE)");
        }
    }

    // ── the proof ─────────────────────────────────────────────────────────────────────────────────────────────

    @ParameterizedTest(name = "{0}")
    @MethodSource("everyStatementTheRegisterIssues")
    @DisplayName("🔒 A10: no read of the register scans members in sequence -- each measured, never judged by its time")
    void noReadScansMembersInSequence(Case asked) {
        List<PlanNode> nodes = walk(explain(asked.statement(), false));
        List<PlanNode> sequential = nodes.stream().filter(PlanNode::scansAWatchedTableInSequence).toList();
        JsonNode analysed = explainAnalyse(asked.statement());
        System.out.printf("A10 plan %s: %s -- %s rows in %.2f ms%n", asked,
                nodes.stream().filter(PlanNode::readsAWatchedTable).map(PlanNode::scan).toList(),
                analysed.path("Plan").path("Actual Rows").asText(), analysed.path("Execution Time").asDouble());

        assertThat(sequential)
                .as("%s: every read of the register reads members through an index -- this plan scans it in sequence "
                        + "(the node, its path from the top, and its JSON)", asked)
                .isEmpty();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("everyStatementTheRegisterIssues")
    @DisplayName("🔒 A10: where an index of V68 is the answer, the plan reads it")
    void thePlanReadsItsIndex(Case asked) {
        if (asked.indexes().isEmpty()) {
            return;
        }
        List<PlanNode> nodes = walk(explain(asked.statement(), false));

        assertThat(nodes.stream().map(PlanNode::index).filter(Objects::nonNull).toList())
                .as("%s: the indexes this plan reads -- none of %s is among them; what reads members is %s", asked,
                        asked.indexes(), nodes.stream().filter(n -> "members".equals(n.relation()))
                                .map(PlanNode::toString).toList())
                .containsAnyElementsOf(asked.indexes());
    }

    @Test
    @DisplayName("canary: a count no index can answer IS found scanning members in sequence -- the walker walks")
    void theWalkerFindsASequentialScan() {
        // Race has no index (V68 says why): the only plan is a scan of the table. A walker that found nothing here would
        // make every green above a walker that looks at no node.
        List<PlanNode> nodes = walk(explain(new Statement("SELECT count(*) FROM members WHERE race = 'African'",
                new MapSqlParameterSource()), false));

        assertThat(nodes).filteredOn(PlanNode::scansAWatchedTableInSequence)
                .extracting(PlanNode::relation).containsOnly("members");
    }

    @Test
    @DisplayName("must-pass: the walker reads the index a plan uses -- members_join_date, for one day's joiners")
    void theWalkerReadsTheIndexAPlanUses() {
        List<PlanNode> nodes = walk(explain(new Statement("SELECT count(*) FROM members WHERE join_date = DATE "
                + "'2001-06-01'", new MapSqlParameterSource()), false));

        assertThat(nodes).extracting(PlanNode::index).contains("members_join_date");
        assertThat(nodes).noneMatch(PlanNode::scansAWatchedTableInSequence);
    }

    @Test
    @DisplayName("must-pass: the population is the one the proof names -- 120,000 members, spread as described")
    void thePopulationIsTheOneNamed() {
        assertThat(probe.queryForObject("SELECT count(*) FROM members", Long.class)).isEqualTo(MEMBERS);
        assertThat(probe.queryForObject("SELECT count(DISTINCT status) FROM members", Long.class)).isEqualTo(6);
        assertThat(probe.queryForObject("SELECT count(*) FROM members WHERE status = 'Active'", Long.class))
                .as("seven in ten Active").isBetween(MEMBERS * 65L / 100, MEMBERS * 75L / 100);
        assertThat(probe.queryForObject("SELECT count(*) FROM workplaces", Long.class)).isEqualTo(900);
        assertThat(probe.queryForObject("SELECT count(*) FROM members WHERE workplace_id IS NULL", Long.class))
                .as("one in fifty at no workplace").isBetween(MEMBERS / 70L, MEMBERS / 35L);
        assertThat(probe.queryForObject("SELECT count(DISTINCT substr(lower(surname), 1, 2)) FROM members",
                Long.class)).as("676 starts of a surname").isEqualTo(676);
        // Each case's subject is in the population: a search that finds nobody proves less.
        assertThat(probe.queryForObject("SELECT count(*) FROM members WHERE lower(surname) LIKE 'mq%'", Long.class))
                .isPositive();
        assertThat(probe.queryForObject("SELECT count(*) FROM members WHERE identity_number = ?", Long.class, WHOLE_ID))
                .isEqualTo(1);
        assertThat(probe.queryForObject("SELECT count(*) FROM members WHERE right(phone, 4) = ?", Long.class,
                PHONE_LAST_FOUR)).isPositive();
        assertThat(probe.queryForObject("SELECT count(*) FROM members WHERE join_date >= DATE '2025-09-01' "
                + "AND join_date < DATE '2025-10-01'", Long.class)).isPositive();
        assertThat(probe.queryForObject("SELECT count(*) FROM members WHERE updated_at >= TIMESTAMP '2026-10-06 "
                + "00:00:00' AND updated_at < TIMESTAMP '2026-10-07 00:00:00'", Long.class)).isPositive();
    }

    // ── the walker ────────────────────────────────────────────────────────────────────────────────────────────

    /** One node of a plan, with the path of nodes above it -- what an assertion names when it fails. */
    record PlanNode(JsonNode node, String path) {

        String type() {
            return this.node.path("Node Type").asText();
        }

        String relation() {
            return this.node.has("Relation Name") ? this.node.get("Relation Name").asText() : null;
        }

        String index() {
            return this.node.has("Index Name") ? this.node.get("Index Name").asText() : null;
        }

        /** A parallel scan is a {@code Seq Scan} with {@code "Parallel Aware": true} -- the same node type. */
        boolean scansAWatchedTableInSequence() {
            return "Seq Scan".equals(type()) && readsAWatchedTable();
        }

        boolean readsAWatchedTable() {
            return relation() != null && WATCHED.contains(relation());
        }

        String scan() {
            return type() + (index() == null ? "" : " using " + index()) + " on " + relation();
        }

        @Override
        public String toString() {
            ObjectNode own = this.node.deepCopy();
            own.remove("Plans");
            return this.path + " -- " + own;
        }
    }

    /** {@code EXPLAIN (FORMAT JSON)} of a statement, bound as the repository binds it: the plan's top node. */
    private static JsonNode explain(Statement statement, boolean analyse) {
        String plan = new NamedParameterJdbcTemplate(probe).queryForObject(
                (analyse ? "EXPLAIN (ANALYZE, FORMAT JSON) " : "EXPLAIN (FORMAT JSON) ") + statement.sql(),
                statement.arguments(), String.class);
        try {
            JsonNode top = JSON.readTree(plan).get(0);
            return analyse ? top : top.get("Plan");
        } catch (JsonProcessingException unreadable) {
            throw new IllegalStateException("EXPLAIN answered something that is not its JSON", unreadable);
        }
    }

    /** The statement run, with its plan and its time: the measurement printed, never asserted. */
    private static JsonNode explainAnalyse(Statement statement) {
        return explain(statement, true);
    }

    /** Every node of the plan, depth first -- InitPlans, SubPlans and CTEs are children like any other. */
    static List<PlanNode> walk(JsonNode top) {
        List<PlanNode> nodes = new ArrayList<>();
        walk(top, "", nodes);
        return nodes;
    }

    private static void walk(JsonNode node, String above, List<PlanNode> into) {
        String here = above.isEmpty() ? describe(node) : above + " > " + describe(node);
        into.add(new PlanNode(node, here));
        for (JsonNode child : node.path("Plans")) {
            walk(child, here, into);
        }
    }

    private static String describe(JsonNode node) {
        String subplan = node.has("Subplan Name") ? "[" + node.get("Subplan Name").asText() + "] " : "";
        String index = node.has("Index Name") ? " using " + node.get("Index Name").asText() : "";
        String relation = node.has("Relation Name")
                ? " on " + node.get("Relation Name").asText() + " " + node.path("Alias").asText() : "";
        return subplan + node.path("Node Type").asText() + index + relation;
    }

    // ── the population ────────────────────────────────────────────────────────────────────────────────────────

    /**
     * The generated register, in SQL and never row by row. {@code h} is a fixed hash of the member's number, so every
     * run generates the same members (ANALYZE still samples them, as it does in production).
     */
    private static final List<String> POPULATION = Stream.of(
            "INSERT INTO sasbo_regions (code, name) SELECT 'PR' || r, 'INVENTED REGION ' || r FROM generate_series(1, 6) r",
            """
            INSERT INTO sasbo_areas (letter, region_id)
            SELECT 'P' || a, (SELECT id FROM sasbo_regions WHERE code = 'PR' || (1 + (a - 1) % 6))
            FROM generate_series(1, 30) a
            """,
            """
            INSERT INTO workplaces (ra_number, name, employer_printed, area_id, committee_code, staff_complement, cl_code)
            SELECT 800000 + w, 'INVENTED WORKPLACE ' || w, 'INVENTED EMPLOYER',
                   (SELECT id FROM sasbo_areas WHERE letter = 'P' || (1 + (w - 1) % 30)), 'Q01', 10, '00000000'
            FROM generate_series(1, 900) w
            """,
            """
            INSERT INTO members (membership_number, employee_number, title, initials, first_names, surname,
                                 identity_type, identity_number, status, join_date, personal_email, work_email, phone,
                                 bank_name, bank_account_number, branch, race, payment_type, workplace_id, created_at,
                                 updated_at)
            SELECT 'M-' || lpad(s.n::text, 6, '0'),
                   'E' || (100000 + s.h % 900000),
                   (ARRAY['Mr', 'Ms', 'Mrs', 'Dr'])[1 + s.h % 4],
                   chr(65 + (s.h / 7) % 26) || chr(65 + (s.h / 11) % 26),
                   'Invented' || chr(97 + (s.h / 17) % 26) || chr(97 + (s.h / 19) % 26) || ' Synthetic',
                   chr(65 + s.h % 26) || chr(97 + (s.h / 26) % 26) || chr(97 + (s.h / 676) % 26) || 'invented',
                   CASE WHEN s.n % 33 = 0 THEN 'Passport' ELSE 'I.D. Number' END,
                   '9' || lpad(s.n::text, 12, '0'),
                   CASE WHEN s.h % 100 < 70 THEN 'Active' WHEN s.h % 100 < 78 THEN 'Suspended'
                        WHEN s.h % 100 < 80 THEN 'Pending' WHEN s.h % 100 < 87 THEN 'Resigned'
                        WHEN s.h % 100 < 94 THEN 'Retired' ELSE 'Terminated' END,
                   DATE '1996-01-01' + (s.n % 11000),
                   'member' || s.n || '@personal.invented.example',
                   'member' || s.n || '@work.invented.example',
                   '+27 60 ' || lpad((s.n % 1000)::text, 3, '0') || ' ' || lpad((s.h % 10000)::text, 4, '0'),
                   'INVENTED BANK ' || lpad((1 + (s.h / 3) % 12)::text, 2, '0'),
                   lpad(s.n::text, 11, '7'),
                   'INVENTED BRANCH ' || lpad((1 + (s.h / 5) % 40)::text, 2, '0'),
                   (ARRAY['African', 'Coloured', 'Indian', 'White'])[1 + (s.h / 13) % 4],
                   CASE WHEN s.h % 3 = 0 THEN 'Payroll Deduction' ELSE 'Debit Order' END,
                   w.id,
                   TIMESTAMP '2026-01-01 00:00:00',
                   TIMESTAMP '2026-01-01 08:00:00' + (s.h % 280) * INTERVAL '1 day'
            FROM (SELECT g.n, ((g.n::bigint * 2654435761) % 1000003)::int AS h
                  FROM generate_series(1, {MEMBERS}) AS g(n)) s
            LEFT JOIN workplaces w ON w.ra_number = 800001 + s.h % 900 AND s.h % 50 <> 7
            """).map(step -> step.replace("{MEMBERS}", String.valueOf(MEMBERS))).toList();

    // ── the machinery ─────────────────────────────────────────────────────────────────────────────────────────

    private static String probeUrl() {
        return "jdbc:postgresql://" + POSTGRES.getHost() + ":"
                + POSTGRES.getMappedPort(PostgreSQLContainer.POSTGRESQL_PORT) + "/" + PROBE_DATABASE;
    }

    private static JdbcTemplate jdbcFor(String url) {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(url);
        dataSource.setUsername(POSTGRES.getUsername());
        dataSource.setPassword(POSTGRES.getPassword());
        return new JdbcTemplate(dataSource);
    }
}
