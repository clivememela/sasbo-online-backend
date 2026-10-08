// @vitest-environment jsdom -- the termination report on the Re-Debits screen (03.5)
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { screen, within } from '@testing-library/react'
import { http, HttpResponse } from 'msw'

import AcbTerminationReport from './AcbTerminationReport'
import { renderWithProviders } from '../../test/renderWithProviders'
import { server } from '../../test/msw/server'
import { pageFixtures } from '../../test/msw/pageFixtures'

/**
 * 03.5 -- the termination report, shown: it was the server's alone. Three unpaid presentations terminate a
 * membership (Q27), and terminating is refused to every caller until administrators sign in (B1) -- so the action
 * is shown disabled with its reason, as Approve is, and a member already terminated shows the status instead.
 */

const candidate = (overrides) => ({
  memberId: 7, membershipNumber: 'SYN-0007', surname: 'Testperson', unpaidCount: 3, terminate: true,
  twelveMonthTotal: 3, unpaidMonths: ['2026-07', '2026-08', '2026-09'], openingUnpaids: 0, status: 'Active',
  ...overrides,
})

const report = (members) => ({
  countingFrom: '2026-07',
  blindness: 'Counted from the first month this system sent a file.',
  members: { content: members, page: 0, size: 50, totalElements: members.length, totalPages: members.length ? 1 : 0 },
})

const answer = (body, status = 200) =>
  server.use(http.get('*/api/acb/termination-report', () => HttpResponse.json(body, { status })), ...pageFixtures)

const rowOf = async (surname) => {
  const table = await screen.findByRole('table', { name: 'Members the termination rule reaches, or is one away from' })
  return (await within(table).findByText(surname)).closest('tr')
}

beforeEach(() => {
  vi.spyOn(console, 'error').mockImplementation(() => {})
})

describe('the termination report (03.5)', () => {
  it('names who the rule terminates and who is one away -- the action refused until sign-in, and saying so', async () => {
    answer(report([
      candidate(),
      candidate({ memberId: 8, membershipNumber: 'SYN-0008', surname: 'Sampleton', unpaidCount: 2, terminate: false,
        unpaidMonths: ['2026-08', '2026-09'], twelveMonthTotal: 2 }),
    ]))
    renderWithProviders(<AcbTerminationReport />)

    const due = await rowOf('Testperson')
    expect(within(due).getByText('three — the rule terminates')).toBeInTheDocument()
    const terminate = within(due).getByRole('button', { name: 'Terminate Testperson’s membership' })
    expect(terminate).toBeDisabled()
    expect(terminate).toHaveAccessibleDescription(/opens when administrators sign in to this system/)

    // The must-pass twin: one away is listed, and has no action.
    const atTwo = await rowOf('Sampleton')
    expect(within(atTwo).getByText('one more makes three')).toBeInTheDocument()
    expect(within(atTwo).queryByRole('button', { name: /Terminate/ })).toBeNull()

    expect(screen.getByText(/Counted from July 2026\./)).toBeInTheDocument()
    expect(screen.getByText('Counted from the first month this system sent a file.')).toBeInTheDocument()
  })

  it('shows a member already terminated by status, with no action and no reason to give', async () => {
    answer(report([candidate({ status: 'Terminated' })]))
    renderWithProviders(<AcbTerminationReport />)

    const row = await rowOf('Testperson')
    expect(within(row).getByText('Terminated')).toBeInTheDocument()
    expect(within(row).queryByRole('button', { name: /Terminate/ })).toBeNull()
    expect(screen.queryByText(/opens when administrators sign in/)).toBeNull()
  })

  it('says so when nobody is at two unpaids', async () => {
    answer(report([]))
    renderWithProviders(<AcbTerminationReport />)
    expect(await screen.findByText('Nobody at two unpaids')).toBeInTheDocument()
  })

  it('says so when the report cannot be read -- never an empty list', async () => {
    answer({ status: 500, message: 'boom' }, 500)
    renderWithProviders(<AcbTerminationReport />)
    expect(await screen.findByText('We could not load the termination report.')).toBeInTheDocument()
    expect(screen.queryByText('Nobody at two unpaids')).toBeNull()
  })
})
