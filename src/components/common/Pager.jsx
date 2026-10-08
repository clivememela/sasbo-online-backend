import { ChevronLeft, ChevronRight } from 'lucide-react';

import { formatInt } from '../../lib/format';
import { cn } from '../../lib/utils';
import Button from '../form/Button';

/**
 * THE PAGER -- card.css `.pager`: the sheet's foot, on the quiet surface under a
 * hairline. "Showing 1–14 of 2 418 · Page 1 of 173" on the left, Previous and Next on
 * the right, 32px controls.
 *
 * It says what it is showing in the reading voice and with the count's own thousands
 * grouping; while the list loads it says so, and when nothing matches it says that
 * rather than "0–0 of 0".
 */
export default function Pager({
  page,
  totalPages,
  onPageChange,
  count,
  start,
  end,
  noun = 'rows',
  loading = false,
  error = false,
  hint,
  children,
  className,
}) {
  const text = loading ? (
    `Loading ${noun}…`
  ) : error ? (
    /* 🔴 K1 (`06.3`). A list that never arrived is not a list in which nothing matched. With the
       register unreachable, the count is 0 only because nothing loaded -- and this foot used to
       read "No members match" two lines under "The register could not be reached", which is
       A27's confident wrong answer surviving one component down. The mock never said it either:
       its offline foot takes no count at all. */
    `Could not load ${noun}`
  ) : count === 0 ? (
    `No ${noun} match`
  ) : (
    <>
      Showing <b className="font-semibold tabular-nums text-ink">{start}–{end}</b> of{' '}
      <b className="font-semibold tabular-nums text-ink">{formatInt(count)}</b>
      {' · '}Page {page} of {totalPages}
    </>
  );

  return (
    <footer
      className={cn(
        'no-print mt-auto flex flex-wrap items-center justify-between gap-4 border-t border-line bg-surface-2 px-5 py-3',
        className,
      )}
    >
      <p className="text-sm text-ink-muted">{text}</p>
      {/* 📐 THE MIDDLE SLOT. The foot ran count-left, buttons-right with a wide gap between
          them, and a legend about the keys that work on THIS surface is exactly what belongs
          in it. Hidden below `lg`, because a phone has no arrow keys to teach. */}
      {hint && <div className="hidden lg:block">{hint}</div>}
      <div className="flex items-center gap-2">
        {children}
        <Button
          variant="pagination"
          size="sm"
          onClick={() => onPageChange(Math.max(1, page - 1))}
          disabled={loading || error || page <= 1}
        >
          <ChevronLeft size={16} strokeWidth={1.75} aria-hidden="true" />
          Previous
        </Button>
        <Button
          variant="pagination"
          size="sm"
          onClick={() => onPageChange(Math.min(totalPages, page + 1))}
          disabled={loading || error || page >= totalPages}
        >
          Next
          <ChevronRight size={16} strokeWidth={1.75} aria-hidden="true" />
        </Button>
      </div>
    </footer>
  );
}
