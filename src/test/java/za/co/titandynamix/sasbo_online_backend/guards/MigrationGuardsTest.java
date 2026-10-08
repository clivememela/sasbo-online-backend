package za.co.titandynamix.sasbo_online_backend.guards;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import za.co.titandynamix.sasbo_online_backend.exception.MigrationRefusalCode;
import za.co.titandynamix.sasbo_online_backend.legacy.rules.RuleCode;
import za.co.titandynamix.sasbo_online_backend.support.ApiSurface;
import za.co.titandynamix.sasbo_online_backend.support.MainSourceInventory;
import za.co.titandynamix.sasbo_online_backend.support.MainSourceInventory.MainClassSource;
import za.co.titandynamix.sasbo_online_backend.support.TestSourceInventory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static za.co.titandynamix.sasbo_online_backend.support.NonVacuousScan.assertNonVacuousScan;

/**
 * The data migration's own guards, each with its canary: every refusal code published with its status and thrown
 * somewhere; every rule produced somewhere and named by a test; every route behind {@code MIGRATION_RUN}; and no log
 * call of the migration handing the logger a value read off a file, a row or a request.
 */
@DisplayName("The data migration's guards")
class MigrationGuardsTest {

    /** The migration's main sources: its engine, and its classes beside other arcs'. */
    static List<MainClassSource> migrationSources() {
        return MainSourceInventory.all().stream()
                .filter(c -> c.path().toString().replace('\\', '/').contains("/legacy/")
                        || c.path().toString().replace('\\', '/').contains("/dto/migration/")
                        || c.name().startsWith("Migration"))
                .toList();
    }

    // ------------------------------------------------------------------------------------------------ refusal codes

    @Test
    @DisplayName("every refusal code is published in the committed vocabulary, with its status")
    void everyRefusalCodeIsPublished() throws IOException {
        Set<String> published = publishedCodes(ApiSurface.readCommitted(ApiSurface.VOCABULARY_FILE));
        List<String> missing = new ArrayList<>();
        for (MigrationRefusalCode code : MigrationRefusalCode.values()) {
            if (!published.contains(code.name() + "=" + code.status().value())) {
                missing.add(code.name() + " (" + code.status().value() + ")");
            }
        }
        assertThat(missing).as("codes the server can throw that no screen has been told about. Regenerate: %s",
                ApiSurface.regenerateCommand()).isEmpty();
    }

    @Test
    @DisplayName("canary: an unpublished code is caught")
    void thePublishedPredicateCanFail() throws IOException {
        Set<String> published = publishedCodes("{\"migration\": {\"refusalCodes\": [{\"code\": \"PAGE_INVALID\", \"status\": 400}]}}");
        assertThat(published).containsExactly("PAGE_INVALID=400").doesNotContain("BATCH_NOT_FOUND=404");
    }

    static Set<String> publishedCodes(String vocabulary) throws IOException {
        Set<String> codes = new TreeSet<>();
        for (JsonNode entry : new ObjectMapper().readTree(vocabulary).path("migration").path("refusalCodes")) {
            codes.add(entry.path("code").asText() + "=" + entry.path("status").asInt());
        }
        return codes;
    }

    @Test
    @DisplayName("every refusal code is thrown somewhere -- the vocabulary promises nothing no route can give")
    void everyRefusalCodeIsThrown() {
        List<MainClassSource> sources = MainSourceInventory.all().stream()
                .filter(c -> !c.name().equals(MigrationRefusalCode.class.getSimpleName())).toList();
        List<String> unthrown = new ArrayList<>();
        for (MigrationRefusalCode code : MigrationRefusalCode.values()) {
            if (sources.stream().noneMatch(source -> names(source.code(), "MigrationRefusalCode", code.name()))) {
                unthrown.add(code.name());
            }
        }
        assertThat(unthrown).as("codes declared ahead of the code that throws them").isEmpty();
    }

    // ------------------------------------------------------------------------------------------------ rule codes

