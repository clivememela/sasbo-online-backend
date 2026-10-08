import { cn } from '../../lib/utils';

/**
 * A member's status, as a chip. `04.2`, and since 2026-09-10 card.html's `.status`:
 * small capitals, a coloured dot, a 24px pill.
 *
 * ♻️ EXTRACTED ON THE SECOND CONSUMER, NOT SPECULATIVELY. `04.2` wrote this twice in one
 * sitting — `StatusChip` in `Members.jsx`, `StatusBadge` in `Recruiters.jsx` — each with
 * its own copy of the same five-value map. Two copies of a colour table is two places for
 * the day someone adds a sixth status, and only one of them gets it.
 *
 * 🔴 WHAT BOTH ORIGINALS GOT WRONG, IN DIFFERENT AMOUNTS. The vocabulary has five values
 * and the token layer carries a text, a fill and a dot for each. `Members.jsx` mapped ONE
 * (`status === 'Active' ? green : gray`), so Pending, Suspended, Resigned and Retired all
 * rendered identically — the register drew exactly one distinction and it was not one the
 * design system makes.
 *
 * ⚠️ AN UNKNOWN STATUS IS NOT STYLED AS A KNOWN ONE. It falls back to the neutral surface
 * and says "Unknown", because a status the vocabulary does not contain is a fact about the
 * data, and colouring it like "Retired" would hide that.
 */
const STATUS_TOKENS = {
  Active: { chip: 'bg-status-active-bg text-status-active', dot: 'bg-status-active-dot' },
  Pending: { chip: 'bg-status-pending-bg text-status-pending', dot: 'bg-status-pending-dot' },
  Suspended: { chip: 'bg-status-suspended-bg text-status-suspended', dot: 'bg-status-suspended-dot' },
  Resigned: { chip: 'bg-status-resigned-bg text-status-resigned', dot: 'bg-status-resigned-dot' },
  Retired: { chip: 'bg-status-retired-bg text-status-retired', dot: 'bg-status-retired-dot' },
  // ACB 03.5: three unpaid presentations cancel a membership (SUBS_IN_ARREARS). It is drawn as the other two
  // cancelled statuses are -- the same tokens, so the same measured contrast -- and the word says which.
  Terminated: { chip: 'bg-status-retired-bg text-status-retired', dot: 'bg-status-retired-dot' },
};

/** The statuses card.html strikes through in the register: nothing disappears, it cancels. */
export const CANCELLED_STATUSES = Object.freeze(['Resigned', 'Retired', 'Terminated']);

const BY_LOWER_CASE = new Map(
  Object.entries(STATUS_TOKENS).map(([status, classes]) => [status.toLowerCase(), classes]),
);

/**
 * 🔑 THE LOOKUP IS CASE-INSENSITIVE BECAUSE THERE ARE TWO VOCABULARIES, and the
 * vocabulary guard is what established that rather than an assumption.
 *
 * A MEMBER's status is title case (`Active`) and comes from `STATUS_OPTIONS`. A
 * SUBSCRIPTION's is upper case (`ACTIVE`) because the server declares
 * `SubscriptionsStatus {PENDING, ACTIVE, CANCELLED, INACTIVE}` with no display names, so
 * Jackson puts the enum NAME on the wire -- and `vocabulary.parity.test.js` records that
 * as ACCEPTED, not as drift. They are different lists that happen to share a field name.
 *
 * ⚠️ SO THE VALUE IS RENDERED AS IT ARRIVED, never normalised. Only the colour lookup
 * ignores case; the chip's small capitals are a text-transform, so the DOM still carries
 * the value the server sent.
 *
 * 🔴 AND AN UNKNOWN VALUE FALLS BACK TO NEUTRAL, WHICH IS THE POINT. `CANCELLED` and
 * `INACTIVE` belong to the subscription list and have no colour here, so they get the
 * quiet surface -- where the copy this replaced painted them with the SUSPENDED red.
 */
export default function StatusChip({ status, className }) {
  const tokens =
    typeof status === 'string' ? BY_LOWER_CASE.get(status.toLowerCase()) : undefined;

  return (
    <span
      className={cn(
        'inline-flex h-6 items-center gap-[7px] whitespace-nowrap rounded-full pl-2 pr-2.5',
        'text-xs font-semibold uppercase tracking-[0.08em]',
        tokens?.chip ?? 'bg-surface-2 text-ink-muted',
        className,
      )}
    >
      <i aria-hidden="true" className={cn('size-1.5 shrink-0 rounded-full', tokens?.dot ?? 'bg-ink-faint')} />
      {status || 'Unknown'}
    </span>
  );
}
