import { Fragment, useMemo, useState } from 'react';
import { ArrowDown, ArrowUp, ArrowUpDown, ChevronDown } from 'lucide-react';

import { cn } from '../../lib/utils';
import DataState from '../common/DataState';
import TableScroller from './TableScroller';

/**
 * THE TABLE SHELL. `03.2a`, and since 2026-09-10 card.html's `.table` in every measurement:
 * small-capital heads on the quiet surface, 11px rows, hairlines between them, the first
 * column indented to 20px, the selected row on the brand's soft fill.
 *
 * This application has **three real `<table>`s and seven `grid-cols-N` pseudo-tables** --
 * ten tabular surfaces, each with its own answer to headers, empty states, overflow and
 * row actions, and none of which a `<table>` grep can find (BR-19). P4 converts the seven;
 * `05.2` hardens all ten. This is what they all become.
 *
 * ---
 *
 * 🔴 **`rowKey` IS REQUIRED AND ITS ABSENCE THROWS.** Not a warning, not a fallback to the
 * array index -- a throw, in development, at the first render.
 *
 * The fallback is what makes this worth a paragraph. A table keyed by index looks perfect
 * until the list reorders or a row is deleted, and then React reuses the DOM node with the
 * old row's state in it: the checkbox you ticked follows the row that took its place, the
 * open detail panel now describes a different member, and an edit-in-place lands on the
 * wrong record. **Nothing errors.** It is R-ROB-H, and it is the exact failure shape this
 * arc keeps finding -- a check that cannot tell its subject from nothing -- so the
 * primitive refuses to guess rather than guessing quietly.
 *
 * The throw is guarded by `import.meta.env.DEV` so a production render degrades to the
 * index instead of blanking a screen a user is standing in front of; the mutation-checked
 * test (`Table.test.jsx`) is what proves the guard fires at all.
 *
 * ---
 *
 * 📱 **THE COLUMN PRIORITY MODEL (R-RESP-K)** -- the third strategy, beside "scroll it" and
 * "cards below `md`", and the one that suits a back office. Every column declares what it
 * is worth:
 *
 *   `essential`  always visible, at every width. A member's name and number.
 *   `secondary`  visible from `md` up; below that it moves INTO the row's detail panel.
 *   `detail`     never a column; only ever in the detail panel.
 *
 * 📐 **AND THE COLUMN LADDER (card.css R-RESP-L).** A column may also declare `fit` --
 * `wide`, `medium` or `narrow` -- the register width below which it yields. Docking the
 * record panel from 1280px narrows the register to 560-740px, so the VIEWPORT stopped
 * predicting how much room the table has; the container query in `index.css` reads the
 * register's own width instead. Employee number goes first, then Branch (a lookup you do
 * once you have found the row), then Bank; Status is the scanning column and never yields.
 *
 * 📱 **TRUNCATION IS ONLY EVER IN THE CELL (R-RESP-I).** A column may set `truncate` and the
 * full value is then in the detail panel and in the cell's `title`. A truncated name with no
 * way to read it is a table that cannot do its job, and this is where that rule is enforced
 * rather than remembered.
 *
 * ---
 *
 * 🔴 **A ROW'S ACTION IS A CONTROL INSIDE THE ROW.** Run 7 found six controls in this
 * application that only a mouse can reach, and click-only table rows were four of them. The
 * row's action is a `<button>` rendered by the column that owns it -- in the tab order, with
 * an accessible name, working with Enter -- and that is still true. What `onRowSelect` adds
 * is card.html's pointer convenience on top: the WHOLE row is a target for a mouse or a
 * finger, exactly as the drawer's scrim is, while the keyboard keeps the real control.
 * ↑ and ↓ move between rows' controls, as the empty panel's hints promise.
 *
 * ---
 *
 * 🔑 **THE FOUR DATA STATES, NOT THREE** (conventions §8, and A27 one layer down). `loading`,
 * `error`, `empty` and loaded are separate branches and `error` NEVER renders as `empty`: a
 * backend that is down must not tell an administrator there are no members. `error` also
 * carries a retry, because an error state with no way out is a dead end. Loading is the
 * mock's: skeleton rows under the head, and a live status line saying so.
 *
 * 🔧 **`total` MAKES SERVER PAGING A DROP-IN (R-SCALE-D).** `rows` is what is on screen;
 * `total` is how many exist. When they differ and no pager is wired, the table says *"showing
 * the first N of M -- refine your search"* rather than implying the list is complete.
 *
 * ➕ **`footer` IS A SHEET'S TOTAL, IN A `<tfoot>`** (month end `02.1`, the mock's `tfoot`). It is
 * a row of the same columns and nothing opens it: its expander cell is there, and empty, so a
 * phone shows no chevron that leads nowhere. **`end`** marks a column that stands at the right of
 * a phone's card row (index.css, "The register on a phone"): the row's second column.
 *
 * ➕ **`below` IS WHAT A ROW OPENS ONTO, AT EVERY WIDTH** (month end A.1, a workplace's visits):
 * given a row, it answers what to show under it, or nothing. The row it opens is the caller's to
 * mark (`selectedKey`) and its control the caller's to render (`aria-expanded`), as a register's
 * record is; a phone's card rows keep it as a block of its own (index.css).
 */

