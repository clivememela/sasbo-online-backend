package za.co.titandynamix.sasbo_online_backend.dto.subscription;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import za.co.titandynamix.sasbo_online_backend.enums.SubscriptionsStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * A bank's subscription row. The constraints bind where a route asks for them ({@code @Valid}): a new row (A23 --
 * once the browser posted here instead of to the recruitment channels, the route had to refuse a row it cannot use).
 */
public record SubscriptionsDto(
        Long id,
        @NotBlank(message = "Bank is required.")
        @Size(max = 255, message = "Bank is at most 255 characters.")
        String bankName,
        @Min(value = 1, message = "Debit day must be between 1 and 31.")
        @Max(value = 31, message = "Debit day must be between 1 and 31.")
        int acbDebitDay,
        @NotNull(message = "Subscription amount is required.")
        @Positive(message = "Subscription amount must be more than R0.")
        @Digits(integer = 8, fraction = 2, message = "Subscription amount is rands and cents, at most R99 999 999.99.")
        BigDecimal subscriptionAmount,
        Boolean isCoreBanking,
        SubscriptionsStatus status,
        LocalDateTime registrationDate
) {
}
