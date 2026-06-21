package za.co.titandynamix.sasbo_online_backend.enums;

public enum VerificationType {
    MOBILE( 1, "Mobile"),
    EMAIL( 2,"Email"),
    WHATSAPP( 3,"WhatsApp");;

    private final int verificationTypeCode;
    private final String verificationType;

    VerificationType(int verificationTypeCode, String verificationType) {
        this.verificationTypeCode = verificationTypeCode;
        this.verificationType = verificationType;
    }
}
