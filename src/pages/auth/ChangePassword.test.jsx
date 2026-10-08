// @vitest-environment jsdom -- changes a password through the real AuthProvider and reads what the screen says
import { describe, expect, it } from 'vitest'
import { screen, waitFor, within } from '@testing-library/react'
import { Route, Routes } from 'react-router-dom'
import { http, HttpResponse } from 'msw'

import ChangePassword from './ChangePassword'
import { AuthProvider } from '../../auth/AuthContext'
// The provider's server conversations are a lazy chunk; in the registry before any test, so a wait times the screen.
import '../../auth/authServer'
import { readSession } from '../../auth/session'
import { expectNoA11yViolations } from '../../test/assertions'
import { authHandlers, refusal, storeSession, userWith } from '../../test/authFixtures'
import { server } from '../../test/msw/server'
import { renderWithProviders } from '../../test/renderWithProviders'
import { setupUser } from '../../test/user'
import { passwordProblem } from './signInRules'

/**
 * `/change-password` (contracts/signin.md): the rules shown before anything is typed, the two the browser can check
 * checked before asking, the server's `PASSWORD_RULES` explained against the same list -- and a temporary password
 * (`mustChangePassword`) changed before the person goes on to where they were going.
 *
 * The passwords typed are placeholders the secret scan clears by shape (`committedSecrets.js`), never realistic ones.
 */
const CURRENT = 'your_password_here'
const CHOSEN = 'your_new_password'

const renderScreen = ({ user = userWith({ mustChangePassword: true }), path = '/change-password?next=%2Fmembers', signedIn = true, me } = {}) => {
  if (signedIn) storeSession(user)
  // `me` replaces the default `GET /api/auth/me` before anything mounts, so the mount's own read meets it too -- and it
  // is listed FIRST: within one `server.use(a, b)` MSW tries `a` before `b`.
  server.use(...(me ? [http.get('*/api/auth/me', me)] : []), ...authHandlers({ user }))
  return renderWithProviders(
    <AuthProvider>
      <Routes>
        <Route path="/change-password" element={<ChangePassword />} />
        <Route path="/members" element={<p>The member register</p>} />
        <Route path="/login" element={<p>The sign-in page</p>} />
      </Routes>
    </AuthProvider>,
    { initialEntries: [path] },
  )
}

const fill = async (user, { current = CURRENT, chosen = CHOSEN, again = chosen } = {}) => {
  if (current) await user.type(screen.getByLabelText(/^(Temporary|Current) password/), current)
  if (chosen) await user.type(screen.getByLabelText(/^New password/), chosen)
  if (again) await user.type(screen.getByLabelText(/^Confirm the new password/), again)
  await user.click(screen.getByRole('button', { name: 'Change password' }))
}

/** `POST /api/auth/password`, remembering each body it was sent. */
const answersChange = (answer) => {
  const bodies = []
  server.use(http.post('*/api/auth/password', async ({ request }) => {
    bodies.push(await request.json())
    return answer()
  }))
  return bodies
}

describe('the password rules, before anything is asked', () => {
  it('lists the rules on the screen, and asks a temporary password to be replaced first', () => {
    renderScreen()
    expect(screen.getByRole('heading', { level: 1 })).toHaveTextContent('Choose your password')
    expect(screen.getByText('At least 10 characters')).toBeInTheDocument()
    expect(screen.getByText('Not your username')).toBeInTheDocument()
    expect(screen.getByLabelText(/^Temporary password/)).toHaveAttribute('autocomplete', 'current-password')
    expect(screen.getByLabelText(/^New password/)).toHaveAttribute('autocomplete', 'new-password')
  })

  it.each([
    ['too short', 'short', /at least 10 characters/],
    ['the username', 'sam.synthetic', /not your username/],
  ])('refuses a new password that is %s, before sending anything', async (_case, chosen, sentence) => {
    const user = setupUser()
    const sent = answersChange(() => new HttpResponse(null, { status: 204 }))
    renderScreen()
    await fill(user, { chosen })
    expect(screen.getByLabelText(/^New password/)).toHaveAttribute('aria-invalid', 'true')
    expect(screen.getAllByText(sentence).length).toBeGreaterThan(0)
    expect(sent).toEqual([])
  })

  it('refuses two new passwords that differ, on the field that can fix it', async () => {
    const user = setupUser()
    renderScreen()
    await fill(user, { again: 'your_other_password' })
    expect(screen.getByLabelText(/^Confirm the new password/)).toHaveAttribute('aria-invalid', 'true')
  })

  it('the rules the browser checks, by name', () => {
    expect(passwordProblem('123456789', 'someone')).toMatch(/at least 10/)
    expect(passwordProblem('SAM.SYNTHETIC', 'sam.synthetic')).toMatch(/not your username/)
    expect(passwordProblem(CHOSEN, 'sam.synthetic')).toBeNull()
  })
})

