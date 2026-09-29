package za.co.titandynamix.sasbo_online_backend.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import za.co.titandynamix.sasbo_online_backend.config.MonthEndApi;
import za.co.titandynamix.sasbo_online_backend.dto.workplace.WorkplacePageDto;
import za.co.titandynamix.sasbo_online_backend.services.WorkplaceService;

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

    public WorkplaceController(WorkplaceService workplaces) {
        this.workplaces = workplaces;
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
}