/** @typedef {'essential'|'secondary'|'detail'} ColumnPriority */

const PRIORITIES = new Set(['essential', 'secondary', 'detail']);
const FITS = new Set(['wide', 'medium', 'narrow']);
const SKELETON_ROWS = 8;

function resolveRowKey(rowKey, row, index) {
  if (typeof rowKey === 'function') return rowKey(row, index);
  if (typeof rowKey === 'string') return row?.[rowKey];
  return undefined;
}

function cellValue(column, row, index) {
  return typeof column.cell === 'function' ? column.cell(row, index) : row?.[column.key];
}

const INTERACTIVE = 'a, button, input, select, textarea, summary, [role="button"], [contenteditable]';

// card.css `.table th`: 11.5px, 600, tracked .08em, on the quiet surface, head ink.
const HEAD_CELL_BASE = 'whitespace-nowrap px-3.5 text-left text-xs font-semibold uppercase tracking-[0.08em] text-head-ink first:pl-5';
const HEAD_CELL_DENSE = `${HEAD_CELL_BASE} py-2`;
/**
 * A head cell at the table's own density. Exported for the two tables that are hand-built because their rows are
 * editors (Subscriptions, Recruitment Channel), which carried this string as copies of each other (RF).
 */
export const HEAD_CELL = `${HEAD_CELL_BASE} pb-[11px] pt-[13px]`;

