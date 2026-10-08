// @vitest-environment jsdom -- renders the member record's SASBO structures tab
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { http, HttpResponse } from 'msw'
import { screen, waitFor, within } from '@testing-library/react'

import MembersPage from './Members'
import { renderWithProviders } from '../../test/renderWithProviders'
import { server } from '../../test/msw/server'
import { pageFixtures } from '../../test/msw/pageFixtures'
import { setupUser } from '../../test/user'
import { BEC_EXECUTIVE_POSITIONS, BEC_STRUCTURES_REPRESENTED } from '../../config/memberConstants'
import { formatDateLong } from '../../lib/format'
import { registerHandlers } from '../../test/msw/memberRegister'

/**
 * EDIT MEMBER · SASBO STRUCTURES -- SASBO's structures tree, through the owner, 2026-09-30.
 *
 *   Shop Steward?
 *     └ Election Date                             only under Shop Steward = Yes
 *   BEC Member?
 *     └ Branch Executive Committee                only under BEC = Yes, and everything below it too
 *     └ BEC Recruiter?
 *     └ BEC Structure Representative?
 *         └ Structures represented                INC / IC · NCF / SJC · VP · President · MANCOM · Resolutions Committee
 *     └ BEC Executive?
 *         └ Executive Position                    one of five
 *     └ COSATU Member?
 *         └ Sits on a COSATU Local structure? · Sits on a COSATU Regional structure? · COSATU positions
 *
 * 🗣️ Where each line comes from:
 * - SASBO, through the owner, 2026-09-30: "Shop Steward (Yes) > Select Election Date. BEC Member (Yes) > Select
 *   Branch Committee. We need those functionalities back. This current one is incomplete" -- and the tree above.
 * - the stakeholder, relayed 2026-09-17: "For a member to participate in COSATU structures, they have to be within
 *   the BEC structure", and a COSATU Local / Region is a structure a member SITS ON (yes/no).
 *
 * 🔴 THIS FILE ASSERTED THE OPPOSITE UNTIL V55 -- "none of the removed questions is still on the tab" -- and it was
 * right then: the tab had been cut to Shop Steward, BEC and COSATU on 2026-09-17 because nothing below them had a
 * column (CM-05). V55 gives each one, so the questions are back and this file now pins that they are.
 * - A37 / A38 (a list field arriving as "PEC,CEC" throwing on `.join`) are fixed at the source:
 *   `memberService.normalizeMember`. The render-level half stays here, so a string-shaped record still opens.
 */

const MEMBER = {
  id: 1,
  membershipNumber: 'M0001',
  firstNames: 'Synthetic',
  surname: 'Testperson',
  identityType: 'I.D. Number',
  identityNumber: '9001010000080',
  status: 'Active',
  paymentType: 'Payroll Deduction',
  isShopSteward: false,
  isBec: false,
  isCosatu: 'N',
}

/** A member on the whole tree, as the server sends one back (lists folded, a date as [y, m, d]). */
const SEATED = {
  ...MEMBER,
  isShopSteward: true,
  shopStewardElectionDate: [2025, 3, 14],
  isBec: true,
  becCommitteeCode: 'G01',
  becCommitteeName: 'JOHANNESBURG',
  isBecRecruiter: true,
  isBecStructureRep: true,
  becStructures: 'INC / IC,MANCOM',
  isBecExecutive: true,
  becExecutivePosition: 'Treasurer',
  isCosatu: 'Y',
  isCosatuLocal: true,
  isCosatuRegional: false,
  cosatuPositions: 'PEC,Treasurer',
}

/** SASBO's committees as `GET /api/reference/committees` answers -- out of order, with an UNATTACHED line. */
const COMMITTEES = {
  list: 'committees',
  printedOn: '2026-08-31',
  confirmedOn: null,
  rows: [
    { code: 'G02', province: 'G', name: 'PRETORIA', recruitingNumber: 2 },
    { code: 'G99', province: 'G', name: 'UNATTACHED', recruitingNumber: null },
    { code: 'G01', province: 'G', name: 'JOHANNESBURG', recruitingNumber: 1 },
    { code: 'W01', province: 'W', name: 'CAPE TOWN', recruitingNumber: 3 },
  ],
}

/** The register answering with `body` -- its page, its figures and each record (A10): handlers, to spread. */
const membersRespond = (body) => registerHandlers(body)
const committeesRespond = (body = COMMITTEES) => http.get('*/api/reference/committees', () => HttpResponse.json(body))
const panel = () => within(screen.getByTestId('member-panel'))

