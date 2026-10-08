import { describe, expect, it } from 'vitest'

import fixture from '../../test/fixtures/recruitment-channel-fixture.json'
import { bankPlacesOf, channelRowsFromSummary } from './channelFigures'

/**
 * RC15: THE RECRUITERS SCREEN'S ROWS, FROM THE SERVER'S FIGURES.
 *
 * `recruitment-channel-fixture.json` is the same file the server's `RecruitmentChannelMembersServiceImplTest` reads, where the
 * grouped SQL is held to `expected.all`. Step 1 (client 3c647cd) held the screen's own count to it too, beside these
 * rows, and they agreed string for string; step 2 removed that count with the member register it was loaded from.
 */

const channels = fixture.channels.map((name, i) => ({ id: i + 1, name }))

/** The server's answer for the fixture, as `GET /api/recruitment-channels/summary` sends it. */
const summary = fixture.expected.all.map((c) => ({
  channelId: channels.find((ch) => ch.name === c.name).id,
  name: c.name,
  enabled: true,
  members: c.members,
  banks: c.banks,
  branches: c.branches,
}))

const places = bankPlacesOf(fixture.banks)
const byName = (rows) => Object.fromEntries(rows.map((row) => [row.name, row]))

describe('RC15: the rows, from the server\'s figures', () => {
  it('every channel\'s count, banks and branches are the fixture\'s', () => {
    const rows = byName(channelRowsFromSummary(summary, places))

    for (const expected of fixture.expected.all) {
      expect(rows[expected.name], expected.name).toMatchObject({
        count: expected.members,
        banks: expected.banks.join(', '),
        branches: expected.branches.join(', '),
      })
    }
  })

  it('each channel\'s provinces and areas follow from its banks, by the branch listing', () => {
    expect(byName(channelRowsFromSummary(summary, places)).BEC).toMatchObject({
      provinces: 'Invented North, Invented South',
      areas: 'Invented Area A, Invented Area B',
    })
  })

  it('the rows keep the screen\'s order: by name, every channel listed, a channel nobody joined reading 0', () => {
    const rows = channelRowsFromSummary(summary, places)

    expect(rows.map((row) => row.name)).toEqual(['BEC', 'EORO', 'MSO', 'RO', 'Website', 'Youth'])
    expect(byName(rows).EORO).toMatchObject({ count: 0, banks: '', branches: '', provinces: '', areas: '' })
  })

  it('what RC15 was: matched on the RECRUITER\'s id, RO counted the member of recruiter 4 and not its own two', () => {
    // The old join, written out once so the difference stays on record: each channel took the members whose
    // recruitmentRepresentativeId was the channel's id.
    const old = fixture.members.filter((m) => m.recruiterId === channels.find((c) => c.name === 'RO').id).length

    expect(old).toBe(1)
    expect(byName(channelRowsFromSummary(summary, places)).RO.count).toBe(2)
  })

  it('the must-pass: no answer, or none yet, is no rows -- never a throw', () => {
    expect(channelRowsFromSummary(undefined, places)).toEqual([])
    expect(channelRowsFromSummary([], {})).toEqual([])
  })
})
