// @vitest-environment jsdom
import { describe, expect, it } from 'vitest'
import { screen } from '@testing-library/react'
import { renderWithProviders } from '../test/renderWithProviders'
import { TestAuthProvider } from '../test/authTestUtils'
import RequirePermission from './RequirePermission'
import { useAuth } from './useAuth'
import { PERMISSIONS, PERMISSION_LABELS } from './permissions'

const Guarded = () => (
  <RequirePermission permission={PERMISSIONS.PAYROLL_PROCESS}>
    <p>The payroll screen</p>
  </RequirePermission>
)

describe('RequirePermission', () => {
  it('shows the screen to a user who holds the permission', () => {
    renderWithProviders(
      <TestAuthProvider permissions={[PERMISSIONS.PAYROLL_PROCESS]}><Guarded /></TestAuthProvider>,
    )
    expect(screen.getByText('The payroll screen')).toBeInTheDocument()
  })

  it('asks an anonymous caller to sign in, and comes back here afterwards', () => {
    renderWithProviders(<Guarded />, { initialEntries: ['/admin/payroll?month=2026-09'] })

    expect(screen.queryByText('The payroll screen')).not.toBeInTheDocument()
    expect(screen.getByRole('status')).toHaveTextContent('Sign in to process payroll files.')
    expect(screen.getByRole('link', { name: 'Sign in' }))
      .toHaveAttribute('href', '/login?next=%2Fadmin%2Fpayroll%3Fmonth%3D2026-09')
  })

  it('tells a signed-in user without the permission whom to ask, with no sign-in link', () => {
    renderWithProviders(
      <TestAuthProvider permissions={[PERMISSIONS.MEMBERS_VIEW]}><Guarded /></TestAuthProvider>,
    )
    expect(screen.getByRole('status')).toHaveTextContent('You need permission to process payroll files.')
    expect(screen.queryByRole('link', { name: 'Sign in' })).not.toBeInTheDocument()
  })

  it('uses a fallback when one is given', () => {
    renderWithProviders(
      <RequirePermission permission={PERMISSIONS.DOD_FILE} fallback={<span>Only the CFO</span>}>
        <p>Download</p>
      </RequirePermission>,
    )
    expect(screen.getByText('Only the CFO')).toBeInTheDocument()
  })

  it('reads as nobody signed in outside any provider, as the app is today', () => {
    const Probe = () => {
      const { status, user, can } = useAuth()
      return <p>{`${status} ${user} ${can(PERMISSIONS.MEMBERS_VIEW)}`}</p>
    }
    renderWithProviders(<Probe />)
    expect(screen.getByText('anonymous null false')).toBeInTheDocument()
  })

  it('names every permission in words', () => {
    expect(Object.keys(PERMISSION_LABELS).sort()).toEqual(Object.keys(PERMISSIONS).sort())
  })
})
