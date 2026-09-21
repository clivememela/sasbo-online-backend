package za.co.titandynamix.sasbo_online_backend.services.impl;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import za.co.titandynamix.sasbo_online_backend.dto.acb.AcbOpeningPositionDto;
import za.co.titandynamix.sasbo_online_backend.exception.AcbRefusal;
import za.co.titandynamix.sasbo_online_backend.exception.AcbRefusalCode;
import za.co.titandynamix.sasbo_online_backend.fixtures.AcbRunFixture;
import za.co.titandynamix.sasbo_online_backend.repository.AcbOpeningPositionRepository;
import za.co.titandynamix.sasbo_online_backend.services.AcbOpeningPositionService;
import za.co.titandynamix.sasbo_online_backend.support.AbstractAcbIT;

import java.io.IOException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static za.co.titandynamix.sasbo_online_backend.acb.opening.OpeningPositionSheetTest.row;
import static za.co.titandynamix.sasbo_online_backend.acb.opening.OpeningPositionSheetTest.workbook;
import static za.co.titandynamix.sasbo_online_backend.fixtures.AcbRunFixture.membershipNumber;

/**
 * 🔒 R3-12, D24: the opening arrears position, captured by hand once -- every row validated against
 * the members on record, all or nothing, stored with who, when and its source, and fixed once the
 * first returns are in. Synthetic members only: the run fixture's.
 */
@DisplayName("The opening arrears position (03.0, D24)")
class AcbOpeningPositionServiceImplIT extends AbstractAcbIT {

    private static final int FIRST = AcbRunFixture.FIRST_MEMBER_ID;

    /** The fixture's first payroll member: 30 debitable members, then four excluded on debit order. */
    private static final int PAYROLL = FIRST + 30 + 4;

    @Autowired
    private AcbOpeningPositionService opening;

    @Autowired
    private AcbOpeningPositionRepository positions;

