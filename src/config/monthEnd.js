/**
 * Month end's vocabulary as the browser holds it -- the codes it ACTS ON and the words it shows for the server's
 * words. Held to the server's own list, `api-vocabulary.json` under `monthEnd`, by `monthEnd.test.js`: a word the
 * server adds reddens that file instead of reaching a screen as a word nobody wrote a sentence for.
 *
 * THE SENTENCE A PERSON READS ON A REFUSAL IS THE SERVER'S `message` (ME-13). The browser keeps only the codes it
 * branches on, and shows a code it has never heard of as the server wrote it.
 *
 * ⚖️ Every frozen table is marked `@__PURE__` (01.3): freezing a fresh object has no effect a bundler must keep, but
 * Rollup cannot prove it for a table built from another's members (`MEMBER_STATUS_KIND`), so without the mark a screen
 * importing one word of this file shipped tables it never reads.
 */
import { EMPTY, formatInt } from '../lib/format.js';

/**
 * A save naming a workplace that is not on record here (01.3) -- said at the Workplace field, the form kept as typed.
 * A constant of its own as well as a member of the table below, so the two member screens that branch on it import
 * one word rather than the table (the bundle's ceiling); `monthEnd.test.js` holds the table to the server's list.
 */
export const WORKPLACE_UNKNOWN = 'WORKPLACE_UNKNOWN';

/**
 * The four refusals of a change of status (03.1) that the *Change status* dialog branches on (03.3) -- constants of their
 * own as well as members of the table below, for `WORKPLACE_UNKNOWN`'s reason: the dialog imports four words, not the
 * table. The member moved since the screen read it -- re-read, and say so; the member is gone; the code chosen left
 * SASBO's list since the dialog loaded it -- say so at the field, and load the list again.
 */
export const STATUS_CHANGED_SINCE = 'STATUS_CHANGED_SINCE';
export const MEMBER_NOT_FOUND = 'MEMBER_NOT_FOUND';
export const DELETION_CODE_UNKNOWN = 'DELETION_CODE_UNKNOWN';
export const SUSPENSION_CODE_UNKNOWN = 'SUSPENSION_CODE_UNKNOWN';

/**
 * A page of a member's history that does not exist (03.6) -- the record's History tab goes back to the first page and
 * says so, and offers no *Try again*: asking for the same page again gets the same answer.
 */
export const HISTORY_PAGE_INVALID = 'HISTORY_PAGE_INVALID';

/**
 * A visit the server would not record (A.1) -- no day, a day still to come, a count below nought, a time out before the
 * time in, a name past its column. `details.field` names which, and the Location Visit form says it AT that field, the
 * form kept as typed. A page of a workplace's visits that is gone is `WORKPLACE_PAGE_INVALID`, the workplaces' own.
 */
export const VISIT_INVALID = 'VISIT_INVALID';
export const WORKPLACE_PAGE_INVALID = 'WORKPLACE_PAGE_INVALID';

/**
 * An edit of a member's record made after somebody else saved it (V68) -- the record says someone else changed it,
 * keeps the form as typed, and offers to read the record again. Without it the later of two edits silently undid the
 * earlier one.
 */
export const MEMBER_CHANGED_SINCE = 'MEMBER_CHANGED_SINCE';

/**
 * October build. The organisers' batch print's numbers could not be read (the screen reads them first, so this is the
 * server's word on what slipped past); a branch outside the caller's region or area -- its members are not listed, and
 * a visit to it is not captured; and the visit drafts' three: saved since in another tab (open it again), submitted
 * already (read-only now), and not one of yours.
 */
export const RA_NUMBERS_INVALID = 'RA_NUMBERS_INVALID';
export const WORKPLACE_OUT_OF_SCOPE = 'WORKPLACE_OUT_OF_SCOPE';
export const VISIT_DRAFT_STALE = 'VISIT_DRAFT_STALE';
export const VISIT_ALREADY_SUBMITTED = 'VISIT_ALREADY_SUBMITTED';
export const VISIT_DRAFT_NOT_FOUND = 'VISIT_DRAFT_NOT_FOUND';

