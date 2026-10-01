package za.co.titandynamix.sasbo_online_backend.controller;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import za.co.titandynamix.sasbo_online_backend.config.MonthEndApi;
import za.co.titandynamix.sasbo_online_backend.dto.member.MemberStatusAnswer;
import za.co.titandynamix.sasbo_online_backend.dto.member.MemberStatusRequest;
import za.co.titandynamix.sasbo_online_backend.services.MemberStatusService;

/**
 * {@code POST /api/members/{id}/status} -- a member's status, changed with SASBO's code for it (month end 03.1).
 *
 * <p>IN A CONTROLLER OF THE ARC, BESIDE {@code MemberController} AND NEVER INSIDE IT (MD49): so a body that is not
 * JSON, or a member's id that is a word, is {@code REQUEST_MALFORMED} here and not the 500 it would be there (A63).
 * AS OPEN AS THE {@code PUT} IT REPLACES (MD13) until sign-in exists, and named in register B1's list of routes that
 * wait on it.
 */
@MonthEndApi
@RestController
@RequestMapping("/api/members")
public class MemberStatusController {

    private final MemberStatusService statuses;

    public MemberStatusController(MemberStatusService statuses) {
        this.statuses = statuses;
    }

    @PostMapping("/{id}/status")
    public MemberStatusAnswer change(@PathVariable Integer id, @RequestBody MemberStatusRequest request) {
        return statuses.change(id, request);
    }
}
