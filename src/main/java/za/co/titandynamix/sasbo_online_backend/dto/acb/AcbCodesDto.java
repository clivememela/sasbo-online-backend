package za.co.titandynamix.sasbo_online_backend.dto.acb;

import java.util.List;

/**
 * The codes, both ways round: the bank's return codes with what each means here, and SASBO's suspension codes with
 * the bank codes that record each (her sheet, DOD-01). Bounded by nature -- tens of rows each.
 */
public record AcbCodesDto(List<AcbBankCodeDto> bankCodes, List<SuspensionCode> suspensionCodes) {

    /** One of SASBO's suspension codes, and the bank codes a return under which records it. */
    public record SuspensionCode(int code, String name, List<String> bankCodes) {
    }
}
