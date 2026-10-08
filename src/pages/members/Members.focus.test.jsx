// @vitest-environment jsdom -- where focus goes when the member record opens and closes
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { screen, waitFor, within } from '@testing-library/react'

import MembersPage from './Members'
import { renderWithProviders } from '../../test/renderWithProviders'
import { server } from '../../test/msw/server'
import { pageFixtures } from '../../test/msw/pageFixtures'
import { setupUser } from '../../test/user'
import { registerHandlers } from '../../test/msw/memberRegister'

/**
 * THE RECORD TAKES FOCUS WHEN IT OPENS, AND GIVES IT BACK WHEN IT CLOSES. `06.5`, F-P55.
 *
 * 🔴 Choosing a member moved no focus, so a keyboard user stayed on the row and a screen reader was told nothing had
 * opened; closing the record dropped focus to the page, losing their place in the register. The scrim was a second
 * "Close member details" button in the tab order and the accessibility tree.
 *
 * The close button is clicked in the first case, so that case is also the must-pass for the second: the record's own
 * close control is still a button anyone can reach.
 */

const MEMBER = {
  id: 7,
  membershipNumber: 'M0007',
  firstNames: 'Synthetic',
  surname: 'Focus',
  initials: 'S',
  status: 'Active',
  paymentType: 'Payroll Deduction',
}

describe('focus and the member record', () => {
  beforeEach(() => {
    vi.spyOn(console, 'error').mockImplementation(() => {})
    // Two `use` calls: the catch-all first, then the specific handlers, which prepend.
    server.use(...pageFixtures)
    server.use(...registerHandlers([MEMBER]))
  })

  it('opening a record moves focus into it, and closing it gives focus back to the row that opened it', async () => {
    const user = setupUser()
    renderWithProviders(<MembersPage />)
    const row = await screen.findByRole('button', { name: /open the record for.*Focus/i })

    await user.click(row)
    const panel = within(screen.getByTestId('member-panel'))
    await waitFor(() => expect(panel.getByText('Member record')).toHaveFocus())

    await user.click(panel.getByRole('button', { name: 'Close member details' }))
    await waitFor(() => expect(row).toHaveFocus())
  })

  it('the scrim is not a second "Close member details" button', async () => {
    const user = setupUser()
    renderWithProviders(<MembersPage />)
    await user.click(await screen.findByRole('button', { name: /open the record for.*Focus/i }))

    expect(screen.getAllByRole('button', { name: 'Close member details' })).toHaveLength(1)
  })
})
