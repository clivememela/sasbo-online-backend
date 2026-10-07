package za.co.titandynamix.sasbo_online_backend.utils;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import za.co.titandynamix.sasbo_online_backend.acb.AcbTime;
import za.co.titandynamix.sasbo_online_backend.config.SasboEnvironment;
import za.co.titandynamix.sasbo_online_backend.dto.migration.MigrationMonthTotalsDto;
import za.co.titandynamix.sasbo_online_backend.legacy.reconcile.Dimension;
import za.co.titandynamix.sasbo_online_backend.legacy.reconcile.Reconciliation;
import za.co.titandynamix.sasbo_online_backend.repository.MigrationBatchRepository;
import za.co.titandynamix.sasbo_online_backend.repository.MigrationRowRepository;
import za.co.titandynamix.sasbo_online_backend.services.MigrationBatches;
import za.co.titandynamix.sasbo_online_backend.services.MigrationReconciliations;
import za.co.titandynamix.sasbo_online_backend.services.MigrationValidation;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.Charset;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The data migration's demonstration (October build: "seed data that reflects all of this work"): an invented legacy
 * export of 24 people -- registered as a batch, checked, and set beside invented month-end totals for the month it
 * represents -- so a fresh stack shows a batch with rows of every outcome, a report, and a reconciliation with no zeros.
 * Nothing is MOVED: no invented member enters the register, and no figure of the month end moves.
 *
 * <p>Read from {@code data/migration_demo_export.csv}, a template: the month it represents is the one before the
 * injected clock's (a past period), its joiners and leavers fall in that month, and its R/A numbers are workplaces on
 * record in areas L and W -- where the invented population stands -- or none, where the deployment's switch keeps
 * SASBO's workplaces off the record. The old system's totals are the batch's own counts with the rows it holds back
 * added back in: an old system counts members its errors are about, so the demonstration's one difference is exactly
 * the rows held back, which is what a real reconciliation most often shows.
 *
 * <p>Seeded WHEN ABSENT, by its key -- the file's name and month -- never where the production marker is set. Called
 * from {@code DataSeeder}'s caught block: a fault here never stops a start.
 */
@Slf4j
@Component
public class MigrationDemoSeeder {

    public static final String RESOURCE = "data/migration_demo_export.csv";
    public static final String FILE_NAME = "demonstration-member-export.csv";
    static final String MAPPING = "legacy-member-export-csv";

    private static final Charset WINDOWS_1252 = Charset.forName("windows-1252");
    private static final DateTimeFormatter LEGACY_DATE = DateTimeFormatter.ofPattern("d/M/yyyy");

    private final MigrationBatches batches;
    private final MigrationValidation validation;
    private final MigrationReconciliations reconciliations;
    private final MigrationBatchRepository batchRepository;
    private final MigrationRowRepository rows;
    private final JdbcTemplate jdbc;
    private final SasboEnvironment environment;
    private final Clock clock;

    public MigrationDemoSeeder(MigrationBatches batches, MigrationValidation validation,
                               MigrationReconciliations reconciliations, MigrationBatchRepository batchRepository,
                               MigrationRowRepository rows, JdbcTemplate jdbc, SasboEnvironment environment, Clock clock) {
        this.batches = batches;
        this.validation = validation;
        this.reconciliations = reconciliations;
        this.batchRepository = batchRepository;
        this.rows = rows;
        this.jdbc = jdbc;
        this.environment = environment;
        this.clock = clock;
    }

