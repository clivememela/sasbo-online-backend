import { expect } from '@playwright/test'
import { ApiClient } from './ApiClient.js'
import { demoPassword, demoUserFor } from '../support/demoUsers.js'

/**
 * Sign-in (the October build), as its contract writes it:
 *
 *   POST /api/auth/login    {username, password} -> 200 {token, expiresAt, user}
 *                           · 401 SIGN_IN_FAILED · 423 ACCOUNT_LOCKED · 403 ACCOUNT_INACTIVE
 *   POST /api/auth/logout   -> 204
 *   GET  /api/auth/me       -> 200 {user} · 401 SIGN_IN_REQUIRED · 401 SESSION_EXPIRED
 *   GET  /api/auth/mode     -> 200 {mode: "open" | "required"}
 *
 * `user` is `{id, username, displayName, email, position: {code, name}, role: {code, name}, permissions: [...],
 * scope: {type, codes: [...]}, mustChangePassword, lastSignInAt}`.
 *
 * 🔑 A TOKEN, NOT A COOKIE. The site and the API are cross-site on the hosted stack, so a session travels as
 * `Authorization: Bearer <token>`; `ApiClient` sends it on every request of a client built with one. In open mode --
 * this stack's -- a route that names no permission still answers an anonymous caller, so only the journeys that need
 * a permission sign in.
 *
 * The refusals have ONE shape (the build's decision OB29): anonymous on a route that needs a permission is
 * `401 {code: SIGN_IN_REQUIRED}`; signed in without it, `403 {code: PERMISSION_REQUIRED, permission}`.
 */
export class AuthApi extends ApiClient {
  /** A sign-in, answered as it came -- `{ status, body }` -- for a journey that asserts a refusal. */
  async login(username, password) {
    const response = await this.post('/api/auth/login', { username, password }, { expectOk: false })
    return { status: response.status(), body: await response.json().catch(() => ({})) }
  }

  /**
   * The demonstration user holding `position` ("Chief Financial Officer"), signed in: the session the client keeps,
   * `{ token, expiresAt, user }`. A refusal fails here, in words that name the stack's two switches -- a stack without
   * its demonstration users answers SIGN_IN_FAILED, which would otherwise read as a wrong password.
   */
  async signInAs(position) {
    const username = demoUserFor(position)
    const { status, body } = await this.login(username, demoPassword())
    expect(
      status,
      `signing the ${position} (${username}) in answered ${status} ${body?.code ?? ''}. The stack seeds its ` +
        'demonstration users only when SASBO_SIGNIN_DEMO_USERS is true AND SASBO_SIGNIN_DEMO_PASSWORD is set -- the ' +
        'same password this run signs them in with (docker-compose.yml passes it to both).',
    ).toBe(200)
    return body
  }

  /** Who this client's token belongs to: `GET /api/auth/me`, answered as it came. */
  async me() {
    const response = await this.get('/api/auth/me', { expectOk: false })
    return { status: response.status(), body: await response.json().catch(() => ({})) }
  }

  /** The stack's sign-in mode -- `open` here. */
  async mode() {
    return (await this.json(await this.get('/api/auth/mode'))).mode
  }

  /** Sign this client's session out; the server revokes it. */
  async logout() {
    return this.post('/api/auth/logout', undefined)
  }
}

/**
 * A request's answer, read as a refusal: `{ status, body }` from a response that is expected to fail. Shared by every
 * client a journey asks "is this refused, and how?".
 */
export async function refusalOf(response) {
  return { status: response.status(), body: await response.json().catch(() => ({})) }
}

/** Refused because nobody is signed in: exactly `401 {code: SIGN_IN_REQUIRED}`, the one shape for it (OB29). */
export function expectSignInRequired({ status, body }, what) {
  expect(status, `${what}: refused to a caller nobody signed in`).toBe(401)
  expect(body?.code, `${what}: the refusal's code`).toBe('SIGN_IN_REQUIRED')
}

/** Refused to a signed-in user without `permission`: exactly `403 {code: PERMISSION_REQUIRED, permission}` (OB29). */
export function expectPermissionRequired({ status, body }, permission, what) {
  expect(status, `${what}: refused to a user without ${permission}`).toBe(403)
  expect(body?.code, `${what}: the refusal's code`).toBe('PERMISSION_REQUIRED')
  expect(body?.permission, `${what}: the permission the refusal names`).toBe(permission)
}
