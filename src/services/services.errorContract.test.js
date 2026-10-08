import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { http, HttpResponse } from 'msw'

import { server } from '../test/msw/server'
import { allServiceMethods, NON_FETCHING } from '../test/serviceCatalogue'
import { assertNonVacuousScan, FLOORS } from '../test/assertNonVacuousScan'
import { DEFECTS, disabled, pin } from '../test/knownDefects'

/**
 * THE FOUR-CASE ERROR CONTRACT, APPLIED TO EVERY SERVICE METHOD BY ITERATION.
 *
 * WHY THIS LAYER AND NOT ANOTHER
 * `fetch` DOES NOT REJECT ON 4xx OR 5xx. Skip the `response.ok` check and the
 * failing response goes straight to `.json()` -- and because Spring returns its
 * error envelope AS JSON, `.json()` SUCCEEDS. The component then receives
 *
 *     { timestamp, status: 500, error: "Internal Server Error", message }
 *
 * as though it were data. It is a confident wrong answer one layer BELOW where
 * the three-state rule is looking for it, which is why no page test would find
 * it. This is the most inconsistent layer in either repository and it is the
 * layer every screen sits on.
 *
 * THE FOUR CASES
 *   1. 2xx with a JSON body            -> resolves with the parsed body
 *   2. 4xx/5xx carrying the envelope   -> REJECTS. Never resolves with the
 *                                         envelope as data.
 *   3. a network failure               -> rejects
 *   4. a 2xx with a non-JSON body      -> rejects, not an unhandled parse error
 *
 * WRITTEN AGAINST THE CLASS, NOT THE INSTANCES
 * The contract iterates `allServiceMethods()`. It does not name the services
 * that are wrong today. A tenth service, or a tenth method, is covered the day
 * it is written -- and the breach list below is the only place current
 * behaviour is admitted, every entry citing a register row.
 */

/** Case ①. */
const okJson = (body = [{ id: 1, name: 'Synthetic' }]) =>
  http.all('*', () => HttpResponse.json(body))

/** Case ②. The envelope exactly as `GlobalExceptionHandler` emits it. */
const envelope = (status = 500) =>
  http.all('*', () =>
    HttpResponse.json(
      {
        timestamp: '2026-09-04T10:00:00.000+00:00',
        status,
        error: status === 500 ? 'Internal Server Error' : 'Bad Request',
        message: 'boom',
        validationErrors: { identityNumber: 'must be 13 digits' },
      },
      { status },
    ),
  )

/** Case ③. */
const networkFailure = () => http.all('*', () => HttpResponse.error())

/** Case ④. An HTML proxy or gateway page returned with a 200. */
const nonJsonBody = () =>
  http.all('*', () =>
    HttpResponse.text('<html><body>gateway</body></html>', {
      status: 200,
      headers: { 'Content-Type': 'text/html' },
    }),
  )

/**
 * Methods that send a request and deliberately read NO body -- the DELETEs.
 *
 * Case ④ does not apply to them and asserting it would be wrong: a DELETE that
 * answers 200 with an empty body has succeeded, and a service that rejected
 * because the body was not JSON would be the defect. They get the other three
 * cases plus an assertion that they did issue their request.
 *
 * Declared, but not trusted: `theListIsHonest` below asserts each one really
 * does issue a request, so this cannot become a place to park a method that is
 * failing the contract.
 */
const BODYLESS = new Set([
  'recruiterService.remove',
  'recruitmentChannelService.remove',
  'recruitmentChannelService.removeMember',
  'subscriptionsService.remove',
  // October build review: Discard draft -- DELETE /api/workplaces/visit-drafts/{id}?version= answers 204.
  'workplaceVisitService.discardDraft',
])

/**
 * CURRENT BEHAVIOUR, ADMITTED ONCE, EACH ENTRY CITING A REGISTER ROW.
 *
 * Everything not listed here is asserted to the correct behaviour. Every entry
 * here carries a live pin below, so the day the service is fixed the pin
 * reddens and names the row -- a defect being repaired is not allowed to happen
 * silently.
 *
 * MEASURED 2026-09-04 by executing every method under every case.
 */
const BREACHES = {
  // ✅ `bankService.getBankNames` IS GONE FROM THIS LIST -- A13 is CLOSED by `06.1a`.
  //
  // It used to end in `.catch(() => [])`, so a 500, a network failure and a non-JSON body all
  // resolved with an empty array and eight consumers across seven screens rendered "no banks".
  // It is on `requestJson` now and rejects on all three, which is why its two pins below are
  // gone rather than "repaired": the register row is closed, under BR-12 and the owner's nod.
  // ✅ `jobGradeService.getByBank` IS GONE FROM THIS LIST TOO -- A25 is CLOSED by `06.1b`.
  //
  // It ended in `.catch(() => JOB_GRADE_OPTIONS)`, and that constant had been emptied, so a 500,
  // a network failure and a non-JSON body all resolved `[]` and both callers rendered "No job
  // grades available". The catch is deleted and the two callers render the failure with a retry
  // -- the row's own criterion is "the method rejects AND the caller renders an error", which is
  // why the two halves landed in one commit rather than one of them here. Its three pins below
  // are gone rather than "repaired", and the method now takes CASE 2, 3 and 4 by discovery.
  'bankService.updateBank': { cases: ['nonJson'], defect: DEFECTS.A26 },
}

