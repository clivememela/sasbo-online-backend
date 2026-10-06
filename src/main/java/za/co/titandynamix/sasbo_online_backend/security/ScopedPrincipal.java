package za.co.titandynamix.sasbo_online_backend.security;

/**
 * A signed-in caller as the rest of the system sees one: who it is, and how far it may see.
 *
 * <p>The sign-in filter puts a principal of this type into the security context; {@link CallerScope#current()} and the
 * audit trail's actor read it from there, so nothing outside the security package knows how a session is kept.
 */
public interface ScopedPrincipal {

    /** The username the caller signed in with. */
    String username();

    /** The name a person reads on a history row: a display name, never a username alone where one exists. */
    String displayName();

    /** The regions or areas the caller may see. */
    CallerScope scope();
}
