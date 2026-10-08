import { expect, test } from '@playwright/test'
import { CAPTURE_FIELDS, TABS_REMOVED_FROM_THE_CLIENT, fieldsOnRemovedTabs } from '../../pages/MemberCapturePage.js'

/**
 * The capture catalogue's own bookkeeping, asserted where it RUNS.
 *
 * 🔴 THIS USED TO LIVE IN A SKIPPED SCENARIO. The closed list of fields on removed
 * tabs sat in the `every field on the form is labelled` step, whose only caller
 * is A35's scenario, tagged `@skip`. So when a tab disappeared and the list was
 * edited to match, nothing ever executed it -- a guard that looked gated and was
 * not. None of this needs a browser: it is data in `pages/MemberCapturePage.js`.
 */
test.describe('the capture catalogue', () => {
  test('the fields on removed tabs are exactly the ones on record', () => {
    // A CLOSED LIST, because this is a closed set: a tab vanishing is a deliberate
    // act upstream, and the right response is that this reddens and somebody
    // decides, per field, whether the member record can still store what it held.
    expect(
      fieldsOnRemovedTabs().sort(),
      'the catalogue fields the labelling walk can no longer reach. If this GREW, a tab ' +
        'left the client and its fields left the walk silently -- update ' +
        'TABS_REMOVED_FROM_THE_CLIENT deliberately, and this list with it',
    ).toEqual(
      [
        '2nd Remarks Field',
        'COSATU Member?',
        'Deduct Subscriptions through ACB (Y/N)',
        'Home Loan Reduced Rate (Y/N)',
        'Local?',
        'MEMNET Member (Y/N)',
        'Print Label for Postage (Y/N)',
        'Print Membership Cards (Y/N)',
        'Regional?',
        'Remarks / Comments',
        'Suspension Code',
        'Use Alternate Address (Y/N)',
      ].sort(),
    )
  })

  test('every removed tab names a tab the catalogue actually used', () => {
    // A misspelt tab name in TABS_REMOVED_FROM_THE_CLIENT would remove nothing and
    // pass the list above only if that list were also wrong -- this catches the
    // first mistake on its own.
    const used = new Set(Object.values(CAPTURE_FIELDS).map((field) => field.tab))
    expect(TABS_REMOVED_FROM_THE_CLIENT.filter((tab) => !used.has(tab)), 'removed tabs no catalogue field sits on').toEqual([])
  })
})
