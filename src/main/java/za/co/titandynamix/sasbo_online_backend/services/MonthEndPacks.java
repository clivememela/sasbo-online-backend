package za.co.titandynamix.sasbo_online_backend.services;

import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import za.co.titandynamix.sasbo_online_backend.acb.AcbActor;
import za.co.titandynamix.sasbo_online_backend.acb.AcbLogging;
import za.co.titandynamix.sasbo_online_backend.dto.monthend.MonthEndPackDto;
import za.co.titandynamix.sasbo_online_backend.exception.MonthEndRefusal;
import za.co.titandynamix.sasbo_online_backend.exception.MonthEndRefusalCode;
import za.co.titandynamix.sasbo_online_backend.repository.MonthEndFactsRepository.TakenMonth;
import za.co.titandynamix.sasbo_online_backend.repository.MonthEndPackRepository;
import za.co.titandynamix.sasbo_online_backend.repository.MonthEndPackRepository.Pack;
import za.co.titandynamix.sasbo_online_backend.repository.MonthEndPackRepository.Workbook;

import java.time.Clock;
import java.time.Duration;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.RejectedExecutionException;

/**
 * The statistics pack (month end 04.10): the two workbooks the Secretariat and the Management Committee are sent,
 * prepared as a JOB -- asked for, prepared off the request thread on month end's own launcher, polled, then fetched.
 *
 * <ul>
 *   <li>A taken month's pack, once READY, is that month's for good: asked again, it is the same pack. The open month is
 *       prepared afresh each time, being worked out as it stands;</li>
 *   <li>NO QUEUE (MR-PERF-11): one pack in preparation at a time, held by a partial unique index -- a pack asked for
 *       while one is being prepared is the one in hand where it is the same month's, and {@code PACK_BUSY} where it is
 *       not;</li>
 *   <li>A JOB THAT DIED IS FOUND (ME-21): a pack whose heartbeat is older than {@link #HEARTBEAT_LIMIT} is FAILED,
 *       interrupted -- at start, and whenever the packs are read. Its failure is said once, in one sentence;</li>
 *   <li>counts only, and open as the statistics are: until sign-in exists the one who asks is
 *       {@value AcbActor#SYSTEM} (PA-12).</li>
 * </ul>
 */
@Slf4j
@Service
public class MonthEndPacks {

    /** A pack whose job has not beaten for this long died with its instance. */
    static final Duration HEARTBEAT_LIMIT = Duration.ofMinutes(10);

    static final String INTERRUPTED = "Interrupted: the service restarted while this pack was being prepared. Ask for "
            + "it again.";

    private static final String XLSX = ".xlsx";

    private final MonthEndPackRepository packs;
    private final MonthEndTakes takes;
    private final MonthEndWorkbooks workbooks;
    private final MonthEndPackLauncher launcher;
    private final Clock clock;

    public MonthEndPacks(MonthEndPackRepository packs, MonthEndTakes takes, MonthEndWorkbooks workbooks,
                         MonthEndPackLauncher launcher, Clock clock) {
        this.packs = packs;
        this.takes = takes;
        this.workbooks = workbooks;
        this.launcher = launcher;
        this.clock = clock;
    }

    /** What asking gave: the pack, and whether this ask started it. */
    public record Asked(MonthEndPackDto pack, boolean started) {
    }

    /** A workbook of a pack: its file name and its bytes. */
    public record File(String name, byte[] bytes) {
    }

    /** A pack of {@code month}'s statistics: the one READY or in hand, or a new one started. */
    public Asked ask(String month) {
        YearMonth asked = takes.month(month);
        Optional<TakenMonth> taken = takes.taken(asked);
        YearMonth open = takes.openMonth();
        if (taken.isEmpty() && !asked.equals(open)) {
            throw new MonthEndRefusal(MonthEndRefusalCode.MONTH_NOT_TAKEN,
                    "That month was not taken, and a past month is read only from what was taken.",
                    MonthEndRefusal.details("month", asked.toString(), "open", open.toString()));
        }
        sweepInterrupted();
        if (taken.isPresent()) {
            Optional<Pack> ready = packs.latestReady(asked.atDay(1), taken.get().id());
            if (ready.isPresent()) {
                return new Asked(dto(ready.get()), false);
            }
        }
        Optional<Pack> inHand = packs.preparing();
        if (inHand.isPresent()) {
            return inHandOrBusy(inHand.get(), asked);
        }
        long id;
        try {
            id = packs.insertPreparing(asked.atDay(1), taken.map(TakenMonth::id).orElse(null), AcbActor.current(),
                    clock.instant());
        } catch (DataIntegrityViolationException anotherInHand) {
            // Another ask started one between the look and the write: the index is the arbiter.
            return inHandOrBusy(packs.preparing().orElseThrow(() -> anotherInHand), asked);
        }
        try {
            launcher.launch(() -> prepare(id, asked));
        } catch (RejectedExecutionException notStarted) {
            packs.fail(id, "Not started: the pack could not be started. Ask for it again.", clock.instant());
        }
        return new Asked(dto(packs.find(id).orElseThrow()), true);
    }

