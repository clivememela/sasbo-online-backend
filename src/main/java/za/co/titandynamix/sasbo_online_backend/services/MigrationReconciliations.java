package za.co.titandynamix.sasbo_online_backend.services;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import za.co.titandynamix.sasbo_online_backend.acb.AcbActor;
import za.co.titandynamix.sasbo_online_backend.acb.AcbTime;
import za.co.titandynamix.sasbo_online_backend.dto.migration.MigrationMonthTotalsDto;
import za.co.titandynamix.sasbo_online_backend.dto.migration.MigrationReconciliationDto;
import za.co.titandynamix.sasbo_online_backend.exception.MigrationRefusal;
import za.co.titandynamix.sasbo_online_backend.exception.MigrationRefusalCode;
import za.co.titandynamix.sasbo_online_backend.legacy.reconcile.Dimension;
import za.co.titandynamix.sasbo_online_backend.legacy.reconcile.Measure;
import za.co.titandynamix.sasbo_online_backend.legacy.reconcile.Reconciliation;
import za.co.titandynamix.sasbo_online_backend.repository.MigrationBatchRepository;
import za.co.titandynamix.sasbo_online_backend.repository.MigrationBatchRepository.Batch;
import za.co.titandynamix.sasbo_online_backend.repository.MigrationRowRepository;
import za.co.titandynamix.sasbo_online_backend.repository.MigrationTotalsRepository;

import java.time.Clock;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Proving a move against SASBO's own figures (MIG-31, MIG-32): the old system's month-end totals for a month, entered
 * whole from its pack, and a batch set beside the totals of the month it represents -- figure by figure, with the rows
 * held back by errors beside each, so a difference can be explained before anything is accepted.
 */
@Slf4j
@Service
public class MigrationReconciliations {

    private final MigrationTotalsRepository totals;
    private final MigrationRowRepository rows;
    private final MigrationBatches batchService;
    private final Clock clock;
    private final TransactionTemplate transactions;

    public MigrationReconciliations(MigrationTotalsRepository totals, MigrationRowRepository rows,
                                    MigrationBatches batchService, Clock clock,
                                    PlatformTransactionManager transactionManager) {
        this.totals = totals;
        this.rows = rows;
        this.batchService = batchService;
        this.clock = clock;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    /** A month's figures as entered -- none, where none were. */
    public MigrationMonthTotalsDto totals(String monthText) {
        YearMonth month = month(monthText);
        List<MigrationTotalsRepository.Total> entered = totals.month(month.atDay(1));
        return dto(month, entered);
    }

    /** A month's figures, entered whole: each checked, then the month's replaced by them in one transaction. */
    public MigrationMonthTotalsDto replace(String monthText, MigrationMonthTotalsDto.Request request) {
        YearMonth month = month(monthText);
        List<MigrationTotalsRepository.Total> figures = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < request.figures().size(); i++) {
            MigrationMonthTotalsDto.Figure figure = request.figures().get(i);
            Dimension dimension = parse(Dimension.class, figure.dimension(), i, "dimension");
            Measure measure = parse(Measure.class, figure.measure(), i, "measure");
            if (!dimension.measures().contains(measure)) {
                throw invalid(i, "a " + dimension + " figure counts " + dimension.measures());
            }
            String key = Reconciliation.key(dimension, figure.key());
            if (dimension != Dimension.TOTAL && key.isEmpty()) {
                throw invalid(i, "a figure cut by " + dimension + " names the one it is about");
            }
            if (dimension.numericKey() && !key.matches("[0-9]{1,9}")) {
                throw invalid(i, "a " + dimension + " is a number");
            }
            if (!seen.add(dimension + "|" + key + "|" + measure)) {
                throw invalid(i, "the same figure twice");
            }
            figures.add(new MigrationTotalsRepository.Total(dimension.name(), key, measure.name(), figure.value(),
                    figure.source() == null || figure.source().isBlank() ? null : figure.source().strip(), null, null));
        }
        transactions.executeWithoutResult(status ->
                totals.replace(month.atDay(1), figures, AcbActor.current(), clock.instant()));
        log.info("Migration month-end figures entered for {}: {}", month, figures.size());
        return totals(monthText);
    }

