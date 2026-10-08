// @vitest-environment jsdom -- what the record resets when a DIFFERENT member is opened, and what it keeps
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { screen, within } from '@testing-library/react'
import { http, HttpResponse } from 'msw'

import MembersPage from './Members'
import { renderWithProviders } from '../../test/renderWithProviders'
import { server } from '../../test/msw/server'
import { pageFixtures } from '../../test/msw/pageFixtures'
import { setupUser } from '../../test/user'
import { registerHandlers } from '../../test/msw/memberRegister'

/**
 * 🔴 THE RECORD'S RESEED, WITNESSED BEFORE IT MOVED. `10.4`, batch 8.
 *
 * Opening a record seeds the edit form from it, and does two quieter things that nothing asserted: it returns to the
 * PERSONAL section -- but only when a different member is opened, compared by MEMBERSHIP NUMBER, not by object -- and
 * it forgets that initials were typed by hand, so the next member's initials derive from their first names again
 * (R4-F1). All of it lived in one effect keyed on `selectedMember`, which `react-hooks/set-state-in-effect` makes an
 * error this phase; and the effect also wrote two refs, so the body could not simply be lifted into render.
 *
 * These pin the three behaviours so the move can be proved rather than hoped:
 *
 *   ① a DIFFERENT member opens on Personal, whatever section the last one was left on;
 *   ② the SAME member, refreshed to a new object after a save, keeps the section the user is in -- the
 *      reason the comparison is by number and not by identity, and the half an "obvious" fix loses;
 *   ③ hand-typed initials on one member do not stop the next member's from deriving.
 *
 * ⚠️ WHAT THE DELETION PROOF SAID, RECORDED BECAUSE IT IS NOT WHAT WAS EXPECTED. Removing the tab reset turns ① red;
 * comparing by identity instead of number turns ② red. Removing the LOAD-TIME initials reset turns NOTHING red: ③
 * still passes, because `handleEditToggle` resets the same flag whenever an edit BEGINS, and there is no way to edit
 * the next member without passing through it. So ③ is a real witness of R4-F1 across members -- held by the
 * edit-start reset, not by the one `10.4` moved. The load-time reset had no consequence this file could construct,
 * and P10's refactor pass traced every path into editing and removed it.
 */

const MEMBERS = [
  { id: 1, membershipNumber: 'M0001', title: 'Mr', initials: 'A', firstNames: 'Alpha', surname: 'Firstrecord', identityType: 'I.D. Number', identityNumber: '9001010000080', status: 'Active', paymentType: 'Payroll', bankName: 'ABSA' },
  { id: 2, membershipNumber: 'M0002', title: 'Ms', initials: 'B', firstNames: 'Beta', surname: 'Secondrecord', identityNumber: '9002020000081', status: 'Active', paymentType: 'Payroll', bankName: 'ABSA' },
]

const panel = () => within(screen.getByTestId('member-panel'))
const section = (name) => panel().getByRole('button', { name })

async function open(user, membershipNumber) {
  await user.click(await screen.findByRole('button', { name: new RegExp(`^${membershipNumber} — open the record`) }))
  await panel().findByText(membershipNumber)
}

describe('the record, when another member is opened', () => {
  beforeEach(() => {
    vi.spyOn(console, 'error').mockImplementation(() => {})
    server.use(...pageFixtures)
    server.use(
      ...registerHandlers(MEMBERS),
      http.get('*/api/job-grades', () => HttpResponse.json([])),
    )
  })

  it('① a different member opens on Personal, not on the section the last one was left on', async () => {
    const user = setupUser()
    renderWithProviders(<MembersPage />)

    await open(user, 'M0001')
    await user.click(section(/Employment & Bank/i))
    expect(section(/Employment & Bank/i)).toHaveAttribute('aria-current', 'true')

    await user.click(panel().getAllByRole('button', { name: 'Close member details' })[0])
    await open(user, 'M0002')

    expect(section(/Personal Information/i)).toHaveAttribute('aria-current', 'true')
    expect(section(/Employment & Bank/i)).not.toHaveAttribute('aria-current')
  })

  it('② the must-pass: the SAME member, refreshed after a save, keeps the section the user is in', async () => {
    // 🔑 A save replaces the record with a NEW object carrying the SAME membership number
    // (`setSelectedMember({ ...editForm })`). Compare by identity and every save throws the user
    // back to Personal -- a fix that passes ① and quietly breaks this.
    server.use(http.put('*/api/members/1', async ({ request }) => HttpResponse.json({ ...MEMBERS[0], ...(await request.json()) })))
    const user = setupUser()
    renderWithProviders(<MembersPage />)

    await open(user, 'M0001')
    await user.click(panel().getByRole('button', { name: /Edit Member/i }))
    // A real change on the Personal section, then move to Employment & Bank and save from there --
    // the section the user is IN when the record refreshes is the one that must survive.
    const initials = panel().getByLabelText(/^Initials/i)
    await user.clear(initials)
    await user.type(initials, 'Q')
    await user.click(section(/Employment & Bank/i))
    await user.click(screen.getByRole('button', { name: /^Save changes$/ }))
    const confirm = await screen.findByRole('dialog', { name: 'Save these changes?' })
    await user.click(within(confirm).getByRole('button', { name: 'Save changes' }))

    // The save has landed and the record has been replaced by a new object: edit mode is over.
    await panel().findByRole('button', { name: /Edit Member/i })
    expect(section(/Employment & Bank/i)).toHaveAttribute('aria-current', 'true')
  })

  it('③ initials typed by hand on one member do not stop the next member’s from deriving', async () => {
    const user = setupUser()
    renderWithProviders(<MembersPage />)

    await open(user, 'M0001')
    await user.click(panel().getByRole('button', { name: /Edit Member/i }))
    const initials = panel().getByLabelText(/^Initials/i)
    await user.clear(initials)
    await user.type(initials, 'XYZ')
    await user.click(panel().getByRole('button', { name: /Cancel/i }))

    await user.click(panel().getAllByRole('button', { name: 'Close member details' })[0])
    await open(user, 'M0002')
    await user.click(panel().getByRole('button', { name: /Edit Member/i }))

    const firstNames = panel().getByLabelText(/^First Name/i)
    await user.clear(firstNames)
    await user.type(firstNames, 'Gamma Delta')

    expect(panel().getByLabelText(/^Initials/i)).toHaveValue('GD')
  })
})
