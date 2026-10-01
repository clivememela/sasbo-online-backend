package za.co.titandynamix.sasbo_online_backend.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import za.co.titandynamix.sasbo_online_backend.config.MonthEndApi;
import za.co.titandynamix.sasbo_online_backend.dto.monthend.MonthEndMonthDto;
import za.co.titandynamix.sasbo_online_backend.dto.monthend.MonthEndMonthsDto;
import za.co.titandynamix.sasbo_online_backend.exception.MonthEndRefusal;
import za.co.titandynamix.sasbo_online_backend.exception.MonthEndRefusalCode;
import za.co.titandynamix.sasbo_online_backend.services.MonthEndTakes;

import java.time.YearMonth;
import java.util.Map;

/**
 * The month taken, under {@code /api/month-end} (month end 04.2).
 *
 * <h2>What is open, and what refuses</h2>
 * Which months are taken, when and by whom, is open like every read of this arc: it names no member and no workplace.
 * TAKING a month freezes its figures for good, so the route is built and REFUSES EVERY CALLER, {@code
 * IDENTITY_REQUIRED}, until this system knows who is calling -- as ACB's approval does (MD4). Before it refuses it says
 * what it knows of the month, because that is no secret -- the month has not begun, it has been taken already and by
 * whom, nothing could be taken here -- so the one who asks learns why before learning who may. The take itself is
 * proved in the service, where the seed calls it.
 */
@MonthEndApi
@RestController
@RequestMapping("/api/month-end")
public class MonthEndController {

    private final MonthEndTakes takes;

    public MonthEndController(MonthEndTakes takes) {
        this.takes = takes;
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

    /** 🔒 Refused to every caller until sign-in exists -- after saying what is known of the month. */
    @PostMapping("/{month}/take")
    public MonthEndMonthDto take(@PathVariable("month") String month) {
        YearMonth asked = takes.month(month);
        takes.refuseIfTaken(asked);
        takes.refuseIfNotReady();
        throw new MonthEndRefusal(MonthEndRefusalCode.IDENTITY_REQUIRED, "Taking a month freezes its figures for "
                + "good, so it waits until administrators sign in to this system. Nothing has been taken.",
                Map.of("month", asked.toString()));
    }
}
