package za.co.titandynamix.sasbo_online_backend.guards;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import za.co.titandynamix.sasbo_online_backend.exception.MonthEndRefusalCode;
import za.co.titandynamix.sasbo_online_backend.support.ApiSurface;
import za.co.titandynamix.sasbo_online_backend.support.MainSourceInventory;
import za.co.titandynamix.sasbo_online_backend.support.MainSourceInventory.MainClassSource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static za.co.titandynamix.sasbo_online_backend.support.NonVacuousScan.assertNonVacuousScan;

/**
 * Month end's guards that need no database and no context (MV-33, MV-34, MV-37, MD16, MD68), each with its canary.
 * The guards that need the running application are {@code MonthEndRoutesTest} and {@code MonthEndExamplesTest}.
 */
@DisplayName("Month end's guards")
class MonthEndGuardsTest {

    /** Month end's own main sources: its controllers, services, repositories, seeders, DTOs and entities. */
    static List<MainClassSource> monthEndSources() {
        return MainSourceInventory.all().stream()
                .filter(c -> MONTH_END_CLASSES.contains(c.name())
                        || c.path().toString().replace('\\', '/').contains("/dto/reference/")
                        || c.path().toString().replace('\\', '/').contains("/dto/workplace/")
                        || c.path().toString().replace('\\', '/').contains("/entity/reference/")
                        || c.path().toString().replace('\\', '/').contains("/entity/workplace/")
                        || c.path().toString().replace('\\', '/').contains("/dto/monthend/")
                        || c.path().toString().replace('\\', '/').contains("/entity/monthend/"))
                .toList();
    }

    /** The classes of the arc outside its own packages -- named, because they live beside other arcs' classes. */
    static final Set<String> MONTH_END_CLASSES = Set.of(
            "MonthEndApi", "WorkplaceSwitch", "ReferenceListController", "WorkplaceController",
            "ListEmptyReason", "SeedFailure", "MonthEndExceptionHandler", "MonthEndRefusal", "MonthEndRefusalCode",
            "SasboReferenceRepository", "WorkplaceRepository", "ReferenceListService", "WorkplaceService",
            "ReferenceListServiceImpl", "WorkplaceServiceImpl", "ListSync", "ReferenceListSeeder", "WorkplaceSeeder",
            "MemberStatusController", "MemberStatusService", "MemberStatusServiceImpl", "MemberStatusRequest",
            "MemberStatusAnswer", "MemberStatusKind", "MemberWorkplaces", "WorkplaceEntityRepository",
            "DemonstrationMember", "DemonstrationMemberSeeder", "MemberSummaryController", "MemberSummaryService",
            "MemberSummaryServiceImpl", "MemberSummaryRepository", "MemberSummaryDto", "SummaryGate", "SummaryGrouping",
            "MonthRange", "SmallCounts", "MemberHistoryController", "MemberHistoryService", "MemberHistoryRepository",
            "MemberHistoryPageDto", "MemberTrail", "MonthEndController", "MonthEndTakes", "MonthEndFactsRepository",
            "MonthEndTakeSeeder", "MonthEndStatistics", "MonthEndStatisticsController", "MonthEndPacks",
            "MonthEndPackController", "MonthEndPackRepository", "MonthEndWorkbooks", "MonthEndPackLauncher",
            "MonthEndPackConfig", "SpreadsheetText",
            // October build, month end's rules: free text and the penetration flag; the reasons for no deduction; the
            // core tiers; the suspended and their lapse; the seed that shows them.
            "FreeText", "Penetration",
            "NoDeductionReasonController", "NoDeductionReasons", "NoDeductionReasonRepository",
            "CoreTierController", "CoreTiers",
            "SuspendedMembersController", "SuspendedMembers", "SuspendedMembersRepository", "SuspensionLapse",
            "MonthEndRulesSeeder");

    // ------------------------------------------------------------------------------------------------ MV-33

