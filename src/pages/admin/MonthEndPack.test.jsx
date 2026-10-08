// @vitest-environment jsdom -- the statistics pack on the Month end page: asked for, polled, and saved
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { screen, waitFor, within } from '@testing-library/react'
import { http, HttpResponse } from 'msw'

/**
 * Every poll the page starts, kept so a test can wait ON THE POLL ITSELF -- the one way to know it has finished
 * without a clock. A pass-through: the page's `pollRun` is A.C.B.'s own, unchanged.
 */
const { polls } = vi.hoisted(() => ({ polls: [] }))
vi.mock('../acb/acbRunRules.js', async (importOriginal) => {
  const actual = await importOriginal()
  return {
    ...actual,
    pollRun: (...args) => {
      const poll = actual.pollRun(...args)
      polls.push(poll)
      return poll
    },
  }
})

import MonthlyMenu from './MonthlyMenu'
import { renderWithProviders } from '../../test/renderWithProviders'
import { server } from '../../test/msw/server'
import { monthEndAnswer, monthEndBody, monthEndExamples, monthEndRefusal } from '../../test/msw/monthEndExamples'
import { setupUser } from '../../test/user'
import { deferred } from '../../test/deferred'

/**
 * MONTH END `04.10` -- THE STATISTICS PACK, A JOB.
 *
 * One POST; the pack re-read on A.C.B.'s delays until it is READY or FAILED; a link a workbook once READY. Never a
 * second POST while one is being prepared, and the reading stops when the screen is left. The refusals each said in
 * the server's words (ME-13): PACK_BUSY with the month in hand and the control left open, PACK_NOT_FOUND, FAILED with
 * the pack's own failure, MONTH_NOT_TAKEN with the month's control shut. Answered as the server answers (MD48): the
 * pack and every refusal are the golden answers', and a pack still preparing is the golden pack in that state.
 */

const ROUTE = 'POST /api/month-end/{month}/pack'
const READY = monthEndBody('GET /api/month-end/packs/{id}')
/** The golden pack, as it stands while it is being prepared: no workbook yet, nothing finished. */
const PREPARING = { ...READY, status: 'PREPARING', finishedAt: null, workbooks: [] }
const FAILED = { ...PREPARING, status: 'FAILED', finishedAt: '2026-09-29T08:05:00Z',
  failure: 'Interrupted: the server stopped while the pack was being prepared' }

let posts
let reads

beforeEach(() => {
  vi.spyOn(console, 'error').mockImplementation(() => {})
  posts = 0
  reads = 0
  polls.length = 0
  // The page's months, and its figures' read (the 5 Oct Show & Tell moved them here) answered as the shared fixture's
  // empty array: *not available*, which asks for nothing more.
  server.use(monthEndAnswer('GET /api/month-end/months'), http.get('*/api/members/summary', () => HttpResponse.json([])))
})

/** The pack asked for: answered PREPARING (202), and each read of it answered by `answer(reads)`. */
function aJob(answer) {
  server.use(
    http.post('*/api/month-end/:month/pack', ({ params }) => {
      posts += 1
      return HttpResponse.json({ ...PREPARING, month: params.month }, { status: 202 })
    }),
    http.get('*/api/month-end/packs/:id', async () => {
      reads += 1
      return answer(reads)
    }),
  )
}

const openPage = (path = '/admin/monthly-menu', delays = [0, 0, 0, 0, 0, 0]) =>
  renderWithProviders(<MonthlyMenu pollDelaysMs={delays} />, { initialEntries: [path] })

const prepare = async (user, month = 'August 2026') => {
  const button = await screen.findByRole('button', { name: `Prepare the pack for ${month}` })
  await waitFor(() => expect(button).toBeEnabled())
  await user.click(button)
  return button
}

const links = () => screen.queryAllByRole('link', { name: /^Download / })
/** The pack's own alert -- the page holds other sections, and the figures above it say their own failures. */
const packAlert = () => within(screen.getByRole('region', { name: 'The statistics pack' })).findByRole('alert')

