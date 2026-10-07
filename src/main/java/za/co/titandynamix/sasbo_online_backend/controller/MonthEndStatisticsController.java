package za.co.titandynamix.sasbo_online_backend.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import za.co.titandynamix.sasbo_online_backend.config.MonthEndApi;
import za.co.titandynamix.sasbo_online_backend.dto.monthend.MonthEndListingDto;
import za.co.titandynamix.sasbo_online_backend.dto.monthend.MonthEndStatisticDto;
import za.co.titandynamix.sasbo_online_backend.services.MonthEndListings;
import za.co.titandynamix.sasbo_online_backend.services.MonthEndStatistics;

/**
 * The statistics of a month, under {@code /api/month-end/{month}} (month end 04.3 to 04.8): each a set of sheets of
 * counts, for the whole or for one area or one region -- <em>"All regions get a copy of the relevant documents for
 * their area"</em>.
 *
 * <h2>Open, and why</h2>
 * A statistic is counts, and an open read like the figures of 02.2: under rule C a membership below five at a named
 * employer or table code never leaves the server, and the switch decides what is cut by where members work (MD62). A
 * LISTING names members: held until sign-in (MD4), it is held by {@code MEMBERS_VIEW} now, and lists only the members
 * within the caller's region or area ({@link MonthEndListings}).
 */
@MonthEndApi
@RestController
@RequestMapping("/api/month-end/{month}")
public class MonthEndStatisticsController {

    private final MonthEndStatistics statistics;
    private final MonthEndListings listings;

    public MonthEndStatisticsController(MonthEndStatistics statistics, MonthEndListings listings) {
        this.statistics = statistics;
        this.listings = listings;
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
     * Stephne, MEN-18: the suspended within their banks and by SASBO's code, and by the reason for no payroll deduction
     * (MEN-17) -- counts, under rule C as every statistic is. The members themselves are a listing, held elsewhere.
     */
    @GetMapping("/suspensions")
    public MonthEndStatisticDto suspensions(@PathVariable("month") String month,
                                            @RequestParam(required = false) String area,
                                            @RequestParam(required = false) String region) {
        return statistics.suspensions(month, area, region);
    }

    /**
     * A listing -- the month's new members, its deleted, the members a recruiter signed up, the elected representatives
     * -- names members: {@code MEMBERS_VIEW}, a page at a time, within the caller's scope.
     */
    @PreAuthorize("hasAuthority('MEMBERS_VIEW')")
    @GetMapping("/listings/{listing}")
    public MonthEndListingDto listing(@PathVariable("month") String month, @PathVariable("listing") String listing,
                                      @RequestParam(required = false) String area,
                                      @RequestParam(required = false) String region,
                                      @RequestParam(required = false) Integer page,
                                      @RequestParam(required = false) Integer size) {
        return listings.listing(month, listing, area, region, page, size);
    }
}
