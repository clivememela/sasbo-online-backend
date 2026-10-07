package za.co.titandynamix.sasbo_online_backend.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import za.co.titandynamix.sasbo_online_backend.config.MonthEndApi;
import za.co.titandynamix.sasbo_online_backend.dto.workplace.WorkplacePageDto;
import za.co.titandynamix.sasbo_online_backend.dto.workplace.WorkplaceVisitPageDto;
import za.co.titandynamix.sasbo_online_backend.dto.workplace.WorkplaceVisitRequest;
import za.co.titandynamix.sasbo_online_backend.services.WorkplaceService;
import za.co.titandynamix.sasbo_online_backend.services.WorkplaceVisitService;

/**
 * SASBO's workplaces, a page at a time (month end 01.2).
 *
 * <p>PAGED BY THE SERVER, two hundred a page at most as ACB's pages are, in the server's order -- the workplace's
 * name, then its R/A number -- and by nothing a request names (MR-SEC-22). Narrowed by employer, table code, area,
 * committee, and a search by name or R/A number. OPEN, as every read of organisations is -- and ANSWERED AS THE SWITCH
 * ALLOWS (MD62): at {@code none} an empty page and the reason, at {@code names} the workplaces without their staff
 * complement, at {@code all} all of it. A page that does not exist is {@code WORKPLACE_PAGE_INVALID}; a page number
 * that is a word is {@code REQUEST_MALFORMED}, not a 500.
 */
@MonthEndApi
@RestController
@RequestMapping("/api/workplaces")
public class WorkplaceController {

    private final WorkplaceService workplaces;
    private final WorkplaceVisitService visits;

    public WorkplaceController(WorkplaceService workplaces, WorkplaceVisitService visits) {
        this.workplaces = workplaces;
        this.visits = visits;
    }

    @GetMapping
    public WorkplacePageDto page(@RequestParam(required = false) Integer page,
                                 @RequestParam(required = false) Integer size,
                                 @RequestParam(required = false) Long employer,
                                 @RequestParam(required = false) Integer tableCode,
                                 @RequestParam(required = false) String area,
                                 @RequestParam(required = false) String committee,
                                 @RequestParam(required = false) String search) {
        return workplaces.page(page, size, employer, tableCode, area, committee, search);
    }

    /** A.1: a workplace's visits, a page at a time, newest first -- the day and the complement found (MD61, B1). */
    @GetMapping("/{id}/visits")
    public WorkplaceVisitPageDto visitsOf(@PathVariable Long id, @RequestParam(required = false) Integer page,
                                          @RequestParam(required = false) Integer size) {
        return visits.page(id, page, size);
    }

    /**
     * A.1: records a visit to a workplace. A visit that found another complement writes the workplace's, keeping what it
     * was. October build review: held as the visit drafts are -- {@code VISITS_CAPTURE}, and the caller's region or area
     * ({@code WorkplaceVisitService.record}) -- and still answered to a caller who is not signed in while sign-in is open,
     * whose scope is everything: the Location Visit form's one-step Save visit keeps working (contracts/signin.md).
     */
    @PreAuthorize("isAnonymous() or hasAuthority('VISITS_CAPTURE')")
    @PostMapping("/{id}/visits")
    public ResponseEntity<WorkplaceVisitPageDto.Visit> record(@PathVariable Long id,
                                                              @RequestBody WorkplaceVisitRequest visit) {
        return ResponseEntity.status(HttpStatus.CREATED).body(visits.record(id, visit));
    }
}
