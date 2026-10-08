import logo from '../../assets/images/logo.png';
import { yearsBetween } from '../../lib/format';
import { cn } from '../../lib/utils';

/**
 * THE MEMBERSHIP CARD. The thesis of the whole design -- card.html's `.mcard`, transcribed
 * measurement for measurement.
 *
 * 🗣️ [`directionContract.md`](../../../../planning/branches/design/directionContract.md):
 * *"Every member is a card; the register is the wallet that hands you that card."* The
 * contract's palette table has a row for it — *"the metal card … component tokens, not page
 * tokens — **the one place the palette is allowed to be material**"*.
 *
 * 🔑 **IT IS A CARD, NOT A TEAL RECTANGLE**, and the difference is the surface treatments
 * that all have to be there at once:
 *
 *   ① the **gradient** across the diagonal — `metal-a → metal-b → metal-c` at 135°
 *   ② the **gloss** — a radial highlight from the top-left, as if lit from above
 *   ③ the **shade** — a radial darkening into the bottom-right corner
 *   ④ the **milled edges** — a light inset line on top, a dark one underneath
 *   ⑤ the **satin** — a faint diagonal sheen laid over the whole face
 *   ⑥ the **sweep** — a band of light that crosses the metal as the card lands
 *
 * 📐 **`aspect-ratio: 1.586` IS THE ISO/IEC 7810 ID-1 RATIO** — the shape of every bank card
 * in the member's wallet already. A card at 1.4 or 1.7 reads as *a rectangle with a name on
 * it*.
 *
 * ✍️ **MARCELLUS APPEARS EXACTLY HERE AND NOWHERE ELSE**, on the engraved name — in small
 * capitals, tracked, in gold, with the engraving's shadow. The union's own name on the card
 * is Schibsted, as the mock sets it.
 */

/** The status vocabulary, engraved rather than chipped (card.css `.status--engraved`). */
const ENGRAVED_STATUS = {
  Active: 'text-(--card-chip-ink) shadow-[inset_0_0_0_1px_var(--card-chip-ring)]',
  Pending: 'text-(--card-chip-pending) shadow-[inset_0_0_0_1px_currentColor]',
  Suspended: 'text-(--card-chip-suspended) shadow-[inset_0_0_0_1px_currentColor]',
  Resigned: 'text-(--card-chip-muted) shadow-[inset_0_0_0_1px_currentColor]',
  Retired: 'text-(--card-chip-muted) shadow-[inset_0_0_0_1px_currentColor]',
};

/** Years since the join date, in the reading voice -- card.js `tenure()`. */
function tenure(since) {
  const years = yearsBetween(since);
  if (years === null) return '';
  if (years < 1) return 'joined this year';
  return years === 1 ? '1 yr' : `${years} yrs`;
}

const LABEL = 'whitespace-nowrap text-[10.5px] font-semibold uppercase tracking-[0.14em] text-(--card-text-2)';

/**
 * @param {{
 *   name: string, membershipNumber: string, status?: string,
 *   since?: string, employer?: string,
 * }} props
 */
