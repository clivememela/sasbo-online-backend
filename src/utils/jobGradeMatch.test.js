import { describe, expect, it } from 'vitest'

import { committedVocabulary } from '../test/apiContractScan.js'
import {
  comparisonKey,
  gradesMatch,
  isBlankGrade,
  jobGradeNoticeFor,
  nearMisses,
  resolveToOption,
  storedForm,
} from './jobGradeMatch'
import { asOptionValue } from '../components/form/options'

/** The same reader `jobGradeMatch` uses -- main's, not a second copy (CM-28). */
const optionValue = (option) =>
  asOptionValue(option && typeof option === 'object' ? option.value : option)

/**
 * THE JOB-GRADE FIXTURE — the browser half (ux.md §6c clause ⑤).
 *
 * The cases are NOT written here. They are READ from `api-vocabulary.json`, which
 * the server generates by calling its own `JobGradeMatcher` — so this file cannot
 * agree with a transcription of the rule while disagreeing with the rule itself.
 *
 * 🔴 That distinction is the entire point. Four registered defects in this system
 * are one rule implemented twice and allowed to drift (A6, A7, A17, A9). A test
 * asserting `comparisonKey('BA2') === 'ba2'` from a hand-typed table would keep
 * passing after the server changed, which is exactly how those four happened. This
 * asserts against the server's published output instead, so:
 *
 *   - change the rule on the server and not here  → these tests redden
 *   - change it here and not on the server        → these tests redden
 *   - change it on the server and forget to copy the artifact
 *                                                 → the SERVER's snapshot test reddens
 *
 * The one thing nobody can do is change half of it in silence.
 */
// Read through the reader the suite already has, not a third way to load one file.
// `committedVocabulary()` memoises, and two other suites already go through it.
const vocabulary = committedVocabulary()

describe('the published matching rule is the rule this browser implements', () => {
  const published = vocabulary.jobGradeMatching

  it('the artifact actually carries the rule', () => {
    // The non-vacuity floor. Without it, an artifact that lost `jobGradeMatching`
    // would leave every case below iterating over nothing and passing — a guard
    // looking at an empty list, which this arc has already found three times.
    expect(published, 'api-vocabulary.json has no jobGradeMatching block -- regenerate it with ./gradlew generateApiSurface').toBeDefined()
    expect(published.cases.length).toBeGreaterThan(8)
    expect(published.outcomes).toEqual(['MATCHED', 'CAPTURED', 'CLEARED'])
  })

  it.each(vocabulary.jobGradeMatching.cases)(
    'reproduces the server for $input',
    ({ input, comparisonKey: expectedKey, storedForm: expectedStored, blank }) => {
      expect(comparisonKey(input)).toBe(expectedKey)
      expect(storedForm(input)).toBe(expectedStored)
      expect(isBlankGrade(input)).toBe(blank)
    },
  )

  it('the fixture still contains the shapes that make the rule necessary', () => {
    // Asserting the INPUTS, not only the outputs. If the server's fixture ever lost
    // its whitespace and casing cases, everything above would stay green over a
    // browser that had stopped collapsing whitespace — there would simply be no
    // case left to catch it.
    const inputs = published.cases.map((c) => c.input)
    expect(inputs, 'a case differing only by CASE').toContain('BA2')
    expect(inputs, 'a case differing only by CASE').toContain('ba2')
    expect(inputs, 'a case with a DOUBLE SPACE -- the drift risk').toContain('Grade  c')
    expect(inputs, 'a case with surrounding whitespace').toContain('BA2 ')
    expect(inputs, 'a blank case').toContain('')
  })

  it('the double-space case really does fold onto the single-space one', () => {
    // The specific silent failure: if only one side collapsed whitespace,
    // "Grade  c" would match in the form and be captured as new on save.
    expect(gradesMatch('Grade  c', 'Grade c')).toBe(true)
    expect(comparisonKey('Grade  c')).toBe(comparisonKey('Grade c'))
  })
})

