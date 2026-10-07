package za.co.titandynamix.sasbo_online_backend.dto.reference;

/**
 * The core tier a table code is to stand in (MEN-31).
 *
 * @param tier {@code A}, {@code B} or {@code C}; {@code null} or blank for all other institutions
 */
public record CoreTierRequest(String tier) {
}
