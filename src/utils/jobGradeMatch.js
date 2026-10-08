/**
 * The job-grade matching rule — the browser's half (ux.md §6c clause ①).
 *
 * TRIM, COLLAPSE INTERNAL WHITESPACE, COMPARE CASE-INSENSITIVELY.
 * STORE EXACTLY AS SUPPLIED.
 *
 * ⚠️ THIS IS A MIRROR, NOT AN INVENTION.
 * The server implements the same rule in `utils/JobGradeMatcher.java` and PUBLISHES
 * it in `api-vocabulary.json` — the cases there are computed by calling the server's
 * own matcher, not transcribed by hand. `jobGradeMatch.test.js` asserts that every
 * published case is reproduced exactly by the functions below, so changing either
 * side without the other reddens the browser suite.
 *
 * 🔴 WHY THAT MACHINERY IS WORTH IT FOR ONE STRING COMPARISON.
 * This system already carries four registered defects that are all one rule
 * implemented twice and left to drift:
 *
 *   A6   'Payroll' here, 'Payroll Deduction' there
 *   A7   'Passport' in the rule, 'Passport Number' in the form
 *   A17  'ID' on the server, 'I.D. Number' from the browser
 *   A9   the client re-deriving a rule the server publishes
 *
 * The job-grade fallback asks the browser to decide "is this a grade we already
 * know?" — and the server then decides the same thing again on save. The
 * dangerous case is the near-agreement: if the browser collapses double spaces and
 * the server does not, `"Grade  c"` resolves to the existing grade on screen, is
 * captured as a brand-new grade on save, and the user is told it worked.
 *
 * 🔑 CASE IS COMPARED, NEVER CHANGED. `Grade c` is a real Old Mutual grade — a
 * lowercase `c`, sitting beside `Grade K`. Upper-casing a typed value to "tidy" it
 * would corrupt the union's own reference data.
 */

/**
 * ⚠️ RE-POINTED IN THE RE-PORT, and the target matters. The old branch carried its own
 * `src/utils/options.js`; main's Card UI owns the one reader, in `components/form/options.js`
 * (`normaliseOptions` / `asOptionValue`), and CM-28 is the defect that came from two readers with
 * "subtly different fallback semantics". So this module reads through MAIN's, and does not bring a
 * second copy across.
 */
import { asOptionValue } from '../components/form/options.js';

/** The value an option stores, whichever of the two shapes the list arrived in. */
const optionValue = (option) =>
  asOptionValue(option && typeof option === 'object' ? option.value : option);

/**
 * The comparison key: trimmed, internal whitespace collapsed to one space, lower
 * cased. Decides whether two grades are the same grade. Never stored.
 */
export const comparisonKey = (grade) =>
  String(grade ?? '').trim().replace(/\s+/g, ' ').toLowerCase();

/**
 * The value to STORE for a grade the user typed: trimmed, internal whitespace
 * collapsed, case left exactly as supplied.
 */
export const storedForm = (grade) => String(grade ?? '').trim().replace(/\s+/g, ' ');

/** Whether a submitted grade is usable at all. */
export const isBlankGrade = (grade) => comparisonKey(grade) === '';

/** Whether two grades are the same grade under this rule. */
export const gradesMatch = (a, b) => {
  const keyA = comparisonKey(a);
  return keyA !== '' && keyA === comparisonKey(b);
};

/**
 * The master-list option a typed value resolves to, or `null`.
 *
 * Rule 2 of §6c: typing that matches case- or space-insensitively resolves to the
 * LIST value — `ba2` becomes `BA2`. Verified against the real data: no two seeded
 * grades collide once folded, so this is unambiguous.
 */
export const resolveToOption = (options, typed) => {
  if (isBlankGrade(typed)) return null;
  return (options ?? []).find((option) => gradesMatch(typed, optionValue(option))) ?? null;
};

/**
 * Options that look like a typo for `typed` — rule 4, the "smartly" in
 * "smartly allow free text".
 *
 * `SBG4` for `SBG04` is the realistic mistake when grades are zero-padded, so the
 * comparison ignores non-alphanumerics and leading zeros inside the key. This is a
 * PROMPT, never an automatic substitution: silently turning what someone typed into
 * something else is the failure this whole unit is correcting.
 */
export const nearMisses = (options, typed) => {
  const loose = (value) => comparisonKey(value).replace(/[^a-z0-9]/g, '').replace(/0+(?=\d)/g, '');
  const target = loose(typed);
  if (target === '') return [];

  return (options ?? [])
    .filter((option) => {
      const candidate = optionValue(option);
      // An exact match is not a near miss -- it is a match, handled by resolveToOption.
      return !gradesMatch(typed, candidate) && loose(candidate) === target;
    })
    .map(optionValue);
};

/**
 * What to tell the user about the job grade they submitted (ux.md 6c clause 2).
 *
 * The three outcomes are the SERVER's, read off the saved member. CLEARED is the one
 * that matters: before 02.5 an unrecognised grade was discarded by `.orElse(null)`
 * and the save still reported success, so a typed grade appeared to work and then
 * vanished. Saying nothing here would leave that failure exactly as silent as it was.
 */
export function jobGradeNoticeFor(saved, submitted) {
    const outcome = saved?.jobGradeOutcome;
    const typed = submitted?.jobGrade;
    switch (outcome) {
        case "CAPTURED":
            return `Saved "${saved?.jobGrade?.grade ?? storedForm(typed)}" as a new job grade for ${submitted?.bankName ?? "this bank"} - it will be reviewed before others can pick it.`;
        case "CLEARED":
            // Only worth saying when the user actually supplied something. A blank
            // field that stays blank is not news.
            return isBlankGrade(typed) ? null : `The job grade "${storedForm(typed)}" could not be saved.`;
        case "MATCHED":
        default:
            return null;
    }
}
