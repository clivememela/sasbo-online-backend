package za.co.titandynamix.sasbo_online_backend.acb.returns;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import za.co.titandynamix.sasbo_online_backend.enums.ReDebit;
import za.co.titandynamix.sasbo_online_backend.enums.ReDebitWithdrawal;
import za.co.titandynamix.sasbo_online_backend.enums.UnpaidDecision;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Stephne's re-debit rules of 6 October 2026, one test each, named after her answer. Each refusing rule sits beside its
 * must-pass twin one change away, so a rule that refused everything would fail here.
 */
@DisplayName("The re-debit rules (Stephne, DOD-03, DOD-04, DOD-26, DOD-27)")
class ReDebitRulesTest {

    @Test
    @DisplayName("Stephne, DOD-03: \"Code 2 means insufficient funds and allows to redebit\" -- a code that says YES is "
            + "presented again")
    void codeTwoIsPresentedAgain() {
        assertThat(ReDebitRules.mayPresentAgain(ReDebit.YES, 1)).isEqualTo(ReDebitRules.Verdict.ALLOWED);
        assertThat(ReDebitRules.mayPresentAgain(ReDebit.YES, 2)).isEqualTo(ReDebitRules.Verdict.ALLOWED);
    }

    @Test
    @DisplayName("Stephne, DOD-03: \"Code 4 - cannot redebit because it indicates a payment stopped by the member\" -- "
            + "a code that says NO is never presented again, at any count")
    void codeFourIsNeverPresentedAgain() {
        assertThat(ReDebitRules.mayPresentAgain(ReDebit.NO, 1)).isEqualTo(ReDebitRules.Verdict.CODE_FORBIDS);
        assertThat(ReDebitRules.mayPresentAgain(ReDebit.NO, 0)).isEqualTo(ReDebitRules.Verdict.CODE_FORBIDS);
        // The twin, one change away: the same count under a code that allows it.
        assertThat(ReDebitRules.mayPresentAgain(ReDebit.YES, 1)).isEqualTo(ReDebitRules.Verdict.ALLOWED);
    }

    @Test
    @DisplayName("Stephne, DOD-04: \"30/31/32/34 - All the same - ... we cannot try to redebit\" -- refused like code 4, "
            + "and the code table holds them NO (V70's rows, asserted in AcbReturnsServiceImplIT)")
    void theNoAuthorityCodesAreNeverPresentedAgain() {
        assertThat(ReDebitRules.mayPresentAgain(ReDebit.NO, 2)).isEqualTo(ReDebitRules.Verdict.CODE_FORBIDS);
    }

    @Test
    @DisplayName("Friday item 9: a code her sheet lists without a ruling (6, 15, 18, 26) -- or one nobody knows -- is "
            + "not presented again until an administrator sets it; nothing is guessed")
    void anUnknownCodeWaits() {
        assertThat(ReDebitRules.mayPresentAgain(ReDebit.UNKNOWN, 1)).isEqualTo(ReDebitRules.Verdict.CODE_UNKNOWN);
        assertThat(ReDebitRules.mayPresentAgain(null, 1)).as("no rule at all reads as unknown")
                .isEqualTo(ReDebitRules.Verdict.CODE_UNKNOWN);
    }

    @Test
    @DisplayName("Stephne, DOD-03: \"Cannot present more then 3 unpaid debits\" -- at three, even code 2 is not presented "
            + "again; at two it is")
    void neverMoreThanThree() {
        assertThat(ReDebitRules.mayPresentAgain(ReDebit.YES, 3)).isEqualTo(ReDebitRules.Verdict.LIMIT_REACHED);
        assertThat(ReDebitRules.mayPresentAgain(ReDebit.YES, 4)).isEqualTo(ReDebitRules.Verdict.LIMIT_REACHED);
        assertThat(ReDebitRules.mayPresentAgain(ReDebit.YES, 2)).as("the twin").isEqualTo(ReDebitRules.Verdict.ALLOWED);
        assertThat(UnpaidCounting.LIMIT).isEqualTo(3);
    }

    @Test
    @DisplayName("the suggestion follows the rules: present again only where allowed, suspend where not, nothing for an "
            + "unknown code")
    void theSuggestionFollowsTheRules() {
        assertThat(ReDebitRules.suggestion(ReDebit.YES, 1)).isEqualTo(UnpaidDecision.RE_PRESENT);
        assertThat(ReDebitRules.suggestion(ReDebit.YES, 3)).isEqualTo(UnpaidDecision.SUSPEND);
        assertThat(ReDebitRules.suggestion(ReDebit.NO, 1)).isEqualTo(UnpaidDecision.SUSPEND);
        assertThat(ReDebitRules.suggestion(ReDebit.UNKNOWN, 1)).isNull();
    }

    @Test
    @DisplayName("Stephne, DOD-26: \"Only the current month\" -- a member made active again leaves the re-debits of "
            + "unpaids on or before that day; one after it stands")
    void aReactivatedMemberPaysOnlyTheCurrentMonth() {
        LocalDate reinstated = LocalDate.of(2026, 9, 30);
        assertThat(ReDebitRules.withdrawal(LocalDate.of(2026, 9, 25), null, reinstated))
                .contains(ReDebitWithdrawal.REACTIVATED);
        assertThat(ReDebitRules.withdrawal(reinstated, null, reinstated)).contains(ReDebitWithdrawal.REACTIVATED);
        assertThat(ReDebitRules.withdrawal(LocalDate.of(2026, 10, 27), null, reinstated))
                .as("an unpaid after the return is the member's own, and stands").isEmpty();
    }

    @Test
    @DisplayName("Stephne, DOD-27: \"This member will now have a new joined date\" -- re-debits from before the new join "
            + "date are withdrawn")
    void aRejoinedMemberLeavesTheOldReDebits() {
        LocalDate joined = LocalDate.of(2026, 10, 5);
        assertThat(ReDebitRules.withdrawal(LocalDate.of(2026, 9, 25), joined, null)).contains(ReDebitWithdrawal.REJOINED);
        assertThat(ReDebitRules.withdrawal(LocalDate.of(2026, 10, 27), joined, null)).isEmpty();
        assertThat(ReDebitRules.withdrawal(LocalDate.of(2026, 9, 25), LocalDate.of(2010, 3, 1), null))
                .as("a member who joined long ago keeps every re-debit").isEmpty();
    }
}