    /** Seeds the demonstration batch where it is absent. The batch's id, or {@code null} where nothing was seeded. */
    public Long seed() {
        if (environment.isProduction()) {
            log.info("The data migration's demonstration is not seeded under the production marker");
            return null;
        }
        YearMonth month = AcbTime.currentMonth(clock).minusMonths(1);
        LocalDate asAt = month.atEndOfMonth();
        if (batchRepository.existsNamed(FILE_NAME, asAt)) {
            return null;
        }
        MigrationBatches.Registered registered = batches.register(FILE_NAME, MAPPING, asAt.toString(), null,
                new ByteArrayInputStream(file(month).getBytes(WINDOWS_1252)));
        long id = registered.batch().id();
        if (!registered.created()) {
            return null;
        }
        validation.validateNow(id);
        reconciliations.replace(month.toString(), new MigrationMonthTotalsDto.Request(totals(id, month)));
        log.info("The data migration's demonstration: batch {} registered, checked and set beside its month", id);
        return id;
    }

    /** The template, filled in for the month. */
    String file(YearMonth month) {
        String template;
        try (InputStream stream = new ClassPathResource(RESOURCE).getInputStream()) {
            template = new String(stream.readAllBytes(), WINDOWS_1252);
        } catch (IOException unreadable) {
            throw new UncheckedIOException(unreadable);
        }
        List<Integer> workplaces = jdbc.queryForList("SELECT w.ra_number FROM workplaces w"
                + " JOIN sasbo_areas a ON a.id = w.area_id WHERE a.letter IN ('L', 'W') AND w.closed_on IS NULL"
                + " ORDER BY w.ra_number LIMIT 3", Integer.class);
        String filled = template
                .replace("{JOINED}", LEGACY_DATE.format(month.atDay(10)))
                .replace("{LEFT}", LEGACY_DATE.format(month.atDay(20)))
                .replace("{EARLIER}", LEGACY_DATE.format(month.minusMonths(2).atDay(1)));
        for (int i = 1; i <= 3; i++) {
            filled = filled.replace("{RA" + i + "}", workplaces.size() >= i ? String.valueOf(workplaces.get(i - 1)) : "");
        }
        return filled;
    }

    /**
     * The old system's figures, invented from the batch: its own counts, with the rows held back added to the whole
     * membership -- the one difference a demonstration shows, and the one a person can explain.
     */
    private List<MigrationMonthTotalsDto.Figure> totals(long id, YearMonth month) {
        Map<Dimension, Map<String, Reconciliation.Counts>> figures = rows.figures(id, month.atDay(1),
                month.plusMonths(1).atDay(1));
        List<MigrationMonthTotalsDto.Figure> totals = new ArrayList<>();
        Reconciliation.Counts all = figures.get(Dimension.TOTAL).get("");
        String pack = "demonstration";
        totals.add(figure("TOTAL", "", "ON_RECORD", all.onRecord() + all.heldBack(), pack));
        totals.add(figure("TOTAL", "", "SUSPENDED", all.suspended(), pack));
        totals.add(figure("TOTAL", "", "MEMBERS", all.members() + all.heldBack(), pack));
        totals.add(figure("TOTAL", "", "JOINED", all.joined(), pack));
        totals.add(figure("TOTAL", "", "LEFT", all.left(), pack));
        figures.get(Dimension.AREA).forEach((area, counts) -> {
            if (!area.isEmpty() && counts.members() > 0) {
                totals.add(figure("AREA", area, "MEMBERS", counts.members(), pack));
            }
        });
        figures.get(Dimension.SUSPENSION_CODE).forEach((code, counts) -> {
            if (!code.isEmpty() && counts.suspended() > 0) {
                totals.add(figure("SUSPENSION_CODE", code, "SUSPENDED", counts.suspended(), pack));
            }
        });
        figures.get(Dimension.DELETION_CODE).forEach((code, counts) -> {
            if (!code.isEmpty() && counts.left() > 0) {
                totals.add(figure("DELETION_CODE", code, "LEFT", counts.left(), pack));
            }
        });
        return totals;
    }

    private static MigrationMonthTotalsDto.Figure figure(String dimension, String key, String measure, long value,
                                                         String source) {
        return new MigrationMonthTotalsDto.Figure(dimension, key, measure, value, source);
    }
}
