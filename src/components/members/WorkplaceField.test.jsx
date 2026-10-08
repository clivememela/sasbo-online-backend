// @vitest-environment jsdom -- the Workplace control, driven: its four states, its search, and a workplace chosen
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { act, screen, waitFor, within } from '@testing-library/react'
import { useState } from 'react'
import { http, HttpResponse } from 'msw'

import WorkplaceField from './WorkplaceField'
import useWorkplaceSource from '../../hooks/useWorkplaceSource'
import useWorkplaceChoice from '../../hooks/useWorkplaceChoice'
import { workplaceService } from '../../services/workplaceService'
import { WORKPLACE_SEARCH } from '../../utils/workplaces'
import { renderWithProviders } from '../../test/renderWithProviders'
import { server } from '../../test/msw/server'
import { pageFixtures } from '../../test/msw/pageFixtures'
import { monthEndAnswer, monthEndBody } from '../../test/msw/monthEndExamples'
import { expectNoA11yViolations } from '../../test/assertions'
import { deferred } from '../../test/deferred'
import { setupUser } from '../../test/user'

/**
 * MONTH END `01.3` -- THE WORKPLACE CONTROL, ON ITS OWN.
 *
 * Mounted as a page mounts it: the page asks `useWorkplaceSource` (SASBO's employers, and whether any workplace is on
 * record here) and hands the answer down. Every answer below is the server's golden answer or built from its invented
 * workplaces (`month-end-examples.json`); nothing is SASBO's.
 *
 * 🔑 THE FOUR STATES ARE FOUR DIFFERENT FACTS, and "not available" is three different facts under one word: the
 * server's own reason, a server built before the route (a 404), and an answer of the wrong shape -- the suite's shared
 * fixture answers every read with `[]` (MV-36). None of them may read as an empty control, and a failure never as
 * "none".
 */

const STATUS = monthEndBody('GET /api/reference/status')
const [HARBOUR, HEAD_OFFICE] = monthEndBody('GET /api/workplaces').content

/** A form of the one key the control writes, and the page's two hooks -- no bank fields, so no offer here. */
function Harness({ stacked }) {
  const source = useWorkplaceSource(true)
  const [form, setForm] = useState({ workplaceId: null })
  const [, setFieldErrors] = useState({})
  const choice = useWorkplaceChoice({
    form, setForm, setFieldErrors, banks: [], branchesFor: () => [], settle: (prev, next) => next,
  })
  return <WorkplaceField source={source} choice={choice} stacked={stacked} />
}

/** A sentence said in one paragraph, whatever marks part of it -- "Not available." is the mock's strong (⑦). */
const said = (text) => (_, node) => node?.tagName === 'P' && node.textContent === text

/** SASBO's workplaces as the server searches them: an employer's, by a name or an R/A number. Records each question. */
function workplacesSearched(asked = []) {
  return http.get('*/api/workplaces', ({ request }) => {
    const query = new URL(request.url).searchParams
    const search = (query.get('search') ?? '').toLowerCase()
    asked.push(Object.fromEntries(query))
    const content = [HARBOUR, HEAD_OFFICE].filter((row) => String(row.employerId) === query.get('employer')
      && (row.name.toLowerCase().includes(search) || String(row.raNumber).startsWith(search)))
    return HttpResponse.json({ content, page: 0, size: 8, totalElements: content.length, totalPages: 1, reason: null })
  })
}

const boom = () => HttpResponse.json({ status: 503, error: 'Service Unavailable' }, { status: 503 })

/**
 * The catch-all, the golden lists over it, and a test's own answers over those. Three `use` calls, because msw answers
 * with the FIRST handler that matches and each call prepends: handlers written after the golden ones in the same call
 * would never be reached.
 */
function answerTheLists(...own) {
  server.use(...pageFixtures)
  server.use(monthEndAnswer('GET /api/reference/status'), monthEndAnswer('GET /api/reference/employers'))
  if (own.length) server.use(...own)
}

async function chooseEmployer(user, name) {
  const employer = await screen.findByRole('combobox', { name: /^Employer/ })
  await waitFor(() => expect(employer).toBeEnabled())
  await user.type(employer, name.slice(0, 5))
  await user.click(await screen.findByRole('option', { name }))
}

