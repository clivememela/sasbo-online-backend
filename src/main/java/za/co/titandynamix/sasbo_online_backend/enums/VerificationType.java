package za.co.titandynamix.sasbo_online_backend.enums;

public enum VerificationType {
    MOBILE( 1, "Mobile"),
    EMAIL( 2,"Email"),
    WHATSAPP( 3,"WhatsApp");

    VerificationType(int verificationTypeCode, String verificationType) {
    }
}
