// @vitest-environment jsdom -- the member register and record: three things they stated without checking
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { screen, within } from '@testing-library/react'
import { http, HttpResponse } from 'msw'

import MembersPage from './Members'
import { renderWithProviders } from '../../test/renderWithProviders'
import { server } from '../../test/msw/server'
import { pageFixtures } from '../../test/msw/pageFixtures'
import { setupUser } from '../../test/user'
import { registerFails, registerHandlers } from '../../test/msw/memberRegister'

/**
 * WHAT THE REGISTER AND THE RECORD SAID WITHOUT CHECKING. `06.4`.
 *
 *   ① a member with no subscription on file was shown "R182.77, Monthly" -- two invented facts about their money;
 *   ② "The register below is up to date." was said whatever the register had done, including when it had failed,
 *     and the sentence before it began in lowercase;
 *   ③ an empty register with nothing set offered "Clear", which cleared nothing.
 *
 * Each has its must-pass, because each has a wrong fix that satisfies the guard alone: a record that never shows a
 * subscription, a notice that never says the register is complete, an empty state that never offers Clear.
 */

const MEMBER = {
  id: 7,
  membershipNumber: 'M0007',
  firstNames: 'Synthetic',
  surname: 'Subscriber',
  identityNumber: '9001010000080',
  status: 'Active',
  paymentType: 'Payroll Deduction',
}
const boom = () => HttpResponse.json({ status: 500, error: 'boom' }, { status: 500 })

async function openMembership(member) {
  const user = setupUser()
  server.use(...registerHandlers([member]))
  renderWithProviders(<MembersPage />)
  await user.click(await screen.findByRole('button', { name: /open the record for/i }))
  await user.click(await screen.findByRole('button', { name: /Membership & Subscriptions/i }))
  return within(await screen.findByTestId('member-panel'))
}

const emptyState = async () => (await screen.findAllByText(/No members match these criteria/i))[0].closest('[data-state]')

describe('Members', () => {
  beforeEach(() => {
    // Failures are reported through the sink as well as shown.
    vi.spyOn(console, 'error').mockImplementation(() => {})
    // Two `use` calls: the catch-all first, then each test's specific handlers, which prepend.
    server.use(...pageFixtures)
  })

  describe('① a subscription the record does not hold', () => {
    it('reads "Not on file", not R182.77 monthly', async () => {
      const panel = await openMembership(MEMBER)

      expect((await panel.findByText('Monthly Subscription (ZAR)')).nextElementSibling).toHaveTextContent('Not on file')
      expect(panel.getByText('Billing Frequency').nextElementSibling).toHaveTextContent('Not on file')
    })

    it('the must-pass: one the record does hold is shown, in rands (00.2)', async () => {
      const panel = await openMembership({ ...MEMBER, monthlySubscription: '250.00', subscriptionFrequency: 'Annually' })

      expect((await panel.findByText('Monthly Subscription (ZAR)')).nextElementSibling).toHaveTextContent(/^R\s250,00$/)
      expect(panel.getByText('Billing Frequency').nextElementSibling).toHaveTextContent('Annually')
    })
  })

  describe('② the notice about the edit panel’s lists', () => {
    it('does not call the register complete when the register failed too', async () => {
      server.use(http.get('*/api/banks', boom), registerFails(500))
      renderWithProviders(<MembersPage />)

      expect(await screen.findByText('Banks did not load, so the edit panel cannot offer them.')).toBeInTheDocument()
      expect(screen.queryByText(/up to date|below is complete/i)).not.toBeInTheDocument()
    })

    it('the must-pass: with the register loaded, it says the register is complete', async () => {
      server.use(http.get('*/api/banks', boom), ...registerHandlers([MEMBER]))
      renderWithProviders(<MembersPage />)

      expect(
        await screen.findByText('Banks did not load, so the edit panel cannot offer them. The register below is complete.'),
      ).toBeInTheDocument()
    })
  })

  describe('③ the empty register', () => {
    it('with nothing set, offers to capture a member -- not a Clear with nothing to clear', async () => {
      server.use(...registerHandlers([]))
      renderWithProviders(<MembersPage />)

      const empty = await emptyState()
      expect(within(empty).getByRole('button', { name: 'Capture member' })).toBeInTheDocument()
      expect(within(empty).queryByRole('button', { name: 'Clear' })).not.toBeInTheDocument()
    })

    it('the must-pass: with a criterion set, it offers Clear', async () => {
      server.use(...registerHandlers([MEMBER]))
      const user = setupUser()
      renderWithProviders(<MembersPage />)
      await screen.findByRole('button', { name: /open the record for/i })

      await user.click(screen.getByRole('combobox', { name: 'Status' }))
      await user.click(await screen.findByRole('option', { name: 'Suspended' }))

      const empty = await emptyState()
      expect(within(empty).getByRole('button', { name: 'Clear' })).toBeInTheDocument()
      expect(within(empty).queryByRole('button', { name: 'Capture member' })).not.toBeInTheDocument()
    })
  })
})
