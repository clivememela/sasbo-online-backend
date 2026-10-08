// @vitest-environment jsdom -- the rename, held where a person reads it: the rail, the crumbs, the pages' heads and cards
import { afterAll, afterEach, beforeAll, beforeEach, describe, expect, it } from 'vitest'
import { fireEvent, screen, waitFor, within } from '@testing-library/react'

import App from '../App'
import Sidebar from '../components/Sidebar'
import Home from '../pages/Home'
import { navItems } from '../config/navigation'
import { renderWithProviders } from './renderWithProviders'
import { server } from './msw/server'
import { smokeHandlers } from './msw/smokeLayer'
import { warmSplitPages } from './splitPages'
import { setupUser } from './user'

/**
 * 🗣️ "A.C.B." IS "DEBIT ORDER DEDUCTIONS" WHEREVER A PERSON READS IT -- SASBO at the 5 Oct Show & Tell (handover §A),
 * and Stephne's DOD answer: "ACB" becomes "Debit Order" everywhere, the screens keep their names for now. The address
 * `/admin/acb` stays, and so do the code's names; what a person reads is what this holds.
 *
 * 🔑 HELD ON THE RENDERED PAGE, NOT ON THE SOURCE. The words a person meets are composed -- the rail from `navigation.js`,
 * the crumbs from the address and the rail together, each page its own head and cards -- so the rail is opened section
 * by section until nothing is left closed, the routes that lead to and through the module are mounted whole, and
 * everything readable is searched: the text, and the names, tooltips and placeholders a screen reader or a pointer
 * finds. The canary below proves the search convicts each spelling the client carried, and acquits the new name.
 */
const OLD_NAME = /\bA\.?\s?C\.?\s?B\b|clearing bureau/i

/** SASBO's name for the module, and the short one the rail and the crumbs print (`navigation.js`). */
const MODULE = 'Debit Order Deductions'
const RAIL = 'Debit Orders'

/** Everything on the page a person can read or hear. */
const readable = () => [
  document.body.textContent,
  ...[...document.body.querySelectorAll('[aria-label], [title], [placeholder], [alt]')].flatMap((element) =>
    ['aria-label', 'title', 'placeholder', 'alt'].map((name) => element.getAttribute(name) ?? '')),
].join('\n')

/**
 * Opens every section of the rail, the nested ones as they appear. A plain click event, not a simulated pointer, on the
 * rail alone: what is held here is what the open rail says -- the disclosures' own keyboard and pointer are
 * `shell.test.jsx`'s -- and opening it inside the whole application re-rendered every page a dozen times over.
 */
const openTheWholeRail = () => {
  const rail = screen.getByRole('navigation', { name: 'Main' })
  for (let opened = 0; opened < 40; opened += 1) {
    const next = within(rail).queryAllByRole('button', { name: /^Expand / })[0]
    if (!next) return rail
    fireEvent.click(next)
  }
  throw new Error('the rail kept offering sections to open: a disclosure that never opens is a defect of its own')
}

/** The rail's entries, all of them, as `navigation.js` declares them -- the population the open rail must show. */
const everyEntry = (items) => items.flatMap((item) => [item, ...everyEntry(item.subItems ?? [])])

await warmSplitPages()

/**
 * The routes that lead to the module and through it. ⏳ ONE IS MISSING ON PURPOSE: the module's own page, `/admin/acb`
 * (`Acb.jsx`), is retitled with its new cards by the Debit Order Deductions screens' work, not here -- it joins this
 * list in the commit that brings that work together with this one.
 */
const ROUTES = [
  '/home',
  '/admin',
  '/admin/acb/runs',
  '/admin/acb/debits',
  '/admin/monthly-menu',
  '/admin/maintenance',
  '/admin/maintenance/subscriptions',
]

const mount = async (path) => {
  renderWithProviders(<App />, { initialEntries: [path] })
  await waitFor(() => expect(screen.queryByTestId('route-pending')).not.toBeInTheDocument(), { timeout: 5000 })
  await screen.findByRole('heading', { level: 1 })
}

