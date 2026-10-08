import { apiUrl } from './api';
import { requestJson, requestJsonWrite } from './parseApiError';

/**
 * What the SHELL asks of `/api/auth` (contracts/signin.md) -- and only that, because the shell is in the first load:
 *
 *   `getMode()`   `{ mode: 'open' | 'required' }` -- anyone may ask, in either mode.
 *   `getMe()`     `{ user }`; a `401` here is the session ending, which `api.js` acts on by itself.
 *   `signOut()`   204 -- revokes the session the request carries.
 *
 * Signing in and changing a password are `signInService.js`'s, loaded with the pages that do them. The token is never
 * handled here: `api.js` adds it to every API request.
 */
export const getMode = () =>
  requestJson((signal) => fetch(apiUrl('/api/auth/mode'), { credentials: 'include', signal }));

export const getMe = () =>
  requestJson((signal) => fetch(apiUrl('/api/auth/me'), { credentials: 'include', signal }));

// 204 by contract; `allowEmpty` still holds a body that is not JSON to be the failure it is.
export const signOut = () =>
  requestJsonWrite((signal) => fetch(apiUrl('/api/auth/logout'), { method: 'POST', credentials: 'include', signal }),
    { allowEmpty: true });

export const authService = { getMode, getMe, signOut };
