package za.co.titandynamix.sasbo_online_backend.dto.migration;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * The old system's month-end figures for a month, as entered: each figure's dimension and key, what it counts, its
 * value and the report it was read from ({@code grlist}, {@code mem_count}, {@code bank_master_summ} ...).
 */
public record MigrationMonthTotalsDto(String month, List<Figure> figures, String enteredBy, String enteredAt) {

    /**
     * One figure. {@code key} is blank for the whole membership, and otherwise the table code, employer, group, area,
     * region or code it is about.
     */
    public record Figure(@NotBlank @Size(max = 20) String dimension, @Size(max = 60) String key,
                         @NotBlank @Size(max = 12) String measure, @NotNull @PositiveOrZero @Max(10_000_000) Long value,
                         @Size(max = 60) String source) {
    }

    /** A month's figures, entered whole: they replace whatever the month had. At most 3,000 -- every key of every list. */
    public record Request(@NotNull @Size(max = 3000) List<@Valid @NotNull Figure> figures) {
    }
}
