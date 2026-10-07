package za.co.titandynamix.sasbo_online_backend.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import za.co.titandynamix.sasbo_online_backend.config.MonthEndApi;
import za.co.titandynamix.sasbo_online_backend.dto.monthend.SuspendedMembersPageDto;
import za.co.titandynamix.sasbo_online_backend.services.SuspendedMembers;

/**
 * The members suspended now, and those whose suspension has lapsed, under {@code /api/month-end/suspensions} (Stephne,
 * MEN-18). A listing that names members: held by {@code MEMBERS_VIEW}, and by {@code MONTH_END_VIEW} too -- Stephne,
 * DOD-14, "Should organisers see the unpaids and suspensions of members in their own area or region?": No. Organisers
 * hold the first and no organiser position holds the second (decided by the parent, OB30). Every row is in the
 * caller's region or area.
 */
@MonthEndApi
@RestController
@RequestMapping("/api/month-end/suspensions")
public class SuspendedMembersController {

    private final SuspendedMembers suspended;

    public SuspendedMembersController(SuspendedMembers suspended) {
        this.suspended = suspended;
    }

    /** Every member suspended now, the oldest suspension first. */
    @PreAuthorize("hasAuthority('MEMBERS_VIEW') and hasAuthority('MONTH_END_VIEW')")
    @GetMapping("/members")
    public SuspendedMembersPageDto members(@RequestParam(required = false) Integer page,
                                           @RequestParam(required = false) Integer size,
                                           @RequestParam(required = false) Long employer,
                                           @RequestParam(required = false) Integer code) {
        return suspended.members(page, size, employer, code);
    }

    /** The members whose suspension has lapsed -- three months on -- the reminder that they can be deleted. */
    @PreAuthorize("hasAuthority('MEMBERS_VIEW') and hasAuthority('MONTH_END_VIEW')")
    @GetMapping("/lapsed")
    public SuspendedMembersPageDto lapsed(@RequestParam(required = false) Integer page,
                                          @RequestParam(required = false) Integer size,
                                          @RequestParam(required = false) Long employer,
                                          @RequestParam(required = false) Integer code) {
        return suspended.lapsed(page, size, employer, code);
    }
}
