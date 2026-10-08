/**
 * ONE HOME FOR DATES, MONEY AND NUMBERS. `03.2b`, R-SCALE-G.
 *
 * The Card mock already has `fmtDate`, `fmtZar` and `fmtInt` (`mockups/design/core.js:32-38`)
 * and the application has none of them: `Subscriptions.jsx:27` calls
 * `new Date(d).toLocaleDateString()`, which renders in whatever locale the browser
 * happens to be in, and everything else prints raw. P4 rebuilds 27 routes; without one
 * home this becomes 27 answers.
 *
 * ---
 *
 * 🔴 **A DATE-ONLY VALUE NEVER PASSES THROUGH `Date` OR `toISOString` HERE. R-ROB-N.**
 *
 * A member's date of birth is a **calendar date**, not an instant. `new Date("1988-06-15")`
 * parses as **midnight UTC**, and `new Date(...).toISOString().split("T")[0]` converts back
 * through UTC — so in South Africa (UTC+2) any date-only value handled that way can come out
 * a day earlier than it went in.
 *
 * It is not hypothetical in this repository. `CaptureMember.jsx:85` computes today as
 * `new Date().toISOString().split("T")`, so **a member captured between 00:00 and 01:59 SAST
 * is dated yesterday** — CM-38, the Capture Member arc's, fixed in their `01.1`. This module
 * is the reason the next one does not happen: `today()` reads the local calendar fields
 * directly, and every function here works on `{ y, m, d }` or on strings, never on a
 * timestamp.
 *
 * ⚠️ **The `toISOString` scan `03.3` adds is SYNTACTIC**, not semantic — it looks for
 * `toISOString()` followed by `.split("T")`, `.slice(0, 10)` or `.substring(0, 10)` — because
 * "is this value date-only?" is not decidable by a scanner and a bare ban would redden a
 * perfectly correct instant.
 *
 * ---
 *
 * 🔑 **`en-ZA` IS NAMED, NOT INHERITED.** `toLocaleString()` with no locale uses the
 * browser's, so the same subscription renders `R 1 234,56` for one administrator and
 * `R 1,234.56` for another — and a back office comparing two screens has no way to tell
 * which is right. The locale is a decision this application makes once.
 */

/** The short month names this app prints -- also the date picker's month grid (RF: it held a copy). */
export const MONTHS = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];

