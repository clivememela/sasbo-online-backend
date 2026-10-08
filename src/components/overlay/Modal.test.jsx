// @vitest-environment jsdom -- opens a real portalled dialog and traps focus in it
import { describe, expect, it, vi } from 'vitest'
import { screen, waitFor, within } from '@testing-library/react'
import { useState } from 'react'

import Modal, { ConfirmDialog } from './Modal'
import Button from '../form/Button'
import DateField from '../form/DateField'
import { renderWithProviders } from '../../test/renderWithProviders'
import { expectNoA11yViolations } from '../../test/assertions'
import { setupUser } from '../../test/user'

/**
 * 🔴 THESE ASSERT THE BEHAVIOURS THE EIGHT HAND-ROLLED OVERLAYS DO NOT HAVE.
 * Portal, focus trap, Escape, scroll lock — none of which is present on the surfaces this
 * primitive replaces, and every one of which is invisible until somebody uses a keyboard.
 * A test that only checked "the dialog renders" would pass against all eight of them.
 */

function Harness({ modalProps = {}, children = <p>Body</p> }) {
  const [open, setOpen] = useState(false)
  return (
    <div>
      <button type="button" onClick={() => setOpen(true)}>
        Open it
      </button>
      <input aria-label="Behind the dialog" />
      <Modal open={open} onOpenChange={setOpen} title="Member details" {...modalProps}>
        {children}
      </Modal>
    </div>
  )
}

/** A date inside a dialog, as Recruiters' Add Recruiter and Recruiter details both have one. */
function DateInModal() {
  const [open, setOpen] = useState(true)
  const [value, setValue] = useState('')
  return (
    <Modal open={open} onOpenChange={setOpen} title="Add Recruiter">
      <DateField label="Election Date" value={value} onChange={(event) => setValue(event.target.value)} />
    </Modal>
  )
}

/**
 * 🔴 R2-F1 (P9's independent review, FIX-NOW): A DATE COULD NOT BE CHOSEN INSIDE A DIALOG.
 *
 * `DateField` portalled its calendar to `document.body`, which put it OUTSIDE the Radix dialog it was opened from --
 * and a modal Radix dialog owns everything outside itself: it sets `pointer-events: none` on the body, traps focus
 * inside its own DOM, and takes Escape on the document in the capture phase. So the calendar opened, and then no day
 * could be pressed (the press fell through to whatever lay beneath, or dismissed the dialog with everything typed in
 * it), the keyboard route was pulled back out of the grid, and Escape closed the dialog instead of the calendar.
 * Reproduced in a real browser with real input before it was fixed: at 1440 the dialog closed and took the form with
 * it; at 390 the press landed on a table cell under the sheet. The record panel's date, in no dialog, worked.
 *
 * ⚠️ user-event refuses to press an element that has or inherits `pointer-events: none` -- which is exactly the
 * failure a person met, so the second test fails for the real reason, not a jsdom one.
 */
describe('Modal — a date field inside it works (R2-F1)', () => {
  const dayTen = (calendar) =>
    within(calendar).getAllByRole('gridcell').find((cell) => cell.textContent.trim() === '10' && cell.getAttribute('aria-disabled') !== 'true')

  it('opens its calendar INSIDE the dialog -- inside its focus trap, its pointer layer and its dismissal', async () => {
    const user = setupUser()
    renderWithProviders(<DateInModal />)
    const dialog = screen.getByRole('dialog', { name: 'Add Recruiter' })

    await user.click(within(dialog).getByRole('combobox', { name: /election date/i }))
    const calendar = screen.getByRole('dialog', { name: /choose election date/i })

    expect(dialog.contains(calendar), 'the calendar was portalled outside the dialog it was opened from').toBe(true)
  })

  it('a day pressed with a pointer is chosen, and the dialog stays open', async () => {
    const user = setupUser()
    renderWithProviders(<DateInModal />)
    const dialog = screen.getByRole('dialog', { name: 'Add Recruiter' })
    const field = within(dialog).getByRole('combobox', { name: /election date/i })

    await user.click(field)
    await user.click(dayTen(screen.getByRole('dialog', { name: /choose election date/i })))

    expect(field.value).not.toBe('')
    expect(screen.getByRole('dialog', { name: 'Add Recruiter' })).toBeInTheDocument()
  })

  it('Escape closes the calendar first, and leaves the dialog -- and what was typed in it -- open', async () => {
    const user = setupUser()
    renderWithProviders(<DateInModal />)
    const dialog = screen.getByRole('dialog', { name: 'Add Recruiter' })
    const field = within(dialog).getByRole('combobox', { name: /election date/i })

    await user.click(field)
    expect(field).toHaveAttribute('aria-expanded', 'true')

    await user.keyboard('{Escape}')
    expect(field).toHaveAttribute('aria-expanded', 'false')
    expect(screen.getByRole('dialog', { name: 'Add Recruiter' })).toBeInTheDocument()

    // ...and the must-pass beside it: with nothing open inside, Escape still closes the dialog.
    await user.keyboard('{Escape}')
    expect(screen.queryByRole('dialog', { name: 'Add Recruiter' })).toBeNull()
  })
})

