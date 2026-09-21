package za.co.titandynamix.sasbo_online_backend.acb;

/**
 * Who is acting, as the audit trail records it.
 *
 * <p>The API does not yet know who is calling -- every route is open to anyone who can reach it
 * (register B1). Until it does, every ACB action is recorded as {@link #SYSTEM}, and every action
 * that moves money, sends a letter, submits a file or changes a member's status is refused to every
 * caller. The day callers have names, this is where the name comes from, and the audit trail says
 * who prepared and who approved -- the same person, as often as not (D14 as amended, Q29).
 */
public final class AcbActor {

    /** The actor recorded while the API is anonymous. */
    public static final String SYSTEM = "system";

    private AcbActor() {
    }

    /** The actor for the current request. */
    public static String current() {
        return SYSTEM;
    }
}
