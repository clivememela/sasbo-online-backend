package za.co.titandynamix.sasbo_online_backend.utils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import za.co.titandynamix.sasbo_online_backend.acb.AcbLogging;
import za.co.titandynamix.sasbo_online_backend.config.SasboEnvironment;
import za.co.titandynamix.sasbo_online_backend.enums.SeedFailure;

import java.io.IOException;
import java.io.InputStream;
import java.sql.Date;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Seeds SASBO's lists -- regions, areas, employer groups, employers, table codes, committee provinces, committees,
 * suspension codes, and SASBO's numbers on the deletion codes -- from {@value #RESOURCE}, which
 * {@code planning/branches/monthly-menu/legacy/reference.py} generates from SASBO's printed reports (month end 01.1).
 *
 * <h2>Each list whole or not at all</h2>
 * One transaction a list, written by JDBC batch ({@link ListSync}, MD44). What happened is recorded beside it in
 * {@code reference_lists} -- seeded, how many rows, when; or failed, in one of four words (ME-18, MR-SEC-21) -- and a
 * failure is recorded after the list's transaction has rolled back, so a list is never half seeded and never silently
 * missing. A list whose references failed fails too: it is refused, never written with holes.
 *
 * <h2>Who owns a list (MR-ROB-20, MD28)</h2>
 * The seeder, row by row, until SASBO confirms it: a row added to the resource reaches a database that holds the rest,
 * and a row the resource corrects is brought up to date. FROM THE DAY {@code confirmed_on} IS SET THE DATABASE OWNS
 * IT, and the seeder leaves that list alone. Where the production marker is set a list is seeded ONCE, and only when
 * the resource says SASBO has confirmed it and no row of it is on record -- until then nothing of SASBO's reports is
 * loaded there as SASBO's truth.
 *
 * <h2>Two starts at once (MR-ROB-23)</h2>
 * Every list has a unique key. The start that loses the race to insert a row is refused by the key, rolls back, finds
 * every row on record, and says so -- the rows are there once, and nothing is recorded as failed.
 *
 * <p>Called from {@link DataSeeder}, in month end's block, caught there (MD65). Never a runner of its own.
 */
@Slf4j
@Component
public class ReferenceListSeeder {

    /** The resource, on the classpath. */
    public static final String RESOURCE = "data/sasbo_reference_lists.json";

    /** The lists, in the order they are written: each after the lists its rows refer to. */
    public static final List<String> ORDER = List.of("regions", "areas", "employerGroups", "employers", "tableCodes",
            "committeeProvinces", "committees", "suspensionCodes", "deletionCodes");

    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;
    private final SasboEnvironment environment;
    private final Clock clock;
    private volatile Runnable beforeWrite = () -> { };

    public ReferenceListSeeder(JdbcTemplate jdbc, PlatformTransactionManager transactionManager,
                               SasboEnvironment environment, Clock clock) {
        this.jdbc = jdbc;
        this.transactions = new TransactionTemplate(transactionManager);
        this.environment = environment;
        this.clock = clock;
    }

    /** A test's hook, run after a list's keys are read and before anything is written -- where two starts race. */
    void beforeWrite(Runnable hook) {
        this.beforeWrite = hook == null ? () -> { } : hook;
    }

    public void seed() {
        seed(RESOURCE);
    }

    /**
     * The block's call (MD65, MV-58): the lists seeded -- and should the seeder itself throw, every list this start left
     * without an outcome is recorded FAILED, in the word for what was thrown, so the status read says the lists could
     * not be loaded rather than that they are not loaded yet. A list already on record keeps its record. The throw goes
     * on to the block, which logs it and starts the next seeder.
     */
    public void seedOrRecord() {
        try {
            seed();
        } catch (RuntimeException thrown) {
            SeedFailure word = SeedFailure.of(thrown);
            Map<String, Record> recorded = Map.of();
            try {
                recorded = recorded();
            } catch (RuntimeException unreadable) {
                log.error("What SASBO's lists hold could not be read either", AcbLogging.scrubbed(unreadable));
            }
            for (String list : ORDER) {
                if (!recorded.containsKey(list)) {
                    recordFailure(list, "unknown", null, word);
                }
            }
            throw thrown;
        }
    }

