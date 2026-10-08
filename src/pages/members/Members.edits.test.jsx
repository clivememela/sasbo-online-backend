// @vitest-environment jsdom -- the member record's edit: never thrown away without a word, and a save that shows it is running
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { screen, waitFor, within } from '@testing-library/react'
import { http, HttpResponse } from 'msw'

import MembersPage from './Members'
import { renderWithProviders } from '../../test/renderWithProviders'
import { server } from '../../test/msw/server'
import { pageFixtures } from '../../test/msw/pageFixtures'
import { setupUser } from '../../test/user'
import { deferred } from '../../test/deferred'
import { registerHandlers } from '../../test/msw/memberRegister'

/**
 * EDITS ARE NEVER LOST SILENTLY. `06.5`, the `06.3` critique, F-P55.
 *
 * 🔴 With the record in edit mode and a field changed, choosing another member, the close button, the scrim or
 * Escape threw the change away without a word -- and the next member opened already in edit mode. "Save changes"
 * closed its confirmation before the write and showed nothing while the write ran.
 *
 * The pairs, because each guard has a wrong fix that satisfies it alone:
 *   ① a changed edit asks before switching            ② and "Discard changes" still switches, to a record to read
 *   ③ an edit with NO changes switches without asking, and the next member opens to be read
 *   ④ the close button asks too
 *   ⑤ the confirmation stays open, busy, until the save has answered -- and then closes and says so
 *   ⑥ COSATU positions that arrive as the server's one string are saved back as a list, untouched
 */

const ALPHA = {
  id: 7,
  membershipNumber: 'M0007',
  firstNames: 'Synthetic',
  surname: 'Alpha',
  initials: 'S',
  identityType: 'I.D. Number',
  identityNumber: '9001010000080',
  status: 'Active',
  paymentType: 'Payroll Deduction',
}
const BRAVO = { ...ALPHA, id: 8, membershipNumber: 'M0008', surname: 'Bravo', identityNumber: '8506155000084' }

const panel = () => within(screen.getByTestId('member-panel'))

async function editAlpha({ change }) {
  const user = setupUser()
  renderWithProviders(<MembersPage />)
  await user.click(await screen.findByRole('button', { name: /open the record for.*Alpha/i }))
  await user.click(await screen.findByRole('button', { name: /Edit Member/i }))
  if (change) {
    // In the record: the register's criteria row has an Initials search of its own.
    const initials = await panel().findByLabelText(/^Initials/)
    await user.clear(initials)
    await user.type(initials, 'Q')
  }
  return user
}

