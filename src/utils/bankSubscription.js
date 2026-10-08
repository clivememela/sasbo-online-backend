/**
 * The subscription row for a bank — the SAME lookup the server performs.
 *
 * <h2>Why this mirrors the server rather than being convenient</h2>
 * `MemberServiceImpl.resolveSubscriptionByBankName` does:
 *
 *     bankRepository.findByNameIgnoreCase(bankName)
 *         .flatMap(bank -> subscriptionsRepository.findByBankIdWithBank(bank.getId()))
 *
 * — an EXACT, case-insensitive match on the bank's name. So this does the same:
 * exact, case-insensitive, trimmed, on `bankName`.
 *
 * 🔑 It is deliberately NOT the fuzzy `includes()` match this screen uses to
 * resolve a bank CODE for the job-grade fetch. `bankBranches.js` records what that
 * difference costs: a fuzzy match here would show the user one bank's subscription
 * and the server would then save a different one — a screen that lies in the most
 * expensive way, by being nearly right. If the two rules ever have to diverge, the
 * divergence belongs in one place with a reason, not in two lookups that quietly
 * disagree.
 */

import { nameKey } from './nameKey.js'
import { isDebitDayInRange } from '../config/memberConstants.js'

/** The bank name a subscription row carries, under either spelling. */
export const subscriptionBankNameOf = (row) =>
  String(row?.bankName ?? row?.bank?.name ?? '').trim()

/**
 * The subscription row for `bankName`, or `null`.
 *
 * `null` rather than `undefined` so a caller can tell "looked and found nothing"
 * from "never looked" — which is the whole subject of `derivation.js`.
 */
export const findSubscriptionForBank = (subscriptions, bankName) => {
  const wanted = nameKey(bankName)
  if (!wanted) return null

  return (Array.isArray(subscriptions) ? subscriptions : []).find(
    (row) => nameKey(subscriptionBankNameOf(row)) === wanted,
  ) ?? null
}

/**
 * The bank's debit day as a member's DEFAULT, as the option string the form holds, or `null` (A67).
 *
 * The server gives a member the bank's ACB day only when they have no day of their own, and only when the day is
 * inside the permitted range -- the same two conditions as here, so the day a form shows before anybody chooses one
 * is the day the bank would have given. `null` for no bank, no row, or a day outside the range (the seeded rows'
 * placeholder days, Absa's 5th among them).
 */
export const bankDebitDay = (subscriptions, bankName) => {
  const day = findSubscriptionForBank(subscriptions, bankName)?.acbDebitDay
  return isDebitDayInRange(day) ? String(Number(day)) : null
}

/**
 * SASBO's own monthly subscription — the ONE amount, when there is one.
 *
 * 🗣️ The subscription is the UNION's membership fee, not a per-bank price: it is
 * "set by Subscription Maintenance and reviewed annually through business
 * discussions", once, for everybody. The reference table stores a row per bank
 * because the ACB debit DAY genuinely differs between banks — the amount does not.
 *
 * 🔴 Why this exists. The capture form derived the amount from the chosen bank
 * alone, so the field sat EMPTY until somebody picked one, reading "choose a bank
 * and its subscription appears here" — on a screen whose section is headed *Sasbo
 * Subscription*. The union's own fee is knowable before any bank is chosen, and a
 * blank box on a read-only field is indistinguishable from a member who pays
 * nothing.
 *
 * @returns the single amount every row agrees on, or `null` when the rows
 *          disagree (then it really is bank-specific and only the bank can say)
 *          or when there are no rows at all.
 */
export const sasboSubscriptionAmount = (subscriptions) => {
  const rows = Array.isArray(subscriptions) ? subscriptions : []
  if (rows.length === 0) return null

  const amounts = new Set(
    rows
      .map((row) => row?.subscriptionAmount)
      .filter((a) => a !== null && a !== undefined && a !== '')
      .map((a) => Number(a))
      .filter((a) => Number.isFinite(a)),
  )
  return amounts.size === 1 ? [...amounts][0] : null
}