export default function Table({
  columns = [],
  rows = [],
  rowKey,
  caption,
  loading = false,
  error = null,
  errorMessage,
  onRetry,
  emptyTitle = 'Nothing here yet',
  emptyMessage,
  emptyAction,
  total,
  className,
  dense = false,
  selectedKey,
  onRowSelect,
  enterKey,
  footer,
  below,
  sort,
  onSortChange,
}) {
  const [expanded, setExpanded] = useState(() => new Set());

  if (import.meta.env?.DEV && !rowKey) {
    throw new Error(
      'Table requires `rowKey` -- a function (row, index) => id, or the name of an id ' +
        'field. Without it React keys rows by array index, and the first reorder or ' +
        'deletion silently hands one row the DOM node of another: a ticked checkbox ' +
        'follows the wrong record and an open detail panel describes a different one. ' +
        'Nothing errors when that happens, which is why this does.',
    );
  }

  const headCell = dense ? HEAD_CELL_DENSE : HEAD_CELL;
  // card.css `.table td`: 11px 14px, hairline under, the first column at 20px.
  const bodyCell = cn('px-3.5 align-middle text-base text-ink first:pl-5', dense ? 'py-2' : 'py-[11px]');

  const { visible, collapsed } = useMemo(() => {
    const known = columns.map((column) => {
      const priority = column.priority ?? 'essential';
      if (!PRIORITIES.has(priority)) {
        throw new Error(
          `Table column "${column.key}" has priority "${priority}". Known priorities: ` +
            `essential, secondary, detail. A typo that fell back to a default would ` +
            `decide, silently, what a phone shows.`,
        );
      }
      if (column.fit !== undefined && !FITS.has(column.fit)) {
        throw new Error(
          `Table column "${column.key}" has fit "${column.fit}". Known fits: wide, medium, ` +
            `narrow -- the register width below which the column yields (index.css, ` +
            `"The register is a CONTAINER").`,
        );
      }
      // 🔑 THE CELL CLASSES ARE COMPUTED PER COLUMN, ONCE -- not per cell. They depend only
      // on the column and on `dense`, never on the row, and computing them in the render
      // loop meant `rows.length × visible.length` clsx joins and twMerge lookups: 450 of
      // them for a 50-row, 9-column members table, for at most 9 distinct answers.
      const alignment =
        column.align === 'right' ? 'text-right' : column.align === 'center' ? 'text-center' : null;
      const hideBelowMd = priority === 'secondary' ? 'hidden md:table-cell' : null;
      // R-RESP-I: truncate ONLY here; the full value is in the panel.
      const bodyClass = cn(
        bodyCell,
        alignment,
        hideBelowMd,
        column.truncate && 'max-w-[16rem] truncate',
        column.nowrap !== false && 'whitespace-nowrap',
        column.className,
      );
      return {
        ...column,
        priority,
        headClass: cn(headCell, alignment, hideBelowMd, column.headClassName),
        bodyClass,
        skeletonClass: cn(bodyClass, 'py-[15px]'),
      };
    });
    return {
      visible: known.filter((column) => column.priority !== 'detail'),
      collapsed: known.filter((column) => column.priority !== 'essential'),
    };
  }, [columns, headCell, bodyCell]);

  const expandable = collapsed.length > 0;
  const columnCount = visible.length + (expandable ? 1 : 0);

  // The same reasoning, for what a ROW wears: two answers per render -- selected or not -- merged twice, not once per
  // row, and the expander cell's once rather than once per row and per skeleton.
  const rowClasses = [false, true].map((selected) =>
    cn(
      // `touch-size`: a 44px row on a coarse pointer, so a row's expander and its action never land on the next row's
      // (`07.2`; the row rule is in index.css, because a row's height is set through its cells).
      'touch-size [content-visibility:auto] [contain-intrinsic-size:auto_var(--size-row)]',
      'transition-colors duration-(--duration-instant)',
      onRowSelect && 'cursor-pointer',
      selected ? 'bg-brand-soft' : 'hover-hover:hover:bg-surface-2',
    ),
  );
  const expanderCellClass = cn(bodyCell, 'w-10 md:hidden');

  /** A row's cells, column by column -- a body row's and the footer's alike. */
  const cellsOf = (row, index) =>
    visible.map((column) => {
      const content = cellValue(column, row, index);
      return (
        <td
          key={column.key}
          data-col={column.key}
          data-fit={column.fit}
          data-end={column.end ? '' : undefined}
          className={column.bodyClass}
          title={column.truncate && typeof content === 'string' ? content : undefined}
        >
          {content}
        </td>
      );
    });

  const toggle = (key) =>
    setExpanded((current) => {
      const next = new Set(current);
      if (next.has(key)) next.delete(key);
      else next.add(key);
      return next;
    });

  /*
   * The whole row as a pointer target. A click that began on a control inside the row --
   * the row's own action, an expander, a link -- belongs to that control and is left alone;
   * everything else on the row selects it. Text selection is not a click.
   */
  const selectFromRow = (row) => (event) => {
    if (event.target.closest(INTERACTIVE)) return;
    if (window.getSelection?.()?.toString()) return;
    onRowSelect(row);
  };

  /* ↑ ↓ move between the rows' first controls -- the empty panel's hint, honoured. */
  const onKeyDown = (event) => {
    if (event.key !== 'ArrowDown' && event.key !== 'ArrowUp') return;
    const current = event.target.closest('tr[data-row]');
    if (!current) return;
    const tbody = current.parentElement;
    const all = [...tbody.querySelectorAll('tr[data-row]')];
    const index = all.indexOf(current);
    const next = all[index + (event.key === 'ArrowDown' ? 1 : -1)];
    if (!next) return;
    event.preventDefault();
    (next.querySelector('[data-row-action]') ?? next.querySelector(INTERACTIVE))?.focus();
  };

  // 🔑 ONE NAMED STATE, not four growing negation chains (`!loading && !error && rows.length
  // === 0` and so on). conventions §8 says a failed query must NEVER render as an empty
  // state; with the branches mutually exclusive by construction that is structural rather
  // than something four conditions have to keep agreeing about.
  const state = loading ? 'loading' : error ? 'error' : rows.length === 0 ? 'empty' : 'rows';

  /** One full-width row carrying a state, so the header stays put while it changes. */
  const stateRow = (content) => (
    <tr>
      <td colSpan={columnCount || 1} className="p-0">
        {content}
      </td>
    </tr>
  );

  return (
    <div className={cn('flex flex-col gap-2', className)}>
      <TableScroller label={caption}>
        <table className="w-full border-collapse text-left">
          {caption && <caption className="sr-only">{caption}</caption>}

          {/* 📱 STICKY, so the column names survive a scroll. A ten-row table does not
              need it; a register of a hundred members read on a phone is exactly a table
              where the header has left the screen by the time the interesting row arrives,
              and "which column is this" then costs a scroll back up and a scroll back
              down. `z-10` keeps it above the rows it is scrolling over. */}
          <thead className="sticky top-0 z-10 border-b border-line-strong bg-surface-2">
            <tr>
              {expandable && (
                <th scope="col" data-expander="" className={cn(headCell, 'w-10 md:hidden')}>
                  <span className="sr-only">Expand row</span>
                </th>
              )}
              {visible.map((column) => {
                /*
                 * ↕️ A HEADING THAT SORTS (the member register, A10): `sortKey` on the column and `onSortChange` on
                 * the table. The order is the SERVER's, so the heading only asks for it; the button is the heading's
                 * own text, and `aria-sort` on the cell says which column the rows are in -- on that one cell alone,
                 * as the APG's sortable table does. A column without `sortKey` is a plain heading, as every table was.
                 */
                const sortable = Boolean(column.sortKey && onSortChange);
                const active = sortable && sort?.key === column.sortKey;
                const Arrow = !active ? ArrowUpDown : sort.direction === 'desc' ? ArrowDown : ArrowUp;
                return (
                  <th
                    key={column.key}
                    scope="col"
                    data-col={column.key}
                    data-fit={column.fit}
                    style={column.width ? { width: column.width } : undefined}
                    className={column.headClass}
                    aria-sort={active ? (sort.direction === 'desc' ? 'descending' : 'ascending') : undefined}
                  >
                    {sortable ? (
                      <button
                        type="button"
                        onClick={() => onSortChange(column.sortKey)}
                        className={cn(
                          'touch-target -mx-1 inline-flex items-center gap-1 rounded-sm px-1 uppercase tracking-[inherit]',
                          'focus:outline-none focus-visible:ring-2 focus-visible:ring-brand-ring hover-hover:hover:text-ink',
                          active && 'text-ink',
                        )}
                      >
                        {column.header}
                        <Arrow aria-hidden="true" className={cn('size-3.5 shrink-0', !active && 'text-ink-faint')} strokeWidth={2} />
                      </button>
                    ) : (
                      column.header
                    )}
                  </th>
                );
              })}
            </tr>
          </thead>

          {/* The arrow keys are handled on the body rather than on each row's control, so
              a row action added by any column inherits them. The body is a listener, not a
              control: it has no role of its own, no tab stop, and the linter's objection is
              to exactly that shape, which is the shape intended here. */}
          {/* eslint-disable-next-line jsx-a11y/no-noninteractive-element-interactions -- ↑/↓ delegate to the rows' own controls; the tbody itself is never a target */}
          <tbody
            key={enterKey}
            onKeyDown={onRowSelect ? onKeyDown : undefined}
            className={cn('divide-y divide-line', enterKey !== undefined && state === 'rows' && 'rows-enter')}
            aria-busy={state === 'loading' || undefined}
          >
            {/* 🔑 THE THREE STATE BRANCHES RENDER THROUGH `DataState` SINCE `06.1b`, and the
                extraction is the DRY rule's own condition rather than a tidy-up: `06.1b`
                needed the same four states OUTSIDE a table -- for the reference-list panels
                and the per-field failures -- which is the second consumer that conventions
                §11 requires before anything is extracted.

                What was here was correct and is unchanged in behaviour: a live `status` while
                loading, an `alert` with a retry on failure, the empty copy on neither. What
                changes is that those rules now have ONE implementation for the whole
                application instead of one inside `Table` and another wherever a panel needed
                them -- which is how the eight separate empty-state copies happened. */}
            {state === 'loading' && (
              <>
                {/* card.html's skeleton: the shape of the rows that are coming, so the
                    register does not collapse to a spinner and jump when they land.

                    ⚠️ THE ROW IS `data-skeleton`, NOT `data-row`. `data-row` means "a row a
                    person can act on" -- ↑/↓ walk it, the pointer handler reads it, and any
                    measurement that counts it is counting the register's contents. A
                    skeleton is the shape of a row that has not arrived; marking it as one
                    makes the loading state indistinguishable from the loaded one to
                    everything that asks, which is this arc's oldest defect wearing a new
                    hat. (Found by a capture that waited for `tr[data-row]`, matched a
                    skeleton, and then found no control inside it.) */}
                {Array.from({ length: SKELETON_ROWS }, (_, index) => (
                  <tr key={`skeleton-${index}`} data-skeleton="" aria-hidden="true">
                    {expandable && <td data-expander="" className={expanderCellClass} />}
                    {visible.map((column, columnIndex) => (
                      <td key={column.key} data-col={column.key} data-fit={column.fit} className={column.skeletonClass}>
                        <span
                          className="skeleton"
                          // The status column's pill as a style, not utilities: `.skeleton` is unlayered, so it
                          // beat `h-[22px] w-[72px] rounded-full` and drew the pill as a 12px bar (the `06.3`
                          // critique) -- the fix FigureStrip's skeleton already took.
                          style={columnIndex === visible.length - 1 ? { width: 72, height: 22, borderRadius: 9999 } : undefined}
                        />
                      </td>
                    ))}
                  </tr>
                ))}
                {stateRow(<DataState state="loading" layout="row" title="Loading…" />)}
              </>
            )}

            {state === 'error' &&
              stateRow(
                <DataState
                  state="error"
                  layout="row"
                  // A caller may pass a sentence (most do) or the rejection itself. A string
                  // has no `retryable`, so `DataState` treats it as retryable -- which is the
                  // behaviour this component always had.
                  title={typeof error === 'string' ? error : undefined}
                  message={errorMessage}
                  error={typeof error === 'string' ? null : error}
                  onRetry={onRetry}
                />,
              )}

            {state === 'empty' &&
              stateRow(
                <DataState
                  state="empty"
                  layout="row"
                  title={emptyTitle}
                  message={emptyMessage}
                  action={emptyAction}
                />,
              )}

            {state === 'rows' &&
              rows.map((row, index) => {
                const key = resolveRowKey(rowKey, row, index) ?? index;
                const isOpen = expanded.has(key);
                const opened = below?.(row, index);
                const isSelected = selectedKey !== undefined && selectedKey !== null && key === selectedKey;

                return (
                  <Fragment key={key}>
                    {/* 🔑 R-PERF-J: `content-visibility: auto` lets the browser skip
                        layout and paint for a row that is off screen, and `contain-intrinsic-size`
                        is what stops that turning into a scrollbar that jumps -- without a
                        size hint the browser treats a skipped row as zero-height and the
                        page shortens as you scroll. One rule in the primitive, so every
                        table gets it and none of them has to remember. */}
                    {/* The row is a pointer target and nothing else: no role, no tabindex,
                        no key handler of its own. Its keyboard action is the control inside
                        it, and ↑/↓ live on the body above. */}
                    <tr
                      data-row=""
                      aria-current={isSelected ? 'true' : undefined}
                      onClick={onRowSelect ? selectFromRow(row) : undefined}
                      style={enterKey !== undefined ? { '--i': Math.min(index, 10) } : undefined}
                      className={rowClasses[isSelected ? 1 : 0]}
                    >
                      {expandable && (
                        <td data-expander="" className={expanderCellClass}>
                          <button
                            type="button"
                            onClick={() => toggle(key)}
                            aria-expanded={isOpen}
                            aria-label={isOpen ? 'Hide details' : 'Show details'}
                            className={cn(
                              'touch-target rounded-sm p-1 text-ink-faint',
                              'focus:outline-none focus-visible:ring-2 focus-visible:ring-brand-ring',
                            )}
                          >
                            <ChevronDown
                              aria-hidden="true"
                              className={cn(
                                'size-4 transition-transform duration-(--duration-instant)',
                                isOpen && 'rotate-180',
                              )}
                            />
                          </button>
                        </td>
                      )}

                      {cellsOf(row, index)}
                    </tr>

                    {expandable && isOpen && (
                      <tr data-detail="" className="md:hidden">
                        <td colSpan={columnCount} className="bg-surface-2 px-5 py-3">
                          <dl className="grid grid-cols-[auto_1fr] gap-x-4 gap-y-2">
                            {collapsed.map((column) => (
                              <Fragment key={column.key}>
                                <dt className="text-xs font-semibold uppercase tracking-[0.08em] text-ink-muted">
                                  {column.header}
                                </dt>
                                <dd className="min-w-0 break-words text-sm text-ink">
                                  {cellValue(column, row, index)}
                                </dd>
                              </Fragment>
                            ))}
                          </dl>
                        </td>
                      </tr>
                    )}

                    {opened && (
                      <tr data-below="">
                        <td colSpan={columnCount} className="p-0">
                          {opened}
                        </td>
                      </tr>
                    )}
                  </Fragment>
                );
              })}
          </tbody>

          {state === 'rows' && footer && (
            <tfoot className="border-t border-line">
              <tr className="touch-size">
                {expandable && <td data-expander="" className={expanderCellClass} />}
                {cellsOf(footer, rows.length)}
              </tr>
            </tfoot>
          )}
        </table>
      </TableScroller>

      {/* R-SCALE-D. Only when the list on screen is genuinely a prefix of a longer one. */}
      {!loading && !error && typeof total === 'number' && total > rows.length && (
        <p className="px-5 pb-3 text-sm text-ink-muted">
          Showing the first <strong className="font-medium text-ink">{rows.length}</strong> of{' '}
          <strong className="font-medium text-ink">{total}</strong> — refine your search to narrow
          it down.
        </p>
      )}
    </div>
  );
}
