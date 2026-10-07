package za.co.titandynamix.sasbo_online_backend.dto.reference;

/**
 * A reason for no payroll deduction, added or edited (V79, MEN-17).
 *
 * @param name           1 to 60 characters, no control characters, unique whatever its case
 * @param suspensionCode SASBO's number for a suspension code on record here
 * @param ordinal        1 to 999; left out on an addition, the reason goes last; left out on an edit, it stays
 * @param active         left out on an addition, true; left out on an edit, as it was
 * @param version        on an edit, the version the screen read -- required there
 */
public record NoDeductionReasonRequest(String name, Integer suspensionCode, Integer ordinal, Boolean active,
                                       Integer version) {
}
