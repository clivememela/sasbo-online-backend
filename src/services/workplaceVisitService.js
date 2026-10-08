import { apiUrl } from './api';
import { requestJson, requestJsonWrite } from './parseApiError';

/**
 * A workplace's visits -- month end A.1 (MO22): the Location Visit form keeps what it records, and the workplaces'
 * screen shows it (A60, quick wins' QW10, closed by this and not built twice).
 *
 * `getVisits` -- `GET /api/workplaces/{id}/visits?page=&size=` -- answers `{ workplaceId, visits, page, size,
 * totalElements, totalPages, withheld }`, newest first and then by the visit's own number. Each visit is `{ id,
 * visitedOn, dayKnown, complementFound, complementBefore, seeded }`: `dayKnown` is false for SASBO's own history (the
 * months its branch list prints, `seeded`), whose `visitedOn` is the first of the month and is shown as a month,
 * never a day. `withheld` names what every visit keeps and this read does not answer -- who visited, and the counts
 * the organiser took (MD61, B1). A workplace not on record here is `WORKPLACE_UNKNOWN`; a page past the last
 * `WORKPLACE_PAGE_INVALID`, never an empty page.
 *
 * `recordVisit` -- `POST /api/workplaces/{id}/visits` -- takes `{ visitedOn, timeIn, timeOut, organiser,
 * complementFound, membershipFound, nonMembers, recruited, nonMembersInterviewed }`, only the day required, and
 * answers 201 with the visit as `getVisits` shows it. A visit that found another complement writes the workplace's
 * and keeps what it was (`complementBefore`). Refused whole: `VISIT_INVALID` with `details.field` naming the field, or
 * `WORKPLACE_UNKNOWN`. A write, so the write's ceiling.
 *
 * 🔑 NAMED READS, IMPORTED BY NAME (MD41): the workplaces' screen reads and the Location Visit form writes, and neither
 * ships the other's. The paths are written at their call sites, where the API contract scan reads them.
 */
export const VISIT_PAGE_SIZE = 20;

export const getVisits = (workplaceId, page = 0, size = VISIT_PAGE_SIZE) => {
  const query = new URLSearchParams({ page: String(page), size: String(size) });
  const url = apiUrl(`/api/workplaces/${encodeURIComponent(workplaceId)}/visits`) + `?${query}`;
  return requestJson((signal) => fetch(url, { credentials: 'include', signal }));
};

export const recordVisit = (workplaceId, visit) =>
  requestJsonWrite((signal) => fetch(apiUrl(`/api/workplaces/${encodeURIComponent(workplaceId)}/visits`), {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    credentials: 'include',
    body: JSON.stringify(visit),
    signal,
  }));

/**
 * A person's own visit drafts -- the visit forms saving and submitting (October build, 5 Oct Show & Tell). Every route
 * needs `VISITS_CAPTURE`, and a draft is only ever its owner's: another person's is `VISIT_DRAFT_NOT_FOUND`.
 *
 * A draft is `{ id, status, workplaceId, raNumber, workplaceName, visitedOn, timeIn, timeOut, organiser,
 * complementFound, complementBefore, membershipFound, nonMembers, recruited, nonMembersInterviewed, createdBy,
 * createdAt, updatedAt, submittedBy, submittedAt, visitId, version }`; `status` is `DRAFT` until it is submitted, then
 * `SUBMITTED` and read-only. A save names the R/A number (`raNumber`) and the fields a visit keeps; saving over a draft
 * and submitting it name the `version` it was made from.
 *
 * Refused: `VISIT_INVALID` (`details.field`), `WORKPLACE_UNKNOWN` (`details.field` = `raNumber`),
 * `WORKPLACE_OUT_OF_SCOPE`, `VISIT_DRAFT_STALE` (saved since -- `details.version`), `VISIT_ALREADY_SUBMITTED`,
 * `VISIT_DRAFT_NOT_FOUND`. A submission sent twice answers the first.
 */
export const DRAFT_PAGE_SIZE = 20;

export const getMyDrafts = (status = 'DRAFT', page = 0, size = DRAFT_PAGE_SIZE) => {
  const query = new URLSearchParams({ status, page: String(page), size: String(size) });
  return requestJson((signal) => fetch(apiUrl('/api/workplaces/visit-drafts') + `?${query}`,
    { credentials: 'include', signal }));
};

export const getDraft = (id) =>
  requestJson((signal) => fetch(apiUrl(`/api/workplaces/visit-drafts/${encodeURIComponent(id)}`),
    { credentials: 'include', signal }));

export const createDraft = (draft) =>
  requestJsonWrite((signal) => fetch(apiUrl('/api/workplaces/visit-drafts'), {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    credentials: 'include',
    body: JSON.stringify(draft),
    signal,
  }));

export const updateDraft = (id, draft) =>
  requestJsonWrite((signal) => fetch(apiUrl(`/api/workplaces/visit-drafts/${encodeURIComponent(id)}`), {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    credentials: 'include',
    body: JSON.stringify(draft),
    signal,
  }));

export const submitDraft = (id, version) =>
  requestJsonWrite((signal) => fetch(apiUrl(`/api/workplaces/visit-drafts/${encodeURIComponent(id)}/submit`), {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    credentials: 'include',
    body: JSON.stringify({ version }),
    signal,
  }));

/**
 * Discards a draft -- `DELETE /api/workplaces/visit-drafts/{id}?version=` -- from the version the person saw: 204, no
 * body. A submitted visit is never discarded (`VISIT_ALREADY_SUBMITTED`); one saved since is `VISIT_DRAFT_STALE`.
 */
export const discardDraft = (id, version) => {
  const query = new URLSearchParams({ version: String(version) });
  return requestJsonWrite((signal) => fetch(apiUrl(`/api/workplaces/visit-drafts/${encodeURIComponent(id)}?${query}`), {
    method: 'DELETE',
    credentials: 'include',
    signal,
  }), { parseBody: false });
};

/** Every function above, gathered, for everything that iterates the service layer -- these very functions, never copies. */
export const workplaceVisitService = {
  getVisits, recordVisit, getMyDrafts, getDraft, createDraft, updateDraft, submitDraft, discardDraft,
};
