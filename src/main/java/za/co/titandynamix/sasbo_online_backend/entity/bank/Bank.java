package za.co.titandynamix.sasbo_online_backend.entity.bank;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;

// This is the Bank Entity in the PostgreSQL database
@Entity
@Getter
@Setter
@AllArgsConstructor
@RequiredArgsConstructor
@Table(name = "banks")
public class Bank {
    // Define the Primary Key
    @Id
    @GeneratedValue
    @Column(name = "id")
    private Integer id;

    @Column(nullable = false, unique = true)
    private String name;

    @Column(name = "universal_branch_code", nullable = false, length = 6)
    private String universalBranchCode; //-- South African CBC/UBC
}
