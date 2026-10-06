package za.co.titandynamix.sasbo_online_backend.security;

/**
 * What a signed-in user may do -- the catalogue every {@code @PreAuthorize("hasAuthority('...')")} names.
 *
 * <p>The names are the October build's contract (sign-in, 5 Oct Show &amp; Tell: "an administrator creating users
 * with a position and an area, roles kept as editable data"). A role is a set of these, kept as data and edited by a
 * System Administrator; a position carries a default role. Who holds what is therefore data, never code: only the
 * catalogue is here. Renaming a constant breaks every annotation that names it, and the roles stored with it.
 *
 * <p>Organisers never see unpaids or payroll discrepancies (Stephne, DOD and PAY answers, 6 Oct): no organiser
 * position holds {@link #DOD_PREPARE}, {@link #DOD_FILE}, {@link #PAYROLL_PROCESS} or {@link #PAYROLL_VIEW}.
 */
public enum Permission {

    USERS_ADMIN("Create and edit users, positions and roles; reset a password; deactivate a user"),
    AUDIT_VIEW("Read the record of who did what"),
    MEMBERS_VIEW("See a listing that names members, within the user's region or area"),
    MEMBERS_CAPTURE("Capture a new member"),
    MEMBERS_EDIT("Edit a member's record and change a member's status"),
    REFERENCE_MAINTAIN("Maintain lists: banks and branches, workplaces, recruitment channels, reasons"),
    SUBSCRIPTIONS_MAINTAIN("Change subscription amounts"),
    MONTH_END_VIEW("Read month-end figures and statistics"),
    MONTH_END_TAKE("Take the month, and take it again with a reason"),
    MONTH_END_SEND("Produce, download and e-mail the month's packs, bank files and provider files"),
    DOD_PREPARE("Prepare a debit order run, record unpaids and suspensions, confirm endings"),
    DOD_FILE("Download the debit order file for the bank"),
    DOD_SETTINGS("Debit order rates, non-working days and moving a bank's members to a new debit day"),
    PAYROLL_PROCESS("Load employer payroll files, work discrepancies, raise and close queries"),
    PAYROLL_VIEW("Read payroll reconciliation results and reports"),
    VISITS_CAPTURE("Capture and submit a workplace visit"),
    REPORTS_PRINT("Print organisers' reports and location listings"),
    MIGRATION_RUN("Run legacy data imports and their checks");

    private final String description;

    Permission(String description) {
        this.description = description;
    }

    /** One sentence for the screen that edits roles. */
    public String description() {
        return description;
    }
}
