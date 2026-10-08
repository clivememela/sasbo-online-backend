import { describe, expect, it } from 'vitest'

import { bankDebitDay, findSubscriptionForBank } from './bankSubscription'

/**
 * THE SUBSCRIPTION LOOKUP MUST AGREE WITH THE SERVER'S, EXACTLY.
 *
 * `MemberServiceImpl.resolveSubscriptionByBankName` does an exact,
 * case-insensitive match on the bank's name. If this one is looser, the screen
 * shows one bank's subscription and the save records another's — a form that is
 * nearly right, which is the most expensive kind of wrong because nobody checks
 * it. `bankBranches.js` carries the same warning about the same temptation, and
 * that one was written after the fuzzy version had already shipped.
 */

const ROWS = [
  { bankName: 'Absa Bank Limited', acbDebitDay: 5, subscriptionAmount: 120.0 },
  { bankName: 'Nedbank Limited', acbDebitDay: 20, subscriptionAmount: 99.99 },
]

describe('findSubscriptionForBank matches the way the server matches', () => {
  it('finds the row for an exact name', () => {
    expect(findSubscriptionForBank(ROWS, 'Nedbank Limited')?.acbDebitDay).toBe(20)
  })

  it('ignores case and surrounding space, as findByNameIgnoreCase does', () => {
    expect(findSubscriptionForBank(ROWS, '  nedbank limited ')?.acbDebitDay).toBe(20)
  })

  it('🔴 does NOT match on a prefix or a substring', () => {
    // The whole point. A fuzzy match would answer Absa's row for "Absa", and the
    // server -- which does not -- would then save something else.
    expect(findSubscriptionForBank(ROWS, 'Nedbank')).toBeNull()
    expect(findSubscriptionForBank(ROWS, 'Absa')).toBeNull()
  })

  it('answers null rather than undefined, so "looked and found nothing" is sayable', () => {
    // derivation.js turns on this distinction: null is NO_MATCH, and never having
    // looked is NOT_APPLICABLE. They produce different words on screen.
    expect(findSubscriptionForBank(ROWS, 'Bank Of Nowhere')).toBeNull()
    expect(findSubscriptionForBank(ROWS, '')).toBeNull()
    expect(findSubscriptionForBank(null, 'Nedbank Limited')).toBeNull()
  })
})


describe('bankDebitDay: the bank\'s day as a member\'s default (A67)', () => {
  const DAYS = [
    { bankName: 'Absa Bank Limited', acbDebitDay: 5 },
    { bankName: 'Nedbank Limited', acbDebitDay: 20 },
    { bankName: 'TymeBank Limited', acbDebitDay: 28 },
  ]

  it('answers a usable day as the option string the form holds', () => {
    expect(bankDebitDay(DAYS, 'Nedbank Limited')).toBe('20')
    expect(bankDebitDay(DAYS, ' tymebank limited ')).toBe('28')
  })

  it('answers null for a day outside the range, as the server declines one', () => {
    // Absa's seeded 5th is placeholder data (the owner, 2026-09-09); the server gives no member a day it would refuse.
    expect(bankDebitDay(DAYS, 'Absa Bank Limited')).toBeNull()
  })

  it('answers null for no bank, no row, or no rows at all', () => {
    expect(bankDebitDay(DAYS, '')).toBeNull()
    expect(bankDebitDay(DAYS, 'Bank Of Nowhere')).toBeNull()
    expect(bankDebitDay(undefined, 'Nedbank Limited')).toBeNull()
  })
})
