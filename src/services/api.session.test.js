// @vitest-environment jsdom -- the session lives in sessionStorage, which only a DOM environment has
import { describe, expect, it } from 'vitest'
import { http, HttpResponse } from 'msw'

import { isApiRequest } from './api'
import { onSessionEnded, readSession, saveSession } from '../auth/session'
import { refusal, storeSession, TOKEN, userWith } from '../test/authFixtures'
import { deferred } from '../test/deferred'
import { server } from '../test/msw/server'

/**
 * THE BEARER TOKEN (contracts/signin.md, "The client"): `api.js` adds it to every API request when a session exists,
 * through the one `fetch` wrapper `setup.js` installs exactly as `main.jsx` does -- so these requests are the ones every
 * service makes, through the real wrapper, answered by MSW.
 *
 * 🔴 THE NARROWNESS IS THE SECURITY HALF. A bearer token sent to any other origin is a leaked session, so the cases
 * that must NOT carry it matter as much as the one that must.
 */
const API = 'http://api.test.invalid' // the suite's VITE_API_BASE_URL (vite.config.js `test.env`)

/** Answer one route, remembering the Authorization header each request arrived with. */
const recordAuthorization = (url, answer = () => HttpResponse.json({ ok: true })) => {
  const seen = []
  server.use(http.all(url, ({ request }) => {
    seen.push(request.headers.get('Authorization'))
    return answer()
  }))
  return seen
}

describe('the session\'s token on API requests', () => {
  it('goes on every request to the API once a session exists, as a bearer token', async () => {
    storeSession()
    const seen = recordAuthorization(`${API}/api/members`)
    await fetch(`${API}/api/members`, { credentials: 'include' })
    expect(seen).toEqual([`Bearer ${TOKEN}`])
  })

  it('is absent with no session -- an anonymous request is sent exactly as before sign-in', async () => {
    const seen = recordAuthorization(`${API}/api/members`)
    await fetch(`${API}/api/members`)
    expect(seen).toEqual([null])
  })

  it('never goes to another origin, nor to a path outside /api on this one', async () => {
    storeSession()
    const elsewhere = recordAuthorization('https://elsewhere.example/api/members')
    const outside = recordAuthorization(`${API}/assets/logo.png`)
    await fetch('https://elsewhere.example/api/members')
    await fetch(`${API}/assets/logo.png`)
    expect(elsewhere).toEqual([null])
    expect(outside).toEqual([null])
  })

  it('leaves an Authorization header the caller set alone', async () => {
    storeSession()
    const seen = recordAuthorization(`${API}/api/members`)
    await fetch(`${API}/api/members`, { headers: { Authorization: 'Bearer chosen-by-the-caller' } })
    expect(seen).toEqual(['Bearer chosen-by-the-caller'])
  })

  it('decides "the API" by the exact prefix apiUrl builds, so a lookalike host is not mistaken for it', () => {
    expect(isApiRequest(`${API}/api/members`)).toBe(true)
    expect(isApiRequest(new Request(`${API}/api/auth/me`))).toBe(true)
    expect(isApiRequest('http://api.test.invalid.evil.example/api/members')).toBe(false)
    expect(isApiRequest('http://evil.example/http://api.test.invalid/api/members')).toBe(false)
  })
})

describe('a session the server ends', () => {
  it.each(['SESSION_EXPIRED', 'SIGN_IN_REQUIRED'])('%s clears the session, and says a session ended', async (code) => {
    storeSession()
    const ended = []
    const stop = onSessionEnded((event) => ended.push(event))
    recordAuthorization(`${API}/api/members`, () => refusal(401, code))

    const response = await fetch(`${API}/api/members`)
    stop()

    expect(readSession()).toBeNull()
    expect(ended).toEqual([{ code, hadSession: true }])
    // The caller still reads the refusal itself: the wrapper read a copy.
    expect((await response.json()).code).toBe(code)
  })

  it('a wrong password (SIGN_IN_FAILED) ends nothing -- it is a refusal, not an ending', async () => {
    storeSession()
    recordAuthorization(`${API}/api/auth/password`, () => refusal(401, 'SIGN_IN_FAILED'))
    await fetch(`${API}/api/auth/password`, { method: 'POST' })
    expect(readSession()?.token).toBe(TOKEN)
  })

  it('a 403 PERMISSION_REQUIRED ends nothing -- the screen says what is needed', async () => {
    storeSession()
    recordAuthorization(`${API}/api/admin/users`, () => refusal(403, 'PERMISSION_REQUIRED'))
    await fetch(`${API}/api/admin/users`)
    expect(readSession()?.token).toBe(TOKEN)
  })

  it('a refusal of an OLD token does not sign out the session that replaced it', async () => {
    storeSession(userWith(), 'the-old-token')
    // The request is held at the server -- by the test, not a clock -- while a fresh sign-in replaces the session.
    const arrived = deferred()
    const answer = deferred()
    server.use(http.get(`${API}/api/members`, () => {
      arrived.resolve()
      return answer.promise
    }))
    const pending = fetch(`${API}/api/members`)
    await arrived.promise

    saveSession({ token: 'the-new-token', expiresAt: null, user: userWith() })
    answer.resolve(refusal(401, 'SESSION_EXPIRED'))
    await pending

    expect(readSession()?.token).toBe('the-new-token')
  })

  it('a 403 PASSWORD_CHANGE_REQUIRED keeps the session and marks its user as on a temporary password', async () => {
    storeSession()
    recordAuthorization(`${API}/api/admin/users`, () => refusal(403, 'PASSWORD_CHANGE_REQUIRED', 'Please change your temporary password before you go on.'))
    await fetch(`${API}/api/admin/users`)
    expect(readSession()).toMatchObject({ token: TOKEN, user: { mustChangePassword: true } })
  })

  it('a 403 PERMISSION_REQUIRED leaves the user as they were -- only the temporary password is a change of state', async () => {
    storeSession()
    recordAuthorization(`${API}/api/admin/users`, () => refusal(403, 'PERMISSION_REQUIRED'))
    await fetch(`${API}/api/admin/users`)
    expect(readSession().user.mustChangePassword).toBe(false)
  })

  it('an anonymous refusal clears nothing, and is announced as no session having ended', async () => {
    const ended = []
    const stop = onSessionEnded((event) => ended.push(event))
    recordAuthorization(`${API}/api/admin/users`, () => refusal(401, 'SIGN_IN_REQUIRED'))
    await fetch(`${API}/api/admin/users`)
    stop()
    expect(ended).toEqual([{ code: 'SIGN_IN_REQUIRED', hadSession: false }])
  })
})
