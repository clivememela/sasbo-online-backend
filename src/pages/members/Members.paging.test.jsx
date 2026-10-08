// @vitest-environment jsdom -- the register a page at a time: the address bar, the pager, the headings and the search
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { screen, waitFor, within } from '@testing-library/react'
import { http, HttpResponse } from 'msw'
import { useState } from 'react'
import { Outlet, Route, Routes, useLocation } from 'react-router-dom'

import MembersPage from './Members'
import { renderWithProviders } from '../../test/renderWithProviders'
import { server } from '../../test/msw/server'
import { pageFixtures } from '../../test/msw/pageFixtures'
import { setupUser } from '../../test/user'
import { registerHandlers } from '../../test/msw/memberRegister'
import { TestAuthProvider } from '../../test/authTestUtils'
import { PERMISSIONS } from '../../auth/permissions'

/**
 * 📄 THE MEMBER REGISTER, A PAGE AT A TIME (register row A10).
 *
 * The register loaded every member, with every field, on every visit and again after every save -- `GET /api/members`,
 * about 65,600 members at SASBO. It asks the server for ONE page now (`POST /api/members/register/search`), and what it
 * asks is the address bar's, so a refresh, a link or Back returns to the same place. What this file holds:
 *
 *   - the page, its size and its order are the address bar's, and the server's answer is what is counted;
 *   - the pager and the page size ask again, a new question from the first page;
 *   - a heading asks for its order, and `aria-sort` says which heading the rows are in;
 *   - the topbar's search asks once typing pauses, and a whole identity number never enters the address bar;
 *   - a save asks again for the page on screen alone -- the record stays open, the page stays put;
 *   - a caller who may not see members is told what is needed, and the server is asked nothing.
 *
 * Pages of 25 are the smallest the screen offers, so the paging cases hold twenty-seven members and OPEN on page 2 --
 * two rows -- instead of drawing twenty-five to get there. Every member here is invented.
 */
const number = (n) => `M${String(n).padStart(5, '0')}`
const BANKS = ['Invented Bank', 'Other Invented Bank', 'Third Invented Bank']
const invented = (count, needles = 0) => Array.from({ length: count }, (_, i) => ({
  id: i + 1,
  membershipNumber: number(i + 1),
  firstNames: 'Synthetic',
  surname: i < needles ? 'Needleworth' : `Haystack${i + 1}`,
  bankName: BANKS[i % BANKS.length],
  branch: 'Rosebank',
  status: 'Active',
}))

/** Twenty-seven members: a page of 25 and a page of two. The first two share a surname nobody else has. */
const REGISTER = invented(27, 2)
/** Three members: one page, whatever its size. */
const FEW = invented(3)
const PAGE_TWO = '/?page=2&size=25&sort=membershipNumber'
const WHOLE_ID = '0000000000000'

let requests

/** The address bar, where a test can read it. */
function AddressBar() {
  return <span data-testid="address-bar" hidden>{useLocation().search}</span>
}
const shown = () => new URLSearchParams(screen.getByTestId('address-bar').textContent)

/** The smallest shell that gives the page what the real one does: the topbar's search box, through the outlet. */
function SearchShell() {
  const [search, setSearch] = useState('')
  return (
    <>
      <input aria-label="Search members" value={search} onChange={(event) => setSearch(event.target.value)} />
      <Outlet context={{ search, setSearch }} />
    </>
  )
}

function renderRegister(members, at, { shell = false, auth } = {}) {
  server.use(...registerHandlers(members, { requests }))
  const page = shell
    ? <Routes><Route element={<SearchShell />}><Route path="/" element={<MembersPage />} /></Route></Routes>
    : <MembersPage />
  const ui = <>{page}<AddressBar /></>
  return renderWithProviders(auth ? <TestAuthProvider {...auth}>{ui}</TestAuthProvider> : ui, { initialEntries: [at] })
}

const heading = (name) => screen.getByRole('columnheader', { name })

beforeEach(() => {
  vi.spyOn(console, 'error').mockImplementation(() => {})
  requests = []
  server.use(...pageFixtures)
})