    @Test
    @DisplayName("MV-33: every refusal code of the arc is published in the committed vocabulary, with its status")
    void everyCodeIsPublished() throws IOException {
        Set<String> published = publishedCodes(ApiSurface.readCommitted(ApiSurface.VOCABULARY_FILE));
        List<String> missing = new ArrayList<>();
        for (MonthEndRefusalCode code : MonthEndRefusalCode.values()) {
            if (!published.contains(code.name() + "=" + code.status().value())) {
                missing.add(code.name() + " (" + code.status().value() + ")");
            }
        }
        assertThat(missing)
                .as("codes the server can throw that the browser has never been told about. Regenerate: %s, and "
                        + "copy the file to the client", ApiSurface.regenerateCommand())
                .isEmpty();
    }

    @Test
    @DisplayName("canary: an unpublished code is caught")
    void theVocabularyPredicateCanFail() throws IOException {
        Set<String> published = publishedCodes(
                "{\"monthEnd\": {\"refusalCodes\": [{\"code\": \"REQUEST_MALFORMED\", \"status\": 400}]}}");
        assertThat(published).containsExactly("REQUEST_MALFORMED=400");
        assertThat(published).doesNotContain("WORKPLACE_PAGE_INVALID=400");
    }

    static Set<String> publishedCodes(String vocabulary) throws IOException {
        Set<String> codes = new TreeSet<>();
        for (JsonNode entry : new ObjectMapper().readTree(vocabulary).path("monthEnd").path("refusalCodes")) {
            codes.add(entry.path("code").asText() + "=" + entry.path("status").asInt());
        }
        return codes;
    }

    @Test
    @DisplayName("MV-33: every refusal code of the arc is thrown somewhere -- the vocabulary promises nothing no route can give")
    void everyCodeIsThrown() {
        List<MainClassSource> sources = MainSourceInventory.all().stream()
                .filter(c -> !c.name().equals(MonthEndRefusalCode.class.getSimpleName()))
                .toList();
        assertNonVacuousScan(sources, 100, "main sources");
        List<String> unthrown = new ArrayList<>();
        for (MonthEndRefusalCode code : MonthEndRefusalCode.values()) {
            if (sources.stream().noneMatch(source -> names(source.code(), code))) {
                unthrown.add(code.name());
            }
        }
        assertThat(unthrown)
                .as("codes declared ahead of the code that throws them. Add a code in the commit that first throws it")
                .isEmpty();
    }

    @Test
    @DisplayName("canary: a code named only in a comment, or not at all, is caught as unthrown")
    void theThrownPredicateCanFail() {
        String throwing = MainSourceInventory.blankComments("class X { void f() { throw new MonthEndRefusal("
                + "MonthEndRefusalCode.WORKPLACE_PAGE_INVALID, \"no\"); } }");
        assertThat(names(throwing, MonthEndRefusalCode.WORKPLACE_PAGE_INVALID)).isTrue();
        assertThat(names(throwing, MonthEndRefusalCode.REQUEST_MALFORMED)).isFalse();
        String commented = MainSourceInventory.blankComments(
                "class X { // throws MonthEndRefusalCode.REQUEST_MALFORMED one day\n }");
        assertThat(names(commented, MonthEndRefusalCode.REQUEST_MALFORMED)).isFalse();
    }

    static boolean names(String blankedSource, MonthEndRefusalCode code) {
        return Pattern.compile("MonthEndRefusalCode\\." + code.name() + "\\b").matcher(blankedSource).find();
    }

    // ------------------------------------------------------------------------------------------------ MD16

