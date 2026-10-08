// @vitest-environment jsdom -- BR-18 is about the RENDERED document, not the source
import { afterEach, beforeEach, describe, expect, it } from 'vitest'
import { cleanup, render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'

import App from '../App'
import { ThemeProvider } from '../context/ThemeContext'
import { server } from './msw/server'
import { smokeHandlers } from './msw/smokeLayer'
import { assertNonVacuousScan, FLOORS } from './assertNonVacuousScan'
import { LAZY_DECLARATIONS, SPLIT_PAGES, warmSplitPages } from './splitPages'

/**
 * BR-18 — ONE `<h1>` PER PAGE, AND IT NAMES THE PAGE.
 *
 * 🔴 WHY THIS COULD NOT BE A SOURCE SCAN. The heading a user meets is composed: the
 * shell contributes one, the page contributes another, and neither file can see the
 * other. P0 measured the rendered DOM and found the application's NAME as the `<h1>` on
 * 23 of 27 routes -- the same string every time, so the one landmark a screen-reader
 * user navigates by said nothing about where they were. Promoting `PageTitle` to `<h1>`
 * on its own would have given those 23 routes TWO. It is one change in two places, and
 * only a render can tell whether it landed.
 *
 * THE CONTRACT, IN TWO HALVES
 *   1. NO route may render more than one `<h1>`. Hard, and permanent.
 *   2. The routes rendering ZERO are exactly `AWAITING_PAGE_TITLE` -- P4's own schedule.
 *      A page adopts `PageTitle` in its wave and its line is deleted here, in that
 *      commit. The list reaches empty when P4 closes.
 *
 * 🔑 THE SECOND HALF IS AN EQUALITY, NOT A SUBSET, and that is what makes it a gate
 * rather than a wish. A subset check ("no unexpected route is missing a heading") stays
 * green forever while the list rots. An equality means a page that GAINS its heading
 * reddens this file until its line is removed -- so the list can only shrink, and it
 * cannot shrink silently.
 *
 * ⚠️ ITS OWN REDDENING CHANGE, run and reverted at `04.1`: turn `PageTitle`'s `<h1>`
 * back into an `<h2>`. Half 2 goes red naming every route that lost its heading. Put the
 * shell's `<h1>` back instead and half 1 goes red naming every route that now has two.
 * Both directions were exercised; a guard that can only fail one way is half a guard.
 */

/** Read App.jsx as text and extract every declared path -- the route smoke's own scan. */
const discoverRoutes = () => {
  const sources = import.meta.glob('../App.jsx', {
    query: '?raw',
    import: 'default',
    eager: true,
  })
  const source = Object.values(sources)[0]
  if (!source) {
    throw new Error(
      'The route scan could not read App.jsx at all. That is a broken walker, not an ' +
        'application with no routes -- fix the glob rather than accepting an empty scan.',
    )
  }

  const declared = [...source.matchAll(/path="([^"]*)"/g)].map((match) => match[1])
  const absolute = declared.map((path) => (path.startsWith('/') ? path : `/${path}`))

  return [...new Set(['/', ...absolute])].sort()
}

const ROUTES = discoverRoutes()

// Every split page's first import, paid before any route is timed (`./splitPages.js`): the 5 s below waits for React
// to swap a fallback for a page that has arrived, never for this machine to transform one.
await warmSplitPages()

/**
 * Routes with no page-level `<h1>` yet.
 *
 * ✅ EMPTY. It opened at `04.1` with nine entries and `04.3` cleared the last eight.
 * With nothing on it, the two assertions below collapse into one rule that admits no
 * exceptions: EVERY route renders exactly one `<h1>`, and it names the page.
 *
 * 🔑 IT STAYS RATHER THAN BEING DELETED, for the reason the equality below is an
 * equality: a route that loses its heading tomorrow reddens this file, and the only way to
 * quiet it is to add a line here -- which is visible as an exemption, where a deleted
 * mechanism would just be a passing test.
 */
const AWAITING_PAGE_TITLE = {}

const renderRoute = (path) =>
  render(
    <MemoryRouter initialEntries={[path]}>
      <ThemeProvider>
        <App />
      </ThemeProvider>
    </MemoryRouter>,
  )

/** Every `<h1>` a route renders, by its text, after the page itself has arrived. */
const headingsFor = async (path) => {
  renderRoute(path)

  // A split route renders its Suspense fallback first. Counting headings then counts the
  // FALLBACK's headings -- which is zero for every route, so the whole guard would agree
  // with itself and mean nothing.
  await waitFor(
    () =>
      expect(
        screen.queryByTestId('route-pending'),
        `Route "${path}" never got past its Suspense fallback, so its headings were ` +
          `never rendered to be counted.`,
      ).not.toBeInTheDocument(),
    { timeout: 5000 },
  )

  return [...document.querySelectorAll('h1')].map((h1) => h1.textContent.trim())
}

describe('BR-18 — the heading contract', () => {
  beforeEach(() => {
    server.use(...smokeHandlers)
  })

  afterEach(cleanup)

  it('the route scan found the whole route table', () => {
    assertNonVacuousScan(ROUTES, FLOORS.ROUTES, 'routes declared in App.jsx')
    expect(
      SPLIT_PAGES.length,
      'a lazy() page is declared in a spelling splitPages.js cannot read, so its first import is timed again',
    ).toBe(LAZY_DECLARATIONS)
  })

  it('no route renders more than one <h1>, and the ones with none are P4 schedule', async () => {
    const measured = new Map()

    for (const path of ROUTES) {
      // Sequential, not concurrent: each render owns `document`, and two routes in the
      // same document would count each other's headings.
      measured.set(path, await headingsFor(path))
      cleanup()
    }

    // 🔴 NON-VACUITY. If every render threw or every route stalled on its fallback, the
    // map is empty -- and "no route has two <h1>s" is perfectly true of no routes.
    assertNonVacuousScan(
      [...measured.keys()],
      FLOORS.HEADINGS_MEASURED,
      'routes mounted and measured for headings',
    )

    const doubled = [...measured]
      .filter(([, headings]) => headings.length > 1)
      .map(([path, headings]) => `${path} -> ${headings.length}: ${headings.join(' | ')}`)

    expect.soft(
      doubled,
      'BR-18: a route renders more than one <h1>. A page has exactly one top-level ' +
        'heading -- the shell contributes a banner, not a heading, and PageTitle is the ' +
        "page's only <h1>.",
    ).toEqual([])

    const headless = [...measured]
      .filter(([, headings]) => headings.length === 0)
      .map(([path]) => path)
      .sort()

    expect.soft(
      headless,
      'BR-18: the set of routes with NO page heading has changed. If a route gained ' +
        'one, delete its line from AWAITING_PAGE_TITLE in this file, in the same ' +
        'commit. If a route LOST one, that is a regression: the shell no longer ' +
        'supplies an <h1>, so a page without PageTitle has no heading at all.',
    ).toEqual(Object.keys(AWAITING_PAGE_TITLE).sort())

    // 🔑 THE MUST-PASS HALF. Everything above is satisfied by a counter that always
    // returns zero -- `doubled` empty, `headless` everything -- so on its own it cannot
    // tell a converted page from a broken query. This asserts the counter SEES.
    const headed = [...measured].filter(([, headings]) => headings.length === 1)

    expect.soft(
      headed.length,
      'Not one route rendered exactly one <h1>. The heading query is broken, or ' +
        'PageTitle stopped rendering an <h1> -- either way every assertion above ' +
        'passed while measuring nothing.',
    ).toBeGreaterThan(ROUTES.length - Object.keys(AWAITING_PAGE_TITLE).length - 1)

    // ...and that the one it sees is the PAGE's, not the shell's. The shell's banner is
    // identical on every route; a heading that repeats is the defect BR-18 names.
    const distinct = new Set(headed.map(([, headings]) => headings[0]))

    expect.soft(
      distinct.size,
      'Every route with a heading renders the SAME heading text, which is exactly the ' +
        'shape P0 measured: the application\'s name promoted to the page\'s landmark. ' +
        'The <h1> must name the PAGE.',
    ).toBeGreaterThan(1)
  }, 60_000)
})
