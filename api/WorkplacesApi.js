import { ApiClient } from './ApiClient.js'

/**
 * SASBO's workplaces (month end `01.2`), read from the generated route artifact:
 *
 *   GET /api/workplaces?page=&size=   { content: [{ id, raNumber, name, employer, area, committee, committeeName, … }],
 *                                       totalElements, totalPages, … }
 *
 * 🔴 NO WORKPLACE IS EVER NAMED IN THIS REPOSITORY. They are SASBO's own, and no file outside the server's repository
 * names one (MD57) -- so a journey reads one from the server when it runs, and looks for it on the screen.
 */
export class WorkplacesApi extends ApiClient {
  async page(size = 50) {
    return this.json(await this.get(`/api/workplaces?page=0&size=${size}`))
  }

  /** The workplaces the screen's search would find for `text`, as the server answers it. */
  async search(text, size = 20) {
    return this.json(await this.get(`/api/workplaces?page=0&size=${size}&search=${encodeURIComponent(text)}`))
  }

  /** The open workplaces of one of SASBO's areas, by its letter -- one page of up to `size`. */
  async openInArea(area, size = 100) {
    const { content = [] } = await this.json(await this.get(`/api/workplaces?page=0&size=${size}&area=${encodeURIComponent(area)}`))
    return content.filter((w) => w.raNumber && w.employerId != null && !w.closedOn)
  }

  /**
   * Three open workplaces of `area`, two at one bank and one at another -- what a batch print groups -- as
   * `[firstBanksFirst, otherBanks, firstBanksSecond]`, the order an organiser might type them in. Asked of the server.
   */
  async threeAtTwoBanks(area) {
    const byBank = new Map()
    for (const w of await this.openInArea(area)) byBank.set(w.employerId, [...(byBank.get(w.employerId) ?? []), w])
    const banks = [...byBank.values()]
    const pair = banks.find((at) => at.length >= 2)
    const other = banks.find((at) => at !== pair)
    if (!pair || !other) {
      throw new Error(`Area ${area}'s first page of workplaces has no bank with two open workplaces beside another bank -- is the switch at all?`)
    }
    return [pair[0], other[0], pair[1]]
  }

  /** A number of R/A shape that no workplace has: the largest R/A number, unless the search finds one there. */
  async aNumberThatIsNoWorkplace() {
    for (let candidate = 999999999; candidate > 999999990; candidate -= 1) {
      const found = await this.search(String(candidate))
      if (!found.content?.some((w) => w.raNumber === candidate)) return candidate
    }
    throw new Error('Every R/A number tried is a workplace -- choose another way to find one that is not')
  }

  /**
   * A workplace whose whole lineage is on record -- an employer, an area and a committee -- and whose name the search
   * finds on its first page, so that the screen can show it without paging. Asked of the server, never assumed.
   */
  async oneWithItsLineage() {
    const { content = [] } = await this.page()
    const candidates = content.filter((w) => w.name?.trim().length >= 2 && w.raNumber && w.employer && w.area
      && (w.committeeName || w.committee) && !w.closedOn)
    for (const candidate of candidates.slice(0, 10)) {
      const found = await this.search(candidate.name.trim())
      if (found.totalElements <= found.size && found.content.some((w) => w.id === candidate.id)) return candidate
    }
    throw new Error('No workplace on the first page has its lineage on record and a name its search finds on one page '
      + '-- is the switch at all?')
  }
}
