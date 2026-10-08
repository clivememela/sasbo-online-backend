import { cn } from '../../lib/utils';

/**
 * THE SHEET -- card.css `.card-surface`: white, radius 14, the resting shadow, no border,
 * its children clipped to its corners. The register is one; so is every other list in
 * the application since 2026-09-10, because the owner's instruction is that card.html is
 * the whole application's world, and the mock has exactly one kind of raised surface.
 *
 * `grow` lets it fill the rest of the page so its footer (the pager) sits at the foot of
 * the sheet rather than under the last row.
 */
export default function Sheet({ children, className, grow = false, ...rest }) {
  return (
    <div
      className={cn(
        'flex flex-col overflow-hidden rounded-lg bg-surface shadow-(--shadow-1)',
        grow && 'flex-1',
        className,
      )}
      {...rest}
    >
      {children}
    </div>
  );
}

/**
 * The page's gutter -- card.css gives every section 28px of side padding on a desk and
 * the same 16px the topbar uses below `lg`. One class string, so a page cannot drift.
 */
export const PAGE_GUTTER = 'px-7 max-lg:px-4';

/** A page: the gutter, the mock's 26px above the head, 28px below the last sheet. */
export function Page({ children, className }) {
  return (
    <div className={cn('flex min-h-0 flex-1 flex-col pb-7 pt-[26px] text-ink', PAGE_GUTTER, className)}>
      {children}
    </div>
  );
}
