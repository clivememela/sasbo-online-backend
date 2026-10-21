package za.co.titandynamix.sasbo_online_backend.dto.acb;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import za.co.titandynamix.sasbo_online_backend.enums.ReDebit;

/**
 * A change to one of the bank's return codes (V70), by an administrator holding DOD_SETTINGS -- the day SASBO settles
 * what is still open about it (Friday items 1, 3 and 9).
 *
 * @param version        the code's version as the screen read it
 * @param reDebit        YES, NO or UNKNOWN -- UNKNOWN is never re-debited
 * @param suspensionCode SASBO's suspension code a return under it records, or null for none yet
 * @param wording        what the letters print, at most 100 characters
 * @param provisional    whether something about the row is still open
 * @param note           why -- SASBO's answer and its date -- at most 255 characters; never logged
 */
public record CodeChangeRequest(@NotNull Integer version, @NotNull ReDebit reDebit, Integer suspensionCode,
                                @NotBlank @Size(max = 100) String wording, @NotNull Boolean provisional,
                                @NotBlank @Size(max = 255) String note) {
}
