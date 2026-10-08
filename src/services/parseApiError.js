/**
 * The single error path for every service call.
 *
 * WHAT WAS WRONG
 * Every service did `throw new Error(\`HTTP ${r.status}: ${r.statusText}\`)`.
 * That discards the JSON body -- the one place the server puts a message written
 * for a person -- and `statusText` is the EMPTY STRING over HTTP/2, which every
 * deployment of this application uses. So the user's entire diagnosis of a failed
 * capture was the observed:
 *
 *     Failed to save member: HTTP 500:
 *
 * with nothing after the colon. The server had said something useful and the
 * client threw it away.
 *
 * WHY `validationErrors` IS FIRST-CLASS AND NOT A `cause`
 * `GlobalExceptionHandler` returns per-field messages under `validationErrors`
 * for a 400. If a consumer has to type-test the error to find out whether it
 * carries them, every consumer writes that test and one of them writes it wrong.
 * It is always present here -- `{}` when there are none -- so a caller can do
 * `Object.assign(fieldErrors, err.validationErrors)` unconditionally.
 *
 * THE SHAPE OF A REJECTION
 *   err.message          a string fit to show a user, never empty
 *   err.status           the HTTP status, or 0 for a network/timeout failure
 *   err.validationErrors { field: message }, `{}` when the server sent none
 *   err.isNetworkError   true when the request never got an answer
 *   err.body             the parsed envelope, for logging. Never rendered.
 */

/**
 * Every user-facing string this module can produce, in one place.
 *
 * All of them, not the three that happened to be reused -- hoisting half of them is
 * worse than hoisting none, because a reader cannot tell which strings are "the
 * vocabulary" and which are incidental.
 */
const NOT_JSON = 'The server returned an unexpected response. Please try again.'
const NO_ANSWER = 'Could not reach the server. Check your connection and try again.'
const TOO_SLOW = 'The request took too long and was stopped.'
const SERVER_FAILED = 'The server could not complete that request.'
const REJECTED = 'That request was rejected.'
const UNKNOWN = 'Something went wrong. Please try again.'
const NOT_A_FILE = 'The server answered with a web page instead of the file. Please try again.'

/* ─────────────────────────────────────────────────────────────────────────────
 * 🔴 EVERYTHING FROM HERE TO THE END OF `requestJson` IS THE DESIGN ARC'S ADDITION,
 * and it is ADDITIVE so that the two branches can still merge cleanly.
 *
 * This file arrived verbatim from `phase/capture-member` **`f020af9`** — deliberately, because
 * `06.1a`'s "build the helper" scope was already discharged there and two error paths in one
 * service layer is worse than either. What that version does not carry is the half the plan
 * assigns to THIS arc:
 *
 *   `kind`       the closed vocabulary conventions.md §16 fixes at the owner's request:
 *                http · network · nonJson · timeout · abort
 *   `retryable`  whether offering "retry" is honest, so no consumer re-derives it from status
 *   the CEILING  A14. Their branch has no timeout anywhere, confirmed: the only
 *                `AbortController|signal:|setTimeout` hits under `src/services/` are inside the
 *                pin that asserts their absence.
 *
 * ⚠️ A14's PIN CANNOT BE CLOSED BY A PARTIAL ADOPTION, which is why the ceiling lands across
 * every service at once rather than service by service: the pin's static half scans
 * `src/services/*.js` and asserts the match list is EMPTY, so a ceiling on some services fails
 * the scan without closing the row.
 *
 * 📨 THE DIFF SHAPE IS OWED TO THE CAPTURE MEMBER ARC and has not been delivered: their session
 * is offline (checked). Recorded here and in `repoState.md` rather than assumed sent — their
 * copy of A14's pin flips when these two halves meet, and that should be one move.
 * ───────────────────────────────────────────────────────────────────────────── */

/**
 * The read and write ceilings, and the notice threshold, from conventions.md §16.
 *
 * 🔑 A WRITE GETS LONGER THAN A READ ON PURPOSE. A read that is slow can be retried for free; a
 * write that is abandoned mid-flight leaves the caller unable to say whether it happened, which
 * is worse than waiting. 5s is where the UI says "taking longer than expected" — that belongs to
 * the component, not here, and is exported so the two cannot drift.
 */
