import { afterEach, describe, expect, it, vi } from 'vitest'

import { READ_CEILING_MS, requestJson, requestJsonWrite, requestOptionalJson, WRITE_CEILING_MS } from './parseApiError'

/**
 * 🔴 R2-F4 (P9's independent review): THE CEILING STOPPED AT THE HEADERS.
 *
 * `requestJson` cleared its deadline in `finally` as soon as `send()` resolved -- which is when the RESPONSE HEADERS
 * arrive, not when the body has been read. `response.json()`, `apiErrorFrom`'s own `json()` and
 * `requestOptionalJson`'s `text()` then ran with no timer at all. A proxy that sends headers and stalls (a streaming
 * gateway, a half-dead upstream) left the loading state up forever, and `DataState` promising *"It will stop trying
 * shortly"* was simply untrue. That is A14's own failure mode, on the row the register calls closed.
 *
 * FAKE TIMERS, NOT A REAL WAIT -- the same rule `services.timeout.test.js` states: a test that really waits ten
 * seconds is the first one deleted when the suite gets slow.
 *
 * The must-pass in each pair is a body that arrives normally, so a helper that rejected everything would not pass.
 */

/** A `Response` whose headers are here and whose body never comes. `signal` aborts it, as a real body read would. */
const stalledBody = (signal, { ok = true, status = 200 } = {}) => ({
  ok,
  status,
  statusText: 'OK',
  json: () =>
    new Promise((_resolve, reject) => {
      signal.addEventListener('abort', () => reject(Object.assign(new Error('aborted'), { name: 'AbortError' })))
    }),
  text: () =>
    new Promise((_resolve, reject) => {
      signal.addEventListener('abort', () => reject(Object.assign(new Error('aborted'), { name: 'AbortError' })))
    }),
})

const answers = (body) => ({ ok: true, status: 200, statusText: 'OK', json: async () => body, text: async () => JSON.stringify(body) })

afterEach(() => {
  vi.useRealTimers()
})

/** Starts the call, runs the clock past `ms`, and returns the settled promise. */
async function pastTheCeiling(start, ms) {
  vi.useFakeTimers()
  const pending = start()
  const settled = pending.then((value) => ({ value }), (error) => ({ error }))
  await vi.advanceTimersByTimeAsync(ms + 1_000)
  return settled
}

describe('the request ceiling covers the BODY, not just the headers (R2-F4)', () => {
  it('a read whose body never arrives rejects as a timeout', async () => {
    const { error } = await pastTheCeiling(() => requestJson((signal) => Promise.resolve(stalledBody(signal))), READ_CEILING_MS)

    expect(error?.kind).toBe('timeout')
    expect(error?.retryable).toBe(true)
  })

  it('a write whose body never arrives rejects as a timeout, at the write ceiling', async () => {
    const { error } = await pastTheCeiling(
      () => requestJsonWrite((signal) => Promise.resolve(stalledBody(signal))),
      WRITE_CEILING_MS,
    )

    expect(error?.kind).toBe('timeout')
  })

  it('an OPTIONAL-body write whose body never arrives rejects too', async () => {
    const { error } = await pastTheCeiling(
      () => requestOptionalJson((signal) => Promise.resolve(stalledBody(signal))),
      WRITE_CEILING_MS,
    )

    expect(error?.kind).toBe('timeout')
  })

  it('a FAILED response whose error body never arrives rejects rather than hanging', async () => {
    const { error } = await pastTheCeiling(
      () => requestJson((signal) => Promise.resolve(stalledBody(signal, { ok: false, status: 500 }))),
      READ_CEILING_MS,
    )

    expect(error?.kind).toBe('timeout')
  })

  it('the must-pass: a body that arrives resolves, and the deadline is cleared', async () => {
    vi.useFakeTimers()
    const value = await requestJson(() => Promise.resolve(answers({ ok: 1 })))

    expect(value).toEqual({ ok: 1 })
    // Nothing is left armed: a stray deadline would fire into whichever test runs next.
    expect(vi.getTimerCount()).toBe(0)
  })

  it('the must-pass: an empty optional body still resolves undefined', async () => {
    vi.useFakeTimers()
    const value = await requestOptionalJson(() => Promise.resolve({ ok: true, status: 200, statusText: 'OK', text: async () => '' }))

    expect(value).toBeUndefined()
    expect(vi.getTimerCount()).toBe(0)
  })
})
