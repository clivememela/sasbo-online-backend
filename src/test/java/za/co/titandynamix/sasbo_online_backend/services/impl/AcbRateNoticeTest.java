package za.co.titandynamix.sasbo_online_backend.services.impl;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import za.co.titandynamix.sasbo_online_backend.entity.acb.AcbRate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 🔒 SD3-G: from December, a January with no rate dated that January says which amount it will charge
 * and since when -- a notice, never a refusal. 🧪 T2-G: today is an argument.
 */
@DisplayName("The January rate notice")
class AcbRateNoticeTest {

    private static final List<AcbRate> SINCE_2026 = List.of(rate("2026-01-01", "174.00"));

    @Test
    @DisplayName("in December, with no January row, it names the amount and the month it has been in force since")
    void december() {
        assertThat(AcbRateServiceImpl.januaryNotice(SINCE_2026, LocalDate.of(2026, 12, 1)))
                .isEqualTo("2027-01 will charge R 174.00, the rate since 2026-01. "
                        + "If January's rate changes, enter it before January is prepared.");
    }

    @Test
    @DisplayName("the must-pass twins: November says nothing, and neither does a December whose January is dated")
    void notYetOrAlreadyDone() {
        assertThat(AcbRateServiceImpl.januaryNotice(SINCE_2026, LocalDate.of(2026, 11, 30))).isNull();
        List<AcbRate> withJanuary = List.of(rate("2026-01-01", "174.00"), rate("2027-01-01", "185.00"));
        assertThat(AcbRateServiceImpl.januaryNotice(withJanuary, LocalDate.of(2026, 12, 15))).isNull();
    }

    @Test
    @DisplayName("with no rate at all before January, it says January cannot be priced")
    void noRateAtAll() {
        assertThat(AcbRateServiceImpl.januaryNotice(List.of(), LocalDate.of(2026, 12, 15)))
                .startsWith("No rate is dated for 2027-01 or before it");
    }

    private static AcbRate rate(String from, String amount) {
        AcbRate rate = new AcbRate();
        rate.setEffectiveFrom(LocalDate.parse(from));
        rate.setAmount(new BigDecimal(amount));
        rate.setSource("test");
        return rate;
    }
}
