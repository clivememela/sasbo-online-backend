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

    private String jobGrade;
    private String paymentType;
    private Integer debitDay;

    private String monthlySubscription;
    private String subscriptionFrequency;

    private String remarks;
    private String remarks2;
    private String remarks3;
    private String remarks4;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    private String userId;
}
