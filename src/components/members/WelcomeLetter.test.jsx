// @vitest-environment jsdom -- mounts the welcome-letter dialog and answers its send request
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { useState } from 'react'
import { screen, within } from '@testing-library/react'
import { http, HttpResponse } from 'msw'

import WelcomeLetter from './WelcomeLetter'
import { renderWithProviders } from '../../test/renderWithProviders'
import { server } from '../../test/msw/server'
import { setupUser } from '../../test/user'
import { deferred } from '../../test/deferred'

/**
 * THE WELCOME LETTER, AFTER A MEMBER IS CAPTURED. Three register rows, each written red first:
 *
 *   A50  a letter that WAS sent is reported as failed. The server answers `POST .../send-welcome` with an empty
 *        200 (`ResponseEntity.ok().build()`), and a read that requires JSON rejects it -- so the dialog said "could
 *        not be sent", and a second press emailed the member twice.
 *   A51  only the first member captured in a visit can be sent a letter. The dialog stays mounted and its send state
 *        was never reset, so the next member's dialog opened already "Sent", with no Send button.
 *   A52  the letter's banner read "Your Sasbo Membership No: —" for every new member. Nothing assigns a membership
 *        number yet (A54, the server's), so no test here expects one: the dialog must SAY the letter goes without it.
 *
 * Each red case has its must-pass beside it: a real failure is still reported; the same member is not reset by a
 * re-render; a member who has a number shows it.
 */

const sent = (status = 200) => http.post('*/api/members/:id/send-welcome', () => new HttpResponse(null, { status }))
/** A JSON answer: what the dialog's old read could accept -- so A51's cases reach "sent" on either side of A50's fix. */
const sentWithBody = () => http.post('*/api/members/:id/send-welcome', () => HttpResponse.json({}))
/** Whether this server emails the letter at all (A64) -- asked once, when the dialog mounts. */
const emailing = (answer) => http.get('*/api/members/metadata/welcome-letter', () => HttpResponse.json(answer))

const person = (id, extra = {}) => ({
  id, title: 'Ms', firstNames: 'Thandi', surname: `Synthetic${id}`, personalEmail: `member${id}@example.test`,
  preferredEmail: 'personal', identityNumber: '9001010000000', bankName: 'Synthetic Bank', status: 'Active', ...extra,
})

/** Captures two members in one visit, as CaptureMember does: the dialog stays mounted, its member changes. */
function TwoCaptures() {
  const [member, setMember] = useState(person(101))
  const [open, setOpen] = useState(true)
  return (
    <>
      <button type="button" onClick={() => { setMember(person(102)); setOpen(true) }}>capture the next member</button>
      <button type="button" onClick={() => setOpen(true)}>reopen</button>
      <WelcomeLetter open={open} onClose={() => setOpen(false)} member={member} />
    </>
  )
}

