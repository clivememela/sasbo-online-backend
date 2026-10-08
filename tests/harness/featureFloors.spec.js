import { test, expect } from '@playwright/test'
import { readFileSync } from 'node:fs'
import { sep } from 'node:path'
import { assertNonVacuousScan, FLOORS } from '../../support/assertNonVacuousScan.js'
import { featureFiles, repoRelative } from '../../support/sourceFiles.js'

/**
 * The floors that stop this suite reporting a green run of zero scenarios.
 *
 * THE FAILURE THIS EXISTS FOR is specific to a BDD suite and it is silent: the
 * `.feature` glob lives in `playwright.config.js`, and if it stops matching --
 * a renamed directory, a changed `testDir`, a `bddgen` that did not run -- then
 * `playwright test` finds no journeys, runs the harness project only, and exits
 * 0. A green run of nothing looks exactly like a green run of everything.
 */

/** Scenario headings, counted the way Gherkin declares them. */
function scenarios(files) {
  return files.flatMap((file) =>
    readFileSync(file, 'utf8')
      .split('\n')
      .filter((line) => /^\s*Scenario(\s+Outline)?:/.test(line))
      .map((line) => `${file.split(sep).pop()}: ${line.trim()}`),
  )
}

/** Discovered once for the module -- five assertions read the same two lists. */
const features = featureFiles()
const allScenarios = scenarios(features)

/**
 * The October build's feature directories, each with a floor of its own (FLOORS.HOME_SCENARIOS says why). Matched on
 * the path INSIDE this repository: an absolute path can carry a directory of the same name above it -- a runner's
 * `/home/...` would put every feature file in "home".
 */
const OWN_FLOORS = [
  ['home', FLOORS.HOME_SCENARIOS, "the first screen's scenarios"],
  ['signin', FLOORS.SIGNIN_SCENARIOS, "signing in's scenarios"],
  ['batch-print', FLOORS.BATCH_PRINT_SCENARIOS, "the batch print's scenarios"],
]
const inDirectory = (directory) => features.filter((file) => repoRelative(file).startsWith(`features/${directory}/`))

test.describe('the journey floors', () => {
  test('there are feature files at all', () => {
    assertNonVacuousScan(features, FLOORS.FEATURES, 'feature files')
  })

  test('there are scenarios inside them', () => {
    assertNonVacuousScan(allScenarios, FLOORS.SCENARIOS, 'scenarios')
  })

  test("month end's own project has scenarios of its own", () => {
    // `journeys-monthly` compiles `features/monthly/` alone, and the floor above
    // cannot see that directory go empty -- see FLOORS.MONTHLY_SCENARIOS.
    const monthly = features.filter((file) => file.split(sep).includes('monthly'))
    assertNonVacuousScan(scenarios(monthly), FLOORS.MONTHLY_SCENARIOS, "month end's scenarios")
  })

  for (const [directory, floor, what] of OWN_FLOORS) {
    test(`features/${directory}/ has scenarios of its own`, () => {
      assertNonVacuousScan(scenarios(inDirectory(directory)), floor, what)
    })
  }

  test('every scenario has a name, not just a heading', () => {
    // Completeness by discovery, carrying no number -- it moves with the suite.
    // An unnamed `Scenario:` compiles and runs, and reports as an empty title in
    // the Cucumber HTML, which is the report a stakeholder reads.
    const unnamed = allScenarios.filter((s) => /Scenario(\s+Outline)?:\s*$/.test(s))
    expect(unnamed, 'scenarios with no name').toEqual([])
  })

  test('every feature file declares a Feature', () => {
    const withoutFeature = features.filter(
      (file) => !/^\s*Feature:/m.test(readFileSync(file, 'utf8')),
    )
    expect(withoutFeature.map((f) => f.split(sep).pop()), 'files with no Feature:').toEqual([])
  })
})
