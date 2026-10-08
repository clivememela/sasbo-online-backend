import { test, expect } from '@playwright/test'
import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { assertNonVacuousScan, FLOORS } from '../../support/assertNonVacuousScan.js'
import { featureFiles, repoRelative, scriptFiles } from '../../support/sourceFiles.js'

/**
 * This repository's disabled population, asserted EXACTLY.
 *
 * WHY A SUITE COUNTS ITS OWN SKIPS
 * The arc deliberately lands roughly a dozen disabled assertions across the
 * three repositories, each one a defect asserted rather than fixed. Individually
 * every one is justified. Collectively, nothing was counting them -- and a suite
 * carrying a dozen disabled tests with no register behind it reads as neglected
 * rather than deliberate. Worse, the rot is asymmetric: adding a skip without a
 * reason is visible in review, but FIXING the defect and leaving the test
 * disabled is invisible forever.
 *
 * So this asserts in both directions:
 *   - every disabled test names a register row;
 *   - every declared row still has a disabled test;
 *   - and the counts match.
 *
 * 🔑 THE EXPECTED POPULATION HERE IS EMPTY, and that is the interesting case.
 * An empty list is not an absence of a guard -- it is the claim "this suite
 * skips nothing", asserted. The moment someone adds a `.skip` or a `.fixme`,
 * this reddens and tells them to justify it against the register. A guard that
 * only exists once there is something to guard is a guard that arrives late.
 */
const EXPECTED_DISABLED = {
  // 'someTestTitle': 'A99 -- what the register says about it',
}

