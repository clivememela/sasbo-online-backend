// @vitest-environment jsdom -- a statistic's screen: its sheets in four states, rule C, the month, and its criteria driven
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { screen, waitFor, within } from '@testing-library/react'
import { useLocation } from 'react-router-dom'
import { http, HttpResponse } from 'msw'

import MonthEndStatistic from './MonthEndStatistic'
import { renderWithProviders } from '../../test/renderWithProviders'
import { server } from '../../test/msw/server'
import { monthEndAnswer, monthEndBody, monthEndExamples, monthEndRefusal } from '../../test/msw/monthEndExamples'
import { setupUser } from '../../test/user'
import { deferred } from '../../test/deferred'
import { STATISTICS, rowHeader, ruleCNote } from './monthEndRules'

/**
 * MONTH END `04.3`-`04.8` -- A STATISTIC'S SCREEN, ONE RENDERER FOR THE SIX.
 *
 * Each statistic in its four states -- ready, loading, failed, empty -- from the server's own answers (MD48): every
 * sheet with its title and legacy title, its columns in the server's order, the total its last row, the notes beneath.
 * Rule C drawn as words and never a number; a taken month and the open month told apart; each refusal in the server's
 * sentence; a statistic nobody has is a not-found state; and the criteria driven, as a person drives them.
 */

const golden = (key) => monthEndBody(`GET /api/month-end/{month}/${key}`)

/** Every statistic read, as `<statistic> <month> <query>`, and every request of any kind. */
let asked
let requests
const record = ({ request }) => {
  requests.push(`${request.method} ${new URL(request.url).pathname}`)
}

/** The statistics answered by `answer(key, month, request)` -- the golden answer unless a test says otherwise. */
const statistics = (answer = (key) => HttpResponse.json(golden(key))) =>
  http.get('*/api/month-end/:month/:statistic', ({ params, request }) => {
    asked.push(`${params.statistic} ${params.month} ${new URL(request.url).search}`)
    return answer(params.statistic, params.month, request)
  })

beforeEach(() => {
  vi.spyOn(console, 'error').mockImplementation(() => {})
  asked = []
  requests = []
  server.events.on('request:start', record)
  server.use(monthEndAnswer('GET /api/month-end/months'), statistics())
})

afterEach(() => {
  server.events.removeListener('request:start', record)
})

/** Where the screen is, as the address bar would say. */
function Where() {
  const { pathname, search } = useLocation()
  return <output data-testid="where">{`${pathname}${search}`}</output>
}

const renderAt = (address) => renderWithProviders(
  <>
    <MonthEndStatistic />
    <Where />
  </>,
  { initialEntries: [address], path: '/admin/monthly-menu/:statistic' },
)

/** A sheet's section, found by its heading. */
const sheetNamed = async (title) => (await screen.findByRole('heading', { level: 2, name: title })).closest('section')
const heads = (table) => within(table).getAllByRole('columnheader').map((head) => head.textContent)
const bodyRows = (table) => within(table).getAllByRole('row').filter((row) => row.hasAttribute('data-row'))
const firstCells = (table) => bodyRows(table).map((row) => within(row).getAllByRole('cell')[0].textContent)

