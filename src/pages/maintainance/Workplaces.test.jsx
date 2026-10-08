// @vitest-environment jsdom -- the workplaces' screen (month end 01.5): what it asks the server, and what it says back
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { act, screen, waitFor, within } from '@testing-library/react'
import { http, HttpResponse } from 'msw'

import App from '../../App'
import Workplaces, { NONE_ON_RECORD, PAGE_SIZE, SEARCH_MAX, SEARCH_PAUSE_MS } from './Workplaces'
import { LIST_EMPTY_REASON } from '../../config/monthEnd'
import { workplaceService } from '../../services/workplaceService'
import apiVocabulary from '../../test/api-vocabulary.json'
import { expectNoA11yViolations } from '../../test/assertions'
import { deferred } from '../../test/deferred'
import { monthEndAnswer, monthEndBody, monthEndExamples, monthEndRefusal } from '../../test/msw/monthEndExamples'
import { server } from '../../test/msw/server'
import { smokeHandlers } from '../../test/msw/smokeLayer'
import { renderWithProviders } from '../../test/renderWithProviders'
import { setupUser } from '../../test/user'

/**
 * MONTH END `01.5`, THE WORKPLACES' SCREEN (MD61) -- *Maintenance → Workplaces*.
 *
 * The page is the server's: it asks for a page, its size and the criteria, and shows what comes back in the order it
 * came (MR-SEC-22). So these tests read the QUESTION off the wire, and hold the ANSWER up against the screen. Every
 * row here is invented (MD57); where the server's own answer fits, it is the golden answer (MD48).
 */

const ROUTE = 'GET /api/workplaces'
const LIST_ROUTES = ['GET /api/reference/employers', 'GET /api/reference/areas', 'GET /api/reference/committees']
const vocabulary = apiVocabulary.monthEnd

/** Every question put to `/api/workplaces`, as the server would read it. */
let asked

/** Answer `/api/workplaces` with `answer(query)`, and record the question. */
const answering = (answer) =>
  http.get('*/api/workplaces', ({ request }) => {
    const query = Object.fromEntries(new URL(request.url).searchParams)
    asked.push(query)
    return answer(query)
  })

/** An invented workplace, in the shape `GET /api/workplaces` answers one. */
const workplace = (id, fields = {}) => ({
  id, raNumber: 900000 + id, name: `INVENTED WORKPLACE ${id}`, employerPrinted: 'INVENTED BANK', employerId: 1,
  employer: 'INVENTED BANK', group: 'INVENTED GROUP CORE', tableCode: 901, tableCodeBy: 'NAME', area: 'A',
  region: 'NORTH', committee: 'Q01', committeeName: 'INVENTED NORTH COMMITTEE', staffComplement: 12, closedOn: null,
  ...fields,
})

/** A page, as the server answers one. */
const body = (content, fields = {}) => ({
  content, page: 0, size: PAGE_SIZE, totalElements: content.length, totalPages: content.length ? 1 : 0, reason: null,
  ...fields,
})
const pageOf = (content, fields) => HttpResponse.json(body(content, fields))
const golden = () => HttpResponse.json(monthEndBody(ROUTE))

/** The names the sheet shows, top to bottom. */
const namesShown = () => [...document.querySelectorAll('td[data-col="name"]')].map((cell) => cell.textContent)
const listsLoaded = () => screen.findByRole('option', { name: 'INVENTED BANK' })
const searchBox = () => screen.getByRole('searchbox', { name: 'Workplace or R/A number' })
const EVERY_WORKPLACE = { page: '0', size: String(PAGE_SIZE) }

beforeEach(() => {
  asked = []
  // A failed read is reported through the one sink, which writes to the console.
  vi.spyOn(console, 'error').mockImplementation(() => {})
  server.use(...LIST_ROUTES.map(monthEndAnswer))
})

