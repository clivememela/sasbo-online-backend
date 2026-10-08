// @vitest-environment jsdom -- Capture Member's Save while it runs, what it says after, and Cancel over a half-captured member
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { screen, waitFor, within } from '@testing-library/react'
import { http, HttpResponse } from 'msw'

import CaptureMember from './CaptureMember'
import { renderWithProviders } from '../../test/renderWithProviders'
import { server } from '../../test/msw/server'
import { pageFixtures } from '../../test/msw/pageFixtures'
import { setupUser } from '../../test/user'
import { deferred } from '../../test/deferred'

/**
 * A CAPTURE IS SAVED ONCE, SAYS WHO WAS SAVED, AND IS NOT THROWN AWAY BY ONE CLICK. `06.5`, F-P55.
 *
 * 🔴 Save Member stayed pressable while the request ran, so a second press sent the member again -- the shape the
 * `Button` primitive's own comment names as how duplicate records happen. The success message sat at the top of a
 * long form. Cancel was `history.back()`, whatever the form held.
 *
 * The pairs:
 *   ① Save is busy while it runs, a second press sends nothing, and the message names the member
 *     -- and once the save has answered, Save works again
 *   ② Cancel with input asks, and "Keep capturing" keeps it       ③ Cancel on an untouched form simply leaves
 */

const escapeForRegex = (s) => s.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
const labelled = (label) => screen.getByLabelText(new RegExp(`^${escapeForRegex(label)}`))
const fill = async (user, label, value) => {
  const field = labelled(label)
  await user.clear(field)
  await user.type(field, value)
}

/** The smallest capture the form accepts: Payroll, so no bank account is required (see the round-trip test). */
async function fillValidMember(user) {
  await fill(user, 'First Name(s)', 'Synthetic')
  await fill(user, 'Surname', 'Testperson')
  await fill(user, 'Personal Email', 'synthetic@example.test')
  await user.click(screen.getByRole('button', { name: /Employment & Bank/i }))
  await user.selectOptions(labelled('Payment Type'), 'Payroll Deduction')
  await user.click(screen.getByRole('button', { name: /Personal Information/i }))
  await user.selectOptions(labelled('Identity Type'), 'I.D. Number')
  await fill(user, 'I.D. Number', '9001010000080')
}

