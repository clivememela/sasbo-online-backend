package za.co.titandynamix.sasbo_online_backend.dto.acb;

/**
 * What capturing a list of unpaid counts did.
 *
 * @param captured  members whose count was written -- new, or different from what was on record
 * @param unchanged members whose count was on record already, as at the same day: capturing the same list twice
 *                  changes nothing
 * @param countedOn the day the counts are as at
 * @param atThree   members captured at three: nothing is presented to them again until a staff member confirms their
 *                  ending or captures a new count (DOD-03, DOD-22)
 */
public record AcbUnpaidCountsResultDto(int captured, int unchanged, String countedOn, int atThree) {
}
