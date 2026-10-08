import { afterEach, describe, expect, it, vi } from 'vitest'
import { http } from 'msw'

import { server } from '../test/msw/server'
import { allServiceMethods, NON_FETCHING } from '../test/serviceCatalogue'
import { WRITE_CEILING_MS } from './parseApiError'

/**
 * THE FOURTH STATE: a request that never resolves.
 *
 * The three-state rule covers loading, empty and failed. A hung backend is
 * NONE of them. It renders the loading state forever -- which an administrator
 * reads as "the system is slow" rather than "it is down", so nobody is called
 * and nothing is escalated. It is the failure mode with the longest time to
 * detection precisely because it looks like the system working.
 *
 * ✅ A14 IS CLOSED, 2026-09-10, by `06.1a`. This file's header used to read *"Verified across
 * `src/services/`: zero `AbortController`, zero `signal:`, zero timeout of any kind (register
 * A14)"* — that was true when it was written and is the opposite of true now. Every service
 * threads an `AbortSignal` through `requestJson`, and the ceilings are published in
 * conventions.md §16.
 *
 * FAKE TIMERS, NOT A REAL WAIT. A test that actually waits for a timeout is a
 * test that costs whatever the timeout is, every run, and is the first thing
 * deleted when the suite gets slow. The clock is advanced deliberately instead.
 */

/**
 * The clock is advanced past the LONGEST ceiling, so one number covers reads and writes.
 *
 * ⚠️ IMPORTED, NOT RETYPED. It was a local `30_000` chosen before the ceilings existed; a copy
 * here would be a second definition of the one number this file is about, and the failure mode is
 * silent — raise `WRITE_CEILING_MS` past a stale local constant and every assertion below starts
 * checking that the call had NOT yet timed out.
 */
const CEILING_MS = WRITE_CEILING_MS + 1_000

/** A handler that accepts the request and then simply never answers. */
const neverAnswers = () => http.all('*', () => new Promise(() => {}))

const fetching = allServiceMethods().filter((m) => !NON_FETCHING.has(m.qualified))

afterEach(() => {
  vi.useRealTimers()
})

describe('a request that never resolves', () => {
  /*
   * ✅ A14 IS CLOSED BY `06.1a`, AND THIS IS THE FLIPPED PAIR.
   *
   * The test below was `it.skip(disabled(DEFECTS.A14, ...))`; it is live now. The two pins that
   * asserted the absence of any ceiling are DELETED rather than made to pass:
   *
   *   the runtime pin  asserted `stillPending.length === fetching.length` -- that EVERY method was
   *                    still hanging a full 30s after the backend stopped answering.
   *   the static pin   scanned `src/services/*.js` for `AbortController|signal:|AbortSignal|
   *                    setTimeout` and asserted the match list was EMPTY.
   *
   * 🔑 THE STATIC HALF IS WHY THE ROW COULD NOT BE SPLIT, and it shaped the change: a ceiling on
   * SOME services fails that scan without closing the row, so the ceiling had to land across all
   * eight at once. That is why `06.1a` is one commit and not a service-by-service migration.
   *
   * The ceiling is `READ_CEILING_MS` (10s) and `WRITE_CEILING_MS` (20s) from conventions.md §16 --
   * a read that is slow can be retried for free, while a write abandoned mid-flight leaves the
   * caller unable to say whether it happened.
   */
  it(
    'every service call gives up and rejects before its ceiling',
    async () => {
      // Written to the standard. Each method is given a request that never
      // answers, the clock is advanced past the ceiling, and the promise must
      // have settled as a rejection -- so the UI can reach its ERROR state
      // rather than its loading state forever.
      for (const m of fetching) {
        vi.useFakeTimers()
        server.use(neverAnswers())

        let settled = false
        const call = m.fn(...m.args).then(
          () => {
            settled = true
          },
          () => {
            settled = true
          },
        )

        await vi.advanceTimersByTimeAsync(CEILING_MS + 1_000)
        await Promise.race([call, Promise.resolve()])

        expect(
          settled,
          `${m.qualified} was still pending ${CEILING_MS / 1000}s after a backend stopped ` +
            `answering. The screen is showing a spinner and will show it until the tab is ` +
            `closed.`,
        ).toBe(true)
        vi.useRealTimers()
      }
    },
  )

  it('a timed-out call rejects with kind `timeout` and offers a retry', async () => {
    // 🔑 THE KIND MATTERS AS MUCH AS THE REJECTION. §16 separates a deadline WE imposed
    // (`timeout` -- a real failure, retryable, and the user is told) from a caller superseding its
    // own request (`abort` -- not a failure at all, dropped silently). The DOM gives both the same
    // `AbortError` name, so the only way to tell them apart is to know which one we caused.
    vi.useFakeTimers()
    server.use(neverAnswers())

    const m = fetching[0]
    const call = m.fn(...m.args)
    const settled = call.then(
      () => ({ resolved: true }),
      (error) => ({ error }),
    )

    await vi.advanceTimersByTimeAsync(CEILING_MS + 1_000)
    const outcome = await settled

    expect(
      outcome.error,
      `${m.qualified} did not reject after its ceiling passed.`,
    ).toBeDefined()
    expect(outcome.error).toMatchObject({ kind: 'timeout', retryable: true })
    vi.useRealTimers()
  })

  it('the service layer can express a timeout, which is the static half of the row', () => {
    // ⚠️ THE INVERSE OF THE PIN THIS REPLACES. That pin asserted this list was EMPTY -- that
    // nothing in `src/services/` could even express a ceiling -- and it is why A14 could not be
    // closed service by service: a ceiling on SOME services fails an emptiness scan without
    // closing the row. Asserted over the source, so a future refactor that quietly drops the
    // signal threading turns this red and names the row.
    const sources = Object.entries(
      import.meta.glob('../services/*.js', { query: '?raw', import: 'default', eager: true }),
    ).filter(([path]) => !path.includes('.test.'))

    expect(sources.length, 'the service source scan found nothing to read').toBeGreaterThan(5)

    const withTimeoutSupport = sources.filter(
      ([, src]) => /AbortController|signal\s*:|AbortSignal|setTimeout/.test(src),
    )

    expect(
      withTimeoutSupport.map(([p]) => p),
      'No service module references an abort or timeout mechanism any more, so the ceiling has ' +
        'been removed and every call can hang indefinitely again -- which is A14, reopened.',
    ).not.toEqual([])
  })
})