describe('the workplaces\' screen -- the four states', () => {
  it('ready: the server\'s page as it came -- its rows in its order, the count on record, and no column named', async () => {
    server.use(answering(golden))
    renderWithProviders(<Workplaces />)

    const served = monthEndBody(ROUTE).content.map((w) => w.name)
    expect(await screen.findByText(served[0])).toBeInTheDocument()
    expect(namesShown()).toEqual(served)
    expect(screen.getByText(/workplaces on record/)).toHaveTextContent(`${monthEndBody(ROUTE).totalElements} workplaces on record`)
    expect(screen.getByText(/Page 1 of 2/)).toBeInTheDocument()
    // A page and its size, and nothing else: no order, no column, no sort (MR-SEC-22).
    expect(asked).toEqual([EVERY_WORKPLACE])
  })

  it('loading: it says so while the page is asked -- no rows, and no count of anything yet', async () => {
    const held = deferred()
    // A response of its own for every question: a body is read once, so one held Response cannot answer two.
    server.use(answering(() => held.promise.then(golden)))
    try {
      renderWithProviders(<Workplaces />)
      expect(await screen.findByText('Loading workplaces…')).toBeInTheDocument()
      expect(document.querySelectorAll('tr[data-skeleton]').length).toBeGreaterThan(0)
      expect(document.querySelector('tr[data-row]')).toBeNull()
      expect(screen.queryByText(/on record/)).toBeNull()
      expect(screen.queryByText(/No workplace/)).toBeNull()
    } finally {
      held.resolve()
    }
    // The must-pass: once the answer lands, the rows replace it.
    expect(await screen.findByText('INVENTED HARBOUR')).toBeInTheDocument()
  })

  it('failed: it says the read failed, offers to try again, and never says "none"', async () => {
    server.use(answering(() => HttpResponse.json({ status: 503, message: 'Invented outage.' }, { status: 503 })))
    const user = setupUser()
    renderWithProviders(<Workplaces />)

    expect(await screen.findByText('Invented outage.')).toBeInTheDocument()
    expect(screen.getByText('The workplaces did not load, so this is not an empty list.')).toBeInTheDocument()
    expect(screen.getByText('Could not load workplaces')).toBeInTheDocument()
    expect(screen.queryByText(/No workplace/)).toBeNull()
    expect(screen.queryByText(/on record/)).toBeNull()

    // Driven: the retry asks again, and what comes back is shown.
    server.use(answering(golden))
    await user.click(screen.getByRole('button', { name: 'Try again' }))
    expect(await screen.findByText('INVENTED HARBOUR')).toBeInTheDocument()
    expect(asked).toEqual([EVERY_WORKPLACE, EVERY_WORKPLACE])
  })

  it('failed: a refusal is the server\'s sentence, and no retry that could not work', async () => {
    server.use(monthEndRefusal('REQUEST_MALFORMED', ROUTE))
    renderWithProviders(<Workplaces />)

    expect(await screen.findByText(monthEndExamples.refusals.REQUEST_MALFORMED.body.message)).toBeInTheDocument()
    await listsLoaded()
    expect(screen.queryByRole('button', { name: 'Try again' })).toBeNull()
    expect(screen.queryByText(/No workplace/)).toBeNull()
  })

  it('an answer that is not a page is not available -- never an empty list (MV-36)', async () => {
    server.use(answering(() => HttpResponse.json([])))
    renderWithProviders(<Workplaces />)

    expect(await screen.findByText('The workplaces are not available here.')).toBeInTheDocument()
    expect(screen.queryByText(/No workplace/)).toBeNull()
  })

  it('has one sentence for each of the server\'s four reasons, and no other (ME-19)', () => {
    expect(Object.keys(NONE_ON_RECORD)).toEqual(vocabulary.listEmptyReasons)
    expect(new Set(Object.values(NONE_ON_RECORD)).size).toBe(vocabulary.listEmptyReasons.length)
  })

  it.each(vocabulary.listEmptyReasons)('empty: where no workplace is on record, it says which reason -- %s (ME-19)', async (reason) => {
    server.use(answering(() => pageOf([], { reason })))
    renderWithProviders(<Workplaces />)

    expect(await screen.findByText('No workplace is on record here.')).toBeInTheDocument()
    expect(screen.getByText(NONE_ON_RECORD[reason])).toBeInTheDocument()
    expect(screen.queryByText(/No workplaces? match/)).toBeNull()
  })

  it('a reason the browser has never heard of is shown as the server wrote it', async () => {
    server.use(answering(() => pageOf([], { reason: 'SOMETHING_NEW' })))
    renderWithProviders(<Workplaces />)

    expect(await screen.findByText('No workplace is on record here.')).toBeInTheDocument()
    expect(screen.getByText('SOMETHING_NEW')).toBeInTheDocument()
  })

  it('the must-pass: criteria nothing matches say so -- and give no reason, for the workplaces are on record', async () => {
    server.use(answering(({ employer }) => (employer ? pageOf([]) : golden())))
    const user = setupUser()
    renderWithProviders(<Workplaces />)
    await listsLoaded()

    await user.selectOptions(screen.getByRole('combobox', { name: 'Employer' }), 'INVENTED BANK')

    expect(await screen.findByText('No workplace matches these criteria.')).toBeInTheDocument()
    expect(screen.getByText('Clear the criteria to see every workplace on record.')).toBeInTheDocument()
    expect(screen.getByText('No workplaces match')).toBeInTheDocument()
    expect(screen.queryByText('No workplace is on record here.')).toBeNull()
    for (const sentence of Object.values(NONE_ON_RECORD)) expect(screen.queryByText(sentence)).toBeNull()
    // A filter does not change what is on record.
    expect(screen.getByText(/workplaces on record/)).toHaveTextContent('4 workplaces on record')

    // Driven: Clear asks for every workplace again, from the first page.
    await user.click(screen.getByRole('button', { name: 'Clear' }))
    expect(await screen.findByText('INVENTED HARBOUR')).toBeInTheDocument()
    expect(asked.at(-1)).toEqual(EVERY_WORKPLACE)
  })
})

