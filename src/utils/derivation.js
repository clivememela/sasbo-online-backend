/**
 * WHY A DERIVED FIELD IS EMPTY — the closed set, decided in 04.1.
 *
 * <h2>The gap this closes</h2>
 * `testingAndErrorHandling.md`'s EH-3 gives the three-state rule — loading is not
 * error is not empty — and it describes a FETCH. Every field in
 * `autoPopulation.md` is a DERIVATION, which has a fourth state the rule does not
 * cover: *the source arrived, and it had nothing to say about this member.*
 *
 * `autoPopulateBankAddress` is what that looks like untreated. It silently does
 * nothing when the branch cannot resolve, so the field stays blank —
 * indistinguishable from "there is nothing to fill", from "we have not tried yet",
 * and from a value the user deliberately cleared. That is ux.md's U3 and U5
 * failing at the same point, and it is why ten unresolvable branch options went
 * unnoticed for as long as they did.
 *
 * <h2>🔑 It is 02.5's idea, generalised</h2>
 * 02.5 invented this vocabulary for exactly one field: the job grade reports
 * MATCHED / CAPTURED / CLEARED and *never a silent null*. That worked, so the plan
 * asks 04.1 to widen it rather than invent a second dialect. These five states are
 * the general form of those three.
 *
 * <h2>Why a closed set and not free text</h2>
 * A note written at the call site is a habit; arc rule ① says a rule that must not
 * be forgotten becomes a prop, not a habit. A closed set can be asserted — 04.4's
 * "S1–S5 audited field by field" needs something mechanical to audit, and a field
 * whose state is not one of these five is a field nobody decided about.
 */

/** The state of one derived field. Every derived field on the form has exactly one. */
export const DERIVATION = Object.freeze({
  /** A value was derived and filled in. Say where it came from. */
  DERIVED: 'DERIVED',
  /** This field cannot apply to this member — nothing is missing. */
  NOT_APPLICABLE: 'NOT_APPLICABLE',
  /** The source could not be reached. NOT the same as having no value. */
  SOURCE_UNAVAILABLE: 'SOURCE_UNAVAILABLE',
  /** The source answered, and it holds nothing for this input. */
  NO_MATCH: 'NO_MATCH',
  /** The user typed over it. A derivation must never win this argument (S4). */
  USER_EDITED: 'USER_EDITED',
})

/** Every legal state, for the audits 04.4 runs. */
export const DERIVATION_STATES = Object.freeze(Object.values(DERIVATION))

/**
 * The sentence shown under a derived field.
 *
 * <p>`source` names WHERE the value came from, in the user's language — "the
 * bank's subscription record", not "GET /api/subscriptions". S3 asks whether the
 * user can tell it was filled *and by what*; a note that says only "auto-filled"
 * answers half of that and is the half that does not help.
 *
 * <p>Returns `undefined` for a state that needs no sentence, so it can be handed
 * straight to `Field`'s `note` prop without a ternary at every call site.
 */
export const derivationNote = (state, { source, detail } = {}) => {
  switch (state) {
    case DERIVATION.DERIVED:
      return source ? `Filled in from ${source}.` : 'Filled in automatically.'
    case DERIVATION.NOT_APPLICABLE:
      return detail ?? 'Not applicable to this member.'
    case DERIVATION.SOURCE_UNAVAILABLE:
      // Deliberately says what to DO. "Could not load" alone leaves the user
      // unable to tell whether waiting or typing is the right response.
      return source
        ? `${capitalise(source)} could not be loaded — you can still enter this yourself.`
        : 'This could not be loaded — you can still enter it yourself.'
    case DERIVATION.NO_MATCH:
      return detail ?? (source ? `Nothing found in ${source}.` : 'Nothing found to fill this in.')
    case DERIVATION.USER_EDITED:
      // No note. The user typed it; telling them so is noise.
      return undefined
    default:
      return undefined
  }
}

const capitalise = (text) => String(text ?? '').charAt(0).toUpperCase() + String(text ?? '').slice(1)

/**
 * The state of a derivation, from the three things that decide it.
 *
 * <p>🔑 THE ORDER OF THESE TESTS IS THE RULE, and it is written once here rather
 * than re-decided per field:
 *
 * <ol>
 *   <li>the user's own edit wins over everything — S4, and the defect that rule
 *       comes from is `Initials`, which re-derives over the user forever;</li>
 *   <li>then "we have no input yet", which is not a failure;</li>
 *   <li>then "the source is missing", which is;</li>
 *   <li>then "the source had nothing for this input", which is not.</li>
 * </ol>
 *
 * Getting 3 and 4 the wrong way round is how a failed fetch comes to read as an
 * empty result — CM-15's exact shape, and the reason the job-grade field keeps two
 * separate empty messages.
 */
export const derivationStateOf = ({ userEdited, hasInput, sourceLoaded, match }) => {
  if (userEdited) return DERIVATION.USER_EDITED
  if (!hasInput) return DERIVATION.NOT_APPLICABLE
  if (!sourceLoaded) return DERIVATION.SOURCE_UNAVAILABLE
  return match ? DERIVATION.DERIVED : DERIVATION.NO_MATCH
}

/**
 * What a DROP-DOWN says when it has no options — 05.2, EH-3's three states.
 *
 * <p>Separate from `derivationNote` on purpose, and the difference is one word:
 * these fields cannot be typed into. `derivationNote` ends "you can still enter
 * this yourself", which is true of a derived text field and a lie about a list-only
 * combobox — and a message that offers the user an action they cannot take is worse
 * than one that offers none.
 *
 * <p>🔑 The three answers are three different facts and they had one appearance:
 * before 05.2 every one of these fields showed "No options available" whether the
 * list was still arriving, had failed to arrive, or had genuinely come back empty.
 * The second is an outage the user should report; the third is reference data
 * somebody needs to capture; the first is neither, and telling the user to wait is
 * the only correct response to it.
 *
 * @param loadState 'loading' | 'ready' | 'failed'
 * @param what      the list, named the way a person would say it
 */
export const referenceListMessage = (loadState, what) => {
  if (loadState === 'loading') return `Loading ${what}…`
  if (loadState === 'failed') return `${capitalise(what)} could not be loaded — try again shortly.`
  return `No ${what} have been set up yet.`
}
