package za.co.titandynamix.sasbo_online_backend.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import za.co.titandynamix.sasbo_online_backend.entity.user.User;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Getter @Setter @AllArgsConstructor @NoArgsConstructor
@Table(name = "members")
public class Member {

    @Id @GeneratedValue @Column(name = "id")
    private Integer id;

    private String membershipNumber;
    private String employeeNumber;
    private String title;
    private String initials;
    private String surname;
    private String firstNames;
    private String identityNumber;
    private String passportNumber;
    private String gender;
    private String race;
    private String status;
    private LocalDate joinDate;

    // Contact
    private String email;
    private String phone;
    private String cellTel;
    private String workTel;
    private String homeTel;
    private String faxNumber;
    private String address;

    // Employment & Bank
    private String bankName;
    private String bankAccountNumber;
    private String branch;
    private String ibtNo;
    private String workIbtNo;
    private String bankAddress;
    private String workBankAddress;
    private String paymentType;
    private Integer debitDay;
    private String jobGrade;

    // Subscriptions
    private String monthlySubscription;
    private String subscriptionFrequency;
    private String deathBenefitSubs;
    private String deathBenefitFrequency;
    private String deathBenefitPolicyNo;
    private String endowmentSubs;
    private String endowmentFrequency;
    private String endowmentPolicyNo;
    private String benevolentSubs;
    private String benevolentFrequency;
    private String benevolentPolicyNo;

    // Balances
    private String currentBalance;
    private String arrearsBalance;
    private String lastMonthBalance;
    private LocalDate dateLastChanged;

    // Representatives
    private String recruitmentRep;
    private String areasRep;
    private String repElectionDay;
    private String repElectionMonth;
    private String repElectionYear;
    private String electedRep;
    private String nationalCouncillor;
    private String branchCommitteeMember;

    // History & flags
    @Column(length = 1000)
    private String remarks;
    @Column(length = 1000)
    private String remarks2;
    @Column(length = 1000)
    private String remarks3;
    @Column(length = 1000)
    private String remarks4;

    private String managersCode;
    private String memnetMember;
    private String printLabelForPostage;
    private String suspensionCode;
    private String useAltAddress;
    private String deductACB;
    private String printMembershipCards;
    private String homeLoanReducedRateOption;

    @Column(nullable = false, updatable = false)
    @CreationTimestamp private LocalDateTime createdAt= LocalDateTime.now();
    @Column(nullable = false)
    @UpdateTimestamp  private LocalDateTime updatedAt = LocalDateTime.now();

    // One-to-One shared primary key or foreign key relationship with User
    @OneToOne(fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    @JoinColumn(name = "user_id", unique = true)
    private User user;

    // Entity lifecycle hooks to automatically track updates
    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

}