describe('the workplaces\' screen -- what it shows', () => {
  it('shows no count of members anywhere: the figures are P2\'s and P4\'s', async () => {
    server.use(answering(golden))
    renderWithProviders(<Workplaces />)
    await screen.findByText('INVENTED HARBOUR')
    await listsLoaded()

    expect(document.body.textContent).not.toMatch(/member/i)
    // The must-pass: it does count -- workplaces.
    expect(document.body).toHaveTextContent('4 workplaces on record')
  })

  it('shows the staff complement, and the absence mark where the server answers none -- never a 0 (MD62)', async () => {
    server.use(answering(() => pageOf([workplace(1, { staffComplement: null }), workplace(2)])))
    renderWithProviders(<Workplaces />)
    await screen.findByText('INVENTED WORKPLACE 1')

    const staff = [...document.querySelectorAll('td[data-col="staffComplement"]')].map((cell) => cell.textContent)
    expect(staff).toEqual(['—', '12'])
  })

  it('says where the lists were read from, and that SASBO has not confirmed them yet (MD28)', async () => {
    server.use(answering(golden))
    renderWithProviders(<Workplaces />)

    expect(await screen.findByText(
      'SASBO’s employers, areas and committees: As printed on 31 August 2026 — not yet confirmed by SASBO',
    )).toBeInTheDocument()
  })

  it('has no accessibility violations, with its rows and its lists loaded', async () => {
    server.use(answering(golden))
    const { container } = renderWithProviders(<Workplaces />)
    await screen.findByText('INVENTED HARBOUR')
    await listsLoaded()

    await expectNoA11yViolations(container)
  })
})

