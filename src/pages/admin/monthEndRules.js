import { CRITERION_WIDE } from '../../components/common/Criteria.jsx';
import { instantParts, monthLabel } from '../../config/monthEnd.js';
import { EMPTY, formatInt, formatPercent, formatZar } from '../../lib/format.js';

/**
 * Month end's small rules, named once -- the Month end page (`04.1`), a statistic's screen (`04.3`-`04.8`) and the
 * statistics pack (`04.10`) read them. The statistics' names and the codes the screens act on are held to the
 * SERVER'S by `monthEndRules.test.js`: a statistic the server adds, or a refusal it stops publishing, reddens there
 * rather than reaching a screen as a row nobody named.
 *
 * ⚖️ Every frozen table is `@__PURE__`, as `config/monthEnd.js`'s are: a screen importing one word ships no table it
 * never reads.
 */

/**
 * The seven statistics, in the order SASBO's reports run: the route's own name, the plain name a person reads, and --
 * until SASBO chooses the plain names at the table (MD8, PA-13) -- the legacy title as the second line: the step of
 * SASBO's *Month End Run* each one answers, as that screen names it.
 */
export const STATISTICS = /* @__PURE__ */ Object.freeze([
  { key: 'new-and-deleted', name: 'New and deleted members', legacy: 'Print Membership Summary' },
  { key: 'membership', name: 'Membership by group and by table code', legacy: 'Member Listing by Group · Bank Branch member count' },
  {
    key: 'leavers',
    name: 'Why members left',
    legacy: 'Print Del Summary by Area · Print Members Joined/Deleted · Print Deletions by Month · Extract members resigned society',
  },
  { key: 'branches', name: 'The branches and their penetration', legacy: 'Print Bank Master Comp' },
  {
    key: 'recruitment',
    name: 'Recruitment',
    legacy: 'Print Recruiting Analyst · Print Recruitment by Rep with Core · Print Recruitment by Group · Print Website Recruitment',
  },
  {
    key: 'representatives',
    name: 'Representatives and committee members',
    legacy: 'Print Reps by Area and T/C · Print Comm Mem by Area and T/C · Print Memnet by Area and T/C',
  },
  // 04.9 (A.1's visits, MO22): the branches longest unvisited first; the manager's details wait for sign-in.
  { key: 'visits', name: 'The branches due a visit', legacy: 'Print Branches requiring visits' },
]);

/** A statistic by its route name -- `null` for a name the server has no statistic for, which is a not-found state. */
export const statisticNamed = (key) => STATISTICS.find((statistic) => statistic.key === key) ?? null;

/** Where a statistic's screen is, for the month chosen: `/admin/monthly-menu/membership?month=2026-08`. */
export const statisticHref = (key, month) =>
  `/admin/monthly-menu/${key}${month ? `?month=${encodeURIComponent(month)}` : ''}`;

/**
 * The four LISTINGS -- each names members, so each is built refusing until sign-in exists (MD4), and the page says
 * so without asking the server: nothing is requested, and nothing is produced.
 */
export const LISTINGS = /* @__PURE__ */ Object.freeze([
  { key: 'new-members', name: "The month's new members", legacy: 'Print Member Roll (No Adding) — the members enrolled' },
  { key: 'deleted-members', name: "The month's deleted members", legacy: 'Print Member Roll (No Adding) — the members deleted' },
  { key: 'recruited', name: 'The members a recruiter signed up', legacy: 'Print Recruiting Analyst' },
  { key: 'representatives', name: 'The elected representatives', legacy: 'Print Shop Steward List' },
]);

/** Why a listing produces nothing -- the sentence screens.md §0.3 gives the listing's place. */
export const LISTING_WAITS = 'This waits until administrators sign in to the system. Nothing was produced.';

/**
 * Why *Take the month* is there and cannot be pressed -- the server's own reason for refusing it (`IDENTITY_REQUIRED`),
 * said before anyone asks, as A.C.B.'s *Approve* says its own.
 */
export const TAKE_WAITS = 'Taking a month freezes its figures for good, so it waits until administrators sign in to this system.';

/**
 * Why *Take the month again* is there and cannot be pressed (04.11, MM51): SASBO's answer is that a correction overwrites
 * the wrong figures, and the take that replaces them refuses every caller until sign-in, as the first take does.
 */
export const TAKE_AGAIN_WAITS = 'Taking a month again replaces its figures with the month as it stands now, and keeps the '
  + 'first take for the record. It waits until administrators sign in to this system.';

/** The statistics pack, as the page names it; the second line is what it replaces. */
export const PACK = /* @__PURE__ */ Object.freeze({
  name: 'The statistics pack',
  legacy: 'The Secretariat’s and the Management Committee’s workbooks, typed by hand until now',
});

