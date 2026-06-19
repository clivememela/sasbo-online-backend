package za.co.titandynamix.sasbo_online_backend.entity.bank;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import za.co.titandynamix.sasbo_online_backend.enums.AccountType;

// This is the Banking Details Entity in the PostgreSQL database
@Entity
@Getter
@Setter
@AllArgsConstructor
@RequiredArgsConstructor
@Table(name = "banking_details")
public class BankingDetails {
    // Define the Primary Key
    @Id
    @GeneratedValue
    @Column(name = "id")
    private Integer id;

    @Column(name = "account_holder_name", nullable = false)
    private String accountHolderName;

    @Column(name = "account_number", nullable = false)
    private String accountNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "account_type", nullable = false)
    private AccountType accountType;

    @ManyToOne
    @JoinColumn(name = "bank_id", nullable = false)
    private Bank bank;

    @ManyToOne
    @JoinColumn(name = "branch_id", nullable = false)
    private Branch branch;

    public String getBranchCode() {
        return branch != null ? branch.getBranchCode() : null;
    }
}
