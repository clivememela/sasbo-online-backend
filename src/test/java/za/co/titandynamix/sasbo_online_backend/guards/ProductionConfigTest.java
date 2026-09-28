package za.co.titandynamix.sasbo_online_backend.guards;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.FileSystemResource;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The PRODUCTION configuration, asserted -- because on 2026-09-16 it dropped
 * every table in the live database, and no test in this repository read it.
 *
 * <h2>What happened</h2>
 * Server {@code 74fdd09} changed {@code spring.jpa.hibernate.ddl-auto} from
 * {@code update} to {@code create-drop}. Hibernate drops the whole schema when
 * the application starts and again when it stops, so the deploy that carried it
 * answered {@code relation "banks" does not exist} on every data endpoint while
 * {@code /actuator/health} still said UP. {@code ace7297} set it back; setting
 * it back restores nothing. Register row <b>C12</b>.
 *
 * <h2>Why nothing caught it</h2>
 * 🔑 <b>Every test profile brings its own {@code ddl-auto}</b> -- the fast lane
 * {@code create-drop} on H2, the integration and migration profiles
 * {@code validate} -- and the test classpath's {@code application.yaml}
 * SHADOWS the production file of the same name. So no test ever loaded the
 * value that deploys, and a context boot cannot be made to: it would load the
 * test file. This guard therefore reads {@code src/main/resources} BY PATH,
 * which is also exactly what the Dockerfile packages.
 *
 * <h2>Three different strengths, deliberately</h2>
 * <ol>
 *   <li><b>Live:</b> production never DESTROYS the schema, under any of the
 *       three spellings that can say so. The incident's own guard.</li>
 *   <li><b>Disabled, with a live pin:</b> production never WRITES the schema
 *       ({@code update} -- register row C1) and never permits
 *       {@code flyway clean} (row C4). Both are standing findings: filed,
 *       not fixed, because the file is the incumbent's runtime code.</li>
 *   <li><b>Disabled, with a live pin:</b> production does not log every SQL
 *       statement at a DEBUG root (row C11).</li>
 * </ol>
 *
 * <p>⚠️ <b>What this cannot see:</b> an environment variable on the host
 * ({@code SPRING_JPA_HIBERNATE_DDL_AUTO} on Render) outranks this file. Only
 * the host's dashboard can show that; this guard covers what is committed.
 */
@DisplayName("The production configuration")
class ProductionConfigTest {

    private static final Path PRODUCTION_CONFIG = Path.of("src", "main", "resources", "application.yaml");

    /** Values of a schema action that DROP objects. Hibernate's and the JPA standard's vocabularies both. */
    private static final Set<String> DESTRUCTIVE = Set.of("create", "create-drop", "drop", "drop-and-create");

    /** Values that neither create nor alter nor drop anything. */
    private static final Set<String> READ_ONLY = Set.of("validate", "none");

    /**
     * Every key that can set a schema action, in canonical form. Three
     * spellings reach the same Hibernate switch, and a guard that read only the
     * first would pass a {@code create-drop} written as either of the others.
     */
    private static final List<String> SCHEMA_ACTION_KEYS = List.of(
            "spring.jpa.hibernate.ddlauto",
            "spring.jpa.properties.hibernate.hbm2ddl.auto",
            "spring.jpa.properties.jakarta.persistence.schemageneration.database.action");

    /**
     * The file, flattened to dotted keys and canonicalised the way Spring's
     * relaxed binding reads them -- {@code ddl-auto}, {@code ddl_auto} and
     * {@code ddlAuto} are one key -- so the guard reads what Spring reads.
     */
    static Map<String, String> canonical(Properties flattened) {
        Map<String, String> result = new TreeMap<>();
        flattened.forEach((key, value) -> result.put(
                key.toString().toLowerCase(Locale.ROOT).replace("-", "").replace("_", ""),
                value.toString().trim().toLowerCase(Locale.ROOT)));
        return result;
    }

    private static Map<String, String> parse(org.springframework.core.io.Resource yaml) {
        YamlPropertiesFactoryBean factory = new YamlPropertiesFactoryBean();
        factory.setResources(yaml);
        return canonical(factory.getObject());
    }

