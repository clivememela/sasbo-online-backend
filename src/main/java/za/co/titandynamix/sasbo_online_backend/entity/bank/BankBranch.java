package za.co.titandynamix.sasbo_online_backend.entity.bank;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Getter @Setter @AllArgsConstructor @NoArgsConstructor
@Table(name = "bank_branch")
public class BankBranch {

    @Id @GeneratedValue @Column(name = "id")
    private Integer id;

    private String bankName;
    private String bankAbbreviation;
    private String branch;
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

    @Column(length = 500)
    private String physicalAddress;
    @Column(length = 500)
    private String postalAddress;
}
