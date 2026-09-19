package za.co.titandynamix.sasbo_online_backend.enums;

/**
 * What became of one debit line.
 *
 * <p>{@code PENDING} until the bank's returns are in. An unpaid is recorded as {@code UNPAID} from
 * the bank's report; when the run's returns are taken in, every line still {@code PENDING} becomes
 * {@code PAID}, because the report lists only what came back. A late return may still turn a
 * {@code PAID} line {@code UNPAID}.
 *
 * <p>There is no "re-presented" outcome: an unpaid stays {@code UNPAID}, because that is what the
 * bank did, and its re-presentation is the later line whose {@code re_presents_line_id} points at
 * it. V42's CHECK constraint still admits {@code RE_PRESENTED} from the plan's first draft; nothing
 * writes it.
 */
public enum AcbLineOutcome {
    PENDING,
    PAID,
    UNPAID
}