describe('the workplaces\' screen -- the criteria and the pager, driven', () => {
  it('filters by employer, area and committee: each choice is asked of the server', async () => {
    server.use(answering(golden))
    const user = setupUser()
    renderWithProviders(<Workplaces />)
    await listsLoaded()

    await user.selectOptions(screen.getByRole('combobox', { name: 'Employer' }), 'INVENTED INSURER')
    await waitFor(() => expect(asked.at(-1)).toEqual({ ...EVERY_WORKPLACE, employer: '2' }))

    await user.selectOptions(screen.getByRole('combobox', { name: 'Area' }), 'B · SOUTH')
    await waitFor(() => expect(asked.at(-1)).toEqual({ ...EVERY_WORKPLACE, employer: '2', area: 'B' }))

    await user.selectOptions(screen.getByRole('combobox', { name: 'Committee' }), 'R01 · INVENTED SOUTH COMMITTEE')
    await waitFor(() => expect(asked.at(-1)).toEqual({ ...EVERY_WORKPLACE, employer: '2', area: 'B', committee: 'R01' }))
  })

  it('turns the page on the server and shows each as it came -- no row shared, none skipped (MV-61)', async () => {
    // Deliberately in no order a browser would sort them into: the sheet shows the server's order, whatever it is.
    const all = Array.from({ length: 60 }, (_, i) =>
      workplace(i + 1, { name: `INVENTED ${String.fromCharCode(90 - (i % 26))} ${i + 1}` }))
    server.use(answering(({ page, size }) => {
      const from = Number(page) * Number(size)
      return pageOf(all.slice(from, from + Number(size)), {
        page: Number(page), size: Number(size), totalElements: all.length, totalPages: Math.ceil(all.length / Number(size)),
      })
    }))
    const user = setupUser()
    renderWithProviders(<Workplaces />)

    await screen.findByText(all[0].name)
    const first = namesShown()
    expect(first).toEqual(all.slice(0, PAGE_SIZE).map((w) => w.name))
    expect(screen.getByText(/Page 1 of 2/)).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: /Next/ }))
    await screen.findByText(all[PAGE_SIZE].name)
    expect(asked.at(-1)).toEqual({ ...EVERY_WORKPLACE, page: '1' })
    const second = namesShown()
    expect(second).toEqual(all.slice(PAGE_SIZE).map((w) => w.name))
    expect([...first, ...second]).toEqual(all.map((w) => w.name))
    expect(screen.getByText(/Page 2 of 2/)).toBeInTheDocument()

    // A new question starts again at the first page.
    await listsLoaded()
    await user.selectOptions(screen.getByRole('combobox', { name: 'Employer' }), 'INVENTED BANK')
    await waitFor(() => expect(asked.at(-1)).toEqual({ ...EVERY_WORKPLACE, employer: '1' }))
  })

  it('a page that no longer exists sends the sheet back to its first page, and says so -- it strands nobody', async () => {
    const refusal = monthEndExamples.refusals.WORKPLACE_PAGE_INVALID
    server.use(answering(({ page }) => (page === '0'
      ? pageOf([workplace(1)], { totalElements: PAGE_SIZE + 1, totalPages: 2 })
      : HttpResponse.json(refusal.body, { status: refusal.status }))))
    const user = setupUser()
    renderWithProviders(<Workplaces />)
    await screen.findByText('INVENTED WORKPLACE 1')

    await user.click(screen.getByRole('button', { name: /Next/ }))

    expect(await screen.findByText('That page no longer exists, so the list is back at its first page.')).toBeInTheDocument()
    await waitFor(() => expect(asked.map((q) => q.page)).toEqual(['0', '1', '0']))
    expect(await screen.findByText('INVENTED WORKPLACE 1')).toBeInTheDocument()
    expect(screen.queryByText(refusal.body.message)).toBeNull()
  })
})

