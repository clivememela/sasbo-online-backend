// @vitest-environment jsdom -- the Re-Debits screen: every unpaid, its decision, and what a save says (04.1)
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { screen, waitFor, within } from '@testing-library/react'
import { http, HttpResponse } from 'msw'

import AcbDebits from './AcbDebits'
import { renderWithProviders } from '../../test/renderWithProviders'
import { server } from '../../test/msw/server'
import { pageFixtures } from '../../test/msw/pageFixtures'
import { setupUser } from '../../test/user'

/**
 * R4-1 and R4-3 (`04.1`): the newest run whose file went to the bank leads the screen with EVERY unpaid;
 * an undecided one comes with the decision its code suggests already chosen (Q40, Q44), always the
 * administrator's to change; *Confirm* records it and the screen says so FROM THE SERVER'S ANSWER; a
 * failed save says so and records nothing -- and one whose answer never came reads the list again
 * rather than claiming either. Each red case has its must-pass beside it.
 */

const pageOf = (content, size = 50) => ({ content, page: 0, size, totalElements: content.length, totalPages: content.length ? 1 : 0 })

const RUN = { id: 40, actionMonth: '2026-09', status: 'RETURNED', version: 3, lineCount: 30, totalAmount: 5220 }

const unpaid = (overrides) => ({
  id: 3, memberId: 7, membershipNumber: 'SYN-0007', surname: 'Testperson', initials: 'S', bankName: 'Synthetic Bank',
  branchCode: '250655', accountLastFour: '0001', accountType: null, debitDay: 25, actionDate: '2026-09-25',
  amount: 174, outcome: 'UNPAID', rePresentsLineId: null, fileSequence: 1, unpaidCode: '2',
  unpaidReason: 'NOT_PROVIDED_FOR', decision: null, suggestedDecision: 'RE_PRESENT', permissionNote: null,
  letterStatus: 'QUEUED', ...overrides,
})

let unpaidReads
let decisions

beforeEach(() => {
  vi.spyOn(console, 'error').mockImplementation(() => {})
  unpaidReads = 0
  decisions = []
  server.use(
    http.get('*/api/acb/runs', () => HttpResponse.json(pageOf([RUN], 12))),
    http.get('*/api/acb/runs/:id/unpaids', () => {
      unpaidReads += 1
      return HttpResponse.json(pageOf([
        unpaid(),
        unpaid({ id: 4, memberId: 8, membershipNumber: 'SYN-0008', surname: 'Sampleton', unpaidCode: '4',
          unpaidReason: 'PAYMENT_STOPPED', suggestedDecision: 'SUSPEND', letterStatus: 'RENDERED' }),
      ]))
    }),
    http.post('*/api/acb/runs/:id/lines/:lineId/decision', async ({ params, request }) => {
      const { decision } = await request.json()
      decisions.push({ run: params.id, line: params.lineId, decision })
      return HttpResponse.json(unpaid({ id: Number(params.lineId), decision, suggestedDecision: null }))
    }),
    ...pageFixtures,
  )
})

const rowOf = async (surname) => {
  const table = await screen.findByRole('table', { name: 'Unpaid debits and their decisions' })
  const cell = await within(table).findByText(new RegExp(surname))
  return cell.closest('tr')
}

describe('the unpaids of the latest returned run', () => {
  it('03.5 an unpaid decided Suspend shows Suspend member -- refused until sign-in, and saying so', async () => {
    server.use(http.get('*/api/acb/runs/:id/unpaids', () => HttpResponse.json(pageOf([
      unpaid(),
      unpaid({ id: 4, memberId: 8, membershipNumber: 'SYN-0008', surname: 'Sampleton', unpaidCode: '4',
        unpaidReason: 'PAYMENT_STOPPED', decision: 'SUSPEND', suggestedDecision: null }),
    ]))))
    renderWithProviders(<AcbDebits />)

    const suspend = within(await rowOf('Sampleton')).getByRole('button', { name: /^Suspend .*Sampleton as a member$/ })
    expect(suspend).toBeDisabled()
    expect(suspend).toHaveAccessibleDescription(/opens when administrators sign in to this system/)
    // The must-pass twin: an undecided unpaid has no such button.
    expect(within(await rowOf('Testperson')).queryByRole('button', { name: /as a member/ })).toBeNull()
  })

  it('lists every unpaid with its reason, code and letter -- the run named', async () => {
    renderWithProviders(<AcbDebits />)
    expect(await screen.findByRole('heading', { name: 'Unpaid debits — September 2026' })).toBeInTheDocument()
    const stopped = await rowOf('Sampleton')
    expect(within(stopped).getByText('Payment Stopped')).toBeInTheDocument()
    expect(within(stopped).getByText('code 4')).toBeInTheDocument()
    expect(within(stopped).getByText('Waiting to send')).toBeInTheDocument()
  })

  it('Q40, Q44: an undecided unpaid comes with the decision its code suggests already chosen', async () => {
    renderWithProviders(<AcbDebits />)
    const noFunds = await rowOf('Testperson')
    expect(within(noFunds).getByRole('combobox', { name: /Decision on/ })).toHaveValue('RE_PRESENT')
    const stopped = await rowOf('Sampleton')
    expect(within(stopped).getByRole('combobox', { name: /Decision on/ })).toHaveValue('SUSPEND')
  })
})

