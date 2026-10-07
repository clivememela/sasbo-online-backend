package za.co.titandynamix.sasbo_online_backend.services.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import za.co.titandynamix.sasbo_online_backend.dto.acb.AcbBankCodeDto;
import za.co.titandynamix.sasbo_online_backend.dto.acb.AcbCodesDto;
import za.co.titandynamix.sasbo_online_backend.dto.acb.CodeAddRequest;
import za.co.titandynamix.sasbo_online_backend.dto.acb.CodeChangeRequest;
import za.co.titandynamix.sasbo_online_backend.dto.acb.RecordUnpaidRequest;
import za.co.titandynamix.sasbo_online_backend.enums.ReDebit;
import za.co.titandynamix.sasbo_online_backend.enums.UnpaidDecision;
import za.co.titandynamix.sasbo_online_backend.enums.UnpaidReason;
import za.co.titandynamix.sasbo_online_backend.exception.AcbRefusal;
import za.co.titandynamix.sasbo_online_backend.exception.AcbRefusalCode;
import za.co.titandynamix.sasbo_online_backend.fixtures.AcbRunFixture;
import za.co.titandynamix.sasbo_online_backend.services.AcbCodeService;
import za.co.titandynamix.sasbo_online_backend.services.AcbReturnsService;
import za.co.titandynamix.sasbo_online_backend.support.AbstractAcbIT;
import za.co.titandynamix.sasbo_online_backend.support.MigrationSeed;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

/**
 * The bank's return codes as data (V70), on PostgreSQL: Stephne's "Suspension codes" sheet read both ways round, a
 * code changed by its version and added once, each change audited without the administrator's note -- and the returns
 * honouring a changed rule the moment it is changed (DOD-01, DOD-03, DOD-04; the build's corrections of 6 October:
 * bank 2 may be re-debited, 4 and 30, 31, 32 and 34 never, 6, 15, 18 and 26 not known).
 */
@DisplayName("AcbCodeService -- the bank's codes as data (V70)")
class AcbCodeServiceImplIT extends AbstractAcbIT {

    private static final BigDecimal RATE = new BigDecimal("174.00");
    private static final LocalDate SEPTEMBER = LocalDate.of(2026, 9, 1);

    @Autowired
    private AcbCodeService codes;
    @Autowired
    private AcbReturnsService returns;

    private static AcbRefusal refusal(Runnable call) {
        AcbRefusal refused = catchThrowableOfType(AcbRefusal.class, call::run);
        assertThat(refused).as("refused").isNotNull();
        return refused;
    }

    /** The reset empties SASBO's lists, which V70 and the reference-list seeder fill: V70's codes are put back. */
    @BeforeEach
    void sasbosCodesAreOnRecord() {
        for (int code : MigrationSeed.SUSPENSION_CODES) {
            jdbc.update("INSERT INTO suspension_codes (code, name) SELECT ?, ? WHERE NOT EXISTS "
                    + "(SELECT 1 FROM suspension_codes WHERE code = ?)", code, "CODE " + code, code);
        }
    }

    @Test
    @DisplayName("Stephne's sheet both ways round: each bank code with its rule and SASBO's code, as V70 wrote them -- "
            + "and each of SASBO's codes with the bank codes that record it")
    void theSheetBothWaysRound() {
        AcbCodesDto read = codes.codes();

        assertThat(read.bankCodes()).extracting(AcbBankCodeDto::bankCode)
                .containsExactlyElementsOf(MigrationSeed.ACB_UNPAID_CODES.stream().map(MigrationSeed.UnpaidCode::code)
                        .toList());
        Map<String, AcbBankCodeDto> byCode = read.bankCodes().stream()
                .collect(Collectors.toMap(AcbBankCodeDto::bankCode, Function.identity()));
        for (MigrationSeed.UnpaidCode seeded : MigrationSeed.ACB_UNPAID_CODES) {
            AcbBankCodeDto code = byCode.get(seeded.code());
            assertThat(code.reDebit()).as("bank code %s's rule", seeded.code())
                    .isEqualTo(ReDebit.of(seeded.reDebitAllowed()));
            assertThat(code.suspensionCode()).as("bank code %s's SASBO code", seeded.code())
                    .isEqualTo(seeded.suspensionCode());
            assertThat(code.provisional()).as("bank code %s provisional", seeded.code()).isEqualTo(seeded.provisional());
            assertThat(code.version()).isZero();
            assertThat(code.updatedBy()).as("as the migration wrote it").isNull();
        }
        assertThat(byCode.get("18").suspensionCode()).as("the bank's 18, \"Deceased\", names none of SASBO's codes")
                .isNull();
        assertThat(byCode.get("18").suspensionName()).isNull();
        assertThat(byCode.get("2").suspensionName()).as("named from SASBO's list").isEqualTo("CODE 18");

        Map<Integer, List<String>> recordedBy = read.suspensionCodes().stream()
                .collect(Collectors.toMap(AcbCodesDto.SuspensionCode::code, AcbCodesDto.SuspensionCode::bankCodes));
        assertThat(recordedBy.keySet()).containsExactlyInAnyOrderElementsOf(MigrationSeed.SUSPENSION_CODES);
        assertThat(recordedBy.get(30)).as("SASBO's 30 is the bank's 30, 31, 32 and 34 (DOD-04)")
                .containsExactly("30", "31", "32", "34");
        assertThat(recordedBy.get(18)).containsExactly("2");
        assertThat(recordedBy.get(16)).containsExactly("4");
        assertThat(recordedBy.get(13)).containsExactly("6");
        assertThat(recordedBy.get(15)).containsExactly("15");
        assertThat(recordedBy.get(19)).containsExactly("26");
        assertThat(recordedBy.get(11)).as("not on payroll: no bank code records it").isEmpty();
    }

