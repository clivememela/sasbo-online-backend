import { expect } from '@playwright/test'
import { BasePage, labelPattern } from './BasePage.js'

/**
 * The member register at `/members` -- three criteria with twelve more behind "More
 * criteria", a paginated table, and a docked record panel that reads and edits one member.
 *
 * 🔴 BEFORE THE DESIGN CLIENT THIS SCREEN HAD NO LOADING STATE AND NO ERROR STATE (register
 * row A27). Its mount effect was four fetches whose entire failure handling was
 * `console.error`, so the table said no members matched in THREE different situations:
 *
 *   the data has not arrived yet    (every load, for a moment)
 *   the search matched nothing      (correct)
 *   the backend is unreachable      (a confident wrong answer)
 *
 * The design client tells them apart: the pager says "Loading members…" while the fetch is
 * out, the table says "The register could not be reached" (with a retry) when it fails, and
 * "No members match these criteria" only when it genuinely matched nothing. ✅ A27 CLOSED
 * 2026-09-16: both A27 scenarios run against that screen and pass -- the loading one with the
 * member list held in flight, so "Loading members…" is asserted while it is still true rather
 * than raced.
 *
 * `expectLoaded` still waits on the summary reporting a count rather than on a spinner
 * clearing, because that is arrival on either client: the summary line only exists once
 * there are members to count.
 */
/** "Thandi Mokoena’s record saved", or "Changes saved" when the record has no name. */
export const SAVED_CONFIRMATION = /’s record saved$|^Changes saved$/

/**
 * 🔁 THE REGISTER'S OWN READ OF ITS MEMBERS -- either of two, and the reason is the October build (register row A10).
 *
 * Until then the screen fetched every member, with every field (`GET /api/members`), and paged them in the browser at
 * a hundred. Register paging asks the server for ONE page of slim rows instead (`POST /api/members/register/search`,
 * fifty to a page unless the address asks otherwise), reads the figure strip apart from it (`GET
 * /api/members/register/overview`), and reads one member whole only when a row is opened (`GET /api/members/{id}`).
 * The journeys judge "the register" by whichever of the two reads this load made, so they hold on the client either
 * side of that change; the whole-list half goes once the paged register is on main.
 */
export const isRegisterReadRequest = (request) => {
  const { pathname } = new URL(request.url())
  const method = request.method()
  return (method === 'GET' && pathname === '/api/members') || (method === 'POST' && pathname === '/api/members/register/search')
}

/** The same question of a response: was it the answer to the register's read? */
export const isRegisterRead = (response) => isRegisterReadRequest(response.request())

/**
 * What one register read says about the register: how many members it holds for the question, and how many a page
 * shows -- the whole list's length and the browser's hundred, or the page's own `totalElements` and `size`.
 */
export async function registerLoadOf(response) {
  const body = await response.json()
  return Array.isArray(body)
    ? { total: body.length, pageSize: 100, page: null }
    : { total: body.totalElements, pageSize: body.size, page: body }
}

/** The page an address asks the register for (`?page=2`), counted from one as the address counts it; page 1 if none. */
const pageAskedFor = (query) => Number(new URLSearchParams(query).get('page') ?? 1)

const REFUSED = Symbol('refused')

export class MemberRegisterPage extends BasePage {
  constructor(page) {
    super(page, '/members')
  }

  /**
   * Overridden: arrival means the members are on screen, not that the route
   * rendered.
   *
   * 🔴 The count assertion is the load-bearing half. Asserting only the heading
   * passes about a hundred milliseconds before the fetch resolves, and every later
   * step then operates on a register that has not arrived. This is the same race
   * the bank listing had in Phase 5.
   *
   * 🔁 ON THE PAGE THE ADDRESS ASKED FOR (register paging). Page 1 is known by its first row, "Showing 1–50 of 1 363",
   * asserted exactly as before. A later page starts at a row only the server's page size decides, so it is known by
   * its own number: "Showing 51–100 of 1 363 · Page 2 of 28". Waiting for "Showing 1–" on page 2 -- which the screen
   * rightly never shows -- was the merged gate's red of 7 Oct (run 37680224612).
   */
  async expectLoaded(pageNumber = 1) {
    await expect(this.page.getByRole('heading', { name: 'Members', exact: true })).toBeVisible()
    await expect(this.summary()).toHaveText(pageNumber === 1 ? /Showing 1–\d+ of \d+/ : new RegExp(`\\bPage ${pageNumber} of \\d`))
  }

