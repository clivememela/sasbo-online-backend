// @vitest-environment jsdom -- every date on the member record, in one place, in one format
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { screen, within } from '@testing-library/react'

import MembersPage from './Members'
import { renderWithProviders } from '../../test/renderWithProviders'
import { server } from '../../test/msw/server'
import { pageFixtures } from '../../test/msw/pageFixtures'
import { setupUser } from '../../test/user'
import { registerHandlers } from '../../test/msw/memberRegister'

/**
 * 🔴 R3-F1 (P9's independent review): THE RECORD PRINTED DATES FOUR WAYS, AND A PASSPORT HOLDER'S RAN TOGETHER.
 *
 * `LocalDate` arrives from the server as `[y, m, d]` (JacksonConfig builds its own mapper without disabling
 * `WRITE_DATES_AS_TIMESTAMPS`; `lib/format.js` records the same). On one panel, for one person:
 *   `1988615`      the read view of Date of Birth for any identity type other than I.D. Number -- the ARRAY, rendered
 *                  raw by React, which is the defect that matters: a passport holder's date of birth cannot be
 *                  derived from their number, and this is where it is read;
 *   `15-06-1988`   an I.D. holder's, through `validateSAID.formatDate` -- day first, the ambiguity §15 exists to remove;
 *   `2022-01-04`   Date Joined, through a local `formatBackendDate`;
 *   `1988,6,15`    the save confirmation's `readable()`, so a change read `1988,6,15 → 1988-06-15`.
 * And `2022/01/04` in the picker, which is the one conventions §15 actually specifies.
 *
 * Every record date goes through `lib/format.js`'s `formatDate` now -- `yyyy/mm/dd`. `formatDateLong` stays where
 * prose wants it (the panel's "joined 04 Jan 2022"), which is a sentence, not a field.
 *
 * ⚠️ **A7 IS UNTOUCHED.** The `identityType === "Passport"` comparisons are the pinned defect and stay exactly as
 * they are; this changes how a date is PRINTED, never how an identity type is tested.
 */

const WIRE_DOB = [1988, 6, 15]
const WIRE_JOINED = [2022, 1, 4]

const PASSPORT = {
  id: 7,
  membershipNumber: 'M0007',
  firstNames: 'Synthetic',
  surname: 'Traveller',
  initials: 'S',
  identityType: 'Passport Number',
  identityNumber: 'A1234567',
  dateOfBirth: WIRE_DOB,
  joinDate: WIRE_JOINED,
  status: 'Active',
  paymentType: 'Payroll',
}

const ID_HOLDER = {
  ...PASSPORT,
  id: 8,
  membershipNumber: 'M0008',
  surname: 'Resident',
  identityType: 'I.D. Number',
  identityNumber: '8806155000084',
}

const panel = () => within(screen.getByTestId('member-panel'))

async function openRecord(member, tab) {
  const user = setupUser()
  server.use(...registerHandlers([member]))
  renderWithProviders(<MembersPage />)
  await user.click(await screen.findByRole('button', { name: /open the record for/i }))
  if (tab) await user.click(await screen.findByRole('button', { name: tab }))
  return user
}

const valueOf = (label) => panel().getByText(label).nextElementSibling

describe('the dates on a member record', () => {
  beforeEach(() => {
    vi.spyOn(console, 'error').mockImplementation(() => {})
    server.use(...pageFixtures)
  })

  it('① a passport holder’s date of birth reads as a date, not as run-together digits', async () => {
    await openRecord(PASSPORT)

    expect(await panel().findByText('Date of Birth')).toBeInTheDocument()
    expect(valueOf('Date of Birth')).toHaveTextContent('1988/06/15')
    expect(valueOf('Date of Birth')).not.toHaveTextContent('1988615')
  })

  it('② the must-pass: an I.D. holder’s is the SAME shape, not day-first', async () => {
    await openRecord(ID_HOLDER)

    expect(await panel().findByText('Date of Birth')).toBeInTheDocument()
    expect(valueOf('Date of Birth')).toHaveTextContent('1988/06/15')
    expect(valueOf('Date of Birth')).not.toHaveTextContent('15-06-1988')
  })

  it('③ Date Joined is the same shape as the rest of the panel', async () => {
    await openRecord(PASSPORT, /Membership & Subscriptions/i)

    expect(await panel().findByText('Date Joined Sasbo')).toBeInTheDocument()
    expect(valueOf('Date Joined Sasbo')).toHaveTextContent('2022/01/04')
    expect(valueOf('Date Joined Sasbo')).not.toHaveTextContent('2022-01-04')
  })

  it('④ the save confirmation reads a changed date as a date', async () => {
    const user = await openRecord(ID_HOLDER)
    await user.click(await screen.findByRole('button', { name: /Edit Member/i }))
    const initials = await panel().findByLabelText(/^Initials/)
    await user.clear(initials)
    await user.type(initials, 'Q')
    await user.click(screen.getByRole('button', { name: /^Save changes$/ }))

    const dialog = await screen.findByRole('dialog', { name: 'Save these changes?' })
    // The dates it does NOT touch must not be printed as arrays anywhere in that list.
    expect(within(dialog).queryByText(/1988,6,15/)).toBeNull()
  })

  it('⑤ the must-pass: a record with no dates on file still says so, rather than printing a stray dash', async () => {
    await openRecord({ ...PASSPORT, dateOfBirth: null, joinDate: null })

    expect(await panel().findByText('Date of Birth')).toBeInTheDocument()
    expect(valueOf('Date of Birth')).toHaveTextContent('—')
  })
})
