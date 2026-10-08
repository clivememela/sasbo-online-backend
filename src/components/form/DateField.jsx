import {
  useCallback,
  useEffect,
  useId,
  useLayoutEffect,
  useMemo,
  useRef,
  useState,
} from 'react';
import { createPortal } from 'react-dom';
import { CalendarDays, ChevronDown, ChevronLeft, ChevronRight } from 'lucide-react';

import { cn } from '../../lib/utils';
import Field, { controlClasses } from './Field';
import useMediaQuery from '../../hooks/useMediaQuery';
import {
  ageInWords,
  compareDates,
  daysInMonth,
  formatDate,
  formatDateLong,
  MONTHS as MONTHS_SHORT,
  parseDate,
  sameDate,
  shiftDays,
  shiftMonths,
  timeAgo,
  toWireDate,
  today as todayDate,
  weekdayOf,
} from '../../lib/format';

/**
 * THE DATE FIELD. `03.3`.
 *
 * 🔒 **THIS UNIT PORTS AN APPROVED ARTIFACT; IT DOES NOT DESIGN ONE.**
 * [`mockups/design/datepicker.html`](../../../../mockups/design/datepicker.html) is the
 * behaviour contract, owner-signed 2026-09-09. Every clause below is from it, and any
 * deviation is a change to an owner-approved artifact that goes back to the owner first.
 *
 * 🔴 **THE ENGINE WAS `react-day-picker` (D4, ratified) AND THE MEASUREMENT REPLACED IT.**
 * The invariant D4 was ratified under is explicit: *"if `react-day-picker` cannot express a
 * clause, the clause wins and the library is wrapped or replaced."* Three measurements,
 * taken while wiring it up, said replace — see `findings.md` F-P16:
 *
 *   **① 81.91 KiB** for the `DateField` chunk, which took total assets to **933.65 KiB
 *   against a 900 KiB ceiling**. The bundle budget failed. `react-day-picker` depends on
 *   `date-fns`, and this component needs no date library: `lib/format.js` already had the
 *   whole of the arithmetic, written for the typed-entry parser.
 *
 *   **② A 15-second import**, measured in plain Node — `date-fns`'s barrel is 1,428 ESM
 *   modules. Under Vitest that made the route smoke FLAKY: a different route timed out on
 *   every run, because whichever lazy route was in flight while that transform held the
 *   loader missed its 5s budget.
 *
 *   **③ It was only ever supplying the DAY grid anyway.** The mock's month and year
 *   pickers are GRIDS, not dropdowns — *"month grid and 20-year grid, one tap each"* —
 *   so both were already hand-written here, and so were the head, the readout, the sheet
 *   and every keyboard binding outside the grid. The library carried about forty lines.
 *
 * ⚠️ **The keyboard model is therefore OURS, and it is the mock's, binding for binding**
 * (`datepicker.js` `onPopKey`): `←→↑↓` `Home/End` `PgUp/PgDn` `Shift+PgUp/PgDn` `Enter`
 * `Space` `Esc`. That is the one thing the library was genuinely giving us for free, so it
 * is the one thing this file's tests drive hardest.
 *
 * **The whole field is the trigger.** THE OWNER'S ORIGINAL COMPLAINT was that only the
 * icon opened the picker. Clicking anywhere on the box opens it, typing keeps working
 * while it is open, and the icon button toggles.
 *
 * ---
 *
 * 🔴 **IT DOES NOT CHANGE WHAT IS SUBMITTED.** The 10 native `<input type="date">` this
 * replaces hand their callers `yyyy-mm-dd` through `e.target.value`, and so does this:
 * `onChange` receives `{ target: { value, name } }`, native-shaped, so a call site written
 * as `onChange={handleChange("joinDate")}` needs no edit at all. **What the user SEES is
 * `yyyy/mm/dd`** (conventions §15) and what the API receives is unchanged. Confusing those
 * two would have quietly changed every date this application posts.
 *
 * 🔴 **AND NO DATE-ONLY VALUE GOES THROUGH `toISOString`** (R-ROB-N). Dates live as
 * `{ y, m, d }` from end to end now — with the library gone there is no longer anywhere
 * that a `Date` is even constructed, except inside `lib/format.js`'s calendar arithmetic,
 * which uses the LOCAL fields constructor and says so.
 *
 * ---
 *
 * **Typed entry** accepts `15 Jun 1988` · `15/06/1988` · `1988-06-15` · `19880615`, and
 * `1988/06/15` because that is what the field itself writes. **A refusal is a sentence
 * under the field, never a silent revert** — the mock is explicit about that too, and a
 * field that erases what you typed is a field you cannot correct.
 */

