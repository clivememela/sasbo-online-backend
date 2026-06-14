package za.co.titandynamix.sasbo_online_backend.dto.bank;

import lombok.Data;

@Data
public class BranchDto {
    private Integer id;
    private String branchName;
    private String branchCode;
    private Integer bankId;
    private String bankName;
    private Long provinceId;
    private String provinceName;
    private String city;
    private String physicalAddress;
    private String postalCode;
    private String phoneNumber;
}
