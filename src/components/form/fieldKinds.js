/**
 * WHAT A FIELD IS, AND THEREFORE WHAT KEYBOARD IT GETS. R-RESP-G.
 *
 * 🔴 THE MEASUREMENT THIS EXISTS FOR: across 43 inputs this application declares
 * **0 `inputmode`, 0 `enterkeyhint` and 1 `autocomplete`**. So every number a member
 * types on a phone -- ID number, membership number, salary, a phone number -- gets the
 * full QWERTY keyboard, and every field asks them to type an address the browser
 * already knows.
 *
 * 🔑 WHY A `kind` RATHER THAN FOUR PROPS. Four attributes that must agree is four
 * chances to disagree, and they disagree silently: `type="number"` with no `inputmode`
 * still gives a phone the wrong keyboard on some Androids, and `autocomplete="off"` on
 * an identifier field is a decision somebody made deliberately (the automation arc
 * asked for it) that a later edit would quietly undo. Naming the field's ROLE once, and
 * deriving all four, means the only thing a call site can get wrong is the role -- and
 * a wrong role is visible in review in a way a missing `enterkeyhint` never is.
 *
 * ⚠️ `type` is NOT derived here. `type="number"` brings spinner arrows, silent
 * scroll-wheel edits and a value that is '' for anything unparseable -- none of which
 * is wanted for a membership number. Numeric fields stay `type="text"` and get their
 * keyboard from `inputmode`, which is the modern advice and the reason `inputmode`
 * exists at all.
 */

/** @typedef {'text'|'name'|'email'|'tel'|'number'|'identifier'|'money'|'postcode'|'search'|'date'} FieldKind */

const KINDS = {
  text: {},

  name: { autoComplete: 'name', enterKeyHint: 'next', autoCapitalize: 'words' },

  email: {
    type: 'email',
    inputMode: 'email',
    autoComplete: 'email',
    enterKeyHint: 'next',
    autoCapitalize: 'none',
    spellCheck: false,
  },

  tel: {
    type: 'tel',
    inputMode: 'tel',
    autoComplete: 'tel',
    enterKeyHint: 'next',
  },

  // Plain quantities. `numeric` rather than `decimal`: no fractional part.
  number: { inputMode: 'numeric', enterKeyHint: 'next', spellCheck: false },

  /**
   * Membership numbers, ID numbers, employee numbers.
   *
   * `autocomplete="off"` is DELIBERATE and was asked for by the automation arc: a
   * browser offering to fill somebody else's membership number into this field is a
   * data-integrity problem, not a convenience. Do not "tidy" it away.
   */
  identifier: {
    inputMode: 'numeric',
    autoComplete: 'off',
    enterKeyHint: 'next',
    spellCheck: false,
    autoCapitalize: 'none',
  },

  // A REFERENCE: letters and digits together -- an employee number (EMP-10001), a passport
  // number (A12345678). `identifier` would hand a phone a numbers-only keypad with no way to
  // type the letters, so the field is unfillable on the device most likely to be used for it.
  // Autofill off for the same reason as `identifier`: the browser only knows the operator's own.
  reference: {
    autoComplete: 'off',
    enterKeyHint: 'next',
    spellCheck: false,
    autoCapitalize: 'characters',
  },

  // Amounts. `decimal` gives the phone a decimal separator; `numeric` does not.
  money: { inputMode: 'decimal', enterKeyHint: 'next', spellCheck: false },

  postcode: {
    inputMode: 'numeric',
    autoComplete: 'postal-code',
    enterKeyHint: 'next',
    spellCheck: false,
  },

  // `search` gets "Search" on the on-screen return key, which is the whole point.
  search: {
    type: 'search',
    inputMode: 'search',
    enterKeyHint: 'search',
    autoComplete: 'off',
    spellCheck: false,
  },

  // Dates go through DateField (03.3), never a bare input. Present so a call site
  // that asks for one gets the right attributes rather than silently getting `text`.
  date: { inputMode: 'numeric', autoComplete: 'off', enterKeyHint: 'next', spellCheck: false },

  /**
   * 🔑 TWO PASSWORD KINDS, NOT ONE, and the difference is the whole reason this module
   * exists. `current-password` tells a password manager to OFFER a saved credential;
   * `new-password` tells it to GENERATE one and, critically, not to autofill the old
   * one into a field that is creating a new account. Written as one `password` kind,
   * the sign-up form would have quietly asked managers to fill in the existing
   * password — the exact class of silent disagreement `fieldKinds` was built to stop.
   *
   * `enterKeyHint: 'go'` on both: a password field is the last thing typed before the
   * form is submitted, so "Go" is what the on-screen key should say.
   */
  password: {
    type: 'password',
    autoComplete: 'current-password',
    enterKeyHint: 'go',
    autoCapitalize: 'none',
    spellCheck: false,
  },

  newPassword: {
    type: 'password',
    autoComplete: 'new-password',
    enterKeyHint: 'go',
    autoCapitalize: 'none',
    spellCheck: false,
  },
}

export const FIELD_KINDS = Object.freeze(Object.keys(KINDS))

/**
 * The DOM attributes for a field of this kind. Unknown kinds throw rather than
 * degrading to `text`: a typo'd kind that silently behaves like plain text is the
 * failure this whole module exists to stop, and it would never be noticed.
 */
export function attributesForKind(kind = 'text') {
  const found = KINDS[kind]
  if (!found) {
    throw new Error(
      `Unknown field kind "${kind}". Known kinds: ${FIELD_KINDS.join(', ')}. ` +
        `A field's kind decides its keyboard, its autofill and its return key -- ` +
        `falling back to plain text would be silent and wrong.`,
    )
  }
  return { type: 'text', ...found }
}
