// @vitest-environment jsdom -- Home: its modules in three views, the switch between them, and what it remembers
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { useState } from 'react'
import { act, screen, within } from '@testing-library/react'
import { Outlet, Route, Routes } from 'react-router-dom'

import Home from './Home'
import { navItems } from '../config/navigation'
import { HOME_MODULES, HOME_VIEW_KEY } from './home/homeModules'
import { summaryService } from '../services/summaryService'
import { server } from '../test/msw/server'
import { renderWithProviders } from '../test/renderWithProviders'
import { setupUser } from '../test/user'

/**
 * 🗣️ THE 5 OCT SHOW & TELL'S HOME (handover §A): the month's figures moved to Month end, Home keeps only the modules,
 * and a switch lays them out as cards, a list or a table. Natalie liked the cards, so they are what a browser that has
 * chosen nothing opens on; the choice is remembered per browser (`localStorage['sasbo.homeView']`), and a browser
 * whose storage throws still renders and still switches. Subscriptions is not a module any more -- it lives under
 * Maintenance.
 */

/** SASBO's name for the module once called A.C.B. (5 Oct), and the short one the rail prints. */
const MODULE = 'Debit Order Deductions'
const RAIL = 'Debit Orders'

/** Every request Home makes -- there should be none: the figures, and their reads, went to Month end. */
let requests
const record = ({ request }) => requests.push(new URL(request.url).pathname)
beforeEach(() => {
  requests = []
  server.events.on('request:start', record)
})
afterEach(() => {
  server.events.removeListener('request:start', record)
})

const view = () => document.querySelector('[data-home-view]').getAttribute('data-home-view')
const choice = (name) => within(screen.getByRole('group', { name: 'Show the modules as' })).getByRole('button', { name })
const pressed = () => ['Cards', 'List', 'Table'].filter((name) => choice(name).getAttribute('aria-pressed') === 'true')
/** Every module's link, by where it goes -- in whichever view is showing. */
const moduleHrefs = () => screen.getAllByRole('link').map((link) => link.getAttribute('href'))

describe('Home keeps only the modules (the 5 Oct Show & Tell)', () => {
  it('🔴 no figures, and nothing asked of the server -- not on the first paint, in no view, and not once every import has settled', async () => {
    // Two instruments: the service the figures read through, called in the very effect that reads, and every request
    // any module sends.
    const summaryRead = vi.spyOn(summaryService, 'getSummary')
    const user = setupUser()
    renderWithProviders(<Home />)

    expect(screen.getByRole('heading', { level: 1, name: 'Welcome to SASBO Online' })).toBeInTheDocument()
    expect(screen.queryByTestId('figure-strip')).not.toBeInTheDocument()
    expect(screen.queryByRole('region', { name: /figures/i })).not.toBeInTheDocument()
    for (const word of ['Paying members', 'Penetration', 'Members on record']) {
      expect(screen.queryByText(word), `${word} is a figure, and the figures moved to Month end`).not.toBeInTheDocument()
    }

    // 🔑 NOT ONE TICK AFTER THE FIRST PAINT, WHICH A READ BEHIND A LAZY IMPORT WOULD OUTLAST (review-home): every view
    // opened and back, every dynamic import settled, and one more turn of the event loop for the effects they bring.
    for (const name of ['List', 'Table', 'Cards']) {
      await user.click(choice(name))
      expect(view()).toBe(name.toLowerCase())
    }
    await vi.dynamicImportSettled()
    await act(() => new Promise((resolve) => setTimeout(resolve, 0)))
    expect(summaryRead).not.toHaveBeenCalled()
    expect(requests).toEqual([])
  })

  it('🔑 the modules are the rail\'s own areas, read across -- Home drops only its own entry', () => {
    renderWithProviders(<Home />)

    const rail = navItems.filter((item) => item.href && item.href !== '/home').map((item) => item.href)
    expect(moduleHrefs()).toEqual(rail)
    expect(HOME_MODULES.map((entry) => entry.href)).toEqual(rail)
    // The must-pass: every module has its line, and every line is a module's.
    for (const entry of HOME_MODULES) expect(entry.desc, `${entry.title} has no description`).toBeTruthy()
  })

  it('🗣️ Subscriptions is not a module any more: only staff change the amounts, so it lives under Maintenance', () => {
    renderWithProviders(<Home />)

    expect(screen.queryByText('Subscriptions')).not.toBeInTheDocument()
    expect(moduleHrefs()).not.toContain('/admin/maintenance/subscriptions')
  })

  it('🗣️ the module once called A.C.B. is "Debit Order Deductions", at the address it always had', () => {
    renderWithProviders(<Home />)

    expect(screen.getByRole('link', { name: new RegExp(`^${MODULE}`) })).toHaveAttribute('href', '/admin/acb')
    expect(document.body.textContent).not.toMatch(/\bA\.?C\.?B\b|clearing bureau/i)
  })
})

