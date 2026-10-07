package za.co.titandynamix.sasbo_online_backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import za.co.titandynamix.sasbo_online_backend.entity.payroll.PayrollEmployer;

import java.util.List;
import java.util.Optional;

/** The employers that send SASBO a payroll file -- sixteen (PAY-01), so the list is bounded by nature. */
public interface PayrollEmployerRepository extends JpaRepository<PayrollEmployer, Long> {

    List<PayrollEmployer> findAllByOrderByOrdinalAsc();

    Optional<PayrollEmployer> findByCode(String code);
}
