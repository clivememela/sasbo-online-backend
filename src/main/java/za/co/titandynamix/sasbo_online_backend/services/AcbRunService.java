package za.co.titandynamix.sasbo_online_backend.services;

import za.co.titandynamix.sasbo_online_backend.dto.acb.AcbExclusionDto;
import za.co.titandynamix.sasbo_online_backend.dto.acb.AcbPage;
import za.co.titandynamix.sasbo_online_backend.dto.acb.AcbRunDto;
import za.co.titandynamix.sasbo_online_backend.dto.acb.AcbRunLineDto;
import za.co.titandynamix.sasbo_online_backend.dto.acb.AcbRunSummaryDto;
import za.co.titandynamix.sasbo_online_backend.enums.AcbDeclineReason;

/**
 * The debit-order run: prepare a month, read what it contains, move it on.
 *
 * <p>Every refusal is an {@code AcbRefusal} with a stable code. Every method that changes a run
 * takes the actor, so the audit trail records who -- {@code system} until the API authenticates.
 */
public interface AcbRunService {

    /**
     * Starts preparing the run for {@code actionMonth} ({@code YYYY-MM}) and answers at once with
     * the run in {@code PREPARING}. The preparation is a job: poll the run until it settles.
     */
    AcbRunDto prepare(String actionMonth, String actor);

    /** The runs on record, newest month first, a page at a time. */
    AcbPage<AcbRunSummaryDto> list(int page, int size);

    /** One run, with its control totals counted from its lines and its exclusion summary. */
    AcbRunDto get(long runId);

    /** A run's lines, a page at a time. Account numbers are masked to their last four digits. */
    AcbPage<AcbRunLineDto> lines(long runId, int page, int size);

    /** The members a run could not debit, with the reason, a page at a time. */
    AcbPage<AcbExclusionDto> exclusions(long runId, int page, int size);

    /**
     * Approves a prepared run. BUILT, and proven -- but no route calls it while the API is
     * anonymous: approving authorises the union to debit thousands of members.
     */
    AcbRunDto approve(long runId, int version, String actor, String reason);

    /**
     * Records that an approved run's bureau file was uploaded to the bank. Refused with
     * {@code FILE_NOT_RENDERED} for a run whose file was never produced. BUILT, and proven -- but no
     * route calls it while the API is anonymous.
     */
    AcbRunDto recordSubmitted(long runId, int version, String actor, String reason);

    /**
     * Records that the bank's returns for a submitted run have been taken in. The bank's report
     * lists only the debits that came back, so every line still PENDING is marked PAID in the same
     * transaction; an unpaid that arrives late may still be recorded against a PAID line afterwards.
     */
    AcbRunDto recordReturns(long runId, int version, String actor, String reason);

    /**
     * Closes a month whose returns are in. Refused with {@code UNPAIDS_UNDECIDED} while any unpaid
     * in it has no decision -- closing would leave it neither presented again nor suspended.
     */
    AcbRunDto close(long runId, int version, String actor, String reason);

    /** Withdraws a prepared or approved run. Its month is released; the run stays on record. */
    AcbRunDto cancel(long runId, int version, String actor, String reason);

    /**
     * Records that the bank declined a submitted run's file (D29): the run becomes {@code DECLINED},
     * its month and its re-presentations are released, and the month is prepared again as a new run.
     * Refused with {@code RUN_HAS_RETURNS} once any line has come back -- a declined file produces
     * none (F45). {@code bankWords} is capped at 300 characters and kept on the run alone (SD3-D).
     */
    AcbRunDto recordDeclined(long runId, int version, String actor, AcbDeclineReason reason, String bankWords);

    /** A download of the file for the bank: the file, and how many times it has been downloaded now. */
    record Download(AcbFileService.RenderedFile file, int downloads) {
    }

    /**
     * The file for the bank, for an approved or sent run (DOD-10, DOD-11): made at the first download and kept as the
     * evidence of what was sent, served byte for byte afterwards -- every download counted and audited.
     */
    Download download(long runId, String actor);

    /**
     * Fails every run whose preparation has gone quiet for longer than the job's ceiling -- a job
     * killed with its instance. Runs on every read and at startup. Answers how many it failed.
     */
    int sweepInterrupted();
}
