package za.co.titandynamix.sasbo_online_backend.guards;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import za.co.titandynamix.sasbo_online_backend.exception.AcbRefusalCode;
import za.co.titandynamix.sasbo_online_backend.support.ApiSurface;
import za.co.titandynamix.sasbo_online_backend.support.MainSourceInventory;
import za.co.titandynamix.sasbo_online_backend.support.MainSourceInventory.Layer;
import za.co.titandynamix.sasbo_online_backend.support.MainSourceInventory.MainClassSource;
import za.co.titandynamix.sasbo_online_backend.support.MigrationSeed;

import java.io.IOException;
import java.lang.reflect.RecordComponent;
import java.time.temporal.Temporal;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static za.co.titandynamix.sasbo_online_backend.support.NonVacuousScan.assertNonVacuousScan;

/**
 * The ACB module's standing contract, as guards -- each with a canary proving its predicate can
 * still fail, because a source scan that stops matching fails BLIND: it finds nothing, reports no
 * offender, and passes.
 *
 * <ul>
 *   <li>No ACB source reads the clock. Time is injected, so every test can freeze it.</li>
 *   <li>No ACB log call names an account or identity number.</li>
 *   <li>Every ACB DTO carries its dates as strings, never {@code java.time} types.</li>
 *   <li>No controller reaches the mail sender or a letters job.</li>
 *   <li>Every refusal code is published in the committed vocabulary, with its status -- and thrown
 *       somewhere, so the vocabulary lists no refusal the server cannot give.</li>
 *   <li>The migration chain names V40 to V49.</li>
 * </ul>
 *
 * <p>"ACB sources" are discovered, not listed: every class in the {@code acb} package, and every
 * class anywhere whose name starts {@code Acb} or {@code ACB}. A class added next month is covered
 * the day it exists.
 */
@DisplayName("ACB guards")
class AcbGuardsTest {

    /** Ways Java reads the wall clock. {@code now(} covers every {@code java.time} type's. */
    static final List<String> CLOCK_READS = List.of("now(", "currentTimeMillis", "nanoTime");

    /** A log call, through to the end of its statement. */
    private static final Pattern LOG_CALL = Pattern.compile("\\blog\\.(trace|debug|info|warn|error)\\(([^;]*);");

    /** What a log call must never name. Matched case-insensitively. */
    static final List<String> NEVER_LOGGED = List.of("accountnumber", "identitynumber", "passportnumber");

    static List<MainClassSource> acbSources() {
        return MainSourceInventory.all().stream()
                .filter(c -> c.path().toString().replace('\\', '/').contains("/acb/")
                        || c.name().startsWith("Acb") || c.name().startsWith("ACB"))
                .toList();
    }

    @Test
    @DisplayName("the scan finds ACB's sources -- rules, entities, DTOs, services, the controller")
    void theScanIsNotVacuous() {
        List<MainClassSource> sources = acbSources();
        // Well below the real count (over forty), and never raised to match it.
        assertNonVacuousScan(sources, 20, "ACB main sources");
        assertThat(sources).extracting(MainClassSource::name)
                .contains("ACBController", "AcbRunServiceImpl", "DebitWindow", "RunPreparer");
    }

    // ------------------------------------------------------------------------------------------

    @Test
    @DisplayName("no ACB source reads the clock -- every rule is handed one")
    void noClockReads() {
        List<String> offenders = new ArrayList<>();
        for (MainClassSource source : acbSources()) {
            for (String read : CLOCK_READS) {
                for (Integer line : source.linesContaining(read)) {
                    offenders.add(source.at(line) + " reads the clock (" + read + ")");
                }
            }
        }
        assertThat(offenders)
                .as("ACB takes a java.time.Clock and asks it; it never asks the system. A rule that "
                        + "reads the wall clock changes its answer between two runs of one test, and "
                        + "the day it is wrong is a month end nobody is watching")
                .isEmpty();
    }

    @Test
    @DisplayName("canary: the clock predicate catches every spelling it claims to")
    void theClockPredicateCanFail() {
        for (String spelling : List.of("LocalDate.now()", "Instant.now()", "LocalDateTime.now(ZoneId.of(\"UTC\"))",
                "System.currentTimeMillis()", "System.nanoTime()")) {
            String blanked = MainSourceInventory.blankComments("class X { Object t = " + spelling + "; }");
            assertThat(CLOCK_READS.stream().anyMatch(blanked::contains)).as(spelling).isTrue();
        }
        // ...and not in a comment, which is blanked before the scan.
        String commented = MainSourceInventory.blankComments("class X { // never LocalDate.now()\n }");
        assertThat(CLOCK_READS.stream().anyMatch(commented::contains)).isFalse();
    }

