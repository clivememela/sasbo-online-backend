// @vitest-environment jsdom -- the report's name, on every surface a person reads it from
import { describe, expect, it } from 'vitest'
import { screen } from '@testing-library/react'

import ReportOptions from '../ReportOptions'
import { OrganizerReport } from './OrganizerReport'
import Breadcrumbs from '../../components/common/Breadcrumbs'
import { navItems } from '../../config/navigation'
import { renderWithProviders } from '../../test/renderWithProviders'

/**
 * 🗣️ "ORGANIZER REPORT" IS NOW "LOCATION VISIT" -- SASBO, 18 Sep 2026, and in their own notes, "change only".
 *
 * The name confused the people filling the form in: "it says on top ... organizers, branch or department report
 * form". So it changes everywhere it is READ -- the sidebar, the Reports menu, the page title, the letterhead and
 * the breadcrumb -- and nowhere it is merely ADDRESSED: the route stays `/reports/organizer-report`, so no bookmark,
 * tool or suite path moves.
 *
 * The last case is the guard and the three before it are its non-vacuity half: "no surface says Organizer Report"
 * passes just as well on a page that rendered nothing, so each surface is first shown to say the new name.
 */
const ROUTE = '/reports/organizer-report'

describe('the Location Visit report', () => {
  it('is named Location Visit on the Reports menu, and still opens the same route', () => {
    renderWithProviders(<ReportOptions />)

    expect(screen.getByRole('link', { name: /Location Visit/ })).toHaveAttribute('href', ROUTE)
  })

  it('is titled Location Visit, and its letterhead says so', () => {
    renderWithProviders(<OrganizerReport />)

    expect(screen.getByRole('heading', { level: 1, name: 'Location Visit' })).toBeInTheDocument()
    expect(screen.getByRole('heading', { level: 2, name: /Location Visit: Branch \/ Department/ })).toBeInTheDocument()
  })

  it('is named Location Visit in the sidebar and the breadcrumb', () => {
    const reports = navItems.find((item) => item.title === 'Reports')
    expect(reports.subItems.find((entry) => entry.href === ROUTE)?.title).toBe('Location Visit')

    renderWithProviders(<Breadcrumbs />, { initialEntries: [ROUTE] })
    expect(screen.getByRole('navigation', { name: 'Breadcrumb' })).toHaveTextContent('Location Visit')
  })

  it('🗣️ 5 Oct: asks for the staff "Complement" -- "Compliment" is nowhere on the form', () => {
    renderWithProviders(<OrganizerReport />)

    expect(screen.getByLabelText('Complement')).toBeInTheDocument()
    expect(document.body.textContent).not.toMatch(/Compliment/i)
  })

  it('says "Organizer Report" nowhere a person can read it', () => {
    renderWithProviders(
      <>
        <Breadcrumbs />
        <ReportOptions />
        <OrganizerReport />
      </>,
      { initialEntries: [ROUTE] },
    )

    expect(document.body).toHaveTextContent(/Location Visit/)
    expect(document.body.textContent).not.toMatch(/Organi[sz]er'?s? Report/i)
    expect(JSON.stringify(navItems.map(({ title, subItems }) => ({ title, subItems: subItems?.map((s) => s.title) }))))
      .not.toMatch(/Organi[sz]er'?s? Report/i)
  })
})
