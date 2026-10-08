import { emptyReasonSentence, listProvenance, listRows } from '../config/monthEnd.js';
import reportError from '../lib/reportError.js';
import { nameKey } from './nameKey.js';

/**
 * THE MEMBER'S WORKPLACE -- month end `01.3` -- the rules the Workplace control reads, kept apart from how it looks so
 * each can be tested without a screen (`workplaces.test.js`).
 *
 * The control is on capture's and the record's Employment tab, above *Bank Where Employed*, *Bank Branch* and
 * *IBT No.*, which STAY (BR21: ACB finds the bank by that text, and job grades and subscriptions are keyed by it). It
 * never writes them by itself: it OFFERS, and only where the names agree (`workplaceFill`).
 *
 * ⚖️ WRITTEN TO THE BUNDLE'S CEILING: every answer below is a plain object, and every sentence is built from as few
 * literals as it can be, because the budget is not raised to fit a change (SCREEN-CONTRACT §7).
 */

/**
 * The search (MR-PERF-12): nothing is asked below the SECOND character, one question per pause in typing, and a page
 * of eight -- a search on the server, never a list of 5,507. The server refuses a term over a hundred characters
 * (`searchMaxLength` in `api-vocabulary.json`); the field stops there rather than send one it would refuse.
 */
export const WORKPLACE_SEARCH = { from: 2, pauseMs: 300, size: 8, maxLength: 100 };

const WORKPLACES = 'SASBO’s workplaces';

/**
 * The sentences for *not available* that are this screen's own. A reason the SERVER gives is said in its word, through
 * `emptyReasonSentence` (ME-19), so the four it knows read the same on every screen.
 */
export const NOT_AVAILABLE = {
  NOT_HERE: `This server does not answer for ${WORKPLACES} yet.`,
  UNREADABLE: `This server’s answer about ${WORKPLACES} could not be read.`,
};

/**
 * A read the server does not have yet is NOT AVAILABLE, never a failure (MD58, MV-53): a server built before month end
 * answers these routes 404, and retrying would only ask again. Only an HTTP answer carries a 404.
 */
export const notHere = (error) => error?.status === 404;

/** A failed read as the control holds it: not available for a 404, else the failure itself -- said, and retried. */
export const failedRead = (error) => (notHere(error) ? { why: NOT_AVAILABLE.NOT_HERE } : { failure: error });

/**
 * The rejection handler of each of the control's reads, for `useAsyncOptions`: the failure becomes the read's ONE
 * answer -- `[{ why }]` or `[{ failure }]` -- never an empty list, and a real failure goes to the one sink, by name.
 */
export const answerFailed = (context) => (error) => {
  const answer = failedRead(error);
  if (answer.failure) reportError(context, error);
  return [answer];
};

const isRecord = (value) => Boolean(value) && typeof value === 'object' && !Array.isArray(value);
const unreadable = { why: NOT_AVAILABLE.UNREADABLE };
const emptyBecause = (what, reason) => ({ why: `${what}: ${emptyReasonSentence(reason || 'EMPTY')}` });

/**
 * What the control can offer, from the status read and SASBO's employers (`01.1`):
 *
 *   `{ employers, employersFrom, workplacesFrom }` -- workplaces are on record here, and there is an employer to
 *        choose; the two `…From` are each list's provenance line (MD28);
 *   `{ why }` -- not available, and the sentence that says why: the server's own reason (every hosted stack says
 *        NOT_LOADED until SASBO agrees, MD45), or an answer of the WRONG SHAPE -- the suite's shared fixture answers
 *        every read with `[]` (BR56, MV-36), and that is never an empty control.
 */
export function workplaceSource(status, employers) {
  const standing = isRecord(status) && isRecord(status.workplaces) ? status.workplaces : null;
  const rows = listRows(employers);
  if (!standing || !rows) return unreadable;
  if (standing.reason || !(standing.rows > 0)) return emptyBecause(WORKPLACES, standing.reason);
  if (!rows.length) return emptyBecause('SASBO’s employers', employers.reason);
  return { employers: rows, employersFrom: listProvenance(employers), workplacesFrom: listProvenance(standing) };
}

/**
 * A page of workplaces as the search shows it: `{ page }`, or `{ why }` -- where none is on record here (the switch
 * was set back since the control opened) or where the answer is not a page's shape. `reason` is null when workplaces
 * exist and the search found none, which is an ANSWER: "No workplace matches".
 */
export function workplaceResults(answer) {
  if (!isRecord(answer) || !Array.isArray(answer.content)) return unreadable;
  return answer.reason ? emptyBecause(WORKPLACES, answer.reason) : { page: answer };
}

/**
 * The member's workplace from the answer for ONE member (`GET /api/members/{id}`), which carries the lineage the list
 * does not (MR-PERF-9): `{ workplace }`; `{ none: true }` where the server now says there is none; `{ why }` where the
 * answer is not a member's shape.
 */
export function memberWorkplace(answer) {
  if (isRecord(answer?.workplace)) return { workplace: answer.workplace };
  return isRecord(answer) && answer.workplaceId == null ? { none: true } : unreadable;
}

/** The two fields the offer can fill, by the names the form gives them. */
export const FILLABLE = { bankName: 'Bank Where Employed', branch: 'Bank Branch' };

/**
 * 🔑 THE OFFER'S RULE: which of *Bank Where Employed* and *Bank Branch* a chosen workplace may fill, and with what.
 *
 * A name AGREES when, trimmed and with case aside (`nameKey` -- the server's own `IgnoreCase` lookups), it is one the
 * field ALREADY OFFERS:
 *
 *   Bank Where Employed  the workplace's employer on SASBO's list -- or, where it has none, its employer as printed on
 *                        its row -- against the banks the field offers;
 *   Bank Branch          the workplace's name, against the branches the field offers for the bank it will hold.
 *
 * What is filled is the FORM'S OWN option, never SASBO's spelling, so each field only ever holds a value its own list
 * offers -- the value ACB and the job grades look up. Nothing is matched by likeness: no `includes`, no "Ltd" folding.
 * A near miss is simply not offered, and the field stays the administrator's to choose.
 *
 * A field is offered only where it agrees, would change, and is FREE: empty, or holding what an earlier offer filled.
 * A value the administrator chose -- or that the record holds -- is never overwritten (S4, `utils/derivation.js`).
 *
 * @param {object} workplace  the chosen workplace's row
 * @param {{ banks: string[], branchesFor: (bank: string) => string[], form: object, free: object }} fields
 * @returns {{ bankName?: string, branch?: string }} the fields to fill -- empty when there is nothing to offer
 */
export function workplaceFill(workplace, { banks, branchesFor, form, free }) {
  const agreeing = (options, name) => (name ? options?.find((option) => nameKey(option) === nameKey(name)) : undefined);
  const fill = {};
  const bank = agreeing(banks, workplace.employer || workplace.employerPrinted);
  if (bank && free.bankName && bank !== form.bankName) fill.bankName = bank;
  const branch = agreeing(branchesFor(fill.bankName ?? form.bankName), workplace.name);
  if (branch && free.branch && branch !== form.branch) fill.branch = branch;
  return fill;
}
