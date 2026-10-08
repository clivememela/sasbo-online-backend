// @vitest-environment jsdom -- it asserts the DOM harness itself -- window, document, cleanup
import { describe, expect, it } from 'vitest'
import { screen } from '@testing-library/react'
import { http, HttpResponse } from 'msw'

import { renderWithProviders } from './renderWithProviders'
import { server } from './msw/server'
import { assertNonVacuousScan } from './assertNonVacuousScan'

/**
 * The harness proving things about ITSELF, before anything trusts it.
 *
 * Everything else in this suite reports green when it passes. So does a runner
 * that is silently not running, a network policy that is silently allowing
 * real requests, and a provider wrapper that is silently supplying nothing.
 * These are the assertions that would notice.
 */
describe('the test harness', () => {
  it('runs jsdom, with a document to render into', () => {
    expect(typeof window).toBe('object')
    expect(document.body).toBeTruthy()
  })

  it('has the jest-dom matchers registered', () => {
    const el = document.createElement('div')
    el.textContent = 'visible'
    document.body.appendChild(el)
    expect(el).toBeInTheDocument()
    expect(el).toHaveTextContent('visible')
  })

  it('has the axe matcher registered', () => {
    expect(expect.getState()).toBeTruthy()
    expect(typeof expect(document.body).toHaveNoViolations).toBe('function')
  })

  it('renders through the provider wrapper without a router error', () => {
    const Probe = () => <p>mounted under the providers</p>
    renderWithProviders(<Probe />)
    expect(screen.getByText('mounted under the providers')).toBeInTheDocument()
  })

  describe('the network policy', () => {
    it('intercepts a request a handler was declared for', async () => {
      server.use(http.get('/api/probe', () => HttpResponse.json({ intercepted: true })))
      const body = await fetch('/api/probe').then((r) => r.json())
      expect(body).toEqual({ intercepted: true })
    })

    it('ERRORS on a request no handler was declared for, rather than warning', async () => {
      // This is the assertion that makes every other test in the suite
      // trustworthy. If an unhandled request merely warned, a service test
      // would quietly reach a real backend and pass for as long as somebody's
      // bootRun happened to be up.
      await expect(fetch('/api/nobody-declared-this')).rejects.toThrow()
    })
  })

  describe('the non-vacuity canary', () => {
    it('passes when the scan found more than its floor', () => {
      expect(() => assertNonVacuousScan([1, 2, 3, 4, 5], 3, 'a probe')).not.toThrow()
    })

    it('FAILS when a discovery came back suspiciously empty', () => {
      // The canary's own reddening change, run inline: a discovery that found
      // nothing must not be able to report success.
      expect(() => assertNonVacuousScan([], 3, 'a broken probe')).toThrow(
        /NON-VACUITY CANARY/,
      )
    })
  })

  describe('state does not leak between tests', () => {
    // These two run in a SHUFFLED suite, so neither may assume it goes first.
    // Each writes, and each asserts it did not inherit the other's write.
    it('leaves no localStorage behind (a)', () => {
      expect(localStorage.getItem('leak-probe')).toBeNull()
      localStorage.setItem('leak-probe', 'a')
    })

    it('leaves no localStorage behind (b)', () => {
      expect(localStorage.getItem('leak-probe')).toBeNull()
      localStorage.setItem('leak-probe', 'b')
    })
  })

  /**
   * 🔴 THE SUITE'S ENVIRONMENT IS PINNED, AND THIS IS WHAT NOTICES IF IT STOPS BEING.
   *
   * `vite.config.js` sets `test.env` to two literals so that no test result depends on
   * the machine it runs on, on a file that ought not to be committed, or on a live key.
   * That pin is one line of configuration, it looks inert, and removing it breaks the
   * suite in a way that reads as a hundred unrelated failures.
   *
   * It is not hypothetical. Upstream `d87a854` (2026-09-22) replaced those two literals
   * with `process.env.VITE_API_BASE_URL` and `process.env.VITE_GOOGLE_API_KEY` -- the
   * variables are unset on a runner, so every service test built its URLs from
   * `undefined`, and **107 of the 263 cases in `services.errorContract.test.js` failed**
   * on that repository's own run (35762991802). Nothing in the message said "environment".
   *
   * 🔑 SO THIS ASSERTS THE FACT, NOT THE FILE. A scan of `vite.config.js` would go green
   * on a rename or a different spelling of the same mistake; asking the running suite what
   * `import.meta.env` actually holds cannot. Both values are named exactly, so an ambient
   * value -- `undefined`, a developer's `.env.local`, or a deployed URL -- fails here
   * first, by name, before it can fail somewhere that sounds like a service bug.
   */
  describe('the environment this suite runs in', () => {
    it('is the pinned fake origin, never the machine or the tracked .env', () => {
      expect(import.meta.env.VITE_API_BASE_URL).toBe('http://api.test.invalid')
    })

    it('carries no Maps key, so nothing here can reach Google or bill anyone', () => {
      expect(import.meta.env.VITE_GOOGLE_API_KEY).toBe('')
    })
  })
})
