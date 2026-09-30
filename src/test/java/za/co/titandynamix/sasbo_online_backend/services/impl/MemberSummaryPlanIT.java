package za.co.titandynamix.sasbo_online_backend.services.impl;

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
import za.co.titandynamix.sasbo_online_backend.enums.MemberStatusKind;
import za.co.titandynamix.sasbo_online_backend.enums.SummaryGrouping;
import za.co.titandynamix.sasbo_online_backend.repository.MemberSummaryRepository;
import za.co.titandynamix.sasbo_online_backend.support.AbstractPostgresIT;
import za.co.titandynamix.sasbo_online_backend.utils.MemberStatuses;
import za.co.titandynamix.sasbo_online_backend.utils.MonthRange;

import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 🔴 <b>MV-26, MV-59 -- the figures' plan on 550,000 generated members: the shape, never a time.</b>
 *
 * <h2>What is proved</h2>
 * After a migration nine records in ten are leavers, and every figure but <i>left</i> reads the tenth (MR-PERF-10);
 * <i>joined</i> reads a date joined in the month whatever the status (rule N), <i>left</i> the month's changes (rule L)
 * -- so the figures have three indexes, and the rules decide them (MD66, MR-PERF-14). This class holds the statement a
 * request issues to them: for every grouping the summary answers, below the switch's {@code all} too, within an area
 * and a region, for the open month and for past ones, the plan PostgreSQL makes after {@code ANALYZE} has
 * <b>no sequential scan of {@code members} or of {@code member_status_changes}</b>, and the members on record are read
 * through the partial index {@code members_on_record}.
 *
 * <h2>The shape, never a time (MR-PERF-1)</h2>
 * A time on a shared runner is a flake by construction. A plan is what the database decided to do, and it is the same
 * decision on every run with the same statistics; so the proof is the plan's shape, walked node by node, and nothing
 * here asserts a millisecond or a cost. The population's build is timed only so that the lane's log says what it took.
 *
 * <h2>The statement is the repository's, bound as it binds it</h2>
 * {@link MemberSummaryRepository#statement} and {@link MemberSummaryRepository#parameters} -- never the SQL typed out
 * again, which would prove a copy. The values are bound, so PostgreSQL plans with them as it does for a request (a
 * custom plan: the month's range is known to the planner).
 *
 * <h2>In a database of its own (BR68)</h2>
 * As {@code MemberMoneyMigrationIT}'s: created beside the shared one in the lane's container, built by the whole
 * migration chain, and dropped {@code WITH (FORCE)} at the end whatever happened. Half a million rows in the shared
 * database would be truncated under a sibling's feet -- and read by every sibling that counts members. So this class
 * takes no lock on {@link za.co.titandynamix.sasbo_online_backend.support.DatabaseReset#SHARED_DATABASE}: it never
 * touches it.
 *
 * <h2>The population, invented, shaped as SASBO's after migration</h2>
 * 550,000 members, 65,000 of them on record -- Active, Suspended and Pending, as the vocabulary names the kinds -- and a
 * handful whose status nobody knows; the rest Resigned, Retired or Terminated, each with the change that made them a
 * leaver. Membership numbers are in the order members joined, from 1975, so the old numbers are mostly leavers, as a
 * key file read in order gives them; about eighty join a month in the last three years, and about ninety leave (SASBO's
 * own reports: 591 joined on seven). 5,507 workplaces for the members on record to stand at, some closed, a few
 * without a table code; table codes, employers, groups, areas, regions and committees, a handful each; suspended
 * members with the change that suspended them; a few reinstated. Every value is invented -- no row of anyone's.
 */
@DisplayName("MV-26: the figures' plan on 550,000 generated members, in a probe database of its own")
class MemberSummaryPlanIT extends AbstractPostgresIT {

    private static final String PROBE_DATABASE = "mv26_plan_probe";

    /** The tables no figure may read in sequence (MV-26: members; MV-59: their changes, for left). */
    private static final Set<String> WATCHED = Set.of("members", "member_status_changes");

    /** The partial index over the members on record (V58). */
    private static final String ON_RECORD_INDEX = "members_on_record";

    /** The month the generated population's clock is in -- the figures' fixture's, the middle of September 2026. */
    private static final YearMonth OPEN = YearMonth.of(2026, 9);

    /** Past months: the one before, and a year back -- a month whose end has a year of changes after it. */
    private static final List<YearMonth> PAST = List.of(YearMonth.of(2026, 8), YearMonth.of(2025, 9));

    private static final ObjectMapper JSON = new ObjectMapper();

    private static SingleConnectionDataSource source;
    private static JdbcTemplate probe;

    /** One statement the summary issues, named for the assertion that reads it. */
    record Case(String name, MemberSummaryRepository.Ask ask) {
        @Override
        public String toString() {
            return this.name;
        }
    }

    /** Every statement the route can issue: six groupings, below {@code all}, within an area and a region; three months. */
    static Stream<Case> everyStatementTheSummaryIssues() {
        List<Case> cases = new ArrayList<>();
        List<YearMonth> months = new ArrayList<>();
        months.add(OPEN);
        months.addAll(PAST);
        for (YearMonth month : months) {
            String when = month + (month.equals(OPEN) ? " (the open month)" : " (past)");
            for (SummaryGrouping by : SummaryGrouping.values()) {
                cases.add(new Case("by " + by.wire() + ", " + when, ask(month, by, true, null, null)));
            }
            // Below the switch's "all" the figures are the totals and one row (MD62): a statement of its own.
            cases.add(new Case("below all -- one row, " + when, ask(month, SummaryGrouping.GROUP, false, null, null)));
            cases.add(new Case("by group within area X1, " + when, ask(month, SummaryGrouping.GROUP, true, "X1", null)));
            cases.add(new Case("by area within region IR1, " + when, ask(month, SummaryGrouping.AREA, true, null, "IR1")));
        }
        return cases.stream();
    }

    /** As the service asks it where production is marked: no seed's numbers (it binds the repository's "~none"). */
    private static MemberSummaryRepository.Ask ask(YearMonth month, SummaryGrouping by, boolean grouped, String area,
                                                   String region) {
        return new MemberSummaryRepository.Ask(MonthRange.of(month), by, grouped, grouped, area, region, List.of());
    }

    // ── the probe ─────────────────────────────────────────────────────────────────────────────────────────────

    @BeforeAll
    static void aPopulationShapedAsSasbosAfterMigration() {
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
        // The server's thirty seconds fail a runaway test fast; building half a million rows is not one, and it is not
        // what is measured. Bounded still: a build that hangs fails in minutes, not at the job's ceiling.
        probe.execute("SET statement_timeout = '5min'");

        long started = System.nanoTime();
        for (String step : POPULATION) {
            probe.execute(step);
        }
        long built = System.nanoTime();
        probe.execute("ANALYZE");
        long analysed = System.nanoTime();
        System.out.printf("MV-26 probe: population built in %d ms, ANALYZE in %d ms; members %s, changes %s%n",
                (built - started) / 1_000_000, (analysed - built) / 1_000_000,
                probe.queryForObject("SELECT pg_size_pretty(pg_total_relation_size('members'))", String.class),
                probe.queryForObject("SELECT pg_size_pretty(pg_total_relation_size('member_status_changes'))",
                        String.class));
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
    @MethodSource("everyStatementTheSummaryIssues")
    @DisplayName("🔒 MV-26, MV-59: no figure scans members or their status changes in sequence")
    void noFigureScansMembersOrTheirChangesInSequence(Case asked) {
        List<PlanNode> nodes = planOf(asked);
        List<PlanNode> sequential = nodes.stream().filter(PlanNode::scansAWatchedTableInSequence).toList();
        System.out.printf("MV-26 %s: members and their changes read by %s%n", asked,
                nodes.stream().filter(PlanNode::readsAWatchedTable).map(PlanNode::scan).toList());

        assertThat(sequential)
                .as("%s: every figure reads members and member_status_changes through an index -- this plan scans"
                        + " one in sequence (the node, its path from the top, and its JSON)", asked)
                .isEmpty();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("everyStatementTheSummaryIssues")
    @DisplayName("🔒 MV-26, MR-PERF-10: the members on record are read through the partial index members_on_record")
    void theMembersOnRecordAreReadThroughThePartialIndex(Case asked) {
        List<PlanNode> nodes = planOf(asked);

        assertThat(nodes.stream().map(PlanNode::index).filter(Objects::nonNull).toList())
                .as("%s: the indexes this plan reads -- %s is not among them; what reads members is %s", asked,
                        ON_RECORD_INDEX, nodes.stream().filter(n -> "members".equals(n.relation()))
                                .map(PlanNode::toString).toList())
                .contains(ON_RECORD_INDEX);
    }

    @Test
    @DisplayName("canary: a count no index can answer IS found scanning members in sequence -- the walker walks")
    void theWalkerFindsASequentialScan() {
        // No index has the surname: the only plan is a scan of the table. A walker that found nothing here would
        // make every green above a walker that looks at no node.
        List<PlanNode> nodes = walk(explain("SELECT count(*) FROM members WHERE surname = 'INVENTED1'",
                new MapSqlParameterSource()));

        assertThat(nodes).filteredOn(PlanNode::scansAWatchedTableInSequence)
                .extracting(PlanNode::relation).containsExactly("members");
    }

    @Test
    @DisplayName("must-pass: the walker reads the index a plan uses -- members_join_date, for one day's joiners")
    void theWalkerReadsTheIndexAPlanUses() {
        // The twin of the canary for the index check: a handful of rows among half a million, on the one index that
        // leads with the date -- so a red on the partial index above is never a walker that cannot read a name.
        List<PlanNode> nodes = walk(explain("SELECT count(*) FROM members WHERE join_date = DATE '2026-09-01'",
                new MapSqlParameterSource()));

        assertThat(nodes).extracting(PlanNode::index).contains("members_join_date");
        assertThat(nodes).noneMatch(PlanNode::scansAWatchedTableInSequence);
    }

    @Test
    @DisplayName("must-pass: the population is the one the proof names -- 550,000 members, 65,000 on record by the vocabulary's kinds")
    void thePopulationIsTheOneNamed() {
        Map<String, Long> byStatus = new HashMap<>();
        for (Map<String, Object> row : probe.queryForList("SELECT status, count(*) AS n FROM members GROUP BY status")) {
            byStatus.put((String) row.get("status"), ((Number) row.get("n")).longValue());
        }
        long all = byStatus.values().stream().mapToLong(Long::longValue).sum();
        long onRecord = byStatus.entrySet().stream().filter(e -> kindIs(e.getKey(), MemberStatusKind.NEITHER)
                || kindIs(e.getKey(), MemberStatusKind.SUSPENDED)).mapToLong(Map.Entry::getValue).sum();
        long leaving = byStatus.entrySet().stream().filter(e -> kindIs(e.getKey(), MemberStatusKind.LEAVING))
                .mapToLong(Map.Entry::getValue).sum();
        long unknown = byStatus.entrySet().stream()
                .filter(e -> e.getKey() != null && MemberStatuses.kindOf(e.getKey()) == null)
                .mapToLong(Map.Entry::getValue).sum();

        assertThat(all).as("members").isEqualTo(550_000);
        assertThat(onRecord).as("on record, by the vocabulary's kinds").isEqualTo(65_000);
        assertThat(leaving).as("leavers: nine in ten").isGreaterThan(480_000);
        assertThat(unknown).as("a status nobody knows (rule X), a handful").isBetween(1L, 100L);
        assertThat(onRecord + leaving + unknown).isEqualTo(all);
        assertThat(probe.queryForObject("SELECT count(*) FROM workplaces", Long.class)).isEqualTo(5_507);
        assertThat(probe.queryForObject("SELECT count(*) FROM member_status_changes", Long.class))
                .as("a change for every leaver, at least").isGreaterThanOrEqualTo(leaving);
        // Each month under test has movement in it: a plan over a month nobody joined or left in proves less.
        NamedParameterJdbcTemplate named = new NamedParameterJdbcTemplate(probe);
        for (YearMonth month : Stream.concat(Stream.of(OPEN), PAST.stream()).toList()) {
            MapSqlParameterSource args = MemberSummaryRepository.parameters(ask(month, SummaryGrouping.GROUP, true,
                    null, null));
            assertThat(named.queryForObject("SELECT count(*) FROM members WHERE join_date >= :firstDay"
                    + " AND join_date < :firstDayOfNext", args, Long.class))
                    .as("%s: members joined in it", month).isPositive();
            assertThat(named.queryForObject("SELECT count(*) FROM member_status_changes WHERE changed_at >= :start"
                    + " AND changed_at < :next", args, Long.class))
                    .as("%s: status changes in it", month).isPositive();
        }
    }

    private static boolean kindIs(String status, MemberStatusKind kind) {
        return status != null && MemberStatuses.kindOf(status) == kind;
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

        /** Most nodes read no table at all; {@code Set.of} refuses to be asked about a null. */
        boolean readsAWatchedTable() {
            return relation() != null && WATCHED.contains(relation());
        }

        /** How this node reads its table, in a line: the scan, the index where there is one, and the table. */
        String scan() {
            return type() + (index() == null ? "" : " using " + index()) + " on " + relation();
        }

        /** The path from the top, and the node's own JSON without the nodes beneath it. */
        @Override
        public String toString() {
            ObjectNode own = this.node.deepCopy();
            own.remove("Plans");
            return this.path + " -- " + own;
        }
    }

    private static List<PlanNode> planOf(Case asked) {
        return walk(explain(MemberSummaryRepository.statement(asked.ask()),
                MemberSummaryRepository.parameters(asked.ask())));
    }

    /** {@code EXPLAIN (FORMAT JSON)} of a statement, bound as the repository binds it: the plan's top node. */
    private static JsonNode explain(String statement, MapSqlParameterSource arguments) {
        String plan = new NamedParameterJdbcTemplate(probe).queryForObject("EXPLAIN (FORMAT JSON) " + statement,
                arguments, String.class);
        try {
            return JSON.readTree(plan).get(0).get("Plan");
        } catch (JsonProcessingException unreadable) {
            throw new IllegalStateException("EXPLAIN answered something that is not its JSON", unreadable);
        }
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
     * The generated population, in SQL and never row by row. {@code h} is a fixed hash of the member's number, so every
     * run generates the same members. (ANALYZE still samples them, as it does in production.)
     */
    private static final List<String> POPULATION = List.of(
            """
            INSERT INTO sasbo_regions (code, name)
            SELECT 'IR' || r, 'INVENTED REGION ' || r FROM generate_series(1, 6) r
            """,
            """
            INSERT INTO sasbo_areas (letter, region_id)
            SELECT 'X' || a, (SELECT id FROM sasbo_regions WHERE code = 'IR' || (1 + (a - 1) % 6))
            FROM generate_series(1, 30) a
            """,
            """
            INSERT INTO employer_groups (name, core, ordinal)
            SELECT 'INVENTED GROUP ' || g, g <= 3, g FROM generate_series(1, 8) g
            """,
            """
            INSERT INTO employers (ordinal, name, group_id)
            SELECT e, 'INVENTED EMPLOYER ' || e, (SELECT id FROM employer_groups WHERE ordinal = 1 + (e - 1) % 8)
            FROM generate_series(1, 40) e
            """,
            // Every fiftieth table code has no employer: the row of those with none.
            """
            INSERT INTO table_codes (code, name, employer_id, part)
            SELECT 90000 + t, 'INVENTED TABLE CODE ' || t,
                   CASE WHEN t % 50 = 0 THEN NULL
                        ELSE (SELECT id FROM employers WHERE ordinal = 1 + (t - 1) % 40) END, NULL
            FROM generate_series(1, 150) t
            """,
            """
            INSERT INTO committee_provinces (letter, name)
            SELECT chr(64 + p), 'INVENTED PROVINCE ' || p FROM generate_series(1, 9) p
            """,
            """
            INSERT INTO committees (code, province_id, name, recruiting_number)
            SELECT chr(64 + 1 + (c - 1) % 9) || lpad(c::text, 2, '0'),
                   (SELECT id FROM committee_provinces WHERE letter = chr(64 + 1 + (c - 1) % 9)),
                   'INVENTED COMMITTEE ' || c, NULL
            FROM generate_series(1, 90) c
            """,
            // 5,507 workplaces, as SASBO's list has: every fortieth with no table code, every hundredth closed.
            """
            INSERT INTO workplaces (ra_number, name, employer_printed, table_code_id, table_code_by, area_id,
                                    committee_code, committee_id, staff_complement, cl_code, closed_on)
            SELECT 700000 + w, 'INVENTED WORKPLACE ' || w, 'INVENTED EMPLOYER', t.id,
                   CASE WHEN t.id IS NULL THEN NULL ELSE 'NAME' END, a.id, c.code, c.id, 5 + w % 33, '00000000',
                   CASE WHEN w % 100 = 0 THEN DATE '2024-06-30' END
            FROM generate_series(1, 5507) w
            LEFT JOIN table_codes t ON t.code = 90000 + 1 + (w - 1) % 150 AND w % 40 <> 0
            JOIN sasbo_areas a ON a.letter = 'X' || (1 + (w - 1) % 30)
            JOIN committees c ON c.name = 'INVENTED COMMITTEE ' || (1 + (w - 1) % 90)
            """,
            """
            INSERT INTO deletion_codes (name, sasbo_code)
            VALUES ('INVENTED LEFT SERVICE', 1), ('INVENTED RESIGNED', 2), ('INVENTED IN ARREARS', 5)
            """,
            """
            INSERT INTO suspension_codes (code, name)
            VALUES (11, 'INVENTED SUSPENSION ELEVEN'), (13, 'INVENTED SUSPENSION THIRTEEN')
            """,
            // The members, in the order they joined. On record, by number: one in two hundred of the oldest two
            // hundred thousand, one in ten of the next, seven in twenty of the next hundred thousand, nine in ten of
            // the newest five thousand -- 1,000 + 24,500 + 35,000 + 4,500 = 65,000. The members on record stand at a
            // workplace, but for one in fifty; of the leavers, only those who left in the last three years still do.
            """
            INSERT INTO members (id, membership_number, employee_number, title, initials, first_names, surname,
                                 identity_type, date_of_birth, gender, status, join_date, personal_email, work_email,
                                 preferred_email, phone, cell_tel, work_tel, address, bank_name, bank_account_number,
                                 branch, ibt_no, employment_type, payment_type, debit_day, subscription_frequency,
                                 monthly_subscription_numeric, is_representative, workplace_id, created_at, updated_at)
            SELECT p.n, 'INV' || lpad(p.n::text, 7, '0'), 'E' || p.n, 'Mx', 'I', 'INVENTED FIRST NAMES',
                   'INVENTED' || p.n, 'OTHER', COALESCE(p.joined, DATE '1990-01-01') - 9131, 'U', p.status, p.joined,
                   'member' || p.n || '@personal.example.invalid', 'member' || p.n || '@work.example.invalid',
                   'personal', '+27 00 000 ' || lpad((p.n % 10000)::text, 4, '0'),
                   '+27 00 001 ' || lpad((p.n % 10000)::text, 4, '0'), '+27 00 002 ' || lpad((p.n % 10000)::text, 4, '0'),
                   p.n || ' INVENTED STREET, INVENTED TOWN, 0000', 'INVENTED BANK', lpad(p.n::text, 10, '0'),
                   'INVENTED BRANCH', '000000', 'Permanent',
                   CASE WHEN p.h % 4 = 0 THEN 'Payroll Deduction' ELSE 'Debit Order' END, 25, 'Monthly', 174.00, FALSE,
                   (SELECT w.id FROM workplaces w WHERE w.ra_number = p.ra),
                   TIMESTAMP '2026-01-01 00:00:00', TIMESTAMP '2026-01-01 00:00:00'
            FROM (
              SELECT q.n, q.h, q.joined, q.status,
                     CASE WHEN q.status IN ('Active', 'Suspended', 'Pending') AND q.h % 50 <> 5
                               THEN 700001 + q.h % 5507
                          WHEN q.status IN ('Resigned', 'Retired', 'Terminated') AND (q.n % 120 = 0 OR q.n > 546400)
                               THEN 700001 + q.h % 5507 END AS ra
              FROM (
                SELECT r.n, r.h, r.joined,
                       CASE WHEN r.on_record THEN CASE WHEN r.h % 30 = 0 THEN 'Suspended'
                                                       WHEN r.h % 97 = 1 THEN 'Pending' ELSE 'Active' END
                            WHEN r.h % 20000 = 7 THEN 'Inactive'
                            ELSE CASE r.n % 3 WHEN 0 THEN 'Resigned' WHEN 1 THEN 'Retired' ELSE 'Terminated' END
                       END AS status
                FROM (
                  SELECT s.n, (s.n::bigint * 2654435761) % 1000003 AS h,
                         CASE WHEN s.n > 546400 THEN DATE '2023-01-01' + ((s.n - 546401)::bigint * 1352 / 3600)::int
                              WHEN (s.n::bigint * 2654435761) % 1000003 % 5000 = 13 THEN NULL
                              ELSE DATE '1975-01-01' + ((s.n - 1)::bigint * 17531 / 546400)::int END AS joined,
                         (s.n <= 200000 AND s.n % 200 = 0) OR (s.n > 200000 AND s.n <= 445000 AND s.n % 10 = 0)
                           OR (s.n > 445000 AND s.n <= 545000 AND s.n % 20 < 7) OR (s.n > 545000 AND s.n % 10 < 9)
                           AS on_record
                  FROM generate_series(1, 550000) s(n)
                ) r
              ) q
            ) p
            """,
            "SELECT setval(pg_get_serial_sequence('members', 'id'), 550000)",
            // A leaver's change, in the order of the members as a migration reads them. Most left before 2023; one in
            // a hundred and twenty left in the last three years -- about ninety a month -- as did the newest who left.
            """
            INSERT INTO member_status_changes (member_id, from_status, to_status, reason, deletion_code_id, changed_by,
                                               changed_at)
            SELECT l.id, 'Active', l.status, CASE WHEN l.arrears THEN 'SUBS_IN_ARREARS' ELSE 'DELETION_CODE' END, d.id,
                   'INVENTED MIGRATION', (l.left_on + TIME '07:00') AT TIME ZONE 'UTC'
            FROM (
              SELECT m.id, m.status, m.status = 'Terminated' AND x.h % 2 = 0 AS arrears,
                     LEAST(DATE '2026-09-13', CASE
                       WHEN m.id > 546400
                            THEN m.join_date + 30 + (x.h % GREATEST(1, DATE '2026-09-13' - m.join_date - 30))::int
                       WHEN m.id % 120 = 0 THEN DATE '2023-01-01' + (x.h % 1352)::int
                       ELSE COALESCE(m.join_date, DATE '1990-01-01') + 180
                            + (x.h % GREATEST(1, DATE '2022-12-31' - COALESCE(m.join_date, DATE '1990-01-01') - 180))::int
                     END) AS left_on
              FROM members m
              CROSS JOIN LATERAL (SELECT (m.id::bigint * 2654435761) % 1000003 AS h) x
              WHERE m.status IN ('Resigned', 'Retired', 'Terminated')
            ) l
            LEFT JOIN deletion_codes d ON NOT l.arrears
                 AND d.sasbo_code = CASE l.status WHEN 'Retired' THEN 1 WHEN 'Resigned' THEN 2 ELSE 5 END
            ORDER BY l.id
            """,
            // The suspended, each with the change that suspended them, since 2020.
            """
            INSERT INTO member_status_changes (member_id, from_status, to_status, reason, suspension_code_id,
                                               changed_by, changed_at)
            SELECT m.id, 'Active', 'Suspended', 'SUSPENSION_CODE', sc.id, 'INVENTED MIGRATION',
                   (LEAST(DATE '2026-09-13', GREATEST(COALESCE(m.join_date, DATE '1990-01-01') + 30,
                                                      DATE '2020-01-01' + (x.h % 2448)::int)) + TIME '07:00')
                     AT TIME ZONE 'UTC'
            FROM members m
            CROSS JOIN LATERAL (SELECT (m.id::bigint * 2654435761) % 1000003 AS h) x
            JOIN suspension_codes sc ON sc.code = CASE WHEN x.h % 2 = 0 THEN 11 ELSE 13 END
            WHERE m.status = 'Suspended'
            ORDER BY m.id
            """,
            // A few reinstated: left in 2024 or early 2025, back within fourteen months.
            """
            INSERT INTO member_status_changes (member_id, from_status, to_status, reason, deletion_code_id, changed_by,
                                               changed_at)
            SELECT r.id, r.from_status, r.to_status, r.reason, r.code_id, 'INVENTED MIGRATION',
                   (r.on_day + TIME '07:00') AT TIME ZONE 'UTC'
            FROM (
              SELECT m.id, 'Active' AS from_status, 'Resigned' AS to_status, 'DELETION_CODE' AS reason,
                     (SELECT id FROM deletion_codes WHERE sasbo_code = 2) AS code_id,
                     DATE '2024-06-01' + (x.h % 300)::int AS on_day
              FROM members m
              CROSS JOIN LATERAL (SELECT (m.id::bigint * 2654435761) % 1000003 AS h) x
              WHERE m.status = 'Active' AND x.h % 400 = 17 AND m.join_date < DATE '2024-01-01'
              UNION ALL
              SELECT m.id, 'Resigned', 'Active', 'REINSTATED', NULL,
                     DATE '2024-06-01' + (x.h % 300)::int + 60 + (x.h / 400 % 400)::int
              FROM members m
              CROSS JOIN LATERAL (SELECT (m.id::bigint * 2654435761) % 1000003 AS h) x
              WHERE m.status = 'Active' AND x.h % 400 = 17 AND m.join_date < DATE '2024-01-01'
            ) r
            ORDER BY r.id, r.on_day
            """);

    // ── the machinery ─────────────────────────────────────────────────────────────────────────────────────────

    /** Built from the container's host and port, as the precedent's is -- the name is the container's to choose. */
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
