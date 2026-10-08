import { expect } from '@playwright/test'
import { Given, When, Then } from '../support/fixtures.js'
import { captureAMemberOfItsOwn } from '../support/monthEndMember.js'
import { mintNumericTail } from '../support/guards.js'

/**
 * Register paging (server oct/register-paging 5ba805d, 98e9595; register row A10), held to the server's contract --
 * the register's screen keeps its view in its address, and is judged once the paging client is final.
 *
 * 🔁 NOTHING HERE COMPARES TWO READS OF A REGISTER OTHER JOURNEYS WRITE TO. Journeys run in parallel, and capture
 * members as they go: a total read twice can differ by a member captured in between. Every claim is judged within one
 * answer, or across pages the captures cannot reach -- a captured member's surname is minted `ZZ-AT-…`, so it sorts
 * after every seeded one, and the first pages in surname order stay as they were.
 */

/** The register's own columns: what a row carries, and nothing else (MemberRegisterPageDto.Row). */
const ROW_COLUMNS = ['bankName', 'branch', 'employeeNumber', 'firstNames', 'id', 'initials', 'membershipNumber', 'status',
  'surname', 'title']

When("the register's first two pages of {int} are asked for", async ({ world }, size) => {
  world.pageSize = size
  world.pages = [await world.membersApi.registerPage({ page: 0, size }), await world.membersApi.registerPage({ page: 1, size })]
})

Then('each page holds {int} members of one count, and no member is on both', async ({ world }, size) => {
  const [first, second] = world.pages
  expect(first.totalElements, 'the register holds more than one page of members').toBeGreaterThan(2 * size)
  for (const [index, answered] of world.pages.entries()) {
    expect({ page: answered.page, size: answered.size, rows: answered.content.length }, `page ${index + 1} as answered`)
      .toEqual({ page: index, size, rows: size })
    expect(answered.totalPages, `page ${index + 1}'s count of pages, from its own total`)
      .toBe(Math.ceil(answered.totalElements / size))
    expect(answered.sort, 'the register\'s own order, unless asked another').toMatch(/^surname/)
  }
  const onTheFirst = new Set(first.content.map((row) => row.id))
  expect(second.content.filter((row) => onTheFirst.has(row.id)).map((row) => row.id), 'members on both pages').toEqual([])
})

Then("each row carries only the register's own columns, and no identity, account or contact detail", async ({ world }) => {
  for (const row of world.pages.flatMap((answered) => answered.content)) {
    expect(Object.keys(row).sort(), `the columns of the row of member ${row.id}`).toEqual(ROW_COLUMNS)
  }
})

When("a page far past the register's last is asked for", async ({ world }) => {
  world.pageSize = 20
  world.answered = await world.membersApi.registerPage({ page: 100000, size: world.pageSize })
})

Then("the server answers the register's last page, with its members", async ({ world }) => {
  const { answered, pageSize } = world
  expect(answered.totalElements, 'the register holds members').toBeGreaterThan(0)
  expect(answered.page, 'the page answered: the last, counted from nought').toBe(answered.totalPages - 1)
  expect(answered.content.length, "the last page's members").toBe(answered.totalElements - answered.page * pageSize)
})

When("the register's figures are asked for", async ({ world }) => {
  world.overview = await world.membersApi.registerOverview()
})

Then("the figures count the register's members, each status and this month's within the total", async ({ world }) => {
  const { total, active, pending, suspended, newThisMonth, newDebitOrders, branches, asOf } = world.overview
  expect(total, 'every member in the register').toBeGreaterThan(0)
  expect(active + pending + suspended, 'Active, Pending and Suspended, within the total').toBeLessThanOrEqual(total)
  expect(newThisMonth, 'joined this month, within the total').toBeLessThanOrEqual(total)
  expect(newDebitOrders, "of this month's joiners, those paying by debit order").toBeLessThanOrEqual(newThisMonth)
  expect(new Set(branches).size, 'each branch offered once').toBe(branches.length)
  expect(asOf, 'the day the figures are of, in South Africa').toMatch(/^\d{4}-\d{2}-\d{2}$/)
})

// A member of the scenario's own: the gate repeats every scenario at once, and a shared record would race itself.
Given("a member of the scenario's own, read by two administrators at once", async ({ page, api, world }) => {
  const member = await captureAMemberOfItsOwn({ page, api, world })
  world.firstCopy = await world.membersApi.findMember(member.id)
  world.secondCopy = await world.membersApi.findMember(member.id)
  expect(world.secondCopy.version, 'the record carries the version it was read at').toBe(world.firstCopy.version)
})

When('the first administrator saves a change to that member', async ({ world }) => {
  world.firstPhone = `+27 82 ${mintNumericTail(6)}`
  world.firstSaved = await world.membersApi.attemptUpdate({ ...world.firstCopy, phone: world.firstPhone })
  expect(world.firstSaved.status, "the first administrator's save").toBe(200)
})

When('the second saves a change from the copy read before it', async ({ world }) => {
  world.secondSaved = await world.membersApi.attemptUpdate({ ...world.secondCopy, phone: `+27 83 ${mintNumericTail(6)}` })
})

Then('the second is refused because the member was saved since', async ({ world }) => {
  const { status, body } = world.secondSaved
  expect(status, 'a save made from a copy read before another save').toBe(409)
  expect(body?.code, "the refusal's code").toBe('MEMBER_CHANGED_SINCE')
})

Then("the member keeps the first administrator's change", async ({ world }) => {
  const now = await world.membersApi.findMember(world.firstCopy.id)
  expect(now.phone, 'the cellphone number on record').toBe(world.firstPhone)
  expect(now.version, 'saved once since it was read').not.toBe(world.firstCopy.version)
})
