import { apiUrl } from './api';
import { requestJson, requestJsonWrite } from './parseApiError';

/**
 * Users, positions, roles and the permission catalogue -- `/api/admin`, every route `USERS_ADMIN` (contracts/signin.md).
 *
 *   `getUsers({ page, size, q })`     a page of users, `{ content, page, size, totalElements, totalPages }`, each the
 *                                     contract's `user` with `active` and `version`; `q` finds a name or a username.
 *   `createUser(body)`                `201 { user, temporaryPassword }`; refused `USERNAME_TAKEN` (409), `SCOPE_INVALID`.
 *   `updateUser(id, body)`            `{ user }`; refused `STALE_VERSION` (409, someone saved first) and
 *                                     `LAST_ADMINISTRATOR` (409, nobody left who could manage users).
 *   `resetPassword(id)`               `{ temporaryPassword }`, shown once; the user's sessions end.
 *   `getPositions()` / `getRoles()`   every position (`{ code, name, defaultRoleCode, active, ordinal }`) and role
 *                                     (`{ code, name, permissions }`) -- a handful each, bounded by nature.
 *   `getPermissions()`                the catalogue, `[{ name, description }]`.
 *
 * Each read is a named export as well, so a screen ships the requests it makes and no others.
 */
const write = (method, body) => ({
  method,
  credentials: 'include',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify(body),
});
const read = (url) => requestJson((signal) => fetch(url, { credentials: 'include', signal }));
const segment = (value) => encodeURIComponent(String(value));

export const getUsers = ({ page = 0, size = 50, q } = {}) => {
  const query = new URLSearchParams({ page: String(page), size: String(size) });
  if (q) query.set('q', q);
  return read(`${apiUrl('/api/admin/users')}?${query}`);
};

export const createUser = (body) =>
  requestJsonWrite((signal) => fetch(apiUrl('/api/admin/users'), { ...write('POST', body), signal }));

export const updateUser = (id, body) =>
  requestJsonWrite((signal) => fetch(apiUrl(`/api/admin/users/${segment(id)}`), { ...write('PUT', body), signal }));

export const resetPassword = (id) =>
  requestJsonWrite((signal) => fetch(apiUrl(`/api/admin/users/${segment(id)}/reset-password`), {
    method: 'POST',
    credentials: 'include',
    signal,
  }));

export const getPositions = () => read(apiUrl('/api/admin/positions'));

export const createPosition = (body) =>
  requestJsonWrite((signal) => fetch(apiUrl('/api/admin/positions'), { ...write('POST', body), signal }));

export const updatePosition = (code, body) =>
  requestJsonWrite((signal) => fetch(apiUrl(`/api/admin/positions/${segment(code)}`), { ...write('PUT', body), signal }));

export const getRoles = () => read(apiUrl('/api/admin/roles'));

export const createRole = (body) =>
  requestJsonWrite((signal) => fetch(apiUrl('/api/admin/roles'), { ...write('POST', body), signal }));

export const updateRole = (code, body) =>
  requestJsonWrite((signal) => fetch(apiUrl(`/api/admin/roles/${segment(code)}`), { ...write('PUT', body), signal }));

export const getPermissions = () => read(apiUrl('/api/admin/permissions'));

/** The same calls, gathered, for everything that iterates the service layer -- these very functions, never copies. */
export const adminService = {
  getUsers,
  createUser,
  updateUser,
  resetPassword,
  getPositions,
  createPosition,
  updatePosition,
  getRoles,
  createRole,
  updateRole,
  getPermissions,
};
