/**
 * The month a month-end journey names: the month of a date it was GIVEN -- never the month a clock says.
 *
 * 🔴 WHY A CLOCK CANNOT SAY IT. A member is joined on the day the capturing browser gives them, and
 * the suite's browser keeps UTC; the server counts who joined by the month that date falls in, and
 * names its own month in Johannesburg. On the last evening of a month the two are a day apart: at
 * 22:30 UTC on 30 September a browser captures a member joined on 30 September, while in
 * Johannesburg it is already 1 October. A journey that asked any clock "which month is it?" would read
 * October's figure for a September member and see it unmoved -- a red that comes once a month, at
 * night, and looks like flake.
 *
 * So a journey reads the date the member was actually given -- from the record the server holds --
 * and names that date's month. This function is the only way it does, and it reads no clock at all:
 * `tests/harness/journeyMonth.spec.js` replaces the clock with one that throws, on the last evening
 * of a month, and asks it.
 *
 * @param {string} date a date as the server writes it -- `2026-09-30`
 * @returns {string} its month, as the server names months -- `2026-09`
 */
export function monthOfDate(date) {
  const match = /^(\d{4})-(\d{2})-(\d{2})$/.exec(date ?? '')
  if (!match) {
    throw new Error(`A journey's month is named from a date the server wrote (yyyy-mm-dd), and this is not one: ${date}`)
  }
  return `${match[1]}-${match[2]}`
}

/**
 * The month before a month the server named -- `2026-01` is preceded by `2025-12`. Arithmetic on the month given,
 * never a clock's.
 */
export function monthBefore(month) {
  const match = /^(\d{4})-(\d{2})$/.exec(month ?? '')
  if (!match) throw new Error(`Not a month the server named (yyyy-mm): ${month}`)
  const [year, number] = [Number(match[1]), Number(match[2])]
  return number === 1 ? `${year - 1}-12` : `${year}-${String(number - 1).padStart(2, '0')}`
}

/**
 * A date as the server wrote it, made `yyyy-mm-dd`. The member domain's older answers write a date as an array --
 * `[2026, 9, 30]`, register row A20 -- and month end's write ISO text; both are the same day.
 *
 * @param {string|number[]} value
 */
export function dateAsWritten(value) {
  if (Array.isArray(value) && value.length >= 3 && value.slice(0, 3).every(Number.isInteger)) {
    const [year, month, day] = value
    return `${year}-${String(month).padStart(2, '0')}-${String(day).padStart(2, '0')}`
  }
  if (typeof value === 'string' && /^\d{4}-\d{2}-\d{2}/.test(value)) {
    return value.slice(0, 10)
  }
  throw new Error(`Not a date the server wrote: ${JSON.stringify(value)}`)
}

const SAID = new Intl.DateTimeFormat('en-GB', {
  timeZone: 'Africa/Johannesburg', day: 'numeric', month: 'long', year: 'numeric', hour: '2-digit', minute: '2-digit',
  hourCycle: 'h23',
})

/**
 * An instant the server wrote, as its sentences say one: `2 October 2026 at 12:03`, in Johannesburg -- the instant
 * GIVEN, never a clock's. The server writes it to the microsecond; a date reads to the millisecond, and the minute is
 * the same.
 *
 * @param {string} instant an instant as the server writes it -- `2026-10-02T10:03:56.324328Z`
 */
export function said(instant) {
  const parts = SAID.formatToParts(new Date(String(instant).replace(/(\.\d{3})\d+/, '$1')))
  const part = Object.fromEntries(parts.map(({ type, value }) => [type, value]))
  return `${part.day} ${part.month} ${part.year} at ${part.hour}:${part.minute}`
}

/**
 * The files of month end's journeys, which read no clock (MD55). A CLOSED SET, pinned exactly: a file
 * joins it when a month-end journey is written, and the harness reads every one of them.
 */
export const MONTH_END_JOURNEY_FILES = [
  'api/MemberStatusApi.js',
  'api/MonthEndApi.js',
  'api/WorkplacesApi.js',
  'pages/MemberRecordPage.js',
  'pages/MonthEndFiguresPage.js',
  'pages/MonthEndPage.js',
  'pages/Navigation.js',
  'pages/WorkplacesPage.js',
  'steps/memberStatus.steps.js',
  'steps/monthEnd.steps.js',
  'steps/monthEndFigures.steps.js',
  'steps/workplaces.steps.js',
  'support/journeyMonth.js',
  'support/monthEndMember.js',
]