/** A pack's states, as the server writes them (`MonthEndPackDto.status`). */
export const PACK_STATUS = /* @__PURE__ */ Object.freeze({
  PREPARING: 'PREPARING',
  READY: 'READY',
  FAILED: 'FAILED',
});

/** The refusals these screens act on -- each one the server publishes (ME-13); the sentence is always the server's. */
export const MONTH_END_P4_REFUSAL = /* @__PURE__ */ Object.freeze({
  MONTH_NOT_TAKEN: 'MONTH_NOT_TAKEN',
  PACK_BUSY: 'PACK_BUSY',
  PACK_NOT_FOUND: 'PACK_NOT_FOUND',
});

/** A pack's workbooks as a person names them; a workbook the browser has never heard of is shown as the server wrote it. */
const WORKBOOK = /* @__PURE__ */ Object.freeze({
  statistics: 'the statistics workbook',
  monthly: 'the monthly workbook',
});
export const workbookLabel = (workbook) => WORKBOOK[workbook] ?? workbook;

/* ── the month ───────────────────────────────────────────────────────────────────────────────────── */

/**
 * The month control's place in a criteria row. Wider than a search's: a month here is named with what it is --
 * *September 2026 — open* -- and a phone sets a field's text at 16px (the Sixteen-Pixel Rule), where the 200px of
 * `CRITERION_WIDE` cut it to *September 2026 — op*. Measured at 390 by the width matrix.
 */
export const MONTH_CRITERION = CRITERION_WIDE.replace('basis-[200px]', 'basis-[16rem]');

/** A month as the server writes one -- `2026-08` -- and nothing else is read as a month. */
export const isYearMonth = (value) => typeof value === 'string' && /^\d{4}-(0[1-9]|1[0-2])$/.test(value);

/**
 * The months' answer -- or `null` where it is not the server's shape (MV-36): the shared test fixture answers every
 * read with `[]`, and a screen shows that as *not available*, never as a month nobody named.
 */
export const monthsOf = (answer) =>
  answer && typeof answer === 'object' && isYearMonth(answer.openMonth) && Array.isArray(answer.taken) ? answer : null;

/** The months taken, newest first -- sorted here, so the screen does not lean on the answer's order. */
const takenNewestFirst = (months) =>
  months.taken.filter((taken) => isYearMonth(taken?.month)).sort((a, b) => b.month.localeCompare(a.month));

/**
 * The month a screen opens on when none was asked for: the newest month taken -- the month end a person comes here
 * to read -- or, before any month is taken, the open month. Both are the server's words (MD55).
 */
export const defaultMonth = (months) => takenNewestFirst(months)[0]?.month ?? months.openMonth;

/**
 * The months a person may choose: the open month, read live, then every month taken, newest first. A month asked for
 * in the address that is neither is kept as a choice too -- by its name alone, claiming nothing -- so the control shows
 * what was asked, and the server says what it is.
 */
export const monthChoices = (months, asked = '') => {
  const choices = [
    { value: months.openMonth, label: `${monthLabel(months.openMonth)} — open` },
    ...takenNewestFirst(months).map((taken) => ({ value: taken.month, label: `${monthLabel(taken.month)} — taken` })),
  ];
  return asked && !choices.some((choice) => choice.value === asked)
    ? [...choices, { value: asked, label: monthLabel(asked) }]
    : choices;
};

/**
 * What a month is, by the server's months: `OPEN`; the taken month's record (`TAKEN`, with when and by whom); a month
 * after the open one, `NOT_BEGUN`; a month before it that is not among the taken, `NOT_TAKEN`; and `null` for what is
 * not a month at all -- the server says what that is.
 */
export const monthStateOf = (months, month) => {
  if (month === months.openMonth) return { month, state: 'OPEN', takenAt: null, takenBy: null };
  const taken = takenNewestFirst(months).find((record) => record.month === month);
  if (taken) return taken;
  if (!isYearMonth(month)) return null;
  return { month, state: month > months.openMonth ? 'NOT_BEGUN' : 'NOT_TAKEN', takenAt: null, takenBy: null };
};

const INSTANT = new Intl.DateTimeFormat('en-ZA', {
  timeZone: 'Africa/Johannesburg',
  day: 'numeric',
  month: 'long',
  year: 'numeric',
  hour: '2-digit',
  minute: '2-digit',
  hourCycle: 'h23',
});