  /** A filter addressed by the name a user reads above it. */
  filter(label) {
    return this.page.getByLabel(label, { exact: true })
  }

  async filterBy(label, value) {
    await this.showAllCriteria()
    await this.filter(label).fill(value)
    return this
  }

  /**
   * Open "More criteria" if it is closed.
   *
   * 🔴 THE DESIGN CLIENT SHOWS THREE CRITERIA AND KEEPS TWELVE BEHIND A TOGGLE. Membership
   * Number and Surname are among the twelve, rendered with `hidden` until "More criteria" is
   * pressed -- and a hidden input cannot be filled. Read from the toggle's own `aria-expanded`
   * rather than assumed, so calling this twice in one scenario does not close it again.
   */
  async showAllCriteria() {
    const toggle = this.page.getByRole('button', { name: 'More criteria' })
    if ((await toggle.getAttribute('aria-expanded')) !== 'true') await toggle.click()
    await expect(toggle).toHaveAttribute('aria-expanded', 'true')
  }

  /**
   * The count the register reports, or that it reports none.
   *
   * With no matches the design client's pager says "No members match" INSTEAD of a summary,
   * so zero is a different sentence rather than a different number in the same one.
   */
  async expectReportedCount(count) {
    if (count === 0) {
      await expect(this.page.getByText('No members match', { exact: true })).toBeVisible()
    } else {
      await expect(this.summary()).toHaveText(new RegExp(`of ${count}\\b`))
    }
    return this
  }

  /**
   * Open the register at `/members` and answer what its own read of the members said (`registerLoadOf`) -- the load the
   * screen is then judged against, so a member captured by another journey a moment before cannot make it disagree.
   * Listened for before the page is asked for, so the answer cannot arrive unheard; settled quietly if the page never
   * asks, so the open fails in its own words rather than as an unhandled rejection.
   *
   * 🔴 WHICH members are on a given page was NOT stable on the whole-list register: `GET /api/members` has no `ORDER
   * BY`, and the browser paged whatever order came. The paged register orders by surname unless asked otherwise. Either
   * way nothing here asserts that a named member is on page one; a scenario that wants a member searches for them.
   */
  async openAndReadTheLoad(query = '') {
    const received = this.page.waitForResponse(isRegisterRead).then(registerLoadOf)
    received.catch(() => {})
    await this.page.goto(`${this.baseURL}${this.path}${query}`)
    await this.expectLoaded(pageAskedFor(query))
    return received
  }

  /**
   * The table shows exactly the rows of a page the server answered, in its order: as many rows, and each of the first
   * few showing its member's surname. The order is the server's to decide (its collation, its tie-breaks); what the
   * screen owes is to show the page it was given, as it was given.
   */
  async expectRowsOf(content) {
    await expect(this.rows()).toHaveCount(content.length)
    for (const [index, member] of content.slice(0, 5).entries()) {
      await expect(this.rows().nth(index), `row ${index + 1} of the page`).toContainText(member.surname)
    }
  }

  /** The row for one member, addressed by text it shows. */
  row(surname) {
    return this.page.getByRole('row').filter({ hasText: surname })
  }

  /** The text the table shows when it has no rows to show. */
  emptyState() {
    return this.page.getByText('No members match these criteria')
  }

