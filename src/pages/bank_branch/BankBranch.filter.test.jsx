// @vitest-environment jsdom -- the Bank/Branch register's own bank filter
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { screen } from '@testing-library/react'
import { http, HttpResponse } from 'msw'

import BankBranch from './BankBranch'
import { renderWithProviders } from '../../test/renderWithProviders'
import { server } from '../../test/msw/server'
import { setupUser } from '../../test/user'

/**
 * 🔴 R2-F5's SECOND SITE — FIX-NOW, found during P9's execution, 2026-09-17.
 *
 * The same shape as Forms: `SearchableSelect` reports a VALUE (`onChange(value, option)`), and `BankBranch.jsx` gave
 * it `handleInputChange("bankName")`, which reads `event.target.value`. Choosing a bank on the branch register threw
 * and took the screen to the error boundary. Members and Recruiters defend against both shapes
 * (`event && event.target ? event.target.value : event`), Subscriptions takes the value -- these two did neither.
 */

const BRANCHES = [
  { id: 1, bankName: 'ABSA BANK', bankAbbreviation: 'ABSA', branchName: 'Sandton', area: 'Gauteng' },
  { id: 2, bankName: 'CAPITEC', bankAbbreviation: 'CAP', branchName: 'Century City', area: 'Western Cape' },
]

describe('the Bank/Branch register’s bank filter', () => {
  beforeEach(() => {
    vi.spyOn(console, 'error').mockImplementation(() => {})
    server.use(http.get('*/api/banks/branches', () => HttpResponse.json(BRANCHES)))
  })

  it('filters the register to the chosen bank instead of falling over', async () => {
    const user = setupUser()
    renderWithProviders(<BankBranch />)
    await screen.findByText('Sandton')

    await user.click(await screen.findByRole('combobox', { name: /^Bank/ }))
    await user.click(await screen.findByRole('option', { name: 'CAPITEC' }))

    expect(await screen.findByText('Century City')).toBeInTheDocument()
    expect(screen.queryByText('Sandton')).not.toBeInTheDocument()
    expect(screen.queryByText(/could not be displayed/i)).not.toBeInTheDocument()
  })

  it('the must-pass: with no bank chosen, every branch is listed', async () => {
    renderWithProviders(<BankBranch />)

    expect(await screen.findByText('Sandton')).toBeInTheDocument()
    expect(screen.getByText('Century City')).toBeInTheDocument()
  })
})
