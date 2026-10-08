// @vitest-environment jsdom -- renders a provider and asserts on documentElement's class list
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { act, render, renderHook, screen } from '@testing-library/react'

import indexHtml from '../../index.html?raw'
import { ThemeProvider } from './ThemeContext'
import { useTheme } from './useTheme'

/**
 * `ThemeContext` is the ONLY `createContext` in this codebase, and it wraps the
 * whole application. A break here is total: every page loses its theme at once,
 * and `useTheme` throwing takes the entire tree with it -- there is no error
 * boundary anywhere in the client to catch it (retired row A11 -- a boundary now sits
 * beside the outlet in both layouts, so the blast radius is one panel, not the app).
 *
 * It is also the only piece of genuinely shared state, which makes it the one
 * place a shuffled suite can leak: `darkMode` is persisted to `localStorage`
 * and applied as a class on `document.documentElement`. Both are reset in
 * `setup.js` after every test, and the last case here asserts that reset
 * actually happens rather than trusting it.
 */
const wrapper = ({ children }) => <ThemeProvider>{children}</ThemeProvider>

const renderTheme = () => renderHook(() => useTheme(), { wrapper })

describe('ThemeProvider', () => {
  it('starts light when nothing has been stored', () => {
    const { result } = renderTheme()
    expect(result.current.darkMode).toBe(false)
    expect(document.documentElement).not.toHaveClass('dark')
  })

  it('🗣️ starts light even where the operating system prefers dark -- light by default (the 5 Oct Show & Tell)', () => {
    // A provider that followed the OS would read this and start dark for every administrator on a dark Windows theme.
    vi.spyOn(window, 'matchMedia').mockImplementation((query) => ({
      matches: /prefers-color-scheme:\s*dark/.test(query),
      media: query,
      onchange: null,
      addListener: () => {},
      removeListener: () => {},
      addEventListener: () => {},
      removeEventListener: () => {},
      dispatchEvent: () => false,
    }))
    const { result } = renderTheme()
    expect(result.current.darkMode).toBe(false)
    expect(document.documentElement).not.toHaveClass('dark')
  })

  it('restores dark mode from localStorage', () => {
    localStorage.setItem('darkMode', 'true')
    const { result } = renderTheme()
    expect(
      result.current.darkMode,
      'A member who chose dark mode gets light mode back on their next visit. The ' +
        'preference is being written and not read.',
    ).toBe(true)
    expect(document.documentElement).toHaveClass('dark')
  })

  it('treats any stored value other than "true" as light', () => {
    // The comparison is `saved === 'true'` against a string. Worth pinning:
    // `localStorage.setItem('darkMode', false)` stores the STRING "false", and
    // a truthiness check would read that as dark.
    for (const stored of ['false', '', '0', 'TRUE', 'yes']) {
      localStorage.setItem('darkMode', stored)
      const { result, unmount } = renderTheme()
      expect(result.current.darkMode, `stored value ${JSON.stringify(stored)}`).toBe(false)
      unmount()
    }
  })

  it('toggling flips the mode, the stored value and the document class together', () => {
    const { result } = renderTheme()

    act(() => result.current.toggleDarkMode())

    expect(result.current.darkMode).toBe(true)
    expect(document.documentElement).toHaveClass('dark')
    expect(
      localStorage.getItem('darkMode'),
      'The class was applied but the preference was not stored, so the choice is lost on ' +
        'reload -- which reads to the member as the toggle not working at all.',
    ).toBe('true')

    act(() => result.current.toggleDarkMode())

    expect(result.current.darkMode).toBe(false)
    expect(document.documentElement).not.toHaveClass('dark')
    expect(localStorage.getItem('darkMode')).toBe('false')
  })

  it('setDarkMode sets it directly, without toggling', () => {
    const { result } = renderTheme()
    act(() => result.current.setDarkMode(true))
    expect(result.current.darkMode).toBe(true)
    act(() => result.current.setDarkMode(true))
    expect(result.current.darkMode, 'setting true twice toggled it off').toBe(true)
  })

  it('renders its children', () => {
    render(
      <ThemeProvider>
        <p>the application</p>
      </ThemeProvider>,
    )
    expect(screen.getByText('the application')).toBeInTheDocument()
  })

  /**
   * R-PERF-F. THE CONTEXT VALUE KEEPS ITS IDENTITY WHILE THE THEME DOES NOT CHANGE.
   *
   * 🔴 WHY THIS IS A TEST AND NOT A CODE REVIEW NOTE. The defect is invisible: with
   * `value={{ ... }}` the application renders identically, passes every other test in
   * this file, and merely re-renders every consumer in the tree on every render of a
   * provider that wraps the whole app. There is no symptom to assert on except the
   * identity itself.
   *
   * 🔑 IT IS TWO ASSERTIONS BECAUSE ONE OF THEM IS SATISFIED BY A CONSTANT. "The value
   * is the same object as last time" is trivially true of a provider that never updates
   * at all -- a memo with an empty dependency array passes it while breaking the theme
   * outright. So the second half asserts the identity DOES change when `darkMode` does.
   * Stability and freshness are different claims and a memo can get either one wrong.
   */
  it('hands consumers the same value until the theme actually changes', () => {
    const { result, rerender } = renderTheme()
    const first = result.current

    rerender()

    expect(
      result.current,
      'The context value is a new object after a re-render that changed nothing. Every ' +
        'consumer of this context -- which is every screen, row and menu in the ' +
        'application -- re-renders on each render of a provider that wraps all of them. ' +
        'The value needs `useMemo`, and the callbacks in it need to be stable too.',
    ).toBe(first)

    act(() => result.current.toggleDarkMode())

    expect(
      result.current,
      'The context value did NOT change identity when the theme changed, so consumers ' +
        'were never told. A memo with the wrong dependencies is worse than no memo: the ' +
        'theme switches in state and nothing re-renders to show it.',
    ).not.toBe(first)
    expect(result.current.darkMode).toBe(true)
  })
})

