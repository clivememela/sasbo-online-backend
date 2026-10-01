package za.co.titandynamix.sasbo_online_backend.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import za.co.titandynamix.sasbo_online_backend.config.MonthEndApi;
import za.co.titandynamix.sasbo_online_backend.dto.member.MemberHistoryPageDto;
import za.co.titandynamix.sasbo_online_backend.services.MemberHistoryService;

/**
 * {@code GET /api/members/{id}/history} -- one member's history, a page at a time (month end 03.6): the fields its
 * saves changed and the changes of its status, newest first. In a controller of THIS ARC, beside
 * {@code MemberController} (MD49): a member's number that is a word is {@code REQUEST_MALFORMED}, not a 500. As open
 * as every member read is today (MR-SEC-1); named in B1's list, and refusing from the day sign-in exists.
 */
@MonthEndApi
@RestController
@RequestMapping("/api/members")
public class MemberHistoryController {

    private final MemberHistoryService history;

    public MemberHistoryController(MemberHistoryService history) {
        this.history = history;
    }

    @GetMapping("/{id}/history")
    public MemberHistoryPageDto history(@PathVariable Integer id,
                                        @RequestParam(required = false) Integer page,
                                        @RequestParam(required = false) Integer size) {
        return history.page(id, page, size);
    }
}