describe.each(STATISTICS)('$name -- the statistic\'s sheet in its four states', ({ key, name, legacy }) => {
  it('ready: every sheet of the server\'s answer, as it lays it out -- the total its last row -- and the notes', async () => {
    const body = golden(key)
    renderAt(`/admin/monthly-menu/${key}?month=2026-08`)

    expect(screen.getByRole('heading', { level: 1, name })).toBeInTheDocument()
    expect(screen.getByText(legacy)).toBeInTheDocument()
    for (const sheet of body.sheets) {
      const section = await sheetNamed(sheet.title)
      expect(section).toHaveTextContent(sheet.legacyTitle)
      if (sheet.rows.length) {
        const table = within(section).getByRole('table', { name: sheet.title })
        expect(heads(table)).toEqual([rowHeader(sheet.key), ...sheet.columns.map((column) => column.label)])
        expect(firstCells(table)).toEqual([...sheet.rows, sheet.total].filter(Boolean).map((row) => row.label))
      } else {
        // A sheet with no rows says so -- a state of its own, not a failure, and not a line of noughts.
        const said = within(section).getByText('Nothing was counted on this sheet for August 2026.')
        expect(said.closest('[data-state]')).toHaveAttribute('data-state', 'empty')
        expect(within(section).queryByRole('table')).not.toBeInTheDocument()
      }
    }
    const notes = (await screen.findByRole('heading', { level: 2, name: 'Notes' })).closest('section')
    expect(within(notes).getAllByRole('listitem').map((item) => item.textContent)).toEqual(body.notes)
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
    expect(asked).toEqual([`${key} 2026-08 `])
  })

  it('loading: the screen says it is working it out, and shows no sheet and no figure until one was read', async () => {
    const held = deferred()
    server.use(statistics(async (statistic) => {
      await held.promise
      return HttpResponse.json(golden(statistic))
    }))
    renderAt(`/admin/monthly-menu/${key}?month=2026-08`)
    try {
      expect(await screen.findByText(`Working out ${name.toLowerCase()}…`)).toBeInTheDocument()
      expect(screen.queryByRole('table')).not.toBeInTheDocument()
      expect(screen.queryByRole('alert')).not.toBeInTheDocument()
      expect(screen.queryByText(/as taken on/)).not.toBeInTheDocument()
    } finally {
      held.resolve()
    }
    expect(await sheetNamed(golden(key).sheets[0].title)).toBeInTheDocument()
  })

  it('failed: said as a failure with Try again -- never as an empty sheet -- and Try again asks again', async () => {
    let tries = 0
    server.use(statistics((statistic) => {
      tries += 1
      return tries === 1
        ? HttpResponse.json({ status: 503, error: 'Service Unavailable' }, { status: 503 })
        : HttpResponse.json(golden(statistic))
    }))
    const user = setupUser()
    renderAt(`/admin/monthly-menu/${key}?month=2026-08`)

    expect(await screen.findByRole('alert')).toHaveTextContent('The server could not complete that request.')
    expect(screen.queryByText(/Nothing was counted/)).not.toBeInTheDocument()
    expect(screen.queryByRole('table')).not.toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Try again' }))

    expect(await sheetNamed(golden(key).sheets[0].title)).toBeInTheDocument()
    expect(tries).toBe(2)
  })

  it('empty: an answer with no sheet says so -- and it is not a failure', async () => {
    server.use(statistics((statistic) => HttpResponse.json({ ...golden(statistic), sheets: [] })))
    renderAt(`/admin/monthly-menu/${key}?month=2026-08`)

    const said = await screen.findByText(`${name} has no sheets for August 2026.`)
    expect(said.closest('[data-state]')).toHaveAttribute('data-state', 'empty')
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Try again' })).not.toBeInTheDocument()
  })
})

describe('rule C -- a row of fewer than five, and the row withheld beside it, never show a number (MD27, MD37)', () => {
  const branches = golden('branches')
  const byEmployer = branches.sheets.find((sheet) => sheet.key === 'by-employer')
  // Invented rows in the golden shape -- WITH numbers in their cells, which the screen must never show.
  const small = { key: 'E8', label: 'INVENTED SMALL EMPLOYER', level: 'ROW', fewerThanFive: true, withheld: false, cells: [40, 3, 0.075, 522, 0, 0] }
  const beside = { key: 'E9', label: 'INVENTED NEIGHBOURING EMPLOYER', level: 'ROW', fewerThanFive: false, withheld: true, cells: [55, 7, 0.1273, 1218, 0, 0] }
  const withRuleC = {
    ...branches,
    sheets: branches.sheets.map((sheet) => (sheet === byEmployer ? { ...sheet, rows: [...sheet.rows, small, beside] } : sheet)),
  }
  const why = branches.notes[ruleCNote(branches.notes)]

  it('🔴 fewer than five reads "fewer than five", and withheld "withheld" -- no figure on either -- each naming the note that says why', async () => {
    server.use(statistics(() => HttpResponse.json(withRuleC)))
    renderAt('/admin/monthly-menu/branches?month=2026-08')

    const section = await sheetNamed(byEmployer.title)
    const smallRow = within(section).getByText(small.label).closest('tr')
    const besideRow = within(section).getByText(beside.label).closest('tr')
    expect(smallRow.textContent).toBe(`${small.label}fewer than five`)
    expect(besideRow.textContent).toBe(`${beside.label}withheld`)
    expect(within(smallRow).getByText('fewer than five')).toHaveAccessibleDescription(why)
    expect(within(besideRow).getByText('withheld')).toHaveAccessibleDescription(why)
  })

  it('the must-pass: a row of five or more shows its figures like any other', async () => {
    server.use(statistics(() => HttpResponse.json(withRuleC)))
    renderAt('/admin/monthly-menu/branches?month=2026-08')

    const section = await sheetNamed(byEmployer.title)
    const bank = within(section).getByText('INVENTED BANK').closest('tr')
    expect(within(bank).getAllByRole('cell').map((cell) => cell.textContent))
      .toEqual(['INVENTED BANK', '160', '0', '0,00 %', 'R 0,00', '160', '0'])
  })
})