describe('the view switch -- cards, a list or a table', () => {
  it('opens on the cards when the browser has chosen nothing: Natalie\'s choice', () => {
    renderWithProviders(<Home />)

    expect(view()).toBe('cards')
    expect(pressed()).toEqual(['Cards'])
    // Cards: each module a card of its own, the whole card the link -- its name and its line inside it.
    for (const entry of HOME_MODULES) {
      const card = screen.getByRole('link', { name: new RegExp(`^${entry.title}`) })
      expect(card).toHaveTextContent(entry.desc)
      expect(card).toHaveClass('rounded-lg', 'bg-surface')
    }
  })

  it('each view shows the same modules -- the list as the ruled rows, the table with what each is for and where it sits in the menu', async () => {
    const user = setupUser()
    renderWithProviders(<Home />)
    const cards = moduleHrefs()

    await user.click(choice('List'))
    expect(view()).toBe('list')
    expect(pressed()).toEqual(['List'])
    expect(moduleHrefs()).toEqual(cards)
    expect(screen.queryByRole('table')).not.toBeInTheDocument()

    await user.click(choice('Table'))
    expect(view()).toBe('table')
    expect(pressed()).toEqual(['Table'])
    const table = screen.getByRole('table', { name: 'The modules of SASBO Online' })
    expect(within(table).getAllByRole('columnheader').map((head) => head.textContent))
      .toEqual(expect.arrayContaining(['Module', 'What it is for', 'In the menu']))
    expect(moduleHrefs()).toEqual(cards)
    // Where each sits in the menu, read from the same tree as the rail: every path to it, and what is under it there.
    const members = screen.getByRole('link', { name: 'Members' }).closest('tr')
    expect(members).toHaveTextContent('The membership register across every bank')
    expect(members).toHaveTextContent('Admin Menu › Daily Menu › Members')
    expect(members).toHaveTextContent('Under it: Capture Member, Labels')
    const debitOrders = screen.getByRole('link', { name: MODULE }).closest('tr')
    expect(debitOrders).toHaveTextContent(`Admin Menu › ${RAIL}`)
    expect(debitOrders).toHaveTextContent('Under it: Debits, Runs')

    await user.click(choice('Cards'))
    expect(view()).toBe('cards')
    expect(moduleHrefs()).toEqual(cards)
  })

  it('is a named group of toggle buttons: Tab reaches every choice, Enter and Space choose, and the focus ring is the primitive\'s', async () => {
    const user = setupUser()
    renderWithProviders(<Home />)

    await user.tab()
    expect(choice('Cards')).toHaveFocus()
    await user.tab()
    expect(choice('List')).toHaveFocus()
    await user.keyboard('{Enter}')
    expect(view()).toBe('list')
    expect(pressed()).toEqual(['List'])

    await user.tab()
    expect(choice('Table')).toHaveFocus()
    await user.keyboard(' ')
    expect(view()).toBe('table')
    expect(pressed()).toEqual(['Table'])

    for (const name of ['Cards', 'List', 'Table']) {
      expect(choice(name)).toHaveAttribute('type', 'button')
      expect(choice(name)).toHaveClass('focus-visible:ring-2', 'focus-visible:ring-brand-ring')
    }
  })

  it('remembers the choice per browser: chosen once, it is the view the next visit opens on', async () => {
    const user = setupUser()
    const first = renderWithProviders(<Home />)

    await user.click(choice('Table'))
    // The exact key the presentation engine resets between screens, and no other.
    expect(HOME_VIEW_KEY).toBe('sasbo.homeView')
    expect(localStorage.getItem('sasbo.homeView')).toBe('table')
    expect(Object.keys(localStorage).sort()).toEqual(['darkMode', 'sasbo.homeView'])
    first.unmount()

    renderWithProviders(<Home />)
    expect(view()).toBe('table')
    expect(pressed()).toEqual(['Table'])
  })

  it('a stored value this build does not know opens on the cards -- never a blank page', () => {
    localStorage.setItem(HOME_VIEW_KEY, 'tiles')
    renderWithProviders(<Home />)

    expect(view()).toBe('cards')
    expect(moduleHrefs()).toHaveLength(HOME_MODULES.length)
  })

  it('🔴 storage that throws -- private mode, blocked site data: the page renders on the cards, and the switch still switches', async () => {
    const denied = () => {
      throw new DOMException('The operation is insecure.', 'SecurityError')
    }
    const read = vi.spyOn(Storage.prototype, 'getItem').mockImplementation(denied)
    const write = vi.spyOn(Storage.prototype, 'setItem').mockImplementation(denied)
    const user = setupUser()
    renderWithProviders(<Home />)

    expect(view()).toBe('cards')
    expect(read).toHaveBeenCalledWith(HOME_VIEW_KEY)
    await user.click(choice('List'))
    expect(view()).toBe('list')
    expect(write).toHaveBeenCalledWith(HOME_VIEW_KEY, 'list')
    expect(moduleHrefs()).toHaveLength(HOME_MODULES.length)
  })

  it.each([['Cards'], ['List'], ['Table']])('in the %s view, choosing a module tells the shell which one -- the rail opens it', async (name) => {
    // The shell's half, as `AppLayout` passes it: the card chosen becomes the rail's selected entry.
    function Shell() {
      const [selected, setSelected] = useState('')
      return (
        <>
          <Outlet context={{ setSelectedCardHref: setSelected }} />
          <output aria-label="selected">{selected}</output>
        </>
      )
    }
    const user = setupUser()
    renderWithProviders(
      <Routes>
        <Route element={<Shell />}>
          <Route path="/home" element={<Home />} />
          <Route path="*" element={<p>Arrived</p>} />
        </Route>
      </Routes>,
      { initialEntries: ['/home'] },
    )

    await user.click(choice(name))
    await user.click(screen.getByRole('link', { name: new RegExp(`^${MODULE}`) }))
    expect(await screen.findByText('Arrived')).toBeInTheDocument()
    expect(screen.getByRole('status', { name: 'selected' })).toHaveTextContent('/admin/acb')
  })
})
