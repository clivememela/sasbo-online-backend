// @vitest-environment jsdom -- the navigation section that holds the route you arrived on
import { beforeEach, describe, expect, it } from 'vitest'
import { screen } from '@testing-library/react'

import App from '../App'
import { renderWithProviders } from '../test/renderWithProviders'
import { server } from '../test/msw/server'
import { smokeHandlers } from '../test/msw/smokeLayer'

/**
 * 🔴 A DEEP LINK ARRIVES WITH ITS OWN SECTION OPEN -- WITNESSED BEFORE IT MOVED. `10.4`, batch 8.
 *
 * `/members/capture` from a bookmark should land with Members expanded and Capture Member visible under it, which
 * is how the mock draws the rail. That was done by an effect keyed on the pathname, and
 * `react-hooks/set-state-in-effect` makes it an error this phase. `shell.test.jsx` covers expanding a section BY
 * HAND, from the keyboard; nothing covered the route doing it. These two pin that before the effect becomes a
 * render-phase update -- the same pattern `Sidebar.jsx` already uses twenty lines above it for the selected card.
 *
 * The deletion proof is removing the expansion outright: the first test goes red, the must-pass does not.
 */

const appRoute = (path) => renderWithProviders(<App />, { initialEntries: [path] })

describe('the rail, on arrival', () => {
  beforeEach(() => {
    server.use(...smokeHandlers)
  })

  it('a deep link into a section opens that section', async () => {
    appRoute('/members/capture')

    const members = await screen.findByRole('button', { name: /^Collapse Members$/ })
    expect(members).toHaveAttribute('aria-expanded', 'true')
  })

  it('the must-pass: a route in no section leaves every section closed', async () => {
    // 🔑 Without this, a rail that opened EVERY section on arrival would pass the test above.
    appRoute('/home')
    await screen.findByRole('button', { name: /open navigation menu/i })

    expect(screen.queryByRole('button', { name: /^Collapse / })).not.toBeInTheDocument()
  })
})
