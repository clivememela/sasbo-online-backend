package za.co.titandynamix.sasbo_online_backend.enums;

public enum AccountType {

    //-- 1=Cheque, 2=Savings, 3=Transmission
    CHEQUE(1, "Cheque"),
    SAVINGS(2, "Savings"),
    TRANSMISSION(3, "Transmission");

    private final int accountTypeCode;
    private final String accountType;


    AccountType(int accountTypeCode, String accountType) {
        this.accountTypeCode = accountTypeCode;
        this.accountType = accountType;
    }
}
