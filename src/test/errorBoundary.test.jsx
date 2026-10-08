// @vitest-environment jsdom -- renders a component that throws, inside a boundary
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'

import ErrorBoundary from '../components/common/ErrorBoundary'
import { ThemeProvider } from '../context/ThemeContext'
import { server } from './msw/server'
import { smokeHandlers } from './msw/smokeLayer'

/**
 * A FAILED RENDER. ✅ **A11 IS CLOSED — this file is now the regression pair.**
 *
 * The three-state rule -- loading, empty, failed -- is about a failed FETCH. Nothing
 * covered a failed RENDER, and the two have completely different blast radii: a failed
 * fetch spoils one panel, while an uncaught render error unmounts the entire React tree.
 * The user got a white page. No message, no navigation, no way back, and nothing in the
 * console a non-developer would ever see.
 *
 * 🔑 WHAT CHANGED, AND WHY THE PINS ARE GONE RATHER THAN REPAIRED. `03.1` shipped
 * `ErrorBoundary` (R-ROB-A) -- earlier than the plan's `03.2b`, because `03.1` is the
 * unit that made every route `lazy()`, and a chunk that fails to load throws during
 * render on an otherwise healthy application. Adding the boundary turned the pins red,
 * which is the register's mechanism working: their message said "un-skip the test above,
 * delete the pins, and close the row", and that is what happened. The skipped
 * standard-facing test below is now the live one. **A pin is flipped, never repaired**
 * (BR-5) -- "repairing" these would have meant deleting the boundary.
 *
 * ⚠️ WHAT IS NOT CLOSED. `06.1` owns the DESIGNED failed state. This asserts the
 * contract -- the shell survives, something is said, there is a way back -- and not how
 * it looks.
 *
 * These tests use a component that throws ON PURPOSE. React logs the error to
 * `console.error` regardless of whether it is caught, so that is silenced here --
 * otherwise a deliberate, expected throw prints a red stack trace on every green run,
 * which is how people learn to ignore red stack traces.
 */

/** The boundary element, OPENED -- with or without props. A `key` is props, and the first spelling of this
 *  predicate did not know that. Its canary is the last test in this file. */
const BOUNDARY_TAG = /<ErrorBoundary(\s[^>]*)?>/

/** A component that fails the way a real one does: while rendering. */
const Exploding = () => {
  throw new Error('a component failed while rendering')
}

/**
 * The shell, with the boundary where the application puts it: around the page content,
 * INSIDE the header and navigation. That placement is the assertion -- a boundary
 * wrapped around the router would catch the same throw and still blank the shell.
 */
const Shell = ({ children }) => (
  <MemoryRouter initialEntries={['/home']}>
    <ThemeProvider>
      <header>
        <h1>SASBO</h1>
        <nav aria-label="Main">
          <a href="/members">Members</a>
        </nav>
      </header>
      <main>
        <ErrorBoundary>{children}</ErrorBoundary>
      </main>
    </ThemeProvider>
  </MemoryRouter>
)

beforeEach(() => {
  server.use(...smokeHandlers)
  vi.spyOn(console, 'error').mockImplementation(() => {})
})

afterEach(() => {
  vi.restoreAllMocks()
})

