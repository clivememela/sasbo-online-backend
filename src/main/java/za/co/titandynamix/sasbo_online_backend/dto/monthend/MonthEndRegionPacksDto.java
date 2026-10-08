package za.co.titandynamix.sasbo_online_backend.dto.monthend;

import java.util.List;

/**
 * The month's regions' packs (October build): one row a recipient in the caller's scope, with its newest pack and that
 * pack's deliveries; whether this server can e-mail; whether it can make a region's pack at all ({@code available}:
 * false where SASBO's workplaces are not held in full, MD62); and the members no region's pack reaches -- the unplaced
 * (no workplace) and those in areas no recipient covers. Dates are ISO text (MD16).
 */
public record MonthEndRegionPacksDto(String month, Take take, Mail mail, boolean available, long unplaced,
                                     Uncovered uncovered, List<SetRow> sets) {

    /** The take the packs are made from: the month's newest. */
    public record Take(int takeNumber, String takenAt, String takenBy) {
    }

    /** Whether this server has mail set up. */
    public record Mail(boolean configured) {
    }

    /** Areas with members on record at the month's end that no active recipient covers, and how many members. */
    public record Uncovered(List<String> areas, long members) {
    }

    /** A recipient, its newest pack of the month's take (or none yet), and that pack's deliveries, newest first. */
    public record SetRow(MonthEndPackSetDto set, MonthEndRegionPackDto pack, List<MonthEndDeliveryDto> deliveries) {
    }
}
