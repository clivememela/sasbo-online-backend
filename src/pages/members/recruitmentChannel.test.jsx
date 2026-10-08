// @vitest-environment jsdom -- the Recruitment Channel tab on both member screens
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { screen, waitFor, within } from '@testing-library/react'
import { http, HttpResponse } from 'msw'

import CaptureMember from './CaptureMember'
import MembersPage from './Members'
import { renderWithProviders } from '../../test/renderWithProviders'
import { server } from '../../test/msw/server'
import { pageFixtures } from '../../test/msw/pageFixtures'
import { registerHandlers } from '../../test/msw/memberRegister'
import { setupUser } from '../../test/user'

/**
 * 03.3 / 03.8 / 03.9 -- THE THREE THINGS THE STAKEHOLDER ASKED THIS TAB TO CAPTURE, AND 18 SEP 2026's RULES FOR THEM.
 *
 * Their words, 2026-09-09: "1. How the member was recruited. 2. The area where the member was recruited.
 * 3. Who recruited the member, where applicable."
 *
 * 🗣️ S6, settled by the owner the same day. The older spec asks for a tab called "Recruiter Type"; the
 * stakeholder calls the tab "Recruitment Channel", twice, on both screens. Both are right about different
 * things -- "Recruiter Type" was never the TAB, it names the KIND of recruitment, which is what field (1)
 * holds. So the tab keeps the stakeholder's name and the FIELD takes the spec's.
 *
 * 🗣️ SASBO, 18 SEP 2026 -- the type decides the rest (`utils/recruitment.js`). "If I choose website, everything else
 * must fall away." "If I say RO, then RO recruiter names must come here." "Area should be linked to the recruiter ...
 * area recruited should not be selection based." So the order is type, recruiter, area; Website and EORO offer
 * neither; RO offers the organisers; and the area is READ OFF the recruiter -- their demarcation letter -- never picked
 * from a list. The list it used to be picked from was `/api/areas`, the bank branches' suburbs: where a member WORKS.
 *
 * 🔑 R2 / RE-3's lesson still holds, and is why the area assertions check the VALUE's kind rather than a label: the
 * field once captured a person under an area-shaped name, and a label that reads right over the wrong kind of thing is
 * worse than an obviously wrong one.
 *
 * S6/R5: the drop-down and the member's stored recruiter read the SAME table -- `GET /api/recruiters`, read-only by
 * design. `/api/representatives` administers representatives, which is Edit Member's business.
 */

const CHANNELS = [
  { id: 1, name: 'Website' }, { id: 2, name: 'BEC' }, { id: 3, name: 'EORO' }, { id: 4, name: 'RO' }, { id: 5, name: 'MSO' },
]
/** The suburbs the area used to be chosen from. Served, so a screen still asking for them would be seen asking. */
const AREAS = [{ id: 1, name: 'Gauteng South' }, { id: 2, name: 'Western Cape Metro' }]
/**
 * The recruiter table's SHAPE: organisers carry a region and (mostly) an area letter; the channel rows it also holds
 * carry neither. Synthetic people.
 */
const RECRUITERS = [
  { id: 1, name: 'Achmat Adams', region: 'JOHANNESBURG', areaCode: 'X' },
  { id: 2, name: 'Hanri Maritz', region: 'CAPE TOWN' },
  { id: 3, name: 'WEBSITE' },
]
/** Deliberately DIFFERENT people: a screen reading the wrong endpoint offers these instead. */
const REPRESENTATIVES = [{ id: 9, name: 'Zelda Representative' }]

let requested

beforeEach(() => {
  requested = []
  vi.spyOn(console, 'error').mockImplementation(() => {})
  server.events.removeAllListeners('request:start')
  server.events.on('request:start', ({ request }) => requested.push(new URL(request.url).pathname))
  server.use(
    http.get('*/api/recruitment-channels', () => HttpResponse.json(CHANNELS)),
    http.get('*/api/areas', () => HttpResponse.json(AREAS)),
    http.get('*/api/recruiters', () => HttpResponse.json(RECRUITERS)),
    http.get('*/api/representatives', () => HttpResponse.json(REPRESENTATIVES)),
    ...pageFixtures,
  )
})

