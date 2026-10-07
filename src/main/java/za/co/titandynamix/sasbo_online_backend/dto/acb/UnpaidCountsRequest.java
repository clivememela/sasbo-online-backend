package za.co.titandynamix.sasbo_online_backend.dto.acb;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Members' unpaid counts typed in by hand (Stephne, DOD-28: "Unpaids for re-debits are captured manually a day before
 * we run ACB"). Each count is checked by the service, which names every problem by row.
 *
 * @param source  where the counts were read from, at most 100 characters
 * @param entries one to two thousand
 */
public record UnpaidCountsRequest(@NotBlank @Size(max = 100) String source,
                                  @NotNull @Size(min = 1, max = 2000) @Valid List<Entry> entries) {

    /** One member's count, 0 to 3. */
    public record Entry(String membershipNumber, Integer unpaidCount) {
    }
}