async function openStructures(user, { edit = false } = {}) {
  await user.click(await screen.findByRole('button', { name: /open the record for/i }))
  if (edit) await user.click(await screen.findByRole('button', { name: /Edit Member/i }))
  await user.click(await screen.findByRole('button', { name: /Sasbo Structures/i }))
}

/** The labels of every control on the panel, in order, so a test can say "exactly these" -- a group's boxes aside: a group is named by its legend (`groupLegends`). */
const controlLabels = () =>
  [...screen.getByTestId('member-panel').querySelectorAll('input:not([type="checkbox"]), select, button[role="combobox"], [role="combobox"]')]
    .map((el) => el.labels?.[0]?.textContent?.replace(/\*/g, '').replace(/\(required\)/, '').trim() || el.getAttribute('aria-label') || '')
    .filter(Boolean)

const answer = async (user, label, value) => user.selectOptions(await panel().findByLabelText(label), value)

/** The legends of the checkbox groups on the panel, in order. */
const groupLegends = () =>
  [...screen.getByTestId('member-panel').querySelectorAll('fieldset > legend')].map((legend) =>
    legend.textContent.replace(/\*/g, '').replace(/\(required\)/, '').trim())

/** The boxes of one checkbox group, by its legend. */
const boxesOf = (legend) => {
  const fieldset = [...screen.getByTestId('member-panel').querySelectorAll('fieldset')]
    .find((set) => set.querySelector('legend')?.textContent.startsWith(legend))
  return within(fieldset).getAllByRole('checkbox')
}

/**
 * This month's first day as the calendar names it -- never in the future, so always a day the picker lets you choose.
 * Named by the app's own formatter (a locale's "Sept" is not the calendar's "Sep"), and matched on its start: on the
 * 1st itself the cell adds ", today".
 */
const firstOfThisMonth = () => {
  const now = new Date()
  const y = now.getFullYear()
  const m = now.getMonth() + 1
  return { cell: new RegExp(`^${formatDateLong([y, m, 1])}`), wire: `${y}-${String(m).padStart(2, '0')}-01` }
}

async function save(user) {
  await user.click(screen.getByRole('button', { name: /^Save changes$/ }))
  const dialog = await screen.findByRole('dialog', { name: 'Save these changes?' })
  await user.click(within(dialog).getByRole('button', { name: 'Save changes' }))
}

beforeEach(() => {
  vi.spyOn(console, 'error').mockImplementation(() => {})
  // Two `use` calls: the catch-all first, then the specific handlers, which prepend.
  server.use(...pageFixtures)
  server.use(committeesRespond())
})

