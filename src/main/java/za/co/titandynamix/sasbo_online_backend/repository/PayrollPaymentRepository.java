package za.co.titandynamix.sasbo_online_backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.titandynamix.sasbo_online_backend.entity.payroll.PayrollPayment;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Money paid over by employers (PAY-28). */
public interface PayrollPaymentRepository extends JpaRepository<PayrollPayment, Long> {

    /** A month's payments for an employer -- a few at most. */
    List<PayrollPayment> findAllByPayrollEmployerIdAndPeriodOrderByReceivedOnAscIdAsc(Long payrollEmployerId,
                                                                                     LocalDate period);

    /** The same payment recorded already: a double click records it once (V76's {@code payroll_payments_once}). */
    @Query("SELECT p FROM PayrollPayment p WHERE p.payrollEmployerId = :employer AND p.period = :period "
            + "AND p.amount = :amount AND p.receivedOn = :receivedOn "
            + "AND COALESCE(p.reference, '') = COALESCE(:reference, '')")
    List<PayrollPayment> findSame(@Param("employer") Long employer, @Param("period") LocalDate period,
                                  @Param("amount") BigDecimal amount, @Param("receivedOn") LocalDate receivedOn,
                                  @Param("reference") String reference);
}
