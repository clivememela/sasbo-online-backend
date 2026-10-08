// @vitest-environment jsdom -- signs in through the real AuthProvider and reads what the screen says and where it goes
import { afterEach, describe, expect, it, vi } from 'vitest'
import { screen, waitFor, within } from '@testing-library/react'
import { Route, Routes } from 'react-router-dom'
import { http, HttpResponse } from 'msw'

import Auth from './Auth'
import { AuthProvider } from '../../auth/AuthContext'
// The provider's server conversations are a lazy chunk; in the registry before any test, so a wait times the screen.
import '../../auth/authServer'
import { SESSION_KEY } from '../../auth/session'
import { expectNoA11yViolations } from '../../test/assertions'
import { authHandlers, refusal, storeSession, userWith } from '../../test/authFixtures'
import { deferred } from '../../test/deferred'
import { server } from '../../test/msw/server'
import { renderWithProviders } from '../../test/renderWithProviders'
import { setupUser } from '../../test/user'

/**
 * The sign-in screen, made real (contracts/signin.md). Every case mounts the real `AuthProvider` over MSW, so what is
 * asserted is what a person meets: the request sent, the sentence shown, the page reached.
 *
 * 🔴 THE FIRST TWO DESCRIBES ARE R-SEC-C AND THEY ARE WHY THIS FILE EXISTS. The screen once did
 * `console.log('Login attempt:', { email, password })`. Now it sends the password to the server -- so the guard is
 * sharper than before: across a real sign-in, a refused one and a failed request, nothing typed may reach any console
 * sink, in any shape.
 */

/**
 * The strings typed into the form. ⚠️ THEIR SHAPE IS LOAD-BEARING: `committedSecrets.js` convicts a credential-shaped
 * name assigned a long literal, and clears `your_password_here` only because nothing that shape is ever a real secret.
 * Do not make it look like a real password -- it reads as a downgrade and reddens the secret guard.
 */
const PASSWORD = 'your_password_here'
const USERNAME = 'sam.synthetic'
const SUMMARY = /There is a problem with this form|You are not signed in/

/** The summary, found by its accessible name rather than by a role (`Field` gives each field's message `alert`). */
const summary = () => screen.getByText(SUMMARY).closest('[aria-labelledby]')

/** One console argument, flattened to text a search can find a secret in -- object fields included (see the canary). */
const flatten = (value, depth = 0) => {
  if (depth > 6) return ''
  if (value === null || value === undefined) return String(value)
  if (typeof value === 'string') return value
  if (typeof value !== 'object') return String(value)
  if (value instanceof Error) return `${value.message} ${value.stack ?? ''}`
  return Object.entries(value).map(([key, nested]) => `${key} ${flatten(nested, depth + 1)}`).join(' ')
}

/** Every console sink, spied together. A guard that watches only `log` is half a guard. */
const spyOnConsole = () => {
  const spies = ['log', 'info', 'debug', 'warn', 'error', 'trace'].map((sink) => vi.spyOn(console, sink).mockImplementation(() => {}))
  return {
    everythingWritten: () => spies.flatMap((spy) => spy.mock.calls).map((call) => call.map((arg) => flatten(arg)).join(' ')).join('\n'),
  }
}

/** /login under the real provider, with the two places a sign-in can lead. */
const renderLogin = ({ path = '/login', mode = 'open', user = userWith() } = {}) => {
  server.use(...authHandlers({ mode, user }))
  return renderWithProviders(
    <AuthProvider>
      <Routes>
        <Route path="/login" element={<Auth />} />
        <Route path="/home" element={<p>The home page</p>} />
        <Route path="/members" element={<p>The member register</p>} />
        <Route path="/change-password" element={<p>The change-password screen</p>} />
      </Routes>
    </AuthProvider>,
    { initialEntries: [path] },
  )
}

const fillAndSubmit = async (user) => {
  await user.type(screen.getByLabelText(/^Username/), USERNAME)
  await user.type(screen.getByLabelText(/^Password/), PASSWORD)
  await user.click(screen.getByRole('button', { name: /^Sign in$/ }))
}

/** `POST /api/auth/login`, remembering the body it was sent. */
const answersSignIn = (answer) => {
  const bodies = []
  server.use(http.post('*/api/auth/login', async ({ request }) => {
    bodies.push(await request.json())
    return answer()
  }))
  return bodies
}

const signedIn = (user = userWith()) => () => HttpResponse.json({ token: 'synthetic-session-token', expiresAt: '2026-10-07T19:30:00Z', user })

afterEach(() => vi.restoreAllMocks())

