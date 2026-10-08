import { cn } from '../../lib/utils';

/**
 * THE KEYBOARD LEGEND — a row of keycaps naming what the keys do on the surface it sits on.
 *
 * 🔴 IT USED TO LIVE INSIDE THE RECORD PANEL'S EMPTY STATE, and that was the wrong home for
 * it twice over. It described the REGISTER's keys — ↑ and ↓ move the selection, Enter opens
 * the record — while sitting in the panel; and it was only ever on screen while the panel had
 * nothing in it, so the moment the owner asked for that empty slot to collapse (2026-09-10),
 * the one place the application taught its own keyboard model went with it. It belongs beside
 * the thing it describes, which is the register's own foot.
 *
 * 📐 The keycap is `border-b-2`: a heavier bottom edge is what gives a rendered key its depth,
 * and it is the reason the design detector flags this file for a thick border on a rounded
 * element. That finding is a false positive on a `<kbd>` and is left standing rather than
 * suppressed.
 *
 * @param {{ hints: Array<[string[], string]>, className?: string }} props
 *   Each hint is a list of keycaps and the word for what they do: `[['↑', '↓'], 'move']`.
 */
export default function KeyHints({ hints, className }) {
  return (
    <p className={cn('flex flex-wrap items-center gap-1.5 text-[12.5px] text-ink-muted', className)}>
      {hints.map(([keys, what], index) => (
        <span key={what} className="flex items-center gap-1.5">
          {index > 0 && <span aria-hidden="true" className="text-ink-faint">·</span>}
          <b className="flex items-center gap-1.5 whitespace-nowrap font-medium">
            {keys.map((key) => (
              <kbd
                key={key}
                className="rounded-[5px] border border-b-2 border-line-strong bg-surface-2 px-1.5 py-[3px] font-sans text-[11px] font-medium leading-none text-ink-muted"
              >
                {key}
              </kbd>
            ))}
            {what}
          </b>
        </span>
      ))}
    </p>
  );
}