describe('the workplaces\' screen -- SASBO\'s lists behind the criteria', () => {
  it('asks for them only once the first page has answered, and keeps them for the visit (MD69)', async () => {
    const lists = []
    server.use(http.get('*/api/reference/:list', ({ params }) => {
      lists.push(params.list)
      return HttpResponse.json(monthEndBody(`GET /api/reference/${params.list}`))
    }))
    const held = deferred()
    // A response of its own for every question -- the filter below asks a second time, after the release.
    server.use(answering(() => held.promise.then(golden)))
    const user = setupUser()
    try {
      renderWithProviders(<Workplaces />)
      expect(await screen.findByText('Loading workplaces…')).toBeInTheDocument()
      // The first paint is one request: the page's.
      expect(lists).toEqual([])
    } finally {
      held.resolve()
    }
    await listsLoaded()
    expect([...lists].sort()).toEqual(['areas', 'committees', 'employers'])

    await user.selectOptions(screen.getByRole('combobox', { name: 'Employer' }), 'INVENTED BANK')
    await waitFor(() => expect(asked.at(-1)).toEqual({ ...EVERY_WORKPLACE, employer: '1' }))
    expect(lists).toHaveLength(3)
  })

  it('a list that fails is an error on its own control, with a retry -- never an empty dropdown', async () => {
    server.use(http.get('*/api/reference/employers', () => HttpResponse.json({ status: 500 }, { status: 500 })))
    server.use(answering(golden))
    const user = setupUser()
    renderWithProviders(<Workplaces />)

    expect(await screen.findByText('Employer could not be loaded.')).toBeInTheDocument()
    const employer = screen.getByRole('combobox', { name: 'Employer' })
    expect(employer).toBeDisabled()
    expect(employer).toHaveTextContent('Unavailable')
    expect(within(employer).queryByRole('option', { name: 'INVENTED BANK' })).toBeNull()
    // The others are not taken down with it.
    expect(await screen.findByRole('option', { name: 'B · SOUTH' })).toBeInTheDocument()

    server.use(monthEndAnswer('GET /api/reference/employers'))
    await user.click(screen.getByRole('button', { name: 'Try again' }))
    expect(await listsLoaded()).toBeInTheDocument()
    expect(screen.queryByText('Employer could not be loaded.')).toBeNull()
  })

  it('a list with nothing on it says why, and is never an empty control (ME-19)', async () => {
    server.use(http.get('*/api/reference/committees', () =>
      HttpResponse.json({ ...monthEndBody('GET /api/reference/committees'), rows: [], reason: 'NOT_CONFIRMED' })))
    server.use(answering(golden))
    renderWithProviders(<Workplaces />)

    const committee = screen.getByRole('combobox', { name: 'Committee' })
    await waitFor(() => expect(committee).toHaveTextContent(LIST_EMPTY_REASON.NOT_CONFIRMED))
    expect(committee).toBeDisabled()
  })
})

