package za.co.titandynamix.sasbo_online_backend.dto.bank;

import lombok.Data;
import za.co.titandynamix.sasbo_online_backend.enums.AccountType;

@Data
public class BankingDetailsDto {
    private Integer id;
    private String accountHolderName;
    private String accountNumber;
    private AccountType accountType;
    private Integer bankId;
    private String bankName;
    private Integer branchId;
    private String branchName;
    private String branchCode;
}