describe('asking for the pack', () => {
  it('🔑 one POST, then the pack re-read until it is ready -- and a link to save each workbook', async () => {
    aJob((n) => HttpResponse.json(n < 3 ? PREPARING : READY))
    const user = setupUser()
    openPage()
    await prepare(user)

    expect(await screen.findByText('The pack for August 2026 is ready — prepared on 29 September 2026 at 10:00.'))
      .toBeInTheDocument()
    expect(links().map((link) => [link.textContent, link.getAttribute('href'), link.hasAttribute('download')])).toEqual([
      ['Download the statistics workbook (.xlsx)', 'http://api.test.invalid/api/month-end/packs/1/statistics', true],
      ['Download the monthly workbook (.xlsx)', 'http://api.test.invalid/api/month-end/packs/1/monthly', true],
    ])
    expect(posts).toBe(1)
    expect(reads).toBe(3)
  })

  it('the golden answer: a pack in hand and READY is shown at once -- no reading, one link a workbook', async () => {
    server.use(http.post('*/api/month-end/:month/pack', () => {
      posts += 1
      const example = monthEndExamples.answers[ROUTE]
      return HttpResponse.json(example.body, { status: example.status })
    }))
    const user = setupUser()
    openPage()
    await prepare(user)

    expect(await screen.findByText(/^The pack for August 2026 is ready/)).toBeInTheDocument()
    expect(links()).toHaveLength(2)
    expect(posts).toBe(1)
    expect(reads).toBe(0)
  })

  it('🔴 never a second POST while the pack is being prepared -- the control is shut until it settles', async () => {
    const held = deferred()
    aJob(async () => {
      await held.promise
      return HttpResponse.json(READY)
    })
    const user = setupUser()
    try {
      openPage()
      const button = await prepare(user)
      await waitFor(() => expect(reads).toBe(1))

      expect(screen.getByText('Preparing the pack for August 2026. This page checks on it by itself.'))
        .toHaveAttribute('role', 'status')
      expect(button).toBeDisabled()
      await user.click(button)
      await user.dblClick(button)
      expect(posts, 'a second POST while the first pack was being prepared').toBe(1)
    } finally {
      held.resolve()
    }
    expect(await screen.findByText(/^The pack for August 2026 is ready/)).toBeInTheDocument()
    expect(posts).toBe(1)
  })

  it('🔴 leaving the screen stops the reading -- the poll ends at the read in flight, and nothing reads again', async () => {
    const held = deferred()
    aJob(async () => {
      await held.promise
      return HttpResponse.json(PREPARING)
    })
    const user = setupUser()
    const { unmount } = openPage()
    try {
      await prepare(user)
      await waitFor(() => expect(reads).toBe(1))
      unmount()
    } finally {
      held.resolve()
    }
    // Waited on the poll itself, never a clock: it settles `null` -- "the screen was left" -- where a poll that was
    // not told would read on through every delay and settle `undefined`, still preparing.
    expect(polls).toHaveLength(1)
    await expect(polls[0]).resolves.toBeNull()
    expect(reads).toBe(1)
  })

  it('the must-pass for that: while the screen stays, it reads until the pack settles', async () => {
    aJob((n) => HttpResponse.json(n < 4 ? PREPARING : READY))
    const user = setupUser()
    openPage()
    await prepare(user)

    expect(await screen.findByText(/^The pack for August 2026 is ready/)).toBeInTheDocument()
    expect(reads).toBe(4)
  })

  it('still preparing when the delays run out: said, and Check on it reads again -- it never asks again', async () => {
    aJob((n) => HttpResponse.json(n < 3 ? PREPARING : READY))
    const user = setupUser()
    openPage('/admin/monthly-menu', [0, 0])
    await prepare(user)

    expect(await screen.findByText(/is still being prepared\. Check on it again in a few minutes\./)).toBeInTheDocument()
    expect(reads).toBe(2)
    await user.click(screen.getByRole('button', { name: 'Check on it' }))

    expect(await screen.findByText(/^The pack for August 2026 is ready/)).toBeInTheDocument()
    expect(posts).toBe(1)
  })

  it('the open month has a pack too, worked out as it stands -- the page says so, and asks for it', async () => {
    aJob(() => HttpResponse.json({ ...READY, month: '2026-09' }))
    const user = setupUser()
    openPage('/admin/monthly-menu?month=2026-09')

    expect(await screen.findByText(/The open month’s pack is worked out as the month stands now/)).toBeInTheDocument()
    await prepare(user, 'September 2026')
    expect(await screen.findByText(/^The pack for September 2026 is ready/)).toBeInTheDocument()
  })
})

