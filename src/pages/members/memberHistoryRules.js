import { instantParts } from '../../config/monthEnd.js';
import { EMPTY, compareDates, formatDate, formatZar, parseDate } from '../../lib/format.js';

/**
 * THE HISTORY TAB'S RULES (month end 03.6) -- everything the tab decides that is not drawing, so each can be held by a
 * test of its own. The tab reads `GET /api/members/{id}/history` (`memberHistoryService.js`); the shape is the server's
 * golden answer's (`month-end-examples.json`), and every word below is a decision the owner approved on the mock
 * (`planning/branches/monthly-menu/mockups/record-history-tab.html`, 2026-09-30).
 */

/** A change of status is named as the record's Membership tab names the field it changes. */
export const STATUS_LABEL = 'Account Membership Status';

/**
 * 🗣️ WHO, UNTIL SIGN-IN: *"Not recorded"* -- the owner's call on the mock. The server records every change as `system`
 * until administrators sign in (PA-12), and printing "System" would say the application made a change a person made.
 * Any other actor -- `seed` for the demonstration's past (03.7), a person once there is sign-in -- is printed as the
 * server wrote it.
 */
export const NOT_RECORDED = 'Not recorded';
export const whoOf = (changedBy) => {
  const who = typeof changedBy === 'string' ? changedBy.trim() : '';
  return !who || who.toLowerCase() === 'system' ? NOT_RECORDED : who;
};

/**
 * WHEN, IN JOHANNESBURG. `changedAt` is an instant (`2026-09-29T08:14:00Z`), not a date-only value, so it may pass
 * through `Date` (SCREEN-CONTRACT §4 is about calendar dates) -- and the zone is named, never the browser's: a change
 * made at 01:30 on the 30th in South Africa is the 30th's whatever machine reads it. The date is the record's own form,
 * `yyyy/mm/dd`; the time `HH:mm`. Built once: a formatter costs far more to make than to use.
 */
const WHEN = new Intl.DateTimeFormat('en-ZA', {
  timeZone: 'Africa/Johannesburg',
  year: 'numeric',
  month: '2-digit',
  day: '2-digit',
  hour: '2-digit',
  minute: '2-digit',
  hourCycle: 'h23',
});
export const whenOf = (instant) => {
  const part = instantParts(instant, WHEN);
  return part
    ? { date: `${part.year}/${part.month}/${part.day}`, time: `${part.hour}:${part.minute}` }
    : { date: instant || EMPTY, time: '' };
};

/**
 * A page of the history -- or `null` where the answer is not the server's shape (MV-36): the shared fixture's `[]`, an
 * older server's answer to some other route. The tab shows that as *not available*, never as an empty history.
 */
export const historyPageOf = (body) =>
  body && typeof body === 'object' && Array.isArray(body.content)
  && body.content.every((entry) => entry && typeof entry === 'object')
  && Number.isInteger(body.page) && Number.isInteger(body.size) && Number.isInteger(body.totalPages)
  && Number.isFinite(body.totalElements)
    ? body
    : null;

/** A neighbouring entry that could not be read: whether a save runs on past this page is then not known. */
export const UNKNOWN = Symbol('unknown');

/**
 * Does this row's save run on past the edge of the page? `neighbour` is the `saveId` of the entry just past that edge --
 * `undefined` where there is no page there, {@link UNKNOWN} where it could not be read. `true` and `false` are known;
 * `null` is not, and the tab then says the save *may* run on rather than state what it did not check.
 */
const runsOn = (row, neighbour) => {
  if (!row.saveId || neighbour === undefined) return false;
  if (neighbour === UNKNOWN) return null;
  return neighbour === row.saveId;
};

/**
 * THE ROWS THE SHEET DRAWS -- one a change, newest first, as the server orders them.
 *
 * A change of status is a row; so is a field a save changed. 🔑 THE FIELDS OF ONE SAVE ARE ONE ROW (ME-22): they share
 * a `saveId`, which the server makes for each save, so three fields changed together read as one change -- "3 changes in
 * one save" -- where the pager, which counts ENTRIES, says 3. A save straddling two pages is a row on each, and each says
 * so: `continuedFrom` on the first row of a page, `continuesOn` on its last, from the entries just past the page's edges
 * (`before`, `after`; see `runsOn`).
 */
export const historyRows = (content, { before, after } = {}) => {
  const rows = [];
  const bySave = new Map();
  for (const entry of content) {
    const saveId = entry.kind === 'FIELD' && entry.saveId ? entry.saveId : null;
    const held = saveId && bySave.get(saveId);
    if (held) {
      held.entries.push(entry);
    } else {
      const row = { key: `${entry.kind}:${entry.id}`, saveId, first: entry, entries: [entry] };
      if (saveId) bySave.set(saveId, row);
      rows.push(row);
    }
  }
  const last = rows.length - 1;
  return rows.map((row, index) => ({
    ...row,
    continuedFrom: index === 0 ? runsOn(row, before) : false,
    continuesOn: index === last ? runsOn(row, after) : false,
  }));
};

/** Is this row a save to be named as one: several fields, or a save whose other fields are on another page? */
export const isSaveOfSeveral = (row) =>
  Boolean(row.saveId) && (row.entries.length > 1 || row.continuedFrom !== false || row.continuesOn !== false);

/**
 * A number the server keeps only the last four digits of (03.5): `****4087`. `null` for anything else -- the shape is
 * the server's mask exactly, four asterisks and at most four characters, so a value that merely starts with a star is
 * not read as one.
 */
export const maskedTail = (value) => {
  const match = /^\*{4}(\S{0,4})$/.exec(typeof value === 'string' ? value : '');
  return match ? match[1] : null;
};

