package za.co.titandynamix.sasbo_online_backend.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import za.co.titandynamix.sasbo_online_backend.config.MonthEndApi;
import za.co.titandynamix.sasbo_online_backend.dto.monthend.MonthEndStatisticDto;
import za.co.titandynamix.sasbo_online_backend.services.MonthEndStatistics;

/**
 * The statistics of a month, under {@code /api/month-end/{month}} (month end 04.3 to 04.8): each a set of sheets of
 * counts, for the whole or for one area or one region -- <em>"All regions get a copy of the relevant documents for
 * their area"</em>.
 *
 * <h2>Open, and why</h2>
 * A statistic is counts, and an open read like the figures of 02.2: under rule C a membership below five at a named
 * employer or table code never leaves the server, and the switch decides what is cut by where members work (MD62). A
 * LISTING names members, and refuses every caller before anything is read, until this system knows who is calling
 * (MD4).
 */
@MonthEndApi
@RestController
@RequestMapping("/api/month-end/{month}")
public class MonthEndStatisticsController {

    private final MonthEndStatistics statistics;

    public MonthEndStatisticsController(MonthEndStatistics statistics) {
        this.statistics = statistics;
    }

    /** 04.3: new and deleted, by area, core and other; the month and the year to date. */
    @GetMapping("/new-and-deleted")
    public MonthEndStatisticDto newAndDeleted(@PathVariable("month") String month,
                                              @RequestParam(required = false) String area,
                                              @RequestParam(required = false) String region) {
        return statistics.newAndDeleted(month, area, region);
    }

    /** 04.4: membership by group and by table code, how it is collected, the suspended by code. */
    @GetMapping("/membership")
    public MonthEndStatisticDto membership(@PathVariable("month") String month,
                                           @RequestParam(required = false) String area,
                                           @RequestParam(required = false) String region) {
        return statistics.membership(month, area, region);
    }

    /** 04.5: why members left. */
    @GetMapping("/leavers")
    public MonthEndStatisticDto leavers(@PathVariable("month") String month,
                                        @RequestParam(required = false) String area,
                                        @RequestParam(required = false) String region) {
        return statistics.leavers(month, area, region);
    }

    /** 04.6: the branches and their penetration. */
    @GetMapping("/branches")
    public MonthEndStatisticDto branches(@PathVariable("month") String month,
                                         @RequestParam(required = false) String area,
                                         @RequestParam(required = false) String region) {
        return statistics.branches(month, area, region);
    }

    /** 04.7: recruitment. */
    @GetMapping("/recruitment")
    public MonthEndStatisticDto recruitment(@PathVariable("month") String month,
                                            @RequestParam(required = false) String area,
                                            @RequestParam(required = false) String region) {
        return statistics.recruitment(month, area, region);
    }

    /** 04.8: representatives and committee members. */
    @GetMapping("/representatives")
    public MonthEndStatisticDto representatives(@PathVariable("month") String month,
                                                @RequestParam(required = false) String area,
                                                @RequestParam(required = false) String region) {
        return statistics.representatives(month, area, region);
    }

    /** 04.9: the branches due a visit -- counts; the manager's details are shown once sign-in exists. */
    @GetMapping("/visits")
    public MonthEndStatisticDto visits(@PathVariable("month") String month,
                                       @RequestParam(required = false) String area,
                                       @RequestParam(required = false) String region) {
        return statistics.visits(month, area, region);
    }

    /**
     * 🔒 A listing -- the month's new members, its deleted, the members a recruiter signed up, the elected
     * representatives -- names members, and is refused to every caller before anything is read (MD4).
     */
    @GetMapping("/listings/{listing}")
    public MonthEndStatisticDto listing(@PathVariable("listing") String listing) {
        throw MonthEndStatistics.listingRefused(listing);
    }
}
