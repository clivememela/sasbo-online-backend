// @vitest-environment jsdom -- Month end with its figures throwing: the boundary holds them, and the rest of the page stands
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { screen, waitFor } from '@testing-library/react'

import MonthlyMenu from './MonthlyMenu'
import { LISTINGS, STATISTICS } from './monthEndRules'
import { monthEndAnswer } from '../../test/msw/monthEndExamples'
import { server } from '../../test/msw/server'
import { renderWithProviders } from '../../test/renderWithProviders'

/**
 * 🔑 THE FIGURES' OWN BOUNDARY, PROVED (review-home). `MonthlyMenu.jsx` holds the figures in an `ErrorBoundary` of their
 * own, so a figures section that throws -- a chunk that will not load, an answer that breaks a render -- takes only
 * itself down, and says so in one line. Without it the route's boundary would catch the throw and the whole page would
 * go: the month, the statistics, the pack and the listings with it. Nothing reached that fallback on Home before the
 * figures moved, nor on Month end after; this file is the case, and the figures are made to throw in it alone (the
 * mock is this file's).
 */
vi.mock('./MonthEndFigures.jsx', () => ({
  default: () => {
    throw new Error('Synthetic: the figures failed to render')
  },
}))

beforeEach(() => {
  // React and the boundary's sink both report the throw; the report is not the assertion.
  vi.spyOn(console, 'error').mockImplementation(() => {})
  server.use(monthEndAnswer('GET /api/month-end/months'))
})

describe('Month end, when its figures throw', () => {
  it('says so in one line where the figures were -- and the month, the statistics, the pack and the listings stand', async () => {
    renderWithProviders(<MonthlyMenu />, { initialEntries: ['/admin/monthly-menu'] })
    await waitFor(() => expect(screen.getByLabelText(/^Month/)).toBeEnabled())

    expect(screen.getByText('The figures could not be shown. Reload the page to try again.')).toBeInTheDocument()
    expect(screen.queryByRole('region', { name: 'The month’s figures' })).not.toBeInTheDocument()

    // The rest of the page, whole: the page's own head and month, every statistic for the month it opened on, the
    // pack's control, and the four listings.
    expect(screen.getByRole('heading', { level: 1, name: 'Month end' })).toBeInTheDocument()
    expect(screen.getByLabelText(/^Month/)).toHaveValue('2026-08')
    expect(screen.getAllByRole('link').map((link) => link.getAttribute('href')))
      .toEqual(STATISTICS.map((statistic) => `/admin/monthly-menu/${statistic.key}?month=2026-08`))
    expect(screen.getByRole('button', { name: 'Prepare the pack for August 2026' })).toBeEnabled()
    for (const listing of LISTINGS) expect(screen.getByText(listing.name)).toBeInTheDocument()
  })
})
