package za.co.titandynamix.sasbo_online_backend.services.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import za.co.titandynamix.sasbo_online_backend.dto.acb.AcbBanksDto;
import za.co.titandynamix.sasbo_online_backend.dto.acb.AcbDebitDayMoveDto;
import za.co.titandynamix.sasbo_online_backend.dto.acb.DebitDayMoveRequest;
import za.co.titandynamix.sasbo_online_backend.exception.AcbRefusal;
import za.co.titandynamix.sasbo_online_backend.exception.AcbRefusalCode;
import za.co.titandynamix.sasbo_online_backend.fixtures.AcbRunFixture;
import za.co.titandynamix.sasbo_online_backend.services.AcbDebitDayService;
import za.co.titandynamix.sasbo_online_backend.support.AbstractAcbIT;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

/**
 * Moving a bank's debit-order members to a new debit day in one step, on PostgreSQL (Stephne, DOD-19: "When a bank
 * changes its debit day, move all of its members in one step? -- Yes"): three thousand invented members previewed and
 * moved in one transaction, each with a history row, the bank's own day with them and one audit row -- and asked
 * again, nothing. Members are found by the bank's name as captured, trimmed and in any case (the run's own rule).
 */
@DisplayName("AcbDebitDayService -- a bank's members to a new debit day in one step (DOD-19)")
class AcbDebitDayServiceImplIT extends AbstractAcbIT {

    /** Fixture Bank 00, the bank moved: the fixture's first. */
    private static final int BANK = AcbRunFixture.FIRST_BANK_ID;
    private static final int NEW_DAY = 27;

    /** The invented members generated at the moved bank, well clear of the fixture's ids. */
    private static final int GENERATED = 3_000;
    private static final int FIRST_GENERATED_ID = 20_000;

    /** How the bank's name is captured across the generated members: as listed, upper case, and with spaces. */
    private static final List<String> NAME_AS_CAPTURED = List.of(AcbRunFixture.bankName(0), "FIXTURE BANK 00",
            "  fixture bank 00 ");

    /** How a debit order is captured: as the member screen stores it, by the enum's own name, and untidily. */
    private static final List<String> DEBIT_ORDER_AS_CAPTURED = List.of("Debit Order", "DEBIT_ORDER", " debit order ");

    @Autowired
    private AcbDebitDayService debitDays;

    /** The bank's debit-order members as built, by their day: what a move must leave on the new day, and from where. */
    private final Map<Integer, Integer> debitOrderByDay = new TreeMap<>();
    private int debitOrderMembers;

    private static AcbRefusal refusal(Runnable call) {
        AcbRefusal refused = catchThrowableOfType(AcbRefusal.class, call::run);
        assertThat(refused).as("refused").isNotNull();
        return refused;
    }

    @BeforeEach
    void aBankWithThreeThousandDebitOrderMembers() {
        AcbRunFixture.insert(jdbc, 15, 2).assertSize();
        // The fixture's own at Fixture Bank 00 that pay by debit order: two debitable, one on the 5th, one with a
        // twelve-digit account and one suspended -- every one of them moves; its payroll member does not.
        debitOrderByDay.clear();
        jdbc.query("SELECT debit_day FROM members WHERE bank_name = ? AND payment_type = 'Debit Order'",
                rs -> {
                    debitOrderByDay.merge(rs.getInt(1), 1, Integer::sum);
                }, AcbRunFixture.bankName(0));
        List<Object[]> generated = new ArrayList<>();
        for (int i = 0; i < GENERATED; i++) {
            int id = FIRST_GENERATED_ID + i;
            int day = 20 + (i % 12);
            String status = i % 10 == 0 ? "Suspended" : "Active";
            generated.add(new Object[] {id, AcbRunFixture.membershipNumber(id), "Surname" + id, "CD", status,
                    DEBIT_ORDER_AS_CAPTURED.get(i % 3), NAME_AS_CAPTURED.get((i / 3) % 3), AcbRunFixture.account(id),
                    day, "Cheque", AcbRunFixture.CREATED, AcbRunFixture.CREATED});
            debitOrderByDay.merge(day, 1, Integer::sum);
        }
        jdbc.batchUpdate("INSERT INTO members (id, membership_number, surname, initials, status, payment_type, "
                + "bank_name, bank_account_number, debit_day, account_type, created_at, updated_at) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)", generated);
        debitOrderMembers = debitOrderByDay.values().stream().mapToInt(Integer::intValue).sum();
        assertThat(debitOrderMembers).as("the fixture's non-vacuity").isGreaterThan(GENERATED);
        assertThat(debitOrderByDay.getOrDefault(NEW_DAY, 0)).as("some are on the new day already").isPositive();
    }

