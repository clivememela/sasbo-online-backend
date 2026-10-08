// @vitest-environment jsdom -- drives Maintenance > Roles over MSW
import { describe, expect, it } from 'vitest'
import { screen, within } from '@testing-library/react'
import { http, HttpResponse } from 'msw'

import Roles from './Roles'
import { PERMISSIONS } from '../../auth/permissions'
import { expectNoA11yViolations } from '../../test/assertions'
import { TestAuthProvider } from '../../test/authTestUtils'
import { server } from '../../test/msw/server'
import { renderWithProviders } from '../../test/renderWithProviders'
import { setupUser } from '../../test/user'

/**
 * *Maintenance → Roles*: what each role may do, as a checklist of the server's permission catalogue, each with the
 * server's own sentence (`GET /api/admin/permissions`).
 */
const CATALOGUE = [
  { name: 'MEMBERS_VIEW', description: 'See a listing that names members, within the user\'s region or area' },
  { name: 'AUDIT_VIEW', description: 'Read the record of who did what' },
]
const ROLES = [
  { code: 'ORGANISER', name: 'Organiser', permissions: ['MEMBERS_VIEW'], fixed: false, version: 4 },
  { code: 'SYSTEM_ADMINISTRATOR', name: 'System Administrator', permissions: ['MEMBERS_VIEW', 'AUDIT_VIEW'], fixed: true, version: 1 },
]

const renderRoles = ({ catalogue = () => HttpResponse.json(CATALOGUE), roles = () => HttpResponse.json(ROLES) } = {}) => {
  server.use(http.get('*/api/admin/roles', roles), http.get('*/api/admin/permissions', catalogue))
  return renderWithProviders(<TestAuthProvider permissions={[PERMISSIONS.USERS_ADMIN]}><Roles /></TestAuthProvider>)
}

describe('Maintenance > Roles', () => {
  it('lists each role with how many of the catalogue\'s permissions it holds', async () => {
    renderRoles()
    const row = (await screen.findByRole('button', { name: 'Change Organiser' })).closest('tr')
    expect(row.querySelector('[data-col="permissions"]')).toHaveTextContent('1 of 2 permissions')
  })

  it('a failed read says so, never "no roles"', async () => {
    renderRoles({ roles: () => HttpResponse.json({ message: 'boom' }, { status: 500 }) })
    expect(await screen.findByText('The roles did not load, so this is not an empty list.')).toBeInTheDocument()
  })

  it('changes a role\'s permissions from a checklist that says what each one lets a user do', async () => {
    const user = setupUser()
    const sent = []
    server.use(http.put('*/api/admin/roles/ORGANISER', async ({ request }) => {
      sent.push(await request.json())
      return HttpResponse.json({})
    }))
    renderRoles()
    await user.click(await screen.findByRole('button', { name: 'Change Organiser' }))
    const dialog = screen.getByRole('dialog')
    const audit = within(dialog).getByRole('checkbox', { name: /Read the record of who did what/ })
    expect(within(dialog).getByRole('checkbox', { name: /See a listing that names members/ })).toBeChecked()
    expect(audit).not.toBeChecked()

    await user.click(audit)
    await user.click(within(dialog).getByRole('button', { name: 'Save changes' }))

    expect(await screen.findByText('Organiser is saved.')).toBeInTheDocument()
    // With the version it was read at, so a change made over somebody else's is refused (STALE_VERSION).
    expect(sent).toEqual([{ code: 'ORGANISER', name: 'Organiser', permissions: ['MEMBERS_VIEW', 'AUDIT_VIEW'], version: 4 }])
  })

  it('the System Administrator\'s role keeps every permission: said, not offered -- only its name changes', async () => {
    const user = setupUser()
    const sent = []
    server.use(http.put('*/api/admin/roles/SYSTEM_ADMINISTRATOR', async ({ request }) => {
      sent.push(await request.json())
      return HttpResponse.json({})
    }))
    renderRoles()
    await user.click(await screen.findByRole('button', { name: 'Change System Administrator' }))
    const dialog = screen.getByRole('dialog')
    expect(within(dialog).getByText(/holds every permission, and they are not changed here/)).toBeInTheDocument()
    expect(within(dialog).queryByRole('checkbox')).not.toBeInTheDocument()

    const name = within(dialog).getByLabelText(/^Name/)
    await user.clear(name)
    await user.type(name, 'Systems Administrator')
    await user.click(within(dialog).getByRole('button', { name: 'Save changes' }))
    expect(await screen.findByText('Systems Administrator is saved.')).toBeInTheDocument()
    expect(sent).toEqual([{ code: 'SYSTEM_ADMINISTRATOR', name: 'Systems Administrator', permissions: ['MEMBERS_VIEW', 'AUDIT_VIEW'], version: 1 }])
  })

  it('a catalogue that did not load says so inside the dialog, with a retry', async () => {
    const user = setupUser()
    renderRoles({ catalogue: () => HttpResponse.json({ message: 'boom' }, { status: 500 }) })
    await user.click(await screen.findByRole('button', { name: 'Change Organiser' }))
    expect(within(screen.getByRole('dialog')).getByText('The permissions could not be loaded.')).toBeInTheDocument()
  })

  it('has no violations on the list and in the dialog', async () => {
    const user = setupUser()
    const { container } = renderRoles()
    await screen.findByRole('button', { name: 'Change Organiser' })
    await expectNoA11yViolations(container)
    await user.click(screen.getByRole('button', { name: 'Change Organiser' }))
    await expectNoA11yViolations(screen.getByRole('dialog'), { rules: { region: { enabled: false } } })
  })
})
