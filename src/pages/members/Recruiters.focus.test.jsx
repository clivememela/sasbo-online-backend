// @vitest-environment jsdom -- the two search dialogs, and which control holds the caret when they open
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { screen, within } from '@testing-library/react'
import { http, HttpResponse } from 'msw'

import { Recruiters } from './Recruiters'
import { renderWithProviders } from '../../test/renderWithProviders'
import { server } from '../../test/msw/server'
import { pageFixtures } from '../../test/msw/pageFixtures'
import { setupUser } from '../../test/user'
import { TestAuthProvider } from '../../test/authTestUtils'
import { PERMISSIONS } from '../../auth/permissions'

/**
 * 🔴 WRITTEN BEFORE `autoFocus` WAS REMOVED FROM EITHER DIALOG, AND THAT ORDER IS THE PROOF. `10.4`.
 *
 * Both dialogs exist to be typed into: each opens onto a "Search members" field and nothing else can usefully be
 * done until something is typed there. They got the caret from `autoFocus`, which `jsx-a11y/no-autofocus` is about
 * to make an error.
 *
 * ⚠️ DELETING THE ATTRIBUTE IS A REAL REGRESSION, NOT A TIDY-UP, and the mechanism is invisible from the call site.
 * React applies `autoFocus` in the COMMIT phase; Radix's focus scope runs in a PASSIVE effect afterwards and only
 * acts when it finds NO focus inside the dialog. So today it finds the search field already focused and leaves it.
 * Remove the attribute and Radix focuses the first tabbable element in the content instead -- the header's Close
 * (X) button, because the header renders before `{children}`. The dialog would open, the user would type, and the
 * characters would go nowhere.
 *
 * These two tests assert today's behaviour so that the removal makes them FAIL, and the `initialFocusRef`
 * replacement makes them pass again. Written afterwards they would only have shown that the replacement does
 * something -- not that it does the same thing. The primitive's own half is `Modal.initialFocus.test.jsx`.
 */

const CHANNEL = { channelId: 1, name: 'Synthetic Channel', enabled: true, members: 1, banks: [], branches: [] }

const MEMBER = {
  id: 7,
  membershipNumber: 'M0007',
  title: 'Mr',
  initials: 'S',
  firstNames: 'Synthetic',
  surname: 'Joiner',
  status: 'Active',
  recruitmentRepresentativeId: 1,
}

/** Add Member is offered to a user who may change a member's channel (MEMBERS_EDIT). */
const asStaff = (ui) => (
  <TestAuthProvider permissions={[PERMISSIONS.MEMBERS_VIEW, PERMISSIONS.MEMBERS_EDIT]}>{ui}</TestAuthProvider>
)

describe('Recruiters, where the caret lands when a search dialog opens', () => {
  beforeEach(() => {
    vi.spyOn(console, 'error').mockImplementation(() => {})
    server.use(...pageFixtures)
    server.use(
      http.get('*/api/recruitment-channels/summary', () => HttpResponse.json([CHANNEL])),
      http.get('*/api/recruitment-channels/1/members', () => HttpResponse.json({
        content: [MEMBER], page: 0, size: 100, totalElements: 1, totalPages: 1,
      })),
      http.get('*/api/recruitment-channels/member-candidates', () => HttpResponse.json({ rows: [MEMBER], more: false })),
    )
  })

  it('Add Recruiter opens with the caret in its search field, not on the Close button', async () => {
    const user = setupUser()
    renderWithProviders(asStaff(<Recruiters />))

    await user.click(await screen.findByRole('button', { name: /Add Recruiter/i }))

    const dialog = await screen.findByRole('dialog', { name: 'Add Recruiter' })
    expect(within(dialog).getByLabelText('Search members')).toHaveFocus()
  })

  it('Add member to recruiter does the same', async () => {
    const user = setupUser()
    renderWithProviders(asStaff(<Recruiters />))

    await user.click(
      await screen.findByRole('button', { name: /show the members recruited by this channel/i }),
    )
    await user.click(await screen.findByRole('button', { name: /Add Member/i }))

    const dialog = await screen.findByRole('dialog', { name: 'Add member to recruiter' })
    expect(within(dialog).getByLabelText('Search members')).toHaveFocus()
  })

  it('the must-pass: nothing takes the caret before a dialog is opened', async () => {
    renderWithProviders(asStaff(<Recruiters />))
    await screen.findByRole('button', { name: /Add Recruiter/i })

    // 🔑 WITHOUT THIS, A REPLACEMENT THAT FOCUSED THE FIELD UNCONDITIONALLY WOULD PASS BOTH
    // TESTS ABOVE while doing the exact thing `no-autofocus` exists to prevent -- taking the
    // caret on arrival from a user who is still reading the page.
    expect(screen.queryByLabelText('Search members')).not.toBeInTheDocument()
    expect(document.body).toHaveFocus()
  })
})
