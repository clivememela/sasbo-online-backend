package za.co.titandynamix.sasbo_online_backend.entity.member;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import za.co.titandynamix.sasbo_online_backend.entity.location.Area;
import za.co.titandynamix.sasbo_online_backend.enums.PaymentType;
import za.co.titandynamix.sasbo_online_backend.entity.representative.Representative;
import za.co.titandynamix.sasbo_online_backend.entity.representative.RepresentativeRole;
import za.co.titandynamix.sasbo_online_backend.entity.representative.UnionStructure;
import za.co.titandynamix.sasbo_online_backend.dto.member.StringArrayToCommaSeparatedDeserializer;
import za.co.titandynamix.sasbo_online_backend.entity.user.User;
import za.co.titandynamix.sasbo_online_backend.validator.MemberValidator;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Getter @Setter @AllArgsConstructor @NoArgsConstructor
@Table(name = "members")
public class Member {

    /**
     * Never bound from the request body. {@code POST} allocates it and {@code PUT}
     * takes it from the path, so accepting it over the wire only lets a caller
     * aim a write at a different row.
     */
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) @Column(name = "id")
    private Integer id;

    /** Server-allocated; see the membership-number sequence. Not client input. */
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
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
    /** Resolved from the transient {@code areasRep}, never bound directly. */
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recruitment_representative_id")
    private Representative recruitmentRepresentative;

    /** Resolved from the transient {@code roleId}, never bound directly. */
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "role_id")
    private RepresentativeRole role;

    /** Resolved from the transient {@code structureId}, never bound directly. */
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
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

    /**
     * 🔒 Read-only over the wire. This association cascades {@code ALL}, so while
     * the member endpoints bind the entity under {@code permitAll()}, a bound
     * {@code user} object is an anonymous write straight into the {@code users}
     * table — {@code username}, {@code enabled} and {@code userRoles} included.
     * ({@code password} happens to be {@code @JsonIgnore}d; that is luck, not a
     * design.) Account creation belongs to the user endpoints.
     */
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    @OneToOne(fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    @JoinColumn(name = "user_id", unique = true)
    private User user;

    // ─── COSATU Structure ───
    private String isCosatu;
    private String cosatuLocal;
    private String cosatuRegion;
    /**
     * Comma-separated list of held positions: PEC, CEC, Treasurer, Chairperson, Secretary.
     * <p>
     * The deserializer is on the ENTITY, not only on {@code MemberDto}, because
     * {@code POST}, {@code PUT} and {@code PATCH /banking} all bind
     * {@code @RequestBody Member}. The client sends a JSON array; without this
     * annotation Jackson raises {@code MismatchedInputException} and the save
     * answers 500. Adding the column is what made a previously-ignored unknown
     * property fatal, so any future array field on this entity needs the same
     * treatment.
     */
    @JsonDeserialize(using = StringArrayToCommaSeparatedDeserializer.class)
    private String cosatuPositions;
}