const find = () => screen.getByLabelText(/^Find the workplace/)

beforeEach(() => {
  // The control reports a failure through the one sink as well as showing it.
  vi.spyOn(console, 'error').mockImplementation(() => {})
})

describe('the Workplace control -- its four states', () => {
  it('① loading: nothing to choose yet, and it says so', async () => {
    const held = deferred()
    server.use(...pageFixtures)
    server.use(
      http.get('*/api/reference/status', async () => {
        await held.promise
        return HttpResponse.json(STATUS)
      }),
      monthEndAnswer('GET /api/reference/employers'),
    )
    renderWithProviders(<Harness />)
    try {
      const employer = await screen.findByRole('combobox', { name: /^Employer/ })
      expect(employer).toBeDisabled()
      expect(employer).toHaveAttribute('placeholder', 'Loading…')
      expect(find()).toBeDisabled()
    } finally {
      held.resolve()
    }
    await waitFor(() => expect(screen.getByRole('combobox', { name: /^Employer/ })).toBeEnabled())
  })

  it('② ready: SASBO\'s employers, with where the list was read from', async () => {
    const user = setupUser()
    answerTheLists()
    renderWithProviders(<Harness />)

    expect(await screen.findByText('As printed on 31 August 2026 — not yet confirmed by SASBO')).toBeInTheDocument()
    await user.click(screen.getByRole('combobox', { name: /^Employer/ }))
    const names = within(screen.getByRole('listbox')).getAllByRole('option').map((option) => option.textContent)
    expect(names).toEqual(['INVENTED BANK', 'INVENTED INSURER'])
    expect(find(), 'the workplace is searched for once the employer is chosen').toBeDisabled()
  })

  it('③ not available, for the server\'s own reason -- said, and no empty control beside it', async () => {
    answerTheLists(http.get('*/api/reference/status', () => HttpResponse.json({
      ...STATUS, workplaces: { ...STATUS.workplaces, rows: 0, printedOn: null, seededAt: null, reason: 'NOT_LOADED' },
      workplacesServed: 'NONE',
    })))
    renderWithProviders(<Harness />)

    expect(await screen.findByText(said('Not available. SASBO’s workplaces: Not loaded on this demonstration yet.')))
      .toBeInTheDocument()
    expect(screen.queryByRole('combobox', { name: /^Employer/ })).not.toBeInTheDocument()
    expect(screen.queryByLabelText(/^Find the workplace/)).not.toBeInTheDocument()
  })

  it('③ not available, for a read the server does not have yet -- a 404 (MD58, MV-53)', async () => {
    answerTheLists(http.get('*/api/reference/status', () =>
      HttpResponse.json({ status: 404, error: 'Not Found', path: '/api/reference/status' }, { status: 404 })))
    renderWithProviders(<Harness />)

    expect(await screen.findByText(said('Not available. This server does not answer for SASBO’s workplaces yet.')))
      .toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Try again' }), 'asking again would only be told no again')
      .not.toBeInTheDocument()
  })

  it('③ not available, for an answer of the wrong shape -- the shared fixture\'s [] (BR56, MV-36)', async () => {
    server.use(...pageFixtures)
    renderWithProviders(<Harness />)

    expect(await screen.findByText(said('Not available. This server’s answer about SASBO’s workplaces could not be read.')))
      .toBeInTheDocument()
    expect(screen.queryByRole('combobox', { name: /^Employer/ })).not.toBeInTheDocument()
  })

  it('④ failed: said as a failure, with Try again -- never as "none" -- and Try again asks again', async () => {
    let calls = 0
    answerTheLists(http.get('*/api/reference/employers', () => {
      calls += 1
      return calls === 1 ? boom() : HttpResponse.json(monthEndBody('GET /api/reference/employers'))
    }))
    const user = setupUser()
    renderWithProviders(<Harness />)

    expect(await screen.findByText('SASBO’s workplaces could not be loaded.')).toBeInTheDocument()
    expect(screen.queryByText(/^Not available/)).not.toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Try again' }))

    await waitFor(() => expect(screen.getByRole('combobox', { name: /^Employer/ })).toBeEnabled())
    expect(calls).toBe(2)
    expect(screen.queryByText('SASBO’s workplaces could not be loaded.')).not.toBeInTheDocument()
  })
})

