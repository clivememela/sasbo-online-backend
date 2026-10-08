// @vitest-environment jsdom -- the A.C.B. Runs screen: the list, preparing a run, and its refusals
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { act, screen, within } from '@testing-library/react'
import { http, HttpResponse } from 'msw'

import AcbRuns from './AcbRuns'
import { acbRunService } from '../../services/acbRunService'
import { renderWithProviders } from '../../test/renderWithProviders'
import { server } from '../../test/msw/server'
import { setupUser } from '../../test/user'
import { deferred } from '../../test/deferred'
import { expectNoA11yViolations } from '../../test/assertions'

/**
 * `01.5` -- A.C.B. -> Runs.
 *
 * The rules under test are the screen's, not the server's: a 202 is followed by polling and never
 * by a second POST; a write that may have landed says so and re-reads; a refusal names what the
 * server said, from its `code` and `details`. Every red case has its must-pass beside it.
 */

const pageOf = (content) => ({ content, page: 0, size: 12, totalElements: content.length, totalPages: content.length ? 1 : 0 })

const SUMMARY = {
  id: 41, actionMonth: '2026-10', status: 'PREPARED', version: 1, setupDate: '2026-09-18', lineCount: 30,
  totalAmount: 5220, totalsAgree: true, holidaysKnown: false, failureReason: null,
}

const runOf = (overrides) => ({
  ...SUMMARY, creationDate: null, makeBy: '2026-10-17', makeByPassed: false, purgeDate: '2026-11-09', firstDueDate: '2026-10-20',
  lastDueDate: '2026-10-30', rateAmount: 174, rateEffectiveFrom: '2026-01-01', holidaySet: 'weekends-only',
  createdBy: 'system', approvedBy: null, startedAt: '2026-09-18T10:00+02:00', heartbeatAt: '2026-09-18T10:00+02:00',
  exclusions: [], ...overrides,
})

const EMPTY = pageOf([])

const CALENDAR = { name: 'za-statutory', knownUntil: '2027-12-31', endingSoon: false, year: 2026, holidays: [] }

const RATES = { rates: [{ effectiveFrom: '2026-01-01', amount: 174, source: 'From January' }], januaryNotice: null }

beforeEach(() => {
  vi.spyOn(console, 'error').mockImplementation(() => {})
  server.use(
    http.get('*/api/acb/runs', () => HttpResponse.json(pageOf([SUMMARY]))),
    http.get('*/api/acb/runs/:id', ({ params }) => HttpResponse.json(runOf({ id: Number(params.id) }))),
    http.get('*/api/acb/runs/:id/lines', () => HttpResponse.json(EMPTY)),
    http.get('*/api/acb/runs/:id/exclusions', () => HttpResponse.json(EMPTY)),
    http.get('*/api/acb/holidays', () => HttpResponse.json(CALENDAR)),
    http.get('*/api/acb/rates', () => HttpResponse.json(RATES)),
  )
})

const openPrepare = async (user) => {
  await user.click(await screen.findByRole('button', { name: /^Prepare / }))
  return screen.findByRole('dialog', { name: /^Prepare the .* run\?$/ })
}

describe('the rate (Q43)', () => {
  it('says beside Prepare what the chosen month will charge, and since when', async () => {
    const user = setupUser()
    renderWithProviders(<AcbRuns />)
    await screen.findByRole('button', { name: 'Open the October 2026 run' })
    await user.selectOptions(screen.getByLabelText('Action month'), '2026-10')
    expect(await screen.findByText(/^October 2026 charges R\s?174,00 a member — the rate since January 2026\.$/)).toBeInTheDocument()
  })

  it('shows the January notice when the server gives one -- a notice, never a refusal', async () => {
    server.use(http.get('*/api/acb/rates', () => HttpResponse.json({ ...RATES, januaryNotice: '2027-01 will charge R 174.00, the rate since 2026-01.' })))
    renderWithProviders(<AcbRuns />)
    expect(await screen.findByText('2027-01 will charge R 174.00, the rate since 2026-01.')).toBeInTheDocument()
  })
})

describe('a month the bank declined (D29, SD3-H)', () => {
  it('the prepare dialog warns when the month\'s last file was declined for a wrong date -- a warning, never a refusal', async () => {
    const declined = { ...SUMMARY, id: 40, status: 'DECLINED', declinedReason: 'WRONG_DEBIT_DATE', calendarAgrees: true }
    server.use(http.get('*/api/acb/runs', () => HttpResponse.json(pageOf([declined]))))
    const user = setupUser()
    renderWithProviders(<AcbRuns />)
    await screen.findByRole('button', { name: 'Open the October 2026 run' })
    await user.selectOptions(screen.getByLabelText('Action month'), '2026-10')
    const dialog = await openPrepare(user)
    expect(within(dialog).getByText(/declined the October 2026 file for a wrong debit date/)).toBeInTheDocument()
    expect(within(dialog).getByRole('button', { name: 'Prepare' })).toBeEnabled()
  })

  it('the must-pass twin: a month with no decline says nothing in the dialog', async () => {
    const user = setupUser()
    renderWithProviders(<AcbRuns />)
    await screen.findByRole('button', { name: 'Open the October 2026 run' })
    await user.selectOptions(screen.getByLabelText('Action month'), '2026-10')
    const dialog = await openPrepare(user)
    expect(within(dialog).queryByText(/declined/)).not.toBeInTheDocument()
  })
})

