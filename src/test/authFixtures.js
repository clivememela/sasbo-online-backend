import { http, HttpResponse } from 'msw'

import { SESSION_KEY } from '../auth/session'
import { PERMISSIONS } from '../auth/permissions'

/**
 * Sign-in's test fixtures: an INVENTED user in the contract's `user` shape, a session for them, and the handlers every
 * screen under `AuthProvider` answers. Like every shared handler here they are opted into (`server.use(...)`), never
 * global. The token is a placeholder and could never be a real session's (they are 256 random bits, base64url).
 */
export const TOKEN = 'synthetic-session-token'

export const userWith = (overrides = {}) => ({
  id: 7,
  username: 'sam.synthetic',
  displayName: 'Sam Synthetic',
  email: 'sam.synthetic@example.invalid',
  position: { code: 'SYSTEM_ADMINISTRATOR', name: 'System Administrator' },
  role: { code: 'SYSTEM_ADMINISTRATOR', name: 'System Administrator' },
  permissions: Object.values(PERMISSIONS),
  scope: { type: 'ALL', codes: [] },
  mustChangePassword: false,
  lastSignInAt: '2026-10-05T07:30:00Z',
  ...overrides,
})

/** The session a signed-in tab holds -- the key and shape the deck sets too (contracts/signin.md, "For the deck"). */
export const storeSession = (user = userWith(), token = TOKEN) =>
  sessionStorage.setItem(SESSION_KEY, JSON.stringify({ token, expiresAt: '2026-10-07T19:30:00Z', user }))

/** A refusal exactly as the contract writes one: `{ code, message }` at its status. */
export const refusal = (status, code, message = 'Refused by the server.') =>
  HttpResponse.json({ code, message, status }, { status })

/** `GET /api/auth/mode` and `GET /api/auth/me`, answered the way a running server would for this user. */
export const authHandlers = ({ mode = 'open', user = userWith() } = {}) => [
  http.get('*/api/auth/mode', () => HttpResponse.json({ mode })),
  http.get('*/api/auth/me', () => HttpResponse.json({ user })),
]