describe('editing: SASBO\'s tree, each follow-up under the Yes that asks it', () => {
  it('a member who is neither is asked two questions -- nothing under Shop Steward or BEC is offered', async () => {
    server.use(...membersRespond([MEMBER]))
    const user = setupUser()
    renderWithProviders(<MembersPage />)
    await openStructures(user, { edit: true })

    expect(await panel().findByLabelText(/^Shop Steward\?/)).toBeInTheDocument()
    expect(panel().getByLabelText(/^BEC \(Branch Executive Committee\) Member\?/)).toBeInTheDocument()
    expect(controlLabels().filter((l) => /Steward|Election|BEC|Committee|Executive|COSATU/.test(l))).toEqual([
      'Shop Steward?',
      'BEC (Branch Executive Committee) Member?',
    ])
  })

  it('Shop Steward = Yes asks for the Election Date, and No takes it away', async () => {
    server.use(...membersRespond([MEMBER]))
    const user = setupUser()
    renderWithProviders(<MembersPage />)
    await openStructures(user, { edit: true })

    await answer(user, /^Shop Steward\?/, 'Yes')
    expect(await panel().findByRole('combobox', { name: /Election Date/ })).toBeInTheDocument()
    await answer(user, /^Shop Steward\?/, 'No')
    expect(panel().queryByRole('combobox', { name: /Election Date/ })).not.toBeInTheDocument()
  })

  it('BEC = Yes asks the rest of the tree, in SASBO\'s order', async () => {
    server.use(...membersRespond([MEMBER]))
    const user = setupUser()
    renderWithProviders(<MembersPage />)
    await openStructures(user, { edit: true })

    await answer(user, /^BEC \(Branch Executive Committee\) Member\?/, 'Yes')
    await answer(user, /^BEC Structure Representative\?/, 'Yes')
    await answer(user, /^BEC Executive\?/, 'Yes')
    await answer(user, /^COSATU Member\?/, 'Yes')

    expect(controlLabels().filter((l) => /Steward|Election|BEC|Committee|Executive|Position|COSATU/.test(l))).toEqual([
      'Shop Steward?',
      'BEC (Branch Executive Committee) Member?',
      'Branch Executive Committee',
      'BEC Recruiter?',
      'BEC Structure Representative?',
      'BEC Executive?',
      'Executive Position',
      'COSATU Member?',
      'Sits on a COSATU Local structure?',
      'Sits on a COSATU Regional structure?',
    ])
    expect(groupLegends()).toEqual(['Structures represented', 'COSATU positions'])
  })

  it('the committees are SASBO\'s own, alphabetical, and UNATTACHED is not one', async () => {
    server.use(...membersRespond([{ ...MEMBER, isBec: true }]))
    const user = setupUser()
    renderWithProviders(<MembersPage />)
    await openStructures(user, { edit: true })

    await user.click(await panel().findByRole('combobox', { name: /^Branch Executive Committee/ }))
    const listbox = await screen.findByRole('listbox')
    await waitFor(() => expect(within(listbox).getAllByRole('option').map((o) => o.textContent.trim()))
      .toEqual(['CAPE TOWN', 'JOHANNESBURG', 'PRETORIA']))
  })

  it('the structures and positions offered are exactly SASBO\'s', async () => {
    server.use(...membersRespond([{ ...MEMBER, isBec: true, isBecStructureRep: true, isBecExecutive: true, isCosatu: 'Y' }]))
    const user = setupUser()
    renderWithProviders(<MembersPage />)
    await openStructures(user, { edit: true })
    await panel().findByLabelText(/^Executive Position/)

    expect(boxesOf('Structures represented').map((box) => box.closest('label').textContent.trim()))
      .toEqual(BEC_STRUCTURES_REPRESENTED)
    const positions = [...panel().getByLabelText(/^Executive Position/).querySelectorAll('option')]
      .map((option) => option.textContent).filter((text) => text !== 'Select...')
    expect(positions).toEqual(BEC_EXECUTIVE_POSITIONS)
    expect(boxesOf('COSATU positions').map((box) => box.closest('label').textContent.trim())).toEqual([
      'PEC (Provincial Executive Committee)', 'CEC (Central Executive Committee)', 'Chairperson', 'Secretary', 'Treasurer',
    ])
  })

  it('🔴 a Yes cannot be saved without what it asks for -- and nothing is sent', async () => {
    let puts = 0
    server.use(
      ...membersRespond([MEMBER]),
      http.put('*/api/members/:id', () => {
        puts += 1
        return HttpResponse.json({})
      }),
    )
    const user = setupUser()
    renderWithProviders(<MembersPage />)
    await openStructures(user, { edit: true })

    await answer(user, /^Shop Steward\?/, 'Yes')
    await answer(user, /^BEC \(Branch Executive Committee\) Member\?/, 'Yes')
    await answer(user, /^BEC Structure Representative\?/, 'Yes')
    await answer(user, /^BEC Executive\?/, 'Yes')
    await user.click(screen.getByRole('button', { name: /^Save changes$/ }))

    for (const message of [
      'Enter the date the shop steward was elected',
      'Choose the Branch Executive Committee this member sits on',
      'Choose the structures this member represents',
      'Choose the position this member holds',
    ]) {
      expect((await screen.findAllByText(message)).length, message).toBeGreaterThan(0)
    }
    expect(screen.queryByRole('dialog', { name: 'Save these changes?' })).not.toBeInTheDocument()
    expect(puts).toBe(0)
  })

  it('the whole tree reaches the server as V55 stores it', async () => {
    let saved
    server.use(
      ...membersRespond([MEMBER]),
      http.put('*/api/members/:id', async ({ request }) => {
        saved = await request.json()
        return HttpResponse.json(saved)
      }),
    )
    const user = setupUser()
    renderWithProviders(<MembersPage />)
    await openStructures(user, { edit: true })
    const day = firstOfThisMonth()

    await answer(user, /^Shop Steward\?/, 'Yes')
    await user.click(await panel().findByRole('combobox', { name: /Election Date/ }))
    await user.click(await screen.findByRole('gridcell', { name: day.cell }))

    await answer(user, /^BEC \(Branch Executive Committee\) Member\?/, 'Yes')
    await user.click(await panel().findByRole('combobox', { name: /^Branch Executive Committee/ }))
    await user.click(await screen.findByRole('option', { name: 'JOHANNESBURG' }))
    await answer(user, /^BEC Recruiter\?/, 'Yes')
    await answer(user, /^BEC Structure Representative\?/, 'Yes')
    // Ticked out of SASBO's order: the record keeps theirs.
    await user.click(boxesOf('Structures represented').find((box) => box.closest('label').textContent.trim() === 'MANCOM'))
    await user.click(boxesOf('Structures represented').find((box) => box.closest('label').textContent.trim() === 'INC / IC'))
    await answer(user, /^BEC Executive\?/, 'Yes')
    await answer(user, /^Executive Position/, 'Treasurer')
    await answer(user, /^COSATU Member\?/, 'Yes')
    await answer(user, /^Sits on a COSATU Local structure\?/, 'Yes')
    await answer(user, /^Sits on a COSATU Regional structure\?/, 'No')
    await user.click(boxesOf('COSATU positions').find((box) => box.closest('label').textContent.trim() === 'Treasurer'))
    await user.click(boxesOf('COSATU positions').find((box) => box.closest('label').textContent.startsWith('PEC')))

    await save(user)

    await waitFor(() => expect(saved).toBeDefined())
    expect(saved).toMatchObject({
      isShopSteward: true,
      shopStewardElectionDate: day.wire,
      isBec: true,
      becCommitteeCode: 'G01',
      isBecRecruiter: true,
      isBecStructureRep: true,
      becStructures: ['INC / IC', 'MANCOM'],
      isBecExecutive: true,
      becExecutivePosition: 'Treasurer',
      isCosatu: 'Y',
      isCosatuLocal: true,
      isCosatuRegional: false,
      cosatuPositions: ['PEC', 'Treasurer'],
    })
  })

  it('the save confirmation names the committee, not its code', async () => {
    server.use(...membersRespond([{ ...MEMBER, isBec: true, becCommitteeCode: 'G02', becCommitteeName: 'PRETORIA' }]))
    const user = setupUser()
    renderWithProviders(<MembersPage />)
    await openStructures(user, { edit: true })

    await user.click(await panel().findByRole('combobox', { name: /^Branch Executive Committee/ }))
    await user.click(await screen.findByRole('option', { name: 'JOHANNESBURG' }))
    await user.click(screen.getByRole('button', { name: /^Save changes$/ }))

    const dialog = await screen.findByRole('dialog', { name: 'Save these changes?' })
    expect(within(dialog).getByText('Branch Executive Committee: PRETORIA → JOHANNESBURG')).toBeInTheDocument()
  })

  it('leaving BEC clears everything under it, and that is what is saved', async () => {
    let saved
    server.use(
      ...membersRespond([SEATED]),
      http.put('*/api/members/:id', async ({ request }) => {
        saved = await request.json()
        return HttpResponse.json(saved)
      }),
    )
    const user = setupUser()
    renderWithProviders(<MembersPage />)
    await openStructures(user, { edit: true })

    await answer(user, /^BEC \(Branch Executive Committee\) Member\?/, 'No')
    expect(panel().queryByLabelText(/^COSATU Member\?/)).not.toBeInTheDocument()
    expect(panel().queryByRole('combobox', { name: /^Branch Executive Committee/ })).not.toBeInTheDocument()

    await save(user)

    await waitFor(() => expect(saved).toBeDefined())
    expect(saved).toMatchObject({
      isBec: false,
      becCommitteeCode: '',
      isBecRecruiter: false,
      isBecStructureRep: false,
      becStructures: [],
      isBecExecutive: false,
      becExecutivePosition: '',
      isCosatu: 'N',
      isCosatuLocal: false,
      isCosatuRegional: false,
      cosatuPositions: [],
    })
    // The must-pass half: a No under BEC takes nothing that is not beneath it.
    expect(saved).toMatchObject({ isShopSteward: true, shopStewardElectionDate: [2025, 3, 14] })
  })

  it('🔑 a Yes stored before V55, with nothing beside it, saves an edit of anything else', async () => {
    let saved
    server.use(
      ...membersRespond([{ ...MEMBER, isShopSteward: true, isBec: true }]),
      http.put('*/api/members/:id', async ({ request }) => {
        saved = await request.json()
        return HttpResponse.json(saved)
      }),
    )
    const user = setupUser()
    renderWithProviders(<MembersPage />)
    await openStructures(user, { edit: true })

    await answer(user, /^BEC Recruiter\?/, 'Yes')
    await save(user)

    await waitFor(() => expect(saved).toBeDefined())
    expect(saved).toMatchObject({ isShopSteward: true, isBec: true, isBecRecruiter: true })
  })

  it('leaves no control on the panel without an accessible name (BR-14, the class)', async () => {
    server.use(...membersRespond([SEATED]))
    const user = setupUser()
    renderWithProviders(<MembersPage />)
    await openStructures(user, { edit: true })
    await panel().findByLabelText(/^Sits on a COSATU Local structure\?/)

    const nameless = [...screen.getByTestId('member-panel').querySelectorAll('input, select')].filter(
      (el) => el.type !== 'hidden' && !(el.labels?.[0]?.textContent || el.getAttribute('aria-label') || '').trim(),
    )
    expect(nameless.length, 'a control on the member panel has no accessible name').toBe(0)
  })
})