const openChannel = async (user) =>
  user.click(await screen.findByRole('button', { name: /^Recruitment Channel/ }))

/** Open a searchable Field's listbox and read its options. */
async function optionsOf(user, labelText) {
  const label = await screen.findByText(new RegExp(`^${labelText}`))
  const input = label.closest('div').querySelector('input')
  await user.click(input)
  const listbox = await screen.findByRole('listbox')
  const names = within(listbox).getAllByRole('option').map((o) => o.textContent.trim())
  await user.keyboard('{Escape}')
  return names
}

async function chooseFrom(user, labelText, typed, optionName) {
  const label = await screen.findByText(new RegExp(`^${labelText}`))
  const input = label.closest('div').querySelector('input')
  await user.type(input, typed)
  await user.click(await screen.findByRole('option', { name: optionName }))
}

/** The Area Recruited control: read-only, so a plain disabled text input, never a picker. */
const areaInput = () => screen.getByLabelText(/^Area Recruited/)

describe('Capture Member · the recruitment channel, as SASBO gave it on 18 Sep 2026', () => {
  it('🗣️ offers Recruiter Type, Recruiter and Area Recruited -- in that order, and no "Area\'s Recruiter"', async () => {
    const user = setupUser()
    renderWithProviders(<CaptureMember />)
    await openChannel(user)

    const type = await screen.findByText(/^Recruiter Type/)
    const who = screen.getByText(/^Recruiter$/)
    const area = screen.getByText(/^Area Recruited/)

    expect(type.compareDocumentPosition(who) & Node.DOCUMENT_POSITION_FOLLOWING, 'Recruiter follows Recruiter Type').toBeTruthy()
    expect(who.compareDocumentPosition(area) & Node.DOCUMENT_POSITION_FOLLOWING, 'Area Recruited follows Recruiter').toBeTruthy()

    // R2/RE-3: the person-capturing field under an area-shaped name is GONE, not relabelled.
    expect(screen.queryByText(/Area's Recruiter/)).not.toBeInTheDocument()
  })

  it('🗣️ the type comes first: no recruiter can be chosen until one is', async () => {
    const user = setupUser()
    renderWithProviders(<CaptureMember />)
    await openChannel(user)

    expect(await screen.findByText('Choose the recruiter type first.')).toBeInTheDocument()
    expect(screen.getByText(/^Recruiter$/).closest('div').querySelector('input')).toBeDisabled()
  })

  it('🔑 RO offers the organisers only -- not the channel rows the recruiter table also holds', async () => {
    const user = setupUser()
    renderWithProviders(<CaptureMember />)
    await openChannel(user)
    await chooseFrom(user, 'Recruiter Type', 'RO', 'RO')

    expect(await optionsOf(user, 'Recruiter$')).toEqual(['Achmat Adams', 'Hanri Maritz'])
  })

  it('🔑 the area is READ OFF the recruiter -- their letter and region -- and says from whom', async () => {
    const user = setupUser()
    renderWithProviders(<CaptureMember />)
    await openChannel(user)
    await chooseFrom(user, 'Recruiter Type', 'RO', 'RO')
    await chooseFrom(user, 'Recruiter$', 'Achmat', 'Achmat Adams')

    expect(areaInput()).toHaveValue('Area X · Johannesburg')
    expect(areaInput()).toBeDisabled()
    expect(screen.getByText("Filled in from Achmat Adams's area.")).toBeInTheDocument()
  })

  it('🔴 and it is never chosen: no suburb list is asked for, offered or shown', async () => {
    // The must-pass beside the derivation: a screen that still loaded /api/areas into a picker would pass the case
    // above only if the derivation happened to win, so the absence of the old list is asserted outright.
    const user = setupUser()
    renderWithProviders(<CaptureMember />)
    await openChannel(user)
    await chooseFrom(user, 'Recruiter Type', 'RO', 'RO')
    await chooseFrom(user, 'Recruiter$', 'Achmat', 'Achmat Adams')

    await waitFor(() => expect(requested).toContain('/api/recruiters'))
    expect(requested, 'the capture screen asked for the bank branches\' suburbs').not.toContain('/api/areas')
    expect(screen.queryByText('Gauteng South')).not.toBeInTheDocument()
    expect(areaInput()).not.toHaveAttribute('role', 'combobox')
  })

  it('an organiser with no area letter on file says so -- never a guess, never a silent blank', async () => {
    const user = setupUser()
    renderWithProviders(<CaptureMember />)
    await openChannel(user)
    await chooseFrom(user, 'Recruiter Type', 'RO', 'RO')
    await chooseFrom(user, 'Recruiter$', 'Hanri', 'Hanri Maritz')

    expect(areaInput()).toHaveValue('')
    expect(screen.getByText('No area is on file for Hanri Maritz yet.')).toBeInTheDocument()
  })

  it.each(['Website', 'EORO'])('🗣️ %s: "everything else must fall away", and the screen says why', async (channel) => {
    const user = setupUser()
    renderWithProviders(<CaptureMember />)
    await openChannel(user)
    await chooseFrom(user, 'Recruiter Type', channel, channel)

    expect(screen.queryByText(/^Recruiter$/)).not.toBeInTheDocument()
    expect(screen.queryByText(/^Area Recruited/)).not.toBeInTheDocument()
    expect(screen.getByText(`No recruiter or area is recorded for a member who joined through ${channel}.`)).toBeInTheDocument()
  })

  it('the must-pass: MSO still offers a recruiter -- everybody, until the table can tell MSOs apart', async () => {
    // Without this, "Website hides the recruiter" is satisfied by a tab that hides it for every type.
    const user = setupUser()
    renderWithProviders(<CaptureMember />)
    await openChannel(user)
    await chooseFrom(user, 'Recruiter Type', 'MSO', 'MSO')

    expect(await optionsOf(user, 'Recruiter$')).toEqual(['Achmat Adams', 'Hanri Maritz', 'WEBSITE'])
  })

  it('a new type drops a recruiter it does not offer, and keeps one it does', async () => {
    const user = setupUser()
    renderWithProviders(<CaptureMember />)
    await openChannel(user)
    await chooseFrom(user, 'Recruiter Type', 'MSO', 'MSO')
    await chooseFrom(user, 'Recruiter$', 'WEB', 'WEBSITE')

    // RO offers organisers only, so the channel row chosen under MSO cannot stand. Asserted through the AREA, not the
    // Recruiter box: `SearchableSelect` shows a value its list lacks as a blank box, so an empty box cannot tell a
    // cleared choice from one still held and still posted. The area reads the held value -- "no area on file for
    // WEBSITE" if it survived, the prompt if it was dropped.
    await chooseFrom(user, 'Recruiter Type', 'RO', 'RO')
    expect(screen.getByText('Follows from the recruiter: choose one and their area fills in.')).toBeInTheDocument()
    expect(screen.queryByText(/No area is on file for WEBSITE/)).not.toBeInTheDocument()

    await chooseFrom(user, 'Recruiter$', 'Achmat', 'Achmat Adams')
    await chooseFrom(user, 'Recruiter Type', 'MSO', 'MSO')
    expect(areaInput(), 'MSO offers Achmat Adams too, so the choice and its area stay').toHaveValue('Area X · Johannesburg')
  })

  it('🔴 clearing the type to pick another is not a choice of "no type" -- the recruiter survives the gap', async () => {
    // The field's clear button passes '' through onChange before the next type is picked. Judged against that '',
    // every recruiter would be dropped on the way from one type to another that offers them too.
    const user = setupUser()
    renderWithProviders(<CaptureMember />)
    await openChannel(user)
    await chooseFrom(user, 'Recruiter Type', 'RO', 'RO')
    await chooseFrom(user, 'Recruiter$', 'Achmat', 'Achmat Adams')

    await user.click(screen.getByRole('button', { name: 'Clear Recruiter Type' }))
    await chooseFrom(user, 'Recruiter Type', 'MSO', 'MSO')

    expect(areaInput()).toHaveValue('Area X · Johannesburg')
  })

  it('🔴 S6/R5: Recruiter comes from /api/recruiters, not /api/representatives', async () => {
    const user = setupUser()
    renderWithProviders(<CaptureMember />)
    await openChannel(user)
    await chooseFrom(user, 'Recruiter Type', 'BEC', 'BEC')

    await waitFor(() => expect(requested).toContain('/api/recruiters'))
    // The must-pass beside it: the two endpoints serve different people, so reading the wrong one shows.
    expect(await optionsOf(user, 'Recruiter$')).not.toContain('Zelda Representative')
  })

  it('Recruiter Type comes from the recruitment channels', async () => {
    const user = setupUser()
    renderWithProviders(<CaptureMember />)
    await openChannel(user)

    expect(await optionsOf(user, 'Recruiter Type')).toEqual(['Website', 'BEC', 'EORO', 'RO', 'MSO'])
  })

  describe('what reaches the request body', () => {
    let posted

    beforeEach(() => {
      posted = undefined
      server.use(http.post('*/api/members', async ({ request }) => {
        posted = await request.json()
        return HttpResponse.json({ id: 1, ...posted })
      }))
    })

    async function captureWith(user, chooseChannel) {
      const field = (text) => screen.getByLabelText(new RegExp(`^${text}`))
      await screen.findByLabelText(/^First Name\(s\)/)
      await user.type(field('First Name\\(s\\)'), 'Synthetic')
      await user.type(field('Surname'), 'Testperson')
      await user.type(field('Personal Email'), 'synthetic@example.test')
      await user.selectOptions(field('Identity Type'), 'I.D. Number')
      await user.type(field('I.D. Number'), '9001010000080')

      // Payroll Deduction so no bank account, bank or branch is required -- this test is about the channel.
      await user.click(screen.getByRole('button', { name: /^Employment & Bank/ }))
      await user.selectOptions(field('Payment Type'), 'Payroll Deduction')

      await openChannel(user)
      await chooseChannel()

      await user.click(screen.getByRole('button', { name: /save member/i }))
      await user.click(await screen.findByRole('button', { name: /send welcome letter/i }))
      await waitFor(() => expect(posted).toBeDefined())
    }

    it('the type and the recruiter, under the names the DTO declares -- and no area, which follows from them', async () => {
      const user = setupUser()
      renderWithProviders(<CaptureMember />)
      await captureWith(user, async () => {
        await chooseFrom(user, 'Recruiter Type', 'RO', 'RO')
        await chooseFrom(user, 'Recruiter$', 'Achmat', 'Achmat Adams')
      })

      // 🔑 The names MemberDto declares. R5 is the defect where the recruiter was dropped on every save,
      // and a field posted under a name the DTO does not carry is dropped in exactly the same silence.
      expect(posted.recruitedHow).toBe('RO')
      expect(posted.recruitmentRep).toBe('Achmat Adams')
      expect(Object.hasOwn(posted, 'areaRecruited'), 'the area is read off the recruiter, not captured').toBe(false)
    })

    it('Website sends the type and no recruiter', async () => {
      const user = setupUser()
      renderWithProviders(<CaptureMember />)
      await captureWith(user, () => chooseFrom(user, 'Recruiter Type', 'Web', 'Website'))

      expect(posted.recruitedHow).toBe('Website')
      expect(posted.recruitmentRep).toBe('')
    })
  })

  it('🗑️ R3-OBS-2a: the elected-recruiter block is gone, not merely unreachable', async () => {
    // It was guarded by `form.electedRep === 'Y'` against a form that has no `electedRep` key, so it
    // could never render. Dead code that LOOKS live is how a removed field keeps being reasoned about.
    const user = setupUser()
    renderWithProviders(<CaptureMember />)
    await openChannel(user)

    expect(screen.queryByText(/Position \/ Structure/)).not.toBeInTheDocument()
    expect(screen.queryByText(/Election Date/)).not.toBeInTheDocument()
  })
})

describe('Edit Member · 03.9, the same three, read-only', () => {
  const MEMBER = {
    id: 7, membershipNumber: 'M0007', firstNames: 'Synthetic', surname: 'Alpha', initials: 'S',
    identityType: 'I.D. Number', identityNumber: '9001010000080', status: 'Active',
    paymentType: 'Payroll Deduction',
    recruitedHow: 'EORO', areaRecruited: 'Western Cape Metro', recruitmentRepresentativeName: 'Hanri Maritz',
  }

  /** Open the one member's record on its Recruitment tab. */
  async function openRecruitment(user, { editing = false } = {}) {
    await user.click(await screen.findByRole('button', { name: /open the record for/i }))
    if (editing) await user.click(await screen.findByRole('button', { name: /Edit Member/i }))
    await user.click(await screen.findByRole('button', { name: /^Recruitment/ }))
    return within(screen.getByTestId('member-panel'))
  }

  it('🗣️ shows all three and offers no control to change them', async () => {
    // The stakeholder: recruitment "should only show ... should not be editable from there".
    server.use(...registerHandlers([MEMBER]))
    const user = setupUser()
    renderWithProviders(<MembersPage />)
    const panel = await openRecruitment(user, { editing: true })

    expect(panel.getByText('EORO')).toBeInTheDocument()
    expect(panel.getByText('Western Cape Metro')).toBeInTheDocument()
    expect(panel.getByText('Hanri Maritz')).toBeInTheDocument()

    // Read-only EVEN WHILE EDITING -- which is the whole instruction. Asserted over the tab's own
    // region rather than the page, so an unrelated control elsewhere cannot make this pass or fail.
    const region = panel.getByText('EORO').closest('[data-tab-panel]') ?? screen.getByTestId('member-panel')
    expect(within(region).queryAllByRole('combobox')).toHaveLength(0)
    expect(within(region).queryAllByRole('textbox')).toHaveLength(0)
  })

  it('🔑 a STORED area is shown as held -- a historical fact -- and the recruiter list is not even asked for', async () => {
    server.use(...registerHandlers([MEMBER]))
    const user = setupUser()
    renderWithProviders(<MembersPage />)
    const panel = await openRecruitment(user)

    expect(panel.getByText('Western Cape Metro')).toBeInTheDocument()
    expect(requested).not.toContain('/api/recruiters')
  })

  it('🗣️ with no stored area, it is read off the recruiter, and the record says whose it is', async () => {
    server.use(...registerHandlers([{ ...MEMBER, recruitedHow: 'RO', areaRecruited: null, recruitmentRepresentativeName: 'Achmat Adams' }]))
    const user = setupUser()
    renderWithProviders(<MembersPage />)
    const panel = await openRecruitment(user)

    expect(await panel.findByText('Area X · Johannesburg')).toBeInTheDocument()
    expect(panel.getByText("Filled in from Achmat Adams's area.")).toBeInTheDocument()
  })

  it('🔴 and when the recruiter list fails, the record says the area cannot be shown -- not that there is none', async () => {
    server.use(
      ...registerHandlers([{ ...MEMBER, recruitedHow: 'RO', areaRecruited: null, recruitmentRepresentativeName: 'Achmat Adams' }]),
      http.get('*/api/recruiters', () => HttpResponse.json({ status: 500, error: 'boom' }, { status: 500 })),
    )
    const user = setupUser()
    renderWithProviders(<MembersPage />)
    const panel = await openRecruitment(user)

    expect(await panel.findByText('The recruiter list is unavailable, so the area cannot be shown yet.')).toBeInTheDocument()
    expect(panel.queryByText(/No area is on file/)).not.toBeInTheDocument()
  })
})
