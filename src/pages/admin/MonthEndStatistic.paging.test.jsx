// @vitest-environment jsdom -- a statistic's sheet longer than a page: paged, its total on every page
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { screen, within } from '@testing-library/react'
import { http, HttpResponse } from 'msw'

import MonthEndStatistic from './MonthEndStatistic'
import { SHEET_PAGE } from './monthEndRules'
import { expectNoA11yViolations } from '../../test/assertions'
import { monthEndAnswer, monthEndBody } from '../../test/msw/monthEndExamples'
import { renderWithProviders } from '../../test/renderWithProviders'
import { server } from '../../test/msw/server'
import { setupUser } from '../../test/user'

/**
 * MONTH END `04.9` ON THE LINK -- THE BRANCHES DUE A VISIT, A ROW A BRANCH.
 *
 * At `all` the sheet holds every branch of SASBO's: on the link on 1 Oct, some 5,500 rows and 814 KB, every one of
 * them drawn. A sheet longer than a page now shows a page of it, the sheet's total the last row of every page, and the
 * pager says where the reader is. The answer is the server's own golden one, its rows multiplied -- invented branches,
 * in the golden row's shape.
 */

const golden = monthEndBody('GET /api/month-end/{month}/visits')

/** The golden answer with `count` rows: R/A 9xxxxx INVENTED BRANCH n, each never visited. */
const withRows = (count) => {
  const sheet = golden.sheets[0]
  const row = sheet.rows.find((candidate) => !candidate.fewerThanFive && !candidate.withheld) ?? sheet.rows[0]
  const rows = Array.from({ length: count }, (_, index) => ({
    ...row,
    key: `W${index + 1}`,
    label: `R/A ${900001 + index} INVENTED BRANCH ${index + 1} · never visited`,
    fewerThanFive: false,
    withheld: false,
  }))
  return { ...golden, sheets: [{ ...sheet, rows }] }
}

const answering = (body) => http.get('*/api/month-end/:month/visits', () => HttpResponse.json(body))

const renderVisits = () => renderWithProviders(<MonthEndStatistic />, {
  initialEntries: [`/admin/monthly-menu/visits?month=${golden.month}`],
  path: '/admin/monthly-menu/:statistic',
})

const theSheet = async () => (await screen.findByRole('heading', { level: 2, name: golden.sheets[0].title })).closest('section')
const labels = (section) => within(within(section).getByRole('table')).getAllByRole('row')
  .filter((row) => row.hasAttribute('data-row'))
  .map((row) => within(row).getAllByRole('cell')[0].textContent)

beforeEach(() => {
  vi.spyOn(console, 'error').mockImplementation(() => {})
  server.use(monthEndAnswer('GET /api/month-end/months'))
})

describe('a statistic\'s sheet longer than a page (04.9 at `all`)', () => {
  it('🔴 shows a page of its rows, the total the last row, and a pager that says where the reader is', async () => {
    const rows = SHEET_PAGE * 2 + 20
    server.use(answering(withRows(rows)))
    const user = setupUser()
    renderVisits()

    const section = await theSheet()
    let shown = labels(section)
    expect(shown).toHaveLength(SHEET_PAGE + 1)
    expect(shown[0]).toMatch(/INVENTED BRANCH 1 /)
    expect(shown.at(-1)).toBe('Total')
    expect(within(section).getByText(/Page 1 of 3/)).toBeInTheDocument()

    await user.click(within(section).getByRole('button', { name: /Next/ }))
    shown = labels(section)
    expect(shown[0]).toMatch(new RegExp(`INVENTED BRANCH ${SHEET_PAGE + 1} `))
    expect(shown.at(-1)).toBe('Total')
    expect(within(section).getByText(/Page 2 of 3/)).toBeInTheDocument()

    await user.click(within(section).getByRole('button', { name: /Next/ }))
    shown = labels(section)
    expect(shown).toHaveLength(20 + 1)
    expect(shown.at(-1)).toBe('Total')
    expect(within(section).getByRole('button', { name: /Next/ })).toBeDisabled()
  })

  it('the must-pass: a sheet of a page or fewer has no pager, and every row', async () => {
    server.use(answering(withRows(SHEET_PAGE)))
    renderVisits()

    const section = await theSheet()
    expect(labels(section)).toHaveLength(SHEET_PAGE + 1)
    expect(within(section).queryByText(/Page 1 of/)).not.toBeInTheDocument()
    expect(within(section).queryByRole('button', { name: /Next/ })).not.toBeInTheDocument()
  })

  it('has no accessibility violations with a sheet paged', async () => {
    server.use(answering(withRows(SHEET_PAGE + 1)))
    const { container } = renderVisits()

    await theSheet()
    await expectNoA11yViolations(container)
  })
})
