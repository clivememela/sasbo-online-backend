/**
 * WHEN THE MEMBER WILL ACTUALLY BE DEBITED — 04.3.
 *
 * <h2>Why this is the one genuinely new thing on the screen</h2>
 * Every other derivation in P4 is plumbing: a value that exists somewhere else,
 * shown where it is needed. This one computes something nobody has written down.
 * *"The 31st falls on a Sunday, so this member is debited on Friday the 29th"* is
 * the only sentence this form can tell an administrator that they did not already
 * know, and the rule behind it exists nowhere in either repository.
 *
 * <h2>The rule (A57, as SASBO answered question 33 on 2026-09-21)</h2>
 * A debit day that lands on a non-business day moves <b>FORWARD</b> to the next business day in the same month
 * -- "If a debit date is on the 25th and it is a Saturday it will move to the following business day which is
 * the 27". <b>Only when forward would leave the month</b> does it move backward instead -- "We never go into the
 * next month. If the debit date is on the 30 or 31st and that fall on a weekend it moved back to the available
 * business day." So the date never falls below the 20th and never leaves its month. Their July 2026 bureau file
 * did exactly this: nobody on Friday the 24th, 365 members on Monday the 27th. The server's DebitWindow runs the
 * same rule, and each is held to one generated fixture, `src/test/debit-window.json` -- never to the other.
 *
 * 🔴 History, because it went wrong three times. The first rule moved backward ALWAYS, which put a Saturday 20th
 * on Friday the 19th, outside the window. The first correction moved every weekend forward, backward only at
 * month end -- the rule above. It was then turned round to the signed specification's reading (18 August session
 * and A.7 glossary: backward first, forward only below the 20th), and SASBO overruled their own specification
 * with the answer quoted above. The two readings agree on all three of the spec's examples and differ on 56 of
 * 288 cells in 2026-2027 (day 25 in July 2026: Monday the 27th, not Friday the 24th); the tests pin a mid-window
 * weekend. 🟡 One seam is SASBO's to settle (question 47): a Sunday 30th with Monday the 31st free goes forward
 * here, and back if their sentence is read literally -- first live on 30 May 2027.
 *
 * <h2>🔴 IT MUST ITERATE, NOT STEP ONCE</h2>
 * Under the Public Holidays Act 36 of 1994, a public holiday falling on a Sunday
 * makes the following Monday a public holiday too. So a single step can land on
 * another non-business day. 2026 has exactly this shape — Women's Day is Sunday
 * 9 August, so Monday 10 August is also a holiday. A one-step implementation is
 * correct for most of the year and wrong in the week somebody checks.
 *
 * <h2>THE HOLIDAYS ARE THE RUN'S OWN CALENDAR, AND WITHOUT IT THE SENTENCE SAYS SO</h2>
 * The run moves a debit off public holidays as well as weekends, from the table
 * ACB's `01.8` made (D30), and `GET /api/acb/holidays` serves it: the screen passes
 * that set in (`holidaySetFor`), so the sentence and the run give one date. A
 * hardcoded table is still ruled out — the set changes, and 2026 carries a one-off
 * local-government-election holiday that no table written last year contains. So
 * when the calendar cannot be read, or does not reach the month, this computes
 * weekends only, `holidaysKnown` is `false`, and the sentence says so on EVERY date.
 * 🔴 It used to say so only on a MOVED date, which presented the one case the
 * caveat exists for as certain: an unmoved weekday that is a public holiday. Day 24
 * read "Thursday 24 September" in Heritage Day's week while the run debited Friday
 * the 25th (the presentation arc found it, 2026-09-21).
 */

import { toWireDate } from '../lib/format.js'

const isWeekend = (date) => date.getDay() === 0 || date.getDay() === 6

/** `YYYY-MM-DD` from the LOCAL calendar fields -- `lib/format.js` owns the rule (CM-38). */
const isoOf = (date) => toWireDate({ y: date.getFullYear(), m: date.getMonth() + 1, d: date.getDate() })

/**
 * The last business day on or before `date`.
 *
 * Iterates. `holidays` is a set of `YYYY-MM-DD` strings; an empty one means
 * weekends only, which is the state this application is actually in.
 */
export const lastBusinessDayOnOrBefore = (date, holidays = new Set()) => {
  const cursor = new Date(date.getFullYear(), date.getMonth(), date.getDate())
  // Bounded, so a caller who passes a set containing every day cannot hang the
  // browser. Fourteen is far past any real run of non-business days; reaching it
  // means the input was wrong, and stopping is better than spinning.
  for (let step = 0; step < 14; step += 1) {
    if (!isWeekend(cursor) && !holidays.has(isoOf(cursor))) return cursor
    cursor.setDate(cursor.getDate() - 1)
  }
  return cursor
}