    @Test
    @DisplayName("🔒 R3-12: an unknown number, a member not on debit order, a count of three and a future month are refused BY ROW -- and nothing is stored")
    void badRowsAreNamedAndNothingIsStored() throws IOException {
        AcbRunFixture.insert(jdbc, 15, 2).assertSize();
        byte[] sheet = workbook(s -> {
            row(s, 1, membershipNumber(FIRST), 1, "2026-06");
            row(s, 2, "F-999999", 1, "2026-06");
            row(s, 3, membershipNumber(PAYROLL), 1, "2026-06");
            row(s, 4, membershipNumber(FIRST + 1), 3, "2026-06");
            row(s, 5, membershipNumber(FIRST + 2), 1, "2026-10");
        });
        AcbRefusal refused = catchThrowableOfType(AcbRefusal.class, () -> opening.capture(sheet, "admin-a"));
        assertThat(refused.code()).isEqualTo(AcbRefusalCode.OPENING_POSITION_INVALID);
        assertThat(refused.details()).containsEntry("totalProblems", "4");
        assertThat(refused.details().get("rows"))
                .contains("row 3 membershipNumber").contains("row 4 membershipNumber")
                .contains("row 5 unpaidsStanding").contains("row 6 lastUnpaidMonth")
                .as("a problem never echoes a cell").doesNotContain("F-999999");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM acb_opening_position", Long.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM acb_audit_events", Long.class)).isZero();
    }

    @Test
    @DisplayName("a good sheet is stored with who, when and its source, audited once without a member's number -- and a second capture replaces it whole")
    void aGoodSheetIsStoredAndReplaced() throws IOException {
        AcbRunFixture.insert(jdbc, 15, 2).assertSize();
        AcbOpeningPositionDto captured = opening.capture(workbook(s -> {
            row(s, 1, membershipNumber(FIRST), 2, "2026-06");
            row(s, 2, membershipNumber(FIRST + 1), 1, "2026-05");
        }), "admin-a");
        assertThat(captured.members()).isEqualTo(2);
        assertThat(captured.enteredBy()).isEqualTo("admin-a");
        assertThat(captured.enteredAt()).startsWith("2026-09-18T10:00");
        assertThat(captured.locked()).isFalse();
        assertThat(jdbc.queryForList("SELECT DISTINCT source FROM acb_opening_position", String.class))
                .containsExactly(AcbOpeningPositionServiceImpl.SOURCE);
        assertThat(jdbc.queryForObject("SELECT reason FROM acb_audit_events WHERE kind = 'OPENING_POSITION_CAPTURED'",
                String.class)).contains("2 member(s)").contains("2026-05").contains("2026-06").doesNotContain("F-");

        AcbOpeningPositionDto replaced = opening.capture(workbook(s -> row(s, 1, membershipNumber(FIRST + 3), 1, "2026-07")),
                "admin-b");
        assertThat(replaced.members()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT membership_number FROM acb_opening_position", String.class))
                .isEqualTo(membershipNumber(FIRST + 3));
        // The repository's own two: the newest row is the replacement's, and the delete says how many it took.
        assertThat(positions.findFirstByOrderByEnteredAtDescIdDesc()).get()
                .satisfies(row -> assertThat(row.getEnteredBy()).isEqualTo("admin-b"));
        assertThat(positions.deleteEverything()).isEqualTo(1);
        assertThat(positions.findFirstByOrderByEnteredAtDescIdDesc()).isEmpty();
    }

    @Test
    @DisplayName("🔒 SD3-J: a last unpaid month this system already sent a file for is refused -- the same unpaid would be counted twice")
    void aMonthInTheLedgerIsRefused() throws IOException {
        AcbRunFixture.insert(jdbc, 15, 2).assertSize();
        insertRun(LocalDate.of(2026, 7, 1), "CLOSED", FROZEN, "system");
        AcbRefusal refused = catchThrowableOfType(AcbRefusal.class,
                () -> opening.capture(workbook(s -> row(s, 1, membershipNumber(FIRST), 1, "2026-07")), "admin-a"));
        assertThat(refused.code()).isEqualTo(AcbRefusalCode.OPENING_POSITION_INVALID);
        assertThat(refused.details().get("rows")).contains("row 2 lastUnpaidMonth").contains("2026-07");
        // The must-pass twin: the month before the ledger begins is taken.
        assertThat(opening.capture(workbook(s -> row(s, 1, membershipNumber(FIRST), 1, "2026-06")), "admin-a").members())
                .isEqualTo(1);
    }

    @Test
    @DisplayName("🔒 EH2-E: once the first run's returns are in, the position is fixed -- OPENING_POSITION_LOCKED, saying since when")
    void lockedOnceTheFirstReturnsAreIn() throws IOException {
        AcbRunFixture.insert(jdbc, 15, 2).assertSize();
        long run = insertRun(LocalDate.of(2026, 8, 1), "RETURNED", FROZEN, "system");
        jdbc.update("INSERT INTO acb_audit_events (run_id, occurred_at, actor, kind, from_state, to_state, reason) "
                        + "VALUES (?, ?, 'system', 'RECORD_RETURNS', 'SUBMITTED', 'RETURNED', 'record_returns')",
                run, OffsetDateTime.of(2026, 9, 1, 8, 0, 0, 0, ZoneOffset.UTC));
        AcbRefusal refused = catchThrowableOfType(AcbRefusal.class,
                () -> opening.capture(workbook(s -> row(s, 1, membershipNumber(FIRST), 1, "2026-06")), "admin-a"));
        assertThat(refused.code()).isEqualTo(AcbRefusalCode.OPENING_POSITION_LOCKED);
        assertThat(refused.details()).containsKey("lockedSince");
        assertThat(opening.current().locked()).isTrue();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM acb_opening_position", Long.class)).isZero();
    }

    @Test
    @DisplayName("the legacy arrears balance is a cross-check: a disagreement is reported, never resolved silently")
    void aDisagreementIsReported() throws IOException {
        AcbRunFixture.insert(jdbc, 15, 2).assertSize();
        // V41's seeded rate, R174.00 from January 2026, is the flat rate the balance is checked against.
        jdbc.update("UPDATE members SET arrears_balance_numeric = 348.00 WHERE id = ?", FIRST);
        jdbc.update("UPDATE members SET arrears_balance_numeric = 174.00 WHERE id = ?", FIRST + 1);
        AcbOpeningPositionDto captured = opening.capture(workbook(s -> {
            row(s, 1, membershipNumber(FIRST), 2, "2026-06");
            row(s, 2, membershipNumber(FIRST + 1), 2, "2026-06");
        }), "admin-a");
        assertThat(captured.disagreements()).singleElement().satisfies(d -> {
            assertThat(d.membershipNumber()).isEqualTo(membershipNumber(FIRST + 1));
            assertThat(d.arrearsBalance()).isEqualTo("174.00");
        });
        assertThat(jdbc.queryForObject("SELECT unpaids_standing FROM acb_opening_position WHERE member_id = ?",
                Integer.class, FIRST + 1)).as("stored as captured -- reported, not corrected").isEqualTo(2);
    }
}
