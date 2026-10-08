// @vitest-environment jsdom -- drives Maintenance > Users over MSW and reads every state a person meets
import { describe, expect, it } from 'vitest'
import { screen, waitFor, within } from '@testing-library/react'
import { http, HttpResponse } from 'msw'

import Users from './Users'
import { PERMISSIONS } from '../../auth/permissions'
import { expectNoA11yViolations } from '../../test/assertions'
import { TestAuthProvider } from '../../test/authTestUtils'
import { refusal, userWith } from '../../test/authFixtures'
import { deferred } from '../../test/deferred'
import { monthEndAnswer } from '../../test/msw/monthEndExamples'
import { server } from '../../test/msw/server'
import { renderWithProviders } from '../../test/renderWithProviders'
import { setupUser } from '../../test/user'

/**
 * *Maintenance → Users* (contracts/signin.md): the 5 Oct ask -- "an administrator creating users with a position and an
 * area". Every person here is invented; every temporary password is a placeholder the secret scan clears by shape.
 */
const TEMPORARY = 'your_temporary_password'

const POSITIONS = [
  { code: 'SYSTEM_ADMINISTRATOR', name: 'System Administrator', defaultRoleCode: 'SYSTEM_ADMINISTRATOR', active: true, ordinal: 1 },
  { code: 'ORGANISER', name: 'Organiser', defaultRoleCode: 'ORGANISER', active: true, ordinal: 6 },
  { code: 'RETIRED_POST', name: 'A retired post', defaultRoleCode: 'ORGANISER', active: false, ordinal: 9 },
]
const ROLES = [
  { code: 'SYSTEM_ADMINISTRATOR', name: 'System Administrator', permissions: ['USERS_ADMIN'] },
  { code: 'ORGANISER', name: 'Organiser', permissions: ['MEMBERS_VIEW'] },
]
const ADA = userWith({ id: 31, username: 'ada.synthetic', displayName: 'Ada Synthetic', active: true, version: 2,
  position: { code: 'ORGANISER', name: 'Organiser' }, role: { code: 'ORGANISER', name: 'Organiser' }, scope: { type: 'AREA', codes: ['A', 'B'] } })
const page = (content) => ({ content, page: 0, size: 50, totalElements: content.length, totalPages: 1 })

/** The lists the dialog chooses from -- positions, roles, and SASBO's regions and areas as the server's golden answers. */
const listHandlers = () => [
  http.get('*/api/admin/positions', () => HttpResponse.json(POSITIONS)),
  http.get('*/api/admin/roles', () => HttpResponse.json(ROLES)),
  monthEndAnswer('GET /api/reference/regions'),
  monthEndAnswer('GET /api/reference/areas'),
]

/** `GET /api/admin/users`, remembering every query string it was asked with. */
const answersUsers = (answer = () => HttpResponse.json(page([ADA]))) => {
  const asked = []
  server.use(http.get('*/api/admin/users', ({ request }) => {
    asked.push(new URL(request.url).searchParams.toString())
    return answer(request)
  }))
  return asked
}

const renderUsers = ({ permissions = [PERMISSIONS.USERS_ADMIN] } = {}) => {
  server.use(...listHandlers())
  return renderWithProviders(
    <TestAuthProvider permissions={permissions}><Users /></TestAuthProvider>,
    { initialEntries: ['/admin/maintenance/users'] },
  )
}

const dialog = () => screen.getByRole('dialog')

/**
 * Wait for the dialog's lists: a select is shut while they load. Asked of the Position select alone, because the Role
 * select offers an "Organiser" too.
 */
const listsLoaded = () => within(within(dialog()).getByLabelText(/^Position/)).findByRole('option', { name: 'Organiser' })

/** Open Create a user, and wait for its lists. */
const openCreate = async (user) => {
  await user.click(await screen.findByRole('button', { name: /Create a user/ }))
  await listsLoaded()
}

/** A dialog is scanned on its own, as Modal's and DateField's are: it is portalled, and it is not a page (`region`). */
const scanDialog = () => expectNoA11yViolations(dialog(), { rules: { region: { enabled: false } } })

/** Everything the tab has stored, as one string -- key by key, the way Storage is meant to be read. */
const storedText = () => [sessionStorage, localStorage]
  .flatMap((storage) => Array.from({ length: storage.length }, (_, i) => storage.getItem(storage.key(i))))
  .join('\n')

