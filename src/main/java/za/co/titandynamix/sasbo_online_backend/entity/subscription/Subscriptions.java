package za.co.titandynamix.sasbo_online_backend.entity.subscription;

import jakarta.persistence.*;
import lombok.*;
import za.co.titandynamix.sasbo_online_backend.entity.bank.Bank;
import za.co.titandynamix.sasbo_online_backend.enums.SubscriptionsStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;

@Entity
@Table(name = "subscriptions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Subscriptions {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(nullable = false)
    private Long id;

    @ToString.Exclude
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bank_id")
    private Bank bank;

    @Column(name = "acb_debit_day")
    private int acbDebitDay;

    @Column(name = "subscription_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal subscriptionAmount;

    @Column(name = "core_banking")
    private Boolean isCoreBanking;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SubscriptionsStatus status; // Tracked status field [1]

    @Column(nullable = false)
    private LocalDateTime registrationDate;

    @PrePersist
    protected void onCreate() {
        this.registrationDate = LocalDateTime.now(ZoneId.of("UTC"));
        if (this.status == null) {
            this.status = SubscriptionsStatus.PENDING; // System default initialization [1]
        }
    }

}