    /** The batch beside its month's figures. Before any are entered, the lines are empty and nothing is proved. */
    public MigrationReconciliationDto reconcile(long batchId) {
        Batch batch = batchService.require(batchId);
        if (MigrationBatchRepository.RECEIVING.equals(batch.status())
                || MigrationBatchRepository.DRAFT.equals(batch.status())) {
            throw MigrationValidation.notInState(batch, "reconciled before it is checked",
                    "VALIDATED, COMMITTING, COMMITTED, ROLLING_BACK, ROLLED_BACK");
        }
        YearMonth month = YearMonth.from(batch.asAt());
        List<Reconciliation.Figure> legacy = totals.month(month.atDay(1)).stream()
                .map(total -> new Reconciliation.Figure(Dimension.valueOf(total.dimension()), total.key(),
                        Measure.valueOf(total.measure()), total.value()))
                .toList();
        Map<Dimension, Map<String, Reconciliation.Counts>> figures = rows.figures(batchId, month.atDay(1),
                month.plusMonths(1).atDay(1));
        List<Reconciliation.Line> lines = Reconciliation.compare(legacy, figures);
        Map<String, Long> summary = new LinkedHashMap<>();
        Reconciliation.summary(lines).forEach((status, count) -> summary.put(status.name(), count));
        long heldBack = figures.getOrDefault(Dimension.TOTAL, Map.of()).values().stream()
                .mapToLong(Reconciliation.Counts::heldBack).sum();
        return new MigrationReconciliationDto(batchId, month.toString(), batch.status(), legacy.size(), heldBack,
                Reconciliation.proved(lines), summary, lines.stream().map(line -> new MigrationReconciliationDto.Line(
                        line.dimension().name(), line.key(), line.measure().name(), line.legacy(), line.batch(),
                        line.heldBack(), line.difference(), line.status().name())).toList());
    }

    private YearMonth month(String text) {
        YearMonth month;
        try {
            month = YearMonth.parse(text == null ? "" : text.strip());
        } catch (DateTimeParseException unreadable) {
            throw new MigrationRefusal(MigrationRefusalCode.MONTH_INVALID, "Give the month as a year and a month: 2026-08.");
        }
        if (month.isAfter(AcbTime.currentMonth(clock))) {
            throw new MigrationRefusal(MigrationRefusalCode.MONTH_INVALID, "The month has not begun.",
                    MigrationRefusal.details("month", month.toString(), "currentMonth",
                            AcbTime.currentMonth(clock).toString()));
        }
        return month;
    }

    private static <E extends Enum<E>> E parse(Class<E> type, String value, int index, String field) {
        try {
            return Enum.valueOf(type, value == null ? "" : value.strip());
        } catch (IllegalArgumentException unknown) {
            throw invalid(index, "the " + field + " is not one the figures know");
        }
    }

    private static MigrationRefusal invalid(int index, String problem) {
        return new MigrationRefusal(MigrationRefusalCode.TOTALS_INVALID, "Figure " + (index + 1) + " cannot be entered: "
                + problem + ". Nothing was entered.", MigrationRefusal.details("index", String.valueOf(index),
                "problem", problem));
    }

    private static MigrationMonthTotalsDto dto(YearMonth month, List<MigrationTotalsRepository.Total> entered) {
        List<MigrationMonthTotalsDto.Figure> figures = entered.stream()
                .map(total -> new MigrationMonthTotalsDto.Figure(total.dimension(), total.key(), total.measure(),
                        total.value(), total.source()))
                .toList();
        String by = entered.isEmpty() ? null : entered.get(0).enteredBy();
        String at = entered.isEmpty() ? null : AcbTime.iso(entered.get(0).enteredAt());
        return new MigrationMonthTotalsDto(month.toString(), figures, by, at);
    }
}
