package za.co.titandynamix.sasbo_online_backend.services;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import za.co.titandynamix.sasbo_online_backend.dto.payroll.PayrollEmployerRef;
import za.co.titandynamix.sasbo_online_backend.dto.payroll.PayrollPage;
import za.co.titandynamix.sasbo_online_backend.dto.payroll.PayrollPaymentDto;
import za.co.titandynamix.sasbo_online_backend.dto.payroll.PayrollPaymentRequest;
import za.co.titandynamix.sasbo_online_backend.entity.payroll.PayrollEmployer;
import za.co.titandynamix.sasbo_online_backend.entity.payroll.PayrollFile;
import za.co.titandynamix.sasbo_online_backend.entity.payroll.PayrollPayment;
import za.co.titandynamix.sasbo_online_backend.enums.PayrollEventKind;
import za.co.titandynamix.sasbo_online_backend.enums.PayrollFileStatus;
import za.co.titandynamix.sasbo_online_backend.exception.PayrollRefusal;
import za.co.titandynamix.sasbo_online_backend.exception.PayrollRefusalCode;
import za.co.titandynamix.sasbo_online_backend.payroll.PayrollKeys;
import za.co.titandynamix.sasbo_online_backend.payroll.PayrollRequests;
import za.co.titandynamix.sasbo_online_backend.repository.PayrollFileRepository;
import za.co.titandynamix.sasbo_online_backend.repository.PayrollPaymentRepository;
import za.co.titandynamix.sasbo_online_backend.repository.PayrollReads;
import za.co.titandynamix.sasbo_online_backend.repository.PayrollStore;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The money each employer pays over, recorded as it arrives, and the check of it against the month's file (Stephne,
 * PAY-28: "Yes, in this module"): its total less the employer's fee -- Absa keeps 3% (PAY-27). Recording a payment
 * twice by a double click records it once.
 */
@Service
public class PayrollPayments {

    private final PayrollEmployers employers;
    private final PayrollPaymentRepository payments;
    private final PayrollFileRepository files;
    private final PayrollReconciler reconciler;
    private final PayrollStore store;
    private final PayrollReads reads;
    private final PayrollAudit audit;
    private final Clock clock;
    private final TransactionTemplate transactions;

    public PayrollPayments(PayrollEmployers employers, PayrollPaymentRepository payments, PayrollFileRepository files,
                           PayrollReconciler reconciler, PayrollStore store, PayrollReads reads, PayrollAudit audit,
                           Clock clock, PlatformTransactionManager transactionManager) {
        this.employers = employers;
        this.payments = payments;
        this.files = files;
        this.reconciler = reconciler;
        this.store = store;
        this.reads = reads;
        this.audit = audit;
        this.clock = clock;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    /** A payment recorded -- or the same one found on record already -- and the month's file checked again. */
    public PayrollPaymentDto record(PayrollPaymentRequest request, String actor) {
        PayrollEmployer employer = employers.named(request.employerId());
        LocalDate period = PayrollRequests.period(request.period(), clock).atDay(1);
        BigDecimal amount = PayrollKeys.signedAmount(request.amount());
        if (amount == null || amount.signum() <= 0 || amount.compareTo(new BigDecimal("100000000")) >= 0) {
            throw new PayrollRefusal(PayrollRefusalCode.PAYMENT_INVALID, "The amount received could not be read as an "
                    + "amount above nought.", Map.of("field", "amount"));
        }
        LocalDate receivedOn = PayrollRequests.day(request.receivedOn(), clock, "receivedOn");
        String reference = request.reference() == null || request.reference().isBlank() ? null
                : request.reference().strip();
        List<PayrollPayment> same = payments.findSame(employer.getId(), period, amount, receivedOn, reference);
        if (!same.isEmpty()) {
            return dto(same.get(0), employer, true);
        }
        try {
            PayrollPayment saved = transactions.execute(status -> {
                PayrollPayment payment = new PayrollPayment();
                payment.setPayrollEmployerId(employer.getId());
                payment.setPeriod(period);
                payment.setAmount(amount);
                payment.setReceivedOn(receivedOn);
                payment.setReference(reference);
                payment.setRecordedBy(actor);
                payment.setRecordedAt(clock.instant());
                payment = payments.saveAndFlush(payment);
                Optional<PayrollFile> live = files.findFirstByPayrollEmployerIdAndPeriodAndStatus(employer.getId(),
                        period, PayrollFileStatus.RECONCILED);
                live.ifPresent(file -> {
                    reconciler.checkPayment(file.getId(), employer.getId(), YearMonth.from(period), file.getFileTotal(),
                            file.getAdminFeePercent(), actor);
                    store.recount(file.getId());
                });
                audit.record(PayrollEventKind.PAYMENT_RECORDED, live.map(PayrollFile::getId).orElse(null), null,
                        "Payment of R" + PayrollKeys.money(amount) + " recorded for " + employer.getCode() + " "
                                + YearMonth.from(period), actor);
                return payment;
            });
            return dto(saved, employer, false);
        } catch (DataIntegrityViolationException raced) {
            List<PayrollPayment> again = payments.findSame(employer.getId(), period, amount, receivedOn, reference);
            if (!again.isEmpty()) {
                return dto(again.get(0), employer, true);
            }
            throw raced;
        }
    }

    public PayrollPage<PayrollPaymentDto> list(Long employerId, String period, Integer page, Integer size) {
        int[] paging = PayrollRequests.page(page, size);
        return reads.payments(employerId, PayrollRequests.periodFilter(period, clock), paging[0], paging[1]);
    }

    private static PayrollPaymentDto dto(PayrollPayment p, PayrollEmployer employer, boolean alreadyRecorded) {
        return new PayrollPaymentDto(p.getId(), new PayrollEmployerRef(employer.getId(), employer.getCode(),
                employer.getName()), YearMonth.from(p.getPeriod()).toString(), PayrollKeys.money(p.getAmount()),
                p.getReceivedOn().toString(), p.getReference(), p.getRecordedBy(), p.getRecordedAt().toString(),
                alreadyRecorded);
    }
}