    @Test
    @DisplayName("every rule is produced somewhere in the engine or the check's statements, and named by a test")
    void everyRuleIsProducedAndTested() {
        List<MainClassSource> sources = MainSourceInventory.all().stream()
                .filter(c -> !c.name().equals(RuleCode.class.getSimpleName())).toList();
        List<TestSourceInventory.TestClassSource> tests = TestSourceInventory.allExcept(MigrationGuardsTest.class);
        assertNonVacuousScan(tests, 50, "test classes");
        List<String> unproduced = new ArrayList<>();
        List<String> untested = new ArrayList<>();
        for (RuleCode code : RuleCode.values()) {
            if (sources.stream().noneMatch(source -> names(source.code(), "RuleCode", code.name()))) {
                unproduced.add(code.name());
            }
            if (tests.stream().noneMatch(test -> namesAlone(test.code(), code.name()))) {
                untested.add(code.name());
            }
        }
        assertThat(unproduced).as("rules nothing produces").isEmpty();
        assertThat(untested).as("rules no test names").isEmpty();
    }

    @Test
    @DisplayName("canary: a code named only in a comment, inside a longer code, or not at all, is caught")
    void theNamedPredicateCanFail() {
        String producing = MainSourceInventory.blankComments("class X { Object f() { return RuleCode.ROW_SHORT; } }");
        assertThat(names(producing, "RuleCode", "ROW_SHORT")).isTrue();
        assertThat(names(producing, "RuleCode", "ROW_MALFORMED")).isFalse();
        String commented = MainSourceInventory.blankComments("class X { // RuleCode.ROW_MALFORMED one day\n }");
        assertThat(names(commented, "RuleCode", "ROW_MALFORMED")).isFalse();
        String longer = MainSourceInventory.blankComments("class XTest { String c = \"TABLE_CODE_UNKNOWN\"; }");
        assertThat(namesAlone(longer, "TABLE_CODE_UNKNOWN")).isTrue();
        assertThat(namesAlone(longer, "CODE_UNKNOWN")).as("a code inside a longer one names the longer one").isFalse();
    }

    static boolean names(String blankedSource, String type, String code) {
        return Pattern.compile(type + "\\." + code + "(?![A-Za-z0-9_])").matcher(blankedSource).find();
    }

    static boolean namesAlone(String blankedSource, String code) {
        return Pattern.compile("(?<![A-Za-z0-9_])" + code + "(?![A-Za-z0-9_])").matcher(blankedSource).find();
    }

    // ------------------------------------------------------------------------------------------------ the permission

    private static final Pattern ROUTE = Pattern.compile("@(Get|Post|Put|Delete|Patch)Mapping\\b");
    private static final String PERMISSION = "@PreAuthorize(\"hasAuthority('MIGRATION_RUN')\")";

    @Test
    @DisplayName("🔒 every route of the migration carries @PreAuthorize(\"hasAuthority('MIGRATION_RUN')\")")
    void everyRouteNeedsThePermission() {
        List<MainClassSource> controllers = migrationSources().stream()
                .filter(c -> c.layer() == MainSourceInventory.Layer.CONTROLLER).toList();
        assertNonVacuousScan(controllers, 1, "the migration's controllers");
        List<String> open = new ArrayList<>();
        int routes = 0;
        for (MainClassSource controller : controllers) {
            routes += unguarded(controller.code(), open, controller.name());
        }
        assertThat(routes).as("routes found").isGreaterThanOrEqualTo(15);
        assertThat(open).as("routes a caller without MIGRATION_RUN could reach").isEmpty();
    }

    @Test
    @DisplayName("canary: a route with no permission above it is caught")
    void theRoutePredicateCanFail() {
        List<String> open = new ArrayList<>();
        int routes = unguarded("""
                class X {
                    @PreAuthorize("hasAuthority('MIGRATION_RUN')")
                    @GetMapping("/a")
                    String a() { return ""; }

                    @GetMapping("/b")
                    String b() { return ""; }

                    @PreAuthorize("hasAuthority('MEMBERS_VIEW')")
                    @PostMapping("/c")
                    String c() { return ""; }
                }
                """, open, "X");
        assertThat(routes).isEqualTo(3);
        assertThat(open).hasSize(2);
    }

    /** The routes found; each without the permission in the three lines above it is added to {@code open}. */
    private static int unguarded(String code, List<String> open, String name) {
        String[] lines = code.split("\n", -1);
        int routes = 0;
        for (int i = 0; i < lines.length; i++) {
            if (!ROUTE.matcher(lines[i]).find() || lines[i].contains("@RequestMapping")) {
                continue;
            }
            routes++;
            boolean guarded = false;
            for (int j = Math.max(0, i - 3); j < i; j++) {
                guarded |= lines[j].contains(PERMISSION);
            }
            if (!guarded) {
                open.add(name + ".java:" + (i + 1));
            }
        }
        return routes;
    }