describe('who may see the list', () => {
  it('a signed-in user without USERS_ADMIN is told what is needed, and nothing is asked of the server', async () => {
    const seen = []
    const listen = ({ request }) => seen.push(request.url)
    server.events.on('request:start', listen)
    renderUsers({ permissions: [PERMISSIONS.MEMBERS_VIEW] })
    expect(screen.getByRole('heading', { level: 1 })).toHaveTextContent('Users')
    expect(screen.getByRole('status')).toHaveTextContent('You need permission to manage users.')
    server.events.removeListener('request:start', listen)
    expect(seen).toEqual([])
  })

  it('nobody signed in is asked to sign in, and brought back here', () => {
    renderWithProviders(<Users />, { initialEntries: ['/admin/maintenance/users'] })
    expect(screen.getByRole('link', { name: 'Sign in' })).toHaveAttribute('href', '/login?next=%2Fadmin%2Fmaintenance%2Fusers')
  })
})

describe('the list of users', () => {
  it('says it is loading until the server answers -- never "no users" first', async () => {
    const answer = deferred()
    answersUsers(() => answer.promise)
    renderUsers()
    expect(await screen.findByText('Loading users…')).toBeInTheDocument()
    expect(screen.queryByText('No user has been created yet.')).not.toBeInTheDocument()
    answer.resolve(HttpResponse.json(page([ADA])))
    expect(await screen.findByRole('button', { name: 'Change Ada Synthetic (ada.synthetic)' })).toBeInTheDocument()
  })

  it('shows each user\'s position, role and what they see', async () => {
    answersUsers()
    renderUsers()
    const row = (await screen.findByRole('button', { name: /Change Ada Synthetic/ })).closest('tr')
    const cell = (column) => row.querySelector(`[data-col="${column}"]`)
    expect(cell('position')).toHaveTextContent('Organiser')
    expect(cell('role')).toHaveTextContent('Organiser')
    expect(cell('scope')).toHaveTextContent('Areas: A, B')
    expect(cell('standing')).toHaveTextContent('Active')
  })

  it('a failed read says so, with a retry, and never claims the list is empty', async () => {
    const user = setupUser()
    let calls = 0
    answersUsers(() => (calls++ === 0 ? HttpResponse.json({ message: 'boom' }, { status: 500 }) : HttpResponse.json(page([ADA]))))
    renderUsers()
    expect(await screen.findByText('The users did not load, so this is not an empty list.')).toBeInTheDocument()
    expect(screen.queryByText('No user has been created yet.')).not.toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Try again' }))
    expect(await screen.findByRole('button', { name: /Change Ada Synthetic/ })).toBeInTheDocument()
  })

  it('a refusal from the server is shown in its own words, with no retry that cannot work', async () => {
    answersUsers(() => refusal(403, 'PERMISSION_REQUIRED', 'You need permission to manage users.'))
    renderUsers()
    expect(await screen.findByText('The users did not load, so this is not an empty list.')).toBeInTheDocument()
    expect(screen.getByText('You need permission to manage users.')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Try again' })).not.toBeInTheDocument()
  })

  it('an empty list says so, and how to begin', async () => {
    answersUsers(() => HttpResponse.json(page([])))
    renderUsers()
    expect(await screen.findByText('No user has been created yet.')).toBeInTheDocument()
  })

  it('an answer that is not a page is not available -- never an empty list', async () => {
    answersUsers(() => HttpResponse.json([]))
    renderUsers()
    expect(await screen.findByText('The users did not load, so this is not an empty list.')).toBeInTheDocument()
  })

  it('searches by name or username once two characters are typed, from the first page', async () => {
    const user = setupUser()
    const asked = answersUsers()
    renderUsers()
    await screen.findByRole('button', { name: /Change Ada Synthetic/ })
    await user.type(screen.getByLabelText('Name or username'), 'Ad')
    await waitFor(() => expect(asked.at(-1)).toBe('page=0&size=50&q=Ad'))
    expect(asked[0]).toBe('page=0&size=50')
  })
})

describe('creating a user with a position and an area', () => {
  it('refuses an empty form on its fields, and an area user with no area', async () => {
    const user = setupUser()
    answersUsers()
    const created = []
    server.use(http.post('*/api/admin/users', async ({ request }) => {
      created.push(await request.json())
      return HttpResponse.json({ user: { ...ADA, id: 41 }, temporaryPassword: TEMPORARY }, { status: 201 })
    }))
    renderUsers()

    await openCreate(user)
    await user.click(within(dialog()).getByRole('button', { name: 'Create user' }))
    expect(within(dialog()).getByLabelText(/^Name/)).toHaveAttribute('aria-invalid', 'true')
    expect(within(dialog()).getByLabelText(/^Username/)).toHaveAttribute('aria-invalid', 'true')
    expect(within(dialog()).getByLabelText(/^Position/)).toHaveAttribute('aria-invalid', 'true')

    await user.type(within(dialog()).getByLabelText(/^Name/), 'Mo Synthetic')
    await user.type(within(dialog()).getByLabelText(/^Username/), 'mo.synthetic')
    await user.selectOptions(within(dialog()).getByLabelText(/^Position/), 'ORGANISER')
    await user.click(within(dialog()).getByRole('radio', { name: 'Only some areas' }))
    await user.click(within(dialog()).getByRole('button', { name: 'Create user' }))
    expect(within(dialog()).getByText('Choose at least one area this user sees.')).toBeInTheDocument()
    expect(created).toEqual([])

    await user.click(within(dialog()).getByRole('checkbox', { name: /Area A/ }))
    await user.click(within(dialog()).getByRole('button', { name: 'Create user' }))

    await waitFor(() => expect(created).toEqual([{
      username: 'mo.synthetic', displayName: 'Mo Synthetic', email: null, positionCode: 'ORGANISER', roleCode: null,
      scope: { type: 'AREA', codes: ['A'] },
    }]))
  })

  it('offers only the positions still offered, and the regions as SASBO names them', async () => {
    const user = setupUser()
    answersUsers()
    renderUsers()
    await openCreate(user)
    const position = within(dialog()).getByLabelText(/^Position/)
    await waitFor(() => expect(within(position).getByRole('option', { name: 'Organiser' })).toBeInTheDocument())
    expect(within(position).queryByRole('option', { name: 'A retired post' })).not.toBeInTheDocument()

    await user.click(within(dialog()).getByRole('radio', { name: 'Only some regions' }))
    expect(await within(dialog()).findByRole('checkbox', { name: 'NORTH' })).toBeInTheDocument()
  })

  it('shows the temporary password ONCE: with a warning, a Copy button, and gone when the dialog closes', async () => {
    const user = setupUser()
    answersUsers()
    server.use(http.post('*/api/admin/users', () => HttpResponse.json({ user: { ...ADA, id: 41 }, temporaryPassword: TEMPORARY }, { status: 201 })))
    renderUsers()

    await openCreate(user)
    await user.type(within(dialog()).getByLabelText(/^Name/), 'Mo Synthetic')
    await user.type(within(dialog()).getByLabelText(/^Username/), 'mo.synthetic')
    await user.selectOptions(within(dialog()).getByLabelText(/^Position/), 'ORGANISER')
    await user.click(within(dialog()).getByRole('button', { name: 'Create user' }))

    expect(await within(dialog()).findByText(TEMPORARY)).toBeInTheDocument()
    expect(dialog()).toHaveAccessibleName('Temporary password for Mo Synthetic')
    expect(within(dialog()).getByText(/shown only this once/)).toBeInTheDocument()
    expect(within(dialog()).getByRole('button', { name: /Copy/ })).toBeInTheDocument()

    await user.click(within(dialog()).getByRole('button', { name: 'Done' }))
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
    expect(document.body.textContent).not.toContain(TEMPORARY)
    expect(storedText()).not.toContain(TEMPORARY)
  })

  it('a username already taken is said on the username field', async () => {
    const user = setupUser()
    answersUsers()
    server.use(http.post('*/api/admin/users', () => refusal(409, 'USERNAME_TAKEN', 'That username is taken.')))
    renderUsers()
    await openCreate(user)
    await user.type(within(dialog()).getByLabelText(/^Name/), 'Mo Synthetic')
    await user.type(within(dialog()).getByLabelText(/^Username/), 'ada.synthetic')
    await user.selectOptions(within(dialog()).getByLabelText(/^Position/), 'ORGANISER')
    await user.click(within(dialog()).getByRole('button', { name: 'Create user' }))
    expect(await within(dialog()).findByText('That username is taken. Choose another.')).toBeInTheDocument()
    expect(within(dialog()).getByLabelText(/^Username/)).toHaveAttribute('aria-invalid', 'true')
  })
})

describe('changing a user', () => {
  const openAda = async (user) => {
    await user.click(await screen.findByRole('button', { name: 'Change Ada Synthetic (ada.synthetic)' }))
    await listsLoaded()
  }

  it('sends the change with the version it was read at, and says it is saved', async () => {
    const user = setupUser()
    answersUsers()
    const sent = []
    server.use(http.put('*/api/admin/users/31', async ({ request }) => {
      sent.push(await request.json())
      return HttpResponse.json({ user: { ...ADA, displayName: 'Ada Synthetic-Jones', version: 3 } })
    }))
    renderUsers()
    await openAda(user)

    const name = within(dialog()).getByLabelText(/^Name/)
    await user.clear(name)
    await user.type(name, 'Ada Synthetic-Jones')
    await user.click(within(dialog()).getByRole('button', { name: 'Save changes' }))

    expect(await screen.findByText('The changes to Ada Synthetic-Jones are saved.')).toBeInTheDocument()
    expect(sent).toEqual([{
      displayName: 'Ada Synthetic-Jones', email: 'sam.synthetic@example.invalid', positionCode: 'ORGANISER', roleCode: 'ORGANISER',
      scope: { type: 'AREA', codes: ['A', 'B'] }, active: true, version: 2,
    }])
  })

  it('STALE_VERSION: someone saved first -- it says so, and reads the user again on request', async () => {
    const user = setupUser()
    const asked = answersUsers((request) => HttpResponse.json(page([
      new URL(request.url).searchParams.get('q') ? { ...ADA, displayName: 'Ada Changed-Elsewhere', version: 5 } : ADA,
    ])))
    server.use(http.put('*/api/admin/users/31', () => refusal(409, 'STALE_VERSION', 'Someone else saved this user.')))
    renderUsers()
    await openAda(user)
    await user.click(within(dialog()).getByRole('button', { name: 'Save changes' }))

    expect(await within(dialog()).findByText(/Someone else changed this user while you were editing/)).toBeInTheDocument()
    await user.click(within(dialog()).getByRole('button', { name: 'Reload the user' }))
    expect(await within(dialog()).findByText(/This is Ada Changed-Elsewhere as saved now/)).toBeInTheDocument()
    expect(within(dialog()).getByLabelText(/^Name/)).toHaveValue('Ada Changed-Elsewhere')
    expect(asked.at(-1)).toBe('page=0&size=20&q=ada.synthetic')
  })

  it('LAST_ADMINISTRATOR: closing the last account that manages users is refused, and explained', async () => {
    const user = setupUser()
    answersUsers()
    server.use(http.put('*/api/admin/users/31', () => refusal(409, 'LAST_ADMINISTRATOR', 'Last administrator.')))
    renderUsers()
    await openAda(user)
    await user.click(within(dialog()).getByRole('checkbox', { name: /This user may sign in/ }))
    await user.click(within(dialog()).getByRole('button', { name: 'Save changes' }))
    expect(await within(dialog()).findByText(/no active user who can manage users/)).toBeInTheDocument()
  })

  it('a save that may have landed says it could not be confirmed -- never "not saved"', async () => {
    const user = setupUser()
    answersUsers()
    server.use(http.put('*/api/admin/users/31', () => HttpResponse.json({ message: 'boom' }, { status: 500 })))
    renderUsers()
    await openAda(user)
    await user.click(within(dialog()).getByRole('button', { name: 'Save changes' }))
    expect(await within(dialog()).findByText(/could not be confirmed/)).toBeInTheDocument()
  })

  it('resets a password after asking, and shows the new temporary password once', async () => {
    const user = setupUser()
    answersUsers()
    const resets = []
    server.use(http.post('*/api/admin/users/31/reset-password', () => {
      resets.push(31)
      return HttpResponse.json({ temporaryPassword: TEMPORARY })
    }))
    renderUsers()
    await openAda(user)
    await user.click(within(dialog()).getByRole('button', { name: 'Reset password' }))
    expect(dialog()).toHaveAccessibleName('Reset the password of Ada Synthetic?')
    expect(resets).toEqual([])

    await user.click(within(dialog()).getByRole('button', { name: 'Reset password' }))
    expect(await within(dialog()).findByText(TEMPORARY)).toBeInTheDocument()
    expect(resets).toEqual([31])
  })
})

describe('Maintenance > Users -- accessibility', () => {
  it('has no violations on the list, in the create dialog, and showing a temporary password', async () => {
    const user = setupUser()
    answersUsers()
    server.use(http.post('*/api/admin/users', () => HttpResponse.json({ user: { ...ADA, id: 41 }, temporaryPassword: TEMPORARY }, { status: 201 })))
    const { container } = renderUsers()
    await screen.findByRole('button', { name: /Change Ada Synthetic/ })
    await expectNoA11yViolations(container)

    await user.click(screen.getByRole('button', { name: /Create a user/ }))
    await listsLoaded()
    await scanDialog()

    await user.type(within(dialog()).getByLabelText(/^Name/), 'Mo Synthetic')
    await user.type(within(dialog()).getByLabelText(/^Username/), 'mo.synthetic')
    await user.selectOptions(within(dialog()).getByLabelText(/^Position/), 'ORGANISER')
    await user.click(within(dialog()).getByRole('button', { name: 'Create user' }))
    await within(dialog()).findByText(TEMPORARY)
    await scanDialog()
  })
})