/**
 * 🔑 INDEX.HTML'S PRE-PAINT SCRIPT GIVES THE SAME ANSWER, BEFORE REACT RUNS -- run here as the browser runs it, against
 * the document's own `color-scheme` meta, so the class and the scheme the browser paints its canvas in are asserted
 * together for every stored value. A script that drifted from the provider would flash one theme and then the other.
 */
describe('the pre-paint script in index.html agrees: light unless this browser chose dark', () => {
  const META = /<meta name="color-scheme" content="([^"]*)" \/>/.exec(indexHtml)
  const SCRIPT = /<script>([\s\S]*?)<\/script>/.exec(indexHtml)

  const prePaint = () => {
    document.head.innerHTML = `<meta name="color-scheme" content="${META[1]}">`
    // The page's own inline script, verbatim -- not a copy of what it is believed to say.
    new Function(SCRIPT[1])()
    return document.querySelector('meta[name="color-scheme"]').content
  }

  beforeEach(() => {
    document.documentElement.classList.remove('dark')
  })
  afterEach(() => {
    document.head.innerHTML = ''
  })

  it('found the meta and the script it runs against', () => {
    expect(META, 'index.html has no <meta name="color-scheme">').not.toBeNull()
    expect(SCRIPT, 'index.html has no inline pre-paint script').not.toBeNull()
    expect(SCRIPT[1]).toContain("localStorage.getItem('darkMode')")
  })

  it('nothing stored: light -- the class absent, and the canvas light before any CSS arrives', () => {
    expect(META[1], 'a "light dark" meta lets a dark operating system paint a dark canvas under a light page').toBe('light')
    expect(prePaint()).toBe('light')
    expect(document.documentElement).not.toHaveClass('dark')
  })

  it('"true" stored: dark -- the class set, and the canvas dark from the first frame', () => {
    localStorage.setItem('darkMode', 'true')
    expect(prePaint()).toBe('dark')
    expect(document.documentElement).toHaveClass('dark')
  })

  it('any other stored value is light, as the provider reads it', () => {
    for (const stored of ['false', '', 'TRUE']) {
      localStorage.setItem('darkMode', stored)
      expect(prePaint(), `stored value ${JSON.stringify(stored)}`).toBe('light')
      expect(document.documentElement).not.toHaveClass('dark')
    }
  })

  it('storage that throws is light, and the script does not throw -- it runs above every boundary', () => {
    vi.spyOn(Storage.prototype, 'getItem').mockImplementation(() => {
      throw new DOMException('The operation is insecure.', 'SecurityError')
    })
    expect(() => prePaint()).not.toThrow()
    expect(document.querySelector('meta[name="color-scheme"]').content).toBe('light')
    expect(document.documentElement).not.toHaveClass('dark')
  })
})

describe('useTheme outside a provider', () => {
  it('throws a message that names the mistake', () => {
    // `createContext()` has no default value, so the context reads `undefined`
    // outside a provider and the guard fires. Asserted because the guard is
    // one `=== undefined` away from being dead: give createContext a default
    // object and this throw silently stops happening, and every component
    // outside the provider gets a theme that does nothing.
    expect(() => renderHook(() => useTheme())).toThrow(/must be used within a ThemeProvider/)
  })
})

describe('the suite cleans up after this provider', () => {
  // These two run in a SHUFFLED order and neither may assume it goes first.
  // ThemeProvider writes to localStorage and to documentElement, which are the
  // only two pieces of global state in the client -- so if the afterEach in
  // setup.js ever stops resetting them, this is what says so.
  it('leaves no dark class behind (a)', () => {
    expect(document.documentElement).not.toHaveClass('dark')
    const { result } = renderTheme()
    act(() => result.current.setDarkMode(true))
  })

  it('leaves no dark class behind (b)', () => {
    expect(document.documentElement).not.toHaveClass('dark')
    expect(localStorage.getItem('darkMode')).toBeNull()
  })
})
