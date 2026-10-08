// @vitest-environment jsdom -- drives Maintenance > Positions over MSW
import { describe, expect, it } from 'vitest'
import { screen, waitFor, within } from '@testing-library/react'
import { http, HttpResponse } from 'msw'

import Positions from './Positions'
import { PERMISSIONS } from '../../auth/permissions'
import { expectNoA11yViolations } from '../../test/assertions'
import { TestAuthProvider } from '../../test/authTestUtils'
import { refusal } from '../../test/authFixtures'
import { server } from '../../test/msw/server'
import { renderWithProviders } from '../../test/renderWithProviders'
import { setupUser } from '../../test/user'

/**
 * *Maintenance → Positions*: SASBO's positions and the role each gives a new user -- provisional until Stephne's list
 * arrives, and replaced as data (permissions.md).
 */
const POSITIONS = [
  { code: 'ORGANISER', name: 'Organiser', defaultRoleCode: 'ORGANISER', active: true, ordinal: 6, version: 2 },
  { code: 'SYSTEM_ADMINISTRATOR', name: 'System Administrator', defaultRoleCode: 'SYSTEM_ADMINISTRATOR', active: true, ordinal: 1 },
]
const ROLES = [
  { code: 'SYSTEM_ADMINISTRATOR', name: 'System Administrator', permissions: ['USERS_ADMIN'] },
  { code: 'ORGANISER', name: 'Organiser role', permissions: ['MEMBERS_VIEW'] },
]

const renderPositions = ({ positions = () => HttpResponse.json(POSITIONS), permissions = [PERMISSIONS.USERS_ADMIN] } = {}) => {
  server.use(http.get('*/api/admin/positions', positions), http.get('*/api/admin/roles', () => HttpResponse.json(ROLES)))
  return renderWithProviders(<TestAuthProvider permissions={permissions}><Positions /></TestAuthProvider>)
}

describe('Maintenance > Positions', () => {
  it('lists the positions in SASBO\'s order, each with the name of its default role', async () => {
    renderPositions()
    const buttons = await screen.findAllByRole('button', { name: /^Change / })
    expect(buttons.map((b) => b.textContent)).toEqual(['System Administrator', 'Organiser'])
    expect(buttons[1].closest('tr').querySelector('[data-col="defaultRoleCode"]')).toHaveTextContent('Organiser role')
  })

  it('a failed read says so, never "no positions"', async () => {
    renderPositions({ positions: () => HttpResponse.json({ message: 'boom' }, { status: 500 }) })
    expect(await screen.findByText('The positions did not load, so this is not an empty list.')).toBeInTheDocument()
    expect(screen.queryByText('No position is on record yet.')).not.toBeInTheDocument()
  })

  it('an empty list says so', async () => {
    renderPositions({ positions: () => HttpResponse.json([]) })
    expect(await screen.findByText('No position is on record yet.')).toBeInTheDocument()
  })

  it('is refused, and asks nothing, without USERS_ADMIN', () => {
    renderPositions({ permissions: [PERMISSIONS.AUDIT_VIEW] })
    expect(screen.getByRole('status')).toHaveTextContent('You need permission to manage users.')
  })

  it('changes a position: the code stays, the rest is sent, and the list says it is saved', async () => {
    const user = setupUser()
    const sent = []
    server.use(http.put('*/api/admin/positions/ORGANISER', async ({ request }) => {
      sent.push(await request.json())
      return HttpResponse.json({})
    }))
    renderPositions()
    await user.click(await screen.findByRole('button', { name: 'Change Organiser' }))
    const dialog = screen.getByRole('dialog')
    expect(within(dialog).queryByLabelText(/^Code/)).not.toBeInTheDocument()
    await user.click(within(dialog).getByRole('checkbox', { name: /Offered when creating a user/ }))
    await user.click(within(dialog).getByRole('button', { name: 'Save changes' }))

    expect(await screen.findByText('Organiser is saved.')).toBeInTheDocument()
    expect(sent).toEqual([{ code: 'ORGANISER', name: 'Organiser', defaultRoleCode: 'ORGANISER', active: false, ordinal: 6, version: 2 }])
  })

  it('a new position needs a code in capitals, and a refusal is said in the server\'s words inside the dialog', async () => {
    const user = setupUser()
    server.use(http.post('*/api/admin/positions', () => refusal(409, 'POSITION_CODE_TAKEN', 'There is already a position with that code.')))
    renderPositions()
    await user.click(await screen.findByRole('button', { name: /Add a position/ }))
    const dialog = screen.getByRole('dialog')
    await waitFor(() => expect(within(dialog).getByRole('option', { name: 'Organiser role' })).toBeInTheDocument())

    await user.type(within(dialog).getByLabelText(/^Name/), 'Regional Secretary')
    await user.type(within(dialog).getByLabelText(/^Code/), 'regional_secretary')
    expect(within(dialog).getByLabelText(/^Code/)).toHaveValue('REGIONAL_SECRETARY')
    await user.selectOptions(within(dialog).getByLabelText(/^Default role/), 'ORGANISER')
    await user.click(within(dialog).getByRole('button', { name: 'Add position' }))

    expect(await within(dialog).findByText('There is already a position with that code.')).toBeInTheDocument()
  })

  it('has no violations on the list and in the dialog', async () => {
    const user = setupUser()
    const { container } = renderPositions()
    await screen.findByRole('button', { name: 'Change Organiser' })
    await expectNoA11yViolations(container)
    await user.click(screen.getByRole('button', { name: 'Change Organiser' }))
    await expectNoA11yViolations(screen.getByRole('dialog'), { rules: { region: { enabled: false } } })
  })
})
