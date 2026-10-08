// @vitest-environment jsdom -- the shell is a rendered thing; none of this is visible in source
import { beforeEach, describe, expect, it } from 'vitest'
import { screen, within } from '@testing-library/react'

import App from '../App'
import * as SHELL_IDS from './shellIds'
import { renderWithProviders } from '../test/renderWithProviders'
import { server } from '../test/msw/server'
import { smokeHandlers } from '../test/msw/smokeLayer'
import { setupUser } from '../test/user'

/**
 * The application shell — header, drawer, and the controls that open them. `04.1`.
 *
 * 🔴 WHAT THIS COVERS THAT NOTHING ELSE DID. The route smoke mounts every route and
 * asserts it did not throw; the a11y pass scans pages. Neither looks at the shell's own
 * controls, and all three of them were broken in the same way: an icon-only raw
 * `<button>` announces as "button" and nothing more. The one control that opens the
 * navigation on a phone, and the one that closes it, were both unusable by anyone not
 * looking at the screen — and both passed every check this repository had.
 *
 * 🔑 AND THE MERGE. `AuthLayout` carried a SECOND theme button: same job, hand-rolled,
 * so every accessibility fix had two places to land and only ever got one. The last two
 * cases here are what stops a third appearing.
 */

const appRoute = (path = '/home') => renderWithProviders(<App />, { initialEntries: [path] })

describe('the shell — every control says what it is', () => {
  beforeEach(() => {
    server.use(...smokeHandlers)
  })

  it('the menu button is named, and points at a drawer that exists', async () => {
    appRoute()

    const menu = await screen.findByRole('button', { name: /open navigation menu/i })

    // 🔴 `aria-controls` NAMING A MISSING ELEMENT FAILS SILENTLY — the attribute is
    // simply ignored and the relationship is gone, with nothing to see. Resolving it is
    // the only way to know the id was not a typo, which is why `SIDEBAR_ID` is exported
    // rather than written twice.
    const controls = menu.getAttribute('aria-controls')
    expect(controls, 'The menu button controls nothing.').toBeTruthy()
    expect(
      document.getElementById(controls),
      `aria-controls="${controls}" names an element that is not in the document.`,
    ).toBeInTheDocument()

    expect(menu).toHaveAttribute('aria-expanded', 'false')
  })

  it('opening the drawer reports itself, and the close button is named too', async () => {
    const user = setupUser()
    appRoute()

    const menu = await screen.findByRole('button', { name: /open navigation menu/i })
    await user.click(menu)

    expect(
      menu,
      'The drawer opened and the button still says it is collapsed, so a screen reader ' +
        'announces the opposite of what happened.',
    ).toHaveAttribute('aria-expanded', 'true')

    const close = screen.getByRole('button', { name: /close navigation menu/i })
    await user.click(close)

    expect(menu).toHaveAttribute('aria-expanded', 'false')
  })

  it('the account placeholder is not a control, because there is no account', async () => {
    appRoute()
    await screen.findByRole('button', { name: /open navigation menu/i })

    // Authentication is DEFERRED (R-SEC-C). This was a <button> with no handler, no name
    // and no menu — a tab stop that did nothing. A control that does nothing is worse
    // than no control: it is a promise.
    expect(
      screen.queryByRole('button', { name: /^CM$/ }),
      'The account placeholder is a button again. It has nothing to open.',
    ).not.toBeInTheDocument()
  })
})

describe('the shell — one theme control, not two', () => {
  beforeEach(() => {
    server.use(...smokeHandlers)
  })

  it('an application route offers exactly one, and it is a real button', async () => {
    appRoute()

    // N7: this was a `<div onClick>`, so the only way to change theme was a mouse — no
    // tab stop, no Enter, no Space, nothing announced. Asking for a BUTTON by role is
    // what says so. Its name is card.html's: "Appearance · Light", in the rail's foot.
    const toggles = await screen.findAllByRole('button', { name: /appearance · (light|dark)/i })
    expect(toggles).toHaveLength(1)
    expect(toggles[0]).toHaveAttribute('aria-pressed', 'false')
  })

  it('an auth route offers exactly one too — the same component', async () => {
    appRoute('/login')

    // 🔴 THE MERGE, AND THE ASSERTION IS `1` RATHER THAN `>= 1` ON PURPOSE. Before
    // `04.1` this route had its own hand-rolled copy; a subset check would have been
    // green throughout and would stay green if a third were added tomorrow.
    const toggles = await screen.findAllByRole('button', { name: /appearance · (light|dark)/i })
    expect(
      toggles,
      'The auth screens have their own theme button again. Two implementations of one ' +
        'control is one place for the next accessibility fix to be forgotten.',
    ).toHaveLength(1)
  })

  it('changes the theme from the keyboard, and says so', async () => {
    const user = setupUser()
    appRoute('/login')

    const toggle = await screen.findByRole('button', { name: /appearance · light/i })

    toggle.focus()
    await user.keyboard('{Enter}')

    expect(document.documentElement).toHaveClass('dark')
    // The name carries the current value, which is how `aria-pressed` and the visible
    // label stay in agreement instead of drifting apart.
    expect(screen.getByRole('button', { name: /appearance · dark/i })).toHaveAttribute(
      'aria-pressed',
      'true',
    )
  })
})

