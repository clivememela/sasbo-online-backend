import { apiUrl } from './api';
import { requestJson } from './parseApiError';

/**
 * One member's history, a page at a time -- month end 03.6, `GET /api/members/{id}/history?page=&size=`.
 *
 * The answer is `{ content, page, size, totalElements, totalPages, historyFrom }`, newest first and then by the entry's
 * own number, so two pages never share an entry or skip one. Each entry is `{ kind, id, changedAt, changedBy, what,
 * from, to, reason, code, codeName, reasonUnderCode, saveId, note }`: `kind` is `FIELD` -- a field a save changed,
 * `what` naming it, the rows of one save sharing a `saveId` -- or `STATUS`, a change of status with its reason and,
 * where it carries one, SASBO's code by its number. The server counts each FIELD as an entry, so a save of three fields
 * is three. `historyFrom` is the day the history began (ME-7), answered on every page and on an empty one above all.
 *
 * A page that does not exist -- negative, a size outside one to two hundred, or one past the last -- is refused
 * `HISTORY_PAGE_INVALID`, never answered as an empty page that looks like the end of the history; a member who is not
 * on record is `MEMBER_NOT_FOUND`.
 *
 * 🔑 A NAMED READ, IMPORTED BY NAME (MD41): the History tab is a chunk of its own, and it is the one screen that asks
 * this -- so it ships this read and no other. The path is written at its call site, where the API contract scan and the
 * stub server's drift check read it.
 */
export const HISTORY_PAGE_SIZE = 50;

export const getMemberHistory = (memberId, page = 0, size = HISTORY_PAGE_SIZE) => {
  const query = new URLSearchParams({ page: String(page), size: String(size) });
  const url = apiUrl(`/api/members/${encodeURIComponent(memberId)}/history`) + `?${query}`;
  return requestJson((signal) => fetch(url, { credentials: 'include', signal }));
};

/** The same read, gathered, for everything that iterates the service layer -- this very function, never a copy. */
export const memberHistoryService = { getMemberHistory };
