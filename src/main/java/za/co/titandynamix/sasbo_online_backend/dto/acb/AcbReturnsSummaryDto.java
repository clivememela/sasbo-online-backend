package za.co.titandynamix.sasbo_online_backend.dto.acb;

import java.util.Map;

/**
 * What the bank sent back for one run, counted.
 *
 * @param undecided          unpaids nobody has decided about -- they are presented again only once
 *                           someone decides so; the screen says how many are waiting
 * @param unrecognisedCodes  unpaids whose bank code the code table does not know, kept as OTHER --
 *                           a new code is noticed the month it appears
 */
public record AcbReturnsSummaryDto(
        Long runId,
        Map<String, Integer> linesByOutcome,
        Map<String, Integer> unpaidByReason,
        int undecided,
        int unrecognisedCodes,
        Map<String, Integer> lettersByStatus) {
}
