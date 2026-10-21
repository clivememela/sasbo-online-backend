package za.co.titandynamix.sasbo_online_backend.dto.reference;

import java.util.List;

/**
 * SASBO's core tiers, each with the table codes standing in it (Stephne, MEN-31) -- and what every other employer is
 * called: "All other are captured under individual institutions".
 */
public record CoreTiersDto(List<ReferenceRows.CoreTier> tiers, String allOther) {
}