describe('the Workplace control -- the search (MR-PERF-12)', () => {
  afterEach(() => {
    vi.useRealTimers()
    delete globalThis.jest
  })

  /**
   * 🔑 THE PAUSE IS DRIVEN, NOT WAITED FOR. The lists load on the real clock; then only `setTimeout`/`clearTimeout`
   * are faked, so every step of the typing is read against a clock this test moves, and nothing depends on how fast
   * the machine is. Testing Library's waits are pointed at the same clock (the `jest` shim, as
   * `AddressAutocomplete.suggestions.test.jsx` does), or every `findBy` would hang on a timer that never fires.
   */
  async function readyToSearch(asked) {
    answerTheLists(workplacesSearched(asked))
    const user = setupUser()
    renderWithProviders(<Harness />)
    await chooseEmployer(user, 'INVENTED BANK')
    await waitFor(() => expect(find()).toBeEnabled())
    vi.useFakeTimers({ toFake: ['setTimeout', 'clearTimeout'] })
    globalThis.jest = { advanceTimersByTime: (ms) => vi.advanceTimersByTime(ms) }
    return user
  }

  const pause = () => act(() => vi.advanceTimersByTimeAsync(WORKPLACE_SEARCH.pauseMs))

  it('one character asks nothing, however long the pause', async () => {
    const asked = []
    const user = await readyToSearch(asked)

    await user.type(find(), 'h')
    await pause()
    await pause()

    expect(asked, 'a question of one character is never sent').toEqual([])
  })

  it('two characters ask -- after the pause, not before -- once, for the chosen employer, a page at a time', async () => {
    const asked = []
    const user = await readyToSearch(asked)

    await user.type(find(), 'he')
    expect(asked, 'nothing is asked while the typing may still go on').toEqual([])
    await pause()

    expect(await screen.findByRole('button', { name: /^INVENTED HEAD OFFICE/ })).toBeInTheDocument()
    expect(asked).toEqual([{ employer: '1', search: 'he', page: '0', size: String(WORKPLACE_SEARCH.size) }])
    expect(screen.getByText('1 found.')).toBeInTheDocument()
  })

  it('typing on restarts the pause: one question, for what was typed last', async () => {
    const asked = []
    const user = await readyToSearch(asked)

    await user.type(find(), 'he')
    await act(() => vi.advanceTimersByTimeAsync(WORKPLACE_SEARCH.pauseMs - 1))
    await user.type(find(), 'a')
    await pause()

    await screen.findByRole('button', { name: /^INVENTED HEAD OFFICE/ })
    expect(asked.map((question) => question.search)).toEqual(['hea'])
  })

  it('🔴 an answer that arrives after a newer question is dropped -- the newer answer stays', async () => {
    const stale = deferred()
    let calls = 0
    answerTheLists(http.get('*/api/workplaces', async ({ request }) => {
      calls += 1
      const search = new URL(request.url).searchParams.get('search')
      if (calls === 1) {
        await stale.promise
        // What the late answer would put on screen, if it were let through: an invented row no newer answer has.
        const late = { ...HEAD_OFFICE, id: 99, raNumber: 900099, name: 'INVENTED STALE ANSWER' }
        return HttpResponse.json({ content: [late], page: 0, size: 8, totalElements: 1, totalPages: 1, reason: null })
      }
      const content = [HEAD_OFFICE].filter((row) => row.name.toLowerCase().includes(search))
      return HttpResponse.json({ content, page: 0, size: 8, totalElements: content.length, totalPages: 1, reason: null })
    }))
    const asked = vi.spyOn(workplaceService, 'getWorkplaces')
    const user = setupUser()
    renderWithProviders(<Harness />)
    await chooseEmployer(user, 'INVENTED BANK')
    await waitFor(() => expect(find()).toBeEnabled())
    vi.useFakeTimers({ toFake: ['setTimeout', 'clearTimeout'] })
    globalThis.jest = { advanceTimersByTime: (ms) => vi.advanceTimersByTime(ms) }

    try {
      await user.type(find(), 'he')
      await pause()
      await waitFor(() => expect(calls).toBe(1))
      await user.type(find(), 'ad')
      await pause()
      expect(await screen.findByRole('button', { name: /^INVENTED HEAD OFFICE/ })).toBeInTheDocument()
    } finally {
      // Back on the real clock before the late answer is let go, so nothing it needs waits on a timer this test holds.
      vi.useRealTimers()
      delete globalThis.jest
      stale.resolve()
    }
    // 🔑 WAIT FOR THE LATE ANSWER ITSELF, NOT FOR A CLOCK (as `Members.jobGradeSequencing.test.jsx` ②): this is the very
    // promise the control's read chained onto. Once it has settled, what is left of that chain is microtasks, and one
    // turn of the event loop drains every one of them -- so by the time `act` returns, the control has had the late
    // answer and rendered whatever it did with it. A turn of the loop, not a measured wait: nothing here is timed.
    expect(asked.mock.calls[0][0].search, 'the first question was the older one').toBe('he')
    await act(async () => {
      await asked.mock.results[0].value.catch(() => {})
      await new Promise((resolve) => setTimeout(resolve, 0))
    })

    expect(screen.queryByText('INVENTED STALE ANSWER')).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: /^INVENTED HEAD OFFICE/ }), 'the newer answer is still the one shown')
      .toBeInTheDocument()
    expect(calls).toBe(2)
  })

  it('finds by R/A number too, and says so when nothing matches -- an answer, not a failure', async () => {
    const asked = []
    const user = await readyToSearch(asked)

    await user.type(find(), '900002')
    await pause()
    expect(await screen.findByRole('button', { name: /^INVENTED HEAD OFFICE/ })).toBeInTheDocument()

    await user.clear(find())
    await user.type(find(), 'zz')
    await pause()
    expect(await screen.findByText('No workplace matches “zz”.')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Try again' })).not.toBeInTheDocument()
  })

  it('a search that failed says so, with Try again -- and Try again asks the same question again', async () => {
    let calls = 0
    answerTheLists(http.get('*/api/workplaces', () => {
      calls += 1
      if (calls === 1) return boom()
      return HttpResponse.json({ content: [HEAD_OFFICE], page: 0, size: 8, totalElements: 1, totalPages: 1, reason: null })
    }))
    const user = setupUser()
    renderWithProviders(<Harness />)
    await chooseEmployer(user, 'INVENTED BANK')
    await waitFor(() => expect(find()).toBeEnabled())

    await user.type(find(), 'head')
    expect(await screen.findByText('The workplaces for “head” could not be loaded.')).toBeInTheDocument()
    expect(screen.queryByText(/^No workplace matches/)).not.toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Try again' }))

    expect(await screen.findByRole('button', { name: /^INVENTED HEAD OFFICE/ })).toBeInTheDocument()
    expect(calls).toBe(2)
  })

  it('a search answered "none are loaded here" is not available, in the server\'s word', async () => {
    answerTheLists(http.get('*/api/workplaces', () => HttpResponse.json({
      content: [], page: 0, size: 8, totalElements: 0, totalPages: 0, reason: 'NOT_LOADED',
    })))
    const user = setupUser()
    renderWithProviders(<Harness />)
    await chooseEmployer(user, 'INVENTED BANK')
    await waitFor(() => expect(find()).toBeEnabled())

    await user.type(find(), 'head')
    expect(await screen.findByText('Not available. SASBO’s workplaces: Not loaded on this demonstration yet.'))
      .toBeInTheDocument()
  })
})

