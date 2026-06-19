package za.co.titandynamix.sasbo_online_backend.dto.bank;

import lombok.Data;

@Data
public class BankBranchDto {
    private Integer id;
    private String bankName;
    private String bankAbbreviation;
    private String branch;
    private String branchCode;
    private String area;
    private String randomAccessNumber;
    private String phoneNumber;
    private String managerName;
    private String fax;
    private String email;
    private String category;
    private Integer repsCount;
    private Integer membersCount;
    private Integer insertsCount;
    private Integer branchStaffComplement;
    private Integer branchMemberComplement;
    private String lastVisitedDate;
    private String sasboAreaCode;
    private String bulkMail;
    private String provinceCommitteeCode;
    private String provinceCommitteeName;
    private String bankDepartmentCode;
    private Double subsValueBranch;
    private Integer tableCode;
    private String physicalAddress;
    private String postalAddress;
}
