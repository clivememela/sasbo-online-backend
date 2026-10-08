// @vitest-environment jsdom -- renderHook mounts into a container
import { describe, expect, it } from 'vitest'
import { renderHook } from '@testing-library/react'

import { usePaymentTypeValidation } from './usePaymentTypeValidation'
import { PAYMENT_TYPE_OPTIONS, PAYMENT_TYPE_PAYROLL } from '../config/memberConstants'
import { server } from '../test/msw/server'
import { DEFECTS, disabled, pin } from '../test/knownDefects'
import { committedVocabulary } from '../test/apiContractScan.js'

/**
 * TESTED AGAINST THE REAL OPTION VALUES, AND AGAINST THE SERVER'S RULE.
 *
 * The temptation here is to write the test against the strings the hook
 * currently compares. That would have encoded A6: the hook compared against
 * `'Payroll Deduction'` while `PAYMENT_TYPE_OPTIONS` could only produce
 * `'Payroll'`, so a test written to the hook's own vocabulary would have
 * passed forever while the branch it covers had never once executed.
 *
 * A6 is closed -- the OPTION moved to the server's spelling, 2026-09-17 -- and
 * the shape below is what kept it visible until it did. Every case is driven
 * from `PAYMENT_TYPE_OPTIONS`, the values a member can actually choose, and
 * checked against the rule the SERVER owns.
 *
 * THE SERVER'S RULE, transcribed from `PaymentType.java`:
 *
 *     DEBIT_ORDER("Debit Order")      requiresBankAccount() -> true
 *     PAYROLL("Payroll Deduction")    requiresBankAccount() -> false
 *
 * IT IS NO LONGER TRANSCRIBED. P3 wrote the rule out by hand here and said in
 * this comment that "Phase 4's vocabulary guard is what keeps this
 * transcription honest". P4 arrived and the honest thing was to delete the
 * transcription instead: the rule is now READ from `api-vocabulary.json`, the
 * artifact the server generates from `PaymentType.requiresBankAccount()`
 * itself and asserts fresh on every server build.
 *
 * That still crosses no repository boundary -- the artifact is a committed
 * file in this repository, copied by a documented step, exactly as
 * `api-surface.txt` is. What it removes is the second source of truth: a
 * hand-written table that agreed with the server on the day it was typed and
 * would drift the moment a payment type was added.
 */
const SERVER_RULE = Object.fromEntries(
  committedVocabulary().paymentTypes.map((type) => [
    type.displayName,
    { requiresBankAccount: type.requiresBankAccount },
  ]),
)

const render = (paymentType) => renderHook(() => usePaymentTypeValidation(paymentType)).result.current