describe('the member record, mid-edit', () => {
  beforeEach(() => {
    vi.spyOn(console, 'error').mockImplementation(() => {})
    // Two `use` calls: the catch-all first, then the specific handlers, which prepend.
    server.use(...pageFixtures)
    server.use(...registerHandlers([ALPHA, BRAVO]))
  })

  it('① choosing another member with a changed field asks first -- and "Keep editing" keeps the change', async () => {
    const user = await editAlpha({ change: true })
    await user.click(screen.getByRole('button', { name: /open the record for.*Bravo/i }))

    const dialog = await screen.findByRole('dialog', { name: /Discard your changes/ })
    await user.click(within(dialog).getByRole('button', { name: 'Keep editing' }))

    await waitFor(() => expect(screen.queryByRole('dialog', { name: /Discard your changes/ })).not.toBeInTheDocument())
    expect(panel().getByLabelText(/^Initials/)).toHaveValue('Q')
    expect(panel().getByText('Synthetic Alpha')).toBeInTheDocument()
  })

  it('② the must-pass: "Discard changes" switches, and the next member opens to be read', async () => {
    const user = await editAlpha({ change: true })
    await user.click(screen.getByRole('button', { name: /open the record for.*Bravo/i }))
    const dialog = await screen.findByRole('dialog', { name: /Discard your changes/ })
    await user.click(within(dialog).getByRole('button', { name: 'Discard changes' }))

    expect(await panel().findByText('Synthetic Bravo')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /Edit Member/i })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /^Save changes$/ })).not.toBeInTheDocument()
  })

  it('③ an edit with no changes switches without asking -- and the next member opens to be read, not edited', async () => {
    const user = await editAlpha({ change: false })
    await user.click(screen.getByRole('button', { name: /open the record for.*Bravo/i }))

    expect(await panel().findByText('Synthetic Bravo')).toBeInTheDocument()
    expect(screen.queryByRole('dialog', { name: /Discard your changes/ })).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: /Edit Member/i })).toBeInTheDocument()
  })

  it('④ the close button asks too, when the edit has changes', async () => {
    const user = await editAlpha({ change: true })
    await user.click(panel().getByRole('button', { name: 'Close member details' }))

    expect(await screen.findByRole('dialog', { name: /Discard your changes/ })).toBeInTheDocument()
    expect(panel().getByLabelText(/^Initials/)).toHaveValue('Q')
  })

  /*
   * ⑤ and ⑥ — ESCAPE, THE WAY OUT NOTHING TESTED. It is not the record's own control: the listener
   * is window-level, registered once in `Members.jsx` (the guarded close), and it predates the record ever
   * being a dialog -- so it is the docked panel's way out too, and it must ask exactly as the close button
   * does. ⑥ is the must-pass: without it, a ⑤ that passed because Escape did nothing at all would look
   * identical to one that passed because the guard held.
   */
  it('⑤ Escape asks too, when the edit has changes -- the same guard, a different way out', async () => {
    const user = await editAlpha({ change: true })
    await user.keyboard('{Escape}')

    expect(await screen.findByRole('dialog', { name: /Discard your changes/ })).toBeInTheDocument()
    expect(panel().getByLabelText(/^Initials/)).toHaveValue('Q')
  })

  it('⑥ the must-pass: Escape on an edit with no changes closes the record, without asking', async () => {
    const user = await editAlpha({ change: false })
    await user.keyboard('{Escape}')

    await waitFor(() => expect(screen.getByTestId('member-panel')).not.toHaveAttribute('data-open'))
    expect(screen.queryByRole('dialog', { name: /Discard your changes/ })).not.toBeInTheDocument()
  })

  it('⑦ the save confirmation stays open and busy until the save has answered, then closes and says so', async () => {
    const { promise: answered, resolve: answer } = deferred()
    server.use(
      http.put('*/api/members/:id', async ({ request }) => {
        const body = await request.json()
        await answered
        return HttpResponse.json(body)
      }),
    )
    const user = await editAlpha({ change: true })
    await user.click(screen.getByRole('button', { name: /^Save changes$/ }))
    const dialog = await screen.findByRole('dialog', { name: 'Save these changes?' })
    await user.click(within(dialog).getByRole('button', { name: 'Save changes' }))

    // The write has not answered: the confirmation is still there, and its button says it is working.
    expect(screen.getByRole('dialog', { name: 'Save these changes?' })).toBeInTheDocument()
    expect(within(dialog).getByRole('button', { name: /Save changes/ })).toHaveAttribute('aria-busy', 'true')

    answer()
    await waitFor(() => expect(screen.queryByRole('dialog', { name: 'Save these changes?' })).not.toBeInTheDocument())
    expect(await screen.findByText(/record saved/)).toBeInTheDocument()
  })

  it("⑧ COSATU positions that arrive as the server's string are saved back as a list, untouched", async () => {
    // 🔴 `MemberDto.cosatuPositions` is a String ("PEC,CEC"). Entering edit mode copied the record raw, so an edit
    // anywhere on the record wrote the string back, or threw on `.filter`. Positions are no longer on the Structures
    // tab (2026-09-17), which makes this the case that matters: data the screen does not show must survive a save of
    // something else. `memberService.normalizeMember` unfolds it once.
    let saved
    server.use(
      ...registerHandlers([{ ...ALPHA, isCosatu: 'Y', cosatuPositions: 'PEC,CEC' }, BRAVO]),
      http.put('*/api/members/:id', async ({ request }) => {
        saved = await request.json()
        return HttpResponse.json(saved)
      }),
    )
    const user = setupUser()
    renderWithProviders(<MembersPage />)
    await user.click(await screen.findByRole('button', { name: /open the record for.*Alpha/i }))
    await user.click(await screen.findByRole('button', { name: /Edit Member/i }))
    const initials = await panel().findByLabelText(/^Initials/)
    await user.clear(initials)
    await user.type(initials, 'Q')
    await user.click(screen.getByRole('button', { name: /^Save changes$/ }))
    const dialog = await screen.findByRole('dialog', { name: 'Save these changes?' })
    await user.click(within(dialog).getByRole('button', { name: 'Save changes' }))

    await waitFor(() => expect(saved).toBeDefined())
    expect(saved.cosatuPositions).toEqual(['PEC', 'CEC'])
  })
  it('⑨ A7 (fixed): a passport member with no date of birth is not saved, and is told why', async () => {
    // 🔴 The second site of A7: Edit Member required a date of birth when identityType === "Passport", and the only
    // option is "Passport Number", so the rule never ran and a passport member saved with no date on file.
    let saved
    server.use(
      ...registerHandlers([{ ...ALPHA, identityType: 'Passport Number', identityNumber: 'A12345678' }, BRAVO]),
      http.put('*/api/members/:id', async ({ request }) => {
        saved = await request.json()
        return HttpResponse.json(saved)
      }),
    )
    const user = await editAlpha({ change: true })

    await user.click(screen.getByRole('button', { name: /^Save changes$/ }))

    // Twice by design: under the field, and in the save summary that names every problem.
    expect((await screen.findAllByText(/Date of Birth is strictly required/i)).length).toBeGreaterThan(0)
    expect(screen.queryByRole('dialog', { name: 'Save these changes?' })).not.toBeInTheDocument()
    expect(saved).toBeUndefined()
  })
})
