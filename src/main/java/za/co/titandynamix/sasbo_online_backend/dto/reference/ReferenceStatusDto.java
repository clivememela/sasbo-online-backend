package za.co.titandynamix.sasbo_online_backend.dto.reference;

import java.util.List;

/**
 * {@code GET /api/reference/status} (01.1, ME-18, ME-19): for every one of SASBO's lists, and for the workplaces, how
 * many rows are answered here, where the list stands, and -- where none is -- WHY, in one of four words, so that a
 * screen can say it instead of showing an empty control: {@code NOT_CONFIRMED} (not confirmed by SASBO yet),
 * {@code NOT_LOADED} (not loaded on this demonstration yet), {@code FAILED} (could not be loaded -- and {@code failure}
 * says how, in one of four words of its own: never an exception's name, MR-SEC-21), {@code EMPTY} (nothing is on it).
 *
 * @param workplacesServed what the deployment's switch says of SASBO's workplaces: {@code NONE}, {@code NAMES} or
 *                         {@code ALL} (MD62)
 */
public record ReferenceStatusDto(List<ListStatus> lists, ListStatus workplaces, String workplacesServed) {

    /**
     * One list's standing. {@code rows} is what is answered here, which for the workplaces follows the switch.
     * Dates and the instant are ISO strings (MD16).
     */
    public record ListStatus(String list, long rows, String printedOn, String confirmedOn, String seededAt,
                             String reason, String failure) {
    }
}
