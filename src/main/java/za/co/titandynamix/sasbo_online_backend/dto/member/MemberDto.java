package za.co.titandynamix.sasbo_online_backend.dto.member;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.StringSerializer;
import lombok.Data;
import za.co.titandynamix.sasbo_online_backend.enums.WelcomeLetterOutcome;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class MemberDto {
    private Integer id;
    private String membershipNumber;
    private String employeeNumber;
    private String title;
    private String initials;
    private String surname;
    private String firstNames;
    private String identityType;
    private String identityNumber;
    private String passportNumber;
    private LocalDate dateOfBirth;
    private String gender;
    private String race;
    private String status;
    private LocalDate joinDate;

    private String personalEmail;
    private String workEmail;
    private String preferredEmail;
    private String phone;
    private String cellTel;
    private String workTel;
    private String homeTel;
    private String address;

    // Employment & Bank
    private String jobGrade;
    private String bankName;
    private String bankAccountNumber;
    /** "Cheque", "Savings" or "Transmission" -- ACB {@code 00.5}; see {@code Member#accountType}. */
    private String accountType;
    private String branch;
    private String ibtNo;
    /*private String workIbtNo;*/
    private String bankAddress;
    private String workBankAddress;
    private String employmentType;
    private String paymentType;
    private Integer debitDay;
    // Job grade Track fields
    private Integer jobGradeId;
    private String jobGradeName;

    /**
     * What the server did with the submitted job grade -- MATCHED, CAPTURED or
     * CLEARED (02.5, ux.md 6c clause 2).
     *
     * <p>🔴 THIS FIELD IS WHY THE FEATURE WORKS AT ALL. The outcome was originally
     * put only on the {@code Member} ENTITY, as a transient. Every member endpoint
     * returns {@code memberMapper.toDto(...)}, and MapStruct maps by name with
     * {@code unmappedTargetPolicy = IGNORE} -- so the outcome was computed correctly,
     * asserted correctly by the service test, and then silently dropped at the
     * mapper. The browser would have received a member with no outcome and said
     * nothing, which is precisely the silent-discard defect 02.5 exists to remove,
     * reintroduced one layer up.
     *
     * <p>Found by the phase's refactor pass. `MemberDtoJobGradeOutcomeTest` now
     * asserts the wire rather than the calculation.
     */
    private String jobGradeOutcome;

    /**
     * What happened to the welcome letter the capture asked for -- SENT, EMAIL_OFF, NO_ADDRESS or FAILED
     * ({@code WelcomeLetterOutcome}, A64). Set only on the answer to {@code POST /api/members?sendWelcome=true},
     * after the member is saved, and left out of every other answer: a member read back says nothing about
     * a letter, because nothing records one.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private WelcomeLetterOutcome welcomeLetter;

    /**
     * The Recruitment Channel tab, as the stakeholder defined it on 2026-09-09:
     * HOW the member was recruited, WHERE, and by WHOM.
     *
     * <p>All three travel by NAME. `recruitedHow` and `areaRecruited` are written by
     * the capture form and read back resolved; `recruitmentRepresentativeName` above
     * already carried the third.
     *
     * <p>🔴 Declared on the DTO and mapped EXPLICITLY, because 02.5 lost exactly this
     * wire once: a field correct on the entity, absent from the DTO, and dropped in
     * silence by `unmappedTargetPolicy = IGNORE`.
     */
    private String recruitedHow;
    private String areaRecruited;

    // Subscriptions
    private BigDecimal monthlySubscription;
    private String subscriptionFrequency;

    // Balances
    private BigDecimal currentBalance;
    private BigDecimal arrearsBalance;
    private BigDecimal lastMonthBalance;
    private LocalDate dateLastChanged;

    // Representatives (§4.1, §4.2) // the representative who recruited this member
    private boolean isRepresentative;// the area this member belongs to
    // Area fields
    private Integer areaId;
    private String areaName;

    /** Combined display name of the member's area representative (read-only on the API response). */
    private String areasRep;

    // Recruiter fields
    private Integer recruitmentRepresentativeId;
    private String recruitmentRepresentativeName;

    // Representative Track fields
    private Integer roleId;
    private String roleName;

    private Integer structureId;
    private String structure; // Maps to structure.name
    private LocalDate electionDate;


    // ─── History & Remarks (§5 — retained pending clarification) ───
    private String remarks;      // §5: needs clarification on business purpose
    private String remarks2;     // §5: 2nd Remarks — needs clarification
    private String remarks3;     // Legacy overflow (retained for backward compatibility)
    private String remarks4;     // Legacy overflow (retained for backward compatibility)

    // §6.5: Use Alternate Address — needs clarification on whether still required
    private String useAltAddress;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    private String userId;

    // ─── SASBO structures (V29, CM-05) ───
    private Boolean isShopSteward;
    private Boolean isBec;
    // ─── and what a Yes needs beside it (V55) ───
    private LocalDate shopStewardElectionDate;
    /** The committee the member sits on, by SASBO's code -- what the record sends back to change it. */
    private String becCommitteeCode;
    private String becCommitteeName;

    // ─── COSATU Structure ───
    private String isCosatu;
    private String cosatuLocal;
    private String cosatuRegion;
    private Boolean isCosatuLocal;
    private Boolean isCosatuRegional;
    /**
     * Stored as comma-separated string internally (e.g. "PEC,CEC,Treasurer").
     * Deserialized from a JSON array sent by the frontend.
     * Serialized back as a comma-separated string; frontend splits on "," to rebuild the array.
     */
    @JsonDeserialize(using = StringArrayToCommaSeparatedDeserializer.class)
    private String cosatuPositions;
}
