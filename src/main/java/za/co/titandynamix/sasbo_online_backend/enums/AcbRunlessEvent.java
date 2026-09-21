package za.co.titandynamix.sasbo_online_backend.enums;

/**
 * The audit trail's events that belong to NO run (SD4-A) -- and the only kinds V43's
 * {@code acb_audit_events_runless_kinds} CHECK lets omit {@code run_id}.
 *
 * <p>They are the writes that decide what members are charged and when -- the rate, the holiday
 * calendar and its extent, the opening arrears position -- plus the seed's own completion. None is a
 * run's, and each is still somebody's: until this existed, the day B1 lands they would have been the
 * only writes in the module with no name on them. Each arrives with the unit that first writes it;
 * the CHECK names them all, because a CHECK is amended in place only while V43 is unpushed (F53).
 */
public enum AcbRunlessEvent {
    /** A yearly rate added ahead of its month ({@code 01.9}). */
    RATE_ADDED,
    /** A holiday added to the calendar ({@code 01.8}). */
    HOLIDAY_ADDED,
    /** A holiday an administrator had added, removed ({@code 01.8}). */
    HOLIDAY_REMOVED,
    /** The calendar's known-until date moved ({@code 01.8}). */
    CALENDAR_EXTENDED,
    /** The opening arrears position captured at switch-over ({@code 03.0}). */
    OPENING_POSITION_CAPTURED,
    /** The seeded ACB history finished writing -- written last, so a crash between months shows ({@code 03.8}). */
    SEED_COMPLETE
}