export default function MembershipCard({ name, membershipNumber, status = 'Active', since, employer }) {
  const years = tenure(since);
  const year = since ? String(since).slice(0, 4) : '';
  const statusKey = Object.keys(ENGRAVED_STATUS).find((key) => key.toLowerCase() === String(status).toLowerCase());

  return (
    <div
      aria-label="Membership card"
      data-testid="membership-card"
      className={cn(
        'mcard relative isolate overflow-hidden rounded-card px-[22px] py-5 text-(--card-engrave)',
        'grid grid-rows-[auto_1fr_auto] gap-2',
        // ISO ID-1. `min-h-max` so a long name can push it taller rather than clipping --
        // a member whose name does not fit is a member whose card is wrong.
        'aspect-[1.586] min-h-max',
        'max-sm:rounded-lg max-sm:px-[18px] max-sm:py-4',
      )}
      style={{
        backgroundImage: [
          'radial-gradient(120% 90% at 18% 0%, var(--card-gloss), transparent 52%)',
          'radial-gradient(90% 70% at 100% 100%, var(--card-shade), transparent 60%)',
          'linear-gradient(135deg, var(--card-metal-a) 0%, var(--card-metal-b) 48%, var(--card-metal-c) 100%)',
        ].join(', '),
        boxShadow:
          'var(--shadow-card), inset 0 1px 0 var(--card-edge-top), inset 0 -1px 0 var(--card-edge-bottom)',
        transformOrigin: '50% 100%',
      }}
    >
      <div className="flex items-start gap-3">
        {/* The crest sits on milled gold foil -- the one gold surface in the design that is
            a material rather than an accent. */}
        <span className="relative grid size-11 flex-none place-items-center rounded-full" aria-hidden="true">
          <span className="absolute inset-0 rounded-full" style={{ backgroundImage: 'var(--card-foil)' }} />
          <img
            src={logo}
            width="38"
            height="38"
            alt=""
            className="relative size-[38px] rounded-full bg-(--card-engrave) object-contain p-[3px]"
          />
        </span>

        <span className="grid leading-[1.1]">
          <span className="text-[13px] font-semibold uppercase tracking-[0.12em] text-(--card-engrave)">SASBO</span>
          <small className="mt-[3px] text-[10.5px] font-semibold uppercase tracking-[0.14em] text-(--card-text-2)">
            The Finance Union
          </small>
        </span>

        <span
          className={cn(
            'ml-auto inline-flex h-6 items-center gap-[7px] whitespace-nowrap rounded-full bg-(--card-chip-bg) pl-2 pr-2.5',
            'text-xs font-semibold uppercase tracking-[0.08em]',
            ENGRAVED_STATUS[statusKey] ?? ENGRAVED_STATUS.Active,
          )}
        >
          <i className="size-1.5 shrink-0 rounded-full bg-current" aria-hidden="true" />
          {status}
        </span>
      </div>

      {/* ✍️ THE ENGRAVING. Marcellus, gold, small capitals, and the only place either appears. */}
      <p
        className="mcard-name self-end text-balance font-engrave text-2xl uppercase leading-[1.12] tracking-[0.05em] text-(--card-engrave-gold) max-sm:text-xl"
        style={{ textShadow: 'var(--engrave-shadow)' }}
      >
        {name}
      </p>

      {/* 🔴 `items-start`, NOT `items-end`. The mock holds all three labels on ONE baseline
          (y=269 in mock-light.png) with the values beneath them. Aligned to their BOTTOMS,
          a two- or three-line employer pushed its own label up and out of that line, so the
          card's foot read as three groups at three heights -- on the thesis element, with
          real data, every time an employer had a long name. The row is bottom-anchored by
          the card's own grid, so the group still sits at the foot. */}
      {/* 🔴 IT WRAPS, because the mock's data never made it. Three groups on one line is the
          mock's foot and it holds wherever there is room — but its employer is "Standard
          Bank" and this register's is "The Standard Bank of South Africa Limited". At 390 the
          two figures take 284 of the card's 354, leaving 70px for a label that is 85px wide,
          and the group ran off the card's own edge. Wrapping puts the employer on a full
          line of its own exactly when it cannot share one, and never before. */}
      <div className="mcard-foot flex flex-wrap items-start gap-x-[22px] gap-y-2.5">
        <span className="grid shrink-0 gap-[3px] leading-[1.1]">
          <small className={LABEL}>Membership no.</small>
          {/* Tabular numerals, spaced like a number pressed into metal. */}
          <b
            className="whitespace-nowrap text-md font-semibold tabular-nums tracking-[0.18em] text-(--card-engrave)"
            style={{ textShadow: 'var(--engrave-shadow)' }}
          >
            {membershipNumber || '—'}
          </b>
        </span>

        {year && (
          <span className="grid shrink-0 gap-[3px] leading-[1.1]">
            <small className={LABEL}>Member since</small>
            <b className="whitespace-nowrap text-[13px] font-medium text-(--card-engrave)">
              {year}
              {years && (
                <span className="ml-[7px] border-l border-(--card-text-2) pl-[7px] font-medium text-(--card-text-2)">
                  {years}
                </span>
              )}
            </b>
          </span>
        )}

        {employer && (
          <span className="ml-auto grid max-w-full shrink-0 gap-[3px] text-right leading-[1.15]">
            {/* 🔴 THE EMPLOYER'S OWN WIDTH DECIDES THE WRAP NOW, AND A FIXED FLOOR USED TO. A
                `min-w-[9rem]` floor needed 144px beside the two figures whatever the employer was
                called, so "Absa Bank" -- 70px of text -- took a line of its own on every docked
                card and lifted the engraved name ~40px up the thesis element (`06.3`, measured
                against the comp's one baseline). A flex item that does not shrink offers its
                CONTENT width to the line: it shares the line when that fits and takes a line
                when it does not, and `max-w-full` wraps a long name inside its own line rather
                than letting it overhang the card. */}
            <small className={LABEL}>Employer</small>
            {/* Whole, never an ellipsis: an engraved fact on the thesis element must read
                entire, and real employers run to "The Standard Bank of South Africa Limited".
                Two lines on the desk, three on the narrower phone card. */}
            <b className="line-clamp-2 text-[13px] font-medium text-(--card-engrave) [overflow-wrap:anywhere] max-sm:line-clamp-3 max-sm:text-xs">{employer}</b>
          </span>
        )}
      </div>

      {/* the satin: a faint diagonal sheen over the whole face */}
      <i
        aria-hidden="true"
        className="pointer-events-none absolute inset-0"
        style={{
          background:
            'linear-gradient(112deg, rgba(255,255,255,0.10) 0%, rgba(255,255,255,0.02) 30%, rgba(255,255,255,0) 45%, rgba(255,255,255,0.04) 70%, rgba(255,255,255,0) 100%)',
        }}
      />
      {/* the sweep: the band of light that crosses the metal as the card lands (index.css) */}
      <i
        aria-hidden="true"
        className="mcard-sweep pointer-events-none absolute -inset-y-[20%] -inset-x-[40%] opacity-0"
        style={{
          background:
            'linear-gradient(100deg, transparent 40%, rgba(255,255,255,0.16) 49%, rgba(255,255,255,0.04) 53%, transparent 62%)',
          transform: 'translateX(-70%)',
        }}
      />
    </div>
  );
}
