// @vitest-environment jsdom -- drives the real screen and reads what it filled in
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { screen, waitFor } from '@testing-library/react'
import { http, HttpResponse } from 'msw'

import CaptureMember from './CaptureMember'
import { renderWithProviders } from '../../test/renderWithProviders'
import { server } from '../../test/msw/server'
import { pageFixtures } from '../../test/msw/pageFixtures'
import { setupUser } from '../../test/user'

/**
 * 04.2 / 04.3 -- THE BRANCH ALREADY KNOWS THIS, AND NOBODY WAS SHOWN IT.
 *
 * E4: "IBT No." is described in the spec as "auto-generated according to the selected branch". It is not a
 * generation at all -- the branch record carries the number, on `branchCode`, and 66 distinct values are
 * seeded. It was a free-text box that an administrator typed by hand, from a value the screen was already
 * holding in state.
 *
 * E6: the same for Bank Address. `MemberServiceImpl` has always derived it on save, after the POST, where the
 * person capturing the member never saw it. Nothing new is computed here; a derivation that already existed
 * is shown to the person it was always for. `formatBranchAddress` matches the server's own formatter part for
 * part so that the line approved on screen is the line that is stored.
 *
 * 04.3: and the debit DATE -- the one thing this screen can tell the member that no other screen can. The day
 * chosen is the 31st; the date debited is a business day, and when those differ the sentence says why.
 *
 * S4 IS THE RULE THAT MAKES ANY OF THIS SAFE. A derivation must never win an argument with a typed value.
 * The defect that rule comes from is `Initials`, which derived correctly and then overwrote the administrator
 * forever -- a fill you cannot correct is worse than no fill at all. Both derived fields are tested for it,
 * and each is tested through a RE-DERIVATION (changing the branch) rather than only immediately after typing,
 * because "not overwritten yet" and "never overwritten" are different properties.
 */

const NEDBANK = {
  id: 1, bankName: 'Nedbank Limited', branchName: 'Wits', branchCode: '198765', universalBranchCode: '198765',
  physicalAddress: {
    streetNumber: '135', streetName: 'Rivonia Rd',
    city: { id: 1, name: 'Sandton' }, province: { id: 2, name: 'Gauteng' },
  },
}

/** A second branch of the SAME bank: switching between them is what re-derives. */
const NEDBANK_CT = {
  id: 2, bankName: 'Nedbank Limited', branchName: 'Clocktower', branchCode: '198766', universalBranchCode: '198766',
  physicalAddress: { streetNumber: '1', streetName: 'Dock Rd', city: { id: 3, name: 'Cape Town' } },
}

/** `GET /api/acb/holidays?year=2026` as the server answers it: V47's statutory rows for the year (D30). */
const CALENDAR_2026 = {
  name: 'za-statutory', knownUntil: '2027-12-31', endingSoon: false, year: 2026,
  holidays: [
    ['2026-01-01', "New Year's Day"], ['2026-03-21', 'Human Rights Day'], ['2026-04-03', 'Good Friday'],
    ['2026-04-06', 'Family Day'], ['2026-04-27', 'Freedom Day'], ['2026-05-01', "Workers' Day"],
    ['2026-06-16', 'Youth Day'], ['2026-08-09', "National Women's Day"],
    ['2026-08-10', "National Women's Day, observed"], ['2026-09-24', 'Heritage Day'],
    ['2026-12-16', 'Day of Reconciliation'], ['2026-12-25', 'Christmas Day'], ['2026-12-26', 'Day of Goodwill'],
  ].map(([date, name]) => ({ date, name, source: 'STATUTORY' })),
}

/** The specific handlers go FIRST -- pageFixtures is a wildcard and msw takes the first match. */
beforeEach(() => {
  vi.spyOn(console, 'error').mockImplementation(() => {})
  server.use(
    // Two DIFFERENT shapes, as the server serves them: /api/banks is the banks, /api/banks/branches
    // is one row per branch. Both rows below are the same bank, which is what makes the branch the
    // only thing that changes between the two derivations.
    http.get('*/api/banks', () => HttpResponse.json([{ id: 1, name: 'Nedbank Limited' }])),
    http.get('*/api/banks/branches', () => HttpResponse.json([NEDBANK, NEDBANK_CT])),
    ...pageFixtures,
  )
})

const openEmployment = async (user) =>
  user.click(await screen.findByRole('button', { name: /^Employment & Bank/ }))

const openPersonal = async (user) =>
  user.click(await screen.findByRole('button', { name: /^Personal Information/ }))

