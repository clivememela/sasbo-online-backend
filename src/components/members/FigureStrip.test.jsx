// @vitest-environment jsdom -- renders the register's figures and reads them back
import { describe, expect, it } from 'vitest'
import { render, screen, within } from '@testing-library/react'

import FigureStrip from './FigureStrip'

/**
 * A FIGURE IS A READING, AND A READING NOBODY TOOK IS NOT A ZERO. `06.3`, K1.
 *
 * 🔴 With the register unreachable the strip printed Total 0 · Active 0 · Pending 0 · Suspended 0
 * -- the union, apparently, with no members -- directly above the sentence saying the register
 * could not be reached. It printed the same row of zeros for the length of every load, so the
 * first thing the register said on every visit was that it was empty.
 */
const ZEROS = { total: 0, active: 0, pending: 0, suspended: 0, newThisMonth: 0, newDebitOrders: 0, updatedToday: 0 }
const strip = () => within(screen.getByTestId('figure-strip'))

describe('FigureStrip', () => {
  it('prints no figure while the register cannot be reached', () => {
    render(<FigureStrip {...ZEROS} state="error" />)

    expect(strip().queryByText('0')).not.toBeInTheDocument()
    // Each figure still has a value for a screen reader, and the value is the truth.
    expect(strip().getAllByText('not available')).toHaveLength(6)
    // K1 holds for a figure's second line too: no "0 by debit order" under a figure nobody read.
    expect(screen.getByTestId('figure-strip')).not.toHaveTextContent(/by debit order/)
  })

  it('prints no figure while the register is still loading', () => {
    render(<FigureStrip {...ZEROS} state="loading" />)

    expect(strip().queryByText('0')).not.toBeInTheDocument()
    expect(screen.getByTestId('figure-strip')).not.toHaveTextContent(/by debit order/)
    expect(screen.getByTestId('figure-strip')).toHaveAttribute('aria-busy', 'true')
  })

  it('a register that really is empty still reads zero', () => {
    // The pair: the locators above are seen to match when zero is the truth.
    render(<FigureStrip {...ZEROS} />)

    expect(strip().getAllByText('0')).toHaveLength(6)
    expect(strip().getByText('0 by debit order')).toBeInTheDocument()
    expect(screen.getByTestId('figure-strip')).not.toHaveAttribute('aria-busy')
  })

  it('🗣️ New this month is ONE figure with its make-up underneath -- six figures, which the grid divides evenly', () => {
    // SASBO, 18 Sep 2026. Two separate figures would make seven, and seven leave one figure alone on a row in the
    // stylesheet's three-column mode -- the owner's own 1280 screen with a record docked. Six fill it: 3 + 3, 2 + 2 + 2.
    render(<FigureStrip {...ZEROS} newThisMonth={2418} newDebitOrders={1902} />)

    expect(strip().getAllByRole('term').map((term) => term.textContent))
      .toEqual(['Total', 'Active', 'Pending', 'Suspended', 'New this month', 'Updated today'])
    // Space-grouped, like every figure on the strip. `\s`, not a codepoint: which no-break space en-ZA groups with has
    // changed between ICU versions (format.test.js), and this is about the grouping, not the Node build.
    expect(strip().getByText(/^2\s418$/)).toBeInTheDocument()
    expect(strip().getByText(/^1\s902 by debit order$/)).toBeInTheDocument()
  })

  it('the must-pass: a caller that passes no month figure gets the strip it had', () => {
    render(<FigureStrip total={1} active={1} pending={0} suspended={0} />)

    expect(strip().getAllByRole('term')).toHaveLength(4)
    expect(screen.getByTestId('figure-strip')).not.toHaveTextContent(/New this month|by debit order/)
  })
})
