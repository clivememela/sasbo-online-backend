package za.co.titandynamix.sasbo_online_backend.utils;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import za.co.titandynamix.sasbo_online_backend.exception.MonthEndRefusal;
import za.co.titandynamix.sasbo_online_backend.exception.MonthEndRefusalCode;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The one place a month becomes a range (rule M, PA-9): South Africa's month, from the one injected clock.
 */
@DisplayName("MonthRange -- the month, in SASBO's zone")
class MonthRangeTest {

    private static Clock at(String instant) {
        return Clock.fixed(Instant.parse(instant), ZoneOffset.UTC);
    }

    @Test
    @DisplayName("🔒 MV-2: both sides of midnight on the first and on the last day -- in Johannesburg, not in UTC")
    void bothSidesOfMidnight() {
        MonthRange september = MonthRange.of(YearMonth.of(2026, 9));

        assertThat(september.start()).isEqualTo(Instant.parse("2026-08-31T22:00:00Z"));
        assertThat(september.next()).isEqualTo(Instant.parse("2026-09-30T22:00:00Z"));
        assertThat(september.contains(Instant.parse("2026-08-31T21:59:59Z"))).as("23:59:59 on 31 August").isFalse();
        assertThat(september.contains(Instant.parse("2026-08-31T22:00:00Z"))).as("00:00 on 1 September").isTrue();
        assertThat(september.contains(Instant.parse("2026-09-30T21:59:59Z"))).as("23:59:59 on 30 September").isTrue();
        assertThat(september.contains(Instant.parse("2026-09-30T22:00:00Z"))).as("00:00 on 1 October").isFalse();
        assertThat(september.contains(LocalDate.of(2026, 9, 1))).isTrue();
        assertThat(september.contains(LocalDate.of(2026, 10, 1))).isFalse();
    }

    @Test
    @DisplayName("🔒 MD55, MV-51: at 23:30 UTC on the last day, the server's month is already the next one")
    void theServersMonthIsSouthAfricas() {
        assertThat(MonthRange.current(at("2026-09-30T23:30:00Z"))).isEqualTo(YearMonth.of(2026, 10));
        assertThat(MonthRange.asked(null, at("2026-09-30T23:30:00Z"))).isEqualTo(YearMonth.of(2026, 10));
        assertThat(MonthRange.asked("  ", at("2026-09-30T21:30:00Z"))).isEqualTo(YearMonth.of(2026, 9));
    }

    @Test
    @DisplayName("MV-38: the clock at 1 January, at 29 February and on the last evening of a month")
    void theCalendarsEdges() {
        assertThat(MonthRange.current(at("2026-12-31T22:00:00Z"))).isEqualTo(YearMonth.of(2027, 1));
        MonthRange january = MonthRange.of(YearMonth.of(2027, 1));
        assertThat(january.firstDayOfYear()).isEqualTo(LocalDate.of(2027, 1, 1));
        assertThat(MonthRange.current(at("2028-02-29T10:00:00Z"))).isEqualTo(YearMonth.of(2028, 2));
        assertThat(MonthRange.of(YearMonth.of(2028, 2)).firstDayOfNext()).isEqualTo(LocalDate.of(2028, 3, 1));
        assertThat(MonthRange.current(at("2026-10-31T21:59:00Z"))).isEqualTo(YearMonth.of(2026, 10));
    }

    @Test
    @DisplayName("ME-14: a month that cannot be read is SUMMARY_MONTH_INVALID, naming the field and giving nothing back")
    void aMonthThatCannotBeRead() {
        String punctuation = "!@#$%^&*()_+-={}[]|:;<>,.?/~`!@#$%^&*()";
        for (String unreadable : new String[]{"2026-9", "September", "2026-13", punctuation}) {
            assertThatThrownBy(() -> MonthRange.asked(unreadable, at("2026-09-15T08:00:00Z")))
                    .isInstanceOfSatisfying(MonthEndRefusal.class, refusal -> {
                        assertThat(refusal.code()).isEqualTo(MonthEndRefusalCode.SUMMARY_MONTH_INVALID);
                        assertThat(refusal.details()).containsEntry("field", "month").hasSize(1);
                        assertThat(refusal.getMessage()).doesNotContain(unreadable);
                    });
        }
    }

    @Test
    @DisplayName("a month after the server's own is SUMMARY_MONTH_IN_FUTURE; the server's own is not")
    void aMonthThatHasNotHappened() {
        assertThatThrownBy(() -> MonthRange.asked("2026-10", at("2026-09-15T08:00:00Z")))
                .isInstanceOfSatisfying(MonthEndRefusal.class,
                        refusal -> assertThat(refusal.code()).isEqualTo(MonthEndRefusalCode.SUMMARY_MONTH_IN_FUTURE));
        assertThat(MonthRange.asked("2026-09", at("2026-09-15T08:00:00Z"))).isEqualTo(YearMonth.of(2026, 9));
        assertThat(MonthRange.asked("2019-01", at("2026-09-15T08:00:00Z"))).isEqualTo(YearMonth.of(2019, 1));
    }
}