    @Test
    @DisplayName("Stephne, DOD-19: the preview says how many would move and from which days -- and changes nothing")
    void thePreviewChangesNothing() {
        AcbDebitDayMoveDto preview = debitDays.preview(BANK, NEW_DAY);

        int already = debitOrderByDay.getOrDefault(NEW_DAY, 0);
        assertThat(preview.bankName()).isEqualTo(AcbRunFixture.bankName(0));
        assertThat(preview.toMove()).isEqualTo(debitOrderMembers - already);
        assertThat(preview.alreadyOnDay()).isEqualTo(already);
        assertThat(preview.moved()).as("a preview moves nobody").isNull();
        assertThat(preview.byCurrentDay()).containsEntry("5", 1).containsEntry(String.valueOf(NEW_DAY), already);
        assertThat(preview.liveRuns()).isEmpty();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM member_changes", Long.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM acb_audit_events", Long.class)).isZero();
    }

    @Test
    @DisplayName("Stephne, DOD-19: three thousand moved in one step -- the name trimmed and in any case, whatever their "
            + "status; payroll and other banks' members left as they were; a history row each, the bank's own day, "
            + "one audit row")
    void aBanksMembersMoveInOneStep() {
        Map<Integer, Integer> otherBanksBefore = daysOfMembersNotMoved();
        AcbDebitDayMoveDto preview = debitDays.preview(BANK, NEW_DAY);

        AcbDebitDayMoveDto moved = debitDays.move(BANK, new DebitDayMoveRequest(NEW_DAY, preview.toMove()),
                "administrator");

        assertThat(moved.moved()).isEqualTo(preview.toMove());
        assertThat(moved.byCurrentDay()).containsExactly(Map.entry(String.valueOf(NEW_DAY), debitOrderMembers));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM members WHERE LOWER(TRIM(bank_name)) = 'fixture bank 00' "
                + "AND LOWER(TRIM(payment_type)) IN ('debit order', 'debit_order') AND debit_day = ?", Long.class,
                NEW_DAY)).as("every debit-order member of the bank").isEqualTo((long) debitOrderMembers);
        assertThat(daysOfMembersNotMoved()).as("payroll members and other banks' are as they were")
                .isEqualTo(otherBanksBefore);

        assertThat(jdbc.queryForObject("SELECT count(*) FROM member_changes WHERE field = 'debitDay' AND new_value = ? "
                + "AND changed_by = 'administrator'", Long.class, String.valueOf(NEW_DAY)))
                .as("one history row for each member moved").isEqualTo((long) preview.toMove());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM member_changes WHERE old_value = new_value", Long.class))
                .as("nobody already on the day has a history row").isZero();
        assertThat(jdbc.queryForObject("SELECT old_value FROM member_changes WHERE member_id = ?", String.class,
                FIRST_GENERATED_ID + 1)).as("the old day, as it was").isEqualTo("21");
        assertThat(jdbc.queryForObject("SELECT updated_at FROM members WHERE id = ?", LocalDateTime.class,
                FIRST_GENERATED_ID + 1)).as("dated by the clock, in Johannesburg").isEqualTo(LocalDateTime.of(2026, 9, 18,
                10, 0));

        assertThat(jdbc.queryForObject("SELECT acb_debit_day FROM subscriptions WHERE bank_id = ? AND status = 'ACTIVE'",
                Integer.class, BANK)).as("the bank's own day").isEqualTo(NEW_DAY);
        assertThat(jdbc.queryForList("SELECT DISTINCT acb_debit_day FROM subscriptions WHERE bank_id <> ?", Integer.class,
                BANK)).as("other banks' days").containsOnly(25);
        assertThat(jdbc.queryForList("SELECT kind, actor, reason FROM acb_audit_events")).singleElement()
                .satisfies(row -> {
                    assertThat(row).containsEntry("kind", "DEBIT_DAY_MOVED").containsEntry("actor", "administrator");
                    assertThat((String) row.get("reason")).contains(AcbRunFixture.bankName(0))
                            .contains(String.valueOf(preview.toMove())).contains("27th")
                            .doesNotContain(AcbRunFixture.membershipNumber(FIRST_GENERATED_ID));
                });

        AcbBanksDto.Bank bank = debitDays.banks().banks().stream().filter(b -> b.bankId() == BANK).findFirst()
                .orElseThrow();
        assertThat(bank.defaultDay()).isEqualTo(NEW_DAY);
        assertThat(bank.debitOrderMembers()).isEqualTo(debitOrderMembers);
        assertThat(bank.byDay()).containsExactly(Map.entry(String.valueOf(NEW_DAY), debitOrderMembers));
    }

    @Test
    @DisplayName("Stephne, DOD-19: asked again, nobody is left to move -- it answers so and writes nothing")
    void askedAgainItMovesNobody() {
        AcbDebitDayMoveDto preview = debitDays.preview(BANK, NEW_DAY);
        debitDays.move(BANK, new DebitDayMoveRequest(NEW_DAY, preview.toMove()), "administrator");
        long changes = jdbc.queryForObject("SELECT count(*) FROM member_changes", Long.class);

        AcbDebitDayMoveDto again = debitDays.move(BANK, new DebitDayMoveRequest(NEW_DAY, preview.toMove()), "cfo");

        assertThat(again.moved()).isZero();
        assertThat(again.toMove()).isZero();
        assertThat(again.alreadyOnDay()).isEqualTo(debitOrderMembers);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM member_changes", Long.class)).isEqualTo(changes);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM acb_audit_events", Long.class)).isEqualTo(1L);
    }

    @Test
    @DisplayName("a member captured at the bank between the preview and the move refuses it MOVE_COUNT_CHANGED -- and "
            + "nobody is moved")
    void aMemberCapturedMeanwhileRefusesTheMove() {
        AcbDebitDayMoveDto preview = debitDays.preview(BANK, NEW_DAY);
        int newcomer = FIRST_GENERATED_ID + GENERATED;
        jdbc.update("INSERT INTO members (id, membership_number, surname, initials, status, payment_type, bank_name, "
                        + "bank_account_number, debit_day, account_type, created_at, updated_at) "
                        + "VALUES (?, ?, ?, 'EF', 'Active', 'Debit Order', ?, ?, 21, 'Cheque', ?, ?)", newcomer,
                AcbRunFixture.membershipNumber(newcomer), "Surname" + newcomer, AcbRunFixture.bankName(0),
                AcbRunFixture.account(newcomer), AcbRunFixture.CREATED, AcbRunFixture.CREATED);

        AcbRefusal refused = refusal(() -> debitDays.move(BANK, new DebitDayMoveRequest(NEW_DAY, preview.toMove()),
                "administrator"));

        assertThat(refused.code()).isEqualTo(AcbRefusalCode.MOVE_COUNT_CHANGED);
        assertThat(refused.details()).containsEntry("expected", String.valueOf(preview.toMove()))
                .containsEntry("actual", String.valueOf(preview.toMove() + 1));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM members WHERE updated_at <> ?", Long.class,
                AcbRunFixture.CREATED)).as("nobody moved: every member as the fixture built them").isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM members WHERE debit_day = ? AND LOWER(TRIM(bank_name)) = "
                + "'fixture bank 00'", Long.class, NEW_DAY)).isEqualTo((long) debitOrderByDay.get(NEW_DAY));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM member_changes", Long.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM acb_audit_events", Long.class)).isZero();
    }

    @Test
    @DisplayName("a run prepared and not yet sent keeps the days it was prepared with -- the preview names it")
    void aLiveRunIsNamed() {
        long october = insertRun(NEXT_MONTH, "PREPARED", FROZEN, "system");
        insertRun(java.time.LocalDate.of(2026, 8, 1), "CLOSED", FROZEN, "system");

        assertThat(debitDays.preview(BANK, NEW_DAY).liveRuns()).singleElement()
                .isEqualTo(new AcbDebitDayMoveDto.LiveRun(october, "2026-10", "PREPARED"));
    }

    @Test
    @DisplayName("no such bank is BANK_NOT_FOUND; a day outside the 20th to the 31st is DEBIT_DAY_INVALID -- nothing is "
            + "written")
    void refusals() {
        assertThat(refusal(() -> debitDays.preview(99_999, NEW_DAY)).code()).isEqualTo(AcbRefusalCode.BANK_NOT_FOUND);
        assertThat(refusal(() -> debitDays.preview(BANK, 5)).code()).isEqualTo(AcbRefusalCode.DEBIT_DAY_INVALID);
        assertThat(refusal(() -> debitDays.preview(BANK, null)).code()).isEqualTo(AcbRefusalCode.DEBIT_DAY_INVALID);
        assertThat(refusal(() -> debitDays.move(BANK, new DebitDayMoveRequest(32, 1), "administrator")).code())
                .isEqualTo(AcbRefusalCode.DEBIT_DAY_INVALID);
        assertThat(refusal(() -> debitDays.move(99_999, new DebitDayMoveRequest(NEW_DAY, 1), "administrator")).code())
                .isEqualTo(AcbRefusalCode.BANK_NOT_FOUND);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM member_changes", Long.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM acb_audit_events", Long.class)).isZero();
    }

    /** Every member the move must not touch -- other banks', and the moved bank's payroll members -- by id. */
    private Map<Integer, Integer> daysOfMembersNotMoved() {
        Map<Integer, Integer> days = new TreeMap<>();
        jdbc.query("SELECT id, debit_day FROM members WHERE NOT (LOWER(TRIM(bank_name)) = 'fixture bank 00' "
                + "AND LOWER(TRIM(payment_type)) IN ('debit order', 'debit_order')) ORDER BY id", rs -> {
                    days.put(rs.getInt(1), rs.getInt(2));
                });
        assertThat(days).as("the twin: members a move must leave alone exist").hasSizeGreaterThan(20);
        return days;
    }
}
