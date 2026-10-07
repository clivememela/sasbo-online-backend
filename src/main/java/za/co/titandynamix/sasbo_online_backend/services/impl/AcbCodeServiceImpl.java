package za.co.titandynamix.sasbo_online_backend.services.impl;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import za.co.titandynamix.sasbo_online_backend.acb.AcbTime;
import za.co.titandynamix.sasbo_online_backend.dto.acb.AcbBankCodeDto;
import za.co.titandynamix.sasbo_online_backend.dto.acb.AcbCodesDto;
import za.co.titandynamix.sasbo_online_backend.dto.acb.CodeAddRequest;
import za.co.titandynamix.sasbo_online_backend.dto.acb.CodeChangeRequest;
import za.co.titandynamix.sasbo_online_backend.dto.reference.ReferenceRows;
import za.co.titandynamix.sasbo_online_backend.entity.acb.AcbUnpaidCode;
import za.co.titandynamix.sasbo_online_backend.enums.AcbRunlessEvent;
import za.co.titandynamix.sasbo_online_backend.exception.AcbRefusal;
import za.co.titandynamix.sasbo_online_backend.exception.AcbRefusalCode;
import za.co.titandynamix.sasbo_online_backend.repository.AcbUnpaidCodeRepository;
import za.co.titandynamix.sasbo_online_backend.repository.SasboReferenceRepository;
import za.co.titandynamix.sasbo_online_backend.services.AcbCodeService;

import java.sql.Timestamp;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * The bank's return codes as data (V70).
 *
 * <p>A change goes through JDBC, conditional on the version the screen read: two administrators editing one code get
 * one change and one {@code CODE_VERSION_CONFLICT}, never a silent overwrite. Every change and addition writes its
 * run-less audit row in the same transaction -- the code, what its rule is now, never the administrator's note, which
 * can hold a name (SD3-D).
 */
@Service
public class AcbCodeServiceImpl implements AcbCodeService {

    private final AcbUnpaidCodeRepository codes;
    private final SasboReferenceRepository reference;
    private final AcbAuditTrail audit;
    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final TransactionTemplate tx;

    public AcbCodeServiceImpl(AcbUnpaidCodeRepository codes, SasboReferenceRepository reference, AcbAuditTrail audit,
                              JdbcTemplate jdbc, Clock clock, PlatformTransactionManager transactions) {
        this.codes = codes;
        this.reference = reference;
        this.audit = audit;
        this.jdbc = jdbc;
        this.clock = clock;
        this.tx = new TransactionTemplate(transactions);
    }

    @Override
    public AcbCodesDto codes() {
        Map<Integer, String> names = suspensionNames();
        List<AcbUnpaidCode> all = new ArrayList<>(codes.findAll());
        all.sort(Comparator.comparingInt((AcbUnpaidCode c) -> c.getCode().length()).thenComparing(AcbUnpaidCode::getCode));
        List<AcbBankCodeDto> bankCodes = all.stream().map(c -> dto(c, names)).toList();
        Map<Integer, List<String>> byCode = all.stream().filter(c -> c.getSuspensionCode() != null)
                .collect(Collectors.groupingBy(AcbUnpaidCode::getSuspensionCode, LinkedHashMap::new,
                        Collectors.mapping(AcbUnpaidCode::getCode, Collectors.toList())));
        List<AcbCodesDto.SuspensionCode> suspension = names.entrySet().stream()
                .map(e -> new AcbCodesDto.SuspensionCode(e.getKey(), e.getValue(), byCode.getOrDefault(e.getKey(), List.of())))
                .toList();
        return new AcbCodesDto(bankCodes, suspension);
    }

    @Override
    public AcbBankCodeDto change(String bankCode, CodeChangeRequest request, String actor) {
        String code = bankCode == null ? "" : bankCode.trim();
        requireSuspensionCode(request.suspensionCode());
        tx.executeWithoutResult(status -> {
            AcbUnpaidCode current = codes.findById(code).orElseThrow(() -> notFound(code));
            int updated = jdbc.update("UPDATE acb_unpaid_codes SET re_debit_allowed = ?, re_debit_inferred = ?, "
                            + "re_debit_source = ?, suspension_code = ?, wording = ?, provisional = ?, note = ?, "
                            + "updated_by = ?, updated_at = ?, version = COALESCE(version, 0) + 1 "
                            + "WHERE code = ? AND COALESCE(version, 0) = ?",
                    request.reDebit().stored(), false, "Set by " + actor, request.suspensionCode(),
                    request.wording().trim(), request.provisional(), request.note().trim(), actor,
                    Timestamp.from(AcbTime.instant(clock)), code, request.version());
            if (updated == 0) {
                throw new AcbRefusal(AcbRefusalCode.CODE_VERSION_CONFLICT, "Someone changed bank code " + code
                        + " first. Here it is as it stands now; make your change again if it is still needed.",
                        Map.of("bankCode", code, "version", String.valueOf(versionOf(current))));
            }
            // 🔒 What the rule is now, never the note: it can carry a person's name (SD3-D).
            audit.recordRunless(AcbRunlessEvent.CODE_CHANGED, "bank code " + code + ": re-debit " + request.reDebit()
                    + ", SASBO code " + (request.suspensionCode() == null ? "none" : request.suspensionCode())
                    + (request.provisional() ? ", provisional" : ", settled"), actor);
        });
        return read(code);
    }

