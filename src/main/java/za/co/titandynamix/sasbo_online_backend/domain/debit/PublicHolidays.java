package za.co.titandynamix.sasbo_online_backend.domain.debit;

import java.time.LocalDate;

/**
 * The source of South African public holidays, as a port rather than a table.
 *
 * <h2>Why this is an interface and not a {@code Set<LocalDate>} constant</h2>
 * A hardcoded holiday table is wrong the moment the year rolls over, and it is
 * wrong <em>silently</em> — every debit date it produces still looks like a date.
 * 2026 alone carries a one-off election holiday that no fixed table written
 * before it was proclaimed could contain.
 *
 * <p>So the calendar is injected. Today the only implementation is
 * {@link WeekendsOnly}, and that is a <strong>deliberate, documented fallback
 * rather than an oversight</strong>: it is honest about knowing nothing of
 * holidays, and {@link #coversPublicHolidays()} exists so the UI can say so on
 * screen instead of implying a precision the system does not have.
 *
 * <h2>The rule this feeds, and why it must ITERATE</h2>
 * Under the Public Holidays Act a public holiday falling on a <em>Sunday</em>
 * moves the holiday to the <em>Monday</em>. So a single step back off a
 * non-business day can land on another one, and an adjustment that steps once is
 * wrong exactly when it matters most. {@link BusinessDays} loops.
 */
public interface PublicHolidays {

    /**
     * @param date the date to test
     * @return {@code true} when this source knows {@code date} to be a public holiday
     */
    boolean isPublicHoliday(LocalDate date);

    /**
     * Whether this source actually knows about public holidays at all.
     *
     * <p>🔑 This is not decoration. When it is {@code false} the debit date shown
     * to an administrator is <em>weekend-adjusted only</em>, and a date that has
     * not been checked against the holiday calendar must not be presented as
     * though it has. The capture screen renders this distinction.
     *
     * @return {@code false} for the weekends-only fallback
     */
    boolean coversPublicHolidays();

    /**
     * The fallback: weekends are non-business days, and nothing else is known.
     *
     * <p>Correct as far as it goes, and it never claims more. Replacing it means
     * providing a maintained source — a proclamation feed or an administered
     * table — not extending this class with a list of dates.
     */
    final class WeekendsOnly implements PublicHolidays {

        @Override
        public boolean isPublicHoliday(LocalDate date) {
            return false;
        }

        @Override
        public boolean coversPublicHolidays() {
            return false;
        }
    }
}
