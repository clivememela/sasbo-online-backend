package za.co.titandynamix.sasbo_online_backend.services.impl;

import org.springframework.stereotype.Service;
import za.co.titandynamix.sasbo_online_backend.config.SasboEnvironment;
import za.co.titandynamix.sasbo_online_backend.config.WorkplaceSwitch;
import za.co.titandynamix.sasbo_online_backend.dto.workplace.WorkplacePageDto;
import za.co.titandynamix.sasbo_online_backend.enums.ListEmptyReason;
import za.co.titandynamix.sasbo_online_backend.exception.MonthEndRefusal;
import za.co.titandynamix.sasbo_online_backend.exception.MonthEndRefusalCode;
import za.co.titandynamix.sasbo_online_backend.repository.SasboReferenceRepository;
import za.co.titandynamix.sasbo_online_backend.repository.WorkplaceRepository;
import za.co.titandynamix.sasbo_online_backend.services.WorkplaceService;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * SASBO's workplaces, a page at a time, answered as the switch allows (01.2, MD62).
 *
 * <p>ASKS THE SWITCH FIRST. At {@code none} nothing is read: the page is empty and says the workplaces are not loaded
 * here -- whatever the table holds, because a switch set back must stop what was published from being answered. At
 * {@code names} the rows are answered without their staff complement (MR-SEC-19); at {@code all}, with it.
 *
 * <p>A page that does not exist is refused {@code WORKPLACE_PAGE_INVALID} -- a negative page, a size outside one to
 * two hundred, or a page past the last -- never answered as an empty page that looks like the end of the list. A text
 * over its ceiling is {@code REQUEST_MALFORMED}, naming the field and giving nothing of it back (MR-SEC-22, ME-14).
 */
@Service
public class WorkplaceServiceImpl implements WorkplaceService {

    static final int DEFAULT_PAGE_SIZE = 50;

    private final WorkplaceRepository workplaces;
    private final SasboReferenceRepository lists;
    private final WorkplaceSwitch workplaceSwitch;
    private final SasboEnvironment environment;

    public WorkplaceServiceImpl(WorkplaceRepository workplaces, SasboReferenceRepository lists,
                                WorkplaceSwitch workplaceSwitch, SasboEnvironment environment) {
        this.workplaces = workplaces;
        this.lists = lists;
        this.workplaceSwitch = workplaceSwitch;
        this.environment = environment;
    }

    @Override
    public WorkplacePageDto page(Integer page, Integer size, Long employer, Integer tableCode, String area,
                                 String committee, String search) {
        int number = page == null ? 0 : page;
        int rows = size == null ? DEFAULT_PAGE_SIZE : size;
        if (number < 0 || rows < 1 || rows > MAX_PAGE_SIZE) {
            throw pageInvalid(number, rows);
        }
        WorkplaceRepository.Filter filter = new WorkplaceRepository.Filter(employer, tableCode,
                code("area", area, 4), code("committee", committee, 3), term(search));
        if (!workplaceSwitch.workplacesOnRecord()) {
            return new WorkplacePageDto(List.of(), number, rows, 0, 0, ListEmptyReason.NOT_LOADED.name());
        }
        WorkplaceRepository.Slice slice = workplaces.page(filter, number, rows, workplaceSwitch.staffNumbersServed());
        if (slice.rows().isEmpty()) {
            if (number > 0) {
                throw pageInvalid(number, rows);
            }
            return new WorkplacePageDto(List.of(), 0, rows, 0, 0, whyNone());
        }
        int pages = (int) ((slice.total() + rows - 1) / rows);
        return new WorkplacePageDto(slice.rows(), number, rows, slice.total(), pages, null);
    }

    /** Why a page came back empty: nothing at all, where no workplace is on record; else a filter found none. */
    private String whyNone() {
        long onRecord = workplaces.count();
        if (onRecord > 0) {
            return null;
        }
        SasboReferenceRepository.ListRecord record = lists.recorded().get(ReferenceListServiceImpl.WORKPLACES);
        ListEmptyReason reason = ReferenceListServiceImpl.reasonFor(0, record, environment.isProduction());
        return reason == null ? null : reason.name();
    }

    /** A search term: trimmed; ignored below two characters; refused above a hundred, naming the field only. */
    private static String term(String search) {
        if (search == null) {
            return null;
        }
        String trimmed = search.trim();
        if (trimmed.length() > MAX_SEARCH_LENGTH) {
            throw malformed("search");
        }
        return trimmed.length() < 2 ? null : trimmed;
    }

    /** A letter or a code a filter names: trimmed, upper-cased, and refused above its ceiling. */
    private static String code(String field, String value, int ceiling) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim().toUpperCase(Locale.ROOT);
        if (trimmed.length() > ceiling) {
            throw malformed(field);
        }
        return trimmed;
    }

    private static MonthEndRefusal malformed(String field) {
        return new MonthEndRefusal(MonthEndRefusalCode.REQUEST_MALFORMED,
                "This request could not be read. Send each field in the form it is expected in.",
                Map.of("field", field));
    }

    private static MonthEndRefusal pageInvalid(int page, int size) {
        return new MonthEndRefusal(MonthEndRefusalCode.WORKPLACE_PAGE_INVALID, "That page does not exist.",
                MonthEndRefusal.details("page", String.valueOf(page), "size", String.valueOf(size),
                        "maxSize", String.valueOf(MAX_PAGE_SIZE)));
    }
}
