package za.co.titandynamix.sasbo_online_backend.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import za.co.titandynamix.sasbo_online_backend.acb.AcbActor;
import za.co.titandynamix.sasbo_online_backend.config.MonthEndApi;
import za.co.titandynamix.sasbo_online_backend.dto.monthend.MonthEndMonthDto;
import za.co.titandynamix.sasbo_online_backend.dto.monthend.MonthEndMonthsDto;
import za.co.titandynamix.sasbo_online_backend.dto.monthend.MonthEndTakeRequest;
import za.co.titandynamix.sasbo_online_backend.services.AuditTrail;
import za.co.titandynamix.sasbo_online_backend.services.MonthEndTakes;

import java.time.YearMonth;

/**
 * The month taken, under {@code /api/month-end} (month end 04.2).
 *
 * <h2>What is open, and what is held</h2>
 * Which months are taken, when and by whom, is open like every read of this arc: it names no member and no workplace.
 * TAKING a month freezes its figures for good, so it waited for sign-in (MD4) and is now held by
 * {@code MONTH_END_TAKE} (October build, contracts/permissions.md): the take, and the take again (04.11) that names the
 * take it replaces -- by anybody holding the permission, not only the person who took it (Stephne, MEN-10: "the same
 * position that took it"). The taker is recorded by name ({@code AcbActor}), and the take is a line in the record of
 * who did what.
 */
@MonthEndApi
@RestController
@RequestMapping("/api/month-end")
public class MonthEndController {

    private final MonthEndTakes takes;
    private final AuditTrail audit;

    public MonthEndController(MonthEndTakes takes, AuditTrail audit) {
        this.takes = takes;
        this.audit = audit;
    }

    /** The months taken, the newest first, and the open month. */
    @GetMapping("/months")
    public MonthEndMonthsDto months() {
        return takes.months();
    }

    /** One month: taken, or the open month; a past month not taken is {@code MONTH_NOT_TAKEN}. */
    @GetMapping("/{month}")
    public MonthEndMonthDto month(@PathVariable("month") String month) {
        return takes.record(month);
    }

    /**
     * Takes the month -- or takes it again, naming the take it replaces (04.11) -- and answers the month as it now
     * stands. A first take of a month taken already is {@code MONTH_ALREADY_TAKEN}, said before anything is written.
     */
    @PreAuthorize("hasAuthority('MONTH_END_TAKE')")
    @PostMapping("/{month}/take")
    public MonthEndMonthDto take(@PathVariable("month") String month,
                                 @RequestBody(required = false) MonthEndTakeRequest request) {
        YearMonth asked = takes.month(month);
        Long replaces = request == null ? null : request.replaces();
        if (replaces == null) {
            takes.refuseIfTaken(asked);
        }
        // The line in the record goes in the take's own transaction: should it fail, nothing is taken.
        takes.take(asked, AcbActor.current(), replaces, taken -> audit.record(
                replaces == null ? "MONTH_END_TAKEN" : "MONTH_END_TAKEN_AGAIN", "MONTH_END", asked.toString(),
                replaces == null ? "Taken." : "Taken again, replacing take " + replaces + "."));
        return takes.record(asked.toString());
    }
}
