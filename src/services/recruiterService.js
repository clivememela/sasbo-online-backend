import { apiUrl } from './api';
import { requestJson, requestJsonWrite } from './parseApiError';

/**
 * 🔴 A24's QUIETER HALF, first of three: `handleResponse` rejected with the raw response TEXT, so
 * a caller's ordinary `catch (e) { e.message }` was `undefined` and the server's own sentence was
 * lost. Every method is on the one error path now (conventions §16).
 *
 * 🔑 AND THE `console.error(...)` WRAPPERS ARE GONE FROM FOUR METHODS. They logged and rethrew,
 * which means the SAME failure was reported twice — once here where no consumer can act on it, and
 * again at the caller which is the only place that can render a state. EH-D10 gives this
 * application one sink; a service is not it. Nothing is swallowed: every one of these still
 * rejects, and now with a message worth showing.
 */
export const recruiterService = {
  /**
   * The REPRESENTATIVES: people who sit on a structure with a role and an election date. This is
   * `Recruiters.jsx`'s list -- the administration screen -- and Edit Member's, and it stays here.
   */
  getAll: () =>
    requestJson((signal) => fetch(apiUrl('/api/representatives'), {
      credentials: 'include',
      signal,
    })),

  /**
   * 🔴 S6 / R5 / 03.10 -- THE CAPTURE SCREEN'S RECRUITER LIST, WHICH IS A DIFFERENT LIST.
   *
   * A recruiter is the person a member is attributed to. A representative sits on a structure. Main's
   * `4e41ed5` pointed `/api/representatives` at the `members` table while the member's own recruiter FK
   * still resolved through `representatives`, so the drop-down and the stored value read DIFFERENT
   * TABLES -- R5's defect, re-created from the other side. The server settled it by giving recruiters
   * their own endpoint (`RecruiterController`, on main at 829f252) and leaving `/api/representatives`
   * to do its own job.
   *
   * GET only, deliberately, and that is the server's decision rather than an omission here: creating a
   * recruiter from the capture screen would let a typo become a permanent option in everybody's
   * drop-down -- the same failure 02.5's `verified` flag exists to prevent for job grades. Recruiters
   * are union staff and are administered where staff are administered.
   */
  getRecruiters: () =>
    requestJson((signal) => fetch(apiUrl('/api/recruiters'), {
      credentials: 'include',
      signal,
    })),

  create: (data) =>
    requestJsonWrite((signal) => fetch(apiUrl('/api/representatives'), {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      credentials: 'include',
      body: JSON.stringify(data),
      signal,
    })),

  update: (id, data) =>
    requestJsonWrite((signal) => fetch(apiUrl(`/api/representatives/${id}`), {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      credentials: 'include',
      body: JSON.stringify(data),
      signal,
    })),

  // `parseBody: false`: a DELETE answers 204 with no body.
  remove: (id) =>
    requestJsonWrite((signal) => fetch(apiUrl(`/api/representatives/${id}`), {
      method: 'DELETE',
      credentials: 'include',
      signal,
    }), { parseBody: false }),

  getStructures: () =>
    requestJson((signal) => fetch(apiUrl('/api/members/structures/tree'), {
      credentials: 'include',
      signal,
    })),

  getRoles: () =>
    requestJson((signal) => fetch(apiUrl('/api/members/userRoles'), {
      credentials: 'include',
      signal,
    })),
};