    @Test
    @DisplayName("MD16: every DTO of the arc carries dates as ISO strings -- no java.time component anywhere")
    void dtoDatesAreStrings() throws ClassNotFoundException {
        List<Class<?>> dtos = new ArrayList<>();
        for (MainClassSource source : monthEndSources()) {
            String path = source.path().toString().replace('\\', '/');
            if (path.contains("/dto/")) {
                String pkg = path.substring(path.indexOf("za/co/"), path.lastIndexOf('/')).replace('/', '.');
                Class<?> type = Class.forName(pkg + "." + source.name());
                dtos.add(type);
                for (Class<?> nested : type.getDeclaredClasses()) {
                    dtos.add(nested);
                }
            }
        }
        assertNonVacuousScan(dtos, 8, "month end's DTOs");
        List<String> offenders = new ArrayList<>();
        for (Class<?> dto : dtos) {
            offenders.addAll(AcbGuardsTest.javaTimeComponents(dto));
        }
        assertThat(offenders).as("a java.time field leaves as whatever Jackson makes of it; the arc's DTOs carry ISO text")
                .isEmpty();
    }

    // ------------------------------------------------------------------------------------------------ MD68

    /**
     * What a log call of the arc may hand the logger: a code, a count, a class, a method and a path, a list's key, a
     * reference, a time. Anything read off a row, a request or a resource is a VALUE, and none may be logged (MD68):
     * this application's own package logs at DEBUG on every host.
     */
    static final Pattern LOG_CALL = Pattern.compile("\\blog\\.(trace|debug|info|warn|error)\\(([^;]*);");
    static final List<String> VALUE_READERS = List.of(".get", "rs.", "row.", ".name()", "search", "term", "note",
            "planted", ".text(", ".asText(");
    static final Set<String> ALLOWED = Set.of("request.getMethod()", "request.getRequestURI()", "refusal.code()",
            "resource.rows().size()", "AcbLogging.scrubbed(", "word", "list", "reference", "resourcePath", "rows",
            "what", "done[0]", "done[1]", "done[2]", "millis", "RESOURCE");

    @Test
    @DisplayName("🔒 MD68: no log call of the arc hands the logger a value read off a row, a request or a resource")
    void noLogCallCarriesAValue() {
        List<String> offenders = new ArrayList<>();
        int calls = 0;
        for (MainClassSource source : monthEndSources()) {
            Matcher call = LOG_CALL.matcher(source.code());
            while (call.find()) {
                calls++;
                String arguments = withoutAllowed(argumentsOf(call.group(2)));
                for (String reader : VALUE_READERS) {
                    if (readsAValue(arguments, reader)) {
                        offenders.add(source.name() + ": log." + call.group(1) + "(... " + reader + " ...)");
                    }
                }
            }
        }
        assertNonVacuousScan(java.util.Collections.nCopies(calls, "log call"), 5, "log calls in month end's sources");
        assertThat(offenders).isEmpty();
    }

    @Test
    @DisplayName("canary: the value predicate catches a search term and a row's field handed to the logger")
    void theValuePredicateCanFail() {
        Matcher call = LOG_CALL.matcher("log.debug(\"searching for {}\", search);");
        assertThat(call.find()).isTrue();
        String arguments = withoutAllowed(argumentsOf(call.group(2)));
        assertThat(VALUE_READERS.stream().anyMatch(reader -> readsAValue(arguments, reader))).isTrue();
        Matcher row = LOG_CALL.matcher("log.info(\"workplace {}\", w.getName());");
        assertThat(row.find()).isTrue();
        assertThat(VALUE_READERS.stream().anyMatch(reader -> readsAValue(withoutAllowed(argumentsOf(row.group(2))), reader)))
                .isTrue();
        Matcher resultSet = LOG_CALL.matcher("log.info(\"read {}\", rs.getString(1));");
        assertThat(resultSet.find()).isTrue();
        assertThat(readsAValue(argumentsOf(resultSet.group(2)), "rs.")).as("a ResultSet read, by its name").isTrue();
    }

    @Test
    @DisplayName("must-pass: a count whose name only ENDS in a reader's letters is not a value -- members.size() is not rs.")
    void aNameThatOnlyEndsInAReaderIsNotOne() {
        Matcher count = LOG_CALL.matcher("log.info(\"Seeded {} member(s) from {}\", members.size(), SEED_FILE);");
        assertThat(count.find()).isTrue();
        String arguments = withoutAllowed(argumentsOf(count.group(2)));
        assertThat(VALUE_READERS.stream().filter(reader -> readsAValue(arguments, reader)).toList()).isEmpty();
    }

