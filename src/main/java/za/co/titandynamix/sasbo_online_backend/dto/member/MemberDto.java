package za.co.titandynamix.sasbo_online_backend.dto.member;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.StringSerializer;
import lombok.Data;

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
    private String branch;
    private String ibtNo;
    private String workIbtNo;
    private String bankAddress;
    private String workBankAddress;
    private String employmentType;
    private String paymentType;
    private Integer debitDay;
    // Job grade Track fields
    private Integer jobGradeId;
    private String jobGradeName;

    // Subscriptions
    private String monthlySubscription;
    private String subscriptionFrequency;

    // Balances
    private String currentBalance;
    private String arrearsBalance;
    private String lastMonthBalance;
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

    // ─── COSATU Structure ───
    private String isCosatu;
    private String cosatuLocal;
    private String cosatuRegion;
    /**
     * Stored as comma-separated string internally (e.g. "PEC,CEC,Treasurer").
     * Deserialized from a JSON array sent by the frontend.
     * Serialized back as a comma-separated string; frontend splits on "," to rebuild the array.
     */
    @JsonDeserialize(using = StringArrayToCommaSeparatedDeserializer.class)
    private String cosatuPositions;
}