    /** Seeds from a resource on the classpath -- the real one, or a test's broken one. */
    void seed(String resourcePath) {
        JsonNode resource;
        try (InputStream stream = new ClassPathResource(resourcePath).getInputStream()) {
            resource = new ObjectMapper().readTree(stream);
        } catch (IOException | RuntimeException unreadable) {
            log.error("SASBO's lists could not be read from {}; none is seeded", resourcePath,
                    AcbLogging.scrubbed(unreadable));
            for (String list : ORDER) {
                recordFailure(list, "unknown", null, SeedFailure.UNREADABLE);
            }
            return;
        }
        seed(resource);
    }

    /** Seeds from a resource already read -- the real one, or a test's own, built from it. */
    void seed(JsonNode resource) {
        Map<String, Record> recorded = recorded();
        ListSync sync = new ListSync(jdbc, () -> beforeWrite.run());
        Map<String, Map<Object, Long>> ids = new HashMap<>();
        for (String list : ORDER) {
            JsonNode node = listNode(resource, list);
            String report = node == null ? "unknown" : node.path("report").asText("unknown");
            LocalDate printedOn = node == null ? null : date(node.path("printedOn"));
            Record record = recorded.get(list);
            if (record != null && record.confirmedOn() != null) {
                log.debug("SASBO's list {} is confirmed; the database owns it and it is left alone", list);
                continue;
            }
            boolean confirmedInResource = node != null && date(node.path("confirmedOn")) != null;
            if (environment.isProduction() && (!confirmedInResource || onRecord(list))) {
                continue;
            }
            List<Object[]> desired = new ArrayList<>();
            try {
                if (node == null) {
                    throw new SeedFailure.UnreadableRow(list + ": the resource has no such list");
                }
                Integer rows = transactions.execute(status -> write(list, node, sync, ids, desired, report, printedOn,
                        record));
                log.info("SASBO's list {}: {} row(s) on record", list, rows);
            } catch (DuplicateKeyException race) {
                if (settledByAnother(list, desired, sync)) {
                    log.info("SASBO's list {} was seeded by another start at the same moment; nothing more to do", list);
                    ids.remove(list);
                } else {
                    fail(list, report, printedOn, race);
                }
            } catch (RuntimeException failure) {
                fail(list, report, printedOn, failure);
            }
        }
    }

    // ------------------------------------------------------------------------------------------------ one list

