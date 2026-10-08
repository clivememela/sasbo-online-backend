import { formatDateLong, formatZar } from '../../lib/format.js';

/**
 * The A.C.B. run's vocabulary and the Runs screen's small rules, named once.
 *
 * The run states, exclusion reasons and refusal codes are the SERVER'S -- published in
 * `api-vocabulary.json` under `acb`, and held to that file by `acbRunRules.test.js`, so a state the
 * server adds reddens a test here instead of rendering as an unexplained word on screen.
 */

export const RUN_STATUS = Object.freeze({
  PREPARING: 'PREPARING',
  PREPARED: 'PREPARED',
  APPROVED: 'APPROVED',
  SUBMITTED: 'SUBMITTED',
  RETURNED: 'RETURNED',
  CLOSED: 'CLOSED',
  DECLINED: 'DECLINED',
  FAILED: 'FAILED',
  CANCELLED: 'CANCELLED',
});

/** What each state is called on screen. */
export const RUN_STATUS_LABEL = Object.freeze({
  PREPARING: 'Preparing',
  PREPARED: 'Prepared',
  APPROVED: 'Approved',
  SUBMITTED: 'Submitted',
  RETURNED: 'Returned',
  CLOSED: 'Closed',
  DECLINED: 'Declined by the bank',
  FAILED: 'Failed',
  CANCELLED: 'Cancelled',
});

/** The states a run can be cancelled from: a proposal, before anything reached the bank. */
export const CANCELLABLE = new Set([RUN_STATUS.PREPARED, RUN_STATUS.APPROVED]);

/** Why a member is not debited, as an administrator reads it. */
export const EXCLUSION_LABEL = Object.freeze({
  NOT_DEBIT_ORDER: 'Not a debit-order member',
  STATUS_NOT_ACTIVE: 'Not Active',
  NO_BANK_ON_RECORD: 'Bank name matches no bank on record',
  NO_SUBSCRIPTION_ROW: 'SASBO does not collect at this bank',
  DAY_OUT_OF_WINDOW: 'Debit day outside the 20th to the 31st',
  ACCOUNT_SHAPE_INVALID: 'Account number cannot go in the file',
  // General on purpose: the server files a missing membership number, a missing surname and -- since 00.5 -- a
  // missing or unknown account type all under this reason, and the row's detail says which.
  SNAPSHOT_INCOMPLETE: 'A detail the file needs is missing or invalid',
});

/** Why the bank declined a file, from the server's short list (D29, SD3-H). */
export const DECLINE_REASON_LABEL = Object.freeze({
  WRONG_DEBIT_DATE: 'A wrong debit date',
  LAYOUT: 'The file layout',
  OTHER: 'Another reason',
});

/**
 * What the prepare dialog says about a month whose last file the bank declined for a wrong debit date,
 * or `null`. A warning, never a refusal: the bank's complaint may be about a day that is no public
 * holiday -- but if the holiday calendar has not changed since, the new file's dates are the old
 * file's dates (SD3-H). `runs` is the list page the screen holds, newest first.
 */
export const declinedMonthWarning = (runs, month) => {
  const declined = (runs ?? []).find((run) => run.actionMonth === month && run.status === RUN_STATUS.DECLINED);
  if (!declined || declined.declinedReason !== 'WRONG_DEBIT_DATE') return null;
  return declined.calendarAgrees === false
    ? `The bank declined the ${monthLabel(month)} file for a wrong debit date. The holiday calendar has changed since, so this run's dates can differ.`
    : `The bank declined the ${monthLabel(month)} file for a wrong debit date, and the holiday calendar has not changed since — this run would carry the same dates. Check the date the bank named first.`;
};

export const REFUSAL = Object.freeze({
  RUN_ALREADY_EXISTS: 'RUN_ALREADY_EXISTS',
  RUN_VERSION_CONFLICT: 'RUN_VERSION_CONFLICT',
  RUN_NOT_IN_STATE: 'RUN_NOT_IN_STATE',
  UNPAID_ALREADY_RECORDED: 'UNPAID_ALREADY_RECORDED',
  PERMISSION_NOTE_REQUIRED: 'PERMISSION_NOTE_REQUIRED',
  LETTER_NOT_HELD: 'LETTER_NOT_HELD',
});

/** The bank's reasons for an unpaid, in the words of the union's own letters -- the server's list. */
export const UNPAID_REASON_LABEL = Object.freeze({
  NOT_PROVIDED_FOR: 'Not Provided For',
  PAYMENT_STOPPED: 'Payment Stopped',
  ACCOUNT_FROZEN: 'Account Frozen',
  INVALID_ACCOUNT_NUMBER: 'Invalid Account Number',
  ACCOUNT_CLOSED: 'Account Closed',
  NO_AUTHORITY_TO_DEBIT: 'No Authority To Debit',
  AUTHORISATION_CANCELLED: 'Authorisation Cancelled',
  OTHER: 'Unrecognised bank code',
});