const withHandler = async (handler, fn) => {
  server.use(handler)
  return fn()
}

const settle = async (m) => {
  try {
    return { resolved: await m.fn(...m.args) }
  } catch (e) {
    return { rejected: e }
  }
}

describe('the service error contract', () => {
  const methods = allServiceMethods()
  const fetching = methods.filter((m) => !NON_FETCHING.has(m.qualified))

  it('discovered every service method, and the scan was not vacuous', () => {
    assertNonVacuousScan(methods, FLOORS.SERVICE_METHODS, 'exported service methods')
    // Recorded so a reader sees what the contract below actually covered.
    expect(methods.length).toBeGreaterThan(fetching.length)
  })

  describe('the non-fetching list is honest', () => {
    it.each([...NON_FETCHING])('%s really does issue no request', async (qualified) => {
      const m = methods.find((x) => x.qualified === qualified)
      expect(m, `${qualified} is on the non-fetching list but does not exist`).toBeDefined()

      const seen = []
      const listener = ({ request }) => seen.push(request.url)
      server.events.on('request:start', listener)
      try {
        await m.fn(...m.args)
      } finally {
        server.events.removeListener('request:start', listener)
      }

      expect(
        seen,
        `${qualified} is excluded from the error contract on the grounds that it makes no ` +
          `request -- and it just made ${seen.length}. The exclusion list is hiding a method ` +
          `that should be under contract.`,
      ).toHaveLength(0)
    })

    it.each([...BODYLESS])('%s really does issue its request', async (qualified) => {
      const m = methods.find((x) => x.qualified === qualified)
      const seen = []
      const listener = ({ request }) => seen.push(request.method)
      server.events.on('request:start', listener)
      try {
        await withHandler(okJson(), () => m.fn(...m.args))
      } finally {
        server.events.removeListener('request:start', listener)
      }
      expect(
        seen,
        `${qualified} is exempted from the JSON-body case on the grounds that it is a ` +
          `body-less request. It issued no request at all, so the exemption is covering ` +
          `something else.`,
      ).not.toHaveLength(0)
    })
  })

  describe.each(fetching.map((m) => [m.qualified, m]))('%s', (qualified, m) => {
    const breach = BREACHES[qualified]
    const breaches = (kase) => breach?.cases.includes(kase)

    it('CASE 1 -- a 2xx with a JSON body resolves', async () => {
      const { resolved, rejected } = await withHandler(okJson(), () => settle(m))
      expect(
        rejected,
        `${qualified} rejected on a perfectly good 2xx response. Every screen sitting on ` +
          `it will render its error state against a healthy backend.`,
      ).toBeUndefined()

      // A body-less DELETE resolves with `undefined`, and that is CORRECT --
      // it read no body to resolve with. Asserting a value here would be
      // asserting the wrong contract for those methods.
      if (!BODYLESS.has(qualified)) {
        expect(
          resolved,
          `${qualified} resolved with nothing at all on a 2xx carrying a JSON body. The ` +
            `caller asked for data, the server sent data, and the service dropped it.`,
        ).toBeDefined()
      }
    })

    const case2 = async () => {
      const { resolved, rejected } = await withHandler(envelope(500), () => settle(m))

      expect(
        rejected,
        `THE CONFIDENT WRONG ANSWER. ${qualified} RESOLVED on a 500. fetch does not reject ` +
          `on 5xx, and Spring's error envelope is valid JSON, so .json() succeeded and the ` +
          `caller has been handed ${JSON.stringify(resolved)?.slice(0, 80)} as though it ` +
          `were data. Nothing downstream can tell this from a real answer.`,
      ).toBeDefined()

      // The second half, and the one that matters: it must not merely reject,
      // it must not have leaked the envelope through as a value.
      expect(
        JSON.stringify(resolved ?? null),
        `${qualified} resolved with the error envelope itself as its value.`,
      ).not.toContain('Internal Server Error')
    }

    const case3 = async () => {
      const { resolved, rejected } = await withHandler(networkFailure(), () => settle(m))
      expect(
        rejected,
        `${qualified} RESOLVED with ${JSON.stringify(resolved)?.slice(0, 60)} when the ` +
          `request never reached a server at all. "The network is down" and "here is your ` +
          `data" are being reported as the same outcome.`,
      ).toBeDefined()
    }

    const case4 = async () => {
      const { resolved, rejected } = await withHandler(nonJsonBody(), () => settle(m))
      expect(
        rejected,
        `${qualified} RESOLVED with ${JSON.stringify(resolved)?.slice(0, 60)} when the ` +
          `server answered 200 with an HTML page -- a proxy error page, typically. The ` +
          `caller cannot tell that from real data.`,
      ).toBeDefined()
    }

    if (breaches('envelope')) {
      it.skip(disabled(breach.defect, `${qualified} must reject on a 4xx/5xx`), case2)
      it(pin(breach.defect, `${qualified} still resolves on a 500`), async () => {
        const { rejected } = await withHandler(envelope(500), () => settle(m))
        expect(rejected).toBeUndefined()
      })
    } else {
      it('CASE 2 -- a 4xx/5xx carrying the error envelope REJECTS', case2)
    }

    if (breaches('network')) {
      it.skip(disabled(breach.defect, `${qualified} must reject on a network failure`), case3)
      it(pin(breach.defect, `${qualified} still resolves on a network failure`), async () => {
        const { rejected } = await withHandler(networkFailure(), () => settle(m))
        expect(rejected).toBeUndefined()
      })
    } else {
      it('CASE 3 -- a network failure rejects', case3)
    }

    if (BODYLESS.has(qualified)) {
      it('CASE 4 -- not applicable: this method reads no response body', () => {
        expect(BODYLESS.has(qualified)).toBe(true)
      })
    } else if (breaches('nonJson')) {
      it.skip(disabled(breach.defect, `${qualified} must reject on a non-JSON 2xx`), case4)
      it(pin(breach.defect, `${qualified} still resolves on a non-JSON 2xx`), async () => {
        const { rejected } = await withHandler(nonJsonBody(), () => settle(m))
        expect(rejected).toBeUndefined()
      })
    } else {
      it('CASE 4 -- a 2xx with a non-JSON body rejects', case4)
    }
  })
})

