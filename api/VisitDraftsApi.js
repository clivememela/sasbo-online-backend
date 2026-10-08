import { ApiClient } from './ApiClient.js'

/**
 * An organiser's visit drafts (client and server `oct/reports-visits`), read from the generated route artifact:
 *
 *   GET    /api/workplaces/visit-drafts/{id}           one of the caller's drafts -- 404 VISIT_DRAFT_NOT_FOUND otherwise
 *   DELETE /api/workplaces/visit-drafts/{id}?version=  discard it at the version named -- 204
 *
 * Every route needs VISITS_CAPTURE and answers only the caller's own drafts, so this client is always made with the
 * signed-in organiser's token.
 */
export class VisitDraftsApi extends ApiClient {
  /** One draft, as the server answers its owner -- `{ status, body }`, never thrown: a 404 is what a journey asserts. */
  async attemptFind(id) {
    const response = await this.get(`/api/workplaces/visit-drafts/${encodeURIComponent(id)}`, { expectOk: false })
    return { status: response.status(), body: await response.json().catch(() => ({})) }
  }

  /** Discard a draft at the version it is at now; a draft already gone is gone, and that is no failure. */
  async discardIfThere(id) {
    const { status, body } = await this.attemptFind(id)
    if (status === 404) return
    if (status !== 200) throw new Error(`visit draft ${id} could not be read to discard it: ${status}`)
    const response = await this.delete(`/api/workplaces/visit-drafts/${encodeURIComponent(id)}?version=${body.version}`,
      { expectOk: false })
    if (response.status() !== 204 && response.status() !== 404) {
      throw new Error(`visit draft ${id} was not discarded: ${response.status()}`)
    }
  }
}