describe('Modal — the four things the hand-rolled overlays lack', () => {
  it('is PORTALLED out of its parent, so no page overflow can clip it', async () => {
    const user = setupUser()
    const { container } = renderWithProviders(<Harness />)

    await user.click(screen.getByRole('button', { name: 'Open it' }))
    const dialog = screen.getByRole('dialog')

    // 🔑 The single thing all eight got wrong: rendered in place, a dialog is trapped in
    // whatever `overflow` and stacking context its page happens to have.
    expect(container.contains(dialog)).toBe(false)
    expect(document.body.contains(dialog)).toBe(true)
  })

  it('TRAPS FOCUS: Tab cycles inside it and never reaches the page behind', async () => {
    const user = setupUser()
    renderWithProviders(
      <Harness
        modalProps={{ footer: <Button variant="primary">Save</Button> }}
        children={<input aria-label="Inside the dialog" />}
      />,
    )

    await user.click(screen.getByRole('button', { name: 'Open it' }))
    const dialog = screen.getByRole('dialog')
    const behind = screen.getByLabelText('Behind the dialog')

    // Ten tabs is more than the dialog holds, so if focus can escape it, it will have.
    for (let i = 0; i < 10; i += 1) {
      await user.tab()
      expect(
        behind,
        'Tab walked out of the dialog and into the page behind it — which the user cannot ' +
          'see, and cannot get back from without a mouse. None of the eight overlays this ' +
          'replaces traps focus, and nothing in the suite noticed until now.',
      ).not.toHaveFocus()
      expect(dialog.contains(document.activeElement)).toBe(true)
    }
  })

  it('RESTORES FOCUS to the control that opened it, on close', async () => {
    const user = setupUser()
    renderWithProviders(<Harness children={<input aria-label="Inside the dialog" />} />)

    const trigger = screen.getByRole('button', { name: 'Open it' })
    await user.click(trigger)
    expect(screen.getByRole('dialog')).toBeInTheDocument()

    await user.keyboard('{Escape}')
    expect(screen.queryByRole('dialog')).toBeNull()

    // 🔴 TRAPPING FOCUS AND THEN DROPPING IT IS HALF A PATTERN, and the second half is
    // the half a keyboard user feels. When a dialog closes without restoring, focus falls
    // back to `<body>` and the next Tab starts again from the top of the document — which
    // in this application means the entire navigation tree, from wherever they were.
    // `05.3` names trap AND restore; the trap has been proved since P3, this had not.
    //
    // ⚠️ `waitFor`, because the close is synchronous but the restore is not: Radix returns
    // focus from a cleanup effect, one tick after the dialog leaves the DOM. Asserting
    // immediately reads `<body>` and reports a defect that is not there.
    await waitFor(() => {
      expect(
        trigger,
        'The dialog closed and focus went nowhere. Tab now restarts from the top of the ' +
          'document, so closing a dialog costs a keyboard user their place on the page.',
      ).toHaveFocus()
    })
  })

  it('ESCAPE closes it — seven of the eight it replaces ignore Escape', async () => {
    const user = setupUser()
    renderWithProviders(<Harness />)

    await user.click(screen.getByRole('button', { name: 'Open it' }))
    expect(screen.getByRole('dialog')).toBeInTheDocument()

    await user.keyboard('{Escape}')
    expect(screen.queryByRole('dialog')).toBeNull()
  })

  it('LOCKS THE PAGE behind it, and gives the scroll back on close', async () => {
    const user = setupUser()
    renderWithProviders(<Harness />)

    await user.click(screen.getByRole('button', { name: 'Open it' }))
    expect(document.body).toHaveStyle({ pointerEvents: 'none' })

    await user.keyboard('{Escape}')
    expect(document.body.style.pointerEvents).not.toBe('none')
  })
})

describe('Modal — the accessible name is required, not optional', () => {
  it('throws in development rather than rendering an unnamed dialog', () => {
    // 🔴 Radix WARNS about a missing Dialog.Title and renders anyway. A warning in a
    // console nobody is reading is not a guard: the result ships as a dialog announced as
    // "dialog" and nothing else. MUTATION-CHECKED by deleting the throw in `Modal.jsx`.
    const consoleError = vi.spyOn(console, 'error').mockImplementation(() => {})
    try {
      expect(() =>
        renderWithProviders(
          <Modal open onOpenChange={() => {}}>
            <p>No title</p>
          </Modal>,
        ),
      ).toThrow(/Modal requires a `title`/)
    } finally {
      consoleError.mockRestore()
    }
  })

  it('titleHidden keeps the NAME and hides only the heading', async () => {
    const user = setupUser()
    renderWithProviders(<Harness modalProps={{ titleHidden: true }} />)
    await user.click(screen.getByRole('button', { name: 'Open it' }))

    // Still named — `sr-only` is a different thing from absent.
    expect(screen.getByRole('dialog', { name: 'Member details' })).toBeInTheDocument()
    expect(screen.getByText('Member details').className).toMatch(/\bsr-only\b/)
  })
})

