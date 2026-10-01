package za.co.titandynamix.sasbo_online_backend.services;

import org.springframework.stereotype.Service;
import za.co.titandynamix.sasbo_online_backend.dto.member.MemberHistoryPageDto;
import za.co.titandynamix.sasbo_online_backend.exception.MonthEndRefusal;
import za.co.titandynamix.sasbo_online_backend.exception.MonthEndRefusalCode;
import za.co.titandynamix.sasbo_online_backend.repository.MemberHistoryRepository;
import za.co.titandynamix.sasbo_online_backend.repository.MemberRepository;

import java.util.Map;

/**
 * One member's history, a page at a time (month end 03.6) -- as open as every member read is today, and named in B1's
 * list (MR-SEC-1). A member who is not on record is {@code MEMBER_NOT_FOUND}; a page that does not exist -- a
 * negative one, a size outside one to two hundred, or one past the last -- is {@code HISTORY_PAGE_INVALID}, never an
 * empty page that looks like the end of the history. Every page says since when the history is kept (ME-7).
 */
@Service
public class MemberHistoryService {

    /** The largest page there is, as the workplaces' and ACB's (MR-PERF-12). */
    public static final int MAX_PAGE_SIZE = 200;

    static final int DEFAULT_PAGE_SIZE = 50;

    private final MemberRepository members;
    private final MemberHistoryRepository history;

    public MemberHistoryService(MemberRepository members, MemberHistoryRepository history) {
        this.members = members;
        this.history = history;
    }

    public MemberHistoryPageDto page(Integer memberId, Integer page, Integer size) {
        int number = page == null ? 0 : page;
        int rows = size == null ? DEFAULT_PAGE_SIZE : size;
        if (number < 0 || rows < 1 || rows > MAX_PAGE_SIZE) {
            throw pageInvalid(number, rows);
        }
        if (memberId == null || !members.existsById(memberId)) {
            throw new MonthEndRefusal(MonthEndRefusalCode.MEMBER_NOT_FOUND, "That member is no longer on record.",
                    Map.of("memberId", String.valueOf(memberId)));
        }
        MemberHistoryRepository.Slice slice = history.page(memberId, number, rows);
        if (slice.entries().isEmpty() && number > 0) {
            throw pageInvalid(number, rows);
        }
        int pages = (int) ((slice.total() + rows - 1) / rows);
        return new MemberHistoryPageDto(slice.entries(), number, rows, slice.total(), pages,
                history.historyFrom().map(Object::toString).orElse(null));
    }

    private static MonthEndRefusal pageInvalid(int page, int size) {
        return new MonthEndRefusal(MonthEndRefusalCode.HISTORY_PAGE_INVALID, "That page of the history does not exist.",
                MonthEndRefusal.details("page", String.valueOf(page), "size", String.valueOf(size),
                        "maxSize", String.valueOf(MAX_PAGE_SIZE)));
    }
}
