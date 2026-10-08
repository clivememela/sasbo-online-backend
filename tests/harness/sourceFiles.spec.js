import { test, expect } from '@playwright/test'
import { mkdirSync, mkdtempSync, readFileSync, rmSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { basename, join, relative, sep } from 'node:path'

import { assertNonVacuousScan } from '../../support/assertNonVacuousScan.js'
import {
  GENERATED_PREFIX,
  SKIP_DIRS,
  featureFiles,
  repoRelative,
  repoRoot,
  scriptFiles,
  sourceFiles,
  workingTreeFiles,
} from '../../support/sourceFiles.js'

/**
 * `support/sourceFiles.js`, which four harness specs stand on and none tested.
 *
 * WHY AN UNTESTED WALKER IS THE MOST EXPENSIVE THING TO LEAVE UNTESTED
 * `invariants`, `disabledPopulation`, `featureFloors` and `stack` all read
 * through it as a DEPENDENCY -- to walk files for their own subject -- and
 * **three of the four fail open.** Every headline assertion in those three is
 * absence-shaped: `toEqual([])`, `toHaveLength(0)`, "no file contains a sibling
 * path". A walker that returns less than it should makes all of them pass over
 * what it missed. They floor their own populations, which catches a walk
 * returning NOTHING; nothing checked the walk itself.
 *
 * 🔑 `stack.spec.js` IS THE EXCEPTION, and it supports the argument rather than
 * weakening it: it does `sourceFiles().find(...)` and THROWS when the file is
 * absent, so it is the one consumer a partial walk cannot quietly satisfy. The
 * difference is that it looks for something specific.
 *
 * 🔑 THIS IS THE SUITE'S COUNTERPART OF THE CLIENT'S `sourceScan.js`, and the two
 * differ in exactly the way that matters. That one CAUGHT a `readdirSync` throw
 * and answered it with `return`, silently dropping a subtree -- a defect, fixed
 * by recording what it skipped. This one has no `try` at all, so an unreadable
 * directory propagates and the run dies loudly. **That is the correct behaviour
 * and it is asserted below**, because "fails loudly" is a property somebody will
 * otherwise helpfully wrap in a try/catch on a Tuesday, turning this walker into
 * the defect its sibling just had.
 */

test.describe('the source walker every harness spec stands on', () => {
  // ⚠️ NO FLOOR ASSERTIONS HERE, and their absence is deliberate rather than an
  // omission. `invariants`, `disabledPopulation` and `featureFloors` each already
  // floor the exact collection this module hands them, with the same constant and
  // the same label -- so repeating them here would add three assertions that
  // redden identically to three that already exist, and a failure would be
  // indistinguishable from theirs.
  //
  // 🔑 AND IT WOULD BREAK THIS REPOSITORY'S OWN RULE. `assertNonVacuousScan` says
  // it "asserts on the collection the caller ALREADY HOLDS. It never
  // re-discovers." A call made only in order to be floored is a re-discovery.
  // The named-directory check below is this file's non-vacuity, and it carries no
  // number, so it grows with the suite.
  //
  // ⏳ A CACHE TEST WAS ALSO WRITTEN HERE AND REMOVED, because it asserted nothing:
  // `sourceFiles()` returns a fresh array from `.filter()` on every call, so
  // `expect(sourceFiles()).toEqual(sourceFiles())` is deep equality between two
  // new arrays -- true of a cached implementation AND of one that re-walks every
  // time, which is precisely what it claimed to catch. The cache has no observable
  // contract from outside the module, so there is nothing here to assert.

  test('skips every generated directory, and skips nothing else', () => {
    // Both halves. Too narrow a skip list means a guard convicts generated
    // output; too broad means a guard passes over real source. The list is the
    // UNION of two lists that had already drifted apart before this module
    // existed, so it is the thing most likely to be trimmed by someone who does
    // not know why an entry is there.
    const walked = sourceFiles().map(repoRelative)

    for (const skipped of SKIP_DIRS) {
      expect(
        walked.filter((p) => p.split('/').includes(skipped)),
        `${skipped}/ is generated, installed or produced, and nothing in it is ours ` +
          `to assert about. A file from it reaching a scan means a guard is about to ` +
          `convict output rather than source`,
      ).toEqual([])
    }

    // And the positive half: the directories that MUST be reachable. Named
    // rather than counted, so growth never reddens it.
    for (const owned of ['support', 'steps', 'pages', 'api', 'features', 'tests', 'tools']) {
      expect(
        walked.some((p) => p.startsWith(`${owned}/`)),
        `${owned}/ is not reachable by the walk, so every guard reading through it is ` +
          `silently ignoring that directory`,
      ).toBe(true)
    }
  })

  test('skips every compiled-feature directory by its prefix, whatever it is called', () => {
    // 🔴 Proved on a tree of its own, because this repository's compiled
    // directories exist only after `bddgen` -- a check over the real walk could
    // pass with none there. `.features-gen-acb` is how it failed: a new name
    // the old list did not carry, whose generated skip declarations the
    // disabled-population guard then convicted (see `GENERATED_PREFIX`).
    const root = mkdtempSync(join(tmpdir(), 'sasbo-walk-'))
    try {
      const put = (path) => {
        mkdirSync(join(root, path, '..'), { recursive: true })
        writeFileSync(join(root, path), '// compiled or owned -- the walk must tell them apart by directory alone\n')
      }
      put(`${GENERATED_PREFIX}/features/a.feature.spec.js`)
      put(`${GENERATED_PREFIX}-some-new-project/features/b.feature.spec.js`)
      put('steps/owned.steps.js')

      const walked = workingTreeFiles(root).map((f) => relative(root, f).split(sep).join('/'))
      expect(walked, 'the must-pass half: an owned file next to them is still walked').toEqual(['steps/owned.steps.js'])
    } finally {
      rmSync(root, { recursive: true, force: true })
    }
  })

  test('every directory the config compiles features into is a top-level one with that prefix', () => {
    // The walker, `scripts/clean.mjs`, `.gitignore` and `tools/test-all.mjs`
    // all recognise compiled features by `GENERATED_PREFIX` alone, so an
    // output directory without it would be scanned, left behind, committable
    // and root-owned on Linux -- all at once. `clean` and the container's chown
    // look only at the repository's top level, so that is asserted too.
    //
    // 🔑 READ FROM WHAT PLAYWRIGHT-BDD WAS GIVEN, not from the config's text:
    // its `defineBddConfig` records every config in `PLAYWRIGHT_BDD_CONFIGS`,
    // keyed by absolute output directory, for the workers -- this one included.
    // A text scan of the config was this test's first draft and matched
    // Playwright's own `outputDir: 'test-results'` on its first run.
    const outputs = Object.keys(JSON.parse(process.env.PLAYWRIGHT_BDD_CONFIGS || '{}')).map((dir) =>
      relative(repoRoot, dir).split(sep).join('/'),
    )

    assertNonVacuousScan(outputs, 2, 'feature-compile directories playwright-bdd was configured with')
    expect(
      outputs.filter((dir) => !dir.startsWith(GENERATED_PREFIX) || dir.includes('/')),
      `compile directories that are not top-level ${GENERATED_PREFIX}* ones`,
    ).toEqual([])
  })

  test('excludes package-lock.json by name, and keeps the other json', () => {
    // Excluded by NAME rather than by extension, which is a distinction worth
    // pinning: it is ours, it is generated, and it is hundreds of lines of
    // integrity hashes no invariant has an opinion about -- while
    // `package.json` in the same directory is read by the Playwright version
    // guard and must stay.
    const names = sourceFiles().map((f) => basename(f))

    expect(names).not.toContain('package-lock.json')
    expect(names).toContain('package.json')
  })

  test('the narrow view is a subset of the broad one', () => {
    // `scriptFiles()` is the JavaScript this suite is written in and
    // `sourceFiles()` is everything it could assert about, so the first must be
    // contained in the second. They are separate filters over one cached walk,
    // and the failure this catches is the two disagreeing -- which is precisely
    // the drift that produced this module: three specs, three walks, three
    // different answers to "is this ours?".
    const broad = new Set(sourceFiles())

    expect(
      scriptFiles().filter((f) => !broad.has(f)).map(repoRelative),
      'a file the script view walks that the source view does not',
    ).toEqual([])

    expect(
      featureFiles().filter((f) => !broad.has(f)).map(repoRelative),
      'a feature file the source view does not reach, which would make the feature ' +
        'floors and the suite-wide invariants disagree about what this repository holds',
    ).toEqual([])
  })

  test('every feature file it returns is actually under features/', () => {
    // Completeness by discovery, carrying no number. `featureFiles()` filters on
    // a path prefix, and a prefix built from the wrong root would return
    // EVERYTHING or nothing. ⚠️ The everything case is the one a floor cannot
    // catch anywhere -- `featureFloors.spec.js`'s canary clears comfortably when
    // the walk hands it the whole repository, and reports a healthy suite.
    const strays = featureFiles().map(repoRelative).filter((p) => !p.startsWith('features/'))

    expect(strays, 'files returned as features that are not under features/').toEqual([])
  })

  test('🔴 fails LOUDLY on a directory it cannot read, rather than skipping it', () => {
    // 🔴 THE PROPERTY THAT MAKES EVERY ASSERTION ABOVE MEAN SOMETHING, and the one
    // whose loss would be invisible.
    //
    // This walker has no `try` around its `readdirSync`, so a permission error or
    // a directory renamed mid-walk propagates and the run dies naming the path.
    // The alternative -- catching and returning -- removes an entire subtree from
    // every guard reading through it, reports nothing, and leaves each of them
    // passing over what it missed.
    //
    // 🔑 THAT IS NOT HYPOTHETICAL: THE CLIENT'S COUNTERPART DID EXACTLY THAT. Its
    // walker caught the throw and answered `return`, and the guard standing on it
    // had 27 points of floor headroom to hide the loss in. It now records what it
    // skipped so a consumer can assert the list is empty.
    //
    // Asserted over the SOURCE rather than by making a directory unreadable,
    // because a test that chmods a directory is a test that leaves a broken
    // directory behind when it fails. The shape is what matters, and
    // `stack.spec.js` already reads its own subject's source for the same reason.
    const source = readFileSync(new URL('../../support/sourceFiles.js', import.meta.url), 'utf8')
    const walk = source.match(/function walk\([\s\S]*?\n}/)?.[0]

    expect(walk, 'the walk function was not found -- this guard is asserting nothing').toBeTruthy()

    expect(
      // 🔴 STATEMENTS, NOT WORDS. `/\btry\b/` over the function's raw text matches
      // the WORD inside a comment -- and this repository writes long explanatory
      // comments inside functions as house style, so an ordinary comment
      // mentioning a caught error would redden this with a message accusing the
      // walker of swallowing errors. That is the "guard matched prose rather than
      // code" defect this pass has already recorded once, and committing it again
      // in the guard against it would be its own finding.
      /\btry\s*\{/.test(walk) || /\}\s*catch\b/.test(walk),
      'the walk now swallows a read error. An unreadable directory removes its whole ' +
        'subtree from every guard reading through this module, and each of those ' +
        'guards asserts an ABSENCE -- so they all pass over the gap and report a ' +
        'clean suite. If a read error genuinely has to be tolerated, RECORD the ' +
        'directories that failed and let each consumer assert the list is empty, ' +
        'which is what the client repository had to do after making this exact change',
    ).toBe(false)
  })
})
