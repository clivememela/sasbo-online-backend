package za.co.titandynamix.sasbo_online_backend.entity.member;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import za.co.titandynamix.sasbo_online_backend.entity.location.Area;
import za.co.titandynamix.sasbo_online_backend.entity.recruitment.Recruiter;
import za.co.titandynamix.sasbo_online_backend.entity.recruitment.RecruitmentChannel;
import za.co.titandynamix.sasbo_online_backend.entity.reference.Committee;
import za.co.titandynamix.sasbo_online_backend.enums.JobGradeOutcome;
import za.co.titandynamix.sasbo_online_backend.enums.PaymentType;
import za.co.titandynamix.sasbo_online_backend.entity.representative.Representative;
import za.co.titandynamix.sasbo_online_backend.entity.representative.RepresentativeRole;
import za.co.titandynamix.sasbo_online_backend.entity.representative.UnionStructure;
import za.co.titandynamix.sasbo_online_backend.dto.member.StringArrayToCommaSeparatedDeserializer;
import za.co.titandynamix.sasbo_online_backend.entity.user.User;
import za.co.titandynamix.sasbo_online_backend.validator.MemberValidator;

import java.math.BigDecimal;
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
    /**
     * The type of the account the debit order draws on -- "Cheque", "Savings" or "Transmission", as
     * the capture screen offers it (ACB {@code 00.5}, V49). The bureau file prints its digit on every
     * debit; {@code null} for every member captured before V49, whom the run leaves out and names.
     * @see za.co.titandynamix.sasbo_online_backend.enums.AccountType
     * @see MemberValidator#validateAccountType(Member, boolean)
     */
    private String accountType;
    private String branch;
    private String ibtNo;
    /*private String workIbtNo;*/
    private String bankAddress;
    private String workBankAddress;
    private String employmentType;
    /**
     * Payment type for member subscriptions.
     * Values: "Debit Order" (requires bankAccountNumber)
     *         "Payroll" (bankAccountNumber optional)
     * @see PaymentType
     * @see MemberValidator#validatePaymentType(Member, Integer)
     */
    private String paymentType;
    private Integer debitDay;

    // Subscriptions
    // 🔴 NUMERIC(12,2) since V40, not text. These four were VARCHAR(255) from V1, which is
    // why '100.00' < '9.00' was true in this database: money held as text cannot be summed,
    // compared or ordered, and the ACB module's whole output is a control total the bureau
    // reconciles against.
    //
    // 🔑 THE COLUMN NAMES ARE TRANSITIONAL, AND THAT IS DELIBERATE. V40 adds these NUMERIC
    // columns BESIDE the original VARCHAR ones and leaves those untouched, so a build that
    // predates this change keeps reading and writing exactly what it always did -- which
    // matters because Flyway runs at boot, before the new instance passes its health check,
    // so the old build is still serving live traffic when the migration commits (F38).
    // V49 drops the text columns and renames these into their final names, and this entity
    // then moves once more. Two renames is the price of a deploy that cannot break the
    // running build; an earlier version of V40 renamed instead and would have broken it.
    @Column(name = "monthly_subscription_numeric")
    private BigDecimal monthlySubscription;

    private String subscriptionFrequency;

    // Balances
    @Column(name = "current_balance_numeric")
    private BigDecimal currentBalance;

    @Column(name = "arrears_balance_numeric")
    private BigDecimal arrearsBalance;

    @Column(name = "last_month_balance_numeric")
    private BigDecimal lastMonthBalance;
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

    /**
     * WHO SIGNED THIS MEMBER UP -- the stakeholder's capture item (3), "where
     * applicable". Its own column since V25.
     *
     * <p>🔑 NOT {@link #recruitmentRepresentative}, and the difference is the
     * finding 03.10 turned into. A RECRUITER signed the member up; a
     * REPRESENTATIVE sits on a structure with a role and an election date. One
     * column was serving both, so neither worked: `representatives` is seeded by
     * nothing, `is_representative` is absent from the member seed, and the
     * drop-down was EMPTY for every user while 33 real recruiter names sat in
     * `recruiters` referenced by no foreign key at all.
     *
     * <p>Same species as {@link #area} vs {@link #recruitedArea} (V24) and
     * {@code job_grades.description} (V21): one name meaning two things.
     *
     * <p>{@code recruitmentRepresentative} above is left in place and unread. It
     * is upstream's column and it is on `main`; removing it is a merge decision,
     * not this arc's.
     */
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recruiter_id")
    private Recruiter recruiter;

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

    /**
     * Area this member BELONGS to -- organisational, ongoing, and it changes when a
     * member transfers.
     *
     * <p>⚠️ NOT the area they were recruited in. See {@link #recruitedArea}: 03.3
     * briefly wrote the recruitment area here, and V24 separated them.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "area_id")
    private Area area;

    /**
     * The area the member was RECRUITED in (RE-3) -- historical, fixed at capture.
     *
     * <p>🔴 Its own column since V24. 03.3 wired this into {@link #area}, whose own
     * javadoc already said "Area this member belongs to" and which
     * {@code RepresentativeServiceImpl} reads as exactly that. Overloading the one
     * column meant a member transferring area would silently rewrite where they were
     * recruited, and a capture would overwrite the organisational area a
     * representative is listed under. Neither would have failed anything.
     *
     * <p>🔑 The same defect V21 fixed on {@code job_grades.description}: one column
     * carrying two meanings.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recruited_area_id")
    private Area recruitedArea;

    /**
     * Transient field carrying the area representative's display name.
     * Populated by the API client on write (resolved to recruitmentRepresentative in the service)
     * and computed from recruitmentRepresentative on read (via the mapper).
     */
    @Transient
    private String areasRep;

    /**
     * The RECRUITER -- the person who signed this member up (R1, R5).
     *
     * <p>🔴 Before 03.3 the capture form sent `recruitmentRep` and NOTHING on this
     * entity was called that, so Jackson ignored it as an unknown property and the
     * recruiter was dropped on every single capture. The save reported success. It is
     * the A31 class -- a field that reads but does not write -- in the field the whole
     * "Recruitment Channel" tab exists for.
     *
     * <p>Transient because it carries a NAME on the wire and resolves to
     * {@link #recruitmentRepresentative} in the service, exactly as {@code areasRep}
     * has always done for the register's edit path.
     */
    @Transient
    private String recruitmentRep;

    /**
     * HOW the member was recruited -- the stakeholder's item (1) for this tab.
     *
     * <p>🔑 The reference data has existed all along and nothing pointed at it.
     * `recruitment_channels` is seeded (BEC, Website, EORO, RO, MSO, Youth), served
     * at /api/recruitment-channels with full CRUD, and administered by its own
     * screen -- wired to no member. Capturing it is what turns that table from
     * decoration into a fact about a person.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recruitment_channel_id")
    private RecruitmentChannel recruitmentChannel;

    /**
     * The channel NAME on the wire, resolved to {@link #recruitmentChannel} in the
     * service -- the same shape as {@code areaRecruited} and {@code recruitmentRep}.
     */
    @Transient
    private String recruitedHow;

    /**
     * The AREA the member was recruited in (R2, RE-3).
     *
     * <p>🔑 THIS IS A CHANGE OF MEANING, NOT A RENAME. The form's old "Area's
     * Recruiter" was populated from the list of recruiter PEOPLE, so it captured a
     * person under an area-shaped name. The specification asks for an AREA. Renaming
     * the label alone would have left the screen collecting the wrong kind of thing
     * behind a corrected word, which is worse than leaving it obviously wrong.
     *
     * <p>Resolves to {@link #recruitedArea}, its OWN column since V24.
     *
     * <p>♻️ This line used to say it resolved to {@link #area} -- true when 03.3
     * wrote it, false the moment V24 separated the two, and still sitting three
     * lines under a javadoc that said the opposite. Corrected by P3's refactor
     * pass. A comment that contradicts the field beside it is worse than none: it
     * is the version a reader trusts, and it survives exactly because nothing
     * compiles it.
     */
    @Transient
    private String areaRecruited;

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

    /**
     * What the server did with the submitted job grade -- MATCHED, CAPTURED or
     * CLEARED (02.5, ux.md 6c clause 2).
     *
     * <p>READ_ONLY on purpose. The client never decides what counts as a custom
     * grade: it sends the string it always sent, the server resolves it, and the
     * answer travels back here. Nothing new is accepted on the request, so a stale
     * client has nothing to lie about.
     *
     * <p>Transient because it describes THIS save, not the member. It is deliberately
     * not persisted: a stored flag would still be sitting there, stale, on the next
     * read of a member nobody had touched.
     */
    @Transient
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private JobGradeOutcome jobGradeOutcome;

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
     * 🔒 Read-only over the wire. This association cascades {@code PERSIST} and
     * {@code MERGE}, so while the member endpoints bind the entity under
     * {@code permitAll()}, a bound {@code user} object is an anonymous write straight
     * into the {@code users} table — {@code username}, {@code enabled} and
     * {@code userRoles} included. ({@code password} happens to be {@code @JsonIgnore}d;
     * that is luck, not a design.) Account creation belongs to the user endpoints.
     *
     * <p>It cascaded {@code ALL} until 2026-09-29, and {@code ALL} includes
     * {@code REMOVE}: deleting a member would have deleted the login linked to it.
     * Nothing deletes a member today, so that path was latent; the narrowing is the
     * incumbent's ({@code cb08ba2}), whose edit also deleted the paragraph above. The
     * paragraph stays -- a narrower cascade does not close the write path it describes.
     * {@code MemberUserCascadeTest} holds the narrowing.
     */
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    @OneToOne(fetch = FetchType.LAZY, cascade = {CascadeType.PERSIST, CascadeType.MERGE})
    @JoinColumn(name = "user_id", unique = true)
    private User user;

    // ─── SASBO structures: the three Edit Member questions ───
    /**
     * 🔴 CM-05: Shop Steward? and BEC Member? had no column, so both answers were
     * dropped on save while the screen reported success. V29 adds them. Null means
     * "never answered" (legacy rows), which is not the same as {@code false}.
     */
    @Column(name = "is_shop_steward")
    private Boolean isShopSteward;

    @Column(name = "is_bec")
    private Boolean isBec;

    /**
     * When the member was elected shop steward (V55): asked once "Shop Steward?" is Yes, and
     * required then -- SASBO, 2026-09-30. Its own column rather than {@link #electionDate}, which
     * is the representative model's and which {@code resolveStructureAndRole} clears for every
     * member not flagged a representative.
     */
    @Column(name = "shop_steward_election_date")
    private LocalDate shopStewardElectionDate;

    /**
     * The Branch Executive Committee the member sits on (V55), one of SASBO's committees.
     * Resolved from the transient {@link #becCommitteeCode}, never bound directly -- the same
     * shape as {@link #structure} and {@code structureId}.
     */
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bec_committee_id")
    private Committee becCommittee;

    /**
     * The committee's code, as the client sends it: SASBO's own identifier, the one
     * {@code GET /api/reference/committees} answers with. Absent keeps the seat, blank clears it,
     * a code sets it (decision ⑨(b)).
     */
    @Transient
    private String becCommitteeCode;

    /**
     * A BEC member's roles (V55), from SASBO's structures tree, 2026-09-30: BEC Recruiter?, BEC
     * Structure Representative? with the structures represented, and BEC Executive? with the one
     * position held. Asked only under BEC Member = Yes, and cleared when it is not
     * ({@code MemberServiceImpl.applySeats}); the lists are held to {@code BecStructure} and
     * {@code BecExecutivePosition} by {@code MemberValidator.validateBecRoles}.
     */
    @Column(name = "is_bec_recruiter")
    private Boolean isBecRecruiter;

    @Column(name = "is_bec_structure_rep")
    private Boolean isBecStructureRep;

    /**
     * Comma-separated, in SASBO's spelling -- {@code cosatuPositions}' shape, and its deserializer
     * for the same reason: the client sends a JSON array to a request body bound to this entity.
     */
    @JsonDeserialize(using = StringArrayToCommaSeparatedDeserializer.class)
    @Column(name = "bec_structures")
    private String becStructures;

    @Column(name = "is_bec_executive")
    private Boolean isBecExecutive;

    @Column(name = "bec_executive_position")
    private String becExecutivePosition;

    // ─── COSATU Structure ───
    private String isCosatu;
    private String cosatuLocal;
    private String cosatuRegion;
    /**
     * Stakeholder, 2026-09-17: a COSATU Local and a COSATU Region are STRUCTURES a
     * member sits on, recorded yes/no. The free-text {@code cosatuLocal} /
     * {@code cosatuRegion} above are kept (no data is discarded) but no screen asks
     * for them any more.
     */
    @Column(name = "is_cosatu_local")
    private Boolean isCosatuLocal;

    @Column(name = "is_cosatu_regional")
    private Boolean isCosatuRegional;
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