    // ------------------------------------------------------------------------------------------

    @Test
    @DisplayName("no ACB log call names an account or identity number")
    void noAccountNumberIsLogged() {
        List<String> offenders = new ArrayList<>();
        int logCalls = 0;
        for (MainClassSource source : acbSources()) {
            Matcher call = LOG_CALL.matcher(source.code());
            while (call.find()) {
                logCalls++;
                String arguments = call.group(2).toLowerCase(java.util.Locale.ROOT);
                for (String forbidden : NEVER_LOGGED) {
                    if (arguments.contains(forbidden)) {
                        offenders.add(source.name() + ": log." + call.group(1) + "(... " + forbidden + " ...)");
                    }
                }
            }
        }
        assertNonVacuousScan(java.util.Collections.nCopies(logCalls, "log call"), 3, "log calls in ACB sources");
        assertThat(offenders)
                .as("run ids, line ids and membership numbers may be logged; account and identity "
                        + "numbers never -- a log line is forwarded, pasted and kept")
                .isEmpty();
    }

    @Test
    @DisplayName("canary: the log predicate catches an account number in a log call")
    void theLogPredicateCanFail() {
        Matcher call = LOG_CALL.matcher("log.info(\"line {} for {}\", line.getId(), line.getAccountNumber());");
        assertThat(call.find()).isTrue();
        assertThat(NEVER_LOGGED.stream().anyMatch(call.group(2).toLowerCase(java.util.Locale.ROOT)::contains))
                .isTrue();
    }

    // ------------------------------------------------------------------------------------------

    @Test
    @DisplayName("every ACB DTO carries dates as ISO strings -- no java.time field anywhere")
    void dtoDatesAreStrings() throws ClassNotFoundException {
        List<MainClassSource> dtos = MainSourceInventory.inLayer(Layer.DTO).stream()
                .filter(c -> c.path().toString().replace('\\', '/').contains("/dto/acb/"))
                .toList();
        assertNonVacuousScan(dtos, 5, "ACB DTOs");

        List<String> offenders = new ArrayList<>();
        for (MainClassSource dto : dtos) {
            Class<?> type = Class.forName("za.co.titandynamix.sasbo_online_backend.dto.acb." + dto.name());
            offenders.addAll(javaTimeComponents(type));
        }
        assertThat(offenders)
                .as("a java.time field leaves as whatever the application's Jackson configuration "
                        + "makes of it -- an array of numbers, today. ACB's DTOs carry the ISO string")
                .isEmpty();
    }

    @Test
    @DisplayName("canary: the DTO predicate catches a java.time component")
    void theDtoPredicateCanFail() {
        record Bad(String month, java.time.LocalDate day) {
        }
        assertThat(javaTimeComponents(Bad.class)).singleElement().asString().contains("day");
    }

    static List<String> javaTimeComponents(Class<?> type) {
        List<String> found = new ArrayList<>();
        RecordComponent[] components = type.getRecordComponents();
        if (components == null) {
            return found;
        }
        for (RecordComponent component : components) {
            if (Temporal.class.isAssignableFrom(component.getType())
                    || component.getType().getName().startsWith("java.time.")) {
                found.add(type.getSimpleName() + "." + component.getName() + " is " + component.getType().getName());
            }
        }
        return found;
    }

    // ------------------------------------------------------------------------------------------

    @Test
    @DisplayName("no controller reaches the mail sender or a letters job -- sending is never on a request")
    void noControllerSendsMail() {
        List<MainClassSource> controllers = MainSourceInventory.inLayer(Layer.CONTROLLER);
        assertNonVacuousScan(controllers, 10, "controllers");
        List<String> offenders = controllers.stream()
                .filter(c -> c.name().startsWith("ACB") || c.name().startsWith("Acb"))
                .filter(c -> c.contains("JavaMailSender") || c.contains("LetterJob") || c.contains("EmailService"))
                .map(MainClassSource::name)
                .toList();
        assertThat(offenders)
                .as("an ACB letter is rendered and sent by a job with a per-letter state -- never on "
                        + "the request thread, where a timeout is how a letter gets sent twice")
                .isEmpty();
    }

    @Test
    @DisplayName("🔒 SD4-B: the history seeder -- the one creator of a run outside the window -- is reached from DataSeeder alone")
    void onlyTheSeedReachesTheHistorySeeder() {
        List<MainClassSource> sources = MainSourceInventory.all();
        assertNonVacuousScan(sources, 100, "main sources");
        List<String> callers = sources.stream()
                .filter(c -> !c.name().equals("AcbHistorySeeder"))
                .filter(c -> c.contains("AcbHistorySeeder"))
                .map(MainClassSource::name)
                .toList();
        assertThat(callers)
                .as("AcbHistorySeeder writes run rows for months the service refuses, by design; anything but "
                        + "the start-up seed reaching it would be a way round the preparation window")
                .containsExactly("DataSeeder");
    }

