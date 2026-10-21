package za.co.titandynamix.sasbo_online_backend.services;

import za.co.titandynamix.sasbo_online_backend.dto.member.MemberStatusAnswer;
import za.co.titandynamix.sasbo_online_backend.dto.member.MemberStatusRequest;

/**
 * A member's status, changed with SASBO's code for it (month end 03.1). Every refusal is a code of its own, declared
 * in {@code MonthEndRefusalCode} and published in the vocabulary (ME-1).
 */
public interface MemberStatusService {

    /** The longest note an administrator may write (MR-SEC-22) -- SASBO asked at the 5 October Show &amp; Tell. */
    int MAX_NOTE_LENGTH = za.co.titandynamix.sasbo_online_backend.utils.FreeText.NOTE_LIMIT;

    /**
     * Moves a member to a status with its reason: a leaving with SASBO's deletion code, a suspension with SASBO's
     * suspension code, a reinstatement with neither -- judged by kind and no finer until the table (MM49). The same
     * change asked twice writes nothing the second time; a change asked on a stale view writes nothing and says so.
     */
    MemberStatusAnswer change(Integer memberId, MemberStatusRequest request);
}
