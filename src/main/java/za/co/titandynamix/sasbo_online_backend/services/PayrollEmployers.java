package za.co.titandynamix.sasbo_online_backend.services;

import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.titandynamix.sasbo_online_backend.acb.AcbActor;
import za.co.titandynamix.sasbo_online_backend.dto.payroll.PayrollEmployerDto;
import za.co.titandynamix.sasbo_online_backend.dto.payroll.PayrollSettingsRequest;
import za.co.titandynamix.sasbo_online_backend.dto.payroll.PayrollVocabularyDto;
import za.co.titandynamix.sasbo_online_backend.entity.payroll.PayrollEmployer;
import za.co.titandynamix.sasbo_online_backend.enums.PayrollAmountStatus;
import za.co.titandynamix.sasbo_online_backend.enums.PayrollConfidence;
import za.co.titandynamix.sasbo_online_backend.enums.PayrollDiscrepancyStatus;
import za.co.titandynamix.sasbo_online_backend.enums.PayrollDiscrepancyType;
import za.co.titandynamix.sasbo_online_backend.enums.PayrollEventKind;
import za.co.titandynamix.sasbo_online_backend.enums.PayrollMatchRule;
import za.co.titandynamix.sasbo_online_backend.enums.PayrollOutcome;
import za.co.titandynamix.sasbo_online_backend.exception.PayrollRefusal;
import za.co.titandynamix.sasbo_online_backend.exception.PayrollRefusalCode;
import za.co.titandynamix.sasbo_online_backend.payroll.PayrollField;
import za.co.titandynamix.sasbo_online_backend.payroll.PayrollKeys;
import za.co.titandynamix.sasbo_online_backend.payroll.PayrollLayout;
import za.co.titandynamix.sasbo_online_backend.payroll.PayrollLayouts;
import za.co.titandynamix.sasbo_online_backend.payroll.PayrollLimits;
import za.co.titandynamix.sasbo_online_backend.repository.PayrollEmployerRepository;
import za.co.titandynamix.sasbo_online_backend.repository.PayrollReads;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * The employers who send payroll files, their settings, and the module's vocabulary. The fee an employer keeps is
 * data -- Absa's 3% (PAY-27), and whether it applies to every deduction is still to be confirmed (PAY.md,
 * clarification 5) -- and so is the mapping of a file's headings to fields, which an administrator may override.
 */
@Service
public class PayrollEmployers {

    private static final BigDecimal MAX_FEE = new BigDecimal("99.99");

    private final PayrollEmployerRepository employers;
    private final PayrollReads reads;
    private final PayrollAudit audit;

    public PayrollEmployers(PayrollEmployerRepository employers, PayrollReads reads, PayrollAudit audit) {
        this.employers = employers;
        this.reads = reads;
        this.audit = audit;
    }

    /** Every employer, in Stephne's order, with where its newest file stands. Sixteen rows: three statements. */
    public List<PayrollEmployerDto> list() {
        Map<Long, PayrollEmployerDto.LatestFile> latest = reads.latestFiles();
        Map<Long, List<String>> sasbo = reads.sasboEmployers();
        return employers.findAllByOrderByOrdinalAsc().stream()
                .map(e -> dto(e, latest.get(e.getId()), sasbo.getOrDefault(e.getId(), List.of())))
                .toList();
    }

    /** An employer an upload or a payment names; one not on record is {@code EMPLOYER_UNKNOWN}. */
    public PayrollEmployer named(Long id) {
        return id == null ? null : employers.findById(id).orElseThrow(() -> new PayrollRefusal(
                PayrollRefusalCode.EMPLOYER_UNKNOWN, "That employer is not one that sends a payroll file.",
                PayrollRefusal.details("employerId", String.valueOf(id))));
    }

    /** The layout an employer's file is read by. */
    public PayrollLayout layoutOf(PayrollEmployer employer) {
        try {
            return PayrollLayout.fromJson(employer.getLayout());
        } catch (IllegalStateException unreadable) {
            return PayrollLayouts.byKey(PayrollLayouts.GENERAL).orElseThrow();
        }
    }

