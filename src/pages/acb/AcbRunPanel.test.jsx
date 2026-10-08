// @vitest-environment jsdom -- one A.C.B. run: its facts, its sheets, cancelling it, and the partial state
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { screen, within } from '@testing-library/react'
import { http, HttpResponse } from 'msw'

import AcbRunPanel from './AcbRunPanel'
import { renderWithProviders } from '../../test/renderWithProviders'
import { server } from '../../test/msw/server'
import { setupUser } from '../../test/user'
import { deferred } from '../../test/deferred'

const pageOf = (content) => ({ content, page: 0, size: 50, totalElements: content.length, totalPages: content.length ? 1 : 0 })

const RUN = {
  id: 41, actionMonth: '2026-10', status: 'PREPARED', version: 1, setupDate: '2026-09-18', creationDate: null,
  makeBy: '2026-10-17', makeByPassed: false, purgeDate: '2026-11-09', firstDueDate: '2026-10-20', lastDueDate: '2026-10-30', lineCount: 1, totalAmount: 174,
  totalsAgree: true, rateAmount: 174, rateEffectiveFrom: '2026-01-01', holidaySet: 'weekends-only', holidaysKnown: false,
  createdBy: 'system', approvedBy: null, startedAt: '2026-09-18T10:00+02:00', heartbeatAt: '2026-09-18T10:00+02:00',
  failureReason: null,
  exclusions: [
    { reason: 'NOT_DEBIT_ORDER', listed: false, detail: 'Payroll Deduction', memberCount: 48 },
    { reason: 'DAY_OUT_OF_WINDOW', listed: true, detail: null, memberCount: 1 },
  ],
}

const LINE = {
  id: 3, memberId: 7, membershipNumber: 'SYN-0007', surname: 'Testperson', initials: 'S', bankName: 'Synthetic Bank',
  branchCode: '632005', accountLastFour: '9911', accountType: null, debitDay: 25, actionDate: '2026-10-26',
  amount: 174, outcome: 'PENDING', rePresentsLineId: null, fileSequence: null, unpaidCode: null, unpaidReason: null,
  decision: null,
}

const EXCLUDED = { memberId: 12, membershipNumber: 'SYN-0012', surname: 'Synthetic', initials: 'T', reason: 'DAY_OUT_OF_WINDOW', detail: '5' }

beforeEach(() => {
  vi.spyOn(console, 'error').mockImplementation(() => {})
  server.use(
    http.get('*/api/acb/runs/41', () => HttpResponse.json(RUN)),
    http.get('*/api/acb/runs/41/lines', () => HttpResponse.json(pageOf([LINE]))),
    http.get('*/api/acb/runs/41/exclusions', () => HttpResponse.json(pageOf([EXCLUDED]))),
  )
})

const renderPanel = (onChanged = vi.fn()) => {
  renderWithProviders(<AcbRunPanel runId={41} onChanged={onChanged} onClose={() => {}} />)
  return onChanged
}