describe('Modal — the height cap is the primitive\'s, not the call site\'s', () => {
  it('caps in dvh and scrolls the BODY, so the footer stays reachable', async () => {
    const user = setupUser()
    renderWithProviders(
      <Harness modalProps={{ footer: <Button variant="primary">Save</Button> }} />,
    )
    await user.click(screen.getByRole('button', { name: 'Open it' }))

    const dialog = screen.getByRole('dialog')
    // 📱 conventions §6. `100vh` on a phone is the viewport with the URL bar RETRACTED, so
    // a vh-capped dialog puts its Save button under the browser chrome. Baked in here
    // because MobiZone found two call-sites that had "forgotten" it (contract ⑮).
    expect(dialog.className).toMatch(/max-h-\[85dvh\]/)
    expect(dialog.className).not.toMatch(/\bh-\[?\d+vh/)

    // The body scrolls, not the whole dialog — otherwise the actions scroll away.
    const scroller = dialog.querySelector('.overflow-y-auto')
    expect(scroller).not.toBeNull()
    expect(within(dialog).getByRole('button', { name: 'Save' })).toBeInTheDocument()
  })
})

describe('Modal — a step you must answer can refuse to be dismissed', () => {
  it('dismissible={false} ignores Escape and has no close button', async () => {
    const user = setupUser()
    renderWithProviders(<Harness modalProps={{ dismissible: false }} />)

    await user.click(screen.getByRole('button', { name: 'Open it' }))
    expect(screen.queryByRole('button', { name: 'Close' })).toBeNull()

    await user.keyboard('{Escape}')
    expect(screen.getByRole('dialog')).toBeInTheDocument()
  })
})

describe('ConfirmDialog — what replaces window.confirm()', () => {
  it('names the action on the button, never "OK"', async () => {
    const user = setupUser()
    const onConfirm = vi.fn()

    renderWithProviders(
      <ConfirmDialog
        open
        onOpenChange={() => {}}
        title="Remove recruiter"
        description="This sets 15 members to Inactive and clears their recruiter assignment."
        confirmLabel="Remove recruiter"
        onConfirm={onConfirm}
      />,
    )

    // 🔑 `window.confirm` can only say OK/Cancel. "Remove recruiter" is a sentence a user
    // can check against what they meant; "OK" is not — and the consequence here is fifteen
    // members going inactive.
    expect(screen.queryByRole('button', { name: 'OK' })).toBeNull()
    await user.click(screen.getByRole('button', { name: 'Remove recruiter' }))
    expect(onConfirm).toHaveBeenCalled()
  })

  it('states the consequence, and Cancel does not confirm', async () => {
    const user = setupUser()
    const onConfirm = vi.fn()
    const onOpenChange = vi.fn()

    renderWithProviders(
      <ConfirmDialog
        open
        onOpenChange={onOpenChange}
        title="Remove recruiter"
        description="This sets 15 members to Inactive."
        confirmLabel="Remove recruiter"
        onConfirm={onConfirm}
      />,
    )

    expect(screen.getByRole('dialog')).toHaveTextContent('This sets 15 members to Inactive.')
    await user.click(screen.getByRole('button', { name: 'Cancel' }))
    expect(onConfirm).not.toHaveBeenCalled()
    expect(onOpenChange).toHaveBeenCalledWith(false)
  })

  it('the destructive action LOOKS destructive', async () => {
    renderWithProviders(
      <ConfirmDialog open onOpenChange={() => {}} title="Delete" confirmLabel="Delete" onConfirm={() => {}} />,
    )
    // The `danger` variant is new in 03.2a: before it, every delete in this application was
    // a neutral button beside a `confirm()`.
    expect(screen.getByRole('button', { name: 'Delete' }).className).toMatch(/status-suspended/)
  })
})

describe('Modal — accessibility, both themes', () => {
  it('has no violations open, in light or dark', async () => {
    const user = setupUser()
    renderWithProviders(
      <Harness modalProps={{ description: 'Everything we hold about this member.', footer: <Button variant="primary">Save</Button> }} />,
    )

    await user.click(screen.getByRole('button', { name: 'Open it' }))
    const dialog = screen.getByRole('dialog')

    // Scoped to the dialog for the reason DateField's is: scanning `document.body` makes a
    // component test's cost depend on whatever else the document holds, and that turned
    // into a 20-second timeout inside the shuffled suite. `region` is off, and only here,
    // because a primitive rendered alone is not a page.
    const rules = { region: { enabled: false } }
    await expectNoA11yViolations(dialog, { rules })

    document.documentElement.classList.add('dark')
    try {
      await expectNoA11yViolations(dialog, { rules })
    } finally {
      document.documentElement.classList.remove('dark')
    }
  })
})