describe('the figures, each through its one formatter, and the rows set apart as the server sets them', () => {
  it('counts grouped, money in rand, shares as percentages -- and a cell with no meaning the absence mark', async () => {
    renderAt('/admin/monthly-menu/branches?month=2026-08')

    const section = await sheetNamed('The branches, by area')
    const table = within(section).getByRole('table')
    const rows = bodyRows(table)
    expect(within(rows.at(-1)).getAllByRole('cell').map((cell) => cell.textContent))
      .toEqual(['Total', '178', '1', '0,56 %', 'R 0,00', '160', '0'])
    const noWorkplace = rows.find((row) => row.textContent.startsWith('No workplace on record'))
    expect(within(noWorkplace).getAllByRole('cell')[3].textContent).toBe('—')
  })

  it('a heading row and a total stand apart from the rows they sum; an ordinary row does not', async () => {
    renderAt('/admin/monthly-menu/membership?month=2026-08')

    const section = await sheetNamed('Membership by group')
    expect(within(section).getByText('INVENTED GROUP CORE').tagName).toBe('STRONG')
    expect(within(section).getByText('All groups').tagName).toBe('STRONG')
    expect(within(section).getByText('INVENTED BANK').tagName).not.toBe('STRONG')
  })
})

describe('the month -- taken, or open, and said so', () => {
  /** The line that says what the month is -- one element, whatever it says. */
  const monthState = () => document.querySelector('[data-month-state]')

  it('a taken month is read as it was taken, and says when and by whom', async () => {
    renderAt('/admin/monthly-menu/membership?month=2026-08')

    expect(await screen.findByText('August 2026, as taken on 29 September 2026 at 10:00 by seed: the month as it stood then.'))
      .toHaveAttribute('data-month-state', 'TAKEN')
    expect(monthState()).not.toHaveTextContent(/read live/)
  })

  it('the open month says its figures are read live, and move until it is taken', async () => {
    // The open month's answer as the server gives it: no record of a take, and no note naming one.
    server.use(statistics((statistic, month) => HttpResponse.json({
      ...golden(statistic), month, state: 'OPEN', takenAt: null, takenBy: null,
      notes: golden(statistic).notes.filter((note) => !/ was taken on /.test(note)),
    })))
    renderAt('/admin/monthly-menu/membership?month=2026-09')

    expect(await screen.findByText('September 2026 is the open month: these figures are read live, and move until it is taken.'))
      .toHaveAttribute('data-month-state', 'OPEN')
    expect(monthState()).not.toHaveTextContent(/taken on/)
    expect(document.querySelectorAll('[data-month-state]')).toHaveLength(1)
  })

  it('with no month in the address, the newest month taken -- the server\'s, never the browser\'s clock (MD55)', async () => {
    renderAt('/admin/monthly-menu/membership')

    expect(await sheetNamed('Membership by group')).toBeInTheDocument()
    expect(asked).toEqual(['membership 2026-08 '])
    expect(screen.getByLabelText(/^Month/)).toHaveValue('2026-08')
  })

  it('the months could not be read and the address names none: said, with Try again, and nothing is read for a guess', async () => {
    let tries = 0
    server.use(http.get('*/api/month-end/months', () => {
      tries += 1
      return tries === 1
        ? HttpResponse.json({ status: 503, error: 'Service Unavailable' }, { status: 503 })
        : HttpResponse.json(monthEndBody('GET /api/month-end/months'))
    }))
    const user = setupUser()
    renderAt('/admin/monthly-menu/membership')

    expect(await screen.findByText('Month could not be loaded.')).toBeInTheDocument()
    expect(asked).toEqual([])
    await user.click(screen.getAllByRole('button', { name: 'Try again' })[0])

    expect(await sheetNamed('Membership by group')).toBeInTheDocument()
    expect(asked).toEqual(['membership 2026-08 '])
  })
})

