package za.co.titandynamix.sasbo_online_backend.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import za.co.titandynamix.sasbo_online_backend.dto.recruitment.ChannelMemberDto;
import za.co.titandynamix.sasbo_online_backend.dto.recruitment.ChannelMembersPageDto;
import za.co.titandynamix.sasbo_online_backend.dto.recruitment.ChannelSummaryDto;
import za.co.titandynamix.sasbo_online_backend.dto.recruitment.MemberCandidatesDto;
import za.co.titandynamix.sasbo_online_backend.services.RecruitmentChannelMembersService;

import java.util.List;

/**
 * The Recruiters screen's reads and writes of the members recruited through each channel (RC15) -- so the screen never
 * loads the member register.
 *
 * <p>🔒 The figures name no member, so they are open, as the screen's figures were (the build session, 6 Oct). Every
 * list that names members needs {@code MEMBERS_VIEW} and is limited to the caller's region or area; putting a member on
 * a channel, or taking them off it, is an edit of their record and needs {@code MEMBERS_EDIT}.
 */
@RestController
@RequestMapping("/api/recruitment-channels")
@RequiredArgsConstructor
public class RecruitmentChannelMembersController {

    private final RecruitmentChannelMembersService service;

    /** Every channel, with how many members were recruited through it and their banks and branches. */
    @GetMapping("/summary")
    public List<ChannelSummaryDto> summary() {
        return service.summary();
    }

    /** A page of a channel's members, each criterion narrowing by "contains". */
    @PreAuthorize("hasAuthority('MEMBERS_VIEW')")
    @GetMapping("/{id}/members")
    public ChannelMembersPageDto members(
            @PathVariable("id") long id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String membershipNumber,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String bank,
            @RequestParam(required = false) String branch,
            @RequestParam(required = false) String status) {
        return service.members(id, membershipNumber, name, bank, branch, status, page, size);
    }

    /** The members a picker offers -- at most fifty, and whether there were more. */
    @PreAuthorize("hasAuthority('MEMBERS_VIEW')")
    @GetMapping("/member-candidates")
    public MemberCandidatesDto candidates(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Long excludeChannel) {
        return service.candidates(q, excludeChannel);
    }

    /** Records the member as recruited through the channel ("Add Member"). */
    @PreAuthorize("hasAuthority('MEMBERS_EDIT')")
    @PutMapping("/{id}/members/{memberId}")
    public ChannelMemberDto assign(@PathVariable("id") long id, @PathVariable("memberId") int memberId) {
        return service.assign(id, memberId);
    }

    /** Takes the member off the channel; their status and the rest of their record are untouched. */
    @PreAuthorize("hasAuthority('MEMBERS_EDIT')")
    @DeleteMapping("/{id}/members/{memberId}")
    public ResponseEntity<Void> unassign(@PathVariable("id") long id, @PathVariable("memberId") int memberId) {
        service.unassign(id, memberId);
        return ResponseEntity.noContent().build();
    }
}