describe('R4-1: confirming records the decision, and the screen says so', () => {
  it('confirming the suggestion records it, and the row and the notice say what the server recorded', async () => {
    const user = setupUser()
    renderWithProviders(<AcbDebits />)
    const row = await rowOf('Testperson')
    await user.click(within(row).getByRole('button', { name: /Confirm the decision/ }))

    expect(await screen.findByText('Recorded: S Testperson’s unpaid is presented again in the next run.')).toBeInTheDocument()
    expect(decisions).toEqual([{ run: '40', line: '3', decision: 'RE_PRESENT' }])
    const recorded = await rowOf('Testperson')
    expect(within(recorded).getByText('Present again')).toBeInTheDocument()
    expect(within(recorded).getByText(/recorded/)).toBeInTheDocument()
    expect(within(recorded).queryByRole('button', { name: /Confirm/ })).not.toBeInTheDocument()
  })

  it('the administrator\'s choice wins over the suggestion', async () => {
    const user = setupUser()
    renderWithProviders(<AcbDebits />)
    const row = await rowOf('Testperson')
    await user.selectOptions(within(row).getByRole('combobox', { name: /Decision on/ }), 'SUSPEND')
    await user.click(within(row).getByRole('button', { name: /Confirm the decision/ }))

    expect(await screen.findByText(/unpaid is suspended/)).toBeInTheDocument()
    expect(decisions).toEqual([{ run: '40', line: '3', decision: 'SUSPEND' }])
  })

  it('🔒 an answer that does not carry the decision is not a recording: the list is read again', async () => {
    server.use(http.post('*/api/acb/runs/:id/lines/:lineId/decision', () => HttpResponse.json(unpaid())))
    const user = setupUser()
    renderWithProviders(<AcbDebits />)
    const row = await rowOf('Testperson')
    await user.click(within(row).getByRole('button', { name: /Confirm the decision/ }))

    expect(await screen.findByText(/could not be confirmed/)).toBeInTheDocument()
    expect(screen.queryByText(/^Recorded:/)).not.toBeInTheDocument()
    await waitFor(() => expect(unpaidReads).toBe(2))
  })
})

describe('R4-3: a failed save says so and records nothing', () => {
  it('a refusal says it was not recorded, and the row still offers the decision', async () => {
    server.use(http.post('*/api/acb/runs/:id/lines/:lineId/decision', () =>
      HttpResponse.json({ status: 400, message: 'The decision was malformed.', code: 'REQUEST_MALFORMED', details: {} }, { status: 400 })))
    const user = setupUser()
    renderWithProviders(<AcbDebits />)
    const row = await rowOf('Testperson')
    await user.click(within(row).getByRole('button', { name: /Confirm the decision/ }))

    const alert = await screen.findByRole('alert')
    expect(alert).toHaveTextContent(/was not recorded\. Nothing was written/)
    expect(alert).toHaveTextContent('The decision was malformed.')
    expect(within(await rowOf('Testperson')).getByRole('button', { name: /Confirm the decision/ })).toBeEnabled()
    expect(unpaidReads).toBe(1)
  })

  it('a decision someone else recorded first is said as such, and the list is read again', async () => {
    server.use(http.post('*/api/acb/runs/:id/lines/:lineId/decision', () =>
      HttpResponse.json({ status: 409, message: 'The decision on this unpaid is already SUSPEND.', code: 'UNPAID_ALREADY_RECORDED',
        details: { lineId: '3' } }, { status: 409 })))
    const user = setupUser()
    renderWithProviders(<AcbDebits />)
    const row = await rowOf('Testperson')
    await user.click(within(row).getByRole('button', { name: /Confirm the decision/ }))

    expect(await screen.findByText(/was already recorded\. Nothing was changed/)).toBeInTheDocument()
    await waitFor(() => expect(unpaidReads).toBe(2))
  })

  it('an answer that never came does not say it was not recorded -- and reads the list again', async () => {
    server.use(http.post('*/api/acb/runs/:id/lines/:lineId/decision', () => HttpResponse.error()))
    const user = setupUser()
    renderWithProviders(<AcbDebits />)
    const row = await rowOf('Testperson')
    await user.click(within(row).getByRole('button', { name: /Confirm the decision/ }))

    const alert = await screen.findByRole('alert')
    expect(alert).toHaveTextContent(/could not be confirmed\. It may have been recorded/)
    expect(alert).not.toHaveTextContent(/not recorded|Nothing was written/)
    await waitFor(() => expect(unpaidReads).toBe(2))
  })
})

