package za.co.titandynamix.sasbo_online_backend.entity.bank;

import jakarta.persistence.*;
import lombok.*;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import za.co.titandynamix.sasbo_online_backend.entity.location.PhysicalAddress;

import java.time.LocalDateTime;

// This entity maps the branch details, includes the 6-digit branch code, and embeds the address
@Entity
@Getter @Setter @AllArgsConstructor @NoArgsConstructor
@Table(name = "bank_branches", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"bank_id", "branch_code"})
})
public class BankBranch {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) @Column(name = "id")
    private Integer id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "bank_id", nullable = false)
    private Bank bank; // Relationship link

    @Column(name = "branch_code", length = 6)
    private String branchCode;

    @Transient
    @JsonProperty("bankName")
    @Getter(AccessLevel.NONE)
    private String tempBankName;

    // Helper method to safely pull the bank name from the parent relationship
    public String getBankName() {
        return this.bank != null ? this.bank.getName() : this.tempBankName;
    }

    private String bankAbbreviation;
    @Column(name = "branch_name")
    private String branchName;
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

    @Embedded
    private PhysicalAddress physicalAddress;

    @Column(length = 500)
    private String postalAddress;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