describe('Capture Member, saving and leaving', () => {
  let back

  beforeEach(() => {
    back = vi.spyOn(window.history, 'back').mockImplementation(() => {})
  })

  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('① Save is busy while the member is saved, a second press sends nothing, and the message names who was captured', async () => {
    let posts = 0
    const { promise: answered, resolve: answer } = deferred()
    server.use(
      ...pageFixtures,
      http.post('*/api/members', async ({ request }) => {
        posts += 1
        const body = await request.json()
        await answered
        return HttpResponse.json({ id: 1, ...body })
      }),
    )
    const user = setupUser()
    renderWithProviders(<CaptureMember />)
    await fillValidMember(user)

    const save = screen.getByRole('button', { name: /Save Member/i })
    await user.click(save)

    const send = await screen.findByRole('button', { name: /Send Welcome Letter/i })
    await user.click(send)
    await waitFor(() => expect(send).toBeDisabled())

    answer()
    // The sentence stays word for word -- the automation arc's MemberCapturePage waits for exactly it -- and the
    // toast names the member beside it.
    const sentence = await screen.findByText('Member captured successfully!')
    expect(within(sentence.closest('output')).getByText('Synthetic Testperson')).toBeInTheDocument()
    expect(posts, 'the member was sent more than once').toBe(1)
  })

  it('② Cancel with something typed asks first -- and "Keep capturing" keeps it', async () => {
    server.use(...pageFixtures)
    const user = setupUser()
    renderWithProviders(<CaptureMember />)
    await fill(user, 'First Name(s)', 'Synthetic')

    await user.click(screen.getByRole('button', { name: 'Cancel' }))
    const dialog = await screen.findByRole('dialog', { name: 'Discard?' })
    await user.click(within(dialog).getByRole('button', { name: 'Keep capturing' }))

    await waitFor(() => expect(screen.queryByRole('dialog', { name: 'Discard?' })).not.toBeInTheDocument())
    expect(labelled('First Name(s)')).toHaveValue('Synthetic')
    expect(back).not.toHaveBeenCalled()
  })

  it('③ the must-pass: Cancel on an untouched form leaves at once, without asking', async () => {
    server.use(...pageFixtures)
    const user = setupUser()
    renderWithProviders(<CaptureMember />)

    await user.click(screen.getByRole('button', { name: 'Cancel' }))

    expect(back).toHaveBeenCalledTimes(1)
    expect(screen.queryByRole('dialog', { name: 'Discard this member?' })).not.toBeInTheDocument()
  })
  // ── what a FAILED save says, and what a successful one leaves behind (design arc P9: R3-F2, R3-OBS-2b) ──


  it('④ 🔴 a save that never answered does NOT say "not saved" -- it says to check the register first', async () => {
    // A timeout or a dropped connection does not mean the server did nothing.
    server.use(...pageFixtures, http.post('*/api/members', () => HttpResponse.error()))
    const user = setupUser()
    renderWithProviders(<CaptureMember />)
    await fillValidMember(user)
    await user.click(screen.getByRole('button', { name: /Save Member/i }))
    const send = await screen.findByRole('button', { name: /Send Welcome Letter/i })
    await user.click(send)

    const alert = await screen.findByRole('alert')
    expect(alert).toHaveTextContent(/could not reach the server|could not be sent/i)
  })

  it('⑤ the must-pass: a save the server REFUSED still says plainly that it was not saved', async () => {
    server.use(
      ...pageFixtures,
      http.post('*/api/members', () => HttpResponse.json({ message: 'Identity number already on record' }, { status: 409 })),
    )
    const user = setupUser()
    renderWithProviders(<CaptureMember />)
    await fillValidMember(user)
    await user.click(screen.getByRole('button', { name: /Save Member/i }))
    const send = await screen.findByRole('button', { name: /Send Welcome Letter/i })
    await user.click(send)

    const alert = await screen.findByRole('alert')
    expect(alert).toHaveTextContent(/already on record/i)
  })

  it("⑥ the server's validation errors show when the capture request fails", async () => {
    server.use(
      ...pageFixtures,
      http.post('*/api/members', () =>
        HttpResponse.json(
          { message: 'Validation failed: identityNumber must be unique', validationErrors: { identityNumber: 'must be unique' } },
          { status: 400 },
        )),
    )
    const user = setupUser()
    renderWithProviders(<CaptureMember />)
    await fillValidMember(user)
    await user.click(screen.getByRole('button', { name: /Save Member/i }))
    const send = await screen.findByRole('button', { name: /Send Welcome Letter/i })
    await user.click(send)

    const alert = await screen.findByRole('alert')
    expect(alert).toHaveTextContent(/Validation failed/i)
  })

  it('⑦ 🔴 after a successful capture the form is empty again, so a second press cannot post the member twice', async () => {
    let posts = 0
    server.use(
      ...pageFixtures,
      http.post('*/api/members', async ({ request }) => {
        posts += 1
        return HttpResponse.json({ id: posts, ...(await request.json()) })
      }),
    )
    const user = setupUser()
    renderWithProviders(<CaptureMember />)
    await fillValidMember(user)
    await user.click(screen.getByRole('button', { name: /Save Member/i }))
    const send = await screen.findByRole('button', { name: /Send Welcome Letter/i })
    await user.click(send)
    await screen.findByText('Member captured successfully!')

    // The welcome letter offer opens over the form; close it the way a clerk would.
    const letter = screen.queryByRole('dialog')
    if (letter) await user.keyboard('{Escape}')

    await waitFor(() => expect(labelled('First Name(s)')).toHaveValue(''))
    await user.click(screen.getByRole('button', { name: /Save Member/i }))
    expect(posts, 'the same member was posted again').toBe(1)
  })
})