/** A workplace as the trail writes it -- `900001 INVENTED MAIN STREET` -- as its name and its R/A number. */
export const workplaceOf = (value) => {
  const match = /^(\d+)\s+(\S.*)$/.exec(typeof value === 'string' ? value.trim() : '');
  return match ? { raNumber: match[1], name: match[2] } : null;
};

const YES_NO = /* @__PURE__ */ Object.freeze({ true: 'Yes', false: 'No', Y: 'Yes', N: 'No' });
/** The record's date fields, named -- never sniffed: a value that merely looks like a date is not one. */
const DATE_FIELDS = new Set(['dateOfBirth', 'shopStewardElectionDate', 'joinDate', 'electionDate']);
/** The two lists the record keeps as SASBO's names joined by commas (V55). */
const LIST_FIELDS = new Set(['becStructures', 'cosatuPositions']);

/**
 * A field's value as the record reads it -- or `null` where there is none, which the tab says as *Not on file*. The
 * trail writes each value as text (`MemberTrail.shown`): a yes/no as `true`/`false` (COSATU's as `Y`/`N`), a date as
 * `1985-06-12`, a list as `PEC,Treasurer`, an amount as `151.00`. Anything else is shown as it was written.
 */
export const valueText = (what, value) => {
  if (value === null || value === undefined || String(value).trim() === '') return null;
  const text = String(value);
  if (/^is[A-Z]/.test(what) && Object.hasOwn(YES_NO, text)) return YES_NO[text];
  if (DATE_FIELDS.has(what)) {
    const shown = formatDate(text);
    return shown === EMPTY ? text : shown;
  }
  if (LIST_FIELDS.has(what)) return text.split(',').map((part) => part.trim()).filter(Boolean).join(', ');
  if (what === 'monthlySubscription') {
    const shown = formatZar(text);
    return shown === EMPTY ? text : shown;
  }
  return text;
};

/**
 * Why a status changed where no code of SASBO's is carried -- one sentence for each of the server's reasons
 * (`statusReasons` in `api-vocabulary.json`, held by `memberHistoryRules.test.js`). Debit Order Deductions' two (the
 * module once called A.C.B.) are its returns loop's (Q27, Q40); where either might sit among SASBO's codes is a proposal
 * (MM58), so no code is shown for them.
 */
export const STATUS_REASON_WORDS = /* @__PURE__ */ Object.freeze({
  SUBS_IN_ARREARS: 'Three unpaid debit orders (Debit Order Deductions)',
  UNPAID_SUSPENDED: 'An unpaid debit order that is not presented again (Debit Order Deductions)',
  DELETION_CODE: 'SASBO’s deletion code',
  SUSPENSION_CODE: 'SASBO’s suspension code',
  REINSTATED: 'Reinstated',
  CAPTURED: 'Set with no code of SASBO’s',
});

/**
 * A change of status's reason: SASBO's code by its NUMBER and its name -- *Code 2 · Resigned Society* (RC7: never the
 * table's id) -- or, with no code, the reason in words. A reason the browser has never heard of is shown as the server
 * wrote it. The reason a member gave under the code (`reasonUnderCode`, 04.5) is the cell's second line, drawn by the tab.
 */
export const statusReasonText = (entry) => {
  if (Number.isInteger(entry.code)) return entry.codeName ? `Code ${entry.code} · ${entry.codeName}` : `Code ${entry.code}`;
  return STATUS_REASON_WORDS[entry.reason] ?? entry.reason ?? null;
};

/** The day a member was captured -- `createdAt`, as `[y, m, d, …]` or as ISO text -- or `null` where it is not known. */
const captureDay = (member) => {
  const at = member?.createdAt;
  if (Array.isArray(at)) return parseDate(at);
  return typeof at === 'string' ? parseDate(at.slice(0, 10)) : null;
};

/**
 * A member older than the history (ME-7): captured before the day the history began, so nothing before that day was
 * kept -- and the tab says *History begins …*. Never claimed where either day is not known.
 */
export const olderThanHistory = (member, historyFrom) => {
  const captured = captureDay(member);
  const began = parseDate(historyFrom);
  return Boolean(captured && began) && compareDates(captured, began) < 0;
};

/** An answer the tab cannot read: the wrong shape, or a server built before the route (MV-36, MR-ROB-31). */
export const NOT_AVAILABLE = 'The history is not available from this server yet.';

/** The approved sentence for a history that did not arrive: a failure is never an empty history. */
export const NO_ANSWER = 'The server did not answer, so this is not an empty history. Try again in a moment.';

/**
 * A failed read, as the tab says it. A 404 that carries no code of the server's is a server with no such route -- not
 * available, and a retry cannot help (MR-ROB-31); `MEMBER_NOT_FOUND` carries its code and is the server's own sentence.
 * The object is what `DataState` reads: `retryable`, `kind` and `status` decide its *Try again* and its *Details*.
 */
export const failureOf = (error) =>
  !error || (error.status === 404 && !error.body?.code) ? { message: NOT_AVAILABLE, retryable: false } : error;

/** The failure's sentence: the server's own where it wrote one (ME-13), else the approved one. */
export const failureMessage = (failure) => {
  if (failure?.message === NOT_AVAILABLE) return NOT_AVAILABLE;
  const said = typeof failure?.body?.message === 'string' ? failure.body.message.trim() : '';
  return said || NO_ANSWER;
};

/** A page that no longer exists: the tab goes back to the first page, and says why in the server's words. */
export const pageGoneSentence = (error) => {
  const said = typeof error?.body?.message === 'string' ? error.body.message.trim() : '';
  return `${said || 'That page of the history does not exist.'} The first page is shown instead.`;
};
