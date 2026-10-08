// @vitest-environment jsdom -- mounts the real shell under the real AuthProvider and reads what each state shows
import { describe, expect, it } from 'vitest'
import { screen, waitFor, within } from '@testing-library/react'
import { Route, Routes, useLocation } from 'react-router-dom'
import { http, HttpResponse } from 'msw'

import AppLayout from '../layouts/AppLayout'
// The ended-session notice and the account controls are lazy chunks; in the registry before any test, so a wait times
// the page, not the box. The controls still arrive a tick after the page, so they are found with `findBy`.
import '../components/common/SessionNotice'
import '../components/account/AccountControls'
import { AuthProvider } from './AuthContext'
// The provider's server conversations are a lazy chunk too.
import './authServer'
import { SESSION_KEY } from './session'
import { expectNoA11yViolations } from '../test/assertions'
import { authHandlers, refusal, storeSession, TOKEN, userWith } from '../test/authFixtures'
import { server } from '../test/msw/server'
import { renderWithProviders } from '../test/renderWithProviders'
import { setupUser } from '../test/user'

/**
 * The shell with sign-in in it (contracts/signin.md, "Modes" and "The client"): who the header says is signed in, what
 * the rail offers, and what happens when the server ends a session -- a redirect in REQUIRED mode, a notice in OPEN mode,
 * where the page stays usable without an account.
 */

/** Where the router is -- what a redirect is judged by. */
function Where({ label }) {
  const { pathname, search } = useLocation()
  return <p>{`${label} at ${pathname}${search}`}</p>
}

const renderShell = ({ path = '/home', mode = 'open', user = userWith(), me } = {}) => {
  // ⚠️ FIRST: within one `server.use(a, b)` MSW tries `a` before `b`, so an override listed after the defaults never runs.
  server.use(...(me ? [http.get('*/api/auth/me', me)] : []), ...authHandlers({ mode, user }))
  return renderWithProviders(
    <AuthProvider>
      <Routes>
        <Route path="/login" element={<Where label="The sign-in page" />} />
        <Route path="/change-password" element={<Where label="The change-password screen" />} />
        <Route path="/" element={<AppLayout />}>
          <Route path="home" element={<p>The home page</p>} />
        </Route>
      </Routes>
    </AuthProvider>,
    { initialEntries: [path] },
  )
}

const banner = () => screen.getByRole('banner')
const rail = () => screen.getByRole('navigation', { name: 'Main' })

describe('the shell, signed out', () => {
  it('open mode: the page is there, and the header and the rail offer Sign in with a way back', async () => {
    renderShell()
    expect(await screen.findByText('The home page')).toBeInTheDocument()
    expect(await within(banner()).findByRole('link', { name: 'Sign in' })).toHaveAttribute('href', '/login?next=%2Fhome')
    expect(await within(rail()).findByRole('link', { name: 'Sign in' })).toHaveAttribute('href', '/login?next=%2Fhome')
    expect(screen.queryByRole('button', { name: 'Sign out' })).not.toBeInTheDocument()
  })

  it('required mode: nobody reaches a screen without a session -- they are sent to sign in, and back here after', async () => {
    renderShell({ mode: 'required' })
    expect(await screen.findByText('The sign-in page at /login?next=%2Fhome')).toBeInTheDocument()
  })
})

