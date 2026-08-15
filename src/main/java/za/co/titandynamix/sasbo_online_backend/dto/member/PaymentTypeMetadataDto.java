package za.co.titandynamix.sasbo_online_backend.dto.member;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Metadata about a payment type for UI field visibility logic.
 * Used by frontend to determine which fields to show/hide based on payment type.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PaymentTypeMetadataDto {
    private String name;
    private String displayName;
    private boolean requiresBankAccount;
}