/** A searchable Field: clear any held choice, type into its own input, take the option by name. */
async function chooseFrom(user, labelText, typed, optionName) {
  const clear = screen.queryByRole('button', { name: new RegExp(`^Clear ${labelText}`) })
  if (clear) await user.click(clear)
  const label = await screen.findByText(new RegExp(`^${labelText}`))
  const input = label.closest('div').querySelector('input')
  await user.type(input, typed)
  await user.click(await screen.findByRole('option', { name: optionName }))
}

const ibtField = () => screen.getByLabelText(/^IBT No\./)
const addressField = () => screen.getByPlaceholderText(/Start typing a bank address/i)

async function chooseWits(user) {
  await openEmployment(user)
  await chooseFrom(user, 'Bank Where Employed', 'Nedbank', 'Nedbank Limited')
  await chooseFrom(user, 'Bank Branch', 'Wits', 'Wits')
}

describe('E4 -- IBT No. comes from the branch, not from someone typing it', () => {
  it('🔴 fills from the branch code, and says where it came from', async () => {
    const user = setupUser()
    renderWithProviders(<CaptureMember />)
    await openEmployment(user)
    expect(ibtField(), 'nothing is derived before a branch is chosen').toHaveValue('')

    await chooseWits(user)

    await waitFor(() => expect(ibtField()).toHaveValue('198765'))
    // S3: "auto-filled" answers half the question. WHICH source is the other half.
    expect(screen.getByText(/Filled in from the branch's code/i)).toBeInTheDocument()
  })

  it('re-derives when a different branch of the same bank is chosen', async () => {
    // The must-pass beside the S4 test below: without this, "never overwrites" would be
    // satisfied by a field that fills once and then goes stale.
    const user = setupUser()
    renderWithProviders(<CaptureMember />)
    await chooseWits(user)
    await waitFor(() => expect(ibtField()).toHaveValue('198765'))

    await chooseFrom(user, 'Bank Branch', 'Clock', 'Clocktower')
    await waitFor(() => expect(ibtField()).toHaveValue('198766'))
  })

  it('🔑 S4: once the administrator types, a re-derivation does not reclaim the field', async () => {
    const user = setupUser()
    renderWithProviders(<CaptureMember />)
    await chooseWits(user)
    await waitFor(() => expect(ibtField()).toHaveValue('198765'))

    await user.clear(ibtField())
    await user.type(ibtField(), '000000')
    expect(ibtField()).toHaveValue('000000')

    // Changing the branch is exactly what recomputes it. This is the moment `Initials` got wrong.
    await chooseFrom(user, 'Bank Branch', 'Clock', 'Clocktower')
    await openPersonal(user)
    await openEmployment(user)
    await waitFor(() => expect(ibtField()).toHaveValue('000000'))
  })
})

describe('E6 -- Bank Address comes from the branch record', () => {
  it('🔴 fills from the branch, formatted the way the server stores it', async () => {
    const user = setupUser()
    renderWithProviders(<CaptureMember />)
    await chooseWits(user)

    // The server's own order: streetNumber, streetName, suburb, city, province, postalCode -- blanks dropped.
    await waitFor(() => expect(addressField()).toHaveValue('135, Rivonia Rd, Sandton, Gauteng'))
    expect(screen.getByText(/Filled in from the branch's address/i)).toBeInTheDocument()
  })

  it('🔑 S4: a corrected address survives a re-derivation', async () => {
    const user = setupUser()
    renderWithProviders(<CaptureMember />)
    await chooseWits(user)
    await waitFor(() => expect(addressField()).toHaveValue('135, Rivonia Rd, Sandton, Gauteng'))

    await user.clear(addressField())
    await user.type(addressField(), '1 Corrected Road')

    await chooseFrom(user, 'Bank Branch', 'Clock', 'Clocktower')
    await openPersonal(user)
    await openEmployment(user)
    await waitFor(() => expect(addressField()).toHaveValue('1 Corrected Road'))
  })

  it('a branch the listing does not know fills nothing, and says so rather than going quiet', async () => {
    // NO_MATCH is not SOURCE_UNAVAILABLE and neither is an empty box. The fourteen dev branch names are
    // offered for every bank and belong to none of them, so this is the ordinary case, not an edge.
    const user = setupUser()
    renderWithProviders(<CaptureMember />)
    await openEmployment(user)
    await chooseFrom(user, 'Bank Where Employed', 'Nedbank', 'Nedbank Limited')
    await chooseFrom(user, 'Bank Branch', 'Soweto', 'Soweto')

    // BOTH fields say it, and both name the branch -- "nothing found" on its own reads as a fault.
    await waitFor(() => expect(screen.getAllByText(/Nothing found for Soweto/i)).toHaveLength(2))
    expect(addressField()).toHaveValue('')
    expect(ibtField()).toHaveValue('')
  })
})

describe('04.3 -- the actual debit date, under the field that chooses the day', () => {
  it('says WHEN the member will be debited', async () => {
    const user = setupUser()
    renderWithProviders(<CaptureMember />)
    await openEmployment(user)

    // Payment Type defaults to Debit Order and the day to the 20th, so the sentence is on screen with no
    // interaction at all -- which is the point: it is not a warning, it is what is going to happen.
    const note = await screen.findByText(/will be debited on/i)
    expect(note.textContent).toMatch(/debited on \w+day \d+ \w+\./)
  })

  it('explains an adjustment rather than quietly showing a different day', async () => {
    const user = setupUser()
    renderWithProviders(<CaptureMember />)
    await openEmployment(user)

    // Walk the whole permitted range. Whenever the sentence names a day other than the one chosen it must
    // also say why -- a date that silently differs from the field above it reads as a bug.
    const day = screen.getByLabelText(/^Debit Day/)
    for (const chosen of ['20', '24', '28', '31']) {
      await user.selectOptions(day, chosen)
      const note = await screen.findByText(/will be debited on/i)
      const named = note.textContent.match(/debited on \w+day (\d+)/)[1]
      if (named !== chosen) {
        expect(note.textContent, `chose ${chosen}, showed ${named}`).toMatch(
          /has no \d+\w\w|not a business day/,
        )
      }
    }
  })

  it('🔴 with the run\'s calendar it gives the run\'s date -- day 24 in Heritage Day\'s week is Friday the 25th', async () => {
    // The presentation arc's finding, 2026-09-21: the sentence counted weekends only while the run also steps
    // over public holidays (D30), and an UNMOVED date carried no caveat -- so Thursday 24 September 2026,
    // Heritage Day, was shown as certain. The clock is pinned to that week; only Date is faked, and setup.js
    // puts the real one back after every test.
    vi.useFakeTimers({ toFake: ['Date'] })
    vi.setSystemTime(new Date(2026, 8, 22, 9))
    const asked = []
    server.use(http.get('*/api/acb/holidays', ({ request }) => {
      asked.push(new URL(request.url).searchParams.get('year'))
      return HttpResponse.json(CALENDAR_2026)
    }))
    const user = setupUser()
    renderWithProviders(<CaptureMember />)
    await openEmployment(user)
    const day = screen.getByLabelText(/^Debit Day/)

    await user.selectOptions(day, '24')
    await waitFor(() => expect(screen.getByText(/will be debited on/i).textContent).toBe(
      'That day is not a business day, so this member will be debited on Friday 25 September.'))
    expect(asked, 'the page reads the calendar of the year it is in').toContain('2026')

    for (const chosen of ['20', '21', '22', '23', '24', '25', '26', '27', '28', '29', '30', '31']) {
      await user.selectOptions(day, chosen)
      const note = await screen.findByText(/will be debited on/i)
      expect(note.textContent, `day ${chosen}: the calendar is known, so nothing is caveated`).not.toMatch(/Weekends only/)
    }
  })

  it('🔴 without the calendar, every date says it counts weekends only -- moved or not', async () => {
    // Asserted on the UNMOVED dates as well: an unmoved weekday can be a public holiday, which is the case the
    // caveat exists for. It used to travel with the adjustment alone.
    vi.spyOn(console, 'error').mockImplementation(() => {})
    server.use(http.get('*/api/acb/holidays', () => HttpResponse.json({ status: 500, message: 'boom' }, { status: 500 })))
    const user = setupUser()
    renderWithProviders(<CaptureMember />)
    await openEmployment(user)
    const day = screen.getByLabelText(/^Debit Day/)

    const unmoved = []
    for (const chosen of ['20', '21', '22', '23', '24', '25', '26', '27', '28', '29', '30', '31']) {
      await user.selectOptions(day, chosen)
      const note = await screen.findByText(/will be debited on/i)
      expect(note.textContent, `day ${chosen}`).toMatch(/Weekends only — public holidays are not accounted for\.$/)
      if (!/has no \d+\w\w|not a business day/.test(note.textContent)) unmoved.push(chosen)
    }
    // Non-vacuity: any month has weekdays among the twelve, so the unmoved case was asserted.
    expect(unmoved.length, 'every day in the range was moved, so the unmoved case went unasserted').toBeGreaterThan(0)
  })
})
