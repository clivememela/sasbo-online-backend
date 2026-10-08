import { describe, expect, it } from 'vitest'
import { MEMBER_TABS } from './memberConstants'

/**
 * The capture form's tabs, as a CLOSED list.
 *
 * 🔑 THE AUTOMATION SUITE RELIES ON THIS AND CANNOT CHECK IT. It keeps
 * `TABS_REMOVED_FROM_THE_CLIENT` in its capture page object, so a scenario
 * refuses by name -- rather than timing out -- on a tab that is not there. It
 * cannot read this constant, because a path into a sibling checkout is forbidden
 * in that repository, and its own docblock says the executable half belongs
 * beside the constant. This is that half.
 *
 * ⚠️ IT WAS MISSING UNTIL 2026-09-14, and how that surfaced is the argument for
 * it. Upstream client 4a7a67b removed the 'Sasbo Structures' tab. The only test
 * that noticed was an A39 pin that happened to drive that tab -- and the tab's
 * removal is what retired the pin. So the one assertion that would have caught
 * a tab going was deleted by the tab going.
 */
describe('the capture form tabs', () => {
  it('are exactly the tabs on record', () => {
    expect(
      MEMBER_TABS.map((tab) => tab.label),
      'a capture tab was added, removed or renamed. Update this list, and move ' +
        'TABS_REMOVED_FROM_THE_CLIENT and the capture catalogue in the automation ' +
        'suite (pages/MemberCapturePage.js) in the same change, or its scenarios ' +
        'will drive a tab that is not there',
    ).toEqual(['Personal Information', 'Employment & Bank', 'Recruitment Channel', 'Membership & Subscriptions'])
  })

  it('put Recruitment Channel straight after Employment & Bank -- SASBO\'s order, 18 Sep 2026', () => {
    const labels = MEMBER_TABS.map((tab) => tab.label)
    expect(
      labels.indexOf('Recruitment Channel'),
      'SASBO asked for the recruitment channel to come before membership & subscriptions',
    ).toBe(labels.indexOf('Employment & Bank') + 1)
    expect(labels.at(-1)).toBe('Membership & Subscriptions')
  })
})
