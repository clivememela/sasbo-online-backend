package za.co.titandynamix.sasbo_online_backend.services;

import za.co.titandynamix.sasbo_online_backend.dto.acb.AcbPage;
import za.co.titandynamix.sasbo_online_backend.dto.acb.AcbUnpaidCountDto;
import za.co.titandynamix.sasbo_online_backend.dto.acb.AcbUnpaidCountsResultDto;
import za.co.titandynamix.sasbo_online_backend.dto.acb.UnpaidCountsRequest;

/**
 * Members' unpaid counts captured by hand (Stephne, DOD-28: "Unpaids for re-debits are captured manually a day before
 * we run ACB") -- usable in any month, since whether it is once at switch-over or every month is asked (Friday item
 * 15). A count is the member's as at the day it is captured: it replaces their debits before that day.
 */
public interface AcbUnpaidCountService {

    /** The counts on record, by membership number, a page at a time. */
    AcbPage<AcbUnpaidCountDto> list(int page, int size);

    /**
     * Captures a list of counts, all or nothing: {@code UNPAID_COUNTS_INVALID} names every problem by row. The same
     * list captured twice the same day changes nothing the second time.
     */
    AcbUnpaidCountsResultDto capture(UnpaidCountsRequest request, String actor);

    /** The capture as at {@code at} -- for the demonstration seed's past. The route captures as at now. */
    AcbUnpaidCountsResultDto capture(UnpaidCountsRequest request, String actor, java.time.Instant at);
}
