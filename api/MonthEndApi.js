import { ApiClient } from './ApiClient.js'
import { monthBefore } from '../support/journeyMonth.js'

/**
 * Month end's reads (month end `04.1`), read from the generated route artifact:
 *
 *   GET  /api/month-end/months        { openMonth: '2026-09', taken: [{ month, state, takenAt, takenBy, … }] }
 *   POST /api/month-end/{month}/take  held by MONTH_END_TAKE since sign-in (server oct/signin 9641f05)
 *
 * 🔴 NO JOURNEY TAKES A MONTH. Taking one freezes its figures for good, and every journey that reads the months reads
 * them on this one stack -- so a take is only ever ASKED in a way the server must refuse (`attemptTake`): without a
 * session, for a month that has not begun, so a refusal that went missing could take nothing. The months a journey
 * names come from here: the server says which month it is, never the browser's clock.
 */
export class MonthEndApi extends ApiClient {
  async months() {
    return this.json(await this.get('/api/month-end/months'))
  }

  /** The month the server says is open. */
  async openMonth() {
    return (await this.months()).openMonth
  }

  /** The month after the open one -- a month that has not begun, which no take can take. Arithmetic, never a clock. */
  async monthNotBegun() {
    const [year, number] = (await this.openMonth()).split('-').map(Number)
    return number === 12 ? `${year + 1}-01` : `${year}-${String(number + 1).padStart(2, '0')}`
  }

  /** Ask for a take, and answer what the server said -- `{ status, body }`, never thrown. */
  async attemptTake(month, body = {}) {
    const response = await this.post(`/api/month-end/${encodeURIComponent(month)}/take`, body, { expectOk: false })
    return { status: response.status(), body: await response.json().catch(() => ({})) }
  }

  /**
   * The newest of the twelve months before the open one that has NOT been taken -- a past month whose figures are
   * worked out when they are asked for. Month end's control offers only the open month and the months taken, so a
   * journey reaches this one by the page's address, as a statistic's screen does.
   */
  async newestPastMonthNotTaken() {
    const { openMonth, taken = [] } = await this.months()
    const takenMonths = new Set(taken.map((month) => month.month))
    let month = openMonth
    for (let back = 0; back < 12; back += 1) {
      month = monthBefore(month)
      if (!takenMonths.has(month)) return month
    }
    throw new Error('Every one of the twelve months before the open one has been taken -- none is worked out live')
  }

  /**
   * The newest month that has been taken.
   *
   * The demonstration seed takes the months before the open one, so on this stack there is one. If there
   * is not, the scenario cannot be written honestly, and it says why rather than passing on nothing.
   */
  async newestTakenMonth() {
    const { taken = [] } = await this.months()
    const newest = taken.map((month) => month.month).sort().pop()
    if (!newest) {
      throw new Error('No month has been taken on this stack -- the demonstration seed takes the months before the open one')
    }
    return newest
  }
}