describe('changing the password', () => {
  it('sends the current and the new password, then goes on to where the person was going', async () => {
    const user = setupUser()
    const sent = answersChange(() => new HttpResponse(null, { status: 204 }))
    // Read on mount as the temporary-password user; read again after the change as the server now has them.
    let reads = 0
    renderScreen({ me: () => HttpResponse.json({ user: userWith({ mustChangePassword: reads++ === 0 }) }) })

    await fill(user)
    expect(await screen.findByText('Your password has been changed')).toBeInTheDocument()
    expect(sent).toEqual([{ currentPassword: CURRENT, newPassword: CHOSEN }])
    await waitFor(() => expect(readSession()?.user.mustChangePassword).toBe(false))

    await user.click(screen.getByRole('button', { name: 'Continue' }))
    expect(await screen.findByText('The member register')).toBeInTheDocument()
  })

  it('explains PASSWORD_RULES against the list on screen, in the server\'s words', async () => {
    const user = setupUser()
    answersChange(() => refusal(400, 'PASSWORD_RULES', 'That password is on the list of common passwords.'))
    renderScreen()
    await fill(user)
    const outcome = await screen.findByText('Your password was not changed')
    expect(outcome.closest('[aria-labelledby]')).toHaveTextContent(/does not meet the rules below\. That password is on the list of common passwords\./)
    await waitFor(() => expect(outcome.closest('[aria-labelledby]')).toHaveFocus())
  })

  it('a wrong current password says so, and clears only that field', async () => {
    const user = setupUser()
    answersChange(() => refusal(401, 'SIGN_IN_FAILED'))
    renderScreen()
    await fill(user)
    expect(await screen.findByText(/The current password is not right/)).toBeInTheDocument()
    expect(screen.getByLabelText(/^Temporary password/)).toHaveValue('')
    expect(screen.getByLabelText(/^New password/)).toHaveValue(CHOSEN)
    // A wrong password is a refusal, not an ending: still signed in.
    expect(readSession()).not.toBeNull()
  })

  it('if the change ends the session, it says to sign in again with the new password', async () => {
    const user = setupUser()
    answersChange(() => new HttpResponse(null, { status: 204 }))
    // The session found in the tab is read once on mount (answered as signed in); the next read, after the change, ends it.
    let reads = 0
    renderScreen({
      me: () => (reads++ === 0 ? HttpResponse.json({ user: userWith({ mustChangePassword: true }) }) : refusal(401, 'SESSION_EXPIRED')),
    })
    await waitFor(() => expect(reads).toBe(1))
    await fill(user)
    const outcome = await screen.findByText('Your password has been changed')
    expect(within(outcome.closest('[aria-labelledby]')).getByRole('link', { name: 'Sign in' })).toHaveAttribute('href', '/login')
    expect(screen.queryByRole('button', { name: 'Continue' })).not.toBeInTheDocument()
  })

  it('nobody signed in is told to sign in first, and asked for nothing', () => {
    renderScreen({ signedIn: false })
    expect(screen.getByRole('heading', { level: 1 })).toHaveTextContent('Change your password')
    expect(screen.getByRole('status')).toHaveTextContent('Sign in first')
    expect(screen.queryByLabelText(/^New password/)).not.toBeInTheDocument()
  })
})

describe('the change-password screen -- accessibility', () => {
  it('has no violations at rest and showing a refusal, in both themes', async () => {
    const user = setupUser()
    answersChange(() => refusal(400, 'PASSWORD_RULES', 'That password is on the list of common passwords.'))
    const { container } = renderScreen()
    await expectNoA11yViolations(container)
    await fill(user)
    await screen.findByText('Your password was not changed')
    await expectNoA11yViolations(container)
    document.documentElement.classList.add('dark')
    await expectNoA11yViolations(container)
  })
})