/** The refusal codes the screens branch on (ME-13) -- each one the server publishes. */
export const MONTH_END_REFUSAL = /* @__PURE__ */ Object.freeze({
  REQUEST_MALFORMED: 'REQUEST_MALFORMED',
  WORKPLACE_PAGE_INVALID,
  WORKPLACE_UNKNOWN,
  STATUS_CHANGED_SINCE,
  MEMBER_NOT_FOUND,
  DELETION_CODE_UNKNOWN,
  SUSPENSION_CODE_UNKNOWN,
  HISTORY_PAGE_INVALID,
  VISIT_INVALID,
  MEMBER_CHANGED_SINCE,
  RA_NUMBERS_INVALID,
  WORKPLACE_OUT_OF_SCOPE,
  VISIT_DRAFT_STALE,
  VISIT_ALREADY_SUBMITTED,
  VISIT_DRAFT_NOT_FOUND,
});

/**
 * What a status IS (03.2): leaving, suspended, or neither. **A figure reads a status's kind, never its spelling** --
 * no status is renamed before SASBO's table, and a figure that counted by spelling would break the day one is.
 */
export const STATUS_KIND = /* @__PURE__ */ Object.freeze({
  LEAVING: 'LEAVING',
  SUSPENDED: 'SUSPENDED',
  NEITHER: 'NEITHER',
});

/** The six statuses and the kind of each, in the server's order (`memberStatuses`). */
export const MEMBER_STATUS_KIND = /* @__PURE__ */ Object.freeze({
  Active: STATUS_KIND.NEITHER,
  Pending: STATUS_KIND.NEITHER,
  Suspended: STATUS_KIND.SUSPENDED,
  Resigned: STATUS_KIND.LEAVING,
  Retired: STATUS_KIND.LEAVING,
  Terminated: STATUS_KIND.LEAVING,
});

/** A status's kind -- `null` for a status the browser has never heard of, which no figure may guess at. */
export const statusKind = (status) => MEMBER_STATUS_KIND[status] ?? null;

/** The reasons a person's change of status carries (03.1). ACB's two, and capture's, are never sent from a screen. */
export const STATUS_REASON = /* @__PURE__ */ Object.freeze({
  DELETION_CODE: 'DELETION_CODE',
  SUSPENSION_CODE: 'SUSPENSION_CODE',
  REINSTATED: 'REINSTATED',
});

/**
 * The reason a change INTO a status carries, as the server judges it -- by the status's kind and no finer, until
 * SASBO's table (MM49): into a leaving status SASBO's deletion code, into Suspended its suspension code, into any
 * other a reinstatement. `null` for a status the browser does not know.
 */
export const reasonFor = (toStatus) => {
  switch (statusKind(toStatus)) {
    case STATUS_KIND.LEAVING: return STATUS_REASON.DELETION_CODE;
    case STATUS_KIND.SUSPENDED: return STATUS_REASON.SUSPENSION_CODE;
    case STATUS_KIND.NEITHER: return STATUS_REASON.REINSTATED;
    default: return null;
  }
};

/**
 * SASBO's deletion codes as a person chooses one (03.3): each numbered code with its number and name, and the five
 * reasons SASBO keeps under code 2 -- *Resigned Society* -- which have no number of their own. `rows` is the list as
 * the server answers it (`{ code, name, under? }`); a row with neither a number nor a code above it is left out
 * rather than offered as a code nobody can send.
 */
export const deletionCodeOptions = (rows) => {
  const numbered = rows.filter((row) => Number.isInteger(row.code));
  return numbered.map((row) => ({
    code: row.code,
    label: `${row.code} · ${row.name}`,
    under: rows.filter((sub) => !Number.isInteger(sub.code) && sub.under === row.code).map((sub) => sub.name),
  }));
};

/** SASBO's suspension codes as a person chooses one: each with its number and name. */
export const suspensionCodeOptions = (rows) => rows
  .filter((row) => Number.isInteger(row.code))
  .map((row) => ({ code: row.code, label: `${row.code} · ${row.name}` }));

/**
 * Why one of SASBO's lists -- or the workplaces -- has nothing to show here (ME-19): one sentence for each of the
 * server's four words, so that a screen says why instead of showing an empty control.
 */