describe('the welcome letter dialog', () => {
  beforeEach(() => {
    vi.spyOn(console, 'error').mockImplementation(() => {})
    // Where mail is configured -- the cases below were written for it. A64's own cases say otherwise where they need to.
    server.use(emailing({ emailEnabled: true }))
  })

  describe('A50 — a sent letter is reported as sent', () => {
    it('an empty 200 from the server reads as sent, not as a failure', async () => {
      server.use(sent(200))
      const user = setupUser()
      renderWithProviders(<WelcomeLetter open onClose={() => {}} member={person(101)} />)

      await user.click(screen.getByRole('button', { name: /send welcome letter/i }))

      expect(await screen.findByText(/welcome letter sent/i)).toBeInTheDocument()
      expect(screen.queryByText(/could not be sent|not valid json|unexpected/i)).not.toBeInTheDocument()
      expect(screen.queryByRole('button', { name: /send welcome letter/i })).not.toBeInTheDocument()
    })

    it('must-pass: a server failure is still reported, and the letter can be sent again', async () => {
      server.use(sent(500))
      const user = setupUser()
      renderWithProviders(<WelcomeLetter open onClose={() => {}} member={person(101)} />)

      await user.click(screen.getByRole('button', { name: /send welcome letter/i }))

      expect(await screen.findByRole('button', { name: /send welcome letter/i })).toBeEnabled()
      expect(screen.queryByText(/welcome letter sent/i)).not.toBeInTheDocument()
      // The server's own message reaches the person (the shared error contract, 06.1a).
      expect(screen.getByText(/the server could not complete that request/i)).toBeInTheDocument()
    })
  })

  describe('A51 — every member captured in a visit can be sent a letter', () => {
    it('the next member opens ready to send, not already "sent"', async () => {
      server.use(sentWithBody())
      const user = setupUser()
      renderWithProviders(<TwoCaptures />)

      await user.click(screen.getByRole('button', { name: /send welcome letter/i }))
      await screen.findByText(/welcome letter sent/i)
      await user.keyboard('{Escape}')
      await user.click(screen.getByRole('button', { name: /capture the next member/i }))

      const dialog = await screen.findByRole('dialog')
      expect(within(dialog).getAllByText(/Synthetic102/).length).toBeGreaterThan(0)
      expect(within(dialog).getByRole('button', { name: /send welcome letter/i })).toBeEnabled()
      expect(within(dialog).queryByText(/welcome letter sent/i)).not.toBeInTheDocument()
    })

    it('must-pass: reopening the SAME member keeps what was already sent', async () => {
      server.use(sentWithBody())
      const user = setupUser()
      renderWithProviders(<TwoCaptures />)

      await user.click(screen.getByRole('button', { name: /send welcome letter/i }))
      await screen.findByText(/welcome letter sent/i)
      await user.keyboard('{Escape}')
      await user.click(screen.getByRole('button', { name: /reopen/i }))

      const dialog = await screen.findByRole('dialog')
      expect(within(dialog).getByText(/welcome letter sent/i)).toBeInTheDocument()
    })
  })

  describe('the outcome of a send is announced, not only drawn', () => {
    it('a sent letter is a status message, a failed one an alert', async () => {
      server.use(sent(200))
      const user = setupUser()
      const { unmount } = renderWithProviders(<WelcomeLetter open onClose={() => {}} member={person(101)} />)
      await user.click(screen.getByRole('button', { name: /send welcome letter/i }))
      expect(await screen.findByRole('status')).toHaveTextContent(/welcome letter sent/i)
      unmount()

      server.use(sent(500))
      renderWithProviders(<WelcomeLetter open onClose={() => {}} member={person(101)} />)
      await user.click(screen.getByRole('button', { name: /send welcome letter/i }))
      expect(await screen.findByRole('alert')).toBeInTheDocument()
    })
  })

  describe('A56 — a letter with nowhere to go is never promised', () => {
    // A56's defect was the PROMISE: "emailed as an attachment to —" beside an enabled Send. Its first fix disabled Send
    // -- which, since Send is also the save, left a member with no address uncapturable (A64). The promise stays gone;
    // the button stays usable, and saving them is what it does.
    it('a member with no email address: the dialog says no letter will go, and Send still saves them', () => {
      renderWithProviders(
        <WelcomeLetter open onClose={() => {}} member={person(101, { personalEmail: '', workEmail: '' })} />,
      )

      const dialog = screen.getByRole('dialog')
      expect(within(dialog).getByRole('button', { name: /send welcome letter/i })).toBeEnabled()
      expect(within(dialog).getByText(/no email address, so no letter will be sent/i)).toBeInTheDocument()
      expect(dialog).not.toHaveTextContent(/attachment to\s*—/)
    })

    it('must-pass: a member with a work address only, preferring work, can be sent the letter', () => {
      renderWithProviders(
        <WelcomeLetter open onClose={() => {}} member={person(101, { personalEmail: '', workEmail: 'work101@example.test', preferredEmail: 'work' })} />,
      )

      const dialog = screen.getByRole('dialog')
      expect(within(dialog).getByRole('button', { name: /send welcome letter/i })).toBeEnabled()
      expect(within(dialog).getByText('work101@example.test')).toBeInTheDocument()
    })
  })

  describe('🔴 A64 — capturing a member never waits on the letter', () => {
    /*
     * The dialog as Capture Member uses it: a member with NO id yet, so Send is the capture itself --
     * `POST /api/members?sendWelcome=true`. The server saves first and says what happened to the letter
     * (`welcomeLetter`); each outcome is driven here, with the case that must not produce it beside it.
     */
    const newcomer = (extra = {}) => ({
      title: 'Ms', firstNames: 'Thandi', surname: 'Newcomer', personalEmail: 'newcomer@example.test',
      preferredEmail: 'personal', identityNumber: '9001010000080', status: 'Active', ...extra,
    })

    /** The capture, answered as the fixed server answers it: the saved member, numbered, with the letter's outcome. */
    function capturing(welcomeLetter) {
      const calls = { captures: [], sends: [] }
      server.use(
        http.post('*/api/members', async ({ request }) => {
          calls.captures.push(new URL(request.url).search)
          const body = await request.json()
          const answer = { id: 7, membershipNumber: 'M-100007', ...body }
          if (welcomeLetter !== undefined) answer.welcomeLetter = welcomeLetter
          return HttpResponse.json(answer)
        }),
        http.post('*/api/members/:id/send-welcome', ({ params }) => {
          calls.sends.push(params.id)
          return new HttpResponse(null, { status: 200 })
        }),
      )
      return calls
    }

    it('with emailing off, the dialog says so BEFORE Send, and Send saves the member: Member Saved', async () => {
      server.use(emailing({ emailEnabled: false }))
      const calls = capturing('EMAIL_OFF')
      const onSuccess = vi.fn()
      const user = setupUser()
      renderWithProviders(<WelcomeLetter open onClose={() => {}} member={newcomer()} onSuccess={onSuccess} />)

      expect(await screen.findByText(/emailing is not switched on yet, so this letter will not be sent/i)).toBeInTheDocument()
      await user.click(screen.getByRole('button', { name: /send welcome letter/i }))

      expect(await screen.findByRole('status')).toHaveTextContent('Member Saved')
      expect(screen.getByText(/the member is saved\. emailing is not switched on yet/i)).toBeInTheDocument()
      expect(screen.queryByText(/welcome letter sent/i)).not.toBeInTheDocument()
      expect(screen.queryByRole('button', { name: /send welcome letter/i }), 'nothing to send where email is off').not.toBeInTheDocument()
      expect(calls.captures).toEqual(['?sendWelcome=true'])
      expect(onSuccess).toHaveBeenCalledWith(expect.objectContaining({ id: 7 }))
    })

    it('a member with no email address is saved, and the dialog says no letter went: NO_ADDRESS', async () => {
      const calls = capturing('NO_ADDRESS')
      const user = setupUser()
      renderWithProviders(<WelcomeLetter open onClose={() => {}} member={newcomer({ personalEmail: '', workEmail: '' })} />)

      await user.click(screen.getByRole('button', { name: /send welcome letter/i }))

      expect(await screen.findByRole('status')).toHaveTextContent('Member Saved')
      expect(screen.getByText(/they have no email address, so no letter was sent/i)).toBeInTheDocument()
      expect(calls.captures).toHaveLength(1)
    })

    it('a letter that failed leaves the member saved, and "try again" sends for THAT member without capturing twice', async () => {
      const calls = capturing('FAILED')
      const user = setupUser()
      renderWithProviders(<WelcomeLetter open onClose={() => {}} member={newcomer()} />)

      await user.click(screen.getByRole('button', { name: /send welcome letter/i }))
      expect(await screen.findByRole('status')).toHaveTextContent('Member Saved')
      expect(screen.getByText(/could not be emailed\. try sending it again/i)).toBeInTheDocument()

      await user.click(screen.getByRole('button', { name: /send welcome letter/i }))

      expect(await screen.findByText('Welcome Letter Sent')).toBeInTheDocument()
      expect(calls.sends, 'the retry must send for the member the capture saved').toEqual(['7'])
      expect(calls.captures, 'the retry captured the member a second time').toHaveLength(1)
    })

    it('must-pass: where the letter WAS emailed, it is Welcome Letter Sent -- and the saved number is on the letter', async () => {
      capturing('SENT')
      const user = setupUser()
      renderWithProviders(<WelcomeLetter open onClose={() => {}} member={newcomer()} />)

      expect(await screen.findByText(/will be emailed as an attachment to/i)).toBeInTheDocument()
      await user.click(screen.getByRole('button', { name: /send welcome letter/i }))

      expect(await screen.findByRole('status')).toHaveTextContent('Welcome Letter Sent')
      expect(screen.getByText(/the letter was emailed as an attachment to/i)).toBeInTheDocument()
      expect(screen.getByText('M-100007'), 'the number the server gave the member').toBeInTheDocument()
      expect(screen.queryByText(/no membership number/i)).not.toBeInTheDocument()
    })

    it('a server from before the fix answers with no outcome, and for it a 200 meant the letter was sent', async () => {
      capturing(undefined)
      const user = setupUser()
      renderWithProviders(<WelcomeLetter open onClose={() => {}} member={newcomer()} />)

      await user.click(screen.getByRole('button', { name: /send welcome letter/i }))

      expect(await screen.findByRole('status')).toHaveTextContent('Welcome Letter Sent')
    })

    it('until the server says whether it emails -- or when it cannot -- the dialog promises nothing either way', async () => {
      // `emailingFrom` holds the mapping (a non-boolean answer is unknown); this holds what unknown SAYS.
      server.use(emailing([]))
      renderWithProviders(<WelcomeLetter open onClose={() => {}} member={newcomer()} />)

      expect(await screen.findByText(/where emailing is switched on/i)).toBeInTheDocument()
      expect(screen.queryByText(/will be emailed as an attachment/i)).not.toBeInTheDocument()
      expect(screen.queryByText(/will not be sent/i)).not.toBeInTheDocument()
    })

    it('must-pass: a capture the server REFUSED is not "saved" -- it is an alert, and Send is offered again', async () => {
      server.use(http.post('*/api/members', () => HttpResponse.json({ message: 'Identity number already on record' }, { status: 409 })))
      const user = setupUser()
      renderWithProviders(<WelcomeLetter open onClose={() => {}} member={newcomer()} />)

      await user.click(screen.getByRole('button', { name: /send welcome letter/i }))

      expect(await screen.findByRole('alert')).toHaveTextContent(/already on record/i)
      expect(screen.queryByText('Member Saved')).not.toBeInTheDocument()
      expect(screen.getByRole('button', { name: /send welcome letter/i })).toBeEnabled()
    })
  })

  describe('A52 — the letter says what it will carry, not a dash', () => {
    it('a member with no membership number: the dialog says the letter goes without one', () => {
      renderWithProviders(<WelcomeLetter open onClose={() => {}} member={person(101)} />)

      const dialog = screen.getByRole('dialog')
      expect(dialog).not.toHaveTextContent(/Membership No:\s*—/)
      expect(within(dialog).getAllByText(/no membership number/i).length).toBeGreaterThan(0)
    })

    it('must-pass: a member who has a number is shown it, and no warning', () => {
      renderWithProviders(<WelcomeLetter open onClose={() => {}} member={person(101, { membershipNumber: '100042' })} />)

      const dialog = screen.getByRole('dialog')
      expect(within(dialog).getByText('100042')).toBeInTheDocument()
      expect(within(dialog).queryByText(/no membership number/i)).not.toBeInTheDocument()
    })
  })

  describe('R2-F3, the one write-bearing dialog it missed — no way out while the letter is sending', () => {
    /*
     * 🔴 FOUND BY P10's COMPONENT INVENTORY, NOT BY P9's REVIEW. R2-F3 gave `Modal` a `busy` prop so a dialog
     * cannot be closed while its write is in flight -- otherwise the result lands on a surface the user has
     * already left, and they cannot tell whether it happened. `ConfirmDialog`, `AcbDebits` and `BankBranchModal`
     * all pass it. This dialog holds `sendState === 'sending'` and did not, so Escape, the scrim and the X all
     * closed it mid-send.
     *
     * ⚠️ AND `busy` ALONE WOULD NOT HAVE BEEN THE FIX, which is why the second assertion is here. The footer has
     * its own Close button calling `onClose` directly; `Modal` cannot intercept that, so it has to be disabled
     * by the caller while sending -- exactly what `ConfirmDialog` does with its Cancel.
     *
     * The send is HELD until the test releases it, so "while sending" is a state the test is in, not a race.
     */
    function held() {
      const answer = deferred()
      return {
        handler: http.post('*/api/members/:id/send-welcome', async () => {
          await answer.promise
          return new HttpResponse(null, { status: 200 })
        }),
        release: () => answer.resolve(),
      }
    }

    it('while sending, Escape does not close it and the footer Close is disabled', async () => {
      const send = held()
      server.use(send.handler)
      const onClose = vi.fn()
      const user = setupUser()
      renderWithProviders(<WelcomeLetter open onClose={onClose} member={person(101)} />)

      try {
        await user.click(screen.getByRole('button', { name: /send welcome letter/i }))
        await screen.findByRole('button', { name: /sending/i })

        await user.keyboard('{Escape}')
        expect(onClose, 'Escape closed the dialog while the letter was still sending').not.toHaveBeenCalled()

        const dialog = screen.getByRole('dialog')
        // BOTH ways out named Close -- the header's X (held by `busy`) and the footer's own button (which `busy`
        // cannot reach, so the dialog disables it) -- and there must be two, or one of them went missing.
        const closes = within(dialog).getAllByRole('button', { name: /^Close$/ })
        expect(closes).toHaveLength(2)
        closes.forEach((button) => expect(button).toBeDisabled())
      } finally {
        send.release()
      }
    })

    it('the must-pass: once the send has answered, Escape closes it again', async () => {
      // 🔑 Without this, a dialog that could NEVER be closed would pass the test above.
      server.use(sent())
      const onClose = vi.fn()
      const user = setupUser()
      renderWithProviders(<WelcomeLetter open onClose={onClose} member={person(101)} />)

      await user.click(screen.getByRole('button', { name: /send welcome letter/i }))
      await screen.findByText('Welcome Letter Sent')
      await user.keyboard('{Escape}')

      expect(onClose).toHaveBeenCalled()
    })
  })
})
