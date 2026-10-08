// @vitest-environment jsdom -- a real portalled Radix dialog, dismissed by key, pointer and its own buttons
import { describe, expect, it } from 'vitest'
import { fireEvent, screen, waitFor, within } from '@testing-library/react'
import { useState } from 'react'

import Modal, { ConfirmDialog, ModalClose } from './Modal'
import { renderWithProviders } from '../../test/renderWithProviders'
import { setupUser } from '../../test/user'

/**
 * 🔴 R2-F3 (P9's independent review): A DIALOG COULD BE CLOSED WHILE ITS WRITE WAS RUNNING.
 *
 * `ConfirmDialog` handed `loading` to its confirm button and nothing else, and `Modal` had no idea a write was in
 * flight -- so Cancel, Escape, a press on the scrim and the X all stayed live. The delete or the banking change kept
 * running with no visible owner, its failure landed on a closed surface (or reopened one), and a second attempt could
 * overlap the first.
 *
 * Every way out is tried while busy, and beside each set the must-pass: the same dialog, not busy, still closes. A
 * dialog that could never be closed would satisfy the first half alone.
 */

function Busy({ busy, confirm = false }) {
  const [open, setOpen] = useState(true)
  return (
    <>
      <p>{open ? 'still open' : 'closed'}</p>
      {confirm ? (
        <ConfirmDialog open={open} onOpenChange={setOpen} title="Delete recruiter type" loading={busy} onConfirm={() => {}} />
      ) : (
        <Modal open={open} onOpenChange={setOpen} title="Update Banking Details" busy={busy}>
          <p>Body</p>
        </Modal>
      )}
    </>
  )
}

/** A press outside the content, the way Radix hears one: a pointerdown on the document beyond the dialog. */
const pressOutside = () => fireEvent.pointerDown(document.body)

describe('Modal, while its write is running (R2-F3)', () => {
  it('Escape, a press outside and the X do not close it', async () => {
    const user = setupUser()
    renderWithProviders(<Busy busy />)
    const dialog = screen.getByRole('dialog', { name: 'Update Banking Details' })

    await user.keyboard('{Escape}')
    pressOutside()
    expect(within(dialog).getByRole('button', { name: 'Close' })).toBeDisabled()

    expect(screen.getByRole('dialog', { name: 'Update Banking Details' })).toBeInTheDocument()
    expect(screen.getByText('still open')).toBeInTheDocument()
  })

  it('the must-pass: not busy, Escape closes it', async () => {
    const user = setupUser()
    renderWithProviders(<Busy busy={false} />)

    await user.keyboard('{Escape}')
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
    expect(screen.getByText('closed')).toBeInTheDocument()
  })

  it('the must-pass: not busy, the X closes it', async () => {
    const user = setupUser()
    renderWithProviders(<Busy busy={false} />)

    await user.click(screen.getByRole('button', { name: 'Close' }))
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
  })
})

describe('ConfirmDialog, while its confirm is running (R2-F3)', () => {
  it('Cancel is disabled, and Escape and a press outside do not close it', async () => {
    const user = setupUser()
    renderWithProviders(<Busy busy confirm />)
    const dialog = screen.getByRole('dialog', { name: 'Delete recruiter type' })

    expect(within(dialog).getByRole('button', { name: 'Cancel' })).toBeDisabled()
    await user.keyboard('{Escape}')
    pressOutside()

    expect(screen.getByRole('dialog', { name: 'Delete recruiter type' })).toBeInTheDocument()
    expect(screen.getByText('still open')).toBeInTheDocument()
  })

  it('the must-pass: not running, Cancel closes it', async () => {
    const user = setupUser()
    renderWithProviders(<Busy busy={false} confirm />)

    await user.click(screen.getByRole('button', { name: 'Cancel' }))
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
  })
})

/**
 * `ModalClose` (RF): a footer's own Cancel/Close, which reads `busy` from the dialog it sits in. Before it, each screen
 * disabled that button by hand, and the welcome letter shipped without doing so. It closes through Radix, so the close
 * is the X's path, through `Modal`'s own guard.
 */
function Footer({ busy }) {
  const [open, setOpen] = useState(true)
  return (
    <>
      <p>{open ? 'still open' : 'closed'}</p>
      <Modal open={open} onOpenChange={setOpen} title="Welcome Letter Preview" busy={busy} footer={<ModalClose variant="ghost">Keep it</ModalClose>}>
        <p>Body</p>
      </Modal>
    </>
  )
}

describe('ModalClose -- a footer close that cannot be forgotten (RF)', () => {
  it('while the write runs it is disabled, and the dialog stays', () => {
    renderWithProviders(<Footer busy />)
    const dialog = screen.getByRole('dialog', { name: 'Welcome Letter Preview' })
    const close = within(dialog).getByRole('button', { name: 'Keep it' })

    expect(close).toBeDisabled()
    fireEvent.click(close)
    expect(screen.getByText('still open')).toBeInTheDocument()
  })

  it('the must-pass: not busy, it closes the dialog through onOpenChange', async () => {
    const user = setupUser()
    renderWithProviders(<Footer busy={false} />)

    await user.click(screen.getByRole('button', { name: 'Keep it' }))
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
    expect(screen.getByText('closed')).toBeInTheDocument()
  })
})
