package za.co.titandynamix.sasbo_online_backend.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import za.co.titandynamix.sasbo_online_backend.config.MonthEndApi;
import za.co.titandynamix.sasbo_online_backend.dto.summary.MemberSummaryDto;
import za.co.titandynamix.sasbo_online_backend.services.MemberSummaryService;

/**
 * {@code GET /api/members/summary} -- the figures of a month (02.2), "the eye of SASBO".
 *
 * <p>In a controller of THIS ARC, beside {@code MemberController} (MD49), so that a request it cannot read is
 * {@code REQUEST_MALFORMED} and each way it can say no is a code of its own -- and so that its literal path is matched
 * before {@code /api/members/{id}} could take "summary" for a member's number. OPEN, as a read of counts is (MD4):
 * rule C keeps a membership below five at a named employer or table code on the server.
 */
@MonthEndApi
@RestController
@RequestMapping("/api/members")
public class MemberSummaryController {

    private final MemberSummaryService summaries;

    public MemberSummaryController(MemberSummaryService summaries) {
        this.summaries = summaries;
    }

    @GetMapping("/summary")
    public MemberSummaryDto summary(@RequestParam(required = false) String month,
                                    @RequestParam(required = false) String by,
                                    @RequestParam(required = false) String area,
                                    @RequestParam(required = false) String region) {
        return summaries.summary(month, by, area, region);
    }
}
