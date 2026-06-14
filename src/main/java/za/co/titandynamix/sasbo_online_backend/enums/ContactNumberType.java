package za.co.titandynamix.sasbo_online_backend.enums;

public enum ContactNumberType {
    MOBILE(1, "Mobile"),
    ALTERNATE(2,"Alternate");

    private final int contactNumberCode;
    private final String contactNumberType;

    ContactNumberType(int contactNumberCode,String contactNumberType) {
        this.contactNumberCode = contactNumberCode;
        this.contactNumberType = contactNumberType;
    }
}