    /**
     * Whether the arguments hand the logger a value, by one reader: a reader that is a NAME -- {@code rs.},
     * {@code row.}, {@code search}, {@code note} -- matches only where it is a name of its own, never the tail of
     * another ({@code members.size()} is a count, not a ResultSet); a reader that is a call -- {@code .get},
     * {@code .asText(} -- matches wherever it is. Aimed at the fact, not at the spelling.
     */
    static boolean readsAValue(String arguments, String reader) {
        String lower = arguments.toLowerCase(Locale.ROOT);
        String wanted = reader.toLowerCase(Locale.ROOT);
        if (!Character.isLetter(wanted.charAt(0))) {
            return lower.contains(wanted);
        }
        return Pattern.compile("(?<![a-z0-9_])" + Pattern.quote(wanted)).matcher(lower).find();
    }

    /** The arguments after the message: the message itself is prose, written here, and names no value. */
    private static String argumentsOf(String call) {
        int close = call.indexOf("\",");
        return close < 0 ? "" : call.substring(close + 2);
    }

    private static String withoutAllowed(String arguments) {
        String left = arguments;
        for (String allowed : ALLOWED) {
            left = left.replace(allowed, "");
        }
        return left;
    }

    // ------------------------------------------------------------------------------------------------ MV-37

    @Test
    @DisplayName("MV-37, BR54: every file the arc generates is pinned to LF by name in .gitattributes")
    void everyGeneratedFileIsPinned() throws IOException {
        String attributes = Files.readString(Paths.get(".gitattributes"), StandardCharsets.UTF_8);
        for (String file : List.of("src/main/resources/data/sasbo_reference_lists.json",
                "src/main/resources/data/sasbo_workplaces.json", "src/main/resources/data/demonstration_members.json",
                "month-end-examples.json")) {
            assertThat(pinned(attributes, file)).as("%s in .gitattributes", file).isTrue();
        }
    }

    /**
     * The resources whose stated sizes no other number of the arc shares: the workplaces', the invented members' and,
     * since A.1, the workplaces' visits -- whose file states two sizes, its workplaces ({@code count}) and its months
     * ({@code visits}), and both are looked for.
     * A list's size -- 137 at the most -- is too small to look for without catching a weight or a width, and the lists'
     * tests read theirs from the file (MonthEndSeedTest.everyListIsThere).
     */
    static final List<String> SIZED = List.of("src/main/resources/data/sasbo_workplaces.json",
            "src/main/resources/data/demonstration_members.json", "src/main/resources/data/sasbo_workplace_visits.json");

    @Test
    @DisplayName("MV-37, BR54: no test types the size a resource states -- each reads it from the file")
    void noTestTypesAStatedSize() throws IOException {
        List<String> offenders = new ArrayList<>();
        int scanned = 0;
        for (String resource : SIZED) {
            JsonNode stated = new ObjectMapper().readTree(Files.readString(Paths.get(resource), StandardCharsets.UTF_8));
            List<Integer> sizes = new ArrayList<>(List.of(stated.path("count").asInt()));
            if (stated.has("visits")) {
                sizes.add(stated.path("visits").asInt());
            }
            for (int size : sizes) {
                assertThat(size).as("%s states its size", resource).isGreaterThan(999);
                Pattern typed = typedSize(size);
                try (var files = Files.walk(Paths.get("src", "test", "java"))) {
                    for (var file : files.filter(f -> f.toString().endsWith(".java")).toList()) {
                        scanned++;
                        if (typed.matcher(Files.readString(file, StandardCharsets.UTF_8)).find()) {
                            offenders.add(file.getFileName() + " types " + size);
                        }
                    }
                }
            }
        }
        assertThat(scanned).as("the test sources, scanned").isPositive();
        assertThat(offenders).as("a size typed goes stale the day SASBO's list changes").isEmpty();
    }