export const LIST_EMPTY_REASON = /* @__PURE__ */ Object.freeze({
  NOT_CONFIRMED: 'Not confirmed by SASBO yet.',
  NOT_LOADED: 'Not loaded on this demonstration yet.',
  FAILED: 'Could not be loaded.',
  EMPTY: 'Nothing is on it.',
});

/** What the deployment's switch says of SASBO's workplaces (MD62). */
export const WORKPLACES_SERVED = /* @__PURE__ */ Object.freeze({
  NONE: 'NONE',
  NAMES: 'NAMES',
  ALL: 'ALL',
});

const MONTHS = ['January', 'February', 'March', 'April', 'May', 'June', 'July', 'August', 'September', 'October',
  'November', 'December'];

/**
 * An ISO date as a person writes it -- `2026-08-31` is `31 August 2026`; anything else is passed through, a month or a
 * day that is none among it: `2026-13-01` read "1 undefined 2026" until 2 Oct (RC17).
 */
export const dayLabel = (iso) => {
  const match = /^(\d{4})-(\d{2})-(\d{2})$/.exec(iso ?? '');
  const month = match ? MONTHS[Number(match[2]) - 1] : undefined;
  const day = match ? Number(match[3]) : 0;
  return month && day >= 1 && day <= 31 ? `${day} ${month} ${match[1]}` : iso ?? '';
};

/**
 * An instant's parts as `formatter` reads them -- `{ day, month, year, hour, minute, … }` -- or `null` for anything that
 * is not an instant's text. The one guard for every screen that says when something happened, each in its own words
 * from the same parts: a take's `instantLabel`, the history's `whenOf`.
 */
export const instantParts = (iso, formatter) => {
  const moment = typeof iso === 'string' ? new Date(iso) : null;
  if (!moment || Number.isNaN(moment.getTime())) return null;
  return Object.fromEntries(formatter.formatToParts(moment).map(({ type, value }) => [type, value]));
};

/**
 * The line under anything grouped by one of SASBO's lists (MD28): where it was read from, and whether SASBO has
 * confirmed it -- `As printed on 31 August 2026 — not yet confirmed by SASBO`.
 */
export const listProvenance = (answer) => {
  if (!answer?.printedOn) return '';
  const printed = `As printed on ${dayLabel(answer.printedOn)}`;
  return answer.confirmedOn
    ? `${printed} — confirmed by SASBO on ${dayLabel(answer.confirmedOn)}`
    : `${printed} — not yet confirmed by SASBO`;
};

/**
 * The same line for SASBO's lists TOGETHER (02.1) -- from the lists' status read (`GET /api/reference/status`): the
 * line every one of them, the workplaces' among them, gives alike. Where any of them gives another -- another day
 * printed, one confirmed and one not -- or the read is not a status read, there is no one line to say, and it is `''`.
 */
export const listsProvenance = (status) => {
  const lines = new Set([status?.workplaces, ...(Array.isArray(status?.lists) ? status.lists : [])].map(listProvenance));
  return lines.size === 1 ? [...lines][0] : '';
};

/** The sentence for an empty list's reason; a word the browser has never heard of is shown as the server wrote it. */
export const emptyReasonSentence = (reason) => LIST_EMPTY_REASON[reason] ?? reason ?? '';

/**
 * A list answer's rows -- or `null` where the answer is not the shape a list's is, which a screen shows as
 * *not available* and never as an empty list (MV-36): the shared test fixture answers every read with `[]`.
 */
export const listRows = (answer) =>
  answer && typeof answer === 'object' && !Array.isArray(answer) && Array.isArray(answer.rows) ? answer.rows : null;

/**
 * A workplace's committee in one line -- its code and its name, each trimmed, either absent: `Q01, INVENTED` -- or the
 * absence mark. Trimmed so a padded value cannot put a space before the comma (MO24's "Q01 ,"). Written once, for the
 * member's Workplace field and a workplace's visits, which had drifted apart.
 */
export const committeeLine = (workplace) =>
  [workplace?.committee, workplace?.committeeName].map((part) => String(part ?? '').trim()).filter(Boolean).join(', ')
  || EMPTY;

