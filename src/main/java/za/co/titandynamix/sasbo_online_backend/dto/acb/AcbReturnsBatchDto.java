package za.co.titandynamix.sasbo_online_backend.dto.acb;

import java.util.List;

/**
 * What typing in one debit day's returns report did (V72).
 *
 * @param source          {@code MANUAL} -- typed in; an import comes when the bank's export format is known (DOD-02)
 * @param recorded        entries new to the system
 * @param alreadyRecorded entries already recorded with the same code: a retry changes nothing
 * @param lettersQueued   letters the new unpaids queued (DOD-07: "Letters must go out as soon as we do the updates")
 * @param lines           the lines the batch recorded, on the answer to recording it; null on a list of batches
 */
public record AcbReturnsBatchDto(Long id, Long runId, String actionDate, String reportDate, String source, int entries,
                                 int recorded, int alreadyRecorded, int lettersQueued, String recordedBy,
                                 String recordedAt, List<AcbRunLineDto> lines) {
}
