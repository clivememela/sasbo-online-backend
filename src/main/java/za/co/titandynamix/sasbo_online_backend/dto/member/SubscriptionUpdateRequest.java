package za.co.titandynamix.sasbo_online_backend.dto.member;

import lombok.Data;

/**
 * Request body for the monthly subscription maintenance endpoint.
 * Used to change the subscription value during annual maintenance.
 */
@Data
public class SubscriptionUpdateRequest {
    private String monthlySubscription;
}