describe('the page is the address bar\'s, and the count is the server\'s', () => {
  it('📄 asks for ONE page -- the address bar\'s page, size and order -- and counts what the server counted', async () => {
    renderRegister(REGISTER, PAGE_TWO)

    expect(await screen.findByText(number(26))).toBeInTheDocument()
    expect(screen.getByText(number(27))).toBeInTheDocument()
    expect(screen.queryByText(number(1))).not.toBeInTheDocument()
    expect(requests).toEqual([expect.objectContaining({ page: 1, size: 25, sort: 'membershipNumber,asc' })])
    expect(screen.getByText(/Page 2 of 2/)).toHaveTextContent('Showing 26–27 of 27 · Page 2 of 2')
  })

  it('Previous asks for the page before -- and the address bar says so, so Back and a refresh return to it', async () => {
    const user = setupUser()
    renderRegister(REGISTER, PAGE_TWO)
    await screen.findByText(number(26))

    await user.click(screen.getByRole('button', { name: /Previous/ }))

    expect(await screen.findByText(number(1))).toBeInTheDocument()
    expect(requests.at(-1)).toMatchObject({ page: 0, size: 25, sort: 'membershipNumber,asc' })
    // Page 1 is the register's own, so the address leaves it out; the size and the order stay.
    expect(shown().get('page')).toBeNull()
    expect(shown().get('size')).toBe('25')
    expect(shown().get('sort')).toBe('membershipNumber,asc')
  })

  it('a page asked past the last is answered as the last, and the address bar follows it there -- asked once', async () => {
    renderRegister(REGISTER, '/?page=9&size=25&sort=membershipNumber')

    expect(await screen.findByText(number(27))).toBeInTheDocument()
    await waitFor(() => expect(shown().get('page')).toBe('2'))
    expect(requests).toHaveLength(1)
  })

  it('the page size asks again from the first page, and is kept in the address bar', async () => {
    const user = setupUser()
    renderRegister(REGISTER, PAGE_TWO)
    await screen.findByText(number(26))

    await user.selectOptions(screen.getByRole('combobox', { name: 'Per page' }), '100')

    expect(await screen.findByText(number(1))).toBeInTheDocument()
    expect(requests.at(-1)).toMatchObject({ page: 0, size: 100 })
    expect(shown().get('size')).toBe('100')
    expect(shown().get('page')).toBeNull()
  })
})

describe('the headings sort, on the server', () => {
  it('↕️ aria-sort is on the heading the rows are in; another heading asks for its order, A to Z, then Z to A', async () => {
    const user = setupUser()
    renderRegister(FEW, '/')
    await screen.findByText(number(1))

    // The register's own order is by surname -- the Member column.
    expect(heading('Member')).toHaveAttribute('aria-sort', 'ascending')
    expect(heading('Bank')).not.toHaveAttribute('aria-sort')

    await user.click(within(heading('Bank')).getByRole('button'))
    await waitFor(() => expect(heading('Bank')).toHaveAttribute('aria-sort', 'ascending'))
    expect(heading('Member')).not.toHaveAttribute('aria-sort')
    expect(requests.at(-1)).toMatchObject({ sort: 'bank,asc', page: 0 })
    expect(shown().get('sort')).toBe('bank,asc')

    await user.click(within(heading('Bank')).getByRole('button'))
    await waitFor(() => expect(heading('Bank')).toHaveAttribute('aria-sort', 'descending'))
    expect(requests.at(-1)).toMatchObject({ sort: 'bank,desc' })
    // The rows are in the server's order: Z to A by bank.
    await waitFor(() => expect(screen.getAllByRole('button', { name: /open the record for/ })[0])
      .toHaveAccessibleName(new RegExp(`^${number(3)}`)))
  })

  it('the must-pass: a heading with no order of its own is a plain heading', async () => {
    renderRegister(FEW, '/')
    await screen.findByText(number(1))

    expect(within(heading('Employee no.')).queryByRole('button')).not.toBeInTheDocument()
    expect(heading('Employee no.')).not.toHaveAttribute('aria-sort')
  })
})

