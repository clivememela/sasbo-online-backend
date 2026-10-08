package za.co.titandynamix.sasbo_online_backend.dto.reference;

/**
 * One table code and the core tier it stands in, as a change of its tier answers it (MEN-31). Dates are ISO strings.
 *
 * @param code     SASBO's number for the table code
 * @param name     its name as printed
 * @param coreTier {@code A}, {@code B} or {@code C}; {@code null} for all other institutions
 * @param setBy    who set the tier -- {@code seed} for the provisional mapping; {@code null} where nobody has
 * @param setAt    when, ISO-8601
 */
public record TableCodeTierDto(int code, String name, String coreTier, String setBy, String setAt) {
}
