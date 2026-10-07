package za.co.titandynamix.sasbo_online_backend.guards;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import za.co.titandynamix.sasbo_online_backend.support.MigrationSeed;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 🧪 R1-23, T2-C: every table an ACB migration creates has an entity -- or the fast lane's H2, which
 * builds its schema from the entities with Flyway off, silently has no such table, and every test
 * there that would touch it proves nothing (memory: H2 drops tables silently).
 *
 * <p>The ACB arc's migrations are V40 on. 🔬 One table is SQL-only by design and is allowed by
 * name (F57): V40's {@code members_money_migration_report}, the record of the money values the
 * migration could not convert -- written once by the migration, read by people, never by the code.
 */
@DisplayName("Every table an ACB migration creates has an entity (R1-23)")
class AcbTablesHaveEntitiesTest {

    private static final Path MIGRATIONS = Paths.get("src", "main", "resources", "db", "migration");

    /** The first migration of the ACB arc (the money migration's step one). */
    private static final int FIRST_ACB_VERSION = 40;

    /** Tables with no entity ON PURPOSE, each with its reason. */
    private static final Map<String, String> SQL_ONLY = Map.of(
            "members_money_migration_report",
            "V40's record of unconverted money values: written by the migration, read by people (F57)");

    private static final Pattern CREATE_TABLE =
            Pattern.compile("(?i)CREATE\\s+TABLE\\s+(?:IF\\s+NOT\\s+EXISTS\\s+)?([a-z_][a-z0-9_]*)");

    @Test
    @DisplayName("each is mapped by an @Entity's @Table, or named on the SQL-only list with its reason")
    void everyTableHasAnEntity() throws IOException {
        Set<String> missing = new TreeSet<>(acbMigrationTables().keySet());
        missing.removeAll(entityTables());
        missing.removeAll(SQL_ONLY.keySet());
        assertThat(missing).as("tables an ACB migration creates that no entity maps -- H2 would not have them").isEmpty();
    }

    @Test
    @DisplayName("the scan is not vacuous: it finds the ACB tables, and the SQL-only list names only real, unmapped tables")
    void theScanFindsWhatItShould() throws IOException {
        Map<String, String> tables = acbMigrationTables();
        assertThat(tables).containsKeys("acb_runs", "acb_run_lines", "acb_holidays", "acb_calendar");
        assertThat(tables).as("a fractional version's table is scanned too")
                .containsEntry("workplace_visit_drafts", "V69_1__workplace_visit_drafts.sql");
        assertThat(entityTables()).contains("acb_runs", "acb_holidays", "acb_calendar", "members");
        for (String sqlOnly : SQL_ONLY.keySet()) {
            assertThat(tables).as("%s is allowed as SQL-only but no ACB migration creates it", sqlOnly).containsKey(sqlOnly);
            assertThat(entityTables()).as("%s is allowed as SQL-only but an entity maps it -- take it off the list", sqlOnly)
                    .doesNotContain(sqlOnly);
        }
        // Canary: the reader finds both spellings, and nothing that is not a CREATE TABLE.
        assertThat(created("CREATE TABLE acb_x (id INT);\ncreate table if not exists acb_y (id INT); ALTER TABLE acb_z ADD c INT;"))
                .containsExactly("acb_x", "acb_y");
    }

    /** Table name to the migration that creates it, for V40 and after. */
    private static Map<String, String> acbMigrationTables() throws IOException {
        Map<String, String> tables = new TreeMap<>();
        try (Stream<Path> files = Files.list(MIGRATIONS)) {
            // A fractional version (V69_1) is a migration too: read by MigrationSeed's one rule, or this scan would
            // pass over it -- and over the table it creates -- in silence.
            for (Path file : files.filter(f -> f.getFileName().toString().matches("V\\d+(_\\d+)?__.*\\.sql")).toList()) {
                String name = file.getFileName().toString();
                BigDecimal version = new BigDecimal(MigrationSeed.versionOf(name));
                if (version.compareTo(BigDecimal.valueOf(FIRST_ACB_VERSION)) >= 0) {
                    for (String table : created(Files.readString(file, StandardCharsets.UTF_8))) {
                        tables.put(table, name);
                    }
                }
            }
        }
        return tables;
    }

    private static Set<String> created(String sql) {
        Set<String> names = new java.util.LinkedHashSet<>();
        Matcher m = CREATE_TABLE.matcher(sql);
        while (m.find()) {
            names.add(m.group(1).toLowerCase(java.util.Locale.ROOT));
        }
        return names;
    }

    private static Set<String> entityTables() {
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(Entity.class));
        Set<String> tables = new TreeSet<>();
        for (BeanDefinition candidate : scanner.findCandidateComponents("za.co.titandynamix.sasbo_online_backend")) {
            try {
                Class<?> type = Class.forName(candidate.getBeanClassName());
                Table table = type.getAnnotation(Table.class);
                tables.add(table != null && !table.name().isBlank()
                        ? table.name().toLowerCase(java.util.Locale.ROOT)
                        : type.getSimpleName().toLowerCase(java.util.Locale.ROOT));
            } catch (ClassNotFoundException unreachable) {
                throw new IllegalStateException(unreachable);
            }
        }
        return tables;
    }
}
