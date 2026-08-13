package za.co.titandynamix.sasbo_online_backend.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import za.co.titandynamix.sasbo_online_backend.entity.representative.Representative;
import za.co.titandynamix.sasbo_online_backend.entity.user.User;

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
    private String surname;
    private String firstNames;
    private String identityType;
    private String identityNumber;
    private String passportNumber;
    private String gender;
    private String race;
    private String status;
    private LocalDate joinDate;

    // Contact
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

    // Balances
    private String currentBalance;
    private String arrearsBalance;
    private String lastMonthBalance;
    private LocalDate dateLastChanged;

    // Representatives (§4.1, §4.2)
    /** The representative responsible for recruiting this member. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recruitment_representative_id")
    private Representative recruitmentRepresentative;

    /** Area this member belongs to. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "area_id")
    private Area area;

    // History & Remarks (§5 — remarks fields retained pending clarification)
    @Column(length = 1000)
    private String remarks;
    @Column(length = 1000)
    private String remarks2;
    @Column(length = 1000)
    private String remarks3;
    @Column(length = 1000)
    private String remarks4;

    // Use alternate address — retained pending clarification (§6.5)
    private String useAltAddress;

    @Column(nullable = false, updatable = false)
    @CreationTimestamp
    private LocalDateTime createdAt;

    @Column(nullable = false)
    @UpdateTimestamp
    private LocalDateTime updatedAt;

    @OneToOne(fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    @JoinColumn(name = "user_id", unique = true)
    private User user;
}
