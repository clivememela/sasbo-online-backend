/**
 * Two independent guards standing between this suite and real data.
 *
 * WHY TWO, FROM ONE MODULE
 * Anything this suite deletes, it must first prove it created. One check is
 * enough right up until the moment it is wrong. So there are two, and they are
 * INDEPENDENT -- a positive rule (the name matches the exact shape this suite
 * mints) and a negative one (the name matches nothing on an explicit
 * never-touch list). A bug in either is caught by the other.
 *
 * They live in ONE module, used by both the creator and the cleaner, because
 * the failure being prevented is DRIFT: a cleaner that recognises test data
 * slightly differently from the creator will one day either leak rows forever
 * or delete something real. Two implementations of "is this ours?" is the bug.
 *
 * WHY THIS MATTERS MORE HERE THAN IT USUALLY WOULD
 * The database this suite runs against is a members register, and the local one
 * is currently irreplaceable -- there is no restore path if a cleanup routine
 * gets it wrong. The cost of the guard is a few microseconds per row. The cost
 * of not having it is unbounded and permanent.
 */
import { randomBytes } from 'node:crypto'

/**
 * The marker every artefact this suite creates carries.
 *
 * `ZZ-` so it sorts to the end of any alphabetical list and is unmistakable to
 * a human reading the data. `AT` for automation testing. If you see this in a
 * production database, a suite was pointed at the wrong target -- which is
 * itself worth being able to spot at a glance.
 */
export const TEST_PREFIX = 'ZZ-AT'

/**
 * The exact shape of a name this suite mints. Guard one, the positive rule.
 *
 * 🔴 THE TAIL IS LETTERS, NOT HEX, AND THE REASON IS THE APPLICATION'S. Client main
 * `4b64c1a` sanitises `firstNames` and `surname` through `inputRules.sanitiseField`
 * (`NAME_DISALLOWED = /[^\p{L}\s'’.-]/gu`), which strips every DIGIT as it is typed --
 * silently. A hex tail therefore went in as `ZZ-AT-member-078556c3c26b` and was SAVED as
 * `ZZ-AT-member-ccb`: a different member from the one the scenario thought it had created,
 * so the round-trip lookup missed and the cleanup could not find its own row either.
 * Measured 2026-09-18 from the p11 run's page snapshot, which showed both strings.
 *
 * 🔴 AND THE ALPHABET MUST NOT BE ABLE TO SPELL GUARD TWO. The first letters tail was
 * `a`-`p`, which contains `f`, `n` and `b` -- so a random tail could spell `fnb`, and p16
 * minted `ZZ-AT-b-edfnbiobkmki`, which guard two then refused to delete, correctly. Hex
 * could never do that (no `n`). Roughly 0.2 % of names, so it surfaced as a rare red,
 * and on a journey it would have left a row the suite may not clean up. `f` and `n` are
 * gone (`q` and `r` replace them), so every NEVER_TOUCH entry holds a letter no tail can
 * produce -- and `guards.spec.js` proves that for the whole list, not for this one word.
 */
export const ALPHABET = 'abcdeghijklmopqr'
const MINTED_NAME = new RegExp(`^ZZ-AT-[A-Za-z0-9]{1,24}-[${ALPHABET}]{12}$`)

/**
 * Guard two, the negative rule: substrings that mean "this is real", checked
 * case-insensitively and independently of the pattern above.
 *
 * These are the seeded institutions the reference data actually contains. The
 * list is not a completeness claim and does not need to be one -- it is a
 * second opinion. Its job is to disagree with guard one when guard one is
 * wrong, and any overlap between a minted name and a real one is a collision
 * worth failing on rather than reasoning about.
 */
export const NEVER_TOUCH = [
  'absa',
  'capitec',
  'discovery',
  'fnb',
  'first national',
  'investec',
  'nedbank',
  'sasbo',
  'standard bank',
  'tymebank',
]

/**
 * Mint a name for something this suite is about to create.
 *
 * The random tail is what makes scenarios safe to run in parallel workers: two
 * scenarios creating "a bank" at the same moment must not collide on a unique
 * constraint, and must not clean up each other's row.
 */
export function mintTestName(label = 'x') {
  const clean = String(label).replace(/[^A-Za-z0-9]/g, '').slice(0, 24) || 'x'
  // Six random bytes as twelve LETTERS from ALPHABET (sixteen of them, so one byte is
  // two characters), not hex: the same 48 bits, through a name field that deletes
  // digits. See MINTED_NAME above.
  const tail = Array.from(randomBytes(6), (byte) => ALPHABET[byte & 15] + ALPHABET[byte >> 4]).join('')
  return `${TEST_PREFIX}-${clean}-${tail}`
}