describe('the shell — landmarks and the skip link, `05.3`', () => {
  beforeEach(() => {
    server.use(...smokeHandlers)
  })

  it('offers one banner, one main and a NAMED main navigation', async () => {
    appRoute()
    await screen.findByRole('button', { name: /open navigation menu/i })

    // 🔴 THE ELEMENTS WERE ALL PRESENT BEFORE AND ONE OF THEM WAS NOT A LANDMARK.
    // `<main>` used to be the outer wrapper with `<header>` inside it, and HTML maps
    // `<header>` to `banner` ONLY when it is not a descendant of `<main>`, `<article>`,
    // `<aside>`, `<nav>` or `<section>`. The page therefore rendered a topbar that
    // announced as nothing at all. (The footer band went with card.html's shell on
    // 2026-09-10: the mock has none, and the union's name lives in the rail's wordmark.)
    expect(screen.getAllByRole('banner')).toHaveLength(1)
    expect(screen.getAllByRole('main')).toHaveLength(1)
    expect(screen.queryAllByRole('contentinfo')).toHaveLength(0)

    // 🔑 AND THE NAVIGATION IS NAMED, because there are two. The menu tree and the
    // breadcrumb trail are both `navigation` landmarks, and two unlabelled ones are
    // announced identically — which is worse than having one.
    expect(
      screen.getByRole('navigation', { name: /main/i }),
      'The menu tree is not a named `navigation` landmark. It is the only way to reach ' +
        '25 of 27 routes, and it used to be a bare <div>.',
    ).toBeInTheDocument()
  })

  it('the skip link is the first tab stop and lands on the main region', async () => {
    const user = setupUser()
    appRoute()
    await screen.findByRole('button', { name: /open navigation menu/i })

    const skip = screen.getByRole('link', { name: /skip to main content/i })

    // 🔴 FIRST, OR IT IS DECORATION. The whole point is to get ahead of the navigation
    // tree; a skip link placed after it saves nobody anything.
    await user.tab()
    expect(
      document.activeElement,
      'The first Tab does not reach the skip link, so a keyboard user still walks the ' +
        'entire menu before reaching the page.',
    ).toBe(skip)

    // 🔑 AND IT POINTS AT SOMETHING THAT EXISTS. A fragment naming a missing id fails in
    // complete silence — and the link is invisible until focused, so nobody ever sees it
    // fail.
    const target = skip.getAttribute('href')
    expect(target).toMatch(/^#/)
    const main = document.getElementById(target.slice(1))
    expect(
      main,
      `The skip link points at "${target}", which is not in the document.`,
    ).toBeInTheDocument()
    expect(main.tagName).toBe('MAIN')
    // Focusable, or the jump moves the caret and not the focus ring.
    expect(main).toHaveAttribute('tabindex', '-1')
  })

  it('a navigation section can be expanded without a mouse', async () => {
    const user = setupUser()
    appRoute()
    await screen.findByRole('button', { name: /open navigation menu/i })

    // 🔴 THIS WAS A `<div onClick>` WRAPPING THE LINK. Expanding a section was mouse-only:
    // no tab stop, no role, no key handler, no `aria-expanded` — and a click on the link
    // fired both handlers, navigating and toggling at once. Every sub-route in the menu
    // was unreachable by keyboard.
    const expanders = screen.getAllByRole('button', { name: /^expand /i })
    expect(
      expanders.length,
      'No navigation section exposes a disclosure control, so the sub-items cannot be ' +
        'reached from the keyboard.',
    ).toBeGreaterThan(0)

    const first = expanders[0]
    expect(first).toHaveAttribute('aria-expanded', 'false')

    first.focus()
    await user.keyboard('{Enter}')

    const opened = screen.getByRole('button', { name: /^collapse /i })
    expect(opened).toHaveAttribute('aria-expanded', 'true')
    const controls = opened.getAttribute('aria-controls')
    expect(
      document.getElementById(controls),
      `aria-controls="${controls}" names an element that is not in the document.`,
    ).toBeInTheDocument()
  })
})

describe('the shell — the drawer can be closed without a mouse, `05.4`', () => {
  beforeEach(() => {
    server.use(...smokeHandlers)
  })

  it('Escape closes the drawer and puts focus back on the control that opened it', async () => {
    const user = setupUser()
    appRoute()

    const menu = await screen.findByRole('button', { name: /open navigation menu/i })
    await user.click(menu)
    expect(menu).toHaveAttribute('aria-expanded', 'true')

    // 🔴 THE DRAWER HAD TWO WAYS OUT AND BOTH WERE A MOUSE — the transparent scrim, and
    // the X inside the panel. A keyboard user on a narrow window who opened it was stuck:
    // no Escape handler existed anywhere in the application.
    await user.keyboard('{Escape}')

    expect(
      menu,
      'Escape did not close the drawer. On a narrow window it is the only dismissal a ' +
        'keyboard user has, because the scrim is a click target and nothing else.',
    ).toHaveAttribute('aria-expanded', 'false')

    // 🔑 AND FOCUS COMES BACK, which is the half that fails silently. Left where it was,
    // focus sits inside a <nav> that is now translated off-screen, so the next Tab resumes
    // from somewhere the user cannot see — the same defect `05.3` found in every dialog.
    expect(
      document.activeElement,
      'The drawer closed and focus stayed inside it. The panel is off-screen now, so the ' +
        'next Tab continues from a place with nothing visible in it.',
    ).toBe(menu)
  })

  it('the scrim is a mouse convenience, and is not in the accessibility tree', async () => {
    const user = setupUser()
    appRoute()

    const menu = await screen.findByRole('button', { name: /open navigation menu/i })
    await user.click(menu)

    // The scrim carries an onClick, which is what `click-events-have-key-events` and
    // `no-static-element-interactions` fire on. `aria-hidden` is why they stand down, and
    // it is only honest while Escape above actually works — so the two are asserted
    // together rather than one being trusted to imply the other.
    const scrim = document.querySelector('[aria-hidden="true"].fixed.inset-0')
    expect(
      scrim,
      'The drawer scrim is no longer aria-hidden. It is a transparent full-screen <div> ' +
        'with a click handler, so in the accessibility tree it is a giant unlabelled ' +
        'control — and its keyboard equivalent is Escape, not a focus stop.',
    ).not.toBeNull()
  })
})

describe('the shell — every id relationship RESOLVES, `05.4`', () => {
  beforeEach(() => {
    server.use(...smokeHandlers)
  })

  it('the three shell ids each name an element that is in the document', async () => {
    appRoute()
    await screen.findByRole('button', { name: /open navigation menu/i })

    // 🔑 ASSERTED BY RESOLUTION, NOT BY PRESENCE. Every one of these relationships crosses
    // a file boundary and every one fails in silence when it breaks: `aria-controls` naming
    // a missing element is ignored outright, `getElementById(...)?.focus()` on a renamed id
    // does nothing, and a skip link pointing at nothing moves the caret and not the focus.
    // They live in `shellIds.js` for that reason — three files needed them and two of the
    // exports had started to form an import cycle.
    for (const [name, id] of Object.entries(SHELL_IDS)) {
      expect(
        document.getElementById(id),
        `${name} is "${id}", and there is no element with that id in the document. ` +
          `Whatever points at it is now pointing at nothing, silently.`,
      ).not.toBeNull()
    }
  })
})

describe('the shell — a coarse pointer gets a 44px target, `07.2`', () => {
  beforeEach(() => {
    server.use(...smokeHandlers)
  })

  it('the topbar search and every rail row wear touch-size', async () => {
    appRoute()
    await screen.findByRole('button', { name: /open navigation menu/i })

    // Measured under real touch emulation: the search offered a 38px target -- its whole label, since a tap on the
    // label focuses the field -- and the drawer's rows were 36px apart with a 24px disclosure in each, so a 44px
    // square around one control landed on its neighbour. `touch-size` grows each to 44px on a coarse pointer only.
    const search = screen.getByRole('searchbox')
    expect(search.closest('label').className).toMatch(/\btouch-size\b/)

    // EVERY row the rail offers a finger -- the wordmark, each section's link (whose row carries the class, so the link
    // and its disclosure share one 44px row), the foot's links and the theme switch -- not a sample of them.
    const rail = screen.getByRole('navigation', { name: /main/i })
    const rows = [...within(rail).getAllByRole('link'), within(rail).getByRole('button', { name: /^appearance/i })]
    expect(rows.length, 'the rail offered no rows to check -- the query, not the rail, is broken').toBeGreaterThan(5)
    for (const row of rows) {
      const wearer = row.closest('.touch-size')
      expect(wearer && rail.contains(wearer), `"${row.textContent.trim()}" has no touch-size row in the rail`).toBe(true)
    }
  })
})

describe('the shell — BR-18, the banner is not the page heading', () => {
  beforeEach(() => {
    server.use(...smokeHandlers)
  })

  it('the application name is present in the rail, and the shell has no heading', async () => {
    appRoute()
    await screen.findByRole('button', { name: /open navigation menu/i })

    const banner = screen.getByRole('banner')
    const rail = screen.getByRole('navigation', { name: /main/i })

    // It still says what the application is — losing the name would be a regression of
    // its own. card.html puts the wordmark in the rail, and the topbar carries the
    // crumbs, the search and the tools; neither contributes an <h1>.
    expect(within(rail).getByText('SASBO Online')).toBeInTheDocument()
    expect(within(rail).getByText(/the finance union/i)).toBeInTheDocument()
    expect(
      within(banner).queryByRole('heading'),
      'The shell contributes a heading again. It is the same string on every route, so ' +
        'it tells a screen-reader user nothing about where they are — and it gives every ' +
        'page that uses PageTitle a second <h1>.',
    ).not.toBeInTheDocument()
    expect(within(rail).queryByRole('heading')).not.toBeInTheDocument()
  })
})