describe('the workplaces\' screen -- the search (MR-PERF-12)', () => {
  /*
   * 🔑 THE PAUSE IS DRIVEN, NOT WAITED FOR -- as `AddressAutocomplete.suggestions.test.jsx` drives its debounce. Only
   * the timer functions are faked, so promises stay real; Testing Library's own waits are pointed at vitest's clock.
   */
  beforeEach(() => {
    vi.useFakeTimers({ toFake: ['setTimeout', 'clearTimeout'] })
    globalThis.jest = { advanceTimersByTime: (ms) => vi.advanceTimersByTime(ms) }
  })

  afterEach(() => {
    vi.useRealTimers()
    delete globalThis.jest
  })

  const pause = () => act(() => vi.advanceTimersByTimeAsync(SEARCH_PAUSE_MS))
  const typist = () => setupUser({ advanceTimers: vi.advanceTimersByTime })
  const matching = ({ search }) => pageOf(search ? [workplace(2, { name: 'INVENTED MATCH' })] : [workplace(1)])

  it('asks nothing for one character, and asks for two once typing pauses', async () => {
    server.use(answering(matching))
    const user = typist()
    renderWithProviders(<Workplaces />)
    await screen.findByText('INVENTED WORKPLACE 1')
    await listsLoaded()

    await user.type(searchBox(), 'I')
    await pause()
    expect(asked, 'one character asked the server').toHaveLength(1)

    await user.type(searchBox(), 'N')
    expect(asked, 'two characters were asked before the pause').toHaveLength(1)
    await pause()
    expect(await screen.findByText('INVENTED MATCH')).toBeInTheDocument()
    expect(asked).toEqual([EVERY_WORKPLACE, { ...EVERY_WORKPLACE, search: 'IN' }])
  })

  it('drops an answer that arrives after a newer question -- the ticket', async () => {
    const held = {}
    vi.spyOn(workplaceService, 'getWorkplaces').mockImplementation(({ search }) => {
      held[search] = deferred()
      return held[search].promise
    })
    const user = typist()
    renderWithProviders(<Workplaces />)
    await act(async () => held[''].resolve(body([workplace(1)])))
    expect(screen.getByText('INVENTED WORKPLACE 1')).toBeInTheDocument()
    await listsLoaded()

    // Three questions out at once, the newest last.
    for (const next of ['IN', 'V', 'E']) {
      await user.type(searchBox(), next)
      await pause()
    }
    expect(Object.keys(held).sort(), 'a question was never asked').toEqual(['', 'IN', 'INV', 'INVE'])

    // An OLDER answer that lands while the newest is still out must not paint...
    await act(async () => held.IN.resolve(body([workplace(3, { name: 'INVENTED STALE' })])))
    expect(screen.queryByText('INVENTED STALE')).toBeNull()
    expect(screen.getByText('Loading workplaces…')).toBeInTheDocument()

    // ...the must-pass: the newest answer does...
    await act(async () => held.INVE.resolve(body([workplace(4, { name: 'INVENTED FRESH' })])))
    expect(screen.getByText('INVENTED FRESH')).toBeInTheDocument()

    // ...and an older answer landing AFTER it neither paints nor takes it away.
    await act(async () => held.INV.resolve(body([workplace(5, { name: 'INVENTED LATE' })])))
    expect(screen.queryByText('INVENTED LATE')).toBeNull()
    expect(screen.getByText('INVENTED FRESH')).toBeInTheDocument()
    expect(screen.queryByText('Loading workplaces…')).toBeNull()
  })

  it('takes a hundred characters and no more: the server refuses a longer term (MR-SEC-22)', async () => {
    server.use(answering(matching))
    const user = typist()
    renderWithProviders(<Workplaces />)
    await screen.findByText('INVENTED WORKPLACE 1')

    expect(SEARCH_MAX).toBe(vocabulary.searchMaxLength)
    expect(PAGE_SIZE).toBeLessThanOrEqual(vocabulary.workplacePageMaxSize)
    expect(searchBox()).toHaveAttribute('maxlength', String(SEARCH_MAX))

    await user.type(searchBox(), 'I'.repeat(SEARCH_MAX + 5))
    expect(searchBox()).toHaveValue('I'.repeat(SEARCH_MAX))
    await pause()
    await waitFor(() => expect(asked).toHaveLength(2))
    expect(asked[1].search).toHaveLength(SEARCH_MAX)
  })

  it('Clear asks for every workplace at once: an emptied box needs no pause', async () => {
    server.use(answering(matching))
    const user = typist()
    renderWithProviders(<Workplaces />)
    await screen.findByText('INVENTED WORKPLACE 1')
    await listsLoaded()

    await user.type(searchBox(), 'IN')
    await pause()
    expect(await screen.findByText('INVENTED MATCH')).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'Clear' }))
    expect(searchBox()).toHaveValue('')
    expect(await screen.findByText('INVENTED WORKPLACE 1')).toBeInTheDocument()
    expect(asked).toEqual([EVERY_WORKPLACE, { ...EVERY_WORKPLACE, search: 'IN' }, EVERY_WORKPLACE])
    await pause()
    expect(asked, 'the pause after Clear asked again').toHaveLength(3)
  })
})

/**
 * 🔍 MO24 -- THE STAND-IN REVIEW'S FINDINGS ON THIS SCREEN: at 390 the R/A number and the staff were behind a sideways
 * scroll and an expander whose details left them out; at 1280 the staff was cut by 48px. jsdom lays nothing out, so
 * these hold the STRUCTURE the layout rests on -- the sheet a `register`, every cell in every row, the ladder each
 * column climbs, the caps -- and the widths it buys were measured in a browser and are recorded with the commit.
 */
