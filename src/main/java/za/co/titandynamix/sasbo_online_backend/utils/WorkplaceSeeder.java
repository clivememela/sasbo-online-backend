package za.co.titandynamix.sasbo_online_backend.utils;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
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
import za.co.titandynamix.sasbo_online_backend.config.WorkplaceSwitch;
import za.co.titandynamix.sasbo_online_backend.enums.SeedFailure;

import java.io.IOException;
import java.io.InputStream;
import java.sql.Date;
import java.time.Clock;
import java.time.Duration;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Seeds SASBO's workplaces -- the 5,507 branches and departments of their branch list -- from {@value #RESOURCE},
 * which {@code legacy/reference.py} generates from that list (month end 01.2). Organisations only: no manager, no
 * member, no count of members.
 *
 * <h2>Only where the switch says so (MD45, MD50, MD62)</h2>
 * The branch list is SASBO's own record, with every workplace's staff complement, and the hosted demonstration has no
 * sign-in. So nothing is seeded unless the deployment's switch is set ({@code names} or {@code all}); blank is
 * {@code none}. Switched back, the rows stay at rest -- nothing is deleted -- and the routes stop answering them.
 * Where the production marker is set, the list is seeded once, and only when the resource says SASBO has confirmed it.
 *
 * <h2>How it is written (MD44, MR-PERF-7, MR-PERF-17)</h2>
 * The resource is read as a stream, a workplace at a time. Absence is decided by ONE query of the R/A numbers on
 * record; the absent are inserted and the changed updated by JDBC batch, in ONE transaction, so the list is whole or
 * not there at all; a workplace the list no longer prints is CLOSED, from the day that list was printed, and never
 * deleted (MR-ROB-24). Its seconds are logged, to be read against the stack's 180-second health budget (BR48).
 *
 * <p>Keyed by the R/A number -- never by name: names repeat across employers. A workplace's table code, area and
 * committee are resolved against SASBO's lists; a workplace whose area is not on record is refused, never written with
 * a hole. Called from {@link DataSeeder}, after the lists, in month end's block, caught there (MD65).
 */
@Slf4j
@Component
public class WorkplaceSeeder {

    /** The resource, on the classpath. */
    public static final String RESOURCE = "data/sasbo_workplaces.json";

    /** The key {@code reference_lists} records the workplaces under. */
    public static final String LIST = "workplaces";

    private static final int BATCH = 500;

    private static final List<String> COLUMNS = List.of("name", "employer_printed", "table_code_id", "table_code_by",
            "area_id", "committee_code", "committee_id", "staff_complement", "cl_code");

    /** Where the staff complement stands among {@link #COLUMNS}. */
    private static final int COMPLEMENT = COLUMNS.indexOf("staff_complement");

    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;
    private final SasboEnvironment environment;
    private final WorkplaceSwitch workplaceSwitch;
    private final Clock clock;
    private volatile Runnable beforeWrite = () -> { };

    public WorkplaceSeeder(JdbcTemplate jdbc, PlatformTransactionManager transactionManager,
                           SasboEnvironment environment, WorkplaceSwitch workplaceSwitch, Clock clock) {
        this.jdbc = jdbc;
        this.transactions = new TransactionTemplate(transactionManager);
        this.environment = environment;
        this.workplaceSwitch = workplaceSwitch;
        this.clock = clock;
    }

    /** A test's hook, run after the keys on record are read and before anything is written. */
    void beforeWrite(Runnable hook) {
        this.beforeWrite = hook == null ? () -> { } : hook;
    }

    /** One workplace of the resource. */
    record Row(int raNumber, String name, String employer, Integer tableCode, String tableCodeBy, String area,
               String committee, int staffComplement, String clCode) {
    }

    /** The resource's heading and its workplaces. */
    record Resource(String report, LocalDate printedOn, LocalDate confirmedOn, int count, List<Row> rows) {
    }

    public void seed() {
        seed(RESOURCE);
    }

