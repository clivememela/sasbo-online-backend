import { describe, expect, it } from 'vitest'

import { MAX_RA_NUMBERS, groupByBank, parseRaNumbers } from './raNumbers.js'

/**
 * THE ORGANISERS' BATCH PRINT READS EVERY R/A NUMBER AT ONCE (October build, 5 Oct Show & Tell: "let me put all 20
 * numbers in at once, click print"). What a person pastes is read here, before anything is asked: each number once,
 * in the order typed; the repeats and what is not a number said; more than fifty refused -- and the listings grouped
 * by bank in the order typed, so "if it's a new bank" starts a new page.
 */
describe('parseRaNumbers -- what was typed, read', () => {
  it('reads numbers separated by spaces, commas, semicolons, tabs and new lines -- a pasted column as readily as a typed line', () => {
    expect(parseRaNumbers('1204, 1388;2051\n3007\t4410  5122\r\n6001').numbers)
      .toEqual(['1204', '1388', '2051', '3007', '4410', '5122', '6001'])
  })

  it('keeps the order typed, and prints a repeated number once -- saying which were repeated', () => {
    const read = parseRaNumbers('2051 1204 2051 1388 1204 2051')

    expect(read.numbers).toEqual(['2051', '1204', '1388'])
    expect(read.repeated).toEqual(['2051', '1204'])
  })

  it('drops leading noughts: 0123 and 123 are one workplace, and count as a repeat', () => {
    const read = parseRaNumbers('0123 123')

    expect(read.numbers).toEqual(['123'])
    expect(read.repeated).toEqual(['123'])
  })

  it('says what is not an R/A number -- a word, a sign, nought, ten digits -- once each, and prints the rest', () => {
    const read = parseRaNumbers('1204 abc 1388 R1204 -5 0 1234567890 abc')

    expect(read.numbers).toEqual(['1204', '1388'])
    expect(read.notNumbers).toEqual(['abc', 'R1204', '-5', '0', '1234567890'])
  })

  it('takes fifty different numbers, and says the fifty-first is too many', () => {
    const fifty = Array.from({ length: MAX_RA_NUMBERS }, (_, i) => String(1000 + i)).join(' ')

    expect(parseRaNumbers(fifty)).toMatchObject({ tooMany: false })
    expect(parseRaNumbers(fifty).numbers).toHaveLength(50)
    expect(parseRaNumbers(`${fifty} 9999`)).toMatchObject({ tooMany: true })
  })

  it('a repeat does not count towards the fifty -- the ceiling is on what is printed', () => {
    const fifty = Array.from({ length: MAX_RA_NUMBERS }, (_, i) => String(1000 + i)).join(' ')

    expect(parseRaNumbers(`${fifty} 1000 1001`)).toMatchObject({ tooMany: false })
  })

  it('reads nothing from nothing -- an empty box, blanks, or no text at all', () => {
    expect(parseRaNumbers('')).toEqual({ numbers: [], repeated: [], notNumbers: [], tooMany: false })
    expect(parseRaNumbers('  \n , ;')).toEqual({ numbers: [], repeated: [], notNumbers: [], tooMany: false })
    expect(parseRaNumbers(undefined).numbers).toEqual([])
  })
})

describe('groupByBank -- a bank a page', () => {
  const at = (ra, employerId, employer, employerPrinted = employer) => ({
    workplace: { id: ra, raNumber: ra, name: `INVENTED BRANCH ${ra}`, employerId, employer, employerPrinted },
  })

  it('groups the branches by bank, each bank in the order its first branch was typed, its branches in the order typed', () => {
    const groups = groupByBank([at(3, 2, 'INVENTED INSURER'), at(1, 1, 'INVENTED BANK'), at(4, 2, 'INVENTED INSURER'),
      at(2, 1, 'INVENTED BANK')])

    expect(groups.map((group) => group.bank)).toEqual(['INVENTED INSURER', 'INVENTED BANK'])
    expect(groups[0].listings.map((listing) => listing.workplace.raNumber)).toEqual([3, 4])
    expect(groups[1].listings.map((listing) => listing.workplace.raNumber)).toEqual([1, 2])
  })

  it('a branch whose employer the files do not prove is grouped under the employer its branch list prints -- never dropped', () => {
    const groups = groupByBank([at(5, null, null, 'INVENTED UNKNOWN'), at(6, null, null, 'INVENTED UNKNOWN'),
      at(7, null, null, 'INVENTED OTHER')])

    expect(groups.map((group) => [group.bank, group.listings.length])).toEqual([['INVENTED UNKNOWN', 2],
      ['INVENTED OTHER', 1]])
  })

  it('nothing to group is no group', () => {
    expect(groupByBank([])).toEqual([])
    expect(groupByBank(undefined)).toEqual([])
  })
})
