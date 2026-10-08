// @vitest-environment jsdom -- what Capture Member actually POSTs
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { screen, waitFor } from '@testing-library/react'
import { http, HttpResponse } from 'msw'

import CaptureMember from './CaptureMember'
import { renderWithProviders } from '../../test/renderWithProviders'
import { server } from '../../test/msw/server'
import { pageFixtures } from '../../test/msw/pageFixtures'
import { setupUser } from '../../test/user'

/**
 * 03.1 / 03.2 -- THE PAYLOAD IS THE CONTRACT, AND A HIDDEN TAB IS NOT A REMOVED FIELD.
 *
 * Two whole tabs came off this screen: Sasbo Structures (03.1 -- "a new member cannot immediately sit on a
 * structure", the stakeholder, 2026-09-09) and History & Remarks (03.2). Their CONTROLS went. Their keys
 * stayed in the initial form state, and `handleSave` posts the form, so every capture has gone on sending
 * them as defaults ever since -- a field nobody can see, nobody can set, and nobody can stop sending.
 *
 * 🔑 WHY THAT IS A DEFECT AND NOT MERELY UNTIDY. `useAltAddress: "N"`, `deductACB: "Y"` and
 * `printMembershipCards: "Y"` are not empty strings -- they are OPINIONS, written into every new member's
 * record by a form that never asked. The day one of those columns starts being read, the answer will be
 * this file's default rather than anybody's decision, and there will be nothing on screen to contradict.
 * `03.1` records the same shape from the other side: adding a column turned a harmlessly-ignored array
 * field fatal.
 *
 * 🔑 WRITTEN AS A CLOSED SET, NOT AS SIXTEEN ABSENCES. The assertion below is that the posted keys are
 * EXACTLY the form's own -- so a key re-appearing reddens this whether or not anyone thought to name it,
 * which is the property a list of `not.toHaveProperty` calls does not have.
 *
 * ⚖️ CLEARED WITH THE AUTOMATION ARC BEFORE LANDING (sasbo-online-ed, 2026-09-17), because
 * `pages/MemberCapturePage.js` records posted defaults and their `captureCatalogue.spec.js` holds a closed
 * list of the removed tabs' fields. Their measurement: the suite asserts the READ-BACK of the 22 fields its
 * one live round-trip scenario types, none of the sixteen is one of them, nine already map to catalogue
 * rows on removed tabs and seven are in the catalogue nowhere. No window needed, and the twelve-label
 * guard does not move.
 */

/** The sixteen keys with no control anywhere on this screen -- each appeared exactly once, in the state. */
const REMOVED = [
  'workBankAddress',
  'repElectionDay', 'repElectionMonth', 'repElectionYear',
  'remarks', 'remarks2', 'remarks3', 'remarks4',
  'memnetMember', 'printLabelForPostage', 'suspensionCode',
  'useAltAddress', 'deductACB', 'printMembershipCards', 'homeLoanReducedRateOption',
  'structure1',
]

/**
 * The 22 the automation suite's live round-trip scenario types and reads back (00.5 added Account Type). Named here because this
 * test is the one that could break them: it is the ONLY place that asserts the payload as a closed set, so
 * it is where a field would be lost without anybody noticing until a containerised run.
 */
const AUTOMATION_ROUND_TRIP = [
  'title', 'firstNames', 'initials', 'surname', 'identityType', 'identityNumber', 'race',
  'personalEmail', 'workEmail', 'phone', 'workTel', 'homeTel', 'address',
  'employeeNumber', 'employmentType', 'bankName', 'branch', 'paymentType',
  'bankAccountNumber', 'accountType', 'debitDay', 'ibtNo',
]

let posted

beforeEach(() => {
  posted = undefined
  vi.spyOn(console, 'error').mockImplementation(() => {})
  server.use(
    ...pageFixtures,
    http.post('*/api/members', async ({ request }) => {
      posted = await request.json()
      return HttpResponse.json({ id: 1, ...posted })
    }),
  )
})