/** The first business day on or after `date`. Iterates, bounded, like its twin above. */
export const nextBusinessDayOnOrAfter = (date, holidays = new Set()) => {
  const cursor = new Date(date.getFullYear(), date.getMonth(), date.getDate())
  for (let step = 0; step < 14; step += 1) {
    if (!isWeekend(cursor) && !holidays.has(isoOf(cursor))) return cursor
    cursor.setDate(cursor.getDate() + 1)
  }
  return cursor
}

/** The last day of a month, so a 31st in February clamps rather than overflowing. */
const lastDayOfMonth = (year, month) => new Date(year, month + 1, 0).getDate()

/**
 * The date a member whose debit day is `day` will actually be debited, in the
 * month containing `reference`.
 *
 * Returns the shape the screen renders:
 *
 *   { iso, day, clamped, adjusted, holidaysKnown }
 *
 * `clamped` is a SHORT MONTH — the 31st in February. `adjusted` is the
 * business-day move. They are reported separately because they have different
 * explanations and a member can hit both at once: the 31st of February 2027 is
 * the 28th, which is a Sunday, so the answer is Friday the 26th.
 */
export const actualDebitDate = (day, reference = new Date(), holidays = new Set()) => {
  const requested = Number(day)
  if (!Number.isInteger(requested) || requested < 1 || requested > 31) return null

  const year = reference.getFullYear()
  const month = reference.getMonth()
  const monthEnd = lastDayOfMonth(year, month)
  const clampedDay = Math.min(requested, monthEnd)

  const target = new Date(year, month, clampedDay)
  // Forward, as SASBO answered; backward only where forward would leave the month. A57 / Q33 -- see the rule above.
  const forward = nextBusinessDayOnOrAfter(target, holidays)
  const actual = forward.getMonth() === month
    ? forward
    : lastBusinessDayOnOrBefore(target, holidays)

  return {
    iso: isoOf(actual),
    day: actual.getDate(),
    clamped: clampedDay !== requested,
    adjusted: actual.getDate() !== clampedDay,
    // Honest by construction: unless the caller supplies the calendar, this answer
    // accounts for weekends only.
    holidaysKnown: holidays.size > 0,
  }
}

/**
 * The set the functions above take, from `GET /api/acb/holidays` (D30): the year's
 * dates when the calendar reaches the end of `reference`'s month, and EMPTY otherwise,
 * so a month the run itself refuses (`HOLIDAYS_NOT_KNOWN`) is never presented as known.
 * Anything that is not a calendar -- no answer, an empty list -- is empty too.
 */
export const holidaySetFor = (calendar, reference = new Date()) => {
  const monthEnd = isoOf(new Date(reference.getFullYear(), reference.getMonth() + 1, 0))
  const reaches = typeof calendar?.knownUntil === 'string' && calendar.knownUntil >= monthEnd
  if (!reaches || !Array.isArray(calendar.holidays)) return new Set()
  return new Set(calendar.holidays.map((holiday) => holiday.date))
}

const WEEKDAYS = ['Sunday', 'Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday']
const MONTHS = ['January', 'February', 'March', 'April', 'May', 'June',
  'July', 'August', 'September', 'October', 'November', 'December']

/**
 * The sentence the screen shows — the whole point of the unit.
 *
 * Says the DATE, and says WHY it is not the day the user picked. A date with no
 * explanation invites "that is wrong, I chose the 31st"; the explanation is what
 * turns it from a surprise into information.
 */
export const debitDateSentence = (day, reference = new Date(), holidays = new Set()) => {
  const result = actualDebitDate(day, reference, holidays)
  if (!result) return null

  const date = new Date(`${result.iso}T00:00:00`)
  const written = `${WEEKDAYS[date.getDay()]} ${result.day} ${MONTHS[date.getMonth()]}`

  // On every date, moved or not: without the calendar an unmoved weekday can still be a public holiday.
  const caveat = result.holidaysKnown
    ? ''
    : ' Weekends only — public holidays are not accounted for.'

  if (!result.clamped && !result.adjusted) {
    return `This member will be debited on ${written}.${caveat}`
  }

  const because = []
  if (result.clamped) because.push(`${MONTHS[date.getMonth()]} has no ${ordinal(Number(day))}`)
  if (result.adjusted) because.push('that day is not a business day')

  // A sentence starts with a capital whichever reason leads it -- "that day is not a
  // business day" leads when the day was only moved.
  const reason = because.join(', and ')
  return `${reason[0].toUpperCase()}${reason.slice(1)}, so this member will be debited on ${written}.${caveat}`
}

const ordinal = (n) => {
  const suffix = n % 100 >= 11 && n % 100 <= 13
    ? 'th'
    : ['th', 'st', 'nd', 'rd'][n % 10] ?? 'th'
  return `${n}${suffix}`
}