    // ------------------------------------------------------------------------------------------------ the logs

    private static final Pattern LOG_CALL = Pattern.compile("\\blog\\.(trace|debug|info|warn|error)\\(([^;]*);");

    /**
     * What a migration log call may never hand the logger: a file's text or name, a row's values, a person's details,
     * an exception's message (a constraint's message quotes the row it refused). Each matches at the start of a name.
     */
    private static final List<String> VALUE_READERS = List.of("rawjson", "normalisedjson", "values", "raw(", "row.",
            "rs.", "getmessage", "getlocalizedmessage", "filename", "sourcefilename", "surname", "identity", "email",
            "phone", "tel(", "definition", "snapshot", "note", "text", "getquerystring", "getparameter", "body");

    /** A throwable handed over as it is: logged, it prints its message. It goes through {@code scrubbed(...)}. */
    private static final Pattern RAW_THROWABLE = Pattern.compile(
            "(?<![a-z0-9_.(])(e|ex|exc|exception|failed|failure|error|err|cause|t|throwable)(?![a-z0-9_.(])");

    @Test
    @DisplayName("🔒 no log call of the migration hands the logger a value read off a file, a row or a request")
    void noLogCallCarriesAValue() {
        List<MainClassSource> sources = migrationSources();
        assertNonVacuousScan(sources, 40, "the migration's main sources");
        List<String> offenders = new ArrayList<>();
        int calls = 0;
        for (MainClassSource source : sources) {
            Matcher call = LOG_CALL.matcher(source.code());
            while (call.find()) {
                calls++;
                offenders.addAll(offences(source.name() + ": log." + call.group(1), call.group(2)));
            }
        }
        assertThat(calls).as("log calls in the migration's sources").isGreaterThanOrEqualTo(10);
        assertThat(offenders).isEmpty();
    }

    @Test
    @DisplayName("canary: a file's name, a row's values, an identity number or a raw exception handed to the logger is caught; counts are not")
    void theValuePredicateCanFail() {
        for (String bad : List.of("log.info(\"Batch {} from {}\", id, fileName);",
                "log.debug(\"Row {}\", row.values());",
                "log.warn(\"Duplicate \" + identityKey);",
                "log.error(\"Failed: {}\", failure.getMessage());",
                "log.error(\"Batch {}: the check failed\", id, failed);")) {
            Matcher call = LOG_CALL.matcher(bad);
            assertThat(call.find()).isTrue();
            assertThat(offences("canary", call.group(2))).as(bad).isNotEmpty();
        }
        for (String good : List.of("log.info(\"Migration batch {} registered: {} rows, from the file name\", id, summary.rows());",
                "log.error(\"Migration batch {}: the check failed\", id, AcbLogging.scrubbed(failed));",
                "log.info(\"Migration batch {} refused while reading: {}\", id, refused.reason());")) {
            Matcher call = LOG_CALL.matcher(good);
            assertThat(call.find()).isTrue();
            assertThat(offences("canary", call.group(2))).as(good).isEmpty();
        }
    }

    /** What one call hands the logger that it may not; the message's own words are not a value, so they are cut. */
    static List<String> offences(String where, String call) {
        String handed = withoutLiterals(call).toLowerCase(Locale.ROOT);
        List<String> found = new ArrayList<>();
        for (String reader : VALUE_READERS) {
            if (Pattern.compile("(?<![a-z0-9_])" + Pattern.quote(reader)).matcher(handed).find()) {
                found.add(where + "(... " + reader + " ...)");
            }
        }
        Matcher raw = RAW_THROWABLE.matcher(handed);
        while (raw.find()) {
            found.add(where + "(... " + raw.group(1) + ", not scrubbed)");
        }
        return found;
    }

    /** The call with every string literal removed: what is left is what the call hands over. */
    static String withoutLiterals(String call) {
        StringBuilder out = new StringBuilder(call.length());
        boolean inLiteral = false;
        for (int i = 0; i < call.length(); i++) {
            char c = call.charAt(i);
            if (inLiteral && c == '\\') {
                i++;
            } else if (c == '"') {
                inLiteral = !inLiteral;
            } else if (!inLiteral) {
                out.append(c);
            }
        }
        return out.toString();
    }
}