/** The smallest capture the form accepts: Payroll Deduction needs no bank account, bank or branch. */
async function captureMinimalMember(user) {
  const field = (text) => screen.getByLabelText(new RegExp(`^${text}`))
  await screen.findByLabelText(/^First Name\(s\)/)
  await user.type(field('First Name\\(s\\)'), 'Synthetic')
  await user.type(field('Surname'), 'Testperson')
  await user.type(field('Personal Email'), 'synthetic@example.test')
  await user.selectOptions(field('Identity Type'), 'I.D. Number')
  await user.type(field('I.D. Number'), '9001010000080')

  await user.click(screen.getByRole('button', { name: /^Employment & Bank/ }))
  await user.selectOptions(field('Payment Type'), 'Payroll Deduction')

  await user.click(screen.getByRole('button', { name: /save member/i }))
  const sendBtn = await screen.findByRole('button', { name: /send welcome letter/i })
  await user.click(sendBtn)
  await waitFor(() => expect(posted).toBeDefined())
}

describe('what a capture actually sends', () => {
  it('🔴 sends none of the removed tabs fields', async () => {
    const user = setupUser()
    renderWithProviders(<CaptureMember />)
    await captureMinimalMember(user)

    const stillSent = REMOVED.filter((key) => Object.hasOwn(posted, key))
    expect(
      stillSent,
      'A field whose control was removed is still being posted. It cannot be seen, cannot be set and ' +
        'cannot be stopped -- and three of these are opinions ("N", "Y", "Y"), not blanks.',
    ).toEqual([])
  })

  it('🔑 and the payload is a CLOSED set, so a key cannot creep back unnoticed', async () => {
    const user = setupUser()
    renderWithProviders(<CaptureMember />)
    await captureMinimalMember(user)

    // Everything the form legitimately holds, plus the two the save adds itself. 🗣️ No `areaRecruited` since
    // 18 Sep 2026: SASBO asked for the area to be read off the recruiter, so no control sets it and nothing posts it.
    // 🏢 `workplaceId` since month end 01.3 (BR46), in the commit that added its control, the Workplace field: the one
    // way a save names a workplace. Always sent, `null` until one is chosen -- which the server reads as no workplace.
    const expected = [
      ...AUTOMATION_ROUND_TRIP,
      'dateOfBirth', 'gender', 'preferredEmail', 'joinDate', 'status',
      'subscriptionFrequency', 'jobGrade', 'bankAddress',
      'recruitedHow', 'recruitmentRep',
      'workplaceId',
      'monthlySubscription',
    ].sort()

    expect(
      Object.keys(posted).sort(),
      'The posted keys are not the set this screen means to send. A NEW key here is a field nobody can ' +
        'see being written to every member; a MISSING one is a field the administrator filled in and the ' +
        'server never received -- R5, which is how the recruiter came to be dropped on every save.',
    ).toEqual(expected)
  })

  it('🔑 the must-pass: every field the automation round-trip types still reaches the server', async () => {
    // The half that catches a trim that went too far. Without it, "sends none of the removed fields"
    // is satisfied perfectly by a form that sends nothing at all.
    const user = setupUser()
    renderWithProviders(<CaptureMember />)
    await captureMinimalMember(user)

    const missing = AUTOMATION_ROUND_TRIP.filter((key) => !Object.hasOwn(posted, key))
    expect(
      missing,
      'The automation suite re-reads GET /api/members and compares these 22 fields against what its ' +
        'scenario typed. A key missing here fails a containerised run nobody is watching from this repo.',
    ).toEqual([])
  })

  it('and the values that ARE sent are the ones that were typed', async () => {
    // Non-vacuity for the two assertions above: they are about key SETS, and a form posting the right
    // keys with everything blanked would satisfy both.
    const user = setupUser()
    renderWithProviders(<CaptureMember />)
    await captureMinimalMember(user)

    expect(posted.firstNames).toBe('Synthetic')
    expect(posted.surname).toBe('Testperson')
    expect(posted.identityNumber).toBe('9001010000080')
    expect(posted.paymentType).toBe('Payroll Deduction')
  })
})