describe('the credential guard can actually see a credential', () => {
  it('finds a secret logged as a field of an object, not just as a bare string', () => {
    const console_ = spyOnConsole()
    console.log('Login attempt:', { username: USERNAME, password: PASSWORD })
    console.warn({ nested: { deeper: { credential: PASSWORD } } })
    console.error(`bare ${PASSWORD}`)
    const written = console_.everythingWritten()
    expect(written.split(PASSWORD).length - 1, 'the flattener cannot see a secret in the shape it was once logged in').toBe(3)
    expect(written).toContain(USERNAME)
  })
})

describe('Auth -- R-SEC-C, nothing typed here reaches a console', () => {
  it.each([
    ['a sign-in that succeeds', signedIn()],
    ['a sign-in the server refuses', () => refusal(401, 'SIGN_IN_FAILED')],
    ['a sign-in that never reaches the server', () => HttpResponse.error()],
  ])('logs neither the username nor the password on %s', async (_case, answer) => {
    const user = setupUser()
    const console_ = spyOnConsole()
    const sent = answersSignIn(answer)
    renderLogin()

    await fillAndSubmit(user)
    // 🔑 THE MUST-PASS HALF: the credentials really were sent. An empty console proves nothing if nothing happened.
    await waitFor(() => expect(sent).toEqual([{ username: USERNAME, password: PASSWORD }]))

    const written = console_.everythingWritten()
    expect(written.includes(PASSWORD), `A password reached the console:\n${written}`).toBe(false)
    expect(written.includes(USERNAME), `A username reached the console:\n${written}`).toBe(false)
  })
})

describe('Auth -- signing in', () => {
  it('sends the username trimmed and the password as typed, keeps the session, and opens Home', async () => {
    const user = setupUser()
    const sent = answersSignIn(signedIn())
    renderLogin()

    await user.type(screen.getByLabelText(/^Username/), `  ${USERNAME} `)
    await user.type(screen.getByLabelText(/^Password/), ` ${PASSWORD} `)
    await user.click(screen.getByRole('button', { name: /^Sign in$/ }))

    expect(await screen.findByText('The home page')).toBeInTheDocument()
    expect(sent).toEqual([{ username: USERNAME, password: ` ${PASSWORD} ` }])
    // The contract's key and shape -- what `api.js` reads its token from, and what a deck slide sets.
    expect(JSON.parse(sessionStorage.getItem(SESSION_KEY))).toMatchObject({ token: 'synthetic-session-token', user: { username: USERNAME } })
  })

  it('honours a `next` on this site -- the screen asked for, with its query', async () => {
    const user = setupUser()
    answersSignIn(signedIn())
    renderLogin({ path: `/login?next=${encodeURIComponent('/members?q=Synthetic')}` })
    await fillAndSubmit(user)
    expect(await screen.findByText('The member register')).toBeInTheDocument()
  })

  it.each([
    ['a protocol-relative address', '//evil.example/steal'],
    ['an absolute address', 'https://evil.example/steal'],
    ['a backslash trick', '/\\evil.example'],
    ['a script address', 'javascript:alert(1)'],
  ])('refuses %s in `next`, and opens Home instead', async (_case, next) => {
    const user = setupUser()
    answersSignIn(signedIn())
    renderLogin({ path: `/login?next=${encodeURIComponent(next)}` })
    await fillAndSubmit(user)
    expect(await screen.findByText('The home page')).toBeInTheDocument()
  })

  it('sends a temporary password to the change-password screen first, keeping where it was going', async () => {
    const user = setupUser()
    answersSignIn(signedIn(userWith({ mustChangePassword: true })))
    renderLogin({ path: '/login?next=%2Fmembers' })
    await fillAndSubmit(user)
    expect(await screen.findByText('The change-password screen')).toBeInTheDocument()
  })

  it('says it is signing in, and cannot be sent twice, while the server is asked', async () => {
    const user = setupUser()
    const answer = deferred()
    server.use(http.post('*/api/auth/login', () => answer.promise))
    renderLogin()

    await fillAndSubmit(user)
    const button = screen.getByRole('button', { name: /Signing in/ })
    expect(button).toBeDisabled()
    expect(button).toHaveAttribute('aria-busy', 'true')

    answer.resolve(signedIn()())
    expect(await screen.findByText('The home page')).toBeInTheDocument()
  })

  it('a session already in the tab goes straight on to `next`', async () => {
    storeSession()
    renderLogin({ path: '/login?next=%2Fmembers' })
    expect(await screen.findByText('The member register')).toBeInTheDocument()
  })
})

