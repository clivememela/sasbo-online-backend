// @vitest-environment jsdom -- renders the capture form and types into it
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { screen, waitFor } from '@testing-library/react'
import { http, HttpResponse } from 'msw'

import CaptureMember from './CaptureMember'
import { renderWithProviders } from '../../test/renderWithProviders'
import { server } from '../../test/msw/server'
import { pageFixtures } from '../../test/msw/pageFixtures'
import { IDENTITY_TYPE_OPTIONS } from '../../config/memberConstants'

import { setupUser } from '../../test/user'

/**
 * THE FIELD ROUND-TRIP -- the client half of the standards' worst defect class.
 *
 * The defect being guarded against is not "the form is broken". It is the one
 * where a field is typed into, the form saves, the screen says saved, and the
 * value never reached the request body -- a dropped `name`, a state key that
 * does not match, a field bound to the wrong slice of state. Everything looks
 * right from the outside, which is precisely why only an assertion on the
 * REQUEST BODY catches it.
 *
 * So these tests assert what went ON THE WIRE, not what is in the DOM after
 * typing. Asserting the input's own value only proves React re-rendered.
 *
 * The queries are by LABEL, never by class name or component internals, so a
 * Tailwind refactor cannot redden them. That is the whole reason this layer is
 * cheaper than the browser.
 */

/** Captured request bodies, so an assertion can be made about the wire. */
let posted

const captureCreate = () =>
  http.post('*/api/members', async ({ request }) => {
    posted = await request.json()
    return HttpResponse.json({ id: 1, ...posted })
  })


/**
 * What the form says when it refuses to save.
 *
 * 🔴 IT USED TO BE `window.alert`, AND THESE TESTS SPIED ON IT. `04.2` replaced that
 * with an error summary rendered on the page: a native modal blocks the thread, cannot be
 * styled, is suppressible, and -- on a six-tab form -- leaves the member facing an
 * unchanged screen the moment they dismiss it. The spy is KEPT so a regression to `alert()`
 * is loud rather than silent, and `refusals()` reads the summary the form now shows.
 */
const alerts = []

const refusals = () =>
  [...document.querySelectorAll('[aria-labelledby$="save-notice-title"] li')]
    .map((li) => li.textContent)
    .join('\n')

beforeEach(() => {
  posted = undefined
  alerts.length = 0
  vi.spyOn(window, 'alert').mockImplementation((m) => alerts.push(String(m)))
  server.use(...pageFixtures, captureCreate())
})

/**
 * Query by label, ANCHORED.
 *
 * An unanchored substring match is not good enough on this form: the identity
 * NUMBER field's label is `form.identityType || "Select Identity Type before
 * entering"`, so a loose match for "Identity Type" hits both it and the
 * Identity Type select. Anchoring at the start of the label is what keeps
 * these queries about the field the test means, while still tolerating the
 * trailing required-marker asterisk.
 */
const escapeForRegex = (s) => s.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')

const labelled = (label) =>
  screen.getByLabelText(new RegExp(`^${escapeForRegex(label)}`))

const fill = async (user, label, value) => {
  const field = labelled(label)
  await user.clear(field)
  await user.type(field, value)
}

const save = async (user) => {
  await user.click(screen.getByRole('button', { name: /save member/i }))
  const send = screen.queryByRole('button', { name: /send welcome letter/i })
  if (send) await user.click(send)
}

/**
 * Switch the capture off Debit Order.
 *
 * The form defaults `paymentType` to "Debit Order", which makes three banking
 * fields required. That is correct behaviour and it agrees with the server's
 * `PaymentType.requiresBankAccount()` -- so a round-trip test that ignored it
 * would be testing an impossible capture.
 *
 * Choosing "Payroll Deduction" is also the honest half of register A9. A6 is
 * now CLOSED -- the option is spelled the way the server spells it, so
 * `usePaymentTypeValidation`'s `isPayrollDeduction` branch is reachable at
 * last. A9 is not: no screen imports that hook, so the rule it carries still
 * comes from a hardcoded string rather than from the server's published
 * `requiresBankAccount()`, and the capture below succeeds because the FORM's
 * own `paymentType === "Debit Order"` test agrees with the server, not because
 * anything consulted it. `usePaymentTypeValidation.test.jsx` pins that.
 *
 * The form is TABBED and renders only the active tab, so Payment Type has to
 * be reached by opening "Employment & Bank" first. That is worth doing through
 * the tab button rather than by reaching into state: a field the user cannot
 * navigate to is a field that does not work, and clicking the tab is what
 * proves the navigation still does.
 */
const openTab = async (user, label) => {
  await user.click(screen.getByRole('button', { name: label }))
}

const choosePayroll = async (user) => {
  await openTab(user, /Employment & Bank/i)
  await user.selectOptions(labelled('Payment Type'), 'Payroll Deduction')
  await openTab(user, /Personal Information/i)
}