export const READ_CEILING_MS = 10_000
export const WRITE_CEILING_MS = 20_000
export const SLOW_NOTICE_MS = 5_000

/**
 * The closed `kind` vocabulary. Frozen, because a sixth kind invented at a call site is how a
 * consumer's `switch` silently stops covering its input.
 */
export const ERROR_KINDS = Object.freeze({
  HTTP: 'http',
  NETWORK: 'network',
  NON_JSON: 'nonJson',
  TIMEOUT: 'timeout',
  ABORT: 'abort',
})

/**
 * Is offering "retry" honest for this failure?
 *
 * 🔑 DERIVED ONCE, HERE, rather than by each consumer from `status`. A 4xx is the server saying
 * the request was wrong: retrying it unchanged produces the same refusal, and a retry button
 * that cannot work is worse than none. A 5xx, a network failure and a timeout are all "try
 * again" — and an abort is not a failure at all, so the question does not arise.
 */
const isRetryable = (kind, status) => {
  if (kind === ERROR_KINDS.ABORT) return false
  if (kind === ERROR_KINDS.HTTP) return status >= 500
  return true
}

/**
 * Builds a rejection in the documented shape, with the defaults living where the
 * contract above is described rather than being retyped at each throw site.
 * @param {string} message
 * @param {{kind?: string, status?: number, validationErrors?: object,
 *          isNetworkError?: boolean, body?: unknown, cause?: unknown}} [fields]
 * @returns {Error}
 */
const apiError = (
  message,
  {
    kind = ERROR_KINDS.HTTP,
    status = 0,
    validationErrors = {},
    isNetworkError = false,
    body = null,
    cause,
  } = {},
) =>
  Object.assign(new Error(message), {
    kind,
    retryable: isRetryable(kind, status),
    status,
    validationErrors,
    isNetworkError,
    body,
    ...(cause !== undefined && { cause }),
  })

/**
 * Builds the rejection for a failed response, reading the server's envelope.
 * @param {Response} response
 * @returns {Promise<Error>}
 */
export async function apiErrorFrom(response) {
  let body = null
  try {
    body = await response.json()
  } catch {
    // Not JSON. `body` stays null and the fallback message below is used.
  }

  // `statusText` is deliberately a SUFFIX on the fallback only, never the whole
  // message: it is empty over HTTP/2, which is what produced the trailing-colon
  // message this helper exists to remove.
  const serverMessage =
    typeof body?.message === 'string' && body.message.trim() ? body.message.trim() : ''
  const fallback = response.status >= 500 ? SERVER_FAILED : REJECTED
  const suffix = response.statusText ? ` (${response.statusText})` : ''

  return apiError(serverMessage || fallback + suffix, {
    // Every non-ok response is `http`, whatever its body turned out to be. §16's wire-shape map
    // is explicit that THREE wire shapes collapse into this one kind — envelope JSON, an empty
    // body, and JSON that is not the envelope — and that the difference between them belongs in
    // `detail`, never in `kind`. Inferring the kind from the shape is how two arcs drift apart
    // while both believing they implemented the same contract.
    kind: ERROR_KINDS.HTTP,
    status: response.status,
    validationErrors:
      body?.validationErrors && typeof body.validationErrors === 'object'
        ? body.validationErrors
        : {},
    body,
  })
}

/**
 * Wraps a thrown-before-response failure (offline, DNS, abort, timeout).
 * @param {unknown} cause
 * @returns {Error}
 */
