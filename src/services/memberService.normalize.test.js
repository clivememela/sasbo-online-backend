import { describe, expect, it } from 'vitest'

import { normalizeMember } from './memberService'

/**
 * A37 / A38 at the source: a list field arrives as a list, whatever shape the server sent.
 * The screen-level half -- a member whose positions are a delimited string opens without
 * throwing -- is `Members.cosatu.test.jsx`.
 */
describe('normalizeMember', () => {
  it('unfolds the server\'s delimited string into a list', () => {
    expect(normalizeMember({ cosatuPositions: 'PEC,CEC', becStructures: ' A , B ' })).toMatchObject({
      cosatuPositions: ['PEC', 'CEC'],
      becStructures: ['A', 'B'],
    })
  })

  it('leaves a list as it is, and makes an absent or empty field an empty list', () => {
    expect(normalizeMember({ cosatuPositions: ['PEC'] }).cosatuPositions).toEqual(['PEC'])
    expect(normalizeMember({ cosatuPositions: '' }).cosatuPositions).toEqual([])
    expect(normalizeMember({}).becStructures).toEqual([])
  })

  it('does not touch any other field, and does not mutate what it was given', () => {
    const raw = { id: 7, surname: 'Changer', isCosatu: 'Y', cosatuPositions: 'PEC' }
    const out = normalizeMember(raw)
    expect(out).toMatchObject({ id: 7, surname: 'Changer', isCosatu: 'Y' })
    expect(raw.cosatuPositions).toBe('PEC')
  })

  it('passes a non-member straight through -- an empty 200, a list, nothing', () => {
    expect(normalizeMember(null)).toBeNull()
    expect(normalizeMember(undefined)).toBeUndefined()
    expect(normalizeMember('')).toBe('')
  })
})
