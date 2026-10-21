package za.co.titandynamix.sasbo_online_backend.dto.acb;

import jakarta.validation.constraints.NotNull;

/**
 * Move every debit-order member of a bank to a new debit day, in one step (Stephne, DOD-19).
 *
 * @param newDay        the 20th to the 31st
 * @param expectedCount how many the preview said would move: a different number refuses the move (MOVE_COUNT_CHANGED)
 */
public record DebitDayMoveRequest(@NotNull Integer newDay, @NotNull Integer expectedCount) {
}
