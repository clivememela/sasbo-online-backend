// @vitest-environment jsdom -- the member register's bank criterion and the record's bank field, when the bank list fails
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { screen, within } from '@testing-library/react'
import { http, HttpResponse } from 'msw'

import MembersPage from './Members'
import { renderWithProviders } from '../../test/renderWithProviders'
import { server } from '../../test/msw/server'
import { pageFixtures } from '../../test/msw/pageFixtures'
import { setupUser } from '../../test/user'
import { registerHandlers } from '../../test/msw/memberRegister'

/**
 * THE BANK LIST, WHERE IT IS CHOSEN. `06.4`.
 *
 * 🔴 The page said "Banks did not load" once, in a notice above the register. The criterion's Bank picker
 * and the record's Bank Where Employed field -- where an administrator actually looks for a bank -- read as
 * pickers with nothing in them. They say so themselves now.
 *
 * The must-pass is the loaded list: without it, a field that always said it failed would pass.
 */

const MEMBER = {
  id: 7,
  membershipNumber: 'M0007',
  firstNames: 'Synthetic',
  surname: 'Banker',
  identityNumber: '9001010000080',
  status: 'Active',
  paymentType: 'Payroll Deduction',
  bankName: 'ABSA',
}
const boom = () => HttpResponse.json({ status: 500, error: 'boom' }, { status: 500 })

async function openEmploymentForEdit(user) {
  renderWithProviders(<MembersPage />)
  await user.click(await screen.findByRole('button', { name: /open the record for/i }))
  await user.click(await screen.findByRole('button', { name: /Employment & Bank/i }))
  await user.click(screen.getByRole('button', { name: /Edit Member/i }))
  return within(await screen.findByTestId('member-panel'))
}

describe('the bank list on Members', () => {
  beforeEach(() => {
    vi.spyOn(console, 'error').mockImplementation(() => {})
    // Two `use` calls: the catch-all first, then the specific handlers, which prepend.
    server.use(...pageFixtures)
    server.use(...registerHandlers([MEMBER]))
  })

  it('that failed says so at the register’s Bank criterion', async () => {
    server.use(http.get('*/api/banks', boom))
    renderWithProviders(<MembersPage />)

    expect(await screen.findByText('Bank could not be loaded.')).toBeInTheDocument()
  })

  it('that failed says so at the record’s Bank Where Employed field', async () => {
    server.use(http.get('*/api/banks', boom))
    const panel = await openEmploymentForEdit(setupUser())

    expect(await panel.findByText('Bank Where Employed could not be loaded.')).toBeInTheDocument()
  })

  it('the must-pass: a list that loaded offers its banks and says nothing of the kind', async () => {
    server.use(http.get('*/api/banks', () => HttpResponse.json([{ name: 'ABSA' }, { name: 'Capitec' }])))
    const panel = await openEmploymentForEdit(setupUser())

    const bank = await panel.findByLabelText(/^Bank Where Employed/i)
    expect(await within(bank).findByRole('option', { name: 'Capitec' })).toBeInTheDocument()
    expect(screen.queryByText(/could not be loaded/i)).not.toBeInTheDocument()
  })
})
