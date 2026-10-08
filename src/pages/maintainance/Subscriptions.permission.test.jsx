// @vitest-environment jsdom -- Subscriptions: only staff change amounts
import { beforeEach, describe, expect, it } from 'vitest'
import { screen } from '@testing-library/react'
import { http, HttpResponse } from 'msw'

import Subscriptions from './Subscriptions'
import { renderWithProviders } from '../../test/renderWithProviders'
import { server } from '../../test/msw/server'
import { TestAuthProvider } from '../../test/authTestUtils'
import { PERMISSIONS } from '../../auth/permissions'

/**
 * ONLY STAFF CHANGE AMOUNTS (the 5 Oct Show & Tell). The server refuses the subscription writes without
 * `SUBSCRIPTIONS_MAINTAIN`, so the screen offers no edit it would refuse: anyone else reads the rows and is told what
 * changing them needs, in the shared words of `RequirePermission`.
 */
const SUB = {
  id: 3, bankName: 'Synthetic Bank', acbDebitDay: 25, subscriptionAmount: 174, isCoreBanking: false,
  status: 'ACTIVE', registrationDate: '2026-01-15',
}
const EDIT = { name: 'Edit the subscription for Synthetic Bank' }

describe('Subscriptions, by what the user may do', () => {
  beforeEach(() => {
    server.use(
      http.get('*/api/subscriptions', () => HttpResponse.json([SUB])),
      http.get('*/api/banks', () => HttpResponse.json([{ name: 'Synthetic Bank' }])),
    )
  })

  it('an anonymous visitor reads the rows, is offered no edit, and is told to sign in', async () => {
    renderWithProviders(<Subscriptions />)

    expect(await screen.findByText('Synthetic Bank')).toBeInTheDocument()
    expect(screen.queryByRole('button', EDIT)).toBeNull()
    expect(screen.getByText(/Sign in to change subscription amounts/)).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Sign in' })).toHaveAttribute('href', expect.stringMatching(/^\/login\?next=/))
  })

  it('a signed-in user without the permission is offered no edit, and told it is needed', async () => {
    renderWithProviders(
      <TestAuthProvider permissions={[PERMISSIONS.MEMBERS_VIEW]}><Subscriptions /></TestAuthProvider>,
    )

    expect(await screen.findByText('Synthetic Bank')).toBeInTheDocument()
    expect(screen.queryByRole('button', EDIT)).toBeNull()
    expect(screen.getByText(/You need permission to change subscription amounts/)).toBeInTheDocument()
  })

  it('the must-pass: staff holding SUBSCRIPTIONS_MAINTAIN are offered the edit, and no notice', async () => {
    renderWithProviders(
      <TestAuthProvider permissions={[PERMISSIONS.SUBSCRIPTIONS_MAINTAIN]}><Subscriptions /></TestAuthProvider>,
    )

    expect(await screen.findByRole('button', EDIT)).toBeInTheDocument()
    expect(screen.queryByText(/change subscription amounts/)).toBeNull()
  })
})