describe('the Invalid Accounts list (Q11) and the banking sheet', () => {
  it('the reason filter asks the server for one reason\'s unpaids', async () => {
    const asked = []
    server.use(http.get('*/api/acb/runs/:id/unpaids', ({ request }) => {
      asked.push(new URL(request.url).searchParams.get('reason'))
      return HttpResponse.json(pageOf([unpaid({ unpaidCode: '8', unpaidReason: 'INVALID_ACCOUNT_NUMBER', suggestedDecision: null })]))
    }))
    const user = setupUser()
    renderWithProviders(<AcbDebits />)
    await rowOf('Testperson')
    await user.selectOptions(screen.getByLabelText('Reason'), 'INVALID_ACCOUNT_NUMBER')

    await waitFor(() => expect(asked).toEqual([null, 'INVALID_ACCOUNT_NUMBER']))
  })

  it('an Invalid Account Number unpaid opens the member\'s banking sheet; a no-funds one offers none', async () => {
    server.use(
      http.get('*/api/acb/runs/:id/unpaids', () => HttpResponse.json(pageOf([
        unpaid({ unpaidCode: '8', unpaidReason: 'INVALID_ACCOUNT_NUMBER', suggestedDecision: null }),
        unpaid({ id: 4, memberId: 8, membershipNumber: 'SYN-0008', surname: 'Sampleton' }),
      ]))),
      http.get('*/api/members/:id', ({ params }) => HttpResponse.json({
        id: Number(params.id), membershipNumber: 'SYN-0007', title: 'Mr', initials: 'S', surname: 'Testperson',
        bankName: 'Synthetic Bank', branch: 'Sample Branch', bankAccountNumber: '62000000001', debitDay: 25, status: 'Active',
      })),
    )
    const user = setupUser()
    renderWithProviders(<AcbDebits />)
    expect(within(await rowOf('Sampleton')).queryByRole('button', { name: /Banking details/ })).not.toBeInTheDocument()
    await user.click(within(await rowOf('Testperson')).getByRole('button', { name: /Banking details for/ }))

    expect(await screen.findByText('62000000001')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Edit Details' })).toBeInTheDocument()
  })
})

describe('when there is no run to decide on', () => {
  it('says no run has gone to the bank yet -- not an empty list of unpaids', async () => {
    server.use(http.get('*/api/acb/runs', () => HttpResponse.json(pageOf([{ ...RUN, id: 41, actionMonth: '2026-10', status: 'PREPARED' }], 12))))
    renderWithProviders(<AcbDebits />)
    expect(await screen.findByText(/No run has gone to the bank yet/)).toBeInTheDocument()
    expect(screen.queryByRole('table', { name: 'Unpaid debits and their decisions' })).not.toBeInTheDocument()
  })

  it('a runs list that fails says so, with a retry', async () => {
    server.use(http.get('*/api/acb/runs', () => HttpResponse.json({ status: 500, error: 'boom' }, { status: 500 })))
    renderWithProviders(<AcbDebits />)
    expect(await screen.findByText('The runs could not be loaded')).toBeInTheDocument()
    expect(screen.queryByText(/No run has gone to the bank yet/)).not.toBeInTheDocument()
  })
})
