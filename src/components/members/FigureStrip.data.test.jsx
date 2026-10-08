// @vitest-environment jsdom -- renders the strip from figures given as data, and reads them back
import { describe, expect, it } from 'vitest'
import { render, screen, within } from '@testing-library/react'

import FigureStrip, { registerFigures } from './FigureStrip'

/**
 * 02.4 (month end, BR9): THE STRIP TAKES ITS FIGURES AS DATA, so the register and the first screen are one mechanism.
 * The register's own three test files -- `FigureStrip.test.jsx`, `Members.states.test.jsx` and
 * `Members.newThisMonth.test.jsx` -- are untouched by it and still pass the register's props; this file holds the
 * other half: a list of figures, each ruled in the order given, each with its definition once it was read.
 */
const strip = () => within(screen.getByTestId('figure-strip'))
const readings = (label) =>
  within(strip().getByText(label).closest('div')).getAllByRole('definition').map((dd) => dd.textContent)

const FIGURES = [
  { label: 'Paying members', value: 2300, definition: 'on record and not suspended' },
  { label: 'Suspended', value: 118, definition: "by SASBO's suspension codes" },
  { label: 'Penetration', value: null, definition: 'paying members over staff complement' },
]

describe('FigureStrip, given its figures as data', () => {
  it('rules them in the order given, under the name it is given, each with the definition it used', () => {
    render(<FigureStrip label="The month's figures" figures={FIGURES} />)

    expect(screen.getByTestId('figure-strip')).toHaveAttribute('aria-label', "The month's figures")
    expect(strip().getAllByRole('term').map((term) => term.textContent)).toEqual(['Paying members', 'Suspended', 'Penetration'])
    expect(readings('Paying members').map((text) => text.replace(/\s/g, ' '))).toEqual(['2 300', 'on record and not suspended'])
    expect(readings('Suspended')).toEqual(['118', "by SASBO's suspension codes"])
  })

  it('🔴 a figure answered as nothing is not available -- never a nought', () => {
    render(<FigureStrip figures={FIGURES} />)

    const penetration = strip().getByText('Penetration').closest('div')
    expect(within(penetration).getByText('not available')).toBeInTheDocument()
    expect(penetration).not.toHaveTextContent(/\d/)
  })

  it('the must-pass: a figure that really is nought reads 0', () => {
    render(<FigureStrip figures={[{ label: 'Suspended', value: 0, definition: "by SASBO's suspension codes" }]} />)

    expect(readings('Suspended')).toEqual(['0', "by SASBO's suspension codes"])
  })

  it.each(['loading', 'error'])('K1: while %s, no figure and no definition is said', (state) => {
    render(<FigureStrip figures={FIGURES} state={state} />)

    expect(screen.getByTestId('figure-strip')).not.toHaveTextContent(/\d|on record and not suspended|suspension codes/)
  })

  it('the register\'s props are the same figures as data -- one mechanism, two callers', () => {
    const props = { total: 6, active: 3, pending: 1, suspended: 2, newThisMonth: 3, newDebitOrders: 1, updatedToday: 0 }
    const { container: asProps } = render(<FigureStrip {...props} />)
    const { container: asData } = render(<FigureStrip figures={registerFigures(props)} />)

    expect(asData.innerHTML).toBe(asProps.innerHTML)
  })
})