    private Asked inHandOrBusy(Pack inHand, YearMonth asked) {
        if (inHand.monthStart().equals(asked.atDay(1))) {
            return new Asked(dto(inHand), false);
        }
        throw new MonthEndRefusal(MonthEndRefusalCode.PACK_BUSY, "A statistics pack is being prepared for another "
                + "month. Ask again once it is ready.", MonthEndRefusal.details("month", asked.toString(),
                "inHand", YearMonth.from(inHand.monthStart()).toString(), "pack", String.valueOf(inHand.id())));
    }

    /** The job: both workbooks, a heartbeat between them, then READY -- or FAILED, said once. */
    void prepare(long id, YearMonth month) {
        try {
            byte[] statistics = workbooks.statistics(month.toString());
            packs.heartbeat(id, clock.instant());
            byte[] monthly = workbooks.monthly(month);
            if (!packs.ready(id, statistics, monthly, clock.instant())) {
                log.warn("Month end: statistics pack {} was swept before it finished, and its workbooks are dropped",
                        id);
            }
        } catch (RuntimeException failed) {
            packs.fail(id, "Failed: the workbooks could not be written (" + failed.getClass().getSimpleName()
                    + "). Ask for it again; if it fails again, the service's log says where.", clock.instant());
            log.warn("Month end: statistics pack {} could not be prepared", id, AcbLogging.scrubbed(failed));
        }
    }

    /** Every pack whose job went quiet: FAILED, interrupted (ME-21). */
    public int sweepInterrupted() {
        return packs.sweep(clock.instant().minus(HEARTBEAT_LIMIT), INTERRUPTED, clock.instant());
    }

    /** A pack, as the screen polls it. */
    public MonthEndPackDto status(long id) {
        sweepInterrupted();
        return dto(packs.find(id).orElseThrow(() -> notFound(id, null)));
    }

    /** One READY pack's workbook, by its name: {@value MonthEndWorkbooks#STATISTICS} or {@value MonthEndWorkbooks#MONTHLY}. */
    public File file(long id, String workbook) {
        Workbook which = switch (workbook) {
            case MonthEndWorkbooks.STATISTICS -> Workbook.STATISTICS;
            case MonthEndWorkbooks.MONTHLY -> Workbook.MONTHLY;
            default -> throw notFound(id, workbook);
        };
        Pack pack = packs.find(id).orElseThrow(() -> notFound(id, workbook));
        byte[] bytes = packs.file(id, which).orElseThrow(() -> notFound(id, workbook));
        return new File("month-end-" + YearMonth.from(pack.monthStart()) + "-" + workbook + XLSX, bytes);
    }

    private static MonthEndRefusal notFound(long id, String workbook) {
        return new MonthEndRefusal(MonthEndRefusalCode.PACK_NOT_FOUND, "There is no such statistics pack here, or it "
                + "is not ready yet.", MonthEndRefusal.details("pack", String.valueOf(id), "workbook",
                MonthEndRefusal.echo(workbook)));
    }

    private static MonthEndPackDto dto(Pack pack) {
        return new MonthEndPackDto(pack.id(), YearMonth.from(pack.monthStart()).toString(), pack.status(),
                pack.askedBy(), pack.startedAt().toString(), pack.finishedAt() == null ? null
                : pack.finishedAt().toString(), pack.failure(), MonthEndPackRepository.READY.equals(pack.status())
                ? List.of(MonthEndWorkbooks.STATISTICS, MonthEndWorkbooks.MONTHLY) : List.of());
    }
}
