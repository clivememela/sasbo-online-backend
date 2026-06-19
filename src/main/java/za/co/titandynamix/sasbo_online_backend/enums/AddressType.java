package za.co.titandynamix.sasbo_online_backend.enums;

public enum AddressType {
    PERSONAL(1, "Personal"),
    ALTERNATE(2, "Alternate"),
    WORK(3, "Work");

    private final int accountTypeCode;
    private final String addressType;

    AddressType(int accountTypeCode,String addressType) {
        this.accountTypeCode = accountTypeCode;
        this.addressType = addressType;
    }
}
