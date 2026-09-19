package za.co.titandynamix.sasbo_online_backend.enums;

/**
 * What became of one debit line.
 *
 * <p>{@code PENDING} until the bank's returns are in; {@code PAID} or {@code UNPAID} then; and
 * {@code RE_PRESENTED} once an unpaid has been presented again in a later run, which is recorded on
 * the later line's {@code re_presents_line_id} as well.
 */
public enum AcbLineOutcome {
    PENDING,
    PAID,
    UNPAID,
    RE_PRESENTED
}