export function networkError(cause, { timedOut = false } = {}) {
  const isAbort = cause?.name === 'AbortError' || cause?.name === 'TimeoutError'

  // 🔴 AN ABORT AND A TIMEOUT ARRIVE AS THE SAME `AbortError`, AND THEY ARE NOT THE SAME EVENT.
  // A deadline we imposed is a `timeout` — a real failure, and the user is offered a retry. A
  // caller superseding its own request (`useAsyncOptions` sequencing an option list) is an
  // `abort`, and §16 is explicit that an abort is NOT a failure: it is dropped silently, never
  // rendered, never logged. The DOM gives both the same `name`, so the only way to tell them
  // apart is to know which one we caused — hence the flag, set by `requestJson` alone.
  const kind = timedOut
    ? ERROR_KINDS.TIMEOUT
    : isAbort
      ? ERROR_KINDS.ABORT
      : ERROR_KINDS.NETWORK

  return apiError(kind === ERROR_KINDS.NETWORK ? NO_ANSWER : TOO_SLOW, {
    kind,
    isNetworkError: true,
    cause,
  })
}

/**
 * The whole contract for one fetch, so no service re-implements it.
 *
 * Handles all four cases the error contract names: a 2xx with JSON resolves; a
 * 4xx/5xx rejects with the server's message; a network failure rejects; and a
 * 2xx whose body is not JSON rejects rather than surfacing a parse error from
 * somewhere unrelated.
 *
 * 🔑 IT TAKES A THUNK, NOT A PATH, and that is load-bearing rather than a style
 * choice: it keeps every `apiUrl('/api/...')` literal at its own call site, where
 * the static API-contract scan can see it. A single `apiFetch(path)` helper would
 * hide eight of nine services' endpoints behind one call and blind that guard.
 *
 * @param {() => Promise<Response>} send
 * @param {{ parseBody?: boolean, file?: boolean }} [options] `parseBody: false` for a 204/DELETE; `file: true` for a
 *   download, which resolves `{ blob, filename }` (see `readFile`).
 * @returns {Promise<unknown>}
 */
export async function requestJson(send, { parseBody = true, allowEmpty = false, optionalBody = false, file = false, timeoutMs = READ_CEILING_MS } = {}) {
  /*
   * 🔴 THE CEILING. A14, and the failure mode with the LONGEST time to detection in this
   * application: a hung backend renders the loading state forever, which an administrator reads
   * as "the system is slow" rather than "it is down", so nobody is called and nothing is
   * escalated. It is the one state that looks like the system working.
   *
   * 🔑 `send` NOW RECEIVES THE SIGNAL, and that is a deliberate signature change to every call
   * site. It stays a THUNK rather than becoming `apiFetch(path)` for the reason the original
   * comment gives: the static API-contract scan reads `apiUrl(<literal>)` per call site, and one
   * shared path-taking helper would hide eight of nine services' endpoints and blind that guard.
   * A thunk that ignores its argument still works, so adoption can be file by file even though
   * A14's pin can only CLOSE once every service has it.
   */
  const controller = new AbortController()
  let timedOut = false
  const deadline = setTimeout(() => {
    timedOut = true
    controller.abort()
  }, timeoutMs)

  /*
   * 🔴 R2-F4: THE DEADLINE COVERS THE BODY, NOT JUST THE HEADERS. It used to be cleared in a `finally` around `send`
   * alone -- so a proxy that sent headers and then stalled (a streaming gateway, a half-dead upstream) left every read
   * below running with no timer, the loading state up forever, and `DataState` promising "It will stop trying
   * shortly" while nothing was going to stop. That is A14's own failure mode, on the row the register calls closed.
   * The signal already passed to `fetch` aborts body reads too, so the ceiling is real once the timer outlives
   * `send`. Every exit path below clears it -- a pending timer keeps the event loop alive and, under fake timers in
   * the suite, a stray deadline fires into whichever test runs next, which is the anti-flake rule's own worst case.
   */
  const done = () => clearTimeout(deadline)

  let response
  try {
    response = await send(controller.signal)
  } catch (cause) {
    done()
    throw networkError(cause, { timedOut })
  }

  try {
    if (!response.ok) throw await apiErrorFrom(response)
    if (!parseBody) return undefined
    if (file) return await readFile(response)
    return await readBody(response, { allowEmpty, optionalBody })
  } catch (cause) {
    /*
     * 🔑 THE DEADLINE IS AUTHORITATIVE, NOT THE ERROR'S SHAPE. An aborted `response.json()` is caught inside
     * `readBody` and rewritten as `nonJson`, and an aborted error body is swallowed by `apiErrorFrom`, which returns
     * an `http` error -- so matching on `AbortError` here would have converted only one of the three paths and left
     * the other two lying about why they failed. If the timer fired, this failed because we stopped waiting.
     */
    if (timedOut) throw networkError(cause, { timedOut })
    throw cause
  } finally {
    done()
  }
}