describe('the holiday calendar (D30)', () => {
  it('from sixty days before its end, the screen names the date it is known until (SD3-G)', async () => {
    server.use(http.get('*/api/acb/holidays', () => HttpResponse.json({ ...CALENDAR, endingSoon: true })))
    renderWithProviders(<AcbRuns />)
    expect(await screen.findByText(/The holiday calendar is known until 31 Dec 2027\./)).toBeInTheDocument()
  })

  it('the must-pass twin: a calendar far from its end adds nothing to the screen', async () => {
    renderWithProviders(<AcbRuns />)
    expect(await screen.findByRole('button', { name: 'Open the October 2026 run' })).toBeInTheDocument()
    expect(screen.queryByText(/holiday calendar/i)).not.toBeInTheDocument()
  })

  it('a calendar that cannot be read leaves the list as it was -- preparing still refuses a month it does not reach', async () => {
    server.use(http.get('*/api/acb/holidays', () => HttpResponse.json({ status: 500, message: 'boom' }, { status: 500 })))
    renderWithProviders(<AcbRuns />)
    expect(await screen.findByRole('button', { name: 'Open the October 2026 run' })).toBeInTheDocument()
    expect(screen.queryByText(/could not load the runs/i)).not.toBeInTheDocument()
  })
})

describe('the list', () => {
  it('shows each run with its state and control total, and opens one', async () => {
    const user = setupUser()
    const { container } = renderWithProviders(<AcbRuns />)
    const open = await screen.findByRole('button', { name: 'Open the October 2026 run' })
    expect(screen.getByText('Prepared')).toBeInTheDocument()
    expect(screen.getByText(/5\s?220,00/)).toBeInTheDocument()
    await expectNoA11yViolations(container)

    await user.click(open)
    expect(await screen.findByRole('heading', { name: 'October 2026 run' })).toBeInTheDocument()
  })

  it('an empty list is the first screen, and says what to do -- it is not a failure', async () => {
    server.use(http.get('*/api/acb/runs', () => HttpResponse.json(EMPTY)))
    renderWithProviders(<AcbRuns />)
    expect(await screen.findByText('No runs yet')).toBeInTheDocument()
    expect(screen.queryByText(/could not load the runs/i)).not.toBeInTheDocument()
  })

  it('a failed list says so, and its retry goes BACK TO LOADING before the rows arrive', async () => {
    server.use(http.get('*/api/acb/runs', () => HttpResponse.json({ status: 500, message: 'boom' }, { status: 500 })))
    const user = setupUser()
    renderWithProviders(<AcbRuns />)
    expect(await screen.findByText('We could not load the runs.')).toBeInTheDocument()

    const held = deferred()
    server.use(http.get('*/api/acb/runs', async () => { await held.promise; return HttpResponse.json(pageOf([SUMMARY])) }))
    try {
      await user.click(screen.getByRole('button', { name: /try again/i }))
      // The retry's own state: without this assertion a retry that never showed loading would pass.
      expect(await screen.findByText(/Loading runs/)).toBeInTheDocument()
      expect(screen.queryByText('We could not load the runs.')).not.toBeInTheDocument()
    } finally {
      held.resolve()
    }
    expect(await screen.findByRole('button', { name: 'Open the October 2026 run' })).toBeInTheDocument()
  })
})

