// @vitest-environment jsdom -- the maintenance menu's cards, and where each one goes
import { describe, expect, it } from 'vitest'
import { screen, within } from '@testing-library/react'

import App from '../../App'
// The route's chunk, in the registry before the test, so the wait below times the page and not this box's first import
// of it (`splitPages.js` records why).
import '../maintainance/Subscriptions.jsx'
import Maintenance from './Maintenance'
import { navItems } from '../../config/navigation'
import { PERMISSIONS } from '../../auth/permissions'
import { menuPaths } from '../home/homeModules'
import { renderWithProviders } from '../../test/renderWithProviders'
import { server } from '../../test/msw/server'
import { smokeHandlers } from '../../test/msw/smokeLayer'

/**
 * 🗣️ SASBO, 18 Sep 2026: "in maintenance there was an option where we could edit the member" -- gone since the menus
 * were rebuilt -- and Bank/Branch was "not linking. It's not active."
 *
 * Neither needed a new screen. A member is edited on their own record (the register at /members: open the member, then
 * Edit), and /banks has had working add and edit since before this arc; the Bank/Branch card had just never been given
 * an href, so `NavigationCard` rendered it as a quiet label that went nowhere. Both cards now go where the work is
 * done, and the sidebar's Maintenance entries say the same as this page -- two lists that are meant to agree, so the
 * last case holds them together.
 */

/** The sidebar's Admin Menu > Maintenance entries. */
const sidebarMaintenance = () => {
  const admin = navItems.find((item) => item.title === 'Admin Menu')
  return admin.subItems.find((item) => item.title === 'Maintenance').subItems
}

describe('the Maintenance menu', () => {
  it('offers Edit Member, which opens the member register where a record is edited', () => {
    renderWithProviders(<Maintenance />)

    expect(screen.getByRole('link', { name: /Edit Member/ })).toHaveAttribute('href', '/members')
  })

  it('links Bank/Branch to /banks, where banks and branches are added and edited', () => {
    renderWithProviders(<Maintenance />)

    expect(screen.getByRole('link', { name: /Bank\/Branch/ })).toHaveAttribute('href', '/banks')
  })

  it('keeps Recruitment Channel and Subscriptions going where they went', () => {
    renderWithProviders(<Maintenance />)

    expect(screen.getByRole('link', { name: /Recruitment Channel/ }))
      .toHaveAttribute('href', '/admin/maintenance/recruitment-channel')
    expect(screen.getByRole('link', { name: /Subscriptions/ })).toHaveAttribute('href', '/admin/maintenance/subscriptions')
  })

  it('offers Workplaces on the page and in the rail alike -- the one screen of SASBO\'s workplaces (01.5, MR-ROB-37)', () => {
    renderWithProviders(<Maintenance />)

    // Written twice -- a card here, a leaf in the rail -- so both halves are asserted by name, not only by count.
    expect(screen.getByRole('link', { name: /^Workplaces/ })).toHaveAttribute('href', '/admin/maintenance/workplaces')
    expect(sidebarMaintenance().find((entry) => entry.title === 'Workplaces')?.href).toBe('/admin/maintenance/workplaces')
  })

  it('offers sign-in\'s users, positions, roles and record (5 Oct), each naming the permission its screen needs', () => {
    const SIGN_IN = [
      ['Users', '/admin/maintenance/users', PERMISSIONS.USERS_ADMIN],
      ['Positions', '/admin/maintenance/positions', PERMISSIONS.USERS_ADMIN],
      ['Roles', '/admin/maintenance/roles', PERMISSIONS.USERS_ADMIN],
      ['Who did what', '/admin/maintenance/audit', PERMISSIONS.AUDIT_VIEW],
    ]
    renderWithProviders(<Maintenance />)
    for (const [title, href, permission] of SIGN_IN) {
      expect(screen.getByRole('link', { name: new RegExp(`^${title}`) })).toHaveAttribute('href', href)
      // The rail's entry is the same object, carrying the key the menu rule (navAccess.js, OB18) marks it by.
      expect(sidebarMaintenance().find((entry) => entry.title === title)).toMatchObject({ href, permission })
    }
  })

  it('🗣️ Subscriptions is reached through Maintenance alone -- off the rail\'s top level, and so off Home (the 5 Oct Show & Tell)', () => {
    // Only staff change the amounts, so it is a table Maintenance keeps, not a module of its own: the one way to it in
    // the menu is Admin Menu > Maintenance, and Home -- the rail's top level read across -- no longer offers it.
    expect(menuPaths('/admin/maintenance/subscriptions')).toEqual([['Admin Menu', 'Maintenance', 'Subscriptions']])
    expect(navItems.some((item) => item.href === '/admin/maintenance/subscriptions')).toBe(false)

    renderWithProviders(<Maintenance />)
    expect(screen.getByRole('link', { name: /Subscriptions/ })).toHaveAttribute('href', '/admin/maintenance/subscriptions')
  })

  it('on Subscriptions itself, the rail and the crumbs say where it lives: Admin Menu › Maintenance › Subscriptions', async () => {
    server.use(...smokeHandlers)
    renderWithProviders(<App />, { initialEntries: ['/admin/maintenance/subscriptions'] })

    expect(await screen.findByRole('heading', { level: 1, name: 'Subscriptions' }, { timeout: 5000 })).toBeInTheDocument()
    const rail = screen.getByRole('navigation', { name: 'Main' })
    // One entry, under Maintenance, open on arrival and marked as the page -- not a second one at the top of the rail.
    expect(within(rail).getByRole('link', { name: 'Subscriptions' })).toHaveAttribute('aria-current', 'page')
    expect(within(rail).getByRole('button', { name: 'Collapse Maintenance' })).toHaveAttribute('aria-expanded', 'true')
    expect(screen.getByRole('navigation', { name: 'Breadcrumb' }))
      .toHaveTextContent(/^Home\s*Admin Menu\s*Maintenance\s*Subscriptions$/)
  })

  it('says the same in the sidebar as on the page -- every entry a link, to the same place', () => {
    renderWithProviders(<Maintenance />)
    const onPage = screen.getAllByRole('link').map((link) => [link.textContent, link.getAttribute('href')])

    const inSidebar = sidebarMaintenance()
    expect(inSidebar.length, 'the sidebar lost or gained a Maintenance entry the page does not have').toBe(onPage.length)
    for (const entry of inSidebar) {
      expect(entry.href, `${entry.title} has no href in the sidebar, so it is a label that goes nowhere`).toBeTruthy()
      expect(onPage.some(([text, href]) => text.includes(entry.title) && href === entry.href)).toBe(true)
    }
  })
})