/** `test.skip` / `test.fixme` in their declaration forms, plus `.only`. */
const DISABLED_FORMS = /\btest\.(skip|fixme)\s*\(/g
const FOCUSED_FORMS = /\btest\.only\s*\(/g

/**
 * Every JavaScript file except this one, paired with its text, read ONCE.
 *
 * The scanner must not count its own patterns. It reads every `.js` in the
 * repository including this one, so without the exclusion below it would report
 * the regex literals above as three disabled tests -- a guard failing on
 * itself, which is the most confusing possible first failure.
 */
const scannable = scriptFiles()
  .filter((file) => file !== fileURLToPath(import.meta.url))
  .map((file) => ({
    path: repoRelative(file),
    text: readFileSync(file, 'utf8'),
  }))

function scanFor(pattern) {
  const hits = []
  for (const { path, text } of scannable) {
    for (const match of text.matchAll(pattern)) {
      hits.push(`${path}: ${match[0]}`)
    }
  }
  return hits
}

/**
 * The Gherkin half of the same population.
 *
 * 🔴 THE `.js` SCAN ABOVE CANNOT SEE A SKIPPED SCENARIO. `playwright-bdd`
 * compiles `@skip` into a `test.skip` inside `.features-gen/`, which this
 * scanner deliberately ignores as generated output -- so before this section
 * existed, a suite could skip every scenario it had and the disabled-population
 * guard would report a clean, empty population. The guard that counts skips has
 * to look where skips are actually written.
 *
 * Two tags, doing two different jobs, and the difference is the whole
 * convention:
 *
 *   @known-broken   this scenario is about a defect the register carries. The
 *                   comment above it names the row. It may still RUN -- a
 *                   scenario that asserts a defect is how the fix gets noticed.
 *   @skip           and it does not run, because it is written the way the
 *                   application SHOULD behave and fails today.
 *
 * A `@skip` without `@known-broken` is a scenario somebody gave up on, and is
 * refused here.
 */

/**
 * Every tagged scenario in the suite, with the comment block that precedes it.
 *
 * The comment is read by walking BACKWARDS from the tag line while the lines
 * are comments, rather than by matching a fixed number of lines: an explanation
 * worth writing is rarely one line, and a guard that only reads the line
 * immediately above rewards short explanations over accurate ones.
 */
function readTaggedScenarios() {
  const found = []

  for (const file of featureFiles()) {
    const lines = readFileSync(file, 'utf8').split('\n')

    lines.forEach((line, index) => {
      if (!/^\s*@/.test(line)) return

      const heading = lines.slice(index + 1).find((next) => next.trim() !== '')
      const preamble = []
      for (let above = index - 1; above >= 0 && /^\s*#/.test(lines[above]); above -= 1) {
        preamble.unshift(lines[above])
      }

      found.push({
        file: repoRelative(file),
        tags: line.trim().split(/\s+/),
        title: (heading ?? '').replace(/^\s*Scenario(\s+Outline)?:\s*/, '').trim(),
        preamble: preamble.join('\n'),
      })
    })
  }
  return found
}

/**
 * Read once for the module. Six assertions below filter this same list, and
 * each one previously re-walked `features/` and re-read every file in it.
 */
const taggedScenarios = readTaggedScenarios()

/** A register row id, as the register writes them: a class letter and a number. */
const REGISTER_ROW = /\b[A-F]\d{1,2}\b/

test.describe('the disabled population in the feature files', () => {
  test('the scan finds scenarios to check at all', () => {
    const knownBroken = taggedScenarios.filter((s) => s.tags.includes('@known-broken'))
    assertNonVacuousScan(knownBroken, FLOORS.KNOWN_BROKEN_SCENARIOS, '@known-broken scenarios')
  })

  test('every @known-broken scenario names the register row it is about', () => {
    // The prose, for whoever opens the feature file.
    const unexplained = taggedScenarios
      .filter((scenario) => scenario.tags.includes('@known-broken'))
      .filter((scenario) => !REGISTER_ROW.test(scenario.preamble))
      .map((scenario) => `${scenario.file}: ${scenario.title}`)

    expect(
      unexplained,
      'scenarios tagged @known-broken with no register row named in the comment above the tag. ' +
        'A skipped scenario with no explanation is indistinguishable from one somebody gave up on.',
    ).toEqual([])
  })

  test('every @known-broken scenario carries its register row as a TAG', () => {
    // 🔴 AND THE TAG, BECAUSE THE COMMENT DOES NOT REACH THE REPORT. Gherkin
    // comments are dropped during compilation, so a stakeholder reading the
    // Cucumber HTML sees a skipped row with no indication of why -- which is
    // exactly the impression the plans warn about: "the tests don't work"
    // rather than "five known issues, tracked". Tags DO survive, so the defect
    // id travels with the scenario into the artefact people are shown.
    const untagged = taggedScenarios
      .filter((scenario) => scenario.tags.includes('@known-broken'))
      .filter((scenario) => !scenario.tags.some((tag) => /^@defect:[A-F]\d{1,2}$/.test(tag)))
      .map((scenario) => `${scenario.file}: ${scenario.title}`)

    expect(
      untagged,
      'scenarios tagged @known-broken with no @defect:<row> tag beside it. The comment explains ' +
        'the defect to a reader of this repository; the tag is what a reader of the report gets.',
    ).toEqual([])
  })

  test('nothing is skipped except a known defect', () => {
    const orphaned = taggedScenarios
      .filter((scenario) => scenario.tags.some((tag) => tag === '@skip' || tag === '@fixme'))
      .filter((scenario) => !scenario.tags.includes('@known-broken'))
      .map((scenario) => `${scenario.file}: ${scenario.title}`)

    expect(
      orphaned,
      'scenarios skipped without being tagged @known-broken. Skipping for unreliability is ' +
        'not permitted here -- a flaky scenario is fixed in the session that finds it.',
    ).toEqual([])
  })

  test('no scenario is focused, and none configures a retry', () => {
    const focused = taggedScenarios.filter((s) => s.tags.includes('@only'))
    expect(focused.map((s) => s.title), 'focused scenarios').toEqual([])

    // `@retries:n` is a playwright-bdd tag that silently overrides the config's
    // `retries: 0` for one scenario -- a per-scenario quarantine that leaves no
    // trace in the report beyond a green run.
    const retried = taggedScenarios.filter((s) => s.tags.some((tag) => /^@retries:/.test(tag)))
    expect(retried.map((s) => s.title), 'scenarios configuring their own retries').toEqual([])
  })
})

test.describe('the disabled population', () => {
  test('the scan reaches this suite at all', () => {
    // 🔴 THE CANARY THIS HALF OF THE FILE DID NOT HAVE.
    //
    // Every assertion below is of the "assert an absence" shape --
    // `toHaveLength(0)` against an empty `EXPECTED_DISABLED`, and
    // `toEqual([])` for focused tests. All of them pass when the walk returns
    // NOTHING. A renamed directory, an added `SKIP_DIRS` entry or a throw
    // inside the walker would leave this file reporting a clean suite forever,
    // which is precisely the fail-open mode the rest of this file is about.
    //
    // Found by the closing refactor: the Gherkin half above was floored from
    // the day it was written, and this half never was.
    assertNonVacuousScan(scannable, FLOORS.SUITE_SCRIPT_FILES, 'suite JavaScript files')
  })

  test('nothing is skipped that the register has not accounted for', () => {
    const disabled = scanFor(DISABLED_FORMS)
    const declared = Object.keys(EXPECTED_DISABLED)

    expect(
      disabled,
      `This suite declares ${declared.length} disabled test(s). Found ${disabled.length}. ` +
        `A skip is only ever for a defect asserted deliberately, and it must name its ` +
        `register row in EXPECTED_DISABLED. Skipping for unreliability is not permitted ` +
        `-- a flaky test is fixed in the session that finds it.`,
    ).toHaveLength(declared.length)
  })

  test('nothing declared as disabled has quietly been re-enabled', () => {
    // The half that actually rots. A defect gets fixed, the test is re-enabled,
    // and the register keeps claiming an assertion that no longer exists.
    const disabled = scanFor(DISABLED_FORMS).join('\n')
    const missing = Object.keys(EXPECTED_DISABLED).filter((title) => !disabled.includes(title))

    expect(missing, 'declared disabled, but no longer skipped in the suite').toEqual([])
  })

  test('no test is focused', () => {
    // `test.only` left behind silently reduces a suite to one test and still
    // exits 0 locally. `forbidOnly` catches it in CI; this catches it here.
    expect(scanFor(FOCUSED_FORMS), 'focused tests left in the suite').toEqual([])
  })
})
