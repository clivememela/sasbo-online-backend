package za.co.titandynamix.sasbo_online_backend.services.impl;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** The debit day move's words, without a database: its PostgreSQL behaviour is {@link AcbDebitDayServiceImplIT}'s. */
@DisplayName("AcbDebitDayService -- the words its audit row writes")
class AcbDebitDayServiceImplTest {

    @Test
    @DisplayName("the day reads as English: the 20th, 21st, 22nd, 23rd, 27th and 31st")
    void theDayReadsAsEnglish() {
        assertThat(List.of(20, 21, 22, 23, 27, 31).stream().map(AcbDebitDayServiceImpl::ordinal).toList())
                .containsExactly("20th", "21st", "22nd", "23rd", "27th", "31st");
    }
}
