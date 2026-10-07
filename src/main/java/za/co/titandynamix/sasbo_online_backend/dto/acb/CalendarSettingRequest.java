package za.co.titandynamix.sasbo_online_backend.dto.acb;

import jakarta.validation.constraints.NotNull;

/**
 * Whether a day the bank is closed moves the debits on it, as a public holiday does -- DOD-17's open choice: Stephne
 * ticked both "South African public holidays only" and "public holidays and any other day the bank is closed".
 */
public record CalendarSettingRequest(@NotNull Boolean bankClosuresCount) {
}