/* ── The figures (02.2, 02.3) ────────────────────────────────────────────────────────────────────── */

/**
 * What the figures are grouped by: the server's six, in its order (`summaryGroupings`), each as a person reads it --
 * which is also how the sheet's first column is headed. Held to the server's list by `monthEnd.test.js`.
 */
export const SUMMARY_GROUPING = /* @__PURE__ */ Object.freeze({
  group: 'Employer group',
  employer: 'Employer',
  tableCode: 'Table code',
  area: 'Area',
  region: 'Region',
  committee: 'Committee',
});

/**
 * `2026-09` as a person writes it -- `September 2026`; anything else is passed through, a month that is none among it:
 * the address can carry `?month=2026-13`, which read "undefined 2026" on the Month end page until 2 Oct (RC17).
 */
export const monthLabel = (yearMonth) => {
  const match = /^(\d{4})-(\d{2})$/.exec(yearMonth ?? '');
  const month = match ? MONTHS[Number(match[2]) - 1] : undefined;
  return month ? `${month} ${match[1]}` : yearMonth ?? '';
};

/**
 * The months the figures may be asked for: the SERVER'S month and the twelve before it, newest first (MD55) --
 * counted back from what the server answered, never from the browser's clock.
 */
export const monthsTo = (yearMonth) => {
  const [year, month] = yearMonth.split('-').map(Number);
  return Array.from({ length: 13 }, (_, back) => {
    const index = year * 12 + month - 1 - back;
    const value = `${Math.floor(index / 12)}-${String((index % 12) + 1).padStart(2, '0')}`;
    return { value, label: monthLabel(value) };
  });
};

/**
 * The summary's answer -- or `null` where it is not the shape the server's is (MV-36): no month, no totals, no rows.
 * The server answers at least one row in every grouping, *No workplace on record*, so an answer with none is not one.
 * The shared test fixture answers every read with `[]`, and a screen shows that as *not available*, never a crash.
 */
export const summaryOf = (answer) =>
  answer && typeof answer.month === 'string' && answer.totals && Array.isArray(answer.rows) && answer.rows.length
    ? answer
    : null;

/**
 * The Area control's choices (MD35): all of SASBO, a region, or an area -- `region:NORTH`, `area:A` -- from SASBO's
 * lists as the reference routes answer them.
 */
export const placeOptions = (regions, areas) => [
  { value: '', label: 'All areas' },
  ...regions.map((region) => ({ value: `region:${region.code}`, label: `${region.name} region` })),
  ...areas.map((area) => ({ value: `area:${area.letter}`, label: `Area ${area.letter}` })),
];

/** The request's part for a place chosen: `{ region: 'NORTH' }`, `{ area: 'A' }`, or nothing for all areas. */
export const placeQuery = (place) => {
  const [kind, code] = place.split(':');
  return code ? { [kind]: code } : {};
};

/** Why the figures are not grouped where the switch does not say *all* (MD62): one line beneath the sheet. */
export const UNGROUPED_BECAUSE = /* @__PURE__ */ Object.freeze({
  [WORKPLACES_SERVED.NONE]: "SASBO's workplaces are not loaded on this demonstration yet.",
  [WORKPLACES_SERVED.NAMES]: "SASBO's staff numbers are not loaded on this demonstration yet, so the figures are not grouped.",
});

const membersHave = (count) => `${formatInt(count)} member${count === 1 ? ' has' : 's have'}`;

/**
 * What the figures could not place, in one line (ME-17) -- a status nobody knows, by its value; no workplace; a
 * workplace with no table code -- or `''` when there is nothing to say.
 */
export const unplacedSentence = (unplaced) => {
  const statuses = unplaced?.statuses ?? [];
  const unknown = statuses.reduce((sum, row) => sum + row.members, 0);
  return [
    unknown && `${membersHave(unknown)} a status this system does not know: ${statuses.map((row) => `"${row.status}"`).join(', ')}`,
    unplaced?.noWorkplace && `${membersHave(unplaced.noWorkplace)} no workplace on record`,
    unplaced?.noTableCode && `${membersHave(unplaced.noTableCode)} a workplace with no table code`,
  ].filter(Boolean).join(' · ');
};
