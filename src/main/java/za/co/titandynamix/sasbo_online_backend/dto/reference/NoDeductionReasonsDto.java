package za.co.titandynamix.sasbo_online_backend.dto.reference;

import java.util.List;

/**
 * The reasons for no payroll deduction, every one, in the drop-down's order (V79, MEN-17) -- with the limits a screen
 * holds its form to: how many reasons the list may hold, how long a name and a typed note may be.
 */
public record NoDeductionReasonsDto(List<NoDeductionReasonDto> rows, int ceiling, int nameLimit, int noteLimit) {
}
