import { expect } from 'vitest'
import { screen, within } from '@testing-library/react'
import { axe } from 'vitest-axe'

/**
 * The shared assertions. A contract holds because ONE function enforces it,
 * not because every author remembered.
 *
 * Every message here names the CONSEQUENCE, not the mismatch.
 * `expected [] to equal ['Passport']` says what differed; "compared against a
 * value no option can produce -- this branch can never be true" says what
 * breaks. The second is what makes a red run actionable by someone who did not
 * write the test.
 */

/**
 * The three-state rule, asserted as ONE thing.
 *
 * An error rendered as an empty table is a CONFIDENT WRONG ANSWER: it tells an
 * administrator there are no members when the backend is down. Only an
 * assertion that checks BOTH halves can tell the two apart -- the error
 * affordance present AND the empty-state text absent -- which is why this is a
 * single helper and not a habit repeated on every list page.
 *
 * @param options.emptyText text the EMPTY state renders and the error state
 *                          must not. Required: without it this helper degrades
 *                          into "something rendered".
 * @param options.container optional scope, for a page with several regions.
 */
export async function expectErrorState({ emptyText, container } = {}) {
  if (!emptyText) {
    throw new Error(
      'expectErrorState needs the empty-state text. Without it this assertion cannot ' +
        'distinguish "the backend is down" from "there is nothing here", which is the ' +
        'entire defect it exists to catch.',
    )
  }

  const scope = container ? within(container) : screen

  const errorAffordance =
    scope.queryByRole('alert') ??
    scope.queryByText(/error|failed|unable|try again|something went wrong/i)

  expect(
    errorAffordance,
    'THE CONFIDENT WRONG ANSWER: the request failed and the page shows no error ' +
      'affordance at all. An administrator reads this screen as "the data is fine"; ' +
      'it means "the backend is down". A failed list must announce itself.',
  ).not.toBeNull()

  expect(
    scope.queryByText(emptyText),
    `THE CONFIDENT WRONG ANSWER: the request failed and the page is still showing the ` +
      `EMPTY state ("${emptyText}"). "There are no records" and "we could not reach the ` +
      `server" are different facts, and this screen is reporting the wrong one.`,
  ).toBeNull()

  const retry =
    scope.queryByRole('button', { name: /retry|try again|reload|refresh/i }) ??
    scope.queryByRole('link', { name: /retry|try again|reload|refresh/i })

  return { errorAffordance, retry }
}

/**
 * A field was rejected, asserted BY FIELD NAME rather than by message.
 *
 * Asserting the message couples every validation test to copy, so a wording
 * change reddens tests that have nothing to do with the rule. The stable fact
 * is which field the form refused.
 */
export function expectFieldRejected(field, { container } = {}) {
  const scope = container ? within(container) : screen

  const control =
    scope.queryByLabelText(new RegExp(field, 'i')) ?? scope.queryByRole('textbox', { name: new RegExp(field, 'i') })

  expect(
    control,
    `expectFieldRejected("${field}") could not find that field at all. A validation ` +
      `assertion against a field that is not on the page passes for the wrong reason ` +
      `the day the field is renamed.`,
  ).not.toBeNull()

  const invalid =
    control.getAttribute('aria-invalid') === 'true' ||
    Boolean(control.getAttribute('aria-errormessage')) ||
    Boolean(
      control.id &&
        scope.queryByText((_, el) => el?.getAttribute?.('data-error-for') === control.id),
    ) ||
    // Fallback: the form renders the error as a sibling of the control.
    Boolean(control.parentElement?.querySelector('[role="alert"], .text-red-500, .error'))

  expect(
    invalid,
    `The form ACCEPTED "${field}" when the rule says it must reject it. A validator ` +
      `that never refuses anything is indistinguishable from no validator, and the ` +
      `bad value reaches the API -- which does not check it either.`,
  ).toBe(true)
}

/**
 * The accessibility gate.
 *
 * `color-contrast` is OFF. jsdom has no canvas, so axe cannot compute contrast
 * at all -- it does not fail, it silently reports nothing. Saying that here is
 * more honest than carrying a rule that cannot run. Contrast is a
 * device-matrix job and is out of this arc's scope, recorded rather than
 * pretended.
 */
export const AXE_RULES = { 'color-contrast': { enabled: false } }

export async function expectNoA11yViolations(container, { rules = {} } = {}) {
  const results = await axe(container, {
    rules: {
      ...AXE_RULES,
      ...rules,
    },
  })

  // 🔑 `toHaveNoViolations`, THE MATCHER THIS SUITE REGISTERS AND HAD NEVER
  // CALLED -- CI-7, closed in the one direction BR-5 leaves open.
  //
  // `setup.js` registers vitest-axe's matchers and `harness.test.jsx` asserts
  // the matcher is present. Nothing used it: every accessibility assertion
  // hand-rolled its own summary and compared `violations` to length 0. So the
  // registration's only consumer was the test asserting the registration
  // existed -- a mechanism kept alive entirely by its own guard.
  //
  // 🔴 AND DELETING IT WAS THE WRONG FIX, which is why BR-5 exists. The
  // registration is the ONLY evidence of a recorded finding: vitest-axe@0.1.0
  // ships an EMPTY `dist/extend-expect.js`, so the documented registration path
  // registers nothing, silently. That was caught only because `harness.test.jsx`
  // asserts the matcher is a function. Removing the assertion would re-open that
  // hole undetectably at the next dependency bump.
  //
  // Using it closes the finding from the other end: the matcher is now exercised
  // by every a11y assertion in the suite, so a future version that registers
  // nothing fails HERE as well as in the harness test.
  //
  // ⚠️ THE CONSEQUENCE MESSAGE SURVIVES, and that was the reason the literal fix
  // was declined once already. `expect(value, message)` keeps it as the
  // assertion's prefix, and vitest-axe's own output is richer than the summary
  // it replaces -- rule id, impact, description, help URL and the offending
  // node's HTML, rather than a selector list. So this is not the trade that was
  // refused; it is the same message plus a better report, and ~12 lines of
  // hand-rolled formatting removed. The one thing genuinely lost is the leading
  // violation COUNT, which the matcher's own report enumerates instead.
  expect(
    results,
    'This surface has accessibility violations. Each one is a member or an ' +
      'administrator who cannot complete the task at all.',
  ).toHaveNoViolations()
}
