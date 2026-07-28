package za.co.titandynamix.sasbo_online_backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.titandynamix.sasbo_online_backend.entity.subscription.Subscriptions;

import java.util.List;
import java.util.Optional;

public interface SubscriptionsRepository extends JpaRepository<Subscriptions, Long> {
    @Query("SELECT s FROM Subscriptions s LEFT JOIN FETCH s.bank")
    List<Subscriptions> findAllWithBank();

    @Query("SELECT s FROM Subscriptions s LEFT JOIN FETCH s.bank WHERE s.id = :id")
    Optional<Subscriptions> findByIdWithBank(Long id);

    @Query("SELECT s FROM Subscriptions s LEFT JOIN FETCH s.bank WHERE s.bank.id = :bankId")
    List<Subscriptions> findByBankIdWithBank(Long bankId);
}
