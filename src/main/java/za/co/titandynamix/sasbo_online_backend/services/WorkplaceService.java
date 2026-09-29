package za.co.titandynamix.sasbo_online_backend.services;

import za.co.titandynamix.sasbo_online_backend.dto.workplace.WorkplaceDto;
import za.co.titandynamix.sasbo_online_backend.dto.workplace.WorkplacePageDto;

import java.util.Optional;

/**
 * SASBO's workplaces, a page at a time (01.2) -- answered as the deployment's switch allows (MD62): nothing at
 * {@code none}, the workplaces without their staff complement at {@code names}, all of it at {@code all}.
 */
public interface WorkplaceService {

    /** The largest page there is: ACB's ceiling (MR-PERF-12). */
    int MAX_PAGE_SIZE = 200;

    /** The longest search term a caller may send (MR-SEC-22). */
    int MAX_SEARCH_LENGTH = 100;

    /**
     * A page of the workplaces, in the server's order.
     *
     * @param search a name, or an R/A number; ignored below two characters, refused {@code REQUEST_MALFORMED} above
     *               a hundred
     * @throws za.co.titandynamix.sasbo_online_backend.exception.MonthEndRefusal {@code WORKPLACE_PAGE_INVALID} for a
     *         page that does not exist; {@code REQUEST_MALFORMED} for a search term over its ceiling
     */
    WorkplacePageDto page(Integer page, Integer size, Long employer, Integer tableCode, String area, String committee,
                          String search);

    /**
     * One workplace and its lineage, by its id on record -- the lineage the answer for one member carries (01.3). As
     * the switch allows (MD62): empty at {@code none}, whatever the table holds; without the staff complement at
     * {@code names}. Empty where there is no such workplace.
     */
    Optional<WorkplaceDto> one(long id);
}
