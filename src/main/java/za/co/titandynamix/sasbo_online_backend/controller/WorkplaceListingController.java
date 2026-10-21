package za.co.titandynamix.sasbo_online_backend.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import za.co.titandynamix.sasbo_online_backend.config.MonthEndApi;
import za.co.titandynamix.sasbo_online_backend.dto.workplace.WorkplaceListingsDto;
import za.co.titandynamix.sasbo_online_backend.dto.workplace.WorkplaceMemberPageDto;
import za.co.titandynamix.sasbo_online_backend.dto.workplace.WorkplaceMembersDto;
import za.co.titandynamix.sasbo_online_backend.services.WorkplaceListingService;

import java.util.List;

/**
 * The Location Listing's two reads (October build, 5 Oct Show &amp; Tell): the workplaces of an organiser's batch
 * print, by R/A number -- organisations, as open as every read of the workplaces -- and the members at one workplace,
 * which name members and so need {@code MEMBERS_VIEW}, within the caller's region or area.
 */
@MonthEndApi
@RestController
@RequestMapping("/api/workplaces")
public class WorkplaceListingController {

    private final WorkplaceListingService listings;

    public WorkplaceListingController(WorkplaceListingService listings) {
        this.listings = listings;
    }

    /**
     * The workplaces whose R/A numbers are named -- {@code ?raNumbers=900001,900002}, fifty at most -- in the order
     * named, and the numbers that are none. Held by {@code REPORTS_PRINT} -- the catalogue's "organisers' reports, batch
     * print, location listings" -- and answered, as every route that worked before sign-in does, to a caller who is not
     * signed in while sign-in is open (contracts/signin.md).
     */
    @PreAuthorize("isAnonymous() or hasAuthority('REPORTS_PRINT')")
    @GetMapping("/listings")
    public WorkplaceListingsDto listings(@RequestParam(required = false) List<String> raNumbers) {
        return listings.listings(raNumbers);
    }

    /**
     * The members at each branch of a batch print, in one answer -- {@code ?ids=1,2,3}, the workplaces' ids the listings
     * answered, fifty at most -- as far as the caller's scope reaches. A listing that names members: {@code MEMBERS_VIEW}.
     */
    @PreAuthorize("hasAuthority('MEMBERS_VIEW')")
    @GetMapping("/members")
    public WorkplaceMembersDto membersOf(@RequestParam(required = false) List<String> ids) {
        return listings.membersOf(ids);
    }

    /** A page of the members on record at a workplace, as far as the caller's scope reaches. */
    @PreAuthorize("hasAuthority('MEMBERS_VIEW')")
    @GetMapping("/{id}/members")
    public WorkplaceMemberPageDto members(@PathVariable Long id, @RequestParam(required = false) Integer page,
                                          @RequestParam(required = false) Integer size) {
        return listings.members(id, page, size);
    }
}