    /** Seeds from a resource on the classpath -- the real one, or a test's own. */
    void seed(String resourcePath) {
        if (!workplaceSwitch.workplacesOnRecord()) {
            log.info("SASBO's workplaces are not seeded here: the switch is off (SASBO_WORKPLACES is not set)");
            return;
        }
        long startedNanos = System.nanoTime();
        Resource resource;
        try (InputStream stream = new ClassPathResource(resourcePath).getInputStream()) {
            resource = read(stream);
        } catch (IOException | RuntimeException unreadable) {
            log.error("SASBO's workplaces could not be read from {}; none is seeded", resourcePath,
                    AcbLogging.scrubbed(unreadable));
            recordFailure("bank_master", null, SeedFailure.UNREADABLE);
            return;
        }
        seed(resource, startedNanos);
    }

    /** Seeds from workplaces already read -- the resource's, or a test's invented ones. */
    void seed(Resource resource, long startedNanos) {
        if (!workplaceSwitch.workplacesOnRecord()) {
            return;
        }
        Record recorded = recorded();
        if (recorded != null && recorded.confirmedOn() != null) {
            log.debug("SASBO's workplaces are confirmed; the database owns them and they are left alone");
            return;
        }
        if (environment.isProduction() && (resource.confirmedOn() == null || count() > 0)) {
            return;
        }
        try {
            int[] done = transactions.execute(status -> write(resource, recorded));
            long millis = Duration.ofNanos(System.nanoTime() - startedNanos).toMillis();
            log.info("SASBO's workplaces: {} on record, {} added, {} brought up to date, {} closed, in {} ms",
                    resource.rows().size(), done[0], done[1], done[2], millis);
        } catch (DuplicateKeyException race) {
            if (count() >= resource.rows().size()) {
                log.info("SASBO's workplaces were seeded by another start at the same moment; nothing more to do");
            } else {
                fail(resource, race);
            }
        } catch (RuntimeException failure) {
            fail(resource, failure);
        }
    }