describe('when a component throws while rendering', () => {
  it('the shell survives, and offers a way to recover', () => {
    render(
      <Shell>
        <Exploding />
      </Shell>,
    )

    // One broken panel is a broken panel: the header and navigation are siblings of
    // the failure and have nothing to do with it.
    expect(screen.getByRole('heading', { name: 'SASBO' })).toBeInTheDocument()
    expect(screen.getByRole('navigation', { name: 'Main' })).toBeInTheDocument()

    // The user is TOLD, rather than left looking at a gap.
    expect(screen.getByRole('alert')).toBeInTheDocument()

    // And there is an affordance that gets them moving again. A boundary with no exit
    // is a white page with text on it.
    //
    // 🔑 `getAllByRole` SINCE `06.1b`, AND THE PLURAL IS THE POINT. The designed fallback
    // offers TWO recoveries because there are two failures behind this screen and one
    // button cannot fix both -- see the "stale chunk" test below.
    expect(
      screen.getAllByRole('button', { name: /try again|reload|go back|home/i }).length,
    ).toBeGreaterThan(0)
  })

  it('does not let the throw escape the render', () => {
    // The inverse of the old pin, which asserted this DID throw. Kept as the direct
    // regression: if the boundary is removed or moved above the shell, this reddens.
    expect(() =>
      render(
        <Shell>
          <Exploding />
        </Shell>,
      ),
    ).not.toThrow()
  })

  it('contains the failure to the panel, leaving the rest of the document intact', () => {
    const { container } = render(
      <Shell>
        <Exploding />
      </Shell>,
    )

    // Before the fix this was empty -- React unmounted everything it had committed.
    expect(container.innerHTML).not.toBe('')
    expect(document.body.textContent.trim()).not.toBe('')
  })

  /**
   * ✅ THE DESIGNED FALLBACK. `06.1b` closed A11's remaining half, and these are its rules.
   *
   * 🗣️ THE AUTOMATION ARC'S CONSTRAINT, VERBATIM AND UNPROMPTED (2026-09-09): *"keep the
   * sentence naming what failed and the one action that recovers it, and never a stack trace
   * or a chunk name."* A message is not a deliverable, so it is asserted here.
   */
  describe('the designed fallback', () => {
    /** What a stale deploy actually throws: the browser's own wording, hash and all. */
    const StaleChunk = () => {
      throw new Error(
        'Failed to fetch dynamically imported module: /assets/Members-B7fQ2x.js',
      )
    }

    it('never renders the chunk name or the stack, however loud the error is', () => {
      // 🔴 THE MOST LIKELY THING TO REACH THIS BOUNDARY IS NOT A BUG IN A COMPONENT. `03.1`
      // made every route lazy, so the common case is a user on a stale index.html asking for
      // a hash a deploy replaced. Rendering the error's own message would show a member of a
      // trade union a build artefact's content hash -- and the action that fixes their
      // situation is not in that sentence anywhere.
      render(
        <Shell>
          <StaleChunk />
        </Shell>,
      )

      const panel = screen.getByTestId('route-error')
      expect(panel.textContent).not.toMatch(/Members-B7fQ2x/)
      expect(panel.textContent).not.toMatch(/\.js\b/)
      expect(panel.textContent).not.toMatch(/dynamically imported module/i)
      expect(panel.textContent).not.toMatch(/\bat\s+\w+\s+\(/)
    })

    it('offers BOTH recoveries, because one of them cannot fix a stale chunk', () => {
      // "Try again" re-renders the subtree -- right for a transient failure, and it keeps
      // what the user has typed elsewhere. On a stale chunk it simply throws again, which
      // is a loop with no exit if it is the only button. "Reload" fetches a fresh index and
      // is the ONLY thing that fixes that case.
      render(
        <Shell>
          <StaleChunk />
        </Shell>,
      )

      expect(screen.getByRole('button', { name: 'Try again' })).toBeInTheDocument()
      expect(screen.getByRole('button', { name: /reload/i })).toBeInTheDocument()
    })

    it('promises nothing about what the user typed', () => {
      // The plain fallback used to say "Nothing you entered has been sent". A boundary
      // cannot know that: a render that threw AFTER a successful save is exactly as likely
      // as one that threw before it, and a reassurance that is sometimes false is worse
      // than none.
      render(
        <Shell>
          <Exploding />
        </Shell>,
      )

      expect(screen.getByTestId('route-error').textContent).not.toMatch(
        /nothing you entered|nothing was (sent|saved)/i,
      )
    })

    it('reports through the sink, and the sink does not carry the message', () => {
      // EH-D10. The boundary used to report nothing at all, so the one failure class that
      // blanks a whole screen was the only one leaving no trace. It says something now --
      // and a render error's message interpolates the offending VALUE, which on these
      // screens is a member, so what it says is the redacted line.
      render(
        <Shell>
          <StaleChunk />
        </Shell>,
      )

      const sunk = console.error.mock.calls.filter(([tag]) => tag === '[sasbo]')
      expect(sunk.length).toBeGreaterThan(0)

      const [, line] = sunk.at(-1)
      expect(line).toMatchObject({ context: 'ErrorBoundary' })
      expect(JSON.stringify(line)).not.toMatch(/Members-B7fQ2x/)
    })
  })

  /**
   * 🔴 PROVE THE BOUNDARY BY REMOVING IT. Every assertion above passes if the boundary
   * works AND if `Exploding` quietly stopped throwing. The second is the failure mode
   * that would make this whole file decorative, so it is ruled out directly.
   */
  it('and the component under test really does throw without one', () => {
    expect(() =>
      render(
        <MemoryRouter>
          <ThemeProvider>
            <Exploding />
          </ThemeProvider>
        </MemoryRouter>,
      ),
    ).toThrow('a component failed while rendering')
  })
})

describe('the application declares a boundary, and it is reachable from every route', () => {
  it('declares an ErrorBoundary in application source', () => {
    // The static half, scanned rather than asserted from memory -- the mirror of the
    // scan that used to assert the opposite.
    //
    // Scoped to APPLICATION source on purpose. A first draft globbed
    // `../**/*.{js,jsx}` and matched `knownDefects.js` -- this suite's own register,
    // which contains the string "ErrorBoundary" inside the A11 description. The guard
    // reported the defect as FIXED because of the text describing the defect. That is
    // the self-matching trap: a scan whose fixture and whose subject read from one
    // source can never fail.
    const sources = import.meta.glob(
      [
        '../App.jsx',
        '../main.jsx',
        '../components/**/*.{js,jsx}',
        '../pages/**/*.{js,jsx}',
        '../layouts/**/*.{js,jsx}',
        '../context/**/*.{js,jsx}',
        '../hooks/**/*.{js,jsx}',
        '../services/**/*.{js,jsx}',
        '../config/**/*.{js,jsx}',
        '../utils/**/*.{js,jsx}',
      ],
      { query: '?raw', import: 'default', eager: true },
    )

    const appSources = Object.entries(sources).filter(([path]) => !path.includes('.test.'))

    expect(
      appSources.length,
      'The boundary scan read no application source at all -- a broken walker, not an ' +
        'application with no files.',
    ).toBeGreaterThan(40)

    const declaring = appSources.filter(([, src]) =>
      /getDerivedStateFromError|componentDidCatch/.test(src),
    )

    expect(
      declaring.map(([p]) => p),
      'The application no longer declares an error boundary anywhere. A11 has regressed ' +
        'and every route is a white page away from an uncaught render error.',
    ).not.toEqual([])
  })

  it('and both layouts mount one around their outlet, so no route is exposed', () => {
    // Placement, not merely existence. A boundary that exists in a file nothing renders
    // is the same as no boundary -- and the layouts are what every route renders through.
    const layouts = import.meta.glob(
      ['../components/Main.jsx', '../layouts/AuthLayout.jsx'],
      { query: '?raw', import: 'default', eager: true },
    )

    const entries = Object.entries(layouts)
    expect(entries.length, 'both layouts must be readable').toBe(2)

    for (const [path, src] of entries) {
      expect(src, `${path} renders an <Outlet> with no <ErrorBoundary> around it`)
        .toMatch(BOUNDARY_TAG)
      expect(src, `${path} renders an <Outlet> with no <Suspense> around it`)
        .toMatch(/<Suspense/)
    }
  })

  /*
   * 🔴 THE GUARD ABOVE IS A SOURCE SCAN, AND A SOURCE SCAN BREAKS ON SPELLING -- BLIND AND LOUD. It read
   * `/<ErrorBoundary>/`, so the moment R1-F1 gave the boundary a `key` the layouts had one and the guard said they
   * did not. The predicate is aimed at the FACT now -- the element is opened, with or without props -- and these two
   * cases are its canary: the shape it must catch, and the shape it must not accept.
   */
  it('the guard above matches a boundary WITH props, and still refuses a layout that has none', () => {
    expect('<ErrorBoundary key={pathname}>').toMatch(BOUNDARY_TAG)
    expect('<ErrorBoundary>').toMatch(BOUNDARY_TAG)
    expect('<Suspense><Outlet /></Suspense>').not.toMatch(BOUNDARY_TAG)
    // A mention in an import or a comment is not a boundary AROUND anything.
    expect("import ErrorBoundary from './common/ErrorBoundary'").not.toMatch(BOUNDARY_TAG)
  })
})
