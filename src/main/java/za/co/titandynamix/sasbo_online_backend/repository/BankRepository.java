package za.co.titandynamix.sasbo_online_backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import za.co.titandynamix.sasbo_online_backend.entity.bank.Bank;

@Repository
public interface BankRepository extends JpaRepository<Bank, Integer> {
}