describe('usePaymentTypeValidation', () => {
  it('the options this hook must handle are the ones the form offers', () => {
    // Pins the vocabulary itself. If someone adds a payment type, this is the
    // first thing that says the rule table below has not been extended.
    expect(PAYMENT_TYPE_OPTIONS).toEqual(['Debit Order', 'Payroll Deduction'])
    expect(Object.keys(SERVER_RULE).sort()).toEqual([...PAYMENT_TYPE_OPTIONS].sort())
  })

  describe('the bank-account rule agrees with the server, option by option', () => {
    it.each(PAYMENT_TYPE_OPTIONS)('%s', (option) => {
      const { isBankAccountRequired, shouldShowBankAccountField, bankAccountFieldRequired } =
        render(option)
      const expected = SERVER_RULE[option].requiresBankAccount

      expect(
        isBankAccountRequired,
        `For "${option}" the server's PaymentType.requiresBankAccount() answers ` +
          `${expected}, and MemberValidator enforces that on every write. The client ` +
          `answers ${isBankAccountRequired}. Whichever is wrong, the member is either ` +
          `asked for a bank account the server will discard, or not asked for one the ` +
          `server will reject the record without.`,
      ).toBe(expected)

      // The derived flags are what the form actually reads.
      expect(shouldShowBankAccountField).toBe(expected)
      expect(bankAccountFieldRequired).toBe(expected)
    })
  })

  /**
   * A6, CLOSED 2026-09-17 -- and this is what it was flipped to.
   *
   * The pin here used to assert that NO option could switch the payroll branch on, and it is the
   * test that went red when `PAYMENT_TYPE_OPTIONS` took the server's spelling. Both halves of the
   * pair are gone; what replaces them is the same scan, asserting the opposite: exactly one of the
   * options the form offers reaches the branch, and it is the one the server calls PAYROLL.
   *
   * Kept as a SCAN over the options rather than as `render('Payroll Deduction')` for the reason the
   * pin was written that way -- a hand-written string here would agree with the hook forever, which
   * is precisely how A6 survived. Driving it from the options means renaming the option again
   * reddens this.
   */
  describe('the branch A6 left unreachable', () => {
    it('exactly one offered option switches the payroll branch on', () => {
      const switchedOn = PAYMENT_TYPE_OPTIONS.filter((o) => render(o).isPayrollDeduction)
      expect(
        switchedOn,
        'The payroll branch is once again unreachable from the form, or reachable from more ' +
          'than one option. That was A6: a rule that reads correctly and never runs.',
      ).toEqual([PAYMENT_TYPE_PAYROLL])
    })

    it('and it disables the two fields payroll members do not fill in', () => {
      const { bankAccountFieldDisabled, debitDayFieldDisabled } = render(PAYMENT_TYPE_PAYROLL)
      expect(bankAccountFieldDisabled).toBe(true)
      expect(debitDayFieldDisabled).toBe(true)
    })

    it('the option the browser offers is the one the server publishes', () => {
      // Read from the generated artifact, not typed: this is the comparison A6 got wrong.
      expect(Object.keys(SERVER_RULE)).toContain(PAYMENT_TYPE_PAYROLL)
    })
  })

  describe('where the rule comes from', () => {
    it.skip(
      disabled(DEFECTS.A9, 'the rule must come from the server, not a hardcoded string'),
      async () => {
        // `GET /api/members/metadata/payment-types` publishes `requiresBankAccount`
        // per type. It is the server's authority, it is already exposed, and the
        // client never calls it. Written to the standard: the hook consults the
        // published rule.
        const requested = []
        const listener = ({ request }) => requested.push(new URL(request.url).pathname)
        server.events.on('request:start', listener)
        try {
          render('Debit Order')
          await new Promise((r) => setTimeout(r, 0))
        } finally {
          server.events.removeListener('request:start', listener)
        }
        expect(requested).toContain('/api/members/metadata/payment-types')
      },
    )

    it(pin(DEFECTS.A9, 'the hook still consults nothing and hardcodes the rule'), async () => {
      const requested = []
      const listener = ({ request }) => requested.push(new URL(request.url).pathname)
      server.events.on('request:start', listener)
      try {
        for (const option of PAYMENT_TYPE_OPTIONS) render(option)
        await new Promise((r) => setTimeout(r, 0))
      } finally {
        server.events.removeListener('request:start', listener)
      }
      expect(
        requested,
        'A9 has been fixed -- the client now reads the rule from the server rather than ' +
          're-implementing it. Delete this pin, un-skip the test above, and close the row.',
      ).toEqual([])
    })
  })

  describe('the memo does not lie', () => {
    it('recomputes when the payment type changes', () => {
      const { result, rerender } = renderHook(({ t }) => usePaymentTypeValidation(t), {
        initialProps: { t: 'Debit Order' },
      })
      expect(result.current.isBankAccountRequired).toBe(true)
      rerender({ t: PAYMENT_TYPE_PAYROLL })
      expect(
        result.current.isBankAccountRequired,
        'The memo returned a stale answer after the payment type changed. The form would ' +
          'keep demanding a bank account after the member switched to payroll.',
      ).toBe(false)
    })

    it.each([undefined, null, '', 'Nonsense'])('treats %s as requiring no bank account', (v) => {
      expect(render(v).isBankAccountRequired).toBe(false)
    })
  })
})
