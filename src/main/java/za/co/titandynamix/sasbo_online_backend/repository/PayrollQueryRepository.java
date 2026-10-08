package za.co.titandynamix.sasbo_online_backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import za.co.titandynamix.sasbo_online_backend.entity.payroll.PayrollQuery;

import java.util.List;

/** The query lists raised for a file -- a few a file at most. */
public interface PayrollQueryRepository extends JpaRepository<PayrollQuery, Long> {

    List<PayrollQuery> findAllByFileIdOrderByRaisedAtDescIdDesc(Long fileId);
}
