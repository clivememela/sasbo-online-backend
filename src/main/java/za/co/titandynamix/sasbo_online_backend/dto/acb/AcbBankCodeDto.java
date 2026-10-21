package za.co.titandynamix.sasbo_online_backend.dto.acb;

import za.co.titandynamix.sasbo_online_backend.enums.ReDebit;
import za.co.titandynamix.sasbo_online_backend.enums.UnpaidReason;

/**
 * One of the bank's return codes as the settings screen reads it (V70; Stephne's "Suspension codes" sheet, DOD-01).
 *
 * @param reDebit        whether an unpaid under it may be presented again -- YES, NO or UNKNOWN (DOD-03, DOD-04)
 * @param reDebitSource  who said so, in words
 * @param suspensionCode SASBO's suspension code a return under it records; null when not known (bank 18, Deceased)
 * @param suspensionName that code's name, as SASBO's list writes it
 * @param provisional    something about the row SASBO has not settled -- said in {@code note}
 * @param source         where the row came from
 * @param updatedBy      who last changed it; null for a row as the migration wrote it
 * @param updatedAt      ISO-8601; null likewise
 * @param version        sent back with a change, so a second administrator's is never lost
 */
public record AcbBankCodeDto(String bankCode, UnpaidReason reason, String wording, ReDebit reDebit,
                             String reDebitSource, Integer suspensionCode, String suspensionName, boolean provisional,
                             String note, String source, String updatedBy, String updatedAt, int version) {
}