describe('what the server refuses, in its own sentence -- never its code (ME-13)', () => {
  it.each(['MONTH_NOT_TAKEN', 'MONTH_NOT_BEGUN', 'REQUEST_MALFORMED', 'SUMMARY_AREA_UNKNOWN'])('%s', async (code) => {
    server.use(monthEndRefusal(code, 'GET /api/month-end/{month}/membership'))
    renderAt('/admin/monthly-menu/membership?month=2026-07')

    expect(await screen.findByRole('alert')).toHaveTextContent(monthEndExamples.refusals[code].body.message)
    expect(document.body).not.toHaveTextContent(code)
    // A refusal is an answer: asking again unchanged would be refused again.
    expect(screen.queryByRole('button', { name: 'Try again' })).not.toBeInTheDocument()
    expect(screen.queryByRole('table')).not.toBeInTheDocument()
  })

  it('an answer of the wrong shape -- the shared fixture\'s [] -- is not available, never an empty statistic', async () => {
    server.use(statistics(() => HttpResponse.json([])))
    renderAt('/admin/monthly-menu/membership?month=2026-08')

    expect(await screen.findByRole('alert')).toHaveTextContent('This statistic is not available from this server yet.')
    expect(screen.queryByText(/has no sheets/)).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Try again' })).not.toBeInTheDocument()
  })
})

describe('a statistic the server has none of', () => {
  it('is a not-found state, never a blank -- and nothing is asked of the server', async () => {
    renderAt('/admin/monthly-menu/nothing-here?month=2026-08')

    expect(await screen.findByRole('heading', { level: 1, name: 'No such statistic' })).toBeInTheDocument()
    expect(screen.getByText(/There is no statistic called “nothing-here” here\./)).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Go to Month end' })).toHaveAttribute('href', '/admin/monthly-menu')
    expect(requests).toEqual([])
  })
})

describe('the criteria row -- the month and an area or a region, driven as a person drives them', () => {
  it('🔑 SASBO\'s areas are asked for when the control is opened, and each choice asks again -- the address says so', async () => {
    let lists = 0
    server.use(
      http.get('*/api/reference/regions', () => {
        lists += 1
        return HttpResponse.json(monthEndBody('GET /api/reference/regions'))
      }),
      http.get('*/api/reference/areas', () => {
        lists += 1
        return HttpResponse.json(monthEndBody('GET /api/reference/areas'))
      }),
    )
    const user = setupUser()
    renderAt('/admin/monthly-menu/membership?month=2026-08')
    await sheetNamed('Membership by group')
    expect(lists, 'a list behind a control is never asked for at first paint (MD69)').toBe(0)

    const area = screen.getByLabelText(/^Area/)
    await user.click(area)
    await waitFor(() => expect(within(area).getAllByRole('option').map((option) => option.textContent))
      .toEqual(['All areas', 'NORTH region', 'SOUTH region', 'Area A', 'Area B', 'Area Y']))
    await user.selectOptions(area, 'area:A')
    await waitFor(() => expect(asked.at(-1)).toBe('membership 2026-08 ?area=A'))
    expect(screen.getByTestId('where')).toHaveTextContent('/admin/monthly-menu/membership?month=2026-08&area=A')

    await user.selectOptions(screen.getByLabelText(/^Month/), '2026-09')
    await waitFor(() => expect(asked.at(-1)).toBe('membership 2026-09 ?area=A'))
    await user.selectOptions(screen.getByLabelText(/^Area/), 'region:NORTH')
    await waitFor(() => expect(asked.at(-1)).toBe('membership 2026-09 ?region=NORTH'))
    await user.selectOptions(screen.getByLabelText(/^Area/), '')
    await waitFor(() => expect(asked.at(-1)).toBe('membership 2026-09 '))
    expect(screen.getByTestId('where')).toHaveTextContent('/admin/monthly-menu/membership?month=2026-09')
    expect(lists, 'the lists are kept for the visit').toBe(2)
  })

  it('a place in the address is shown as chosen before its list is asked for -- and read for', async () => {
    renderAt('/admin/monthly-menu/membership?month=2026-08&region=NORTH')

    await sheetNamed('Membership by group')
    expect(screen.getByLabelText(/^Area/)).toHaveValue('region:NORTH')
    expect(asked).toEqual(['membership 2026-08 ?region=NORTH'])
  })
})
