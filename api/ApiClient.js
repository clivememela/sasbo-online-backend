import { expect } from '@playwright/test'
import { API_URL } from '../support/env.js'

/**
 * The base every API client extends. All REST logic in this suite lives in this
 * directory and nowhere else.
 *
 * WHY A BASE CLASS RATHER THAN CALLING `request` DIRECTLY FROM STEPS
 * Two things have to be true of every call, and neither is something an author
 * should have to remember per call:
 *
 *   1. A non-2xx response must fail with the SERVER'S message, not with a
 *      downstream `undefined is not an object` forty lines later. An API test
 *      whose failure output is a JavaScript type error tells you nothing about
 *      what the server said.
 *   2. A response must be checked before it is parsed. `response.json()` on a
 *      500 that returned an HTML error page throws a parse error, and the parse
 *      error is what gets reported -- so the actual status code, which is the
 *      thing you needed, never appears.
 *
 * It is built on Playwright's own `request` context rather than fetch, so API
 * calls share the runner's tracing, timeouts and retry-free semantics. A call
 * made here shows up in the trace viewer beside the browser steps.
 */
export class ApiClient {
  /**
   * @param {import('@playwright/test').APIRequestContext} request
   *   Playwright's request context, supplied by the fixture.
   * @param {{ token?: string }} [caller]
   *   A signed-in caller's session token (`AuthApi.signInAs`); left out, the
   *   caller is anonymous -- which in sign-in's open mode reaches every route
   *   that names no permission, as every journey before October did.
   */
  constructor(request, { token } = {}) {
    this.request = request
    this.baseURL = API_URL
    this.token = token ?? null
  }

  /** Absolute URL for a path, so callers never assemble one by hand. */
  url(path) {
    return `${this.baseURL}${path.startsWith('/') ? path : `/${path}`}`
  }

  async get(path, options) {
    return this.#send('get', path, options)
  }

  async post(path, data, options) {
    return this.#send('post', path, { ...options, data })
  }

  async put(path, data, options) {
    return this.#send('put', path, { ...options, data })
  }

  async delete(path, options) {
    return this.#send('delete', path, options)
  }

  /**
   * Send, and fail with something a human can act on.
   *
   * `expectOk: false` is how a caller deliberately asserts a failure response --
   * a 404 for a deleted record, a 400 for invalid input. Those are real
   * assertions and must not be turned into thrown errors.
   */
  async #send(method, path, { expectOk = true, headers, ...options } = {}) {
    // The sign-in contract's bearer token, on every request of a signed-in
    // client -- one place, so no call can forget it. Never printed: a failure
    // below says the status and the body, never the request's headers.
    const sent = this.token ? { ...headers, Authorization: `Bearer ${this.token}` } : headers
    const response = await this.request[method](this.url(path), { ...options, headers: sent })

    if (expectOk && !response.ok()) {
      const body = await response.text().catch(() => '<unreadable body>')
      expect(
        response.ok(),
        `${method.toUpperCase()} ${this.url(path)} answered ${response.status()} ` +
          `${response.statusText()}.\nBody: ${body.slice(0, 2000)}`,
      ).toBe(true)
    }

    return response
  }

  /**
   * Parse a JSON body, having first established the response was worth parsing.
   *
   * The status check happens before the parse for the reason in the class
   * comment: a parse error masks the status code that explains it.
   */
  async json(response) {
    const text = await response.text()
    try {
      return JSON.parse(text)
    } catch {
      throw new Error(
        `Expected JSON from ${response.url()} (status ${response.status()}) but the ` +
          `body did not parse. First 500 characters:\n${text.slice(0, 500)}`,
      )
    }
  }

  /**
   * Take a record this suite did not create, change one field, and hand back a
   * function that puts the original value back.
   *
   * This is how a journey asserts real persistence WITHOUT creating anything:
   * an update is reversible where a create is not. The revert is registered as
   * the scenario's cleanup, so it runs even when the scenario fails -- which is
   * the run that would otherwise leave the seeded data modified.
   *
   * 🔑 WHY IT LIVES HERE AND NOT ONCE PER DOMAIN. It was written three times --
   * banks, members and subscriptions -- and the copies had already drifted: the
   * missing-record guard below existed only in the bank copy, so calling it with
   * an id that no longer resolved gave members and subscriptions
   * `Cannot read properties of undefined` from inside a cleanup registration,
   * which is precisely the "downstream `undefined is not an object` forty lines
   * later" this class's header says it exists to prevent. One implementation is
   * one place for that guard to be right.
   *
   * Subclasses supply the two domain hooks below. They keep their
   * domain-named `findBranch`/`findMember`/`findSubscription` as the
   * implementation, so no step definition changes.
   */
  async changeFieldReversibly(id, field, value) {
    const before = await this.findOne(id)
    if (!before) throw new Error(`No ${this.recordNoun} with id ${id}.`)

    const original = before[field]
    await this.updateOne(id, { [field]: value })

    return async () => {
      await this.updateOne(id, { [field]: original })
    }
  }

  /** What one record is called, for the message above. Subclasses override. */
  get recordNoun() {
    return 'record'
  }

  /** Domain hook: fetch one record by id, or a falsy value if there is none. */
  findOne() {
    throw new Error(`${this.constructor.name} does not implement findOne(id).`)
  }

  /** Domain hook: apply a partial change to one record by id. */
  updateOne() {
    throw new Error(`${this.constructor.name} does not implement updateOne(id, changes).`)
  }
}