/*
 * 🔑 A ROUTE'S READS ARE ALL ANSWERED BEFORE ITS TEST ENDS. Month end reads its figures only once its months have
 * answered, so a test could end with that read still to come -- and it would reach MSW after these handlers were
 * reset, as an unhandled request that belongs to no test. The figures are waited for by name, then every read in flight.
 */
const inFlight = new Set()
const started = ({ requestId }) => inFlight.add(requestId)
const ended = ({ requestId }) => inFlight.delete(requestId)
beforeAll(() => {
  server.events.on('request:start', started)
  server.events.on('request:end', ended)
})
afterAll(() => {
  server.events.removeListener('request:start', started)
  server.events.removeListener('request:end', ended)
})
afterEach(async () => {
  const figures = screen.queryByRole('region', { name: 'The month’s figures' })
  if (figures) await waitFor(() => expect(figures).not.toHaveAttribute('data-figures', 'loading'))
  await waitFor(() => expect(inFlight.size, 'a read is still out').toBe(0))
})

describe('"Debit Order Deductions", never "A.C.B." (the 5 Oct Show & Tell; Stephne, DOD)', () => {
  beforeEach(() => {
    server.use(...smokeHandlers)
  })

  it('the canary: the search convicts every spelling the client carried, and acquits the new names', () => {
    for (const old of ['A.C.B', 'A.C.B. Runs', 'A.C.B Menu', 'ACB Debit Day', 'Deduct via ACB', '(A.C.B.)',
      'Members matching the ACB search', 'Automated clearing bureau debits and returns']) {
      expect(OLD_NAME.test(old), old).toBe(true)
    }
    for (const now of [MODULE, RAIL, 'Debit Order Runs', 'Debit Order Day', 'Feedback',
      'Deduct Subs through Debit Order (Y/N)']) {
      expect(OLD_NAME.test(now), now).toBe(false)
    }
  })

  it('the rail, every section open: nothing readable says A.C.B., and the module is on it twice, at its address', () => {
    renderWithProviders(<Sidebar sideBarOpen setSideBarOpen={() => {}} selectedCardHref="" />, { initialEntries: ['/home'] })
    const rail = openTheWholeRail()

    // Non-vacuity: the rail really is open -- every entry `navigation.js` declares is on it -- and the module is on it
    // under its new name in both places it sits, at the address it always had.
    expect(within(rail).getAllByRole('link').length + within(rail).queryAllByTitle('Not available yet').length)
      .toBeGreaterThanOrEqual(everyEntry(navItems).length)
    const entries = within(rail).getAllByRole('link', { name: RAIL })
    expect(entries).toHaveLength(2)
    for (const entry of entries) expect(entry).toHaveAttribute('href', '/admin/acb')

    expect(readable()).not.toMatch(OLD_NAME)
  })

  it.each(ROUTES)('%s: nothing readable says A.C.B. -- the page, its crumbs and the rail as it opens there', async (path) => {
    await mount(path)

    expect(readable()).not.toMatch(OLD_NAME)
  })

  it('Debit Order Runs: the screen named Runs, under the module\'s new name in the crumbs', async () => {
    await mount('/admin/acb/runs')
    expect(screen.getByRole('heading', { level: 1, name: 'Debit Order Runs' })).toBeInTheDocument()
    expect(screen.getByRole('navigation', { name: 'Breadcrumb' }))
      .toHaveTextContent(new RegExp(`^Home\\s*Admin Menu\\s*${RAIL}\\s*Runs$`))
  })

  it('Home, in the list and the table too: the module is "Debit Order Deductions" in every view', async () => {
    const user = setupUser()
    renderWithProviders(<Home />)

    for (const view of ['List', 'Table']) {
      await user.click(screen.getByRole('button', { name: view }))
      expect(screen.getByRole('link', { name: new RegExp(`^${MODULE}`) })).toHaveAttribute('href', '/admin/acb')
      expect(readable(), `Home's ${view} view`).not.toMatch(OLD_NAME)
    }
  })
})