describe('the shell, signed in', () => {
  it('names the person and their position, and offers Change your password and Sign out', async () => {
    storeSession()
    renderShell()
    expect(await screen.findByText('The home page')).toBeInTheDocument()
    expect(await within(banner()).findByText('Sam Synthetic')).toBeInTheDocument()
    expect(within(banner()).getByText('System Administrator')).toBeInTheDocument()
    expect(within(banner()).getByRole('link', { name: 'Change your password' })).toHaveAttribute('href', '/change-password')
    expect(within(banner()).getByRole('button', { name: 'Sign out' })).toBeInTheDocument()
    expect(await within(rail()).findByRole('button', { name: 'Sign out' })).toBeInTheDocument()
    expect(screen.queryByRole('link', { name: 'Sign in' })).not.toBeInTheDocument()
  })

  it('a session is refreshed from the server once -- a changed position shows', async () => {
    storeSession()
    renderShell({ user: userWith({ position: { code: 'ORGANISER', name: 'Organiser' } }) })
    expect(await within(banner()).findByText('Organiser')).toBeInTheDocument()
  })

  it('Sign out revokes the session on the server, forgets it in the tab, and opens the sign-in page', async () => {
    const user = setupUser()
    storeSession()
    const revoked = []
    server.use(http.post('*/api/auth/logout', ({ request }) => {
      revoked.push(request.headers.get('Authorization'))
      return new HttpResponse(null, { status: 204 })
    }))
    renderShell()

    await user.click(await within(banner()).findByRole('button', { name: 'Sign out' }))

    expect(await screen.findByText('The sign-in page at /login')).toBeInTheDocument()
    expect(revoked).toEqual([`Bearer ${TOKEN}`])
    expect(sessionStorage.getItem(SESSION_KEY)).toBeNull()
  })

  it('a 403 PASSWORD_CHANGE_REQUIRED from any request sends the person to change it, with a way back', async () => {
    storeSession()
    // The session was a temporary password's all along; the server says so on the first request that asks.
    renderShell({ me: () => refusal(403, 'PASSWORD_CHANGE_REQUIRED', 'Please change your temporary password before you go on.') })
    expect(await screen.findByText('The change-password screen at /change-password?next=%2Fhome')).toBeInTheDocument()
  })

  it('a temporary password is changed before anything else is opened', async () => {
    storeSession(userWith({ mustChangePassword: true }))
    renderShell({ user: userWith({ mustChangePassword: true }) })
    expect(await screen.findByText('The change-password screen at /change-password?next=%2Fhome')).toBeInTheDocument()
  })
})

describe('a session the server ends', () => {
  it('open mode keeps the page and says so, gently, with a way to sign in again', async () => {
    storeSession()
    renderShell({ me: () => refusal(401, 'SESSION_EXPIRED') })

    const notice = await screen.findByText(/Your session has ended\./)
    expect(screen.getByText('The home page')).toBeInTheDocument()
    expect(within(notice).getByRole('link', { name: 'Sign in again' })).toHaveAttribute('href', '/login?next=%2Fhome')
    // The header follows the session: nobody is signed in now.
    await waitFor(() => expect(within(banner()).getByRole('link', { name: 'Sign in' })).toBeInTheDocument())
    expect(sessionStorage.getItem(SESSION_KEY)).toBeNull()
  })

  it('required mode sends the person to sign in, and back to where they were', async () => {
    storeSession()
    renderShell({ mode: 'required', me: () => refusal(401, 'SESSION_EXPIRED') })
    expect(await screen.findByText('The sign-in page at /login?next=%2Fhome')).toBeInTheDocument()
  })

  it('a failure that is not a refusal keeps the session -- a network blip is not a sign-out', async () => {
    storeSession()
    renderShell({ me: () => HttpResponse.error() })
    expect(await screen.findByText('The home page')).toBeInTheDocument()
    await waitFor(() => expect(within(banner()).getByRole('button', { name: 'Sign out' })).toBeInTheDocument())
    expect(sessionStorage.getItem(SESSION_KEY)).not.toBeNull()
  })
})

describe('the shell with sign-in -- accessibility', () => {
  it('has no violations signed out, signed in, and with the ended-session notice', async () => {
    // Each scan waits for the account controls too: they are lazy, and a scan before they land would not see them.
    const signedOut = renderShell()
    await within(banner()).findByRole('link', { name: 'Sign in' })
    await expectNoA11yViolations(signedOut.container)
    signedOut.unmount()

    storeSession()
    const signedIn = renderShell()
    await within(banner()).findByRole('button', { name: 'Sign out' })
    await expectNoA11yViolations(signedIn.container)
    signedIn.unmount()

    storeSession()
    const ended = renderShell({ me: () => refusal(401, 'SESSION_EXPIRED') })
    await screen.findByText(/Your session has ended\./)
    await within(banner()).findByRole('link', { name: 'Sign in' })
    await expectNoA11yViolations(ended.container)
  })
})