    private Integer write(String list, JsonNode node, ListSync sync, Map<String, Map<Object, Long>> ids,
                          List<Object[]> desired, String report, LocalDate printedOn, Record record) {
        JsonNode rows = node.path("rows");
        if (!rows.isArray()) {
            throw new SeedFailure.UnreadableRow(list + ": its rows are not a list");
        }
        ListSync.Outcome outcome = switch (list) {
            case "regions" -> sync.sync("sasbo_regions", "code", List.of("name"),
                    each(rows, list, r -> new Object[] {text(r, "code", list), text(r, "name", list)}, desired),
                    null, null);
            case "areas" -> {
                Map<Object, Long> regions = idsOf("regions", ids, sync);
                yield sync.sync("sasbo_areas", "letter", List.of("region_id"),
                        each(rows, list, r -> new Object[] {text(r, "letter", list),
                                reference(regions, r.path("region"), list)}, desired), null, null);
            }
            case "employerGroups" -> {
                int[] ordinal = {0};
                yield sync.sync("employer_groups", "name", List.of("core", "ordinal"),
                        each(rows, list, r -> new Object[] {text(r, "name", list), bool(r, "core", list),
                                ++ordinal[0]}, desired), null, null);
            }
            case "employers" -> {
                Map<Object, Long> groups = idsOf("employerGroups", ids, sync);
                yield sync.sync("employers", "ordinal", List.of("name", "group_id"),
                        each(rows, list, r -> new Object[] {number(r, "ordinal", list), text(r, "name", list),
                                reference(groups, r.path("group"), list)}, desired), null, null);
            }
            case "tableCodes" -> {
                Map<Object, Long> employers = idsOf("employers", ids, sync);
                yield sync.sync("table_codes", "code", List.of("name", "employer_id", "part"),
                        each(rows, list, r -> new Object[] {number(r, "code", list), text(r, "name", list),
                                reference(employers, r.path("employer"), list), optional(r, "part")}, desired),
                        null, null);
            }
            case "committeeProvinces" -> sync.sync("committee_provinces", "letter", List.of(),
                    each(rows, list, r -> new Object[] {text(r, "letter", list)}, desired), null, null);
            case "committees" -> {
                Map<Object, Long> provinces = idsOf("committeeProvinces", ids, sync);
                yield sync.sync("committees", "code", List.of("province_id", "name", "recruiting_number"),
                        each(rows, list, r -> new Object[] {text(r, "code", list),
                                reference(provinces, r.path("province"), list), text(r, "name", list),
                                r.path("recruitingNumber").isNumber() ? r.path("recruitingNumber").asInt() : null},
                                desired), null, null);
            }
            case "suspensionCodes" -> sync.sync("suspension_codes", "code", List.of("name"),
                    each(rows, list, r -> new Object[] {number(r, "code", list), text(r, "name", list)}, desired),
                    null, null);
            case "deletionCodes" -> deletionCodes(rows, sync, desired);
            default -> throw new SeedFailure.UnreadableRow(list + ": not a list the seeder knows");
        };
        ids.put(list, outcome.ids());
        int count = rows.size();
        boolean recordStale = record == null || !"SEEDED".equals(record.outcome())
                || record.rowCount() == null || record.rowCount() != count
                || !java.util.Objects.equals(record.printedOn(), printedOn)
                || !java.util.Objects.equals(record.report(), report);
        if (outcome.wrote() || recordStale) {
            sync.record(list, report, printedOn, "SEEDED", null, count, clock.instant());
        }
        return count;
    }

    /**
     * SASBO's ten deletion codes keyed by SASBO's number -- never by the table's id (RC7) -- and the reasons for
     * resigning keyed by their name under code 2. A code an administrator added by hand is not touched.
     */
    private ListSync.Outcome deletionCodes(JsonNode rows, ListSync sync, List<Object[]> desired) {
        List<Object[]> numbered = new ArrayList<>();
        List<String> resigned = new ArrayList<>();
        Integer parentCode = null;
        for (JsonNode r : rows) {
            if (r.path("sasboCode").isNumber()) {
                numbered.add(new Object[] {r.path("sasboCode").asInt(), text(r, "name", "deletionCodes")});
            } else if (r.path("parent").isNumber()) {
                int parent = r.path("parent").asInt();
                if (parentCode != null && parentCode != parent) {
                    throw new SeedFailure.UnreadableRow("deletionCodes: reasons under more than one code");
                }
                parentCode = parent;
                resigned.add(text(r, "name", "deletionCodes"));
            } else {
                throw new SeedFailure.UnreadableRow("deletionCodes: a row with neither a number nor a parent");
            }
        }
        desired.addAll(numbered);
        ListSync.Outcome main = sync.sync("deletion_codes", "sasbo_code", List.of("name"), numbered,
                "sasbo_code IS NOT NULL", null);
        if (resigned.isEmpty()) {
            return main;
        }
        Long parentId = main.ids().get((long) parentCode);
        if (parentId == null) {
            throw new DataIntegrityViolationException("deletionCodes: the code the reasons stand under is not on record");
        }
        List<Object[]> reasons = resigned.stream().map(name -> new Object[] {name}).toList();
        desired.addAll(reasons);
        ListSync.Outcome sub = sync.sync("deletion_codes", "name", List.of(), reasons,
                "parent_id = " + parentId + " AND sasbo_code IS NULL", new Object[] {"parent_id", parentId});
        Map<Object, Long> ids = new HashMap<>(main.ids());
        return new ListSync.Outcome(main.inserted() + sub.inserted(), main.updated() + sub.updated(), ids);
    }

