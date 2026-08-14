package za.co.titandynamix.sasbo_online_backend.dto.member;

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
    private String gender;
    private String race;
    private String status;
    private LocalDate joinDate;

    private String email;
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

    // Representatives (§4.1, §4.2)
    private Integer recruitmentRepresentativeId;  // the representative who recruited this member
    private Integer areaId;                       // the area this member belongs to

    // History & Remarks
    private String remarks;
    private String remarks2;
    private String remarks3;
    private String remarks4;

    // Use alternate address — retained pending clarification (§6.5)
    private String useAltAddress;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    private String userId;

    // Removed fields per §5 & §6:
    // - managersCode (§6.1)
    // - memnetMember (§6.2)
    // - printLabelForPostage (§6.3)
    // - suspensionCode (§6.4)
    // - deductACB (§6.6)
    // - printMembershipCards (§6.7)
    // - homeLoanReducedRate (§6.8)
}