describe('the Workplace control -- a workplace chosen', () => {
  it('shows its R/A number, its area and its committee, read-only, with where they came from', async () => {
    answerTheLists(workplacesSearched())
    const user = setupUser()
    const { container } = renderWithProviders(<Harness />)
    await chooseEmployer(user, 'INVENTED BANK')
    await waitFor(() => expect(find()).toBeEnabled())
    await user.type(find(), 'head')
    await user.click(await screen.findByRole('button', { name: /^INVENTED HEAD OFFICE/ }))

    const group = screen.getByRole('group', { name: 'Workplace' })
    expect(within(group).getByText('INVENTED HEAD OFFICE')).toBeInTheDocument()
    // Its R/A number, its area and its committee -- the golden answer's, every one, and where they came from.
    expect(within(group).getByText('R/A 900002 · Area A · Committee Q01, INVENTED NORTH COMMITTEE')).toBeInTheDocument()
    expect(within(group).getByText(/From SASBO’s list of workplaces\. As printed on 31 August 2026 — not yet confirmed by SASBO/))
      .toBeInTheDocument()
    // Read-only: nothing of the workplace can be typed into.
    expect(within(group).queryByDisplayValue('900002')).not.toBeInTheDocument()
    // Chosen, the search is cleared for another question.
    expect(find()).toHaveValue('')
    await expectNoA11yViolations(container)
  })
})

