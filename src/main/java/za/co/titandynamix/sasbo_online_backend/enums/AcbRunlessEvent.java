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
    SEED_COMPLETE,
    /** A bank's return code changed -- its re-debit rule, SASBO's code, its wording or provisional mark (V70). */
    CODE_CHANGED,
    /** A bank's return code added to the table (V70). */
    CODE_ADDED,
    /** Whether bank closures count as non-working days, changed (DOD-17, V70). */
    CALENDAR_SETTING_CHANGED,
    /** A bank's debit-order members moved to a new debit day in one step (DOD-19). */
    DEBIT_DAY_MOVED,
    /** Members' unpaid counts captured by hand (DOD-28). */
    UNPAID_COUNTS_CAPTURED,
    /** A membership ended after the third unpaid, confirmed by a staff member (DOD-22). */
    MEMBER_ENDED,
    /** A suspended member made active again: only the current month is collected (DOD-26). */
    MEMBER_REACTIVATED,
    /** A member whose membership ended joined again, with a new join date (DOD-27). */
    MEMBER_REJOINED,
    /** The Debit Order Deductions demonstration finished writing -- written last, in the same transaction. */
    DOD_SEED_COMPLETE
}
