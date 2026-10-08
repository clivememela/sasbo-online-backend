// @vitest-environment jsdom -- the phone drawer: what a keyboard reaches while it is closed, and what opens it
import { beforeEach, describe, expect, it } from 'vitest'
import { screen, waitFor, within } from '@testing-library/react'

import App from '../App'
import { renderWithProviders } from '../test/renderWithProviders'
import { server } from '../test/msw/server'
import { smokeHandlers } from '../test/msw/smokeLayer'
import { setupUser } from '../test/user'

/**
 * 🔑 THE ROUTE'S MODULE IS IMPORTED HERE, AND THAT IS AN ANTI-FLAKE FIX RATHER THAN AN UNUSED IMPORT.
 *
 * `App` loads every page with `lazy(() => import(...))`. The assertions below wait for Home to RENDER, so
 * without this they wait for a dynamic import -- and how long that takes depends on how busy the machine is.
 * Measured: green in isolation, red inside the full suite on a box down to 227 MB free. Importing the module
 * here puts it in the registry before the test runs, so `lazy` resolves on the next microtask and the result
 * stops depending on load. Same rule as `setupUser`'s `delay: null`: fix the wall-clock dependence, never the
 * threshold.
 */
import '../pages/Home.jsx'

/**
 * 🔴 R1-F2 and R1-F4 (P9's independent review), on the rail.
 *
 *   R1-F2  Below `lg` the drawer is hidden by `-translate-x-64` ALONE -- no `inert`, no `aria-hidden`, no
 *          `visibility`. Every rail link and disclosure stayed focusable while off canvas, so a keyboard user on a
 *          narrow window tabbed through about twenty invisible stops before reaching the page (WCAG 2.4.3 / 2.4.7),
 *          and opening the drawer moved no focus at all: focus stayed on the hamburger, behind the scrim.
 *   R1-F4  The wordmark was a raw `<a href="/home">`, so pressing it reloaded the whole application -- a full
 *          document navigation that drops any page state, asks nothing about unsaved work, and on a phone leaves the
 *          drawer open because `setSideBarOpen` never runs.
 *
 * jsdom has no layout, so "off canvas" cannot be measured here. What CAN be measured is the thing that matters: is the
 * hidden rail in the tab order, and is the wordmark a router link or a document navigation. The must-pass in each pair
 * is the desk, where the rail must stay reachable.
 */

const RAIL = () => within(screen.getByRole('navigation', { name: 'Main' }))

/** The window width the rail reads through `useMediaQuery`. jsdom's default is 1024, which IS the desk breakpoint. */
function atWidth(width) {
  window.innerWidth = width
  window.matchMedia = (query) => ({
    matches: /max-width:\s*(\d+)px/.test(query) ? width <= Number(query.match(/max-width:\s*(\d+)px/)[1]) : width >= 1024,
    media: query,
    addEventListener() {},
    removeEventListener() {},
    addListener() {},
    removeListener() {},
    dispatchEvent: () => false,
  })
}

describe('the phone drawer, closed', () => {
  beforeEach(() => {
    server.use(...smokeHandlers)
  })

  it('R1-F2: its links are out of the tab order until it is opened', async () => {
    atWidth(390)
    renderWithProviders(<App />, { initialEntries: ['/home'] })
    await screen.findByRole('button', { name: /open navigation menu/i })

    const members = RAIL().getByRole('link', { name: /^Members/ })
    expect(members.closest('[inert], [aria-hidden="true"]'), 'the closed drawer is still tabbable').not.toBeNull()
  })

  it('R1-F2: opening it puts focus inside the drawer, not behind the scrim', async () => {
    atWidth(390)
    const user = setupUser()
    renderWithProviders(<App />, { initialEntries: ['/home'] })

    await user.click(await screen.findByRole('button', { name: /open navigation menu/i }))

    const close = await screen.findByRole('button', { name: /close navigation menu/i })
    await waitFor(() => expect(close).toHaveFocus())
  })

  it('the must-pass: at desk width the rail is reachable and nothing is inert', async () => {
    atWidth(1440)
    renderWithProviders(<App />, { initialEntries: ['/home'] })
    await screen.findByRole('navigation', { name: 'Main' })

    const members = RAIL().getByRole('link', { name: /^Members/ })
    expect(members.closest('[inert], [aria-hidden="true"]')).toBeNull()
  })
})

describe('the rail wordmark (R1-F4)', () => {
  beforeEach(() => {
    server.use(...smokeHandlers)
  })

  it('is a router link, not a document navigation', async () => {
    atWidth(1440)
    const user = setupUser()
    renderWithProviders(<App />, { initialEntries: ['/members'] })

    const wordmark = await screen.findByRole('link', { name: /SASBO Online home/i })
    await user.click(wordmark)

    // A full reload in jsdom is "Not implemented: navigation", never a rendered Home.
    /*
     * 🔴 THE SUITE'S BUDGET, NOT TESTING LIBRARY'S DEFAULT -- the same fix, for the same reason, as
     * `errorBoundaryRoutes.test.jsx` (`e7e92ef`). Home is already imported above, so what is left is the cost of
     * rendering the shell and the page, and that belongs to the BOX. Measured alone on 2026-09-21, with OneDrive and
     * Explorer holding ~1.8 cores: **355ms, 497ms, 1,307ms** -- and RED in a full run at four workers, against the
     * implicit 1,000ms nobody chose. 5,000ms is the suite's own figure at its slowest sites, still far under
     * `testTimeout`, so a wordmark that reloads the document (R1-F4's defect) still fails here.
     */
    expect(
      await screen.findByRole('heading', { level: 1, name: /welcome to sasbo online/i }, { timeout: 5000 }),
    ).toBeInTheDocument()
  })
})
