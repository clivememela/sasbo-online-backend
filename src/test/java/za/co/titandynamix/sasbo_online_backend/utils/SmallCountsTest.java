package za.co.titandynamix.sasbo_online_backend.utils;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.RepetitionInfo;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static za.co.titandynamix.sasbo_online_backend.utils.SmallCounts.State.FEWER_THAN_FIVE;
import static za.co.titandynamix.sasbo_online_backend.utils.SmallCounts.State.SHOWN;
import static za.co.titandynamix.sasbo_online_backend.utils.SmallCounts.State.WITHHELD;

/**
 * Rule C (MD27, MD37): a membership from one to four at a named employer or table code carries no number, and no
 * answer gives its own row back.
 */
@DisplayName("SmallCounts -- rule C, for one sum")
class SmallCountsTest {

    private static boolean[] all(int n) {
        boolean[] guarded = new boolean[n];
        java.util.Arrays.fill(guarded, true);
        return guarded;
    }

    @Test
    @DisplayName("🔒 MV-18: a row of four at a named employer carries no number")
    void aRowOfFour() {
        assertThat(SmallCounts.settle(new long[]{4, 40, 50}, all(3), 94)[0]).isEqualTo(FEWER_THAN_FIVE);
    }

    @Test
    @DisplayName("🔒 MV-19: one such row, whose total would give it back -- the smallest row of five or more is withheld with it")
    void theTotalWouldGiveItBack() {
        SmallCounts.State[] states = SmallCounts.settle(new long[]{3, 11, 40}, all(3), 54);

        assertThat(states).containsExactly(FEWER_THAN_FIVE, WITHHELD, SHOWN);
    }

    @Test
    @DisplayName("must-pass: two rows of fewer than five, the total leaving each four possible values -- nothing more is withheld")
    void twoSmallRowsGiveNothingBack() {
        SmallCounts.State[] states = SmallCounts.settle(new long[]{2, 3, 40}, all(3), 45);

        assertThat(states).containsExactly(FEWER_THAN_FIVE, FEWER_THAN_FIVE, SHOWN);
    }

    @Test
    @DisplayName("a row that names no employer -- no workplace on record -- is not guarded, but is a part of the sum")
    void anUnguardedRowIsAPart() {
        SmallCounts.State[] states = SmallCounts.settle(new long[]{3, 2, 40}, new boolean[]{true, false, true}, 45);

        assertThat(states[1]).isEqualTo(SHOWN);
        assertThat(states[0]).isEqualTo(FEWER_THAN_FIVE);
        assertThat(states[2]).as("the three can be found from the total and the two printed").isEqualTo(WITHHELD);
    }

    @Test
    @DisplayName("nobody at a row is not a row of fewer than five, and the total is never withheld")
    void noneIsNotFewer() {
        assertThat(SmallCounts.settle(new long[]{0, 9}, all(2), 9)).containsExactly(SHOWN, SHOWN);
    }

    @RepeatedTest(20)
    @DisplayName("the property: after settling, no row of fewer than five is narrowed below three of its four values")
    void neverGivenBack(RepetitionInfo repetition) {
        // A seed of its own for each repetition, fixed: a property test that draws from the clock is a flake by design.
        Random random = new Random(20_260_929L + repetition.getCurrentRepetition());
        int n = 2 + random.nextInt(8);
        long[] counts = new long[n];
        boolean[] guarded = new boolean[n];
        long total = 0;
        for (int i = 0; i < n; i++) {
            counts[i] = random.nextInt(4) == 0 ? 1 + random.nextInt(4) : random.nextInt(30);
            guarded[i] = random.nextInt(5) != 0;
            total += counts[i];
        }
        SmallCounts.State[] states = SmallCounts.settle(counts, guarded, total);
        boolean somethingLeftToWithhold = false;
        for (int i = 0; i < n; i++) {
            somethingLeftToWithhold |= states[i] == SHOWN && counts[i] >= 1;
        }
        for (int i = 0; i < n; i++) {
            if (states[i] == FEWER_THAN_FIVE && somethingLeftToWithhold) {
                assertThat(SmallCounts.values(i, counts, states, total)).isGreaterThanOrEqualTo(SmallCounts.KEEP);
            }
        }
    }

    // ------------------------------------------------------------------ MD27: a courtesy until sign-in, and no longer

    @Test
    @DisplayName("🔒 MD27: a caller signed in with MONTH_END_VIEW is answered every figure exactly")
    void notForACallerWhoSeesTheMonthsFigures() {
        try {
            SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                    "invented.administrator", null, AuthorityUtils.createAuthorityList("MEMBERS_VIEW", "MONTH_END_VIEW")));

            assertThat(SmallCounts.appliesToTheCaller()).isFalse();
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    @DisplayName("🔒 MD27: anyone else keeps it -- nobody, the anonymous, a caller signed in without MONTH_END_VIEW, a job")
    void forEveryoneElse() {
        try {
            assertThat(SmallCounts.appliesToTheCaller()).as("no caller: a job's thread, or the link").isTrue();

            SecurityContextHolder.getContext().setAuthentication(new AnonymousAuthenticationToken("key", "anonymous",
                    AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS")));
            assertThat(SmallCounts.appliesToTheCaller()).as("the anonymous").isTrue();

            SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                    "invented.organiser", null, AuthorityUtils.createAuthorityList("MEMBERS_VIEW", "MEMBERS_CAPTURE")));
            assertThat(SmallCounts.appliesToTheCaller()).as("signed in, without MONTH_END_VIEW").isTrue();

            SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.unauthenticated(
                    "invented.administrator", null));
            assertThat(SmallCounts.appliesToTheCaller()).as("not authenticated, whatever it claims").isTrue();
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    @DisplayName("the permission that ends it is a name from security.Permission")
    void thePermissionIsOneOfTheCatalogues() {
        assertThat(SmallCounts.EXACT_FIGURES).isEqualTo(
                za.co.titandynamix.sasbo_online_backend.security.Permission.MONTH_END_VIEW.name());
    }
}
