package za.co.titandynamix.sasbo_online_backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import za.co.titandynamix.sasbo_online_backend.entity.bank.BankBranch;

import java.util.List;
import java.util.Optional;

// The BankBranchRepository interface provides data access methods for the BankBranch entity.
// It extends JpaRepository, which gives us basic CRUD operations out-of-the-box.
// We can also define custom query methods here, like findByBankNameAndBranchCode,
// which will be automatically implemented by Spring Data JPA based on the method name.

// A simple repository interface to handle common queries for South African banking lookups.
@Repository
public interface BankBranchRepository extends JpaRepository<BankBranch, Integer> {
    boolean existsByBank_NameAndBranchName(String bankName, String branchName);

    // Find a specific branch by bank and its unique 6-digit code
    Optional<BankBranch> findByBank_NameAndBranchCode(String bankName, String branchCode);

    // Find all branches of a specific bank in a certain city
    List<BankBranch> findByBank_NameAndPhysicalAddress_City_Name(String bankName, String cityName);

    // Find branches in a specific province
    List<BankBranch> findByPhysicalAddress_Province_Name(String provinceName);

    // Efficient JPQL approach: Navigate the object graph directly to the parent name
    @Query("SELECT b.bank.name FROM BankBranch b WHERE b.id = :branchId")
    Optional<String> findBankNameByBranchId(@Param("branchId") Integer branchId);

    // Fetch by SA Branch Code
    @Query("SELECT b.bank.name FROM BankBranch b WHERE b.branchCode = :branchCode")
    Optional<String> findBankNameByBranchCode(@Param("branchCode") String branchCode);
}