    @Test
    @DisplayName("the codes as data: the day SASBO says code 6 may be re-debited it is, and of two administrators "
            + "changing it the second is told")
    void theCodesAreData() {
        AcbRunFixture.insert(jdbc, 15, 2).assertSize();
        int m = AcbRunFixture.FIRST_MEMBER_ID;
        long september = insertRun(SEPTEMBER, "SUBMITTED", FROZEN, "system");
        long line = insertLine(september, m, null, "PENDING", null, RATE);
        AcbBankCodeDto six = codes.codes().bankCodes().stream().filter(c -> c.bankCode().equals("6")).findFirst()
                .orElseThrow();
        assertThat(six.reDebit()).isEqualTo(ReDebit.UNKNOWN);
        assertThat(six.provisional()).isTrue();
        assertThat(six.suspensionCode()).isEqualTo(13);

        AcbBankCodeDto changed = codes.change("6", new CodeChangeRequest(six.version(), ReDebit.YES, 13, "Debit returned",
                false, "SASBO, the clarifications meeting"), "cfo");
        assertThat(changed.reDebit()).isEqualTo(ReDebit.YES);
        assertThat(changed.version()).isEqualTo(six.version() + 1);
        assertThat(changed.updatedBy()).isEqualTo("cfo");
        assertThat(refusal(() -> codes.change("6", new CodeChangeRequest(six.version(), ReDebit.NO, 13, "Debit returned",
                false, "a second administrator"), "cfo")).code()).isEqualTo(AcbRefusalCode.CODE_VERSION_CONFLICT);

        assertThat(returns.recordUnpaid(september, line, new RecordUnpaidRequest("6", "2026-09-28",
                UnpaidDecision.RE_PRESENT), "administrator").decision()).isEqualTo(UnpaidDecision.RE_PRESENT);

        AcbBankCodeDto added = codes.add(new CodeAddRequest("8", UnpaidReason.OTHER, "Other", ReDebit.UNKNOWN, null,
                "the bank's full list"), "cfo");
        assertThat(added.provisional()).isTrue();
        assertThat(refusal(() -> codes.add(new CodeAddRequest("8", UnpaidReason.OTHER, "Other", ReDebit.UNKNOWN, null,
                "again"), "cfo")).code()).isEqualTo(AcbRefusalCode.CODE_ALREADY_LISTED);
        assertThat(refusal(() -> codes.change("6", new CodeChangeRequest(changed.version(), ReDebit.YES, 77,
                "Debit returned", false, "no such code"), "cfo")).code()).isEqualTo(AcbRefusalCode.SUSPENSION_CODE_UNKNOWN);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM acb_audit_events WHERE kind IN ('CODE_CHANGED', 'CODE_ADDED')",
                Long.class)).isEqualTo(2L);
    }

    @Test
    @DisplayName("SD3-D: a change's audit row says what the rule is now -- never the administrator's note, which can "
            + "carry a name")
    void theAuditRowNeverCarriesTheNote() {
        AcbBankCodeDto fifteen = codes.codes().bankCodes().stream().filter(c -> c.bankCode().equals("15")).findFirst()
                .orElseThrow();
        codes.change("15", new CodeChangeRequest(fifteen.version(), ReDebit.NO, 15, "Account closed", false,
                "Told by an invented person on the telephone"), "cfo");

        String reason = jdbc.queryForObject("SELECT reason FROM acb_audit_events WHERE kind = 'CODE_CHANGED'",
                String.class);
        assertThat(reason).contains("bank code 15").contains("re-debit NO").contains("SASBO code 15").contains("settled");
        assertThat(reason).doesNotContain("invented person").doesNotContain("telephone");
        assertThat(jdbc.queryForObject("SELECT note FROM acb_unpaid_codes WHERE code = '15'", String.class))
                .as("the note is kept on the row, for the settings screen").contains("telephone");
    }

    @Test
    @DisplayName("a code not in the list is CODE_NOT_FOUND, and nothing is written")
    void aCodeNotListedIsRefused() {
        assertThat(refusal(() -> codes.change("77", new CodeChangeRequest(0, ReDebit.YES, null, "Other", false,
                "a code nobody listed"), "cfo")).code()).isEqualTo(AcbRefusalCode.CODE_NOT_FOUND);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM acb_unpaid_codes WHERE code = '77'", Long.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM acb_audit_events", Long.class)).isZero();
    }

    @Test
    @DisplayName("two administrators adding one code at once: one code, one CODE_ALREADY_LISTED -- never a 500, and the "
            + "loser's audit row rolled back with its insert")
    void twoAddsRace() throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        List<Future<Object>> outcomes = new ArrayList<>();
        try {
            for (String who : List.of("admin-a", "admin-b")) {
                outcomes.add(pool.submit(() -> {
                    start.await();
                    try {
                        return codes.add(new CodeAddRequest("8", UnpaidReason.OTHER, "Other", ReDebit.UNKNOWN, null,
                                "the bank's full list, typed by " + who), who);
                    } catch (AcbRefusal refused) {
                        return refused.code();
                    }
                }));
            }
            start.countDown();
            List<Object> results = new ArrayList<>();
            for (Future<Object> outcome : outcomes) {
                results.add(outcome.get(30, TimeUnit.SECONDS));
            }
            assertThat(results).filteredOn(AcbBankCodeDto.class::isInstance).hasSize(1);
            assertThat(results).filteredOn(AcbRefusalCode.CODE_ALREADY_LISTED::equals).hasSize(1);
        } finally {
            pool.shutdownNow();
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM acb_unpaid_codes WHERE code = '8'", Long.class))
                .isEqualTo(1L);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM acb_audit_events WHERE kind = 'CODE_ADDED'", Long.class))
                .isEqualTo(1L);
    }
}
