import { ApiClient } from './ApiClient.js'

/**
 * The subscriptions reference table, whose rows carry each bank's debit order day (Debit Order Deductions, the module
 * once called A.C.B.).
 *
 *   GET    /api/subscriptions        every row
 *   GET    /api/subscriptions/{id}   one row
 *   POST   /api/subscriptions        create
 *   PUT    /api/subscriptions/{id}   update
 *   DELETE /api/subscriptions/{id}   remove  <- the only domain in this suite
 *                                              that has one
 *
 * ⚠️ THIS TABLE IS REFERENCE DATA THE CAPTURE PATH DEPENDS ON, which is why a
 * money-adjacent screen ends up mattering to the member journey. On create,
 * `MemberServiceImpl` overwrites the member's `monthlySubscription` and
 * `debitDay` from the row matching their bank -- so editing a row here changes
 * what a later capture is allowed to do. Every scenario that touches it must
 * put the original value back, and does.
 *
 * 🔒 THE THREE WRITES NEED `SUBSCRIPTIONS_MAINTAIN` since the October build
 * (server `oct/bugs`: only staff change amounts, the 5 Oct Show & Tell). The
 * reads stay open. So a client that writes is built with a signed-in caller's
 * token -- the Chief Financial Officer holds it -- and an anonymous one is
 * refused `401 SIGN_IN_REQUIRED`.
 */
export class SubscriptionsApi extends ApiClient {
  /** Every subscription row, sorted by id so "the first" means one record. */
  async listSubscriptions() {
    const rows = await this.json(await this.get('/api/subscriptions'))
    return rows.sort((a, b) => a.id - b.id)
  }

  async findSubscription(id) {
    return this.json(await this.get(`/api/subscriptions/${id}`))
  }

  async updateSubscription(id, changes) {
    const current = await this.findSubscription(id)
    return this.json(await this.put(`/api/subscriptions/${id}`, { ...current, ...changes }))
  }

  /**
   * Ask to write a row back exactly as it is, and answer what the server said -- `{ status, body }`, never thrown. A
   * journey asserting a refusal changes nothing even if the refusal it expects has gone.
   */
  async attemptUnchangedWrite(id) {
    const current = await this.findSubscription(id)
    const response = await this.put(`/api/subscriptions/${id}`, current, { expectOk: false })
    return { status: response.status(), body: await response.json().catch(() => ({})) }
  }

  /** Domain hooks for `ApiClient.changeFieldReversibly`. */
  get recordNoun() {
    return 'subscription'
  }

  findOne(id) {
    return this.findSubscription(id)
  }

  updateOne(id, changes) {
    return this.updateSubscription(id, changes)
  }
}