    /** An administrator's change to an employer's fee or column mapping, on the version they read. */
    @Transactional
    public PayrollEmployerDto update(long id, PayrollSettingsRequest request) {
        PayrollEmployer employer = employers.findById(id).orElseThrow(() -> new PayrollRefusal(
                PayrollRefusalCode.EMPLOYER_NOT_FOUND, "No such payroll employer."));
        if (!Objects.equals(employer.getVersion(), request.version())) {
            throw new PayrollRefusal(PayrollRefusalCode.SETTINGS_CHANGED_SINCE, "Someone changed this employer's "
                    + "settings after you opened them. Reload to see the change, then try again.",
                    Map.of("version", String.valueOf(employer.getVersion())));
        }
        BigDecimal fee = PayrollKeys.signedAmount(request.adminFeePercent());
        if (fee == null || fee.signum() < 0 || fee.compareTo(MAX_FEE) > 0) {
            throw new PayrollRefusal(PayrollRefusalCode.SETTINGS_INVALID, "The fee is a percentage from 0 to 99.99.",
                    Map.of("field", "adminFeePercent"));
        }
        Map<String, String> mapping = request.columnMapping();
        if (mapping != null) {
            for (Map.Entry<String, String> entry : mapping.entrySet()) {
                if (PayrollField.fromWire(entry.getKey()).isEmpty()
                        || (entry.getValue() != null && entry.getValue().length() > PayrollLimits.MAX_CELL_LENGTH)) {
                    throw new PayrollRefusal(PayrollRefusalCode.SETTINGS_INVALID, "The column mapping names a field "
                            + "a column cannot be, or a heading longer than any on a file.", PayrollRefusal.details(
                            "field", PayrollRefusal.echo(entry.getKey())));
                }
            }
            boolean any = mapping.values().stream().anyMatch(v -> v != null && !v.isBlank());
            if (any && (mapping.get("amount") == null || mapping.get("amount").isBlank())) {
                throw new PayrollRefusal(PayrollRefusalCode.SETTINGS_INVALID, "A column mapping says which heading "
                        + "holds the amount.", Map.of("field", "amount"));
            }
        }
        BigDecimal oldFee = employer.getAdminFeePercent();
        employer.setAdminFeePercent(fee.setScale(2));
        employer.setColumnMapping(PayrollLayouts.mappingJson(mapping));
        try {
            employers.saveAndFlush(employer);
        } catch (ObjectOptimisticLockingFailureException raced) {
            throw new PayrollRefusal(PayrollRefusalCode.SETTINGS_CHANGED_SINCE, "Someone changed this employer's "
                    + "settings at the same moment. Reload to see the change, then try again.");
        }
        audit.record(PayrollEventKind.SETTINGS_CHANGED, null, null, "Settings of " + employer.getCode()
                + " changed: fee " + PayrollKeys.money(oldFee) + "% to " + PayrollKeys.money(fee) + "%; column mapping "
                + (employer.getColumnMapping() == null ? "none" : "set"), AcbActor.current());
        return dto(employer, reads.latestFiles().get(employer.getId()),
                reads.sasboEmployers().getOrDefault(employer.getId(), List.of()));
    }

    private static PayrollEmployerDto dto(PayrollEmployer e, PayrollEmployerDto.LatestFile latest,
                                          List<String> sasbo) {
        Map<String, String> mapping = PayrollLayouts.mapping(e.getColumnMapping());
        return new PayrollEmployerDto(e.getId(), e.getCode(), e.getName(), e.getDelivery().name(),
                Boolean.TRUE.equals(e.getReadable()), e.getLayoutKey(), PayrollKeys.money(e.getAdminFeePercent()),
                mapping.isEmpty() ? null : mapping, sasbo, latest, e.getVersion());
    }

    /** The module's words: what a screen builds its filters and choices from. */
    public PayrollVocabularyDto vocabulary() {
        List<PayrollVocabularyDto.DiscrepancyType> types = Arrays.stream(PayrollDiscrepancyType.values())
                .map(t -> new PayrollVocabularyDto.DiscrepancyType(t.name(), t.label(),
                        t.rule().isEmpty() ? null : t.rule(), t.whatToDo(),
                        t.queryAction() == null ? null : t.queryAction().name(),
                        t.outcomes().stream().map(Enum::name).toList()))
                .toList();
        List<PayrollVocabularyDto.Outcome> outcomes = Arrays.stream(PayrollOutcome.values())
                .map(o -> new PayrollVocabularyDto.Outcome(o.name(), o.label(), o.systemOnly())).toList();
        return new PayrollVocabularyDto(types, outcomes, names(PayrollDiscrepancyStatus.values()),
                Arrays.stream(PayrollMatchRule.values()).filter(r -> r != PayrollMatchRule.NONE).map(Enum::name)
                        .toList(),
                names(PayrollConfidence.values()), names(PayrollAmountStatus.values()),
                new PayrollVocabularyDto.Limits(PayrollLimits.MAX_BYTES, PayrollLimits.MAX_ROWS,
                        PayrollLimits.MAX_TYPED_ROWS, PayrollLimits.NOTE_MAX_LENGTH, PayrollLimits.PAGE_MAX_SIZE),
                List.of("xlsx", "xls", "csv"));
    }

    private static List<String> names(Enum<?>[] values) {
        return Arrays.stream(values).map(Enum::name).toList();
    }
}
