import { describe, expect, it } from 'vitest'

import { countArchive, filterArchive } from './archiveTree.js'

/**
 * The search both deep archives ran as private copies (RF). Every way a branch survives, beside the way it is dropped,
 * so a filter that kept everything -- or matched only files -- fails a named row.
 */
const TREE = {
  'Absa Bank': ['Absa-Agreement-2014.pdf', 'Absa-Wage-2015.pdf'],
  Nedbank: {
    Agreements: ['Ned-SalAgreement-2014.pdf', 'Ned-Recognition.pdf'],
    Minutes: ['Ned-Minutes-2016.doc'],
  },
  'Standard Bank': {
    Correspondence: ['SBSA-Letter.doc'],
  },
}

describe('filterArchive', () => {
  it('a blank query is the whole tree, the same object', () => {
    expect(filterArchive(TREE, '   ')).toBe(TREE)
  })

  it('a matching top-level NAME keeps its whole branch, at either depth', () => {
    expect(filterArchive(TREE, 'nedbank')).toEqual({ Nedbank: TREE.Nedbank })
    expect(filterArchive(TREE, 'ABSA BANK')).toEqual({ 'Absa Bank': TREE['Absa Bank'] })
  })

  it('a matching middle name keeps that whole list, and only that one', () => {
    expect(filterArchive(TREE, 'minutes')).toEqual({ Nedbank: { Minutes: ['Ned-Minutes-2016.doc'] } })
  })

  it('otherwise only the matching FILES are kept, in a flat branch and a nested one', () => {
    expect(filterArchive(TREE, '2014')).toEqual({
      'Absa Bank': ['Absa-Agreement-2014.pdf'],
      Nedbank: { Agreements: ['Ned-SalAgreement-2014.pdf'] },
    })
  })

  it('the must-pass: a branch with nothing matching is dropped', () => {
    expect(filterArchive(TREE, 'no such document')).toEqual({})
  })
})

describe('countArchive', () => {
  it('counts files at both depths', () => {
    expect(countArchive(TREE)).toBe(6)
  })
})
