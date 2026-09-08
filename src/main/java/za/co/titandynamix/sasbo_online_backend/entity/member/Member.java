package za.co.titandynamix.sasbo_online_backend.entity.member;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import za.co.titandynamix.sasbo_online_backend.entity.location.Area;
import za.co.titandynamix.sasbo_online_backend.enums.PaymentType;
import za.co.titandynamix.sasbo_online_backend.entity.representative.Representative;
import za.co.titandynamix.sasbo_online_backend.entity.representative.RepresentativeRole;
import za.co.titandynamix.sasbo_online_backend.entity.representative.UnionStructure;
import za.co.titandynamix.sasbo_online_backend.entity.user.User;
import za.co.titandynamix.sasbo_online_backend.validator.MemberValidator;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Getter @Setter @AllArgsConstructor @NoArgsConstructor
@Table(name = "members")
public class Member {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) @Column(name = "id")
    private Integer id;

    private String membershipNumber;
    private String employeeNumber;
    private String title;
    private String initials;

    @Column(name = "first_names")
    private String firstNames;

    @Column(name = "surname")
    private String surname;

    private String identityType;
    private String identityNumber;
    private String passportNumber;
    private LocalDate dateOfBirth;
    private String gender;
    private String race;
    private String status;
    private LocalDate joinDate;

    // Contact
    private String personalEmail;
    private String workEmail;
    private String preferredEmail;
    private String phone;
    private String cellTel;
    private String workTel;
    private String homeTel;
    private String address;

    // Employment & Bank
    private String bankName;
    /**
     * Member's bank account number.
     * REQUIRED when paymentType = "Debit Order"
     * OPTIONAL when paymentType = "Payroll"
     */
    private String bankAccountNumber;
    private String branch;
    private String ibtNo;
    private String workIbtNo;
    private String bankAddress;
    private String workBankAddress;
    private String employmentType;
    /**
     * Payment type for member subscriptions.
     * Values: "Debit Order" (requires bankAccountNumber)
     *         "Payroll" (bankAccountNumber optional)
     * @see PaymentType
     * @see MemberValidator#validatePaymentType(Member)
     */
    private String paymentType;
    private Integer debitDay;

    // Subscriptions
    private String monthlySubscription;
    private String subscriptionFrequency;

    // Balances
    private String currentBalance;
    private String arrearsBalance;
    private String lastMonthBalance;
    private LocalDate dateLastChanged;

    @Column(name = "is_representative")
    private Boolean isRepresentative;

    // Representatives (§4.1, §4.2)
    /** The representative responsible for recruiting this member. */
    // Self-referencing link: A member recruited by another member acting as a Rep
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recruitment_representative_id")
    private Representative recruitmentRepresentative;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "role_id")
    private RepresentativeRole role;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "structure_id")
    private UnionStructure structure;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "job_grade_id")
    private JobGrade jobGrade;

    private LocalDate electionDate;

    /** Area this member belongs to. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "area_id")
    private Area area;

    /**
     * Transient field carrying the area representative's display name.
     * Populated by the API client on write (resolved to recruitmentRepresentative in the service)
     * and computed from recruitmentRepresentative on read (via the mapper).
     */
    @Transient
    private String areasRep;

    /**
     * Transient field carrying the selected structure ID from the API client.
     * Resolved to the {@link UnionStructure} entity in the service layer.
     */
    @Transient
    private Integer structureId;

    /**
     * Transient field carrying the selected role ID from the API client.
     * Resolved to the {@link RepresentativeRole} entity in the service layer.
     */
    @Transient
    private Integer roleId;

    // ─── History & Remarks (§5 — retained pending clarification) ───
    // §5: "Remarks / Comments" — needs clarification on business purpose
    @Column(length = 1000)
    private String remarks;
    // §5: "2nd Remarks Field" — needs clarification on whether required
    @Column(length = 1000)
    private String remarks2;
    // Legacy remarks overflow fields (retained for backward compatibility)
    @Column(length = 1000)
    private String remarks3;
    @Column(length = 1000)
    private String remarks4;

    // §6.5: "Use Alternate Address (Y/N)" — needs clarification on whether still required
    private String useAltAddress;

    // ─── Removed per §6 (columns dropped in V8 migration) ───
    // §6.1 managersCode, §6.2 memnetMember, §6.3 printLabelForPostage,
    // §6.4 suspensionCode, §6.6 deductACB, §6.7 printMembershipCards,
    // §6.8 homeLoanReducedRate

    @Column(nullable = false, updatable = false)
    @CreationTimestamp
    private LocalDateTime createdAt;

    @Column(nullable = false)
    @UpdateTimestamp
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    @OneToOne(fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    @JoinColumn(name = "user_id", unique = true)
    private User user;

    // ─── COSATU Structure ───
    private String isCosatu;
    private String cosatuLocal;
    private String cosatuRegion;
    /** Comma-separated list of held positions: PEC, CEC, Treasurer, Chairperson, Secretary */
    private String cosatuPositions;
}
