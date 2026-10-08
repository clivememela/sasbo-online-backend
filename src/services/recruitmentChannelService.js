import { apiUrl } from './api';
import { requestJson, requestJsonWrite } from './parseApiError';

/**
 * 🔴 THIS FILE USED TO REJECT WITH A BARE STRING, and that is A24's quieter half.
 *
 * `handleResponse` did `Promise.reject(msg)` with the response TEXT, so a caller's ordinary
 * `catch (e) { e.message }` was `undefined` — the free-text message the server had bothered to
 * send was lost by anyone handling the error the normal way. Three services did this.
 *
 * Every method now goes through `requestJson`, which is the one error path (conventions §16):
 * it rejects with an `Error` carrying `kind`, `status`, `validationErrors` and `retryable`, and
 * it imposes the request ceiling A14 exists for.
 *
 * 🔑 THE `apiUrl(...)` LITERAL STAYS AT ITS CALL SITE. `requestJson` takes a thunk precisely so
 * the static API-contract scan can still see each endpoint; a path-taking helper would hide them
 * and blind that guard.
 */
export const recruitmentChannelService = {
  getAll: () =>
    requestJson((signal) => fetch(apiUrl('/api/recruitment-channels'), {
      credentials: 'include',
      signal,
    })),

  create: (name) =>
    requestJsonWrite((signal) => fetch(apiUrl('/api/recruitment-channels'), {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      credentials: 'include',
      body: JSON.stringify({ name }),
      signal,
    })),

  update: (id, data) =>
    requestJsonWrite((signal) => fetch(apiUrl(`/api/recruitment-channels/${id}`), {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      credentials: 'include',
      body: JSON.stringify(data),
      signal,
    })),

  // `parseBody: false` because a DELETE answers 204 with no body: a service that rejected
  // because the body was not JSON would be the defect, not the fix.
  remove: (id) =>
    requestJsonWrite((signal) => fetch(apiUrl(`/api/recruitment-channels/${id}`), {
      method: 'DELETE',
      credentials: 'include',
      signal,
    }), { parseBody: false }),

  /**
   * RC15: every channel with its members counted, and their banks and branches, worked out by the server --
   * `[{ channelId, name, enabled, members, banks, branches }]`. The Recruiters screen used to load every member for
   * this. Open to anyone: it names no member.
   */
  summary: () =>
    requestJson((signal) => fetch(apiUrl('/api/recruitment-channels/summary'), {
      credentials: 'include',
      signal,
    })),

  /**
   * A page of the members recruited through a channel: `{ content, page, size, totalElements, totalPages }`. Each
   * criterion narrows by "contains"; a page holds at most 100. Needs MEMBERS_VIEW, within the user's region or area.
   */
  members: (id, criteria = {}) =>
    requestJson((signal) => fetch(apiUrl(`/api/recruitment-channels/${id}/members`) + queryOf(criteria, MEMBER_CRITERIA), {
      credentials: 'include',
      signal,
    })),

  /**
   * The members a picker offers: `{ rows, more }` -- at most fifty, and whether more matched. `excludeChannel` leaves
   * out the members recruited through that channel already. Needs MEMBERS_VIEW.
   */
  candidates: (criteria = {}) =>
    requestJson((signal) => fetch(apiUrl('/api/recruitment-channels/member-candidates') + queryOf(criteria, ['q', 'excludeChannel']), {
      credentials: 'include',
      signal,
    })),

  /** Add Member: records the member as recruited through the channel, and answers their row. Needs MEMBERS_EDIT. */
  addMember: (id, memberId) =>
    requestJsonWrite((signal) => fetch(apiUrl(`/api/recruitment-channels/${id}/members/${memberId}`), {
      method: 'PUT',
      credentials: 'include',
      signal,
    })),

  /** Takes the member off the channel; their status and the rest of their record stay. Answers 204. */
  removeMember: (id, memberId) =>
    requestJsonWrite((signal) => fetch(apiUrl(`/api/recruitment-channels/${id}/members/${memberId}`), {
      method: 'DELETE',
      credentials: 'include',
      signal,
    }), { parseBody: false }),
};

/** The criteria a channel's member list takes, by the names the server reads. */
const MEMBER_CRITERIA = ['page', 'size', 'membershipNumber', 'name', 'bank', 'branch', 'status'];

/** `?a=1&b=2` of the named criteria that carry a value, trimmed; '' when none do. */
function queryOf(criteria, names) {
  const query = new URLSearchParams();
  for (const name of names) {
    const value = criteria?.[name];
    const text = value === undefined || value === null ? '' : String(value).trim();
    if (text !== '') query.set(name, text);
  }
  const text = query.toString();
  return text ? `?${text}` : '';
}