    @Override
    public AcbBankCodeDto add(CodeAddRequest request, String actor) {
        String code = request.bankCode().trim();
        requireSuspensionCode(request.suspensionCode());
        try {
            tx.executeWithoutResult(status -> {
                codes.findById(code).ifPresent(listed -> {
                    throw alreadyListed(code);
                });
                jdbc.update("INSERT INTO acb_unpaid_codes (code, reason, wording, inferred, source, re_debit_allowed, "
                                + "re_debit_inferred, suspension_code, re_debit_source, provisional, note, updated_by, "
                                + "updated_at, version) VALUES (?, ?, ?, FALSE, ?, ?, FALSE, ?, ?, TRUE, ?, ?, ?, 0)",
                        code, request.reason().name(), request.wording().trim(), "Added by " + actor,
                        request.reDebit().stored(), request.suspensionCode(), "Set by " + actor, request.note().trim(),
                        actor, Timestamp.from(AcbTime.instant(clock)));
                audit.recordRunless(AcbRunlessEvent.CODE_ADDED, "bank code " + code + " (" + request.reason()
                        + "): re-debit " + request.reDebit(), actor);
            });
        } catch (DataIntegrityViolationException lostTheRace) {
            // Another administrator added the same code between the check and the insert: the key is the arbiter.
            throw alreadyListed(code);
        }
        return read(code);
    }

    // ------------------------------------------------------------------------------------------

    private AcbBankCodeDto read(String code) {
        return dto(codes.findById(code).orElseThrow(() -> notFound(code)), suspensionNames());
    }

    private Map<Integer, String> suspensionNames() {
        Map<Integer, String> names = new LinkedHashMap<>();
        for (ReferenceRows.SuspensionCode row : reference.suspensionCodes()) {
            names.put(row.code(), row.name());
        }
        return names;
    }

    /** A SASBO suspension code must be on record: refused, naming those that are, before anything is written. */
    private void requireSuspensionCode(Integer suspensionCode) {
        if (suspensionCode != null && reference.suspensionCode(suspensionCode).isEmpty()) {
            throw new AcbRefusal(AcbRefusalCode.SUSPENSION_CODE_UNKNOWN, "SASBO's suspension code " + suspensionCode
                    + " is not on the list. Choose one that is.",
                    Map.of("suspensionCode", String.valueOf(suspensionCode), "known",
                            reference.suspensionCodeNumbers().stream().map(String::valueOf)
                                    .collect(Collectors.joining(","))));
        }
    }

    static AcbBankCodeDto dto(AcbUnpaidCode c, Map<Integer, String> names) {
        return new AcbBankCodeDto(c.getCode(), c.getReason(), c.getWording(), c.reDebit(), c.getReDebitSource(),
                c.getSuspensionCode(), c.getSuspensionCode() == null ? null : names.get(c.getSuspensionCode()),
                c.isProvisional(), c.getNote(), c.getSource(), c.getUpdatedBy(), AcbTime.iso(c.getUpdatedAt()),
                versionOf(c));
    }

    private static int versionOf(AcbUnpaidCode c) {
        return Objects.requireNonNullElse(c.getVersion(), 0);
    }

    private static AcbRefusal notFound(String code) {
        return new AcbRefusal(AcbRefusalCode.CODE_NOT_FOUND, "Bank code " + code + " is not in the list.",
                Map.of("bankCode", code));
    }

    private static AcbRefusal alreadyListed(String code) {
        return new AcbRefusal(AcbRefusalCode.CODE_ALREADY_LISTED, "Bank code " + code + " is in the list already. "
                + "Change it rather than adding it again.", Map.of("bankCode", code));
    }
}