    // ------------------------------------------------------------------------------------------------ failures

    /**
     * After a start was refused by a key: is every row this start meant to write on record now? Then another start
     * wrote them at the same moment, and the rows are there once (MR-ROB-23).
     */
    private boolean settledByAnother(String list, List<Object[]> desired, ListSync sync) {
        if (desired.isEmpty()) {
            return false;
        }
        try {
            if (list.equals("deletionCodes")) {
                List<Object[]> numbered = desired.stream().filter(r -> r[0] instanceof Integer).toList();
                List<Object[]> reasons = desired.stream().filter(r -> r[0] instanceof String).toList();
                return sync.allOnRecord("deletion_codes", "sasbo_code", numbered, "sasbo_code IS NOT NULL")
                        && sync.allOnRecord("deletion_codes", "name", reasons,
                                "parent_id IS NOT NULL AND sasbo_code IS NULL");
            }
            String[] keyed = KEYED.get(list);
            return keyed != null && sync.allOnRecord(keyed[0], keyed[1], desired, null);
        } catch (RuntimeException couldNotLook) {
            return false;
        }
    }

    /** Each list's table and key -- written here, never taken from the resource. */
    private static final Map<String, String[]> KEYED = Map.of(
            "regions", new String[] {"sasbo_regions", "code"},
            "areas", new String[] {"sasbo_areas", "letter"},
            "employerGroups", new String[] {"employer_groups", "name"},
            "employers", new String[] {"employers", "ordinal"},
            "tableCodes", new String[] {"table_codes", "code"},
            "committeeProvinces", new String[] {"committee_provinces", "letter"},
            "committees", new String[] {"committees", "code"},
            "suspensionCodes", new String[] {"suspension_codes", "code"});

    private void fail(String list, String report, LocalDate printedOn, RuntimeException failure) {
        SeedFailure word = SeedFailure.of(failure);
        log.error("SASBO's list {} could not be seeded ({}); nothing of it was written", list, word,
                AcbLogging.scrubbed(failure));
        recordFailure(list, report, printedOn, word);
    }

    private void recordFailure(String list, String report, LocalDate printedOn, SeedFailure word) {
        try {
            transactions.executeWithoutResult(status -> new ListSync(jdbc, () -> { })
                    .record(list, report, printedOn, "FAILED", word.name(), null, clock.instant()));
        } catch (RuntimeException couldNotRecord) {
            log.error("What happened to SASBO's list {} could not be recorded either", list,
                    AcbLogging.scrubbed(couldNotRecord));
        }
    }

    // ------------------------------------------------------------------------------------------------ reading

    /** What {@code reference_lists} holds of each list -- one statement. */
    private Map<String, Record> recorded() {
        Map<String, Record> found = new HashMap<>();
        jdbc.query("SELECT list_key, report, printed_on, confirmed_on, outcome, row_count FROM reference_lists", rs -> {
            Date printed = rs.getDate("printed_on");
            Date confirmed = rs.getDate("confirmed_on");
            int rows = rs.getInt("row_count");
            boolean none = rs.wasNull();
            found.put(rs.getString("list_key"), new Record(rs.getString("report"),
                    printed == null ? null : printed.toLocalDate(), confirmed == null ? null : confirmed.toLocalDate(),
                    rs.getString("outcome"), none ? null : rows));
        });
        return found;
    }