describe('preparing a run', () => {
  it('one POST, then the run is re-read until it settles, and the screen says what it holds', async () => {
    let posts = 0
    let reads = 0
    server.use(
      http.post('*/api/acb/runs', async ({ request }) => {
        posts += 1
        const { actionMonth } = await request.json()
        return HttpResponse.json(runOf({ id: 42, actionMonth, status: 'PREPARING', lineCount: 0, totalAmount: 0 }), { status: 202 })
      }),
      http.get('*/api/acb/runs/42', () => {
        reads += 1
        return HttpResponse.json(runOf({ id: 42, status: reads < 3 ? 'PREPARING' : 'PREPARED' }))
      }),
    )
    const user = setupUser()
    renderWithProviders(<AcbRuns pollDelaysMs={[0, 0, 0, 0, 0, 0]} />)
    const dialog = await openPrepare(user)
    await user.click(within(dialog).getByRole('button', { name: 'Prepare' }))

    expect(await screen.findByText(/run is prepared: 30 debits/)).toBeInTheDocument()
    expect(posts).toBe(1)
    expect(reads).toBeGreaterThanOrEqual(3)
  })

  it('an older list answer arriving last does not put the older list back (R3-F4)', async () => {
    // Read 2 is the prepare's re-read, held; read 3 is the settle's, answered at once with the new run.
    const NEW = { ...SUMMARY, id: 42, actionMonth: '2027-01' }
    const held = deferred()
    let lists = 0
    server.use(
      http.get('*/api/acb/runs', async () => {
        lists += 1
        if (lists === 2) { await held.promise; return HttpResponse.json(pageOf([SUMMARY])) }
        return HttpResponse.json(pageOf(lists === 1 ? [SUMMARY] : [NEW, SUMMARY]))
      }),
      http.post('*/api/acb/runs', () => HttpResponse.json(runOf({ id: 42, actionMonth: '2027-01', status: 'PREPARING' }), { status: 202 })),
      http.get('*/api/acb/runs/42', () => HttpResponse.json(runOf({ id: 42, actionMonth: '2027-01' }))),
    )
    const list = vi.spyOn(acbRunService, 'list')
    const user = setupUser()
    renderWithProviders(<AcbRuns pollDelaysMs={[0]} />)
    try {
      await user.click(within(await openPrepare(user)).getByRole('button', { name: 'Prepare' }))
      expect(await screen.findByRole('button', { name: 'Open the January 2027 run' })).toBeInTheDocument()
    } finally {
      held.resolve()
    }
    // Wait on the stale read itself, never on a clock: the screen's own `.then` was attached to this
    // promise first, so it has run by the time this resumes, and `act` flushes whatever it rendered.
    expect(lists).toBe(3)
    await act(async () => { await list.mock.results[1].value })
    expect(screen.getByRole('button', { name: 'Open the January 2027 run' })).toBeInTheDocument()
  })

  it('must-pass for the ceiling: a run still preparing when the backoff runs out is "still preparing", not a failure', async () => {
    server.use(
      http.post('*/api/acb/runs', () => HttpResponse.json(runOf({ id: 42, status: 'PREPARING' }), { status: 202 })),
      http.get('*/api/acb/runs/42', () => HttpResponse.json(runOf({ id: 42, status: 'PREPARING' }))),
    )
    const user = setupUser()
    renderWithProviders(<AcbRuns pollDelaysMs={[0, 0]} />)
    await user.click(within(await openPrepare(user)).getByRole('button', { name: 'Prepare' }))
    expect(await screen.findByText(/is still preparing\. Check back/)).toBeInTheDocument()
  })

  it('a month already held opens that run and says what to do about it -- from the refusal\'s details', async () => {
    server.use(http.post('*/api/acb/runs', () => HttpResponse.json({
      status: 409, message: 'The run for 2026-10 already exists.', code: 'RUN_ALREADY_EXISTS',
      details: { runId: '41', actionMonth: '2026-10', status: 'PREPARED', version: '1' },
    }, { status: 409 })))
    const user = setupUser()
    renderWithProviders(<AcbRuns />)
    await user.click(within(await openPrepare(user)).getByRole('button', { name: 'Prepare' }))

    expect(await screen.findByText(/already exists and is prepared\. To prepare the month again, cancel it first\./)).toBeInTheDocument()
    expect(await screen.findByRole('heading', { name: 'October 2026 run' })).toBeInTheDocument()
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
  })

  it('a write that may have landed is never repeated: it says so, closes, and re-reads the list', async () => {
    let posts = 0
    let lists = 0
    server.use(
      http.post('*/api/acb/runs', () => { posts += 1; return HttpResponse.json({ status: 503, message: 'down' }, { status: 503 }) }),
      http.get('*/api/acb/runs', () => { lists += 1; return HttpResponse.json(pageOf([SUMMARY])) }),
    )
    const user = setupUser()
    renderWithProviders(<AcbRuns />)
    await user.click(within(await openPrepare(user)).getByRole('button', { name: 'Prepare' }))

    expect(await screen.findByText(/could not be confirmed\. It may have been started/)).toBeInTheDocument()
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    expect(posts).toBe(1)
    expect(lists).toBeGreaterThanOrEqual(2)
  })

  it('must-pass for that: a 4xx wrote nothing, and says so inside the dialog, which stays open', async () => {
    server.use(http.post('*/api/acb/runs', () => HttpResponse.json({
      status: 422, message: 'No subscription rate is in force for 2026-10.', code: 'RATE_NOT_IN_FORCE', details: {},
    }, { status: 422 })))
    const user = setupUser()
    renderWithProviders(<AcbRuns />)
    const dialog = await openPrepare(user)
    await user.click(within(dialog).getByRole('button', { name: 'Prepare' }))

    expect(await within(dialog).findByText(/was not prepared\. Nothing was written — No subscription rate/)).toBeInTheDocument()
    expect(screen.getByRole('dialog')).toBeInTheDocument()
  })
})
