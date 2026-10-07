package za.co.titandynamix.sasbo_online_backend.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import za.co.titandynamix.sasbo_online_backend.config.MonthEndApi;
import za.co.titandynamix.sasbo_online_backend.dto.member.MemberRegisterOverviewDto;
import za.co.titandynamix.sasbo_online_backend.dto.member.MemberRegisterPageDto;
import za.co.titandynamix.sasbo_online_backend.dto.member.MemberRegisterQuery;
import za.co.titandynamix.sasbo_online_backend.security.CallerScope;
import za.co.titandynamix.sasbo_online_backend.services.MemberRegisterService;

/**
 * The member register, a page at a time (register row A10) -- what the register screen reads instead of
 * {@code GET /api/members}, which answered every member with every field.
 *
 * <p>Beside {@code MemberController} and never inside it, as the summary and the history are (MD49): so that a request
 * it cannot read is {@code REQUEST_MALFORMED} with each way it says no a code of its own, and so that its literal paths
 * are matched before {@code /api/members/{id}}.
 *
 * <p>🔒 OPEN WHILE SIGN-IN IS OPEN, AS THE REGISTER IS TODAY (contracts/signin.md): an anonymous caller only reaches a
 * route at all in open mode, and is let through; a signed-in caller needs {@code MEMBERS_VIEW}. Either way the members
 * read are the caller's own region's or area's ({@link CallerScope#current()}), filtered in the statement.
 */
@MonthEndApi
@RestController
@RequestMapping("/api/members/register")
public class MemberRegisterController {

    private final MemberRegisterService register;

    public MemberRegisterController(MemberRegisterService register) {
        this.register = register;
    }

    /**
     * A page of the register. A POST, so that what is searched for -- a member's name, an identity number's digits --
     * travels in the body and never in a URL, which this server's log prints whole.
     */
    @PreAuthorize("isAnonymous() or hasAuthority('MEMBERS_VIEW')")
    @PostMapping("/search")
    public MemberRegisterPageDto search(@RequestBody(required = false) MemberRegisterQuery query) {
        return register.page(query, CallerScope.current());
    }

    /** The register's figure strip and the branches its Branch criterion offers. */
    @PreAuthorize("isAnonymous() or hasAuthority('MEMBERS_VIEW')")
    @GetMapping("/overview")
    public MemberRegisterOverviewDto overview() {
        return register.overview(CallerScope.current());
    }
}
