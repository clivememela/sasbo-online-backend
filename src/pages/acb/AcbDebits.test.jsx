// @vitest-environment jsdom -- the ACB banking screen when a member's full record will not load
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { screen } from '@testing-library/react'
import { http, HttpResponse } from 'msw'

import AcbDebits from './AcbDebits'
import { renderWithProviders } from '../../test/renderWithProviders'
import { server } from '../../test/msw/server'
import { pageFixtures } from '../../test/msw/pageFixtures'
import { setupUser } from '../../test/user'

/**
 * A FAILED RECORD FETCH IS NOT THE RECORD. `06.4`.
 *
 * 🔴 Choosing a member here fetches their full record, and a failure used to open the REGISTER ROW
 * in its place -- `.catch(() => setSelectedMember(m))`. The row is the list's summary. On the screen
 * that changes a member's debit order that meant: banking fields the row does not carry printed as
 * "-", and *Edit Details* seeded its form with blanks that *Update* would have written over the
 * member's account number.
 *
 * The two cases are the guard and its must-pass: without the second, a page that never opens any
 * member would satisfy the first.
 */

/** What the search carries for a member (`04.1`): the account's last four digits, no identity number. */
const ROW = {
  id: 7,
  membershipNumber: 'SYN-0007',
  title: 'Mr',
  initials: 'S',
  surname: 'Testperson',
  bankName: 'Synthetic Bank',
  accountLastFour: '0001',
  debitDay: 25,
  status: 'Active',
}

const RECORD = { ...ROW, branch: 'Sample Branch', bankAccountNumber: '62000000001', debitDay: '25' }

const pageOf = (content) => ({ content, page: 0, size: 25, totalElements: content.length, totalPages: 1 })

async function chooseTheMember() {
  const user = setupUser()
  renderWithProviders(<AcbDebits />)
  await user.type(screen.getByLabelText('Surname'), 'Testperson')
  await user.click(screen.getByRole('button', { name: /^Search$/ }))
  const [choose] = await screen.findAllByRole('button', { name: /load banking details/i })
  await user.click(choose)
}

describe('AcbDebits, choosing a member', () => {
  beforeEach(() => {
    // The page reports the failure through the sink as well as showing it.
    vi.spyOn(console, 'error').mockImplementation(() => {})
    server.use(
      http.post('*/api/acb/members/search', () => HttpResponse.json(pageOf([ROW]))),
      ...pageFixtures,
    )
  })

  it('does not open the register row as the record when the record will not load', async () => {
    server.use(
      http.get('*/api/members/:id', () => HttpResponse.json({ status: 500, error: 'boom' }, { status: 500 })),
    )
    await chooseTheMember()

    expect(await screen.findByText(/banking details for SYN-0007 could not be loaded/i)).toBeInTheDocument()
    expect(screen.queryByText('Banking Details')).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Edit Details' })).not.toBeInTheDocument()
  })

  it('the must-pass: a record that loads opens with its banking details', async () => {
    server.use(http.get('*/api/members/:id', () => HttpResponse.json(RECORD)))
    await chooseTheMember()

    expect(await screen.findByText('62000000001')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Edit Details' })).toBeInTheDocument()
    expect(screen.queryByText(/could not be loaded/i)).not.toBeInTheDocument()
  })
})