    private static Map<String, String> production() {
        assertThat(PRODUCTION_CONFIG)
                .as("the production configuration is not where this guard reads it. The test "
                        + "working directory must be the project root -- if it moved, fix the "
                        + "path; do not delete the guard")
                .isRegularFile();
        Map<String, String> config = parse(new FileSystemResource(PRODUCTION_CONFIG));

        // NON-VACUITY: the test classpath carries a file of the same name. A guard
        // that silently read THAT one would assert the H2 profile and pass.
        assertThat(config)
                .as("this does not look like the production file -- it should bind the "
                        + "datasource to SPRING_DATASOURCE_URL, which no test profile does")
                .containsEntry("spring.datasource.url", "${spring_datasource_url}");
        return config;
    }

    /** Every schema action the configuration sets, keyed by where it was set. */
    static Map<String, String> schemaActions(Map<String, String> config) {
        Map<String, String> actions = new TreeMap<>();
        SCHEMA_ACTION_KEYS.forEach((key) -> {
            if (config.containsKey(key)) {
                actions.put(key, config.get(key));
            }
        });
        return actions;
    }

    static Map<String, String> destructiveActions(Map<String, String> config) {
        Map<String, String> found = new TreeMap<>(schemaActions(config));
        found.values().removeIf((value) -> !DESTRUCTIVE.contains(value));
        return found;
    }

    static Map<String, String> schemaWritingActions(Map<String, String> config) {
        Map<String, String> found = new TreeMap<>(schemaActions(config));
        found.values().removeIf(READ_ONLY::contains);
        if ("true".equals(config.get("spring.jpa.generateddl")) && !config.containsKey(SCHEMA_ACTION_KEYS.get(0))) {
            // generate-ddl only decides when ddl-auto is absent -- and then it means update.
            found.put("spring.jpa.generateddl", "true");
        }
        return found;
    }

