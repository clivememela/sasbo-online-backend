package za.co.titandynamix.sasbo_online_backend.utils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import za.co.titandynamix.sasbo_online_backend.acb.AcbLogging;
import za.co.titandynamix.sasbo_online_backend.config.SasboEnvironment;
import za.co.titandynamix.sasbo_online_backend.config.WorkplaceSwitch;

import java.io.IOException;
import java.io.InputStream;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * SASBO's own history of branch visits (month end A.1): the months the branch list prints for each workplace -- LAST
 * VISITED, ten a workplace -- as seeded visits, their day unknown, beside the visits captured here.
 *
 * <p>Read from {@code data/sasbo_workplace_visits.json}, which {@code legacy/reference.py --resource} writes from the
 * branch list of 31 August 2026 and checks byte for byte: organisations and months only, no person and no count. A
 * resource of its own, tested as a file and named in {@code .gitattributes} (BR54).
 *
 * <p>Seeded only where the deployment's switch puts SASBO's workplaces on record (MD45, MD53) -- a visit is SASBO's
 * record as the complement is -- and where the production marker is set, only once SASBO has confirmed the list
 * ({@code confirmedOn}), as the workplaces are. When absent, by workplace and month: a second start writes nothing
 * again, and the database's {@code workplace_visits_seeded_once} refuses a twin. Written by JDBC batch (MD44), after
 * the workplaces it stands on, in {@code DataSeeder}'s month-end block, its failure caught there (MD65).
 */
@Slf4j
@Component
public class WorkplaceVisitSeeder {

    public static final String RESOURCE = "data/sasbo_workplace_visits.json";

    private static final int BATCH = 500;

    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;
    private final SasboEnvironment environment;
    private final WorkplaceSwitch workplaceSwitch;
    private final Clock clock;

    public WorkplaceVisitSeeder(JdbcTemplate jdbc, PlatformTransactionManager transactionManager,
                                SasboEnvironment environment, WorkplaceSwitch workplaceSwitch, Clock clock) {
        this.jdbc = jdbc;
        this.transactions = new TransactionTemplate(transactionManager);
        this.environment = environment;
        this.workplaceSwitch = workplaceSwitch;
        this.clock = clock;
    }

    /** One workplace's months, newest first, as the file holds them. */
    record Row(int raNumber, List<YearMonth> visits) {
    }

    record Resource(boolean confirmed, List<Row> rows) {
    }

    public void seed() {
        seed(RESOURCE);
    }

    void seed(String resourcePath) {
        if (!workplaceSwitch.workplacesOnRecord()) {
            log.info("SASBO's branch visits are not seeded here: the switch is off (SASBO_WORKPLACES is not set)");
            return;
        }
        long startedNanos = System.nanoTime();
        Resource resource;
        try (InputStream stream = new ClassPathResource(resourcePath).getInputStream()) {
            resource = read(stream);
        } catch (IOException | RuntimeException unreadable) {
            log.error("SASBO's branch visits could not be read from {}; none is seeded", resourcePath,
                    AcbLogging.scrubbed(unreadable));
            return;
        }
        if (environment.isProduction() && !resource.confirmed()) {
            log.info("SASBO's branch visits are not seeded under the production marker until SASBO confirms the list");
            return;
        }
        try {
            int[] done = transactions.execute(status -> write(resource));
            log.info("SASBO's branch visits: {} added for {} workplaces, in {} ms", done[0], done[1],
                    Duration.ofNanos(System.nanoTime() - startedNanos).toMillis());
        } catch (DuplicateKeyException race) {
            log.info("SASBO's branch visits were seeded by another start at the same moment; nothing more to do");
        }
    }

    static Resource read(InputStream stream) throws IOException {
        JsonNode root = new ObjectMapper().readTree(stream);
        List<Row> rows = new ArrayList<>();
        for (JsonNode workplace : root.path("workplaces")) {
            List<YearMonth> months = new ArrayList<>();
            for (JsonNode month : workplace.path("visits")) {
                months.add(YearMonth.parse(month.asText()));
            }
            rows.add(new Row(workplace.path("raNumber").asInt(), List.copyOf(months)));
        }
        return new Resource(!root.path("confirmedOn").isNull() && !root.path("confirmedOn").isMissingNode(),
                List.copyOf(rows));
    }

    /**
     * When one of SASBO's printed visits is on record from: the first day of the month it was made in, in SASBO's zone --
     * never later than now. A month taken reads only the visits on record when it was taken (MD19), and the seed dates
     * its own takes at the end of each past month (MonthEndTakeSeeder); stamped with the start's instant instead, every
     * printed visit would postdate every seeded take, and each taken month's branches would all read "never visited".
     */
    static Timestamp knownFrom(YearMonth month, Timestamp now) {
        Timestamp from = Timestamp.from(month.atDay(1).atStartOfDay(MonthRange.ZONE).toInstant());
        return from.before(now) ? from : now;
    }

    private int[] write(Resource resource) {
        Map<Integer, Long> workplaceOf = new HashMap<>();
        jdbc.query("SELECT id, ra_number FROM workplaces", rs -> {
            workplaceOf.put(rs.getInt("ra_number"), rs.getLong("id"));
        });
        Set<String> seeded = new HashSet<>();
        jdbc.query("SELECT workplace_id, visited_on FROM workplace_visits WHERE seeded", rs -> {
            seeded.add(rs.getLong(1) + "@" + rs.getDate(2).toLocalDate());
        });
        Timestamp now = Timestamp.from(clock.instant());
        List<Object[]> inserts = new ArrayList<>();
        Set<Long> workplaces = new HashSet<>();
        for (Row row : resource.rows()) {
            Long workplace = workplaceOf.get(row.raNumber());
            if (workplace == null) {
                continue;
            }
            for (YearMonth month : row.visits()) {
                if (seeded.add(workplace + "@" + month.atDay(1))) {
                    inserts.add(new Object[] {workplace, Date.valueOf(month.atDay(1)), knownFrom(month, now)});
                    workplaces.add(workplace);
                }
            }
        }
        for (int i = 0; i < inserts.size(); i += BATCH) {
            jdbc.batchUpdate("INSERT INTO workplace_visits (workplace_id, visited_on, day_known, seeded, captured_at) "
                    + "VALUES (?, ?, FALSE, TRUE, ?)", inserts.subList(i, Math.min(i + BATCH, inserts.size())));
        }
        return new int[] {inserts.size(), workplaces.size()};
    }
}