/**
 * The body of a successful response, in the three shapes `requestJson` supports.
 *
 * Its own function because the ceiling above has to wrap every read in one `try`, and a `finally` around a block this
 * long is where the next exit path forgets to clear the timer. One parse path for all three: `response.json()` is
 * UTF-8-decode-then-parse, which is exactly `text()` then `JSON.parse`, so the default shape did not need its own.
 *
 *   default         a body is expected: empty or not JSON rejects (case ④).
 *   `allowEmpty`    a success that says NOTHING is a success, and a success that says something must still be JSON.
 *                   `POST /api/members/{id}/send-welcome` answers an empty 200 (A50) -- rejected as not-JSON, a sent
 *                   letter read as failed; `optionalBody` would accept it, and also a proxy's HTML page as sent.
 *   `optionalBody`  a body MAY arrive, and an unreadable one is not a failure -- `requestOptionalJson` below.
 */
async function readBody(response, { allowEmpty, optionalBody }) {
  const text = await response.text()
  if (!text && (allowEmpty || optionalBody)) return undefined
  try {
    return JSON.parse(text)
  } catch (cause) {
    // A successful write that answered with something unparseable: the write happened, the echo is unreadable, and
    // reporting a failure would turn a saved change into a reported error.
    if (optionalBody) return undefined
    throw apiError(NOT_JSON, { kind: ERROR_KINDS.NON_JSON, status: response.status, cause })
  }
}

/**
 * A download's body (`file: true`): `{ blob, filename }`, the name read from `Content-Disposition` when the server gave
 * one. A fourth shape, for a report fetched rather than linked -- a sign-in token travels on a `fetch`, never on an
 * `<a href>` -- and it stays inside `requestJson` so the deadline still covers the body (R2-F4).
 *
 * 🔴 AN HTML PAGE ANSWERED WITH A 200 IS NOT THE FILE: it is a proxy's or a gateway's page, and saving it would hand an
 * administrator a web page named `.xlsx`. It rejects as case ④ does; anything else is the file.
 */
async function readFile(response) {
  if (/^\s*text\/html\b/i.test(response.headers.get('Content-Type') ?? '')) {
    throw apiError(NOT_A_FILE, { kind: ERROR_KINDS.NON_JSON, status: response.status })
  }
  return { blob: await response.blob(), filename: filenameOf(response.headers.get('Content-Disposition')) }
}

/**
 * The file name in a `Content-Disposition` header -- RFC 6266's `filename*=UTF-8''…` first, then `filename="…"` -- or
 * `null`. Only the last path segment is kept, so a name can never point the save anywhere but the downloads folder.
 * @param {string | null} header
 * @returns {string | null}
 */
export function filenameOf(header) {
  if (!header) return null
  const extended = /filename\*\s*=\s*utf-8''([^;]+)/i.exec(header)
  const plain = /filename\s*=\s*"?([^";]+)"?/i.exec(header)
  let name = null
  try {
    name = extended ? decodeURIComponent(extended[1].trim()) : plain?.[1].trim() ?? null
  } catch {
    name = plain?.[1].trim() ?? null
  }
  const leaf = name?.split(/[\\/]/).pop()?.trim()
  return leaf && leaf !== '.' && leaf !== '..' ? leaf : null
}

/**
 * A write, which gets the longer ceiling.
 *
 * 🔑 A NAMED WRAPPER RATHER THAN A NUMBER AT 15 CALL SITES. `requestJson(send, { timeoutMs:
 * WRITE_CEILING_MS })` typed out per mutation is fifteen chances to use the read ceiling by
 * accident, and the mistake is invisible: the request simply gives up sooner than intended, on
 * the operations where giving up early is most expensive. Which ceiling applies is a property of
 * the CALL, so it is named at the call.
 */
