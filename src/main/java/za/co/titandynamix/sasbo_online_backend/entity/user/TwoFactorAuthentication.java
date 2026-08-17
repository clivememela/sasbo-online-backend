package za.co.titandynamix.sasbo_online_backend.entity.user;

import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.Data;
import za.co.titandynamix.sasbo_online_backend.enums.VerificationType;

@Data
public class TwoFactorAuthentication {
    @jakarta.persistence.Column(name = "tfa_enabled")
    private boolean enabled;
    @Enumerated(EnumType.STRING)
    private VerificationType sendTo;

}