describe('CaptureMember round-trips its fields', () => {
  it('every value typed in reaches the request body', async () => {
    const user = setupUser()
    renderWithProviders(<CaptureMember />)

    await fill(user, 'First Name(s)', 'Synthetic')
    await fill(user, 'Surname', 'Testperson')
    await fill(user, 'Personal Email', 'synthetic@example.test')
    await choosePayroll(user)

    // Identity type is a select, and its own value gates the identity-number
    // field -- so it must be chosen before the number can be typed.
    await user.selectOptions(labelled('Identity Type'), 'I.D. Number')
    await fill(user, 'I.D. Number', '9001010000080')

    await save(user)

    await waitFor(() =>
      expect(
        posted,
        `Save was clicked and no POST reached /api/members. The form either failed ` +
          `validation silently or never called the service. Alerts raised: ` +
          `${JSON.stringify(alerts)}; refusals shown: ${refusals()}`,
      ).toBeDefined(),
    )

    // THE ACTUAL ASSERTION: the wire, not the DOM.
    expect(posted).toMatchObject({
      firstNames: 'Synthetic',
      surname: 'Testperson',
      identityType: 'I.D. Number',
      identityNumber: '9001010000080',
    })
  })

  it('refuses to submit at all when a required field is empty', async () => {
    const user = setupUser()
    renderWithProviders(<CaptureMember />)

    await fill(user, 'Surname', 'Testperson')
    await save(user)

    expect(
      posted,
      'The form POSTed a member with no first name, which the server would then have to ' +
        'refuse -- or worse, accept. Client-side validation that does not stop the ' +
        'request is decoration.',
    ).toBeUndefined()
    // The refusal names the FIELD, which `alert()` also did -- and it stays on the screen,
    // which `alert()` did not.
    expect(refusals()).toMatch(/First Name/i)
    expect(alerts, 'a native alert() is back').toEqual([])
  })

  it('refuses an identity number that fails the SA ID checksum', async () => {
    const user = setupUser()
    renderWithProviders(<CaptureMember />)

    await fill(user, 'First Name(s)', 'Synthetic')
    await fill(user, 'Surname', 'Testperson')
    await choosePayroll(user)
    await user.selectOptions(labelled('Identity Type'), 'I.D. Number')
    // Correct length, wrong check digit -- the vector named wrongCheckDigit in
    // the validateSAID specification table.
    await fill(user, 'I.D. Number', '9001010000081')

    await save(user)

    expect(
      posted,
      'A member with an invalid SA ID number was POSTed. The server does NOT check the ' +
        'checksum (register A8) -- it accepts any 13 digits -- so this form is the only ' +
        'thing standing between a typo and the database.',
    ).toBeUndefined()
  })

  it('derives date of birth and gender from a valid I.D. Number', async () => {
    const user = setupUser()
    renderWithProviders(<CaptureMember />)

    await user.selectOptions(labelled('Identity Type'), 'I.D. Number')
    await fill(user, 'I.D. Number', '9001010000080')

    // Derived, not typed: the two fields are disabled for I.D. Number exactly
    // because the number already carries the answer.
    await waitFor(() =>
      expect(labelled('Gender')).toHaveValue('Female'),
    )
  })
})

describe('the identity-type vocabulary', () => {
  it('the passport branch compares against a value the dropdown cannot produce', () => {
    // Register A7, asserted rather than described. The validation reads
    // `form.identityType === "Passport"`; the options offer "Passport Number".
    // The rule -- date of birth is required when the identity is a passport --
    // has therefore never once run.
    //
    // This is deliberately NOT a skipped test with a pin. The behaviour lives
    // in a string comparison inside a 858-line component, and the honest,
    // stable assertion is the one about the vocabulary itself: the two sides
    // do not agree. Phase 4's vocabulary guard is what fixes both halves
    // together; this records the client side of the disagreement.
    expect(IDENTITY_TYPE_OPTIONS).toContain('Passport Number')
    expect(
      IDENTITY_TYPE_OPTIONS,
      'A7 has been fixed on the client side -- the dropdown now offers the exact string ' +
        'the validation compares against. Check that the server agrees before closing the ' +
        'row.',
    ).not.toContain('Passport')
  })

  const capturePassportMember = async (user) => {
    renderWithProviders(<CaptureMember />)
    await fill(user, 'First Name(s)', 'Synthetic')
    await fill(user, 'Surname', 'Testperson')
    await choosePayroll(user)
    await user.selectOptions(labelled('Identity Type'), 'Passport Number')
    await fill(user, 'Passport Number', 'A12345678')
    await save(user)
  }

  it('A7 (fixed): a passport capture must require a date of birth', async () => {
    // Written to the standard. Unlike an I.D. Number, a passport number
    // carries no date to derive from -- so if the form does not insist on one,
    // the record is simply incomplete, and nothing downstream can tell that
    // from a member who genuinely has no date on file.
    await capturePassportMember(setupUser())

    expect(posted).toBeUndefined()
    expect(refusals()).toMatch(/date of birth/i)
    expect(alerts, 'a native alert() is back').toEqual([])
  })

})
