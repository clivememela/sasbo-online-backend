// @vitest-environment jsdom -- the recruitment roster's 120 inputs: what they are called, and what their prompts wear
import { describe, expect, it } from 'vitest'
import { screen } from '@testing-library/react'

import GeneralForm from './GeneralForm'
import { renderWithProviders } from '../../test/renderWithProviders'

/**
 * 🔴 R4-F3 (P9's independent review): THE ROSTER ASKED A SCREEN-READER USER FOR AN "INDUSTRY CODE".
 *
 * The visible column head is *Indicator*, and the slip above the form defines its values as J / NI / NA / NT --
 * joined, not interested, not applicable, not traced. The accessible name said "Industry code for non-member N",
 * which is a different thing entirely, on a union recruitment roster. The label was added by this arc, so it is this
 * arc's to fix.
 *
 * 🔑 THIS FORM IS A STATE-GATED SURFACE: it renders only after choosing *General Form* on `/reports/forms`, so no
 * route-level sweep, matrix or contrast pass has ever reached it. That is exactly why its defects survived to P9, and
 * why the label is asserted here rather than assumed from source.
 *
 * R4-F2, its neighbour, is the same inputs' placeholder colour. It is guarded in
 * `src/test/inkFaintIsNeverText.test.js`, where the rule about `ink-faint` on text already lives -- with a canary for
 * the `placeholder-` spelling the guard could not see.
 */

describe('the General Form roster', () => {
  it('names its indicator inputs "Indicator", the way the column and the instructions do', () => {
    renderWithProviders(<GeneralForm />)

    expect(screen.getByLabelText('Indicator for non-member 1')).toBeInTheDocument()
    expect(screen.queryByLabelText(/industry code/i)).not.toBeInTheDocument()
  })

  it('the must-pass: the name inputs are still named per row, and the column heads are unchanged', () => {
    renderWithProviders(<GeneralForm />)

    expect(screen.getByLabelText('Name of non-member 1')).toBeInTheDocument()
    expect(screen.getAllByText('Indicator').length).toBeGreaterThan(0)
    expect(screen.getAllByText('Name of Non-Member').length).toBeGreaterThan(0)
  })
})
