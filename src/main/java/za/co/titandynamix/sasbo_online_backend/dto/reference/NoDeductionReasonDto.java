package za.co.titandynamix.sasbo_online_backend.dto.reference;

/**
 * One reason for no payroll deduction, as the list answers it (V79, Stephne MEN-17). Dates are ISO strings (MD16).
 *
 * @param id             what a status change names
 * @param name           the words on the drop-down
 * @param suspensionCode SASBO's number for the suspension code the reason stands under
 * @param suspensionName that code's words, where the code is on record here; {@code null} otherwise
 * @param ordinal        where it stands on the drop-down
 * @param active         offered on the drop-down; an inactive reason is kept for the changes that name it
 * @param provisional    the code it stands under is ours, until SASBO says (MEN-17's clarification)
 * @param version        what an edit names, so two administrators never overwrite each other unseen
 * @param updatedBy      who last wrote it -- {@code migration} for her list as V79 wrote it
 * @param updatedAt      when, ISO-8601
 */
public record NoDeductionReasonDto(long id, String name, int suspensionCode, String suspensionName, int ordinal,
                                   boolean active, boolean provisional, int version, String updatedBy,
                                   String updatedAt) {
}