describe('the topbar\'s search', () => {
  it('🔍 typing asks ONCE, when it pauses, from the first page -- and the address bar keeps the question', async () => {
    const user = setupUser()
    renderRegister(REGISTER, PAGE_TWO, { shell: true })
    await screen.findByText(number(26))

    await user.type(screen.getByRole('textbox', { name: 'Search members' }), 'Needle')

    expect(await screen.findByText(number(2))).toBeInTheDocument()
    expect(screen.queryByText(number(26))).not.toBeInTheDocument()
    expect(requests.filter((asked) => asked.q).map((asked) => asked.q)).toEqual(['Needle'])
    expect(requests.at(-1)).toMatchObject({ q: 'Needle', page: 0 })
    expect(shown().get('q')).toBe('Needle')
    expect(shown().get('page')).toBeNull()
  })

  it('🔒 a whole identity number is asked for -- and never written into the address bar', async () => {
    const user = setupUser()
    renderRegister(FEW, '/', { shell: true })
    await screen.findByText(number(1))

    await user.type(screen.getByRole('textbox', { name: 'Search members' }), WHOLE_ID)

    await waitFor(() => expect(requests.at(-1)).toMatchObject({ q: WHOLE_ID, page: 0 }))
    expect(await screen.findByText(/No member on record has that identity number/)).toBeInTheDocument()
    expect(screen.getByTestId('address-bar').textContent).not.toContain(WHOLE_ID)
  })
})

describe('after a save', () => {
  /** The member edited here carries what the record's own rules ask of a save (as `Members.saveOutcome`'s does). */
  const EDITABLE = REGISTER.map((m) => (m.id === 26
    ? { ...m, bankName: null, branch: null, initials: 'S', identityType: 'I.D. Number', identityNumber: '9001010000080', paymentType: 'Payroll' }
    : m))

  it('🔁 the page on screen alone is asked again -- the record stays open, and the page stays put', async () => {
    const user = setupUser()
    server.use(http.put('*/api/members/:id', async ({ request }) => HttpResponse.json(await request.json())))
    renderRegister(EDITABLE, PAGE_TWO)

    await user.click(await screen.findByRole('button', { name: /open the record for.*Haystack26/i }))
    const panel = within(screen.getByTestId('member-panel'))
    await user.click(await panel.findByRole('button', { name: /Edit Member/i }))
    const initials = await panel.findByLabelText(/^Initials/)
    await user.clear(initials)
    await user.type(initials, 'Q')
    await user.click(screen.getByRole('button', { name: /^Save changes$/ }))
    const dialog = await screen.findByRole('dialog', { name: 'Save these changes?' })
    await user.click(within(dialog).getByRole('button', { name: 'Save changes' }))

    await waitFor(() => expect(requests).toHaveLength(2))
    expect(requests[1], 'the same page, asked again -- not the first page, not the whole register').toEqual(requests[0])
    expect(await panel.findByRole('button', { name: /Edit Member/i })).toBeInTheDocument()
    expect(panel.getAllByText(/Haystack26/).length).toBeGreaterThan(0)
    expect(screen.getByText(number(27))).toBeInTheDocument()
    expect(shown().get('page')).toBe('2')
  })
})

describe('who may read the register', () => {
  let asked
  const listen = ({ request }) => asked.push(new URL(request.url).pathname)

  beforeEach(() => {
    asked = []
    server.events.on('request:start', listen)
  })
  afterEach(() => server.events.removeListener('request:start', listen))

  it('🔒 a signed-in user without MEMBERS_VIEW is told what is needed -- and the server is asked nothing', async () => {
    renderRegister(FEW, '/', { auth: { signedIn: true, permissions: [] } })

    expect(await screen.findByText('You need permission to see members. Ask your administrator if you should have it.'))
      .toBeInTheDocument()
    expect(screen.queryByRole('table')).not.toBeInTheDocument()
    expect(asked.filter((path) => path.startsWith('/api/members'))).toEqual([])
  })

  it('🔒 nobody, while sign-in is required: asked to sign in -- and the server is asked nothing', async () => {
    renderRegister(FEW, '/', { auth: { signedIn: false, mode: 'required' } })

    expect(await screen.findByText(/Sign in to see members\./)).toBeInTheDocument()
    expect(asked.filter((path) => path.startsWith('/api/members'))).toEqual([])
  })

  it('the must-pass: a user who holds MEMBERS_VIEW reads the register', async () => {
    renderRegister(FEW, '/', { auth: { permissions: [PERMISSIONS.MEMBERS_VIEW] } })

    expect(await screen.findByText(number(1))).toBeInTheDocument()
    expect(asked).toContain('/api/members/register/search')
  })
})
