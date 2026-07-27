package za.co.titandynamix.sasbo_online_backend.dto.subscription;

import za.co.titandynamix.sasbo_online_backend.enums.SubscriptionsStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record SubscriptionsDto(
        Long id,
        String bankName,
        int acbDebitDay,
        BigDecimal subscriptionAmount,
        SubscriptionsStatus status,
        LocalDateTime registrationDate
) {
}
