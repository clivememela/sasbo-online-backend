import { ApiClient } from './ApiClient.js'

/**
 * The debit order run and its returns -- `/api/acb/runs` (Debit Order Deductions, the module once called A.C.B.; its
 * addresses kept the old name).
 *
 *   GET  /api/acb/runs                            the runs, newest first, a page at a time
 *   GET  /api/acb/runs/{id}/lines                 a run's debits
 *   GET  /api/acb/runs/{id}/unpaids               a run's unpaids, each with its decision or the one its code suggests
 *   POST /api/acb/runs/{id}/lines/{lineId}/unpaid records an unpaid -- data entry, not gated by sign-in
 *
 * 🔑 A DECISION ON AN UNPAID IS FINAL BY DESIGN: the server refuses a second one (UNPAID_ALREADY_RECORDED), so
 * no scenario can put one back. What a scenario decides, it therefore MINTS first -- the seed's one undecided
 * unpaid when it is still undecided, and otherwise a late return recorded on one of the seeded month's paid
 * debits, as a bank's late report is. Nothing the seed decided is touched.
 */
export class AcbApi extends ApiClient {
  /** The newest run whose unpaids still take a decision -- the run the Re-Debits screen leads with. */
  async latestReturnedRun() {
    const page = await this.json(await this.get('/api/acb/runs?page=0&size=12'))
    return page.content.find((run) => run.status === 'RETURNED' || run.status === 'SUBMITTED') ?? null
  }

  async unpaids(runId) {
    return (await this.json(await this.get(`/api/acb/runs/${runId}/unpaids?page=0&size=200`))).content
  }

  async lines(runId) {
    return (await this.json(await this.get(`/api/acb/runs/${runId}/lines?page=0&size=200`))).content
  }

  /**
   * An undecided unpaid of the returned run whose code suggests presenting it again: the seed's own while it is
   * undecided, else a late "Not Provided For" return minted on a paid debit of the same run.
   *
   * @param {string} notedOn the day the bank's report is noted, YYYY-MM-DD
   */
  async undecidedUnpaidToPresentAgain(runId, notedOn) {
    const waiting = (await this.unpaids(runId))
      .find((line) => line.decision == null && line.suggestedDecision === 'RE_PRESENT')
    if (waiting) return waiting
    // A member with ONE line in the run, so the screen shows them in one row -- a member presented again
    // beside their own debit has two.
    const lines = await this.lines(runId)
    const paid = lines.find((line) => line.outcome === 'PAID'
      && lines.filter((other) => other.memberId === line.memberId).length === 1)
    if (!paid) throw new Error(`run ${runId} has no paid debit left to record a late return on`)
    return this.json(await this.post(`/api/acb/runs/${runId}/lines/${paid.id}/unpaid`,
      { code: '2', notedOn, decision: null }))
  }

  /** The runs on record, newest first -- one page, which is every run a scenario can have made. */
  async runs() {
    return (await this.json(await this.get('/api/acb/runs?page=0&size=50'))).content
  }

  async run(id) {
    return this.json(await this.get(`/api/acb/runs/${id}`))
  }

  /** Every line of a run, page by page -- the server caps a page at 200. */
  async allLines(runId) {
    const lines = []
    for (let page = 0; ; page += 1) {
      const answer = await this.json(await this.get(`/api/acb/runs/${runId}/lines?page=${page}&size=200`))
      lines.push(...answer.content)
      if (page + 1 >= answer.totalPages) return lines
    }
  }

  /**
   * The restore for a scenario that prepared a run: cancel it, at the version it has NOW, if it is still a
   * proposal. A run a scenario already cancelled is left as it is -- cancelled, and on record.
   */
  async cancelIfLive(runId) {
    const run = await this.run(runId)
    if (run.status !== 'PREPARED' && run.status !== 'APPROVED') return
    await this.post(`/api/acb/runs/${runId}/cancel`, { version: run.version, reason: 'Cancelled by the automation suite after its scenario' })
  }

  /** An anonymous caller approving: the answer as it came, never thrown (`expectOk: false`). */
  async approveAnonymously(runId, version) {
    const response = await this.post(`/api/acb/runs/${runId}/approve`, { version }, { expectOk: false })
    return { status: response.status(), body: await response.json().catch(() => ({})) }
  }

  /** The decision the server holds for one line of the run's unpaids, or undefined when it is not an unpaid. */
  async decisionOf(runId, lineId) {
    return (await this.unpaids(runId)).find((line) => line.id === lineId)?.decision
  }
}
