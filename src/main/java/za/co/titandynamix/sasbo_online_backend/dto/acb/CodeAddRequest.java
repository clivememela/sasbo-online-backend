package za.co.titandynamix.sasbo_online_backend.dto.acb;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import za.co.titandynamix.sasbo_online_backend.enums.ReDebit;
import za.co.titandynamix.sasbo_online_backend.enums.UnpaidReason;

/**
 * A bank return code the table does not know yet (V70) -- the day the bank's full list arrives (Friday item 10).
 *
 * @param bankCode       the number the bank's report prints, one to three digits
 * @param reason         which of the union's reasons it is -- the reason decides the letter
 * @param wording        what the letters print, at most 100 characters
 * @param reDebit        YES, NO or UNKNOWN
 * @param suspensionCode SASBO's suspension code a return under it records, or null for none yet
 * @param note           where it came from, at most 255 characters
 */
public record CodeAddRequest(@NotBlank @Pattern(regexp = "^[0-9]{1,3}$") String bankCode, @NotNull UnpaidReason reason,
                             @NotBlank @Size(max = 100) String wording, @NotNull ReDebit reDebit,
                             Integer suspensionCode, @NotBlank @Size(max = 255) String note) {
}