describe('resolveToOption -- rule 2, typing resolves to the list value', () => {
  const OPTIONS = [
    { value: 'BA2', label: 'BA2 - Bargaining unit' },
    { value: 'Grade c', label: 'Grade c' },
    { value: 'SBG04', label: 'SBG04' },
  ]

  it('resolves case- and space-insensitively to the canonical option', () => {
    expect(optionValue(resolveToOption(OPTIONS, 'ba2'))).toBe('BA2')
    expect(optionValue(resolveToOption(OPTIONS, 'BA2 '))).toBe('BA2')
    expect(optionValue(resolveToOption(OPTIONS, 'grade c'))).toBe('Grade c')
    expect(optionValue(resolveToOption(OPTIONS, 'GRADE  C'))).toBe('Grade c')
  })

  it('does not resolve a genuinely new grade', () => {
    expect(resolveToOption(OPTIONS, 'Prokureur')).toBeNull()
    expect(resolveToOption(OPTIONS, 'SBG4')).toBeNull()
  })

  it('does not resolve blank input', () => {
    expect(resolveToOption(OPTIONS, '')).toBeNull()
    expect(resolveToOption(OPTIONS, '   ')).toBeNull()
    expect(resolveToOption(null, 'BA2')).toBeNull()
  })

  it('works on plain-string options too', () => {
    // The API returns {value,label}; JOB_GRADE_OPTIONS and every other list on this
    // form are plain strings. Both shapes reach this code.
    expect(optionValue(resolveToOption(['BA2', 'Grade c'], 'ba2'))).toBe('BA2')
  })
})

describe('nearMisses -- rule 4, the "smartly" in "smartly allow free text"', () => {
  const OPTIONS = [{ value: 'SBG04' }, { value: 'SBG05' }, { value: 'BA2' }]

  it('offers the zero-padded grade for the realistic typo', () => {
    expect(nearMisses(OPTIONS, 'SBG4')).toEqual(['SBG04'])
  })

  it('ignores punctuation differences', () => {
    expect(nearMisses([{ value: 'Grade c' }], 'Grade-c')).toEqual(['Grade c'])
  })

  it('an exact match is a match, not a near miss', () => {
    expect(nearMisses(OPTIONS, 'SBG04')).toEqual([])
    expect(nearMisses(OPTIONS, 'sbg04')).toEqual([])
  })

  it('a genuinely new grade has no near miss', () => {
    expect(nearMisses(OPTIONS, 'Prokureur')).toEqual([])
    expect(nearMisses(OPTIONS, '')).toEqual([])
  })
})

describe('jobGradeNoticeFor -- the outcome, said out loud (clause ②)', () => {
  it('says nothing when the grade matched the master list', () => {
    // The canonical value is quietly substituted. There is nothing to report.
    expect(jobGradeNoticeFor({ jobGradeOutcome: 'MATCHED' }, { jobGrade: 'ba2' })).toBeNull()
  })

  it('names the bank when a new grade is captured', () => {
    const notice = jobGradeNoticeFor(
      { jobGradeOutcome: 'CAPTURED', jobGrade: { grade: 'Prokureur' } },
      { jobGrade: 'Prokureur', bankName: 'Absa Bank Limited' },
    )
    expect(notice).toContain('Prokureur')
    expect(notice).toContain('Absa Bank Limited')
    expect(notice, 'the user is told it is not yet everyone else’s option').toContain('reviewed')
  })

  it('🔴 does NOT stay silent when a submitted grade could not be saved', () => {
    // The exact regression. Before 02.5 the server discarded an unrecognised grade
    // with `.orElse(null)` and the save still reported success -- so the grade
    // appeared to work and then vanished. Saying nothing here would preserve that.
    const notice = jobGradeNoticeFor({ jobGradeOutcome: 'CLEARED' }, { jobGrade: 'Prokureur' })
    expect(notice).not.toBeNull()
    expect(notice).toContain('Prokureur')
  })

  it('does not report a blank field that stayed blank', () => {
    expect(jobGradeNoticeFor({ jobGradeOutcome: 'CLEARED' }, { jobGrade: '' })).toBeNull()
    expect(jobGradeNoticeFor({ jobGradeOutcome: 'CLEARED' }, { jobGrade: '   ' })).toBeNull()
  })

  it('says nothing when the server reported no outcome at all', () => {
    // An older server, or a save that never touched the field.
    expect(jobGradeNoticeFor({}, { jobGrade: 'BA2' })).toBeNull()
    expect(jobGradeNoticeFor(null, null)).toBeNull()
  })

  it('handles every outcome the server can report', () => {
    // Read from the published list rather than a local copy, so a fourth outcome
    // added on the server reddens here instead of silently falling through to the
    // default branch and saying nothing.
    expect(vocabulary.jobGradeMatching.outcomes).toEqual(['MATCHED', 'CAPTURED', 'CLEARED'])
  })
})
