package za.co.titandynamix.sasbo_online_backend.payroll;

/**
 * How an employer writes a name in one column (fixtures' {@code LAYOUTS.md}: "Names come in seven styles"). Where the
 * style cannot say where the surname ends -- "Firstname Surname", "SURNAME FIRSTNAME", a name in capitals in either
 * order -- every split is kept as a reading, and a member fits when any reading does.
 */
public enum NameStyle {
    /** Initials, first names and surname in columns of their own. */
    SEPARATE,
    /** Absa: {@code SURNAME , FIRST NAMES}. */
    SURNAME_COMMA_FIRSTNAMES,
    /** GBS: {@code SURNAME, INITIALS}. */
    SURNAME_COMMA_INITIALS,
    /** Bankserv, SBV: {@code Mr AB Surname}. */
    TITLE_INITIALS_SURNAME,
    /** Capitec: {@code Firstname Surname}. */
    FIRSTNAMES_SURNAME,
    /** Land Bank: {@code SURNAME FIRSTNAME}, no comma. */
    SURNAME_FIRSTNAMES,
    /** Metropolitan Health: a full name in capitals, its order unread -- both orders are tried. */
    EITHER_ORDER,
    /** SA Reserve Bank: {@code Surname I.J., Mr.}. */
    SURNAME_INITIALS_TITLE
}
