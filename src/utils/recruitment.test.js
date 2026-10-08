import { describe, expect, it } from 'vitest'

import { DERIVATION } from './derivation'
import {
  channelHasNoRecruiter, findRecruiter, isOrganiser, recruitedArea, recruiterAreaText, recruitersFor,
} from './recruitment'

/**
 * THE RECRUITMENT CHANNEL'S RULES, AS SASBO GAVE THEM ON 18 SEP 2026 -- see `recruitment.js`.
 *
 * The fixture is the SHAPE of the real recruiter table (`recruiters.json` on the server): organisers with a region and
 * mostly an area letter, one organiser without a letter yet, and the channel rows that table also holds, which carry
 * neither. Synthetic names; the letters and regions are the real kind of value.
 */
const RECRUITERS = [
  { id: 1, name: 'ADMINISTRATION' },
  { id: 2, name: 'SYNTHETIC ORGANISER', region: 'JOHANNESBURG', areaCode: 'X' },
  { id: 3, name: 'SECOND ORGANISER', region: 'PORT ELIZABETH', areaCode: 'M' },
  { id: 4, name: 'LETTERLESS ORGANISER', region: 'PRETORIA' },
  { id: 5, name: 'WEBSITE' },
  { id: 6, name: 'EORO (EACH ONE REACH ONE)' },
  { id: 7, name: 'YOUTH' },
]
const names = (rows) => rows.map((row) => row.name)

describe('which recruiters a channel offers', () => {
  it('🗣️ none until a recruiter type is chosen -- the type comes first', () => {
    expect(recruitersFor('', RECRUITERS)).toEqual([])
    expect(recruitersFor(undefined, RECRUITERS)).toEqual([])
  })

  it('🗣️ none for Website or EORO -- "everything else must fall away"', () => {
    expect(recruitersFor('Website', RECRUITERS)).toEqual([])
    expect(recruitersFor('EORO', RECRUITERS)).toEqual([])
    // Renamed in maintenance to the recruiter table's own spelling, it is still EORO.
    expect(recruitersFor('EORO (Each One Reach One)', RECRUITERS)).toEqual([])
    expect(channelHasNoRecruiter(' website ')).toBe(true)
  })

  it('🗣️ only the organisers for RO -- "RO recruiter names must come here"', () => {
    expect(names(recruitersFor('RO', RECRUITERS))).toEqual(['SYNTHETIC ORGANISER', 'SECOND ORGANISER', 'LETTERLESS ORGANISER'])
  })

  it('the must-pass: every other channel still offers everybody, until the table can tell them apart', () => {
    // Without this, "RO lists organisers" is satisfied by a rule that lists organisers for EVERY channel -- which
    // would silently take the channel rows away from MSO, Youth and BEC captures.
    expect(recruitersFor('MSO', RECRUITERS)).toHaveLength(RECRUITERS.length)
    expect(recruitersFor('Youth', RECRUITERS)).toHaveLength(RECRUITERS.length)
    expect(recruitersFor('BEC', RECRUITERS)).toHaveLength(RECRUITERS.length)
    expect(channelHasNoRecruiter('RO')).toBe(false)
    expect(channelHasNoRecruiter('MSO')).toBe(false)
  })

  it('an organiser is a row with a region; the channel rows have none', () => {
    expect(isOrganiser(RECRUITERS[1])).toBe(true)
    expect(isOrganiser(RECRUITERS[3])).toBe(true)
    expect(isOrganiser(RECRUITERS[4])).toBe(false)
    expect(isOrganiser({ name: 'X', region: '   ' })).toBe(false)
  })
})

describe('the area, read off the recruiter', () => {
  it('🔑 is the organiser\'s area letter and region, never a list to choose from', () => {
    expect(recruiterAreaText(RECRUITERS[1])).toBe('Area X · Johannesburg')
    expect(recruiterAreaText(RECRUITERS[2])).toBe('Area M · Port Elizabeth')
    expect(recruiterAreaText({ areaCode: 'Q' })).toBe('Area Q')
  })

  it('is DERIVED, and says from whom, when the recruiter has a letter', () => {
    const area = recruitedArea({ channel: 'RO', recruiterName: 'synthetic organiser', recruiters: RECRUITERS })

    expect(area).toEqual({
      state: DERIVATION.DERIVED,
      text: 'Area X · Johannesburg',
      note: "Filled in from SYNTHETIC ORGANISER's area.",
    })
  })

  it('is NO_MATCH, and says so, when the organiser has no letter on file yet -- never a guess', () => {
    const area = recruitedArea({ channel: 'RO', recruiterName: 'LETTERLESS ORGANISER', recruiters: RECRUITERS })

    expect(area.state).toBe(DERIVATION.NO_MATCH)
    expect(area.text).toBe('')
    expect(area.note).toBe('No area is on file for LETTERLESS ORGANISER yet.')
  })

  it('is NOT_APPLICABLE before a recruiter is chosen, and for Website and EORO', () => {
    const none = recruitedArea({ channel: 'RO', recruiterName: '', recruiters: RECRUITERS })
    expect(none).toEqual({ state: DERIVATION.NOT_APPLICABLE, text: '', note: undefined })
    // The prompt is the caller's: capture asks for a recruiter, a read-only record cannot.
    expect(recruitedArea({ channel: 'RO', recruiterName: '', recruiters: RECRUITERS, noRecruiterNote: 'Choose one.' }).note)
      .toBe('Choose one.')
    const website = recruitedArea({ channel: 'Website', recruiterName: 'SYNTHETIC ORGANISER', recruiters: RECRUITERS })
    expect(website).toMatchObject({ state: DERIVATION.NOT_APPLICABLE, text: '' })
    expect(website.note).toBe('No area is recorded for a member who joined through Website.')
  })

  it('🔴 a list that failed is SOURCE_UNAVAILABLE, not "no area on file" -- CM-15\'s shape', () => {
    const failed = recruitedArea({ channel: 'RO', recruiterName: 'SYNTHETIC ORGANISER', recruiters: [], listState: 'failed' })
    const loading = recruitedArea({ channel: 'RO', recruiterName: 'SYNTHETIC ORGANISER', recruiters: [], listState: 'loading' })

    expect(failed.state).toBe(DERIVATION.SOURCE_UNAVAILABLE)
    expect(failed.note).toBe('The recruiter list is unavailable, so the area cannot be shown yet.')
    // The Recruiter field already says "could not be loaded" for the same failure; one failed list says it once.
    expect(failed.note).not.toMatch(/could not be loaded/i)
    expect(loading.note).toBe('Waiting for the recruiter list.')
  })

  it('finds a recruiter by name the way the rest of the screen compares names', () => {
    expect(findRecruiter(RECRUITERS, '  second organiser ')?.id).toBe(3)
    expect(findRecruiter(RECRUITERS, 'NOBODY')).toBeNull()
    expect(findRecruiter(undefined, 'NOBODY')).toBeNull()
  })
})