    // ------------------------------------------------------------------------------------------

    @Test
    @DisplayName("every refusal code is published in the committed vocabulary, with its status")
    void everyCodeIsPublished() throws IOException {
        Set<String> published = publishedCodes(ApiSurface.readCommitted(ApiSurface.VOCABULARY_FILE));
        List<String> missing = new ArrayList<>();
        for (AcbRefusalCode code : AcbRefusalCode.values()) {
            if (!published.contains(code.name() + "=" + code.status().value())) {
                missing.add(code.name() + " (" + code.status().value() + ")");
            }
        }
        assertThat(missing)
                .as("codes the server can throw that the browser has never been told about. "
                        + "Regenerate: %s, and copy the file to the client", ApiSurface.regenerateCommand())
                .isEmpty();
    }

    @Test
    @DisplayName("canary: an unpublished code is caught")
    void theVocabularyPredicateCanFail() throws IOException {
        String vocabulary = "{\"acb\": {\"refusalCodes\": [{\"code\": \"RUN_NOT_FOUND\", \"status\": 404}]}}";
        Set<String> published = publishedCodes(vocabulary);
        assertThat(published).containsExactly("RUN_NOT_FOUND=404");
        assertThat(published).doesNotContain("RUN_ALREADY_EXISTS=409");
    }

    static Set<String> publishedCodes(String vocabulary) throws IOException {
        Set<String> codes = new TreeSet<>();
        JsonNode refusalCodes = new ObjectMapper().readTree(vocabulary).path("acb").path("refusalCodes");
        for (JsonNode entry : refusalCodes) {
            codes.add(entry.path("code").asText() + "=" + entry.path("status").asInt());
        }
        return codes;
    }

    @Test
    @DisplayName("every refusal code is thrown somewhere -- the vocabulary promises no refusal the server cannot give")
    void everyCodeIsThrown() {
        List<MainClassSource> sources = MainSourceInventory.all().stream()
                .filter(c -> !c.name().equals(AcbRefusalCode.class.getSimpleName()))
                .toList();
        assertNonVacuousScan(sources, 100, "main sources");
        List<String> unthrown = new ArrayList<>();
        for (AcbRefusalCode code : AcbRefusalCode.values()) {
            if (sources.stream().noneMatch(source -> names(source.code(), code))) {
                unthrown.add(code.name());
            }
        }
        assertThat(unthrown)
                .as("codes declared ahead of the code that throws them. The browser's parity list "
                        + "and the automation suite both copy the vocabulary, and would branch on a "
                        + "refusal no route can give. Add a code in the commit that first throws it")
                .isEmpty();
    }

    @Test
    @DisplayName("canary: a code named only in a comment, or not at all, is caught as unthrown")
    void theThrownPredicateCanFail() {
        String throwing = MainSourceInventory.blankComments(
                "class X { void f() { throw new AcbRefusal(AcbRefusalCode.RUN_EMPTY, \"no lines\", null); } }");
        assertThat(names(throwing, AcbRefusalCode.RUN_EMPTY)).isTrue();
        assertThat(names(throwing, AcbRefusalCode.RUN_NOT_FOUND)).isFalse();
        String commented = MainSourceInventory.blankComments(
                "class X { // throws AcbRefusalCode.RUN_NOT_FOUND one day\n }");
        assertThat(names(commented, AcbRefusalCode.RUN_NOT_FOUND)).isFalse();
    }

    /**
     * Whether comment-blanked source names the code as {@code AcbRefusalCode.NAME} -- the spelling
     * every ACB class uses. A static import would read as unthrown: the guard fails loud, and is
     * re-aimed, never the import contorted.
     */
    static boolean names(String blankedSource, AcbRefusalCode code) {
        return Pattern.compile("\\bAcbRefusalCode\\." + code.name() + "\\b").matcher(blankedSource).find();
    }

    // ------------------------------------------------------------------------------------------

    @Test
    @DisplayName("the migration chain names ACB's V40 to V49")
    void theChainNamesAcbsMigrations() {
        assertThat(MigrationSeed.MIGRATIONS).contains(
                "V40__member_money_columns_to_numeric_step_one.sql",
                "V41__acb_runs.sql",
                "V42__acb_run_lines.sql",
                "V43__acb_audit_events.sql",
                "V44__acb_run_files.sql",
                "V45__acb_unpaid_codes.sql",
                "V46__acb_letters.sql",
                "V47__acb_holidays.sql",
                "V48__acb_opening_position.sql",
                "V49__acb_member_account_type.sql");
    }
}
