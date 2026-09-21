package za.co.titandynamix.sasbo_online_backend.services;

import za.co.titandynamix.sasbo_online_backend.dto.acb.AcbPage;
import za.co.titandynamix.sasbo_online_backend.dto.acb.AcbReturnsSummaryDto;
import za.co.titandynamix.sasbo_online_backend.dto.acb.AcbRunLineDto;
import za.co.titandynamix.sasbo_online_backend.dto.acb.AcbTerminationReportDto;
import za.co.titandynamix.sasbo_online_backend.dto.acb.RecordUnpaidRequest;
import za.co.titandynamix.sasbo_online_backend.dto.acb.AcbHeldOverDto;
import za.co.titandynamix.sasbo_online_backend.dto.acb.AcbHeldLetterDto;
import za.co.titandynamix.sasbo_online_backend.enums.UnpaidDecision;
import za.co.titandynamix.sasbo_online_backend.enums.UnpaidReason;

/**
 * The returns loop: what the bank sent back, the decision on each unpaid, the union's letter, and
 * who three unsuccessful presentations would terminate.
 *
 * <p>Recording an unpaid and rendering its letter are data entry, like capturing a member, and are
 * open. SENDING the letter and changing a member's status are not built behind any route until the
 * API knows who is calling -- and a member's status is only ever changed by the member's own service.
 */
public interface AcbReturnsService {

    /**
     * Records one unpaid from the bank's report, against a line of a sent run: its code, the reason
     * the code means, the day it arrived and -- if made -- the decision. Recording the same unpaid
     * again changes nothing; recording a different one for the line is refused.
     */
    AcbRunLineDto recordUnpaid(long runId, long lineId, RecordUnpaidRequest request, String actor);

    /** The decision on an unpaid recorded without one. */
    AcbRunLineDto decide(long runId, long lineId, UnpaidDecision decision, String actor);

    /**
     * A run's unpaids, a page at a time; with {@code reason} INVALID_ACCOUNT_NUMBER, the legacy
     * "Invalid Accounts" list.
     */
    AcbPage<AcbRunLineDto> unpaids(long runId, UnpaidReason reason, int page, int size);

    /** What the bank sent back for a run, counted. */
    AcbReturnsSummaryDto summary(long runId);

    /**
     * Records the member's permission for a further re-debit of an unpaid (D32) -- deciding it present
     * again if it was undecided. {@code PERMISSION_NOTE_REQUIRED} without a note; the note is capped at
     * 300 characters and kept on the line alone (SD3-D).
     */
    AcbRunLineDto permit(long runId, long lineId, String note, String actor);

    /**
     * The run's held letters -- no address, or one that cannot be sent to -- as the worklist Q46 asks for,
     * each saying whether the member now has an address it could go to. A page at a time.
     */
    AcbPage<AcbHeldLetterDto> heldLetters(long runId, int page, int size);

    /**
     * Marks a held letter dealt with by hand, with a note of how (Q46): it leaves the worklist and is
     * never sent afterwards. {@code LETTER_NOT_HELD} for a letter that is not held.
     */
    AcbHeldLetterDto markHandled(long runId, long letterId, String note, String actor);

    /** The re-debits run {@code runId} holds over for want of the member's permission (D32), a page at a time. */
    AcbPage<AcbHeldOverDto> heldOver(long runId, int page, int size);

    /** Who three unpaid presentations would terminate -- every unpaid counts (Q27). Terminates nobody. */
    AcbTerminationReportDto terminationReport(int page, int size);
}
