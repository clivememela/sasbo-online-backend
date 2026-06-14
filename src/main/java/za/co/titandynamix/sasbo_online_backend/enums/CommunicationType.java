package za.co.titandynamix.sasbo_online_backend.enums;

public enum CommunicationType {
    EMAIL(1,"Email"),
    SMS(2,"Sms"),
    WHATSAPP(3,"WhatsApp");

    private final int communicationTypeCode;
    private final String communicationType;

    CommunicationType(int communicationTypeCode, String communicationType) {
        this.communicationTypeCode = communicationTypeCode;
        this.communicationType = communicationType;
    }
}
