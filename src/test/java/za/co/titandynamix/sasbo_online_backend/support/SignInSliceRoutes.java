package za.co.titandynamix.sasbo_online_backend.support;

import org.springframework.boot.test.context.TestComponent;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import za.co.titandynamix.sasbo_online_backend.acb.AcbActor;
import za.co.titandynamix.sasbo_online_backend.security.CallerScope;

/**
 * Routes that exist only in the sign-in slices: one of each kind the October build's streams declare, so the chain's
 * answer to each kind of caller is proved once, in both modes, without leaning on another stream's controller.
 *
 * <p>{@link TestComponent}, so component scanning never puts these routes into another test's context (where they
 * would join the routed surface); the slice imports them by name.
 */
@TestComponent
@RestController
public class SignInSliceRoutes {

    /** A route that names no permission -- what every route on main is. */
    @GetMapping("/api/test/open")
    public String open() {
        return "open";
    }

    /** A route that names one permission. */
    @PreAuthorize("hasAuthority('MEMBERS_VIEW')")
    @GetMapping("/api/test/members")
    public String members() {
        return "members";
    }

    /** The register's shape (register-paging, A10): open to an anonymous caller while sign-in is open. */
    @PreAuthorize("isAnonymous() or hasAuthority('MEMBERS_VIEW')")
    @GetMapping("/api/test/register")
    public String register() {
        return "register";
    }

    /** A route that names two permissions -- the refusal names neither rather than guess. */
    @PreAuthorize("hasAnyAuthority('DOD_FILE', 'DOD_PREPARE')")
    @GetMapping("/api/test/either")
    public String either() {
        return "either";
    }

    /** Who the route thinks is calling, and how far they see. */
    @GetMapping("/api/test/who")
    public String who() {
        CallerScope scope = CallerScope.current();
        return AcbActor.current() + " | " + scope.type() + " " + new java.util.TreeSet<>(scope.codes());
    }

    /** Stands in for the actuator's health check, which a slice has no actuator to serve. */
    @GetMapping("/actuator/health")
    public String health() {
        return "UP";
    }
}