describe('what a rejection carries', () => {
  /*
   * 🔴 THE BREACHED METHODS ARE EXCLUDED, AND SAYING WHY MATTERS MORE THAN THE FILTER.
   *
   * This block is about the SHAPE of a rejection, not about which methods reject. One is still
   * admitted not to reject on every case — `bankService.updateBank` (A26) — and it is documented at
   * its site with the reason. Asking it "what does your rejection carry" produces `undefined`,
   * which is a fact about A26 and tells you nothing about A24.
   *
   * ✅ `jobGradeService.getByBank` LEFT THIS EXCLUSION AUTOMATICALLY when `06.1b` closed A25, and
   * that is the derived filter earning its keep: removing one line from `BREACHES` both un-skipped
   * its three cases above and added it to this population, with nothing here to remember.
   *
   * ⚠️ AND THE EXCLUSION IS DERIVED FROM `BREACHES`, never listed again here. A second hand-typed
   * list would drift from the first the day a row closes — which is precisely what happened to the
   * skipped version of this test: it iterated ALL methods, and nobody noticed it could not have
   * passed, because it was skipped.
   */
  const methods = allServiceMethods().filter(
    (m) => !NON_FETCHING.has(m.qualified) && !BREACHES[m.qualified],
  )

  /*
   * ✅ A24 IS CLOSED BY `06.1a`, AND THIS IS THE FLIPPED PAIR.
   *
   * The standard-facing test below was `it.skip(disabled(DEFECTS.A24, ...))` and is now live; the
   * two pins that asserted the defect are DELETED rather than made to pass. What they asserted:
   *
   *   "every rejection still discards status and validationErrors" -- every service threw
   *   `new Error(\`HTTP ${r.status}: ${r.statusText}\`)`, which keeps neither. And `statusText` is
   *   the EMPTY STRING over HTTP/2, which every deployment of this application uses, so the whole
   *   of a failed capture's diagnosis was `HTTP 500:` with nothing after the colon.
   *
   *   "three modules still reject with a bare string rather than an Error" -- `handleResponse` in
   *   recruiterService, recruitmentChannelService and subscriptionsService did
   *   `Promise.reject(msg)` with the raw response TEXT, so a caller's ordinary
   *   `catch (e) { e.message }` was `undefined` and even the free-text message was lost.
   *
   * Both are fixed at one home: every service goes through `requestJson`, which rejects with an
   * `Error` carrying `message`, `status`, `validationErrors`, `kind` and `retryable`
   * (conventions.md §16).
   */
  it('a rejection carries status and validationErrors, on every method', async () => {
    // The server sends `validationErrors` -- the one part of its envelope that is stable and
    // field-addressable -- and the service layer used to throw it away, so a form could not show
    // "identityNumber: must be 13 digits" even though it had been sent.
    for (const m of methods) {
      server.use(envelope(400))
      const { rejected } = await settle(m)
      expect(rejected, `${m.qualified}`).toMatchObject({
        status: 400,
        validationErrors: { identityNumber: 'must be 13 digits' },
      })
    }
  })

  it('every rejection is an Error, so `e.message` is never undefined', async () => {
    const nonErrors = []
    for (const m of methods) {
      server.use(envelope(400))
      const { rejected } = await settle(m)
      if (rejected !== undefined && !(rejected instanceof Error)) nonErrors.push(m.qualified)
    }
    expect(
      nonErrors,
      'A service is rejecting with something that is not an Error again. A caller doing the ' +
        'ordinary `catch (e) { e.message }` gets `undefined`, so the server\'s own sentence is ' +
        'lost by anyone handling the failure the normal way -- which is A24\'s quieter half.',
    ).toEqual([])
  })

  it('every rejection carries a `kind` from the closed vocabulary, and a `retryable`', async () => {
    // 🔑 §16's VOCABULARY, ASSERTED OVER THE WHOLE DISCOVERED POPULATION rather than at the one
    // or two call sites a hand-written test would have picked. A sixth kind invented at a call
    // site is how a consumer's `switch` silently stops covering its input.
    const KINDS = new Set(['http', 'network', 'nonJson', 'timeout', 'abort'])
    const wrong = []
    for (const m of methods) {
      server.use(envelope(500))
      const { rejected } = await settle(m)
      if (!rejected) continue
      if (!KINDS.has(rejected.kind) || typeof rejected.retryable !== 'boolean') {
        wrong.push(`${m.qualified} kind=${rejected.kind} retryable=${rejected.retryable}`)
      }
    }
    expect(
      wrong,
      'A rejection is missing `kind` or `retryable`, or carries a kind outside the closed set ' +
        `${[...KINDS].join(' | ')}. Both are published in conventions.md §16 and the Capture ` +
        'Member arc consumes them.',
    ).toEqual([])
  })

  it('a 5xx is retryable and a 4xx is NOT, so no retry button lies', async () => {
    // Derived once in the helper rather than by each consumer from `status`. A 4xx is the server
    // saying the request was wrong: retrying it unchanged produces the same refusal, and a retry
    // control that cannot work is worse than none.
    const m = methods.find((x) => !NON_FETCHING.has(x.qualified))

    server.use(envelope(500))
    expect((await settle(m)).rejected).toMatchObject({ kind: 'http', retryable: true })

    server.use(envelope(400))
    expect((await settle(m)).rejected).toMatchObject({ kind: 'http', retryable: false })

    server.use(networkFailure())
    expect((await settle(m)).rejected).toMatchObject({ kind: 'network', retryable: true })
  })
})