const MONTHS_LONG = [
  'January', 'February', 'March', 'April', 'May', 'June',
  'July', 'August', 'September', 'October', 'November', 'December',
];
const WEEKDAYS = [
  { short: 'Su', long: 'Sunday' },
  { short: 'Mo', long: 'Monday' },
  { short: 'Tu', long: 'Tuesday' },
  { short: 'We', long: 'Wednesday' },
  { short: 'Th', long: 'Thursday' },
  { short: 'Fr', long: 'Friday' },
  { short: 'Sa', long: 'Saturday' },
];

// Sunday, the CLDR first day for South Africa and what the current app already shows.
const YEARS_PER_PAGE = 20;

// One decision, not ten. This string was written out at ten sites in this file while every
// other file in the arc states it once -- so a change to the focus treatment was ten edits
// that had to stay identical, and conventions §7 makes the ring non-optional.
const FOCUS_RING = 'focus:outline-none focus-visible:ring-2 focus-visible:ring-brand-ring';

// The month and year grids are the same control with different contents.
const OPTION_BUTTON = [
  // `touch-size`, not an overlay: a 44px square on each 36px cell lay across its neighbours (`07.2`, the second sweep).
  'touch-size rounded-md px-2 py-2 text-sm text-ink',
  'hover-hover:hover:bg-surface-2',
  FOCUS_RING,
  'disabled:cursor-not-allowed disabled:opacity-40',
].join(' ');

/** The mock's threshold, and the 16px rule's: a tablet in landscape is a touch device. */
const SHEET_QUERY = '(max-width: 760px), (pointer: coarse)';

/** `"today"` or an ISO date, as the mock's `data-min` / `data-max` accept. */
function readBound(bound) {
  if (!bound) return null;
  if (bound === 'today') return todayDate();
  return parseDate(bound);
}

// `useMediaQuery` lived here until the topbar needed the same answer; it is src/hooks/useMediaQuery.js now, with the
// reasons it is a store and not an effect, and why it is not cached.