/**
 * 🔍 MO24 -- THE STAND-IN REVIEW'S FINDINGS ON THIS CONTROL. jsdom lays nothing out and draws no glyph, so each is held
 * by the structure and the classes that decide it; what they look like at 1280 and 390 was measured in a browser.
 */
describe('the Workplace control -- the stand-in review (MO24)', () => {
  /** The employer and the search's pair: the nearest box that holds both controls. */
  const pairOf = () => {
    const employer = screen.getByRole('combobox', { name: /^Employer/ })
    let pair = find()
    while (!pair.contains(employer)) pair = pair.parentElement
    return pair
  }

  it("⑦ 'Not available.' is said in the record's ink, at 600, before the reason", async () => {
    answerTheLists(http.get('*/api/reference/status', () => HttpResponse.json({
      ...STATUS, workplaces: { ...STATUS.workplaces, rows: 0, printedOn: null, seededAt: null, reason: 'NOT_LOADED' },
      workplacesServed: 'NONE',
    })))
    renderWithProviders(<Harness />)

    const words = await screen.findByText('Not available.')
    expect(words.tagName).toBe('STRONG')
    expect(words).toHaveClass('font-semibold', 'text-ink')
    // The reason stays in the quiet ink beside it.
    expect(words.parentElement).toHaveClass('text-ink-muted')
  })

  it('🔴 the facts are not set in tabular figures, and a padded part puts no space before the comma', async () => {
    // A committee as a fixed-width column might hand it over: padded, both of its parts.
    const padded = { ...HEAD_OFFICE, committee: 'Q01  ', committeeName: '  INVENTED NORTH COMMITTEE ' }
    answerTheLists(http.get('*/api/workplaces', () =>
      HttpResponse.json({ content: [padded], page: 0, size: 8, totalElements: 1, totalPages: 1, reason: null })))
    const user = setupUser()
    renderWithProviders(<Harness />)
    await chooseEmployer(user, 'INVENTED BANK')
    await waitFor(() => expect(find()).toBeEnabled())
    await user.type(find(), 'head')
    await user.click(await screen.findByRole('button', { name: /^INVENTED HEAD OFFICE/ }))

    const facts = await screen.findByText('R/A 900002 · Area A · Committee Q01, INVENTED NORTH COMMITTEE')
    // The face gives a comma a figure's width under tabular-nums: "Q01," read "Q01 ," on screen (the review's capture).
    expect(facts).not.toHaveClass('tabular-nums')
  })

  it("on capture the employer and the search stand side by side, as the mock's capture frames draw them", async () => {
    answerTheLists()
    renderWithProviders(<Harness />)
    await waitFor(() => expect(screen.getByRole('combobox', { name: /^Employer/ })).toBeEnabled())

    expect(pairOf()).toHaveClass('grid', 'grid-cols-2', 'max-sm:grid-cols-1')
  })

  it("⑫ stacked -- the record's panel -- they stand one over the other, in the panel's one column", async () => {
    answerTheLists()
    renderWithProviders(<Harness stacked />)
    await waitFor(() => expect(screen.getByRole('combobox', { name: /^Employer/ })).toBeEnabled())

    expect(pairOf()).toHaveClass('grid')
    expect(pairOf()).not.toHaveClass('grid-cols-2')
  })
})