/** The reasons whose cure is the member's banking: the sheet and *Edit Details* stay for these (`04.1`). */
export const BANKING_REASONS = new Set(['INVALID_ACCOUNT_NUMBER', 'ACCOUNT_CLOSED']);

/** The choice SASBO makes on each unpaid (Q7, Q9). */
export const DECISION_LABEL = Object.freeze({
  RE_PRESENT: 'Present again',
  SUSPEND: 'Suspend',
});

/** Where an unpaid's letter is. `QUEUED` is a letter that is waiting -- for an address, or for the send. */
/** A letter's statuses -- the server's `letterStatuses`, named once so no screen spells one twice. */
export const LETTER_STATUS = Object.freeze({
  QUEUED: 'QUEUED',
  RENDERED: 'RENDERED',
  SENT: 'SENT',
  FAILED: 'FAILED',
  HANDLED: 'HANDLED',
});

export const LETTER_STATUS_LABEL = Object.freeze({
  QUEUED: 'Held',
  RENDERED: 'Waiting to send',
  SENT: 'Sent',
  FAILED: 'Failed',
  HANDLED: 'Dealt with by hand',
});

/** The union's three letters. */
export const LETTER_TEMPLATE_LABEL = Object.freeze({
  RETURN_DEBIT: 'Return debit',
  ACCOUNT_CLOSED: 'Account closed',
  RE_DEBIT: 'Re-debit',
});

/** Why a letter is held (Q46, SD3-C). */
export const HELD_REASON_LABEL = Object.freeze({
  NO_EMAIL: 'No email address on record',
  INVALID_ADDRESS: 'The address on record cannot be used',
});

/**
 * Why a letter that could not go did not go (Q46, EH-E): a held letter's reason, or -- for a send that FAILED -- what
 * its failure's class means to an administrator. The class itself stays the server's; the screen never shows a
 * message, which could carry the address.
 */
export const couldNotGoBecause = (letter) => {
  if (letter.status !== LETTER_STATUS.FAILED) return HELD_REASON_LABEL[letter.reason] ?? letter.reason;
  if (letter.reason === 'LETTER_CHANGED_SINCE_RENDERED') return 'Not sent — the member’s details changed after it was written';
  if (letter.reason?.startsWith('LETTER_NOT_RENDERABLE')) return 'Not sent — the letter could not be written';
  return 'Sending failed';
};

/**
 * The debit days a member may have: any day from the 20th to the 31st, the member's own -- no bank
 * dictates it (Q42, 2026-09-21).
 */
export const DEBIT_DAYS = Object.freeze(Array.from({ length: 12 }, (_, i) => 20 + i));

/** The runs whose unpaids still take a decision: the file went to the bank, and the month is not closed. */
export const DECIDABLE = new Set([RUN_STATUS.SUBMITTED, RUN_STATUS.RETURNED]);

/** The runs that can hold a re-debit over for the member's permission: a proposal (D32). */
const PROPOSED = new Set([RUN_STATUS.PREPARED, RUN_STATUS.APPROVED]);

/**
 * The runs the Debits screen works on, from a page of the runs list (newest first): the newest whose
 * unpaids still take a decision, and the newest proposal after it -- the run that holds re-debits over
 * for permission. Either is `null` when there is none.
 */
export const debitsRuns = (runs) => {
  const list = Array.isArray(runs) ? runs : [];
  const returned = list.find((run) => DECIDABLE.has(run.status)) ?? null;
  const next = list.find((run) => PROPOSED.has(run.status)
    && (!returned || String(run.actionMonth) > String(returned.actionMonth))) ?? null;
  return { returned, next };
};

/**
 * What the screen says when the member service answered a different debit day from the one chosen, or
 * `null`. It was written for register A67, when the member service set the bank's day over a day chosen
 * here; since the October build the service keeps a chosen day and gives the bank's only where none is
 * chosen (SASBO, Q42: *"we have asked that this is flexible"*). It stays as the net: a saved day that is
 * not the one on screen is never left unsaid.
 */
export const debitDayReplaced = (chosen, answered) => {
  if (chosen === '' || chosen == null || answered == null) return null;
  if (String(chosen) === String(answered)) return null;
  return `The debit day was saved as the ${ordinal(answered)}, not the ${ordinal(chosen)} chosen. `
    + 'Check the member’s banking details before the next debit order run.';
};

export const ordinal = (day) => {
  const n = Number(day);
  const tens = n % 100;
  const suffix = tens >= 11 && tens <= 13 ? 'th' : ({ 1: 'st', 2: 'nd', 3: 'rd' }[n % 10] ?? 'th');
  return `${n}${suffix}`;
};

