package za.co.titandynamix.sasbo_online_backend.payroll;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import za.co.titandynamix.sasbo_online_backend.enums.PayrollDiscrepancyType;
import za.co.titandynamix.sasbo_online_backend.exception.PayrollRefusal;
import za.co.titandynamix.sasbo_online_backend.exception.PayrollRefusalCode;
import za.co.titandynamix.sasbo_online_backend.security.CallerScope;

import java.time.Clock;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** What a payroll request names, held to its limits -- each refusal a stable code and nothing of the input echoed. */
@DisplayName("Payroll requests: months, pages, filter words, the money check")
class PayrollRequestsTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-07T08:00:00Z"), ZoneId.of("Africa/Johannesburg"));

    @Test
    @DisplayName("a month is 2026-09, never after the clock's own; anything else is refused by code")
    void months() {
        assertThat(PayrollRequests.period("2026-09", CLOCK)).isEqualTo(YearMonth.of(2026, 9));
        assertThat(PayrollRequests.period("2026-10", CLOCK)).as("the month the file arrives in").isEqualTo(YearMonth.of(2026, 10));
        assertThatThrownBy(() -> PayrollRequests.period("2026-11", CLOCK)).isInstanceOfSatisfying(PayrollRefusal.class,
                r -> assertThat(r.code()).isEqualTo(PayrollRefusalCode.PERIOD_IN_FUTURE));
        assertThatThrownBy(() -> PayrollRequests.period("September", CLOCK)).isInstanceOfSatisfying(PayrollRefusal.class,
                r -> {
                    assertThat(r.code()).isEqualTo(PayrollRefusalCode.PERIOD_INVALID);
                    assertThat(r.details()).doesNotContainValue("September");
                });
        assertThat(PayrollRequests.periodFilter(" ", CLOCK)).isNull();
    }

    @Test
    @DisplayName("a page starts at 0 and holds 1 to 100 rows, 20 by default")
    void pages() {
        assertThat(PayrollRequests.page(null, null)).containsExactly(0, 20);
        assertThat(PayrollRequests.page(2, 100)).containsExactly(2, 100);
        assertThatThrownBy(() -> PayrollRequests.page(0, 101)).isInstanceOfSatisfying(PayrollRefusal.class,
                r -> assertThat(r.code()).isEqualTo(PayrollRefusalCode.PAGE_INVALID));
        assertThatThrownBy(() -> PayrollRequests.page(-1, 20)).isInstanceOfSatisfying(PayrollRefusal.class,
                r -> assertThat(r.code()).isEqualTo(PayrollRefusalCode.PAGE_INVALID));
    }

    @Test
    @DisplayName("a filter word is one of the list's, in any case; another is FILTER_UNKNOWN naming the ones taken")
    void filterWords() {
        assertThat(PayrollRequests.word("not_a_member", PayrollDiscrepancyType.class, "type"))
                .isEqualTo(PayrollDiscrepancyType.NOT_A_MEMBER);
        assertThat(PayrollRequests.word(null, PayrollDiscrepancyType.class, "type")).isNull();
        assertThatThrownBy(() -> PayrollRequests.word("BOGUS", PayrollDiscrepancyType.class, "type"))
                .isInstanceOfSatisfying(PayrollRefusal.class, r -> {
                    assertThat(r.code()).isEqualTo(PayrollRefusalCode.FILTER_UNKNOWN);
                    assertThat(r.details().get("accepted")).contains("MISSING_FROM_FILE");
                });
    }

    @Test
    @DisplayName("Stephne, PAY-27, PAY-28: the payment expected is the file's total less the employer's fee; Absa keeps 3%")
    void theMoneyCheck() {
        assertThat(PayrollMoney.adminFee(new java.math.BigDecimal("4168.65"), new java.math.BigDecimal("3.00")))
                .isEqualByComparingTo("125.06");
        assertThat(PayrollMoney.expectedPayment(new java.math.BigDecimal("4168.65"), new java.math.BigDecimal("3.00")))
                .as("the fixtures' Absa sample: 4,168.65 less 3%").isEqualByComparingTo("4043.59");
        assertThat(PayrollMoney.status(true, new java.math.BigDecimal("4043.59"), new java.math.BigDecimal("4043.59")).name())
                .isEqualTo("EXACT");
        assertThat(PayrollMoney.status(true, new java.math.BigDecimal("3869.59"), new java.math.BigDecimal("4043.59")).name())
                .isEqualTo("SHORT");
        assertThat(PayrollMoney.status(false, null, new java.math.BigDecimal("4043.59")).name()).isEqualTo("NOT_RECORDED");
    }

    @Test
    @DisplayName("payroll is head office's: a caller limited to a region or an area is HEAD_OFFICE_ONLY")
    void headOfficeOnly() {
        assertThat(CallerScope.current().isAll()).as("no caller on this thread is everything").isTrue();
        PayrollAccess.headOffice();
        var authentication = new org.springframework.security.authentication.TestingAuthenticationToken(
                new RegionalUser(), null);
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(authentication);
        try {
            assertThatThrownBy(PayrollAccess::headOffice).isInstanceOfSatisfying(PayrollRefusal.class,
                    r -> assertThat(r.code()).isEqualTo(PayrollRefusalCode.HEAD_OFFICE_ONLY));
        } finally {
            org.springframework.security.core.context.SecurityContextHolder.clearContext();
        }
    }

    /** A signed-in Regional Secretary, as the sign-in filter would put one on the thread. */
    private static final class RegionalUser implements za.co.titandynamix.sasbo_online_backend.security.ScopedPrincipal {
        @Override
        public String username() {
            return "regional";
        }

        @Override
        public String displayName() {
            return "A Regional Secretary";
        }

        @Override
        public CallerScope scope() {
            return CallerScope.regions(Set.of("DBN"));
        }
    }
}