    @Test
    @DisplayName("canary: a size typed in any spelling is caught, and a longer number is not")
    void theTypedSizePredicateCanFail() {
        Pattern typed = typedSize(4321);
        assertThat(typed.matcher("isEqualTo(4_321)").find()).isTrue();
        assertThat(typed.matcher("generate_series(1, 4321)").find()).isTrue();
        assertThat(typed.matcher("// 4,321 workplaces").find()).isTrue();
        assertThat(typed.matcher("14321 and 43210 and 0.4321").find()).isFalse();
    }

    /** A size in the spellings a hand types it -- 4321, 4_321, 4,321 or 4 321 -- and never inside a longer number. */
    static Pattern typedSize(int size) {
        String digits = String.valueOf(size);
        String thousands = digits.substring(0, digits.length() - 3);
        return Pattern.compile("(?<![0-9_.,])" + thousands + "[_, ]?" + digits.substring(digits.length() - 3)
                + "(?![0-9_])");
    }

    @Test
    @DisplayName("canary: a file left out of .gitattributes is caught")
    void thePinPredicateCanFail() {
        assertThat(pinned("api-surface.txt text eol=lf\n", "month-end-examples.json")).isFalse();
        assertThat(pinned("month-end-examples.json text eol=lf\n", "month-end-examples.json")).isTrue();
    }

    static boolean pinned(String attributes, String file) {
        return attributes.lines().anyMatch(line -> line.trim().equals(file + " text eol=lf"));
    }

    // ------------------------------------------------------------------------------------------------ migrations

    /** Month end's migrations: from the first the arc added. */
    static final int FIRST_MONTH_END_VERSION = 53;

    @Test
    @DisplayName("🔒 MR-ROB-21, MR-ROB-34: every migration of the arc begins with the lock timeout, and none gives a column the database's time")
    void everyMigrationIsBounded() throws IOException {
        List<String> offenders = new ArrayList<>();
        int found = 0;
        try (var files = Files.list(Paths.get("src", "main", "resources", "db", "migration"))) {
            for (var file : files.toList()) {
                String name = file.getFileName().toString();
                if (!name.matches("V\\d+__.*\\.sql") || Integer.parseInt(name.substring(1, name.indexOf("__")))
                        < FIRST_MONTH_END_VERSION) {
                    continue;
                }
                found++;
                offenders.addAll(unbounded(name, Files.readString(file, StandardCharsets.UTF_8)));
            }
        }
        assertThat(found).as("month end's migrations").isPositive();
        assertThat(offenders).isEmpty();
    }

    @Test
    @DisplayName("canary: a migration with no lock timeout, or a column defaulting to now(), is caught")
    void theMigrationPredicateCanFail() {
        assertThat(unbounded("V99__x.sql", "CREATE TABLE x (id INT);")).hasSize(1);
        assertThat(unbounded("V99__y.sql", "SET LOCAL lock_timeout = '5s';\nCREATE TABLE y (at TIMESTAMPTZ DEFAULT now());"))
                .hasSize(1);
        assertThat(unbounded("V99__z.sql", "-- a header\nSET LOCAL lock_timeout = '5s';\nCREATE TABLE z (id INT);"))
                .isEmpty();
    }

    static List<String> unbounded(String name, String sql) {
        List<String> found = new ArrayList<>();
        String statements = sql.replaceAll("(?m)--.*$", "").trim();
        if (!statements.startsWith("SET LOCAL lock_timeout")) {
            found.add(name + " does not begin SET LOCAL lock_timeout");
        }
        if (Pattern.compile("(?i)default\\s+(now\\(\\)|current_timestamp|current_date|localtimestamp)")
                .matcher(statements).find()) {
            found.add(name + " gives a column the database's time; every instant is the application's");
        }
        return found;
    }

    /** Why a statistic of month end is open (04.3 to 04.8). */
    static final String STATISTIC = "counts, under rule C -- a membership below five at a named employer, table code or "
            + "group never leaves the server -- and cut by where members work only where the switch says all (MD62)";