describe('the workplaces\' screen -- the stand-in review (MO24)', () => {
  const sheetOf = () => screen.getByRole('table').closest('.register')
  const COLUMN_KEYS = ['name', 'raNumber', 'employer', 'area', 'committee', 'staffComplement']

  it('the sheet is a register: on a phone each row is a card holding all six -- nothing behind an expander', async () => {
    server.use(answering(golden))
    renderWithProviders(<Workplaces />)
    await screen.findByText('INVENTED HARBOUR')

    expect(sheetOf(), 'the card rows and the ladder are the register\'s (index.css)').not.toBeNull()
    expect(document.querySelector('[data-expander]')).toBeNull()
    expect(screen.queryByRole('button', { name: 'Show details' })).not.toBeInTheDocument()
    for (const row of document.querySelectorAll('tr[data-row]')) {
      expect([...row.querySelectorAll('td')].map((cell) => cell.dataset.col)).toEqual(COLUMN_KEYS)
    }
    // The card's second column, in the columns' own order: the R/A number, the area, the staff -- standing right.
    const ends = [...document.querySelectorAll('tr[data-row]:first-child td[data-end]')].map((cell) => cell.dataset.col)
    expect(ends).toEqual(['raNumber', 'area', 'staffComplement'])
  })

  it('wider, the columns yield to the sheet\'s own width: the committee, then the employer, then the area -- the staff never', async () => {
    server.use(answering(golden))
    renderWithProviders(<Workplaces />)
    await screen.findByText('INVENTED HARBOUR')

    const fits = Object.fromEntries(COLUMN_KEYS.map((key) =>
      [key, document.querySelector(`th[data-col="${key}"]`).dataset.fit ?? 'never']))
    expect(fits).toEqual({
      name: 'never', raNumber: 'never', employer: 'medium', area: 'narrow', committee: 'wide', staffComplement: 'never',
    })
  })

  it('the workplace is capped at 18rem, the employer and the committee at 16rem -- each whole in its cell\'s title', async () => {
    const long = workplace(1, {
      name: 'INVENTED CENTRAL BUSINESS DISTRICT REGIONAL PROCESSING CENTRE',
      employer: null, employerPrinted: 'INVENTED BANK DEPARTMENTS AND SUBSIDIARIES, AS PRINTED',
      committeeName: 'INVENTED NORTH AND CENTRAL PROVINCES COMMITTEE',
    })
    server.use(answering(() => pageOf([long])))
    renderWithProviders(<Workplaces />)
    await screen.findByText(long.name)

    const cell = (key) => document.querySelector(`td[data-col="${key}"]`)
    expect(cell('name')).toHaveClass('max-w-[18rem]', 'truncate')
    expect(cell('name')).not.toHaveClass('max-w-[16rem]')
    expect(cell('name')).toHaveAttribute('title', long.name)
    expect(cell('employer')).toHaveClass('max-w-[16rem]', 'truncate')
    expect(cell('employer')).toHaveAttribute('title', long.employerPrinted)
    expect(cell('committee')).toHaveClass('max-w-[16rem]', 'truncate')
    expect(cell('committee')).toHaveAttribute('title', long.committeeName)
  })

  it('on a phone the search takes the whole line', async () => {
    server.use(answering(golden))
    renderWithProviders(<Workplaces />)
    await screen.findByText('INVENTED HARBOUR')

    expect(searchBox().closest('.criterion')).toHaveClass('max-md:basis-full')
  })
})

describe('the workplaces\' screen -- where it lives', () => {
  it('the route, the rail and the crumb agree: Maintenance → Workplaces, and one heading that names it', async () => {
    server.use(...smokeHandlers)
    server.use(answering(golden), ...LIST_ROUTES.map(monthEndAnswer))
    renderWithProviders(<App />, { initialEntries: ['/admin/maintenance/workplaces'] })

    expect(await screen.findByRole('heading', { level: 1, name: 'Workplaces' })).toBeInTheDocument()
    expect(document.querySelectorAll('h1')).toHaveLength(1)

    const crumbs = screen.getByRole('navigation', { name: 'Breadcrumb' })
    expect(within(crumbs).getByText('Workplaces')).toHaveAttribute('aria-current', 'page')
    expect(within(crumbs).getByRole('link', { name: 'Maintenance' })).toHaveAttribute('href', '/admin/maintenance')

    const leaf = within(screen.getByRole('navigation', { name: 'Main' })).getByRole('link', { name: 'Workplaces' })
    expect(leaf).toHaveAttribute('href', '/admin/maintenance/workplaces')
    expect(leaf).toHaveAttribute('aria-current', 'page')

    // Let the page's reads settle while their handlers still stand.
    expect(await screen.findByText('INVENTED HARBOUR')).toBeInTheDocument()
  })
})