/**
 * An instant as a person reads it, in Johannesburg: `2026-09-29T08:00:00Z` is `29 September 2026 at 10:00`.
 * An instant is not a date-only value (SCREEN-CONTRACT §4) -- it names a moment, and the zone is said, not assumed.
 * Anything unreadable is shown as it came.
 */
export const instantLabel = (iso) => {
  const part = instantParts(iso, INSTANT);
  return part ? `${part.day} ${part.month} ${part.year} at ${part.hour}:${part.minute}` : iso ?? '';
};

/**
 * A taken month's record in one sentence: when, and by whom -- and, for a month taken again (04.11), that it was, with
 * when it was first taken and by whom. Its figures are the newest take's.
 */
export const takenSentence = ({ takenAt, takenBy, firstTakenAt, firstTakenBy }) => {
  const by = (who) => (who ? ` by ${who}` : '');
  return firstTakenAt
    ? `Taken again on ${instantLabel(takenAt)}${by(takenBy)}; first taken on ${instantLabel(firstTakenAt)}${by(firstTakenBy)}.`
    : `Taken on ${instantLabel(takenAt)}${by(takenBy)}.`;
};

/* ── a statistic ─────────────────────────────────────────────────────────────────────────────────── */

/**
 * A statistic's answer -- or `null` where it is not the server's shape (MV-36): no month, or sheets that are not
 * sheets. A screen shows that as *not available*, never as an empty statistic.
 */
export const statisticOf = (answer) =>
  answer && typeof answer === 'object' && isYearMonth(answer.month) && Array.isArray(answer.sheets)
  && answer.sheets.every((sheet) => Array.isArray(sheet?.columns) && Array.isArray(sheet?.rows))
    ? answer
    : null;

/**
 * What a failed read is, to a person. A server built before these routes answers 404 or 500 with no code of this
 * arc's, and an answer of the wrong shape is no answer: each is *not available*, and trying again changes nothing.
 * A refusal of the arc's -- `MONTH_NOT_TAKEN` is a 404 too -- is said in the server's own sentence (ME-13), and
 * anything else as it came.
 */
export const failureOf = (error, notAvailable) =>
  !error || (!error.body?.code && (error.status === 404 || error.status === 500))
    ? { message: notAvailable, retryable: false }
    : error;

/** The sheets' first column says what each row is -- the server's sheet names it by its key, and this reads it. */
const ROW_IS = /* @__PURE__ */ Object.freeze({
  'month-joined': 'Area',
  'month-left': 'Area',
  'year-joined': 'Area',
  'year-left': 'Area',
  'joined-deleted': 'Table code',
  resigned: 'Employer',
  representatives: 'Table code',
  'committee-members': 'Table code',
  'memnet-members': 'Table code',
  'suspended-by-code': 'Suspension code',
  'due-a-visit': 'Branch',
});
const ROWS_BY = /* @__PURE__ */ Object.freeze({
  area: 'Area',
  'table-code': 'Table code',
  group: 'Group',
  employer: 'Employer',
  recruiter: 'Recruiter',
  channel: 'Channel',
});

/**
 * The rows a statistic's sheet shows at once -- the workplaces' register's page. Most sheets are a table code or an area
 * a row and never reach it; 04.9's is a branch a row, every branch of SASBO's at `all`.
 */
export const SHEET_PAGE = 50;

/**
 * The head of a sheet's first column: what each of its rows is. The sheet's shape carries no name for it, so it is read
 * from the sheet's key -- `by-area` is by area -- and a sheet this browser has never seen is headed *Row*, which
 * `monthEndRules.test.js` forbids for every sheet the server answers today.
 */
export const rowHeader = (sheetKey) =>
  ROW_IS[sheetKey] ?? ROWS_BY[/(?:^|-)by-(.+)$/.exec(sheetKey ?? '')?.[1]] ?? 'Row';

/**
 * One figure as a person reads it, by its column's kind: a count with the strip's grouping, money in rand, a share as
 * a percentage. `null` is a cell with no meaning -- a penetration with no complement -- and reads as the absence mark,
 * never a nought. A kind the browser has never heard of is shown as the server wrote it.
 */
export const cellText = (kind, value) => {
  switch (kind) {
    case 'COUNT': return formatInt(value);
    case 'RAND': return formatZar(value);
    case 'SHARE': return formatPercent(value);
    default: return value == null ? EMPTY : String(value);
  }
};

/**
 * Which of the notes says why a row shows no number -- the one naming the row *withheld* beside a row of fewer than
 * five (rule C). Its index, or `-1` where no note says so; the screen then says nothing it was not told.
 */
export const ruleCNote = (notes) => (Array.isArray(notes) ? notes.findIndex((note) => /\bwithheld\b/i.test(note)) : -1);