    /** Writes the list in one transaction: {added, updated, closed}. */
    private int[] write(Resource resource, Record recorded) {
        Map<Object, Long> areas = ids("sasbo_areas", "letter");
        Map<Object, Long> tableCodes = ids("table_codes", "code");
        Map<Object, Long> committees = ids("committees", "code");
        Map<Long, Object[]> onRecord = new HashMap<>();
        Set<Long> open = new HashSet<>();
        jdbc.query("SELECT ra_number, " + String.join(", ", COLUMNS) + ", closed_on FROM workplaces", rs -> {
            long ra = rs.getLong(1);
            Object[] values = new Object[COLUMNS.size()];
            for (int i = 0; i < COLUMNS.size(); i++) {
                values[i] = ListSync.normal(rs.getObject(i + 2));
            }
            onRecord.put(ra, values);
            if (rs.getDate(COLUMNS.size() + 2) == null) {
                open.add(ra);
            }
        });
        // A.1: a complement a visit captured here wrote is a person's own reading of the branch, and the seed leaves it
        // as it is -- as the members' placement leaves a person's own choice (MD53). The complement is kept by hand from
        // organisers' visits (SASBO's reply, point 42), and the branch list is the older of the two.
        Set<Long> visited = new HashSet<>(jdbc.queryForList("SELECT DISTINCT w.ra_number FROM workplace_visits v "
                + "JOIN workplaces w ON w.id = v.workplace_id WHERE NOT v.seeded AND v.complement_found IS NOT NULL",
                Long.class));
        beforeWrite.run();
        List<Object[]> inserts = new ArrayList<>();
        List<Object[]> updates = new ArrayList<>();
        Set<Long> printed = new HashSet<>();
        for (Row row : resource.rows()) {
            Long area = areas.get(row.area());
            if (area == null) {
                throw new DataIntegrityViolationException("a workplace's area is not on record");
            }
            Long tableCode = row.tableCode() == null ? null : tableCodes.get((long) row.tableCode());
            if (row.tableCode() != null && tableCode == null) {
                throw new DataIntegrityViolationException("a workplace's table code is not on record");
            }
            Object[] values = {row.name(), row.employer(), tableCode, row.tableCodeBy(), area, row.committee(),
                    committees.get(row.committee()), row.staffComplement(), row.clCode()};
            long ra = row.raNumber();
            printed.add(ra);
            Object[] existing = onRecord.get(ra);
            if (existing != null && visited.contains(ra)) {
                values[COMPLEMENT] = existing[COMPLEMENT];
            }
            if (existing == null) {
                Object[] insert = new Object[values.length + 1];
                insert[0] = row.raNumber();
                System.arraycopy(values, 0, insert, 1, values.length);
                inserts.add(insert);
            } else if (!same(existing, values) || !open.contains(ra)) {
                Object[] update = new Object[values.length + 1];
                System.arraycopy(values, 0, update, 0, values.length);
                update[values.length] = row.raNumber();
                updates.add(update);
            }
        }
        List<Object[]> closes = new ArrayList<>();
        for (Long ra : open) {
            if (!printed.contains(ra)) {
                closes.add(new Object[] {Date.valueOf(resource.printedOn()), ra});
            }
        }
        for (int i = 0; i < inserts.size(); i += BATCH) {
            jdbc.batchUpdate("INSERT INTO workplaces (ra_number, " + String.join(", ", COLUMNS) + ") VALUES ("
                    + String.join(", ", java.util.Collections.nCopies(COLUMNS.size() + 1, "?")) + ")",
                    inserts.subList(i, Math.min(i + BATCH, inserts.size())));
        }
        for (int i = 0; i < updates.size(); i += BATCH) {
            jdbc.batchUpdate("UPDATE workplaces SET " + String.join(", ", COLUMNS.stream().map(c -> c + " = ?").toList())
                    + ", closed_on = NULL WHERE ra_number = ?", updates.subList(i, Math.min(i + BATCH, updates.size())));
        }
        if (!closes.isEmpty()) {
            jdbc.batchUpdate("UPDATE workplaces SET closed_on = ? WHERE ra_number = ?", closes);
        }
        boolean wrote = !inserts.isEmpty() || !updates.isEmpty() || !closes.isEmpty();
        boolean recordStale = recorded == null || !"SEEDED".equals(recorded.outcome())
                || recorded.rowCount() == null || recorded.rowCount() != resource.rows().size()
                || !java.util.Objects.equals(recorded.printedOn(), resource.printedOn());
        if (wrote || recordStale) {
            new ListSync(jdbc, () -> { }).record(LIST, resource.report(), resource.printedOn(), "SEEDED", null,
                    resource.rows().size(), clock.instant());
        }
        return new int[] {inserts.size(), updates.size(), closes.size()};
    }

    // ------------------------------------------------------------------------------------------------ reading

    /**
     * The resource, read a workplace at a time: its heading, then each workplace, checked for its shape. The file
     * states how many workplaces it holds, and a file whose rows are not that many is unreadable.
     */
    static Resource read(InputStream stream) throws IOException {
        ObjectMapper json = new ObjectMapper();
        String report = null;
        LocalDate printedOn = null;
        LocalDate confirmedOn = null;
        Integer count = null;
        List<Row> rows = new ArrayList<>();
        try (JsonParser parser = json.getFactory().createParser(stream)) {
            if (parser.nextToken() != JsonToken.START_OBJECT) {
                throw new SeedFailure.UnreadableRow("workplaces: the resource is not an object");
            }
            while (parser.nextToken() == JsonToken.FIELD_NAME) {
                String field = parser.currentName();
                parser.nextToken();
                switch (field) {
                    case "report" -> report = parser.getValueAsString();
                    case "printedOn" -> printedOn = parser.currentToken() == JsonToken.VALUE_NULL ? null
                            : LocalDate.parse(parser.getValueAsString());
                    case "confirmedOn" -> confirmedOn = parser.currentToken() == JsonToken.VALUE_NULL ? null
                            : LocalDate.parse(parser.getValueAsString());
                    case "count" -> count = parser.getIntValue();
                    case "workplaces" -> {
                        if (parser.currentToken() != JsonToken.START_ARRAY) {
                            throw new SeedFailure.UnreadableRow("workplaces: they are not a list");
                        }
                        while (parser.nextToken() == JsonToken.START_OBJECT) {
                            rows.add(row(json.readTree(parser)));
                        }
                    }
                    default -> parser.skipChildren();
                }
            }
        } catch (java.time.format.DateTimeParseException notADate) {
            throw new SeedFailure.UnreadableRow("workplaces: a date is not a date");
        }
        if (report == null || printedOn == null || count == null || count != rows.size()) {
            throw new SeedFailure.UnreadableRow("workplaces: the heading does not state what the rows hold");
        }
        return new Resource(report, printedOn, confirmedOn, count, rows);
    }

