import { expect } from '@playwright/test'
import { CLIENT_URL } from '../support/env.js'

/**
 * The base every page object extends. All UI logic in this suite lives in this
 * directory and nowhere else -- step definitions delegate here and do nothing
 * else themselves.
 *
 * WHY PAGE OBJECTS AT ALL, GIVEN PLAYWRIGHT'S LOCATORS ARE ALREADY GOOD
 * Not to wrap the API -- Playwright's locators need no wrapper. The reason is
 * that a selector change must touch ONE file rather than every `.feature` that
 * happens to mention that screen. Gherkin decays faster than any other test
 * style when its steps carry selectors, because a feature file is the one
 * artefact a non-engineer is supposed to be able to read and edit.
 *
 * A second reason, specific to this suite: page objects are plain Playwright.
 * They have no dependency on the BDD layer at all. If `playwright-bdd` ever
 * stops being the right choice, the features and steps are rewritten and
 * everything in this directory survives untouched.
 *
 * SELECTOR POLICY, WHICH IS A CONTRACT AND NOT A PREFERENCE
 * Use `getByRole`, `getByLabel`, `getByText`. Reach for `data-testid` ONLY
 * where no accessible name exists -- and treat needing one as a finding about
 * the UI rather than a normal cost of testing it. A control this suite cannot
 * address by its accessible name is a control a screen reader cannot address
 * either.
 *
 * NO FIXED WAITS, EVER
 * There is no `waitFor(ms)` on this class and there must never be one.
 * Playwright's assertions retry on their own; a hard sleep is a flake with a
 * delay attached, and it hides precisely the races the determinism gate exists
 * to find.
 */
export class BasePage {
  /**
   * @param {import('@playwright/test').Page} page
   * @param {string} path Route this page lives at, e.g. `/banks`.
   */
  constructor(page, path = '/') {
    this.page = page
    this.path = path
    this.baseURL = CLIENT_URL
  }

  /** Navigate to this page's own route. */
  async open() {
    await this.page.goto(`${this.baseURL}${this.path}`)
    await this.expectLoaded()
    return this
  }

  /**
   * Assert this page is actually the one on screen.
   *
   * Subclasses OVERRIDE this with an assertion about their own content -- a
   * heading, a landmark, a table caption. The default only checks the URL,
   * which is deliberately weak: a route that renders a blank page still has the
   * right URL. A page object that does not override this is asserting
   * navigation rather than arrival, and that is exactly the scenario that stays
   * green when the page it tests is broken.
   */
  async expectLoaded() {
    // Compared as a parsed pathname rather than by pattern-matching the whole
    // URL, so a route containing regex-significant characters needs no escaping
    // and a query string or fragment does not change the answer.
    await expect(this.page).toHaveURL((url) => trimTrailingSlash(url.pathname) === trimTrailingSlash(this.path))
  }



  /**
   * Every data row of the screen's table.
   *
   * Both list screens that page their data render one `<tbody>` and nothing
   * else in it, so this is the same locator in both -- and it is here rather
   * than twice because the two copies had already been written identically,
   * which is how they start to drift.
   */
  rows() {
    return this.page.locator('tbody tr')
  }

  /**
   * The pager's summary line -- "Showing 1–68 of 68 · Page 1 of 1".
   *
   * 🔑 THE ONE PLACE THE SUMMARY IS RECOGNISED. Every list screen shares the design client's
   * pager, and a second copy of this pattern in a subclass is how one of them came to reject a
   * grouped count while this one accepted it. `PAGER_SUMMARY` says what it tolerates.
   */
  summary() {
    return this.page.getByText(PAGER_SUMMARY)
  }

  /**
   * The record count the screen reports, parsed from the pager's summary.
   *
   * 🔑 Read from the SUMMARY, never by counting rows: every list screen here paginates, so a
   * row count is a count of one page and silently becomes wrong the day the data outgrows
   * the page size.
   *
   * The noun this method used to be told was removed rather than kept as an argument that
   * nothing reads -- the design client's pager names none. The digits are read with their
   * grouping separators and the separators then dropped.
   */
  async reportedCount() {
    const summary = this.summary()
    await expect(summary).toBeVisible()
    const digits = new RegExp(`of (${GROUPED_INTEGER})`).exec(await summary.textContent())?.[1] ?? ''
    return Number(digits.replace(/\D/g, '') || NaN)
  }
}

/**
 * An integer as the pager prints it: grouped the South African way once it reaches
 * thousands, with a space the browser may render as a no-break or narrow no-break
 * space -- "2 418".
 */
const GROUPED_INTEGER = '\\d[\\d\\s\\u00a0\\u202f]*'

/**
 * 🔴 THE DESIGN CLIENT'S PAGER NAMES NO NOUN. It reads "Showing 1–68 of 68 · Page 1 of 1":
 * an EN DASH rather than a hyphen (a hyphen is still accepted), no "banks" or "members" after
 * the count, and grouped digits. While a list loads it says "Loading <noun>…" and when nothing
 * matches "No <noun> match" INSTEAD, so this pattern not matching is itself an answer.
 */
export const PAGER_SUMMARY = new RegExp(
  `Showing ${GROUPED_INTEGER}[\\u2013-]${GROUPED_INTEGER} of ${GROUPED_INTEGER}`,
)

/** `/banks/` and `/banks` are the same route; only `/` keeps its slash. */
function trimTrailingSlash(value) {
  return value.length > 1 && value.endsWith('/') ? value.slice(0, -1) : value
}

/**
 * Match a control by the label above it, tolerating what the markup adds.
 *
 * 🔴 THE SURROUNDING WHITESPACE IS LOAD-BEARING, and it cost a run to find. The
 * application's field labels render as `{label} {required && '*'}`, so a
 * REQUIRED field's text is "Surname *" and an OPTIONAL one's is "Title " --
 * with a trailing space, left by the separator that has nothing after it.
 * Playwright does NOT trim label text before matching a regular expression, so
 * `/^Title$/` matches nothing on a screen that plainly shows a field called
 * Title, and the failure reads as a missing element rather than as a stray
 * space.
 *
 * The alternative -- writing "Title " and "Surname *" into the feature files --
 * would put the markup's accidents into the sentences a non-engineer reads.
 *
 * 🔑 IT LIVES HERE, ONCE. It was written three times before this pass -- twice
 * in page objects and once in a step -- which is the same drift the destructive
 * guards are kept in one module to avoid: three implementations of one rule
 * disagree eventually, and the one that disagrees quietly is the one that
 * matters.
 */
export function labelPattern(label) {
  const escaped = label.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
  // 🔴 AND A REQUIRED FIELD ALSO CARRIES "(required)". The design client's `Field` marks a
  // required control with an `aria-hidden` asterisk AND a screen-reader-only "(required)", and
  // Playwright reads both into the label: "Surname *(required)". Tolerated here, once, for
  // the same reason the asterisk is -- it is the markup's, not the sentence a reader writes.
  return new RegExp(`^\\s*${escaped}\\s*\\*?\\s*(?:\\(required\\))?\\s*$`)
}