export const requestJsonWrite = (send, options = {}) =>
  requestJson(send, { timeoutMs: WRITE_CEILING_MS, ...options })

/**
 * A write whose response body is OPTIONAL: resolves the parsed JSON when there is any, and
 * `undefined` when the server answered with an empty body or with something that is not JSON.
 *
 * 🔑 A THIRD SHAPE, BECAUSE THERE GENUINELY ARE THREE, and collapsing this one into either of the
 * others tells a lie:
 *
 *   `requestJson`      a body is expected. A 2xx that is not JSON is a defect (case ④).
 *   `parseBody: false` there is no body at all. A DELETE answering 204.
 *   this               a body MAY arrive. `PUT /api/banks/branches/{id}` returns the updated row
 *                      on some deployments and an empty 200 on others.
 *
 * Without it, `bankService.updateBank` had to be given `parseBody: false` and then read the
 * response itself — which meant reaching past the helper for the `Response`, and a helper you have
 * to reach past is a helper the next author will simply bypass.
 *
 * ⚠️ THE FAILURE PATH IS IDENTICAL. A 4xx/5xx still rejects with the server's own message,
 * `status` and `validationErrors`; a network failure and the ceiling behave exactly as above. What
 * is tolerated here is a SUCCESSFUL response that says nothing — never a failed one.
 *
 * 🔑 ONE CEILING, NOT TWO (RF). This was a second copy of `requestJson`'s timer, abort and R2-F4 body deadline; a
 * third shape of BODY is `readBody`'s concern, so it is `requestJson` with `optionalBody` at the write ceiling.
 */
export const requestOptionalJson = (send, { timeoutMs = WRITE_CEILING_MS } = {}) =>
  requestJson(send, { optionalBody: true, timeoutMs })

/**
 * Reads a message off anything a `catch` block might receive, so a component
 * never renders `undefined` or `[object Object]`.
 * @param {unknown} error
 * @returns {string}
 */
export function messageOf(error) {
  return typeof error?.message === 'string' && error.message.trim()
    ? error.message
    : UNKNOWN
}

/**
 * Could a failed WRITE have landed anyway? (R3-F2, P9's independent review.)
 *
 * 🔴 "NOTHING WAS WRITTEN" IS A CLAIM ABOUT THE SERVER, and only one rejection supports it: a 4xx, where the server
 * answered and refused. A timeout or a lost connection says only that the ANSWER did not arrive; a 5xx can fail after
 * the row was written; a 2xx whose body was not JSON succeeded. Telling a clerk any of those was "not saved" invites
 * them to redo a change that already happened -- on Capture Member, a second copy of the member.
 *
 * So a screen asks this before it chooses its words: `false` may say "not saved", `true` must say "could not be
 * confirmed -- check before trying again". Anything unrecognised counts as possibly landed, the safe side to be wrong on.
 * @param {unknown} error
 * @returns {boolean}
 */
export function writeMayHaveLanded(error) {
  return !(error?.kind === ERROR_KINDS.HTTP && error.status >= 400 && error.status < 500)
}

/**
 * The sentence `writeMayHaveLanded` chooses, for a screen whose notice is one line: "was not saved" only on a refusal,
 * otherwise "could not be confirmed" and where to go and look. Recruiters and Recruitment Channel each wrote this
 * sentence themselves, identically but for the place named (RF); the rule and its words now live together.
 * @param {unknown} error
 * @param {string} what the thing written, as the subject of the sentence -- "That recruiter"
 * @param {string} [whereToCheck] what to reload and check -- "the recruiter"
 * @returns {string}
 */
export function writeFailure(error, what, whereToCheck = 'the list') {
  return writeMayHaveLanded(error)
    ? `${what} could not be confirmed. Reload ${whereToCheck} and check before trying again.`
    : `${what} was not saved. ${messageOf(error)}`.trim()
}

/**
 * Reads the per-field errors off anything, always an object.
 * @param {unknown} error
 * @returns {Record<string, string>}
 */
export function validationErrorsOf(error) {
  return error?.validationErrors && typeof error.validationErrors === 'object'
    ? error.validationErrors
    : {}
}
