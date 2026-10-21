package za.co.titandynamix.sasbo_online_backend.services;

import za.co.titandynamix.sasbo_online_backend.dto.acb.AcbBankCodeDto;
import za.co.titandynamix.sasbo_online_backend.dto.acb.AcbCodesDto;
import za.co.titandynamix.sasbo_online_backend.dto.acb.CodeAddRequest;
import za.co.titandynamix.sasbo_online_backend.dto.acb.CodeChangeRequest;

/**
 * The bank's return codes as data (V70; Stephne's "Suspension codes" sheet and DOD-03, DOD-04): what each means, which
 * of SASBO's suspension codes it records, whether it may be re-debited, and what is still provisional. Read by anyone
 * who works the debit orders; changed only by an administrator holding DOD_SETTINGS, each change audited.
 */
public interface AcbCodeService {

    /** Every bank code, and SASBO's suspension codes with the bank codes that record each. */
    AcbCodesDto codes();

    /** Changes one code: {@code CODE_NOT_FOUND}, {@code CODE_VERSION_CONFLICT}, {@code SUSPENSION_CODE_UNKNOWN}. */
    AcbBankCodeDto change(String bankCode, CodeChangeRequest request, String actor);

    /** Adds a code the table does not know: {@code CODE_ALREADY_LISTED}, {@code SUSPENSION_CODE_UNKNOWN}. */
    AcbBankCodeDto add(CodeAddRequest request, String actor);
}
