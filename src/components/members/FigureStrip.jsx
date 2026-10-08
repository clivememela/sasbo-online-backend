import { cn } from '../../lib/utils';
import { formatInt } from '../../lib/format';

/**
 * THE REGISTER'S TOTALS, AS A RULED FIGURE STRIP.
 *
 * 🗣️ [`directionContract.md`](../../../../planning/branches/design/directionContract.md) →
 * *Density and layout*, in its own words:
 *
 *     "The register's totals are a **ruled figure strip** (ledger line, not tiles)"
 *
 * 🔴 **AND THE CONTRACT'S REFUSALS NAME THE ALTERNATIVE EXPLICITLY**: *"Hero-metric tiles,
 * dashboards of same-size cards, nested cards."* So this is not "a stats row, styled" — the
 * shape is the decision. A ledger rules a line above and below its figures and separates
 * them with hairlines; it does not put each number in its own box, because a box says *"this
 * is a thing"* and a ruled column says *"this is a reading of one thing"*.
 *
 * 🔑 **TABULAR NUMERALS, AND THE FONT WAS CHOSEN FOR THEM.** The contract's type section:
 * *"One family; the numerals do the identity work (tabular in the register, the figure strip
 * and the calendar)."* Proportional digits make 1 902 and 1 118 different widths, so the
 * eye cannot compare two figures without reading both.
 *
 * ⚠️ **SPACE-SEPARATED THOUSANDS, NOT COMMAS** — `2 418`. South African convention, and it
 * is what the mock renders.
 *
 * 📐 **THE COLUMNS ARE THE STYLESHEET'S** (index.css, "The figure strip follows the width it is
 * given"): equal columns, three when they do not fit the strip, two on a phone. They answer
 * to the strip's own width, which is why the component is a wrapper and a list -- a container
 * query is asked of an ancestor, never of the element it lays out.
 *
 * 🗣️ **"NEW THIS MONTH" -- SASBO, 18 Sep 2026**: "dashboard to capture total number of new debit
 * orders captured for the month", and "new members captured". ONE figure, not two, and the reason
 * is the grid: six figures divide evenly into the stylesheet's three and two columns, where seven
 * leave one figure alone on a row of the owner's own 1280 screen with a record docked (ruling ③
 * already refused a strip that looks squashed). So the month's joiners are the figure, and how
 * many of them pay by debit order is its second line -- a reading of one thing and its make-up,
 * which the ledger allows, rather than another box beside it. Quiet, like "Updated today": both
 * are readings of this month's traffic, not of what the register holds.
 *
 * 🔑 THE FIGURES ARE DATA (month end `02.4`, BR9). The register and Month end's figures are two
 * instruments -- the register counts the list it holds, the figures read the server's summary
 * (`MonthEndFigures.jsx`; on Home until 5 Oct) -- and ONE strip draws both: `figures` is the list of `{ label, value, dot?, quiet?,
 * breakdown?, definition? }` it rules, in order. The register's props (`total`, `active`, ...) are
 * still accepted and become that list through `registerFigures`, so its call site and its tests read
 * exactly as they did. `definition` is a figure's second line saying what it counted (ME-10) -- shown,
 * like `breakdown`, only once the figure was read -- and `label` names the strip for a screen reader.
 */

/**
 * `2418` → `2 418`. en-ZA groups thousands with a no-break space (U+00A0), so a figure can never wrap mid-number --
 * which is also why the comma replacement that stood here never matched anything. `formatInt` is that same call, on a
 * formatter built once.
 */
const groups = (n) => (typeof n === 'number' ? formatInt(n) : n);

/** The register's figures as data: the props it has always passed, as the list the strip rules. */
export const registerFigures = ({ total, active, pending, suspended, newThisMonth, newDebitOrders, updatedToday }) => [
  { label: 'Total', value: total },
  { label: 'Active', value: active, dot: 'bg-status-active-dot' },
  { label: 'Pending', value: pending, dot: 'bg-status-pending-dot' },
  { label: 'Suspended', value: suspended, dot: 'bg-status-suspended-dot' },
  ...(newThisMonth === undefined
    ? []
    : [{
      label: 'New this month',
      value: newThisMonth,
      quiet: true,
      breakdown: newDebitOrders === undefined ? undefined : `${groups(newDebitOrders)} by debit order`,
    }]),
  ...(updatedToday === undefined
    ? []
    : [{ label: 'Updated today', value: updatedToday, quiet: true }]),
];