    /** The routes of the arc that are open, and the written reason each is (MV-34). */
    static final Map<String, String> OPEN_BY_REASON = Map.ofEntries(
            Map.entry("GET /api/reference/regions", "a list of SASBO's regions: codes, and nobody"),
            Map.entry("GET /api/reference/areas", "a list of SASBO's area letters and their regions"),
            Map.entry("GET /api/reference/employer-groups", "a list of employer groups: organisations"),
            Map.entry("GET /api/reference/employers", "a list of employers: organisations, and no count of members"),
            Map.entry("GET /api/reference/table-codes", "a list of table codes: organisations and their parts"),
            Map.entry("GET /api/reference/committee-provinces", "a list of province letters"),
            Map.entry("GET /api/reference/committees", "a list of committees: organisations"),
            Map.entry("GET /api/reference/suspension-codes", "a list of SASBO's suspension codes"),
            Map.entry("GET /api/reference/deletion-codes", "a list of SASBO's deletion codes, by their numbers"),
            Map.entry("GET /api/reference/no-deduction-reasons", "a list of the reasons for no payroll deduction: words "
                    + "and SASBO's codes, and nobody (MEN-17)"),
            Map.entry("GET /api/reference/core-tiers", "SASBO's core tiers and the table codes standing in each: "
                    + "organisations and codes (MEN-31)"),
            Map.entry("GET /api/reference/status", "how many rows each list holds, and why none where none"),
            Map.entry("GET /api/workplaces", "workplaces: organisations, answered as the switch allows (MD62)"),
            Map.entry("GET /api/workplaces/{id}/visits", "a workplace's visits: the day and the complement found; who "
                    + "visited and the counts of members are withheld (MD61, B1)"),
            Map.entry("POST /api/workplaces/{id}/visits", "a visit recorded: as open as every write today, and named in "
                    + "B1's list (MR-SEC-1)"),
            Map.entry("POST /api/members/{id}/status", "as open as the PUT it replaces (MD13); named in B1's list, "
                    + "and refusing from the day sign-in exists"),
            Map.entry("GET /api/members/summary", "counts, under rule C: a membership below five at a named employer "
                    + "or table code never leaves the server (MD4, MD37)"),
            Map.entry("GET /api/members/{id}/history", "as open as every member read is today (MR-SEC-1); named in B1's "
                    + "list, and refusing from the day sign-in exists"),
            Map.entry("GET /api/month-end/months", "which months are taken, when and by whom: no member, no workplace"),
            Map.entry("GET /api/month-end/{month}", "one month's record -- taken or open, when and by whom: no member, "
                    + "no workplace"),
            Map.entry("GET /api/month-end/{month}/new-and-deleted", STATISTIC),
            Map.entry("GET /api/month-end/{month}/membership", STATISTIC),
            Map.entry("GET /api/month-end/{month}/leavers", STATISTIC),
            Map.entry("GET /api/month-end/{month}/branches", STATISTIC),
            Map.entry("GET /api/month-end/{month}/recruitment", STATISTIC),
            Map.entry("GET /api/month-end/{month}/representatives", STATISTIC),
            Map.entry("GET /api/month-end/{month}/suspensions", STATISTIC + "; the suspended within each bank and by code "
                    + "(MEN-18) -- the members themselves are a listing, held"),
            Map.entry("GET /api/month-end/{month}/visits", "a statistic: counts, by branch, rule C applied; the "
                    + "manager's name and telephone are not answered until sign-in (B1)"),
            Map.entry("POST /api/month-end/{month}/pack", "the statistics as two workbooks: counts only, the sheets the "
                    + "statistics answer, rule C and the switch applied as they apply them (04.10)"),
            Map.entry("GET /api/month-end/packs/{id}", "a pack's progress: its month, its state and when"),
            Map.entry("GET /api/month-end/packs/{id}/{workbook}", "a READY pack's workbook: the statistics' counts, as "
                    + "open as the statistics are"));

}