describe('Auth -- each refusal is a sentence, and gives nothing away', () => {
  it.each([
    ['SIGN_IN_FAILED', 401, /username or the password is not right/],
    ['ACCOUNT_LOCKED', 423, /locked for 15 minutes, after five sign-ins/],
    ['ACCOUNT_INACTIVE', 403, /account is closed/],
  ])('%s says what happened and what to do, with focus on it', async (code, status, sentence) => {
    const user = setupUser()
    answersSignIn(() => refusal(status, code))
    renderLogin()
    await fillAndSubmit(user)

    await waitFor(() => expect(summary()).toHaveFocus())
    expect(within(summary()).getByText(sentence)).toBeInTheDocument()
    // Not the username typed, and never which half was wrong (the server answers both the same: no enumeration).
    expect(summary()).not.toHaveTextContent(USERNAME)
    expect(summary()).not.toHaveTextContent(/no such user|unknown user|wrong password/i)
    // The password is cleared for the next attempt; the username is kept.
    expect(screen.getByLabelText(/^Password/)).toHaveValue('')
    expect(screen.getByLabelText(/^Username/)).toHaveValue(USERNAME)
    expect(sessionStorage.getItem(SESSION_KEY)).toBeNull()
  })

  it('a failure that is not a refusal says so, in the request layer\'s own words', async () => {
    const user = setupUser()
    answersSignIn(() => HttpResponse.error())
    renderLogin()
    await fillAndSubmit(user)
    expect(await screen.findByText(/Could not reach the server/)).toBeInTheDocument()
  })

  it('an empty form names both fields at once, focuses the summary, and sends nothing', async () => {
    const user = setupUser()
    const sent = answersSignIn(signedIn())
    vi.spyOn(window, 'alert').mockImplementation(() => {})
    renderLogin()

    await user.click(screen.getByRole('button', { name: /^Sign in$/ }))

    expect(within(summary()).getAllByRole('listitem')).toHaveLength(2)
    expect(summary()).toHaveFocus()
    expect(screen.getByLabelText(/^Username/)).toHaveAttribute('aria-invalid', 'true')
    expect(window.alert, 'a native alert() is back').not.toHaveBeenCalled()
    expect(sent).toEqual([])
  })
})

describe('Auth -- the two modes', () => {
  it('open mode offers to carry on without an account, to where the person was going', async () => {
    const user = setupUser()
    renderLogin({ path: '/login?next=%2Fmembers', mode: 'open' })
    await user.click(await screen.findByRole('button', { name: 'Continue without signing in' }))
    expect(await screen.findByText('The member register')).toBeInTheDocument()
  })

  it('required mode does not -- once the server has said so', async () => {
    renderLogin({ mode: 'required' })
    await waitFor(() => expect(screen.queryByRole('button', { name: 'Continue without signing in' })).not.toBeInTheDocument())
    // The must-pass half: the form itself is there, so the absence above is the mode's doing, not an empty page.
    expect(screen.getByRole('button', { name: /^Sign in$/ })).toBeInTheDocument()
  })
})

describe('Auth -- the fields', () => {
  it('asks a password manager for the right things, and shows the password on request', async () => {
    const user = setupUser()
    renderLogin()
    const password = screen.getByLabelText(/^Password/)
    expect(screen.getByLabelText(/^Username/)).toHaveAttribute('autocomplete', 'username')
    expect(password).toHaveAttribute('autocomplete', 'current-password')
    expect(password).toHaveAttribute('type', 'password')

    const show = screen.getByRole('button', { name: 'Show password' })
    expect(show).toHaveAttribute('aria-pressed', 'false')
    await user.click(show)
    expect(password).toHaveAttribute('type', 'text')
    expect(show).toHaveAttribute('aria-pressed', 'true')
  })

  it('renders exactly one <h1>, and it names the screen', () => {
    renderLogin()
    const headings = screen.getAllByRole('heading', { level: 1 })
    expect(headings).toHaveLength(1)
    expect(headings[0]).toHaveTextContent('Sign in')
  })

  it('offers no sign-up: an administrator creates every account', () => {
    renderLogin()
    expect(screen.queryByRole('button', { name: /sign up|create account/i })).not.toBeInTheDocument()
  })
})

describe('Auth -- accessibility, both themes', () => {
  it('has no violations at rest, showing a refusal, and in dark', async () => {
    const user = setupUser()
    answersSignIn(() => refusal(401, 'SIGN_IN_FAILED'))
    const { container } = renderLogin()
    await expectNoA11yViolations(container)

    await fillAndSubmit(user)
    await screen.findByText(/username or the password is not right/)
    await expectNoA11yViolations(container)

    document.documentElement.classList.add('dark')
    await expectNoA11yViolations(container)
  })
})
