// @vitest-environment jsdom -- which control a dialog hands the caret to when it opens
import { useRef } from 'react'
import { describe, expect, it } from 'vitest'
import { render, screen } from '@testing-library/react'

import Modal from './Modal'

/**
 * 🔴 `initialFocusRef` — WHERE A DIALOG PUTS THE CARET, DECIDED IN THE PRIMITIVE. `10.4`.
 *
 * Two dialogs in this application open onto a search field and want the caret in it: *Add member to recruiter* and
 * *Add Recruiter*. Both got it by putting `autoFocus` on the `TextInput`, and `jsx-a11y/no-autofocus` is about to
 * become an error.
 *
 * ⚠️ DELETING `autoFocus` IS NOT A NO-OP, AND THE REASON IS A TIMING DETAIL NOBODY SHOULD HAVE TO REDISCOVER.
 * React applies `autoFocus` by calling `.focus()` in the COMMIT phase. Radix's focus scope runs in a PASSIVE effect,
 * which is later, and it only moves focus if it finds none inside the content (`hasFocusedCandidate`). So today Radix
 * looks, finds the search field already focused, and leaves it alone. Remove the attribute and Radix instead focuses
 * the first tabbable thing in the content -- which, because the header renders before `{children}`, is the **Close
 * (X) button**. The user opens a search dialog and their first keystroke goes nowhere.
 *
 * 🔑 SO THE REPLACEMENT BELONGS HERE, NOT AT THE CALL SITES. `Modal` is "the one overlay" and already owns every
 * other focus decision in a dialog: the trap, the Escape scoping, returning focus to the opener on close, and
 * refusing to close while `busy`. Initial focus is the same kind of decision, and putting it in the primitive means
 * the next dialog that wants it does not reinvent a ref-and-effect -- or reach for `autoFocus` again.
 *
 * The pair below is what makes either half believable: with the prop, the caret is where the caller asked; WITHOUT
 * it, the default is unchanged and focus still lands inside the dialog. A test for only the first would pass against
 * an implementation that had quietly broken every other dialog in the application.
 */

function DialogWithFocusTarget({ useInitialFocus }) {
  const searchRef = useRef(null)
  return (
    <Modal
      open
      onOpenChange={() => {}}
      title="Add member to recruiter"
      description="Synthetic Recruiter"
      initialFocusRef={useInitialFocus ? searchRef : undefined}
    >
      <input ref={searchRef} aria-label="Search members" />
      <button type="button">Somewhere else</button>
    </Modal>
  )
}

describe('Modal: where the caret lands when it opens', () => {
  it('hands the caret to `initialFocusRef` when the caller names one', async () => {
    render(<DialogWithFocusTarget useInitialFocus />)

    expect(await screen.findByLabelText('Search members')).toHaveFocus()
  })

  it('the must-pass: with no `initialFocusRef` the default is unchanged, and focus is still INSIDE the dialog', async () => {
    render(<DialogWithFocusTarget useInitialFocus={false} />)

    const dialog = await screen.findByRole('dialog')
    // Not asserted as "the Close button" on purpose: WHICH element Radix picks is Radix's
    // business and may change with a version. What this application depends on -- and what a
    // broken `onOpenAutoFocus` would destroy -- is that focus is inside the trap at all.
    // Focus on `document.body` here would mean the first Tab starts from outside the dialog.
    expect(dialog).toContainElement(document.activeElement)
    expect(screen.getByLabelText('Search members')).not.toHaveFocus()
  })
})