    private static Row row(JsonNode node) {
        JsonNode ra = node.path("raNumber");
        JsonNode complement = node.path("staffComplement");
        if (!ra.isIntegralNumber() || !complement.isIntegralNumber() || complement.asInt() < 0) {
            throw new SeedFailure.UnreadableRow("workplaces: a workplace has no R/A number or complement");
        }
        String tableCodeBy = node.path("tableCodeBy").isTextual() ? node.path("tableCodeBy").asText() : null;
        Integer tableCode = node.path("tableCode").isIntegralNumber() ? node.path("tableCode").asInt() : null;
        if ((tableCode == null) != (tableCodeBy == null)) {
            throw new SeedFailure.UnreadableRow("workplaces: a table code without its proof, or a proof without a code");
        }
        return new Row(ra.asInt(), text(node, "name"), text(node, "employer"), tableCode, tableCodeBy,
                text(node, "area"), text(node, "committee"), complement.asInt(), text(node, "clCode"));
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (!value.isTextual() || value.asText().isBlank()) {
            throw new SeedFailure.UnreadableRow("workplaces: a workplace has no " + field);
        }
        return value.asText();
    }

    private Map<Object, Long> ids(String table, String key) {
        return new ListSync(jdbc, () -> { }).ids(table, key, null);
    }

    private long count() {
        Long rows = jdbc.queryForObject("SELECT count(*) FROM workplaces", Long.class);
        return rows == null ? 0 : rows;
    }

    private Record recorded() {
        List<Record> found = jdbc.query("SELECT printed_on, confirmed_on, outcome, row_count FROM reference_lists "
                + "WHERE list_key = ?", (rs, n) -> {
                    Date printed = rs.getDate(1);
                    Date confirmed = rs.getDate(2);
                    int rows = rs.getInt(4);
                    boolean none = rs.wasNull();
                    return new Record(printed == null ? null : printed.toLocalDate(),
                            confirmed == null ? null : confirmed.toLocalDate(), rs.getString(3), none ? null : rows);
                }, LIST);
        return found.isEmpty() ? null : found.get(0);
    }

    private static boolean same(Object[] onRecord, Object[] desired) {
        for (int i = 0; i < onRecord.length; i++) {
            if (!java.util.Objects.equals(ListSync.normal(onRecord[i]), ListSync.normal(desired[i]))) {
                return false;
            }
        }
        return true;
    }

    // ------------------------------------------------------------------------------------------------ failures

    private void fail(Resource resource, RuntimeException failure) {
        SeedFailure word = SeedFailure.of(failure);
        log.error("SASBO's workplaces could not be seeded ({}); nothing of them was written", word,
                AcbLogging.scrubbed(failure));
        recordFailure(resource.report(), resource.printedOn(), word);
    }

    private void recordFailure(String report, LocalDate printedOn, SeedFailure word) {
        try {
            transactions.executeWithoutResult(status -> new ListSync(jdbc, () -> { })
                    .record(LIST, report, printedOn, "FAILED", word.name(), null, clock.instant()));
        } catch (RuntimeException couldNotRecord) {
            log.error("What happened to SASBO's workplaces could not be recorded either",
                    AcbLogging.scrubbed(couldNotRecord));
        }
    }

    /** What {@code reference_lists} holds of the workplaces. */
    private record Record(LocalDate printedOn, LocalDate confirmedOn, String outcome, Integer rowCount) {
    }
}
