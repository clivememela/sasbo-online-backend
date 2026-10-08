import { describe, expect, it } from 'vitest'

import { answerOf, committeeOptions, flagOf, inOrder, structureErrors, withStructureAnswer } from './structures'
import { BEC_STRUCTURES_REPRESENTED, COSATU_POSITIONS } from '../config/memberConstants'

/**
 * The Structures tab's rules (`utils/structures.js`) -- the half the browser holds of what the server's
 * `MemberValidator.validateSeats` / `validateBecRoles` and `MemberServiceImpl.applySeats` hold on save. Each rule is
 * pinned with its must-pass twin: a guard that refused everything, or cleared everything, would pass the red half.
 */

/** A BEC member on the whole tree, as the record holds one: lists unfolded, a stored date as [y, m, d]. */
const SEATED = {
  isShopSteward: true,
  shopStewardElectionDate: [2025, 3, 14],
  isBec: true,
  becCommitteeCode: 'G01',
  becCommitteeName: 'JOHANNESBURG',
  isBecRecruiter: true,
  isBecStructureRep: true,
  becStructures: ['INC / IC'],
  isBecExecutive: true,
  becExecutivePosition: 'Treasurer',
  isCosatu: 'Y',
  isCosatuLocal: true,
  isCosatuRegional: true,
  cosatuPositions: ['PEC'],
}

const TODAY = { y: 2026, m: 9, d: 30 }

describe('withStructureAnswer -- a No takes with it what its Yes needed', () => {
  it('Shop Steward = No clears the election date, to null -- an empty string is not a date on the wire', () => {
    expect(withStructureAnswer(SEATED, 'isShopSteward', false).shopStewardElectionDate).toBeNull()
  })

  it('BEC = No clears the committee, every BEC role, and COSATU -- which needs BEC', () => {
    expect(withStructureAnswer(SEATED, 'isBec', false)).toMatchObject({
      isBec: false,
      becCommitteeCode: '',
      becCommitteeName: '',
      isBecRecruiter: false,
      isBecStructureRep: false,
      becStructures: [],
      isBecExecutive: false,
      becExecutivePosition: '',
      isCosatu: 'N',
      isCosatuLocal: false,
      isCosatuRegional: false,
      cosatuPositions: [],
    })
  })

  it('a role answered No clears only its own list; COSATU = No clears only COSATU\'s', () => {
    expect(withStructureAnswer(SEATED, 'isBecStructureRep', false)).toMatchObject({ becStructures: [], becExecutivePosition: 'Treasurer' })
    expect(withStructureAnswer(SEATED, 'isBecExecutive', false)).toMatchObject({ becExecutivePosition: '', becStructures: ['INC / IC'] })
    expect(withStructureAnswer(SEATED, 'isCosatu', 'N')).toMatchObject({
      isCosatuLocal: false, isCosatuRegional: false, cosatuPositions: [], becCommitteeCode: 'G01',
    })
  })

  it('must-pass: a Yes, or any other answer, clears nothing', () => {
    // The twin of every case above: a helper that cleared on any change would pass them.
    for (const [field, value] of [['isShopSteward', true], ['isBec', true], ['isBecStructureRep', true],
      ['isBecExecutive', true], ['isCosatu', 'Y'], ['isBecRecruiter', false], ['becExecutivePosition', 'Secretary']]) {
      expect(withStructureAnswer(SEATED, field, value), field).toEqual({ ...SEATED, [field]: value })
    }
  })

  it('leaves the answers it was given untouched', () => {
    const before = structuredClone(SEATED)
    withStructureAnswer(SEATED, 'isBec', false)
    expect(SEATED).toEqual(before)
  })
})