describe('what the server refuses, in its own words (ME-13)', () => {
  it('PACK_BUSY: another month\'s pack is in hand -- said, with its month, and the control left open to try again later', async () => {
    const refusal = monthEndExamples.refusals.PACK_BUSY
    server.use(http.post('*/api/month-end/:month/pack', () => {
      posts += 1
      return HttpResponse.json(refusal.body, { status: refusal.status })
    }))
    const user = setupUser()
    openPage()
    const button = await prepare(user)

    expect(await packAlert()).toHaveTextContent(`${refusal.body.message} The pack in hand is July 2026’s.`)
    await waitFor(() => expect(button).toBeEnabled())
    await user.click(button)
    await waitFor(() => expect(posts).toBe(2))
  })

  it('FAILED: the pack\'s own failure, and the control open to ask for it again', async () => {
    aJob((n) => HttpResponse.json(n < 2 ? PREPARING : FAILED))
    const user = setupUser()
    openPage()
    const button = await prepare(user)

    expect(await packAlert()).toHaveTextContent(
      `The pack for August 2026 could not be prepared: ${FAILED.failure}. Ask for it again.`)
    expect(links()).toEqual([])
    await waitFor(() => expect(button).toBeEnabled())
  })

  it('PACK_NOT_FOUND while it is read: the server\'s sentence, and nothing claimed', async () => {
    server.use(
      http.post('*/api/month-end/:month/pack', () => HttpResponse.json(PREPARING, { status: 202 })),
      monthEndRefusal('PACK_NOT_FOUND', 'GET /api/month-end/packs/{id}'),
    )
    const user = setupUser()
    openPage()
    await prepare(user)

    expect(await packAlert()).toHaveTextContent(monthEndExamples.refusals.PACK_NOT_FOUND.body.message)
    expect(links()).toEqual([])
    expect(screen.queryByText(/is ready/)).not.toBeInTheDocument()
  })

  it('MONTH_NOT_TAKEN: a month the server makes no pack of -- its control shut, with the server\'s reason', async () => {
    server.use(http.post('*/api/month-end/:month/pack', () => {
      posts += 1
      const refusal = monthEndExamples.refusals.MONTH_NOT_TAKEN
      return HttpResponse.json(refusal.body, { status: refusal.status })
    }))
    const user = setupUser()
    openPage('/admin/monthly-menu?month=2026-07')
    const button = await prepare(user, 'July 2026')

    await waitFor(() => expect(button).toBeDisabled())
    expect(button).toHaveAccessibleDescription(monthEndExamples.refusals.MONTH_NOT_TAKEN.body.message)
    expect(posts).toBe(1)
  })

  it('no answer to the POST: unconfirmed, never "not prepared" -- and asking again is safe', async () => {
    server.use(http.post('*/api/month-end/:month/pack', () => {
      posts += 1
      return HttpResponse.error()
    }))
    const user = setupUser()
    openPage()
    const button = await prepare(user)

    expect(await packAlert()).toHaveTextContent(
      'The pack for August 2026 could not be confirmed. Asking again is safe — a pack in hand for the month is answered, never started twice.')
    expect(screen.queryByText(/was not prepared/)).not.toBeInTheDocument()
    await waitFor(() => expect(button).toBeEnabled())
    expect(posts).toBe(1)
  })
})
