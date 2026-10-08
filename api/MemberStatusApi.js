import { ApiClient } from './ApiClient.js'

/**
 * A member's status, the one way it changes (month end `03.1`): `POST /api/members/{id}/status`, with the reason its
 * kind asks for -- into a leaving status SASBO's deletion code, into Suspended SASBO's suspension code, back to any
 * other a reinstatement -- and the status the caller sees as `expectedStatus`, so a change made on a stale view is
 * refused rather than written over somebody else's.
 *
 * Read from the generated route artifact. Used by the figures' journeys to do the ONE thing a scenario does between
 * two readings, and by the history's to change a member behind an open record; the record's own Change status dialog
 * is driven through its page object.
 */
export class MemberStatusApi extends ApiClient {
  /** Ask for a change; `expectOk: false` returns a refusal for the scenario to read. */
  async change(memberId, change, options) {
    const response = await this.post(`/api/members/${memberId}/status`, change, options)
    return { status: response.status(), body: await this.json(response) }
  }

  /** Suspend, with SASBO's suspension code -- 11, *Not on payroll*, unless another is named. */
  async suspend(member, suspensionCode = 11) {
    return this.change(member.id, {
      toStatus: 'Suspended',
      expectedStatus: member.status,
      reason: 'SUSPENSION_CODE',
      suspensionCode,
      note: 'Suspended by a month-end journey.',
    })
  }

  /**
   * When the member's status last changed, as the server STORED it: read back from the member's history
   * (`GET /api/members/{id}/history`, newest first), never from the change's own answer. The answer carries the instant
   * the server stamped, and the database keeps it to the microsecond -- so only the stored one is, to the minute, the
   * instant the server's sentences name.
   */
  async lastChangedAt(member) {
    const page = await this.json(await this.get(`/api/members/${member.id}/history?page=0&size=10`))
    const change = page.content.find((entry) => entry.kind === 'STATUS')
    if (!change) throw new Error(`No change of status in the history of member ${member.id}`)
    return change.changedAt
  }

  /** Resign, with SASBO's deletion code -- 2, the code a resignation is filed under, unless another is named. */
  async resign(member, deletionCode = 2) {
    return this.change(member.id, {
      toStatus: 'Resigned',
      expectedStatus: member.status,
      reason: 'DELETION_CODE',
      deletionCode,
      note: 'Resigned by a month-end journey.',
    })
  }
}
