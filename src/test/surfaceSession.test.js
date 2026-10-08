// @vitest-environment jsdom -- the planted session is read back through the application's own sessionStorage reader
import { describe, expect, it } from 'vitest'

import { SESSION_KEY as PLANTED_KEY, storeSession, surfaceSession, userForToken } from '../../tools/surfaceSession.mjs'
import { SESSION_KEY, readSession } from '../auth/session'
import { PERMISSIONS } from '../auth/permissions'

/**
 * The browser run's signed-in surfaces (`tools/surfaceSession.mjs`): what `surfaces.mjs` plants must be a session the
 * APPLICATION accepts, and `serve-dist.mjs` must answer `GET /api/auth/me` with the same user. Three halves written in
 * three files -- a key renamed in one, or a shape the app's reader refuses, would measure every guarded screen signed
 * out while the surfaces claimed to be signed in.
 */
const plant = (permissions) => new Function(`return ${storeSession(permissions)}`)()

describe('a surface signed in for the browser run', () => {
  it('plants the key the application reads, in a shape it accepts, holding exactly the permissions asked for', () => {
    expect(PLANTED_KEY).toBe(SESSION_KEY)
    expect(plant([PERMISSIONS.USERS_ADMIN, PERMISSIONS.AUDIT_VIEW])).toBe(true)

    const session = readSession()
    expect(session?.user.permissions).toEqual([PERMISSIONS.USERS_ADMIN, PERMISSIONS.AUDIT_VIEW])
    expect(session.user.displayName).toBe('Surface Tester')
  })

  it('is answered as the same user by the stub, read off the Authorization header alone', () => {
    const { token, user } = surfaceSession([PERMISSIONS.USERS_ADMIN])
    expect(userForToken(`Bearer ${token}`)).toEqual(user)
    // `[]` is a signed-in user holding nothing -- a refusal to measure, not an anonymous caller.
    expect(userForToken(`Bearer ${surfaceSession([]).token}`)?.permissions).toEqual([])
  })

  it('answers nobody for no token, or for a token it did not make', () => {
    expect(userForToken(undefined)).toBeNull()
    expect(userForToken('Bearer synthetic-session-token')).toBeNull()
    expect(userForToken('Basic c3VyZmFjZQ==')).toBeNull()
  })

  it('a surface with no session removes the one a previous surface left', () => {
    plant([PERMISSIONS.USERS_ADMIN])
    expect(readSession()).not.toBeNull()
    plant(undefined)
    expect(readSession()).toBeNull()
  })
})