describe('reading: the whole tree, and an unanswered question says so', () => {
  it('shows every answer the record holds', async () => {
    server.use(...membersRespond([SEATED]))
    const user = setupUser()
    renderWithProviders(<MembersPage />)
    await openStructures(user)

    expect(await screen.findByText('Core Appointments')).toBeInTheDocument()
    const valueOf = (label) => panel().getByText(label).nextElementSibling
    expect(valueOf('Election Date')).toHaveTextContent('2025/03/14')
    expect(valueOf('Branch Executive Committee')).toHaveTextContent('JOHANNESBURG')
    expect(valueOf('BEC Recruiter')).toHaveTextContent('Yes')
    expect(valueOf('Structures Represented')).toHaveTextContent('INC / IC, MANCOM')
    expect(valueOf('Executive Position')).toHaveTextContent('Treasurer')
    expect(valueOf('COSATU Local structure')).toHaveTextContent('Yes')
    expect(valueOf('COSATU Regional structure')).toHaveTextContent('No')
    expect(valueOf('COSATU Positions')).toHaveTextContent('PEC, Treasurer')
  })

  it('a member saved before V29 reads "Not recorded", not "No" -- nobody was ever asked', async () => {
    // CM-05: Shop Steward and BEC had no column, so for every earlier member the answer is absent.
    const legacy = { ...MEMBER }
    delete legacy.isShopSteward
    delete legacy.isBec
    server.use(...membersRespond([legacy]))
    const user = setupUser()
    renderWithProviders(<MembersPage />)
    await openStructures(user)

    // And Memnet (MM80), asked only since V63: nobody was asked that either.
    expect(await screen.findAllByText('Not recorded')).toHaveLength(3)
  })

  it('a BEC member from before V55 reads "Not recorded" for each question they were never asked', async () => {
    server.use(...membersRespond([{ ...MEMBER, isShopSteward: true, isBec: true }]))
    const user = setupUser()
    renderWithProviders(<MembersPage />)
    await openStructures(user)

    // Election Date, Branch Executive Committee, BEC Recruiter, BEC Structure Representative, BEC Executive -- and
    // Memnet (MM80), asked only since V63.
    expect(await screen.findAllByText('Not recorded')).toHaveLength(6)
  })

  it('🔴 MM80: Memnet is asked on the record, and the answer reaches the server', async () => {
    // SASBO, 2026-10-01: a member is "captured as a mnet member when we get the nomination firm" -- on the record, as
    // every structure answer is. Never a default: nothing is sent for a member nobody asked about.
    let saved
    server.use(
      ...membersRespond([MEMBER]),
      http.put('*/api/members/:id', async ({ request }) => {
        saved = await request.json()
        return HttpResponse.json(saved)
      }),
    )
    const user = setupUser()
    renderWithProviders(<MembersPage />)
    await openStructures(user, { edit: true })

    await answer(user, /^Memnet Member\?/, 'Yes')
    await save(user)

    await waitFor(() => expect(saved).toBeDefined())
    expect(saved.isMemnet).toBe(true)
  })

  it('MM80: the record reads its Memnet answer', async () => {
    server.use(...membersRespond([{ ...SEATED, isMemnet: true }]))
    const user = setupUser()
    renderWithProviders(<MembersPage />)
    await openStructures(user)

    expect(await screen.findByText('Core Appointments')).toBeInTheDocument()
    expect(panel().getByText('Memnet Member').nextElementSibling).toHaveTextContent('Yes')
  })
})

describe('A37 / A38 -- a list field that arrives as the server\'s delimited string', () => {
  it('a record whose COSATU positions and BEC structures are strings opens, reads and edits without throwing', async () => {
    // The shape that threw "cosatuPositions.join is not a function" on open. memberService unfolds it now;
    // this is the render-level proof that nothing on the page still receives the string.
    server.use(...membersRespond([SEATED]))
    const user = setupUser()
    renderWithProviders(<MembersPage />)

    await openStructures(user)
    expect(await screen.findByText('Core Appointments')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: /Edit Member/i }))
    expect(await panel().findByLabelText(/^COSATU Member\?/)).toBeInTheDocument()
    expect(boxesOf('COSATU positions').filter((box) => box.checked)).toHaveLength(2)
  })
})
