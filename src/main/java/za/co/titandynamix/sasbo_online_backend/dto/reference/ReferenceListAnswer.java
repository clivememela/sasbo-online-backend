package za.co.titandynamix.sasbo_online_backend.dto.reference;

import java.util.List;

/**
 * One of SASBO's lists, as one typed route answers it (01.1, MD41): the rows, in the server's order, and where the
 * list stands -- the day the report it was read from was printed, and the day SASBO confirmed it, empty until she has
 * (MD28). A screen that groups by the list says <i>as printed on 31 August 2026 -- not yet confirmed by SASBO</i>
 * until {@code confirmedOn} is set. Dates are ISO strings (MD16). {@code reason} says why a list is empty, and is
 * empty itself when it is not (ME-19).
 *
 * @param <T> the list's own row -- each list has a shape of its own
 */
public record ReferenceListAnswer<T>(String list, String printedOn, String confirmedOn, String reason, List<T> rows) {
}