  /**
   * What this screen says when the members could not be fetched.
   *
   * EXACT, now that the screen has one. This locator was written before the screen could
   * report a failure at all (A27) and matched three generous wordings -- none of which the
   * design client uses, so the scenario asserting it could not have passed. A generous
   * pattern was right for a requirement; for a screen that exists, it is a locator that can
   * match a sentence nobody meant.
   */
  errorState() {
    return this.page.getByText('The register could not be reached', { exact: true })
  }

  /** The pager's sentence while the members are on their way. One ellipsis character. */
  loadingState() {
    return this.page.getByText('Loading members…', { exact: true })
  }

  /**
   * The failed TABLE's way out.
   *
   * Scoped to the register's region: when the whole API is down the reference lists fail
   * too, and their banner carries a "Try again" of its own -- a page-wide locator resolved
   * to both and failed on strict mode (2026-09-16).
   */
  retry() {
    return this.page.getByRole('region', { name: 'Member register' }).getByRole('button', { name: 'Try again' })
  }

  /** Every sentence on the screen that states a count or a freshness it cannot know on failure. */
  claimsAboutMissingData() {
    return this.page.getByText(/^No members match$|The register below is up to date/)
  }

  /**
   * Open one member's record, addressed by MEMBERSHIP NUMBER.
   *
   * 🔴 NOT by surname, and the reason is the same one the branch page learned
   * from 31 branches all called "Head Office": the seeded register has two
   * members surnamed Naidoo, so filtering by surname leaves two rows and the
   * click opens whichever sorted first -- a different record from the one the
   * scenario chose, and the assertion then fails comparing two real members.
   *
   * ⚠️ IT WAS THE MEMBERSHIP NUMBER UNTIL 2026-09-18, and that is register row A61
   * rather than a change of mind: on a database built from empty every seeded member's
   * membership number is now NULL (the seeder loads members.json through Jackson into an
   * entity whose `membershipNumber` is `@JsonProperty(READ_ONLY)`, so the 160 numbers in
   * the file are dropped). The column renders empty and `GET /api/members` answers null,
   * which reached Playwright as "expected string, got object" -- `typeof null`. (A61 is fixed; the employee number
   * stays the key, being unique and needing no `M-`.)
   *
   * The EMPLOYEE number is the replacement, and it is unique across all 160 seeded
   * members (measured in data/members.json: 160 present, 160 distinct). The membership
   * number stays asserted, as a skipped scenario against A61, so the day the seeder is
   * fixed the suite says so rather than quietly never looking again.
   *
   * The count is asserted before the click rather than after, because a click into an
   * ambiguous list is already wrong by the time anything else could notice.
   */
  async openMember(employeeNumber) {
    await this.filterBy('Employee Number', employeeNumber)
    await expect(this.summary()).toHaveText(/Showing 1–1 of 1\b/)
    await this.row(employeeNumber).click()
    await expect(this.record()).toBeVisible()
    return this
  }

  /**
   * The member's record.
   *
   * 🔴 IT IS NOT A DIALOG ANY MORE. The design client shows the record as a docked side panel
   * beside the register -- an `<aside>` named "Member details" -- rather than a modal. It is a
   * landmark, so it is found by that role and name, the same way a screen reader reaches it.
   * Below 1280px it becomes a drawer over the register with the same role and name.
   */
  record() {
    return this.page.getByRole('complementary', { name: 'Member details', exact: true })
  }

  /**
   * One of the record's five tabs, by its full name.
   *
   * The strip SHOWS short labels ("Employment") and NAMES each tab in full through a
   * screen-reader span, so the full phrase in a feature file is still the tab's name.
   */
  async openRecordTab(name) {
    await this.record().getByRole('button', { name, exact: true }).click()
    return this
  }

  /**
   * A labelled value on the record, in read mode.
   *
   * The design client renders each field as a label paragraph followed by a value paragraph,
   * so the value is the paragraph straight after the label -- not "the paragraph in the div
   * holding the label", which now matches both and fails as ambiguous.
   */
  detail(label) {
    return this.record().getByText(label, { exact: true }).locator('xpath=following-sibling::p[1]')
  }