// 🔑 BUILT ONCE. `toLocaleString(locale, opts)` constructs an `Intl.NumberFormat`
// internally on every call, and constructing the formatter costs far more than using it.
// These two are what every money and count cell in a table calls, so a 50-row list was 50+
// constructions per render -- and alternating between them defeats the engine's
// single-entry cache as well. Identical output by definition: `toLocaleString` is specified
// as exactly this call.
const ZAR = new Intl.NumberFormat('en-ZA', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
const COUNT = new Intl.NumberFormat('en-ZA');

/** What a missing value renders as, everywhere. One em dash, not "N/A", not blank. */
export const EMPTY = '—';

/** @typedef {{ y: number, m: number, d: number }} CalendarDate */

const pad2 = (n) => String(n).padStart(2, '0');

/** Days in a month, 1-indexed, with February resolved against the actual year. */
export function daysInMonth(y, m) {
  return [31, (y % 4 === 0 && y % 100 !== 0) || y % 400 === 0 ? 29 : 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31][
    m - 1
  ];
}

/** Is this a real calendar date? 31 February is not, and neither is month 13. */
export function isValidDate(date) {
  if (!date) return false;
  const { y, m, d } = date;
  if (!Number.isInteger(y) || !Number.isInteger(m) || !Number.isInteger(d)) return false;
  if (m < 1 || m > 12) return false;
  return d >= 1 && d <= daysInMonth(y, m);
}

/**
 * Today, as a LOCAL calendar date.
 *
 * 🔴 The whole point of this function is the three lines it is not: `getFullYear`,
 * `getMonth` and `getDate` read the fields in the user's own zone, where
 * `toISOString().split('T')[0]` reads them in UTC and is a day out for two hours every
 * night in South Africa.
 */
export function today(now = new Date()) {
  return { y: now.getFullYear(), m: now.getMonth() + 1, d: now.getDate() };
}

/**
 * Parse the four typed forms the approved date picker accepts (conventions §15):
 * `15 Jun 1988` · `15/06/1988` · `1988-06-15` · `19880615`. Plus `1988/06/15`, because it
 * is what this application STORES and a field must be able to read back what it wrote.
 *
 * Returns a `CalendarDate` or `null`. Never throws, never guesses: an ambiguous or
 * impossible input is `null`, and the caller says so in plain words under the field
 * rather than silently reverting — which is the mock's explicit contract.
 */
export function parseDate(input) {
  if (input == null) return null;
  // The API serialises a LocalDate as `[y, m, d]` (Jackson, no timestamp module), so every
  // date on a member arrives as an array. Read it as the calendar day it is.
  if (Array.isArray(input)) {
    if (input.length < 3) return null;
    const date = { y: Number(input[0]), m: Number(input[1]), d: Number(input[2]) };
    return isValidDate(date) ? date : null;
  }
  if (typeof input === 'object') return isValidDate(input) ? input : null;

  const text = String(input).trim();
  if (!text) return null;

  let match;

  // 1988-06-15 or 1988/06/15 — year first, unambiguous.
  match = text.match(/^(\d{4})[-/](\d{1,2})[-/](\d{1,2})$/);
  if (match) {
    const date = { y: +match[1], m: +match[2], d: +match[3] };
    return isValidDate(date) ? date : null;
  }

  // 15/06/1988 or 15-06-1988 — day first, which is the South African convention and the
  // one the mock names. Deliberately NOT month-first: guessing between the two is how a
  // birth date silently becomes a different birth date.
  match = text.match(/^(\d{1,2})[-/](\d{1,2})[-/](\d{4})$/);
  if (match) {
    const date = { y: +match[3], m: +match[2], d: +match[1] };
    return isValidDate(date) ? date : null;
  }

  // 19880615 — eight digits, year first.
  match = text.match(/^(\d{4})(\d{2})(\d{2})$/);
  if (match) {
    const date = { y: +match[1], m: +match[2], d: +match[3] };
    return isValidDate(date) ? date : null;
  }

  // 15 Jun 1988 / 15 June 1988 — the display form, read back.
  match = text.match(/^(\d{1,2})\s+([A-Za-z]{3,})\s+(\d{4})$/);
  if (match) {
    const monthIndex = MONTHS.findIndex(
      (name) => name.toLowerCase() === match[2].slice(0, 3).toLowerCase(),
    );
    if (monthIndex === -1) return null;
    const date = { y: +match[3], m: monthIndex + 1, d: +match[1] };
    return isValidDate(date) ? date : null;
  }

  return null;
}

/**
 * The STORED and displayed form: `yyyy/mm/dd`. conventions §15.
 * Anything unparseable renders as the em dash rather than as the raw input, so a broken
 * value is visible as missing instead of leaking a timestamp into a table cell.
 */
export function formatDate(value) {
  const date = parseDate(value);
  if (!date) return EMPTY;
  return `${date.y}/${pad2(date.m)}/${pad2(date.d)}`;
}

/** The long form, for prose and for the picker's readout: `15 Jun 1988`. */
export function formatDateLong(value) {
  const date = parseDate(value);
  if (!date) return EMPTY;
  return `${pad2(date.d)} ${MONTHS[date.m - 1]} ${date.y}`;
}

/** `yyyy-mm-dd` — the wire form, for a request body. Never via `toISOString`. */
export function toWireDate(value) {
  const date = parseDate(value);
  if (!date) return '';
  return `${date.y}-${pad2(date.m)}-${pad2(date.d)}`;
}

/**
 * Whole years between two calendar dates — the picker's age readout.
 * Computed on the fields, so it is not off by one for anybody born after today's date in
 * the current year, and not off by a day for anyone near midnight.
 */
export function yearsBetween(from, to = today()) {
  const start = parseDate(from);
  const end = parseDate(to);
  if (!start || !end) return null;

  let years = end.y - start.y;
  if (end.m < start.m || (end.m === start.m && end.d < start.d)) years -= 1;
  return years;
}

/**
 * Rands, always two decimals, always `en-ZA`. Anything that is not an amount renders as the em dash.
 *
 * 🔴 ONLY A NUMBER, OR A STRING THAT IS ONE, IS AN AMOUNT. `Number(null)`, `Number('')`, `Number('  ')`,
 * `Number([])` and `Number(false)` are all 0, so the obvious `Number.isFinite(Number(value))` shows a member with
 * no subscription as one who pays R 0,00 -- two different facts, and the second is the kind that reaches a finance
 * report. `formatSubscriptionAmount` kept this rule for the capture form; it is the one formatter's now (00.2, F33).
 */
export function formatZar(value) {
  if (typeof value === 'string' ? value.trim() === '' : typeof value !== 'number') return EMPTY;
  const number = Number(value);
  if (!Number.isFinite(number)) return EMPTY;
  return `R ${ZAR.format(number)}`;
}

/** Counts and quantities, `en-ZA` grouping. */
export function formatInt(value) {
  if (value == null || value === '') return EMPTY;
  const number = Number(value);
  if (!Number.isFinite(number)) return EMPTY;
  return COUNT.format(number);
}

/**
 * A fraction as a percentage, two decimals, `en-ZA` -- `0.6337` is `63,37 %`, and `1.042` is `104,20 %`, shown as it
 * is. Only a number is a fraction: `null` -- no staff complement on record -- is the em dash, never `0,00 %`. The
 * space before the sign does not break, so a figure never wraps away from its unit.
 */
export function formatPercent(fraction) {
  return typeof fraction === 'number' && Number.isFinite(fraction) ? `${ZAR.format(fraction * 100)}\u00a0%` : EMPTY;
}

/* ──────────────────────────────────────────────────────────────────────────
   CALENDAR ARITHMETIC — added by `03.3` for the date field.

   🔴 EVERY FUNCTION BELOW THAT TOUCHES `Date` USES THE **LOCAL FIELDS**
   CONSTRUCTOR, `new Date(y, m - 1, d)`, and that distinction is the whole of
   R-ROB-N. `new Date(y, m - 1, d)` builds midnight in the USER'S zone and its
   `getFullYear`/`getMonth`/`getDate` read back the same calendar day, always.
   What is forbidden is `new Date("1988-06-15")` — midnight UTC — and
   `.toISOString().split("T")[0]`, which converts back through UTC. Date is a
   perfectly good calendar calculator; it is a terrible calendar serialiser.
   ────────────────────────────────────────────────────────────────────────── */

/** Negative if a is earlier, 0 if equal, positive if later. Field comparison, no Date. */
export function compareDates(a, b) {
  return a.y - b.y || a.m - b.m || a.d - b.d;
}

/** Same calendar day? Tolerates null on either side, because callers hold optional dates. */
export function sameDate(a, b) {
  return Boolean(a) && Boolean(b) && compareDates(a, b) === 0;
}

/** The same calendar MONTH -- year and month, whatever the day. Either missing is `false`, never a match. */
export function sameMonth(a, b) {
  return Boolean(a) && Boolean(b) && a.y === b.y && a.m === b.m;
}

/** 0 = Sunday. The CLDR first day for South Africa, and what the current app shows. */
export function weekdayOf(date) {
  return new Date(date.y, date.m - 1, date.d).getDay();
}

/** n days later (or earlier). Month and year roll over correctly, including leap days. */
export function shiftDays(date, days) {
  const moved = new Date(date.y, date.m - 1, date.d + days);
  return { y: moved.getFullYear(), m: moved.getMonth() + 1, d: moved.getDate() };
}

/**
 * n months later (or earlier), with the day CLAMPED to the target month's length.
 * 31 January + 1 month is 28 February, not 3 March — a rollover would silently move
 * the user two days past where they aimed.
 */
export function shiftMonths(date, months) {
  const total = date.y * 12 + (date.m - 1) + months;
  const y = Math.floor(total / 12);
  const m = (total % 12) + 1;
  return { y, m, d: Math.min(date.d, daysInMonth(y, m)) };
}

/**
 * "3 months ago" / "in 2 years" / "today" — the picker's readout for a non-birth date.
 * Approximate by design: nobody wants "97 days ago" for a visit last quarter.
 */
export function timeAgo(value, from = today()) {
  const date = parseDate(value);
  const now = parseDate(from);
  if (!date || !now) return '';

  const days = Math.round(
    (new Date(now.y, now.m - 1, now.d) - new Date(date.y, date.m - 1, date.d)) / 86400000,
  );
  if (days === 0) return 'today';
  if (days === 1) return 'yesterday';
  if (days === -1) return 'tomorrow';

  const magnitude = Math.abs(days);
  const months = Math.round(magnitude / 30.44);
  const years = Math.floor(magnitude / 365.25);
  const word =
    magnitude < 31
      ? `${magnitude} days`
      : magnitude < 365
        ? `${months} month${months === 1 ? '' : 's'}`
        : `${years} year${years === 1 ? '' : 's'}`;
  return days > 0 ? `${word} ago` : `in ${word}`;
}

/** "38 years old" / "under a year old" / "not born yet" — the readout for a birth date. */
export function ageInWords(value, from = today()) {
  const years = yearsBetween(value, from);
  if (years === null) return '';
  if (years < 0) return 'not born yet';
  if (years === 0) return 'under a year old';
  return `${years} year${years === 1 ? '' : 's'} old`;
}