    /** Whether any row of a list is on record -- asked only where the production marker is set. */
    private boolean onRecord(String list) {
        String table = switch (list) {
            case "regions" -> "sasbo_regions";
            case "areas" -> "sasbo_areas";
            case "employerGroups" -> "employer_groups";
            case "employers" -> "employers";
            case "tableCodes" -> "table_codes";
            case "committeeProvinces" -> "committee_provinces";
            case "committees" -> "committees";
            case "suspensionCodes" -> "suspension_codes";
            default -> "deletion_codes WHERE sasbo_code IS NOT NULL";
        };
        Long rows = jdbc.queryForObject("SELECT count(*) FROM " + table, Long.class);
        return rows != null && rows > 0;
    }

    private Map<Object, Long> idsOf(String list, Map<String, Map<Object, Long>> ids, ListSync sync) {
        return ids.computeIfAbsent(list, l -> switch (l) {
            case "regions" -> sync.ids("sasbo_regions", "code", null);
            case "employerGroups" -> sync.ids("employer_groups", "name", null);
            case "employers" -> sync.ids("employers", "ordinal", null);
            case "committeeProvinces" -> sync.ids("committee_provinces", "letter", null);
            default -> throw new IllegalStateException("no list " + l + " is referred to");
        });
    }

    private static JsonNode listNode(JsonNode resource, String list) {
        for (JsonNode node : resource.path("lists")) {
            if (list.equals(node.path("list").asText())) {
                return node;
            }
        }
        return null;
    }

    private interface RowReader {
        Object[] read(JsonNode row);
    }

    private static List<Object[]> each(JsonNode rows, String list, RowReader reader, List<Object[]> desired) {
        List<Object[]> read = new ArrayList<>();
        for (JsonNode row : rows) {
            if (!row.isObject()) {
                throw new SeedFailure.UnreadableRow(list + ": a row is not an object");
            }
            read.add(reader.read(row));
        }
        desired.addAll(read);
        return read;
    }

    private static String text(JsonNode row, String field, String list) {
        JsonNode value = row.path(field);
        if (!value.isTextual() || value.asText().isBlank()) {
            throw new SeedFailure.UnreadableRow(list + ": a row has no " + field);
        }
        return value.asText();
    }

    private static Integer number(JsonNode row, String field, String list) {
        JsonNode value = row.path(field);
        if (!value.canConvertToInt() || !value.isIntegralNumber()) {
            throw new SeedFailure.UnreadableRow(list + ": a row has no " + field);
        }
        return value.asInt();
    }

    private static Boolean bool(JsonNode row, String field, String list) {
        JsonNode value = row.path(field);
        if (!value.isBoolean()) {
            throw new SeedFailure.UnreadableRow(list + ": a row has no " + field);
        }
        return value.asBoolean();
    }

    private static String optional(JsonNode row, String field) {
        JsonNode value = row.path(field);
        return value.isTextual() && !value.asText().isBlank() ? value.asText() : null;
    }

    /** The id of a row another list refers to; {@code null} where the reference is empty; refused where it is absent. */
    private static Long reference(Map<Object, Long> ids, JsonNode key, String list) {
        if (key == null || key.isNull() || key.isMissingNode()) {
            return null;
        }
        Object normal = key.isNumber() ? (Object) key.asLong() : key.asText();
        Long id = ids.get(normal);
        if (id == null) {
            throw new DataIntegrityViolationException(list + ": a row refers to something not on record");
        }
        return id;
    }

    private static LocalDate date(JsonNode value) {
        if (value == null || !value.isTextual()) {
            return null;
        }
        try {
            return LocalDate.parse(value.asText());
        } catch (java.time.format.DateTimeParseException notADate) {
            throw new SeedFailure.UnreadableRow("a list's date is not a date");
        }
    }

    /** One row of {@code reference_lists}, as the seeder reads it. */
    private record Record(String report, LocalDate printedOn, LocalDate confirmedOn, String outcome, Integer rowCount) {
    }
}