/**
 * @param {{
 *   figures?: Array<{ label: string, value?: number|string|null, dot?: string, quiet?: boolean,
 *     breakdown?: string, definition?: string }>,
 *   label?: string, state?: 'ready'|'loading'|'error', className?: string,
 *   total?: number, active?: number, pending?: number, suspended?: number,
 *   newThisMonth?: number, newDebitOrders?: number, updatedToday?: number,
 * }} props
 */
export default function FigureStrip({ figures, label = 'Register summary', state = 'ready', className, ...register }) {
  const shown = figures ?? registerFigures(register);

  /*
   * 🔴 K1 (`06.3`): A FIGURE NOBODY READ IS NOT A ZERO. The counts are taken from the register the
   * page holds, so while it loads -- and when it could not be reached at all -- every one of them
   * is 0, and the strip printed "Total 0 · Active 0 · Pending 0 · Suspended 0": the union with no
   * members, over the sentence saying the server had not answered. So no figure is printed until
   * one was read: the skeleton's shimmer while the register is on its way, the record's own "—"
   * when it is not coming, and each with its truth in words for a screen reader.
   *
   * 🔑 And a figure that was answered as NOTHING reads the same way: a penetration with no staff
   * complement on record is `null` from the server, and `null` is not a nought (02.3).
   *
   * ⚠️ The skeleton is sized INLINE: `.skeleton` is an unlayered rule in index.css, so utility
   * classes cannot resize it.
   */
  const reading = (figure) => {
    if (state === 'loading') {
      return (
        <>
          <span aria-hidden="true" className="skeleton" style={{ width: '3.5rem', height: '1.5rem' }} />
          <span className="sr-only">loading</span>
        </>
      );
    }
    if (state === 'error' || figure.value == null) {
      return (
        <>
          <span aria-hidden="true" className="text-ink-muted">—</span>
          <span className="sr-only">not available</span>
        </>
      );
    }
    return groups(figure.value);
  };

  return (
    <div className={cn('figure-strip', className)}>
      <dl
        aria-label={label}
        aria-busy={state === 'loading' || undefined}
        data-testid="figure-strip"
        // 🔑 THE RULES ARE THE COMPONENT. Two hairlines, top and bottom, and the figures sit
        // BETWEEN them -- a ledger line, and not a row of boxes.
        className="border-y border-line-strong"
      >
        {shown.map((figure, index) => (
          <div
            key={figure.label}
            className={cn(
              // `px-5 first:pl-0` rather than `pr-5 mr-5`: with columns of equal width the
              // hairline belongs ON the column's edge, so the rules fall at even intervals
              // across the strip. The first figure keeps the page's own gutter.
              'grid content-start gap-1 px-5 py-2.5 first:pl-0',
              // The separating hairline, on every figure but the last.
              index < shown.length - 1 && 'border-r border-line',
            )}
          >
            {/* A label longer than its column takes a second line rather than crossing the hairline beside it: Month
                end's six share a desk's width with the rail, and "Members on record" is wider than a sixth.
                Every label sits on its value (`self-end`), so the strip's rows read level (index.css, the subgrid). */}
            <dt className="flex items-center gap-[7px] self-end text-[11.5px] font-semibold uppercase tracking-[0.08em] text-ink-muted">
              {figure.dot && <i className={cn('size-[7px] rounded-full', figure.dot)} aria-hidden="true" />}
              {figure.label}
            </dt>
            <dd
              className={cn(
                'text-2xl leading-none tracking-[-0.01em] tabular-nums',
                figure.quiet ? 'font-medium text-ink-muted' : 'font-semibold text-ink',
              )}
            >
              {reading(figure)}
            </dd>
            {/* The figure's make-up, only once the figure itself was read: K1's rule holds for the
                second line too, so a register that is loading or unreachable shows no "0 by …". */}
            {figure.breakdown && state === 'ready' && (
              <dd className="whitespace-nowrap text-xs tabular-nums text-ink-muted">{figure.breakdown}</dd>
            )}
            {/* What the figure counted (ME-10) -- a sentence, so it wraps inside its column. */}
            {figure.definition && state === 'ready' && <dd className="text-xs text-ink-muted">{figure.definition}</dd>}
          </div>
        ))}
      </dl>
    </div>
  );
}
