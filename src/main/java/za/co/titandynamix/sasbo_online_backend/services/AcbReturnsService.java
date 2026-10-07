package za.co.titandynamix.sasbo_online_backend.services;

import za.co.titandynamix.sasbo_online_backend.dto.acb.AcbPage;
import za.co.titandynamix.sasbo_online_backend.dto.acb.AcbReturnsSummaryDto;
import za.co.titandynamix.sasbo_online_backend.dto.acb.AcbRunLineDto;
import za.co.titandynamix.sasbo_online_backend.dto.acb.AcbTerminationReportDto;
import za.co.titandynamix.sasbo_online_backend.dto.acb.RecordUnpaidRequest;
import za.co.titandynamix.sasbo_online_backend.dto.acb.AcbHeldOverDto;
import za.co.titandynamix.sasbo_online_backend.dto.acb.AcbHeldLetterDto;
import za.co.titandynamix.sasbo_online_backend.dto.acb.AcbMemberDto;
import za.co.titandynamix.sasbo_online_backend.dto.acb.MemberSearchRequest;
import za.co.titandynamix.sasbo_online_backend.dto.acb.AcbDebitDaysDto;
import za.co.titandynamix.sasbo_online_backend.dto.acb.AcbMemberStatusDto;
import za.co.titandynamix.sasbo_online_backend.dto.acb.AcbReturnsBatchDto;
import za.co.titandynamix.sasbo_online_backend.dto.acb.MemberReturnRequest;
import za.co.titandynamix.sasbo_online_backend.dto.acb.ReturnsBatchRequest;
import za.co.titandynamix.sasbo_online_backend.dto.acb.SuspendMemberRequest;
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

    /**
     * The decision on an unpaid recorded without one. Present it again only where the bank's code allows a re-debit
     * ({@code RE_DEBIT_NOT_ALLOWED}) and the member is below three unpaids ({@code UNPAID_LIMIT_REACHED}) -- Stephne,
     * DOD-03, DOD-04.
     */
    AcbRunLineDto decide(long runId, long lineId, UnpaidDecision decision, String actor);

    /**
     * One debit day's returns report, typed in (DOD-05, DOD-06, DOD-12): every entry recorded, every letter queued,
     * every decision checked against the rules -- or nothing at all, with each problem named by entry
     * ({@code RETURNS_BATCH_INVALID}). An entry already recorded with the same code changes nothing.
     */
    AcbReturnsBatchDto recordReturnsBatch(long runId, ReturnsBatchRequest request, String actor);

    /** The returns reports typed in for a run, newest debit day first, a page at a time. */
    AcbPage<AcbReturnsBatchDto> returnsBatches(long runId, int page, int size);

    /** A run's debit days, with how far each one's returns have come back (DOD-06). */
    AcbDebitDaysDto debitDays(long runId);

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
     * The run's letters that could not go -- held for want of an address it can go to, or a send that FAILED --
     * as the worklist Q46 and EH-E ask for, each saying whether the member now has an address it could go to.
     * A page at a time.
     */
    AcbPage<AcbHeldLetterDto> heldLetters(long runId, int page, int size);

    /**
     * Marks a letter that could not go dealt with by hand, with a note of how (Q46): it leaves the worklist and
     * is never sent afterwards. {@code LETTER_NOT_HELD} for any other letter.
     */
    AcbHeldLetterDto markHandled(long runId, long letterId, String note, String actor);

    /**
     * Sends a letter that could not go -- once the member has an address it can go to (Q46), or again after a send
     * that FAILED (EH-E, R3-7): rendered now, by {@code actor}, and handed to the sender after this commits; the
     * sender's claim counts the attempt. {@code LETTER_NOT_HELD} for any other letter; {@code LETTER_NO_ADDRESS}
     * while the member has no address it can go to. No route calls it while the API is anonymous (B1): a letter
     * the anonymous API queued is never sent.
     */
    AcbHeldLetterDto sendHeldLetter(long runId, long letterId, String actor);

    /**
     * The Debits screen's member search (04.1): part of a surname or membership number, or a whole
     * identity number -- server-side and paged. With every criterion blank, an empty page.
     */
    AcbPage<AcbMemberDto> searchMembers(MemberSearchRequest request);

    /** The re-debits run {@code runId} holds over for want of the member's permission (D32), a page at a time. */
    AcbPage<AcbHeldOverDto> heldOver(long runId, int page, int size);

    /** Who three unpaid presentations would terminate -- every unpaid counts (Q27). Terminates nobody. */
    AcbTerminationReportDto terminationReport(int page, int size);

    /**
     * 03.3 -- a SPECIMEN of one of the union's three letters, as the member would receive it: a PDF on the
     * letterhead, filled from a member who does not exist. It is what the letters review SASBO asked for reads
     * (question 32 c).
     */
    byte[] letterSpecimen(za.co.titandynamix.sasbo_online_backend.enums.LetterTemplate template);

    /**
     * 03.5 -- terminates a member the termination rule says three unpaid presentations have reached, through
     * {@code MemberService.changeStatus} with the reason {@code SUBS_IN_ARREARS} and never the whole-record
     * update (Q25, Q27, R3-9). Refuses a member the rule does not reach ({@code TERMINATION_NOT_DUE}); a member
     * already terminated is answered as they are. No route calls it while the API is anonymous: ending a
     * membership waits for B1, like approval.
     */
    za.co.titandynamix.sasbo_online_backend.dto.acb.AcbMemberStatusDto terminate(Integer memberId, String actor);

    /**
     * A suspended member made active again (DOD-25): only the current month is collected (DOD-26) -- every re-debit
     * waiting from before is withdrawn. {@code MEMBER_NOT_SUSPENDED} for anyone not suspended.
     */
    AcbMemberStatusDto reactivate(Integer memberId, MemberReturnRequest request, String actor);

    /**
     * A member whose membership ended joins again, freely, with a new join date (DOD-27): their count starts again
     * at nought and re-debits from before are withdrawn. {@code MEMBER_NOT_ENDED} for anyone whose membership has not
     * ended.
     */
    AcbMemberStatusDto rejoin(Integer memberId, MemberReturnRequest request, String actor);

    /**
     * 03.5 -- suspends the member of an unpaid decided SUSPEND -- a code that allows no re-debit (Q40, Q44) --
     * through {@code changeStatus} with the reason {@code UNPAID_SUSPENDED}. Refuses any other line ({@code
     * UNPAID_NOT_SUSPENDED}) and a member already terminated ({@code MEMBER_TERMINATED}). No route calls it
     * while the API is anonymous.
     */
    default za.co.titandynamix.sasbo_online_backend.dto.acb.AcbMemberStatusDto suspendMember(long runId, long lineId,
                                                                                              String actor) {
        return suspendMember(runId, lineId, null, actor);
    }

    /**
     * Suspends the member of an unpaid decided SUSPEND with SASBO's suspension code (DOD-12; her sheet, DOD-01): the
     * one the bank's code records, unless {@code request} names another. {@code SUSPENSION_CODE_REQUIRED} where the
     * bank's code records none (bank 18, "Deceased", Friday item 1); {@code SUSPENSION_CODE_UNKNOWN} for a code not on
     * record.
     */
    AcbMemberStatusDto suspendMember(long runId, long lineId, SuspendMemberRequest request, String actor);
}