describe('the endpoint each method actually calls', () => {
  // THE RUNTIME WITNESS. Phase 4 discovers the client's endpoint surface by
  // STATICALLY scanning src/services/. A static discovery guard with no runtime
  // counterpart is grading its own homework: an endpoint the scan misses is
  // invisible, and one it invents looks real. This records what the methods
  // actually requested when executed, so the two can be compared.
  const observed = new Map()

  beforeEach(() => server.use(okJson()))
  afterEach(() => {})

  it.each(allServiceMethods().filter((m) => !NON_FETCHING.has(m.qualified)).map((m) => [m.qualified, m]))(
    '%s addresses exactly one endpoint family',
    async (qualified, m) => {
      const seen = []
      const listener = ({ request }) =>
        seen.push(`${request.method} ${new URL(request.url).pathname}`)
      server.events.on('request:start', listener)
      try {
        await settle(m)
      } finally {
        server.events.removeListener('request:start', listener)
      }
      expect(seen.length, `${qualified} issued no request`).toBeGreaterThan(0)
      observed.set(qualified, [...new Set(seen)])
    },
  )

  // A23, fixed by the October build: the pin that asserted `POST /api/recruitment-channels` is deleted, and the
  // standard-facing test below runs live. It also holds the body: a subscription's row, never `{ name }`.
  it('A23: subscriptionsService.create POSTs a subscription row to /api/subscriptions', async () => {
    const seen = []
    let body
    const listener = async ({ request }) => {
      seen.push(`${request.method} ${new URL(request.url).pathname}`)
      body = await request.clone().json().catch(() => undefined)
    }
    server.events.on('request:start', listener)
    try {
      server.use(okJson())
      await settle(
        allServiceMethods().find((m) => m.qualified === 'subscriptionsService.create'),
      )
    } finally {
      server.events.removeListener('request:start', listener)
    }
    expect(seen).toEqual(['POST /api/subscriptions'])
    await vi.waitFor(() => expect(body).toMatchObject({ bankName: 'Synthetic Bank Limited', subscriptionAmount: 174 }))
  })
})