/**
 * What the Runs screen says about the holiday calendar (D30), or `null` when there is nothing to say.
 *
 * With no calendar on record nothing can be prepared, and the screen says so before anyone tries.
 * From sixty days before the date the calendar is known until -- the server decides when, and says
 * `endingSoon` -- the screen names that date, so a month past it is never first refused inside
 * December's three-day window (SD3-G). Anything that is not a calendar -- the route smoke answers
 * every read with `[]` -- says nothing rather than something wrong.
 */
export const calendarNotice = (calendar) => {
  if (!calendar || typeof calendar !== 'object' || Array.isArray(calendar)) return null;
  if (!calendar.knownUntil) return 'No holiday calendar is on record, so no month can be prepared yet.';
  if (!calendar.endingSoon) return null;
  return `The holiday calendar is known until ${formatDateLong(calendar.knownUntil)}. `
    + 'A month after that cannot be prepared until the calendar is extended.';
};

/**
 * What a month will charge, and the next dated rate after it if there is one (Q43): the rate in
 * force for a month is the latest row dated on or before its first day. `null` when the answer is
 * not a list of rates -- the route smoke answers every read with `[]` -- or no rate reaches the month.
 */
export const rateNote = (ratesAnswer, month) => {
  const rates = Array.isArray(ratesAnswer?.rates) ? ratesAnswer.rates : null;
  if (!rates || !/^\d{4}-\d{2}$/.test(month ?? '')) return null;
  const first = `${month}-01`;
  const inForce = rates.filter((r) => r.effectiveFrom <= first).at(-1);
  if (!inForce) return null;
  const next = rates.find((r) => r.effectiveFrom > first);
  return `${monthLabel(month)} charges ${formatZar(inForce.amount)} a member — the rate since `
    + `${monthLabel(inForce.effectiveFrom.slice(0, 7))}.`
    + (next ? ` From ${monthLabel(next.effectiveFrom.slice(0, 7))}: ${formatZar(next.amount)}.` : '');
};

const MONTH_NAMES = ['January', 'February', 'March', 'April', 'May', 'June', 'July', 'August',
  'September', 'October', 'November', 'December'];

/** `2026-10` -> `October 2026`; anything else is shown as it came. */
export const monthLabel = (yearMonth) => {
  const match = /^(\d{4})-(\d{2})$/.exec(yearMonth ?? '');
  if (!match) return yearMonth ?? '';
  return `${MONTH_NAMES[Number(match[2]) - 1]} ${match[1]}`;
};

const asYearMonth = (date) => `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}`;

/**
 * The months a run may be prepared for: this one and the next -- what the server accepts. `today`
 * is the browser's date; the server decides in Johannesburg, and a refusal says so.
 */
export const preparableMonths = (today = new Date()) => {
  const next = new Date(today.getFullYear(), today.getMonth() + 1, 1);
  return [today, next].map((date) => ({ value: asYearMonth(date), label: monthLabel(asYearMonth(date)) }));
};

/**
 * A server page, or an empty one. Anything that is not `{ content: [...] }` -- the route smoke
 * answers every read with `[]` -- becomes an empty page rather than a crash.
 */
export const asPage = (payload) => {
  if (!payload || !Array.isArray(payload.content)) {
    return { content: [], page: 0, totalPages: 0, totalElements: 0 };
  }
  return {
    content: payload.content,
    page: Number(payload.page) || 0,
    totalPages: Number(payload.totalPages) || 0,
    totalElements: Number(payload.totalElements) || 0,
  };
};

/** The last four digits the server sends, shown as such -- never more is ever received. */
export const maskedAccount = (lastFour) => (lastFour ? `•••• ${lastFour}` : '—');

/**
 * How long to wait before each re-read of a run that is preparing: backing off from a second to
 * eight, about two minutes in all. After the last, the screen stops and says to check back.
 */
export const POLL_DELAYS_MS = Object.freeze([1000, 2000, 4000, 8000, 8000, 8000, 8000, 8000, 8000, 8000,
  8000, 8000, 8000, 8000, 8000, 8000, 8000]);

/**
 * Re-reads run `id` until it leaves PREPARING. Resolves with the settled run, `null` when
 * `stopped()` became true (the screen was left), or `undefined` when the delays ran out. A failed
 * read rejects -- the caller says what it could not confirm.
 */
export async function pollRun(read, id, { delays = POLL_DELAYS_MS, stopped = () => false } = {}) {
  for (const delay of delays) {
    await new Promise((resolve) => { setTimeout(resolve, delay); });
    if (stopped()) return null;
    const run = await read(id);
    if (stopped()) return null;
    if (run?.status !== RUN_STATUS.PREPARING) return run;
  }
  return undefined;
}
