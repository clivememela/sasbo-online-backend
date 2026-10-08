// @vitest-environment jsdom -- Bank Listing when the universal branch codes do not arrive
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { screen } from '@testing-library/react'
import { http, HttpResponse } from 'msw'

import BankListing from './BankListing'
import { renderWithProviders } from '../../test/renderWithProviders'
import { server } from '../../test/msw/server'

/**
 * A PARTIAL LISTING SAYS IT IS PARTIAL. `06.4`, F-P54.
 *
 * 🔴 The codes come from a second request, and its failure used to become `[]` inside
 * `bankService`. Every bank still listed -- with a dash where its code belongs, and a search by code
 * that answered "No banks match these criteria". A dash says the bank has no code; nobody had asked.
 *
 * The second case is the must-pass: without it, a page that always said the codes were missing, or
 * disabled the field for good, would satisfy the first.
 */

const BRANCHES = [{ id: 1, bankName: 'Standard Bank', bankAbbreviation: 'SBSA', branchCode: '051001' }]
const branchesOk = () => http.get('*/api/banks/branches', () => HttpResponse.json(BRANCHES))

describe('Bank Listing, and the universal branch codes', () => {
  beforeEach(() => {
    // The page reports the missing codes through the sink as well as showing them.
    vi.spyOn(console, 'error').mockImplementation(() => {})
  })

  it('lists the banks and SAYS their codes are missing, instead of a dash per code', async () => {
    server.use(
      branchesOk(),
      http.get('*/api/banks', () => HttpResponse.json({ status: 500, error: 'boom' }, { status: 500 })),
    )
    renderWithProviders(<BankListing />)

    expect(await screen.findByText('Universal branch codes could not be loaded.')).toBeInTheDocument()
    expect(screen.getAllByText('Standard Bank').length).toBeGreaterThan(0)
    expect(screen.getAllByText('Unavailable').length).toBeGreaterThan(0)
    expect(screen.getByLabelText('Universal branch code')).toBeDisabled()
  })

  it('the must-pass: when both requests answer, the codes are shown and nothing is said', async () => {
    server.use(
      branchesOk(),
      http.get('*/api/banks', () => HttpResponse.json([{ name: 'Standard Bank', universalBranchCode: '051001' }])),
    )
    renderWithProviders(<BankListing />)

    expect((await screen.findAllByText('051001')).length).toBeGreaterThan(0)
    expect(screen.queryByText(/could not be loaded/i)).not.toBeInTheDocument()
    expect(screen.getByLabelText('Universal branch code')).toBeEnabled()
  })
})