    private static Map<String, String> yaml(String text) {
        return parse(new ByteArrayResource(text.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    @DisplayName("🔴 C12: never lets Hibernate DROP the live schema, under any spelling")
    void productionNeverDropsTheSchema() {
        assertThat(destructiveActions(production()))
                .as("a schema action that DROPS objects is set in the production "
                        + "configuration. On 2026-09-16 `ddl-auto: create-drop` (74fdd09) "
                        + "deleted every table in the live database on deploy -- register row "
                        + "C12. Schema changes belong in a Flyway migration under "
                        + "src/main/resources/db/migration, never in this setting")
                .isEmpty();
    }

    @Test
    @DisplayName("the drop guard reddens on every spelling of a drop, and passes what is safe")
    void theDropGuardCanFail() {
        // The canary pair. A guard over a single file that is fine today has never
        // been seen to fail; these fixtures are the incident, spelled three ways,
        // beside the two values that must never trip it.
        assertThat(destructiveActions(yaml("spring:\n  jpa:\n    hibernate:\n      ddl-auto: create-drop\n")))
                .as("the incident's own value").containsValue("create-drop");
        assertThat(destructiveActions(yaml("spring:\n  jpa:\n    hibernate:\n      ddl_auto: CREATE\n")))
                .as("relaxed binding: underscore, upper case").containsValue("create");
        assertThat(destructiveActions(yaml("spring.jpa.properties.hibernate.hbm2ddl.auto: create-drop\n")))
                .as("the raw Hibernate property").containsValue("create-drop");
        assertThat(destructiveActions(yaml(
                "spring.jpa.properties.jakarta.persistence.schema-generation.database.action: drop-and-create\n")))
                .as("the JPA standard property").containsValue("drop-and-create");

        assertThat(destructiveActions(yaml("spring:\n  jpa:\n    hibernate:\n      ddl-auto: validate\n")))
                .as("must pass: validate").isEmpty();
        assertThat(destructiveActions(yaml("spring:\n  jpa:\n    hibernate:\n      ddl-auto: update\n")))
                .as("must pass THIS guard: update alters, it does not drop -- C1's test owns it").isEmpty();
    }

    @Test
    @DisplayName("🔴 D11: answers an unmatched path itself, rather than mapping it to a static resource")
    void productionDispatchesUnmatchedPathsToTheHandler() {
        // 🔴 THESE TWO KEYS DECIDE WHICH EXCEPTION AN UNKNOWN URL RAISES, and the test
        // profile sets NEITHER -- which is how register row A18 came to describe a 500 that
        // production has never produced (probed 2026-09-18: /api/acb and two nonsense paths
        // each answered 404 with the error envelope). With `add-mappings` back at Boot's
        // default of true, an unmatched path becomes a static-resource lookup and raises
        // NoResourceFoundException, which the catch-all claims -- the 500 the slice saw.
        //
        // Pinned here because this file is the only place that reads the file that DEPLOYS.
        // The handler on `phase/acb-00-groundwork` answers both exception types, so the fix
        // does not depend on these keys; this says the keys are what production relies on
        // today, and that dropping one is a deliberate act rather than a quiet one.
        Map<String, String> config = production();

        assertThat(config.get("spring.mvc.throwexceptionifnohandlerfound"))
                .as("without this, Spring answers an unmatched path with a static-resource lookup "
                        + "instead of raising NoHandlerFoundException (D11, A18)")
                .isEqualTo("true");
        assertThat(config.get("spring.web.resources.addmappings"))
                .as("Boot's default is true; with mappings on, an unknown URL raises "
                        + "NoResourceFoundException and the catch-all turns it into a 500")
                .isEqualTo("false");
    }

    /**
     * ✅ C1 FIXED 2026-09-28 -- {@code ddl-auto: validate}, and {@code generate-ddl} removed with it.
     *
     * <p>This test was {@code @Disabled} behind a KnownDefects row, paired with a
     * {@code productionStillRunsUpdate} pin that asserted the defect was still there. Fixing the
     * config reddened the pin, the pin named this test, and the pair is now retired: the guard runs
     * and the pin is deleted. ⚠️ Read the pin's own instruction before ever re-disabling this --
     * a guard turned off to make a build green is a defect nobody is looking for any more.
     *
     * <p>🔑 C13 ({@code is_bargaining_council}) is NOT closed by this. That column reached the live
     * database while `update` was on, and it is still there; this only stops the next one.
     */
    @Test
    @DisplayName("🔴 C1: never lets Hibernate write the live schema -- migrations own it")
    void productionNeverWritesTheSchema() {
        assertThat(schemaWritingActions(production()))
                .as("Hibernate may create or alter the production schema. With `update` a mapped "
                        + "field with no migration becomes a column no migration knows about -- "
                        + "exactly how is_bargaining_council (C13) reached the live database "
                        + "while every migration-built database lacks it. Use `validate`")
                .isEmpty();
    }

    @Test
    @DisplayName("C4 (fixed on capture-member): never permits flyway clean against the live database")
    void productionForbidsFlywayClean() {
        assertThat(production().get("spring.flyway.cleandisabled"))
                .as("`flyway.clean-disabled: false` lets one `flyway clean` -- a mistyped URL, a "
                        + "Gradle task, a well-meant reset -- drop every object in the schema. "
                        + "Delete the line: Flyway's default is true")
                .isNotEqualTo("false");
    }

    /**
     * ✅ C11 FIXED 2026-09-28 -- {@code show-sql: false} and the root logger at INFO.
     *
     * <p>Retired the same way C1 was: the {@code productionStillLogsEveryStatement} pin reddened,
     * named this test, and both halves are now gone. The fix was not only about noise -- on
     * 2026-09-28 {@code show-sql} was the whole of a production slowdown, four identical calls to
     * {@code /api/banks} taking 3.1s, 6.8s, 7.1s then 11.8s as the log I/O compounded on a
     * 0.5-CPU instance. With it off the same call is ~1.3s, flat.
     */
    @Test
    @DisplayName("🟠 C11: does not echo every SQL statement, or run the root logger at DEBUG")
    void productionLogsQuietly() {
        Map<String, String> config = production();

        assertThat(config.get("spring.jpa.showsql"))
                .as("show-sql writes every statement to stdout, bypassing the logging levels "
                        + "set for org.hibernate.SQL in the same file")
                .isNotEqualTo("true");
        assertThat(config.get("logging.level.root"))
                .as("a DEBUG root logs framework internals for every request on a service "
                        + "holding identity and bank account numbers")
                .isNotEqualTo("debug");
    }

}