export default function DateField({
  label,
  value,
  onChange,
  name,
  min,
  max,
  /** `"age"` for a birth date, `"ago"` for everything else. The mock's `data-hint`. */
  hint: hintKind,
  helpText,
  error: errorProp,
  required,
  disabled,
  className,
  placeholder = 'Choose a date',
  ...rest
}) {
  const popoverId = useId();
  const isSheet = useMediaQuery(SHEET_QUERY);

  const [open, setOpen] = useState(false);
  const [view, setView] = useState('days');
  const [text, setText] = useState(() => (value ? formatDate(value) : ''));
  const [refusal, setRefusal] = useState(null);
  const [cursor, setCursor] = useState(() => parseDate(value) ?? todayDate());
  // 🔑 `0`, not a derived value. The year grid only exists while the panel is open, and the
  // only two ways in -- `openPicker` and the year header button -- both set this first. The
  // clever initialiser was never once read, which is a derivation that looks load-bearing.
  const [yearBase, setYearBase] = useState(0);

  // Which day the arrows are on. SEPARATE from the selection: moving around the grid must
  // not change the answer, only where you are — the difference between browsing a calendar
  // and editing a member's date of birth by accident.
  const [focusDay, setFocusDay] = useState(() => parseDate(value) ?? todayDate());

  const inputRef = useRef(null);
  const popoverRef = useRef(null);
  const focusCellRef = useRef(null);
  // 🔑 A REF, not state. The only question is "did the KEYBOARD move the focus?", which is
  // set in an event handler and read in a layout effect — it never affects what is
  // rendered, so making it state would be a second render per arrow key, and would put a
  // `setState` inside an effect for no gain.
  const keyboardMoved = useRef(false);
  const [position, setPosition] = useState({ top: 0, left: 0, above: false });
  /**
   * 🔴 WHERE THE CALENDAR IS PORTALLED: INTO THE DIALOG IT WAS OPENED FROM, when there is one (R2-F1). On the body, a
   * calendar opened from a modal dialog was outside that dialog's pointer layer, focus trap and Escape handling -- no
   * day could be pressed. Read when the calendar OPENS, from the event, never from a ref during render.
   */
  const [portalTo, setPortalTo] = useState(null);

  const now = todayDate();

  const bounds = useMemo(() => ({ min: readBound(min), max: readBound(max) }), [min, max]);
  const selected = parseDate(value);

  // The field shows the SELECTION unless the user is mid-edit. An external change (a form
  // reset, a record loading) must reach the box; a keystroke must not be overwritten.
  //
  // 🔑 ADJUSTED DURING RENDER, not in an effect. React's own guidance for "state derived
  // from a prop", and here it is also the correct behaviour: an effect would repaint the
  // box a frame late, which on a fast typist reads as the field fighting back. The
  // `sameDate` guard is what stops the parent echoing our own emitted value back and
  // reformatting `1988/06/1` to `1988/06/01` under the caret mid-word.
  const [lastValue, setLastValue] = useState(value);
  if (value !== lastValue) {
    setLastValue(value);
    if (!sameDate(parseDate(text), parseDate(value))) setText(value ? formatDate(value) : '');
  }

  const outOfRange = useCallback(
    (date) => {
      if (bounds.min && compareDates(date, bounds.min) < 0) return 'before';
      if (bounds.max && compareDates(date, bounds.max) > 0) return 'after';
      return null;
    },
    [bounds],
  );

  const emit = useCallback(
    (date) => {
      // 🔑 NATIVE-SHAPED, so `handleChange("joinDate")` keeps working unchanged, and the
      // wire value is `yyyy-mm-dd` exactly as `<input type="date">` produced.
      onChange?.({ target: { name, value: date ? toWireDate(date) : '' } });
    },
    [onChange, name],
  );

  const commit = useCallback(
    (date) => {
      if (!date || outOfRange(date)) return;
      setText(formatDate(date));
      setRefusal(null);
      setCursor(date);
      emit(date);
      setOpen(false);
      inputRef.current?.focus({ preventScroll: true });
    },
    [emit, outOfRange],
  );

  const openPicker = useCallback(() => {
    if (disabled) return;
    setPortalTo(inputRef.current?.closest('[role="dialog"][data-state]') ?? null);
    const start = parseDate(text) ?? selected ?? todayDate();
    setCursor(start);
    setFocusDay(start);
    setYearBase(Math.floor(start.y / YEARS_PER_PAGE) * YEARS_PER_PAGE);
    setView('days');
    setOpen(true);
  }, [disabled, text, selected]);

  const closePicker = useCallback(
    (refocus) => {
      setOpen(false);
      setView('days');
      if (refocus) inputRef.current?.focus({ preventScroll: true });
    },
    [],
  );

  // Placement. A popover that runs off the bottom flips above the field.
  //
  // 🔑 TWO EFFECTS, not one. `place()` depends on the view and the month (they change the
  // panel's height); the scroll and resize LISTENERS do not. With one effect keyed on all
  // four, paging a month or opening the year grid detached and re-attached a capture-phase
  // scroll listener for nothing.
  const place = useCallback(() => {
    const anchor = inputRef.current?.getBoundingClientRect();
    const panel = popoverRef.current;
    if (!anchor || !panel) return;
    const height = panel.offsetHeight;
    const width = panel.offsetWidth;
    const viewportH = window.innerHeight;
    const viewportW = document.documentElement.clientWidth;
    const below = anchor.bottom + 6;
    const above = anchor.top - 6 - height;
    const flip = below + height > viewportH - 8 && above > 8;
    setPosition((previous) => {
      const next = {
        top: Math.round(flip ? above : below),
        left: Math.round(Math.min(Math.max(anchor.left, 8), Math.max(8, viewportW - width - 8))),
        above: flip,
      };
      // A scroll event fires at frame rate; a fresh object is never equal, so without this
      // the panel re-rendered on every one of them for an unchanged position.
      return previous.top === next.top && previous.left === next.left && previous.above === next.above
        ? previous
        : next;
    });
  }, []);

  // Re-place when anything that changes the panel's size changes.
  useLayoutEffect(() => {
    if (!open || isSheet) return;
    place();
  }, [open, isSheet, view, cursor, place]);

  // Attach the listeners once per opening, not per view change.
  useLayoutEffect(() => {
    if (!open || isSheet) return undefined;
    window.addEventListener('scroll', place, true);
    window.addEventListener('resize', place);
    return () => {
      window.removeEventListener('scroll', place, true);
      window.removeEventListener('resize', place);
    };
  }, [open, isSheet, place]);

  // Dismiss on a pointer outside the field or the panel.
  useEffect(() => {
    if (!open) return undefined;
    const onPointerDown = (event) => {
      if (popoverRef.current?.contains(event.target)) return;
      if (inputRef.current?.closest('[data-date-field]')?.contains(event.target)) return;
      closePicker(false);
    };
    document.addEventListener('pointerdown', onPointerDown);
    return () => document.removeEventListener('pointerdown', onPointerDown);
  }, [open, closePicker]);

  // Move REAL focus to the cell the arrows landed on, after the grid has re-rendered with
  // it. A roving-focus grid that updates `tabIndex` without moving focus leaves the
  // browser's focus on a cell that is now `tabIndex={-1}` — the arrows appear to work and
  // the screen reader announces nothing.
  useLayoutEffect(() => {
    if (!open || view !== 'days' || !keyboardMoved.current) return;
    keyboardMoved.current = false;
    focusCellRef.current?.focus({ preventScroll: true });
  }, [open, view, focusDay, cursor]);

  // Escape closes it, from ANYWHERE inside it. One document-level path rather than a
  // handler on the panel: focus can be inside the day grid, on the scrim, or back
  // on the input, and three handlers that must agree is three chances for one to be missed.
  useEffect(() => {
    if (!open) return undefined;
    const onKeyDown = (event) => {
      if (event.key !== 'Escape') return;
      event.preventDefault();
      closePicker(true);
    };
    document.addEventListener('keydown', onKeyDown);
    return () => document.removeEventListener('keydown', onKeyDown);
  }, [open, closePicker]);

  // The sheet covers the page, so the page must not scroll behind it.
  useEffect(() => {
    if (!open || !isSheet) return undefined;
    const previous = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
    return () => {
      document.body.style.overflow = previous;
    };
  }, [open, isSheet]);

  const onInputKeyDown = (event) => {
    if (event.key === 'ArrowDown') {
      event.preventDefault();
      // 🔑 ArrowDown MOVES INTO THE GRID, it does not merely open the panel — the mock's
      // `open(field, "keyboard")` lands on a day, and pressing it again when the calendar
      // is already open does the same. A picker that opens under the keyboard and leaves
      // focus in the text box is a picker a keyboard user cannot actually reach.
      keyboardMoved.current = true;
      if (open) focusCellRef.current?.focus({ preventScroll: true });
      else openPicker();
      return;
    }
    // Escape is NOT handled here: the document-level listener above owns it, and owns it
    // from anywhere inside the panel. Two handlers for one key is the "one dismissal path"
    // argument losing to itself.
    if (event.key === 'Enter' || event.key === ' ') {
      // The field is a chooser: Enter and Space toggle the calendar, and the form is NOT
      // submitted from a field that has nothing typed in it to submit.
      event.preventDefault();
      if (open) closePicker(true);
      else openPicker();
    }
  };

  const readout = (() => {
    const shown = parseDate(text) ?? selected;
    if (!shown) return { title: 'No date yet', detail: 'Choose a day.' };
    const meaning =
      hintKind === 'age' ? ageInWords(shown) : hintKind === 'ago' ? timeAgo(shown) : '';
    return { title: formatDateLong(shown), detail: meaning };
  })();

  const message = errorProp || refusal;

  const renderMonthGrid = () => (
    <div role="listbox" aria-label="Month" className="grid grid-cols-3 gap-1 p-2">
      {MONTHS_SHORT.map((short, index) => {
        const month = index + 1;
        const monthDisabled =
          (bounds.max && compareDates({ y: cursor.y, m: month, d: 1 }, bounds.max) > 0) ||
          (bounds.min &&
            compareDates({ y: cursor.y, m: month, d: daysInMonth(cursor.y, month) }, bounds.min) < 0);
        return (
          <button
            key={short}
            type="button"
            role="option"
            aria-selected={selected?.y === cursor.y && selected?.m === month}
            aria-disabled={monthDisabled || undefined}
            disabled={monthDisabled}
            onClick={() => {
              setCursor({ y: cursor.y, m: month, d: Math.min(cursor.d, daysInMonth(cursor.y, month)) });
              setView('days');
            }}
            className={cn(OPTION_BUTTON, cursor.m === month && 'bg-brand-soft font-medium text-brand-ink')}
          >
            {short}
          </button>
        );
      })}
    </div>
  );

  const renderYearGrid = () => (
    <div role="listbox" aria-label="Year" className="grid grid-cols-4 gap-1 p-2">
      {Array.from({ length: YEARS_PER_PAGE }, (_, index) => yearBase + index).map((year) => {
        const yearDisabled =
          (bounds.max && year > bounds.max.y) || (bounds.min && year < bounds.min.y);
        return (
          <button
            key={year}
            type="button"
            role="option"
            aria-selected={selected?.y === year}
            aria-disabled={yearDisabled || undefined}
            disabled={yearDisabled}
            onClick={() => {
              setCursor({ ...cursor, y: year, d: Math.min(cursor.d, daysInMonth(year, cursor.m)) });
              setView('months');
            }}
            className={cn(OPTION_BUTTON, cursor.y === year && 'bg-brand-soft font-medium text-brand-ink')}
          >
            {year}
          </button>
        );
      })}
    </div>
  );

  /* ── the day grid, and the roving-focus keyboard model ────────────────────
     Six weeks of seven, always, so the panel does not change height as you page
     through months — a calendar that resizes under the pointer is a calendar you
     mis-click. `role="grid"` with `role="gridcell"` buttons and ONE tabbable cell
     is the ARIA grid pattern: Tab enters the grid at the focused day, and the
     arrows move within it. */

  const gridDays = useMemo(() => {
    const first = { y: cursor.y, m: cursor.m, d: 1 };
    let day = shiftDays(first, -weekdayOf(first));
    const weeks = [];
    for (let w = 0; w < 6; w += 1) {
      const week = [];
      for (let i = 0; i < 7; i += 1) {
        week.push(day);
        day = shiftDays(day, 1);
      }
      weeks.push(week);
    }
    return weeks;
  }, [cursor.y, cursor.m]);

  const onGridKeyDown = (event) => {
    // Every binding here is the approved mock's (`datepicker.js` → `onPopKey`).
    const week = weekdayOf(focusDay);
    const moves = {
      ArrowLeft: () => shiftDays(focusDay, -1),
      ArrowRight: () => shiftDays(focusDay, 1),
      ArrowUp: () => shiftDays(focusDay, -7),
      ArrowDown: () => shiftDays(focusDay, 7),
      Home: () => shiftDays(focusDay, -week),
      End: () => shiftDays(focusDay, 6 - week),
      PageUp: () => shiftMonths(focusDay, event.shiftKey ? -12 : -1),
      PageDown: () => shiftMonths(focusDay, event.shiftKey ? 12 : 1),
    };

    if (moves[event.key]) {
      event.preventDefault();
      const target = moves[event.key]();
      setFocusDay(target);
      // Paging past the edge of the month brings the month with it, so the focused
      // day is always one you can see.
      if (target.m !== cursor.m || target.y !== cursor.y) setCursor(target);
      keyboardMoved.current = true;
      return;
    }

    if (event.key === 'Enter' || event.key === ' ') {
      event.preventDefault();
      if (!outOfRange(focusDay)) commit(focusDay);
    }
  };

  /*
   * 📐 THE DAYS SPAN THE PICKER, AS `datepicker.css` LAYS THEM OUT: `.dp__week` is `repeat(7, 1fr)`,
   * and a day is 38px tall in the popover and 44px in the phone's sheet. They were fixed 36px
   * squares in a flex row, so the sheet's month header ran the width of the phone while its days
   * stopped at 252px (`06.3`, date-sheet-phone-light.png) -- and 36px is under the 44px target the
   * approved sheet promises.
   */
  const renderDayGrid = () => (
    <div
      role="grid"
      aria-label={`${MONTHS_LONG[cursor.m - 1]} ${cursor.y}`}
      className={isSheet ? 'px-4 py-2' : 'p-2'}
    >
      <div role="row" className="grid grid-cols-7">
        {WEEKDAYS.map((day) => (
          <span
            key={day.short}
            role="columnheader"
            aria-label={day.long}
            title={day.long}
            className={cn(
              'text-center font-semibold uppercase tracking-wide text-ink-muted',
              isSheet ? 'h-7 text-xs leading-7' : 'h-6 text-2xs leading-6',
            )}
          >
            {day.short}
          </span>
        ))}
      </div>

      {gridDays.map((week) => (
        <div key={`${week[0].y}-${week[0].m}-${week[0].d}`} role="row" className="grid grid-cols-7 gap-0.5">
          {week.map((day) => {
            const outside = day.m !== cursor.m;
            const unavailable = Boolean(outOfRange(day));
            const isToday = sameDate(day, now);
            const isSelected = sameDate(day, selected);
            const isFocus = sameDate(day, focusDay);
            return (
              <button
                key={`${day.y}-${day.m}-${day.d}`}
                type="button"
                role="gridcell"
                ref={isFocus ? focusCellRef : undefined}
                // 🔑 ONE tabbable cell. A grid where every day is a tab stop is 42 tab
                // stops between a keyboard user and the next field.
                tabIndex={isFocus ? 0 : -1}
                aria-selected={isSelected}
                aria-disabled={unavailable || undefined}
                aria-label={`${formatDateLong(day)}${isToday ? ', today' : ''}`}
                // 🔑 ON THE CELL, not on the grid container. The container is not
                // focusable — the ARIA grid pattern puts the tab stop on ONE cell — so a
                // handler up there only ever fires by bubbling, and `jsx-a11y` is right to
                // call a keyboard handler on a non-focusable element a smell.
                onKeyDown={onGridKeyDown}
                onClick={() => {
                  if (!unavailable) commit(day);
                }}
                className={cn(
                  'w-full rounded-md text-ink',
                  isSheet ? 'h-11 text-[15px]' : 'h-[38px] text-sm',
                  'hover-hover:hover:bg-surface-2',
                  FOCUS_RING,
                  outside && 'text-ink-muted',
                  unavailable && 'cursor-not-allowed opacity-40',
                  // Today is RINGED in accent; the choice is FILLED in brand. The mock is
                  // explicit that they are different marks, so that today still reads as
                  // today on the day it is also the selection.
                  isToday && 'ring-1 ring-inset ring-accent-line',
                  isSelected && 'bg-brand font-medium text-on-brand',
                )}
              >
                {day.d}
              </button>
            );
          })}
        </div>
      ))}
    </div>
  );

  const step = (direction) => {
    if (view === 'days') setCursor(shiftMonths(cursor, direction));
    else if (view === 'months') setCursor({ ...cursor, y: cursor.y + direction });
    else setYearBase(yearBase + YEARS_PER_PAGE * direction);
  };

  const stepUnit = view === 'days' ? 'month' : view === 'months' ? 'year' : `${YEARS_PER_PAGE} years`;

  // 🔴 A FUNCTION, NOT A BINDING -- and this is the largest single waste the refactor pass
  // found. `panel` is only ever used inside `{open && createPortal(...)}`, but as a `const`
  // it was BUILT ON EVERY RENDER, open or shut: ~80 React elements (12 month buttons, 20
  // year buttons, 42 day cells and the chrome), ~120 handler closures, and per day cell an
  // `outOfRange`, three `sameDate`, a `formatDateLong` and a seven-argument `cn` -- so ~42
  // date formats and ~42 class merges. This component re-renders on EVERY KEYSTROKE in its
  // own text box, so all of that was paid per character typed with the calendar closed.
  const renderPanel = () => (
    <div
      ref={popoverRef}
      role="dialog"
      aria-modal={isSheet}
      aria-label={label ? `Choose ${label.toLowerCase()}` : 'Choose a date'}
      style={isSheet ? undefined : { top: position.top, left: position.left }}
      className={cn(
        'z-[60] border border-line bg-surface shadow-2',
        isSheet
          ? 'fixed inset-x-0 bottom-0 rounded-t-(--radius-sheet) pb-[env(safe-area-inset-bottom)]'
          : 'fixed w-[19rem] rounded-lg',
      )}
    >
      {isSheet && (
        <div className="flex items-center justify-between border-b border-line px-4 py-3">
          <h2 className="text-sm font-semibold text-ink">{label || 'Choose a date'}</h2>
          <button
            type="button"
            onClick={() => closePicker(true)}
            className={cn('touch-target rounded-md px-3 py-1 text-sm font-medium text-brand-ink', FOCUS_RING)}
          >
            Done
          </button>
        </div>
      )}

      <div className="flex items-center justify-between gap-1 px-2 pt-2">
        <button
          type="button"
          onClick={() => step(-1)}
          aria-label={`Previous ${stepUnit}`}
          className={cn('touch-size rounded-md p-1.5 text-ink-muted hover-hover:hover:bg-surface-2', FOCUS_RING)}
        >
          <ChevronLeft aria-hidden="true" className="size-4" />
        </button>

        <div className="flex items-center gap-1">
          <button
            type="button"
            aria-expanded={view === 'months'}
            onClick={() => setView(view === 'months' ? 'days' : 'months')}
            className={cn('touch-size inline-flex items-center gap-0.5 rounded-md px-2 py-1 text-sm font-medium text-ink hover-hover:hover:bg-surface-2', FOCUS_RING)}
          >
            {MONTHS_LONG[cursor.m - 1]}
            <ChevronDown aria-hidden="true" className="size-3.5" />
          </button>
          <button
            type="button"
            aria-expanded={view === 'years'}
            onClick={() => {
              if (view === 'years') setView('days');
              else {
                setYearBase(Math.floor(cursor.y / YEARS_PER_PAGE) * YEARS_PER_PAGE);
                setView('years');
              }
            }}
            className={cn('touch-size inline-flex items-center gap-0.5 rounded-md px-2 py-1 text-sm font-medium text-ink hover-hover:hover:bg-surface-2', FOCUS_RING)}
          >
            {view === 'years' ? `${yearBase}–${yearBase + YEARS_PER_PAGE - 1}` : cursor.y}
            <ChevronDown aria-hidden="true" className="size-3.5" />
          </button>
        </div>

        <button
          type="button"
          onClick={() => step(1)}
          aria-label={`Next ${stepUnit}`}
          className={cn('touch-size rounded-md p-1.5 text-ink-muted hover-hover:hover:bg-surface-2', FOCUS_RING)}
        >
          <ChevronRight aria-hidden="true" className="size-4" />
        </button>
      </div>

      {view === 'days' && renderDayGrid()}

      {view === 'months' && renderMonthGrid()}
      {view === 'years' && renderYearGrid()}

      <div className="flex items-center justify-between gap-2 border-t border-line px-3 py-2">
        <p className="min-w-0 text-xs text-ink-muted">
          <strong className="block truncate font-medium text-ink">{readout.title}</strong>
          {readout.detail && <span className="block truncate">{readout.detail}</span>}
        </p>
        <div className="flex shrink-0 items-center gap-1">
          <button
            type="button"
            onClick={() => {
              const now = todayDate();
              if (outOfRange(now)) {
                // Out of range: go there rather than choosing it, so `Today` still orients
                // you on a field where today is not a legal answer.
                setCursor(now);
                setView('days');
                return;
              }
              commit(now);
            }}
            className={cn('touch-target rounded-md px-2 py-1 text-xs font-medium text-brand-ink hover-hover:hover:bg-surface-2', FOCUS_RING)}
          >
            Today
          </button>
          <button
            type="button"
            onClick={() => {
              setText('');
              setRefusal(null);
              emit(null);
              closePicker(true);
            }}
            className={cn('touch-target rounded-md px-2 py-1 text-xs text-ink-muted hover-hover:hover:bg-surface-2', FOCUS_RING)}
          >
            Clear
          </button>
        </div>
      </div>
    </div>
  );

  return (
    <Field label={label} required={required} error={message} hint={helpText} className={className}>
      {({ id, describedBy, invalid }) => (
        <div className="relative" data-date-field>
          {/* 🔴 CHOOSE-ONLY (the owner, 2026-09-10): "I don't really want to allow dates to be
              typed in manually. Clicking on the field either opens or closes the datepicker."
              The box is read-only -- it shows the chosen date in the app's own form and
              nothing can be typed into it -- and the WHOLE FIELD toggles the calendar, which
              was the owner's original complaint about the control this replaced (only the
              icon opened it). Enter, Space and ArrowDown are the keyboard's way in, Escape
              and Done the way out, and Clear in the calendar's foot is how a date is removed. */}
          <input
            {...rest}
            ref={inputRef}
            id={id}
            name={name}
            type="text"
            readOnly
            autoComplete="off"
            spellCheck={false}
            role="combobox"
            value={text}
            placeholder={placeholder}
            required={required}
            disabled={disabled}
            aria-expanded={open}
            aria-controls={open ? popoverId : undefined}
            aria-haspopup="dialog"
            aria-invalid={invalid}
            aria-describedby={describedBy}
            onKeyDown={onInputKeyDown}
            onMouseDown={(event) => {
              // A read-only box has no caret to place; the press is the toggle. Focus is
              // moved by hand because the default that would have moved it is prevented.
              event.preventDefault();
              if (disabled) return;
              inputRef.current?.focus({ preventScroll: true });
              if (open) closePicker(true);
              else openPicker();
            }}
            className={controlClasses({ invalid, className: 'touch-target cursor-pointer pr-10' })}
          />

          <button
            type="button"
            tabIndex={-1}
            disabled={disabled}
            aria-hidden="true"
            onMouseDown={(event) => {
              // The icon TOGGLES; the text keeps it open. Its own mousedown must not also
              // reach the input's, which would close and immediately reopen.
              event.preventDefault();
              event.stopPropagation();
              if (open) closePicker(true);
              else openPicker();
            }}
            className={cn(
              'absolute right-2 top-1/2 -translate-y-1/2 rounded-md p-1.5 text-ink-faint',
              'hover-hover:hover:text-ink disabled:opacity-50',
            )}
          >
            <CalendarDays className="size-4" />
          </button>

          {open &&
            typeof document !== 'undefined' &&
            createPortal(
              <div id={popoverId}>
                {/* The scrim carries NO click handler: the document-level pointerdown
                    listener above already closes on anything outside the panel, and the
                    scrim is outside it. One dismissal path rather than two that can
                    disagree — and a bare `<div onClick>` is a control only a mouse can
                    reach, which is the shape this arc keeps removing. Escape and Done are
                    the keyboard routes. */}
                {isSheet && <div aria-hidden="true" className="fixed inset-0 z-50 bg-ink/40" />}
                {renderPanel()}
              </div>,
              portalTo ?? document.body,
            )}
        </div>
      )}
    </Field>
  );
}