/**
 * A South African identity number this suite can capture WITHOUT colliding.
 *
 * 🔴 WHY THIS EXISTS. Server `V26__member_identity_number_unique.sql` (on main since
 * `829f252`) adds a partial unique index on `members.identity_number`, which is register
 * row A48's first half closing. Every capture scenario shared one hardcoded number, so
 * the second save in a run is now REFUSED -- *"This conflicts with a record that already
 * exists"* -- and the refusal is correct. The suite has to mint them, exactly as the
 * server's own `MemberFixture` had to.
 *
 * The shape is the real one, because the browser validates it: `YYMMDD` + a four-digit
 * sequence + citizenship + `8` + a Luhn check digit. The date is fixed at 1990-01-01 so a
 * scenario reading the derived date of birth still reads something stable; only the
 * sequence varies, which is the field that actually distinguishes two people born the
 * same day.
 */
export function mintIdentityNumber() {
  const sequence = String(randomBytes(2).readUInt16BE(0) % 10000).padStart(4, '0')
  const body = `900101${sequence}08`
  return body + luhnCheckDigit(body)
}

/**
 * A run-unique numeric tail, for fields that hold DIGITS rather than a name.
 *
 * 🔴 THE CELLPHONE NUMBER USED TO BORROW `mintTestName`'s tail, and that broke the day
 * the tail became letters (2026-09-18, for A62's name sanitiser): `phone` strips
 * everything but digits and punctuation, so `+27 82 <six letters>` reached the field as
 * four digits and the register refused the save -- "This number looks too short --
 * include the area or network code" (validateField, `digitCount < 9`). Correctly: the
 * number really was too short. A digits field needs a digits mint.
 */
export function mintNumericTail(length = 6) {
  const digits = Array.from(randomBytes(length), (byte) => String(byte % 10)).join('')
  return digits.slice(0, length)
}

/**
 * A recipient address this suite can capture, and then find that member's letter by.
 *
 * 🔴 WHY THIS EXISTS. Since client 8c6b675 and server 99936a5, capturing a member SENDS
 * their welcome letter (register row A64), and the stack's mail catcher keeps every letter
 * any scenario sent. A scenario asserting on ITS letter needs an address no other scenario
 * used, or two workers read each other's mail -- so it is minted, like the name.
 *
 * 🔑 `example.invalid` IS RESERVED NEVER TO RESOLVE (RFC 2606), so even a stack pointed at
 * a real mail server by mistake could not deliver it. The local part is a minted name,
 * lower-cased: it keeps the prefix a reader can spot, and the capture form's email check
 * already accepts this domain (`zola@example.invalid` has saved since A30 closed).
 */
export function mintEmailAddress(label = 'mail') {
  return `${mintTestName(label).toLowerCase()}@example.invalid`
}

/** The Luhn digit the browser's own validator computes (`validateSAID`). */
function luhnCheckDigit(body) {
  let sum = 0
  // Right to left over the body; every second digit doubles, and a double over 9
  // has its digits added -- which is the same as subtracting 9.
  for (let i = body.length - 1, double = true; i >= 0; i--, double = !double) {
    let digit = Number(body[i])
    if (double) {
      digit *= 2
      if (digit > 9) digit -= 9
    }
    sum += digit
  }
  return String((10 - (sum % 10)) % 10)
}

/** Guard one, in isolation. Exported so the self-tests can prove it alone. */
export function matchesMintedShape(name) {
  return typeof name === 'string' && MINTED_NAME.test(name)
}

/** Guard two, in isolation. Exported so the self-tests can prove it alone. */
export function hitsNeverTouchList(name) {
  const lower = String(name ?? '').toLowerCase()
  return NEVER_TOUCH.some((real) => lower.includes(real))
}

/**
 * The only question the cleaner is allowed to ask.
 *
 * BOTH guards must agree. Not "either" -- a name has to look exactly like one
 * this suite minted AND contain nothing that marks it as real.
 */
export function isDeletable(name) {
  return matchesMintedShape(name) && !hitsNeverTouchList(name)
}

/**
 * Refuse loudly rather than deleting something we cannot prove is ours.
 *
 * This throws instead of returning false because there is no sensible way for a
 * caller to continue: reaching here means the cleaner was handed a row it did
 * not create, and the correct outcome is a red run that someone reads.
 */
export function assertDeletable(name, what = 'record') {
  if (isDeletable(name)) return name

  throw new Error(
    `REFUSING to delete ${what} ${JSON.stringify(name)}. This suite only ever ` +
      `deletes what it created, and only names of the exact form ` +
      `${TEST_PREFIX}-<label>-<12 letters from its alphabet> qualify. Reaching here means either a ` +
      `cleanup routine was handed a row it did not create, or the suite is ` +
      `pointed at a database it should not be able to write to. Neither is ` +
      `safe to continue past.`,
  )
}