  async expectDetail(label, value) {
    await expect(this.detail(label)).toHaveText(String(value))
    return this
  }

  async startEditing() {
    await this.record().getByRole('button', { name: 'Edit Member', exact: true }).click()
    await expect(this.record().getByRole('button', { name: 'Save changes', exact: true })).toBeVisible()
    return this
  }

  /**
   * An editable field inside the record, addressed by its label.
   *
   * ⚠️ A required field's accessible name carries more than the words above it -- an
   * asterisk and a screen-reader "(required)", "Surname *(required)". `labelPattern`
   * tolerates both, so the feature files read in the user's words instead of the DOM's.
   */
  editable(label) {
    return this.record().getByLabel(labelPattern(label))
  }

  async editField(label, value) {
    await this.editable(label).fill(String(value))
    return this
  }

  /** A `<select>` in the record. `fill` throws on one, so they need two methods. */
  async editSelect(label, value) {
    await this.editable(label).selectOption(String(value))
    return this
  }

  /**
   * Save the record, through the design client's own confirmation.
   *
   * 🔴 THERE ARE NO BROWSER DIALOGS LEFT. The register used to `confirm()` and `alert()`; the
   * design client asks "Save these changes?" in an in-page dialog, confirms with a notice
   * naming the member, and reports a refusal inside the record -- "Fix these before saving" or
   * "The changes were not saved". A `dialog` listener would wait for an event that never comes.
   *
   * A refusal can arrive at either step -- validation stops the first click before the
   * confirmation opens -- so each wait accepts success OR refusal, and a refusal is thrown
   * with what the record said rather than left to time out.
   *
   * 🔴 THE CONFIRMATION IS RECORDED BY THE PAGE ITSELF, because it does not stay. The client
   * hides it 2 600 ms after it appears (`Members.jsx`, `setShowSavedNotification(false)`). The
   * Then step used to look for it a second time, and on a loaded host the gap between the two
   * looks outlived it: on 2026-09-16 the PUT answered 200, the register had saved, and the
   * scenario failed on "element(s) not found". Reading it once after `toBeVisible` would only
   * shrink that window, not close it -- the host can stall between any two protocol calls. So a
   * MutationObserver, armed BEFORE the click, stores the text in the page the instant it
   * renders, and both this method and the Then step read what was stored. No timing is left.
   */
  async saveChanges() {
    const refused = this.record().getByText(/^(Fix these before saving|The changes were not saved)$/)
    const refusal = async () =>
      new Error(`The register refused the change:\n${await refused.locator('xpath=ancestor::div[1]').textContent()}`)

    await this.record().getByRole('button', { name: 'Save changes', exact: true }).click()
    const confirm = this.page.getByRole('dialog', { name: 'Save these changes?' })
    await expect(confirm.or(refused)).toBeVisible()
    if (await refused.isVisible()) throw await refusal()

    await this.page.evaluate((source) => {
      const pattern = new RegExp(source)
      window.__sasboSaveConfirmation = null
      const observer = new MutationObserver(() => {
        const notice = [...document.querySelectorAll('span')].find((el) => pattern.test(el.textContent.trim()))
        if (!notice) return
        window.__sasboSaveConfirmation = notice.textContent.trim()
        observer.disconnect()
      })
      observer.observe(document.body, { subtree: true, childList: true, characterData: true })
    }, SAVED_CONFIRMATION.source)

    await confirm.getByRole('button', { name: 'Save changes', exact: true }).click()
    const outcome = async () =>
      (await this.page.evaluate(() => window.__sasboSaveConfirmation)) ?? ((await refused.isVisible()) ? REFUSED : null)
    await expect.poll(outcome, { message: 'the save was neither confirmed nor refused' }).not.toBeNull()
    if ((await outcome()) === REFUSED) throw await refusal()

    this.lastSaveConfirmation = await this.page.evaluate(() => window.__sasboSaveConfirmation)
    return this
  }

}