describe('the run', () => {
  it('shows its facts, a masked account, who is left out and why, and the counted ones', async () => {
    renderPanel()
    expect(await screen.findByRole('heading', { name: 'October 2026 run' })).toBeInTheDocument()
    expect(screen.getByText('Control total')).toBeInTheDocument()
    expect(await screen.findByText('•••• 9911')).toBeInTheDocument()
    expect(await screen.findByText('Debit day outside the 20th to the 31st')).toBeInTheDocument()
    expect(screen.getByText(/counted rather than listed: Payroll Deduction 48/)).toBeInTheDocument()
    expect(screen.getByText(/Public holidays are not yet taken into account/)).toBeInTheDocument()
  })

  it('before its file is made, the run says the day to make it by (D31)', async () => {
    renderPanel()
    expect(await screen.findByText('Make the file by')).toBeInTheDocument()
    expect(screen.getByText('2026/10/17')).toBeInTheDocument()
    expect(screen.queryByText('File made on')).not.toBeInTheDocument()
    expect(screen.queryByText(/has passed/)).not.toBeInTheDocument()
  })

  it('once made, it says the day the file was made -- the header\'s creation date, stamped then', async () => {
    server.use(http.get('*/api/acb/runs/41', () => HttpResponse.json({ ...RUN, status: 'APPROVED', creationDate: '2026-10-16' })))
    renderPanel()
    expect(await screen.findByText('File made on')).toBeInTheDocument()
    expect(screen.getByText('2026/10/16')).toBeInTheDocument()
    expect(screen.queryByText('Make the file by')).not.toBeInTheDocument()
  })

  it('a day to make it by that has passed is flagged, never refused -- the bank judges a late file (Q29)', async () => {
    server.use(http.get('*/api/acb/runs/41', () => HttpResponse.json({ ...RUN, makeByPassed: true })))
    renderPanel()
    expect(await screen.findByText(/The day to make this run's file by, 2026\/10\/17, has passed/)).toBeInTheDocument()
    expect(screen.getByText(/the bank decides whether it accepts a late file/)).toBeInTheDocument()
  })

  it('a declined run says why, from the short list, with the bank\'s own words beside it (D29)', async () => {
    server.use(http.get('*/api/acb/runs/41', () => HttpResponse.json({
      ...RUN, status: 'DECLINED', declinedReason: 'WRONG_DEBIT_DATE', declinedWords: 'Heritage Day on some lines',
    })))
    renderPanel()
    expect(await screen.findByText(/The bank declined this file: A wrong debit date — “Heritage Day on some lines”/)).toBeInTheDocument()
    expect(screen.getByText('Declined by the bank')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Cancel this run' })).not.toBeInTheDocument()
  })

  it('a run prepared after a decline names what it replaces (SD3-H)', async () => {
    server.use(http.get('*/api/acb/runs/41', () => HttpResponse.json({ ...RUN, replacesRunId: 40 })))
    renderPanel()
    expect(await screen.findByText('This run was prepared to replace the one the bank declined.')).toBeInTheDocument()
  })

  it('a run whose month gained a holiday after it was prepared says to prepare it again (SD3-F)', async () => {
    server.use(http.get('*/api/acb/runs/41', () => HttpResponse.json({ ...RUN, calendarAgrees: false })))
    renderPanel()
    expect(await screen.findByText(/A public holiday was added in this month after the run was prepared/)).toBeInTheDocument()
    expect(screen.getByText(/cancel it and prepare the month again/)).toBeInTheDocument()
  })

  it('the must-pass twin: a run whose calendar agrees says nothing about it', async () => {
    server.use(http.get('*/api/acb/runs/41', () => HttpResponse.json({ ...RUN, calendarAgrees: true })))
    renderPanel()
    expect(await screen.findByRole('heading', { name: 'October 2026 run' })).toBeInTheDocument()
    expect(screen.queryByText(/A public holiday was added/)).not.toBeInTheDocument()
  })

  it('approve is shown, refused, and says why -- the API does not yet know who is asking', async () => {
    renderPanel()
    const approve = await screen.findByRole('button', { name: 'Approve' })
    expect(approve).toBeDisabled()
    expect(approve).toHaveAccessibleDescription(/open when administrators sign in/)
  })

  it('a run that cannot be loaded says so, and its retry goes back to loading', async () => {
    server.use(http.get('*/api/acb/runs/41', () => HttpResponse.json({ status: 500, message: 'boom' }, { status: 500 })))
    const user = setupUser()
    renderPanel()
    expect(await screen.findByText('This run could not be loaded')).toBeInTheDocument()

    const held = deferred()
    server.use(http.get('*/api/acb/runs/41', async () => { await held.promise; return HttpResponse.json(RUN) }))
    try {
      await user.click(screen.getByRole('button', { name: /try again/i }))
      expect(await screen.findByText('Loading the run')).toBeInTheDocument()
    } finally {
      held.resolve()
    }
    expect(await screen.findByRole('heading', { name: 'October 2026 run' })).toBeInTheDocument()
  })

  it('partial: the debits fail, the run\'s facts and the exclusions still show, and the debits offer a retry', async () => {
    server.use(http.get('*/api/acb/runs/41/lines', () => HttpResponse.json({ status: 500, message: 'boom' }, { status: 500 })))
    renderPanel()
    expect(await screen.findByText('We could not load this run’s debits.')).toBeInTheDocument()
    expect(screen.getByText('Control total')).toBeInTheDocument()
    expect(await screen.findByText('Debit day outside the 20th to the 31st')).toBeInTheDocument()
  })
})

describe('cancelling', () => {
  it('sends the version the screen was showing, and hands back to the list', async () => {
    let sent = null
    server.use(http.post('*/api/acb/runs/41/cancel', async ({ request }) => {
      sent = await request.json()
      return HttpResponse.json({ ...RUN, status: 'CANCELLED', version: 2 })
    }))
    const user = setupUser()
    const onChanged = renderPanel()
    await user.click(await screen.findByRole('button', { name: 'Cancel this run' }))
    const dialog = await screen.findByRole('dialog', { name: 'Cancel the October 2026 run?' })
    await user.click(within(dialog).getByRole('button', { name: 'Cancel run' }))

    await vi.waitFor(() => expect(onChanged).toHaveBeenCalled())
    expect(sent).toEqual({ version: 1, reason: 'Cancelled from the Runs screen' })
  })

  it('someone else moved it first: the dialog says what it is now, from the refusal -- not "try again"', async () => {
    server.use(http.post('*/api/acb/runs/41/cancel', () => HttpResponse.json({
      status: 409, message: 'Someone else changed this run first.', code: 'RUN_VERSION_CONFLICT',
      details: { runId: '41', actionMonth: '2026-10', status: 'APPROVED', version: '2' },
    }, { status: 409 })))
    const user = setupUser()
    const onChanged = renderPanel()
    await user.click(await screen.findByRole('button', { name: 'Cancel this run' }))
    const dialog = await screen.findByRole('dialog', { name: 'Cancel the October 2026 run?' })
    await user.click(within(dialog).getByRole('button', { name: 'Cancel run' }))

    expect(await within(dialog).findByText(/it is now approved\. Nothing was cancelled/)).toBeInTheDocument()
    expect(onChanged).not.toHaveBeenCalled()
  })

  it('a run that is not a proposal offers no cancel at all', async () => {
    server.use(http.get('*/api/acb/runs/41', () => HttpResponse.json({ ...RUN, status: 'SUBMITTED' })))
    renderPanel()
    expect(await screen.findByRole('heading', { name: 'October 2026 run' })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Cancel this run' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Approve' })).not.toBeInTheDocument()
  })
})