describe('structureErrors -- what each Yes needs before it can be saved', () => {
  it('🔴 a new Yes needs its follow-up: the date, the committee, the structures, the position', () => {
    const asked = { isShopSteward: true, isBec: true, isBecStructureRep: true, becStructures: [], isBecExecutive: true }
    expect(structureErrors(asked, { isShopSteward: false, isBec: false }, TODAY)).toEqual({
      shopStewardElectionDate: 'Enter the date the shop steward was elected',
      becCommitteeCode: 'Choose the Branch Executive Committee this member sits on',
      becStructures: 'Choose the structures this member represents',
      becExecutivePosition: 'Choose the position this member holds',
    })
  })

  it('must-pass: the whole tree answered asks for nothing', () => {
    expect(structureErrors(SEATED, SEATED, TODAY)).toEqual({})
  })

  it('an election date after today is refused, however it arrived', () => {
    expect(structureErrors({ isShopSteward: true, shopStewardElectionDate: '2026-10-01' }, null, TODAY))
      .toEqual({ shopStewardElectionDate: 'The election date cannot be in the future' })
    // The twin: today itself is not the future, and a stored [y, m, d] is read as the day it is.
    expect(structureErrors({ isShopSteward: true, shopStewardElectionDate: '2026-09-30' }, null, TODAY)).toEqual({})
    expect(structureErrors({ isShopSteward: true, shopStewardElectionDate: [2026, 9, 30] }, null, TODAY)).toEqual({})
  })

  it('🔑 a Yes stored before V55, with nothing beside it, stays saveable', () => {
    const legacy = { isShopSteward: true, isBec: true }
    expect(structureErrors(legacy, legacy, TODAY)).toEqual({})
  })

  it('🔴 but a follow-up once recorded can be changed, never removed while the answer stays Yes', () => {
    const removed = { ...SEATED, shopStewardElectionDate: null, becCommitteeCode: '' }
    expect(Object.keys(structureErrors(removed, SEATED, TODAY))).toEqual(['shopStewardElectionDate', 'becCommitteeCode'])
  })

  it('must-pass: the roles are asked only of a BEC member', () => {
    // The server clears a role beneath a No; so the screen does not refuse one.
    expect(structureErrors({ isBec: false, isBecStructureRep: true, isBecExecutive: true }, null, TODAY)).toEqual({})
  })
})

describe('committeeOptions -- SASBO\'s committees, as the BEC dropdown offers them', () => {
  it('alphabetical, by name, with the code as the value -- and UNATTACHED left out', () => {
    const answer = {
      rows: [
        { code: 'G02', name: 'PRETORIA' },
        { code: 'G99', name: ' unattached ' },
        { code: 'G01', name: 'JOHANNESBURG' },
      ],
    }
    expect(committeeOptions(answer)).toEqual([
      { value: 'G01', label: 'JOHANNESBURG' },
      { value: 'G02', label: 'PRETORIA' },
    ])
  })

  it('an answer that is not a list\'s -- the shared fixture\'s [], a failure page -- is no committees, not a throw', () => {
    expect(committeeOptions([])).toEqual([])
    expect(committeeOptions(null)).toEqual([])
    expect(committeeOptions({ rows: [{ code: 'G01' }, { name: 'NO CODE' }] })).toEqual([])
  })
})

describe('inOrder -- ticked boxes in the list\'s own order', () => {
  it('is the vocabulary\'s order, whatever order the boxes were ticked in', () => {
    expect(inOrder(['MANCOM', 'INC / IC'], BEC_STRUCTURES_REPRESENTED)).toEqual(['INC / IC', 'MANCOM'])
  })

  it('keeps a value the list does not offer, after the rest -- the old record\'s free text', () => {
    expect(inOrder(['COSATU Local', 'Treasurer', 'PEC'], COSATU_POSITIONS)).toEqual(['PEC', 'Treasurer', 'COSATU Local'])
  })
})

describe('answerOf / flagOf -- a stored yes/no and the answer on screen', () => {
  it('never answered is its own answer, not No', () => {
    expect([answerOf(true), answerOf(false), answerOf(null)]).toEqual(['Yes', 'No', ''])
    expect([flagOf('Yes'), flagOf('No'), flagOf('')]).toEqual([true, false, null])
  })
})
