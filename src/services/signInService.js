import { apiUrl } from './api';
import { requestJsonWrite } from './parseApiError';

/**
 * Signing in and changing a password -- `/api/auth` (contracts/signin.md), in a module of their own so the first load
 * carries neither: only `/login` and `/change-password` import it (`auth/authActions.js`).
 *
 *   `signIn(credentials)`    `{ token, expiresAt, user }`; refused `SIGN_IN_FAILED` (401), `ACCOUNT_LOCKED` (423),
 *                            `ACCOUNT_INACTIVE` (403).
 *   `changePassword(body)`   204; refused `PASSWORD_RULES` (400) or `SIGN_IN_FAILED` (401, the current password).
 *
 * A password travels only in a request body, never in an address and never in anything logged.
 */
const json = (body) => ({
  method: 'POST',
  credentials: 'include',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify(body),
});

export const signIn = ({ username, password }) =>
  requestJsonWrite((signal) => fetch(apiUrl('/api/auth/login'), { ...json({ username, password }), signal }));

export const changePassword = ({ currentPassword, newPassword }) =>
  requestJsonWrite((signal) => fetch(apiUrl('/api/auth/password'), { ...json({ currentPassword, newPassword }), signal }),
    { allowEmpty: true });

export const signInService = { signIn, changePassword };
