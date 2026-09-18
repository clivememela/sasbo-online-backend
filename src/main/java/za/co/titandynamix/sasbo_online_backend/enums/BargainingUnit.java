package za.co.titandynamix.sasbo_online_backend.enums;

/**
 * Whether a job grade places a member inside the bargaining unit.
 *
 * <h2>🔑 This is not a new concept — it was already in the data, untyped</h2>
 * {@code job_grades.description} was already carrying this, spelled four different
 * ways ({@code "Bargaining Unit"}, {@code "Bargaining unit"}, {@code "Non - Bargaining"},
 * and absent), with a third of the rows empty — and three rows using the same column
 * for a SENIORITY BAND instead ({@code "Junior Managers"}, {@code "Middle Managers"},
 * {@code "Senior Managers"}). One column meaning two things is why this is a typed
 * column now and why {@code description} goes back to being a description.
 *
 * <h2>🔴 Why an enum and not a boolean</h2>
 * Two employers' rules cannot be expressed as yes/no, and neither is ours to
 * simplify away:
 *
 * <ul>
 *   <li><b>Nedbank</b> classifies on the grade AND a guaranteed-package threshold —
 *       "a guaranteed package threshold that works in conjunction with the grade".
 *       The email promising that table says "see below" and the table is not there,
 *       on any page. So every Nedbank grade is the GRADE HALF of a two-part rule and
 *       calling a C3 "BU" would be silently wrong for anyone above the threshold.
 *   <li><b>Capitec grade C</b> is bargaining-unit <i>only for Assistant Branch
 *       Managers</i> — a job title this system does not store. A flag on grade C
 *       would mis-classify every Capitec C who is not one.
 * </ul>
 *
 * <p>Hence {@link #CONDITIONAL}, with the condition kept as readable text beside it.
 * A rule nobody can read is a rule nobody applies.
 *
 * <h2>Absent is a fourth state, and it is NULL</h2>
 * The column is nullable on purpose. Nine seeded OM Bank grades and Standard Bank's
 * three manager rows carry no classification from the union, and NULL says that.
 * Inferring one from the Paterson C│D pattern would be a guess wearing a fact's
 * clothing — see {@code jobGrades.md}, where that pattern is recorded explicitly as
 * a cross-check and NEVER as the implementation.
 */
public enum BargainingUnit {

    /** Inside the bargaining unit. */
    BU,

    /** Outside it. */
    NBU,

    /**
     * The grade alone does not decide. {@code bargainingUnitCondition} carries what
     * else is needed, in words, because the missing part is either data we do not
     * hold (a job title) or data we were never sent (a package threshold).
     */
    CONDITIONAL
}
