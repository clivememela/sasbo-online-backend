import { execFileSync } from 'node:child_process'
import { mkdtempSync, rmSync, writeFileSync, mkdirSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join } from 'node:path'

import { describe, expect, it } from 'vitest'

import { REPO_ROOT } from './apiContractScan.js'
import { FLOORS, assertNonVacuousScan } from './assertNonVacuousScan'
import { shadowedSources, trackedSources } from './ignoredSources.js'

/**
 * NO SOURCE FILE IS HIDDEN FROM THE BUILD BY A .gitignore RULE.
 *
 * Tailwind v4's automatic source detection skips whatever .gitignore matches,
 * so an ignore rule is a build input, not just a git convenience. A bare
 * `reports` -- written for the test output at the repository root -- also
 * matched `src/pages/reports/`, and every tracked file under it stopped being
 * scanned. Three headings on /reports/organizer-report rendered white on white.
 * (The file count differs by tree -- four at the trunk `01819d2`, three after
 * `9f2c233` deleted the dead `OrganizerReport.css` -- so it is not written here.
 * Register row A42 carries it.)
 *
 * 🔴 THE GATE COULD NOT SEE IT. `npm run build` was green; the CSS was merely
 * thinner. `npm test` was green; jsdom has no CSS. Nothing failed. This test is
 * the only thing in the repository that would have.
 */
describe('no tracked source file is shadowed by .gitignore', () => {
  const tracked = trackedSources(REPO_ROOT)

  it('asked git for the source files and got a repository back', () => {
    // Without this, an ls-files that returned NOTHING would sail through the
    // assertion below -- zero shadowed files out of zero files is "clean", and
    // is exactly the vacuous pass this suite's floors doctrine exists to stop.
    assertNonVacuousScan(
      tracked,
      FLOORS.TRACKED_SOURCES,
      'tracked files under src/ and index.html that Tailwind must be able to scan',
    )
  })

  it('finds no source file that .gitignore hides from the build', () => {
    const shadowed = shadowedSources(REPO_ROOT, tracked)

    expect(
      shadowed.map((entry) => `${entry.path}  (hidden by ${entry.rule})`),
      'A tracked source file matches a .gitignore rule, so Tailwind v4 never ' +
        'scans it and every utility class unique to it is missing from the built ' +
        'CSS -- silently, with a green build. Anchor the rule to the repository ' +
        'root (`reports` -> `/reports`) rather than deleting it.',
    ).toEqual([])
  })

  /**
   * 🔴 PROVE THE GUARD BY BREAKING ITS SUBJECT.
   *
   * The assertion above passes on a clean repository, and it would also pass if
   * `shadowedSources` silently returned nothing -- a wrong `--no-index`, a
   * swallowed error, a bad parse. A passing test proves nothing until the thing
   * it guards is removed and the lane goes red. So: build a throwaway repository
   * with the ORIGINAL bare pattern in it, and require the scan to catch it.
   */
  describe('the guard can actually fail', () => {
    it('catches a bare pattern that shadows a nested source directory', () => {
      const dir = mkdtempSync(join(tmpdir(), 'ignored-sources-'))
      try {
        execFileSync('git', ['init', '-q'], { cwd: dir })
        mkdirSync(join(dir, 'src', 'pages', 'reports'), { recursive: true })
        writeFileSync(join(dir, 'src', 'pages', 'reports', 'Page.jsx'), 'export default null\n')
        writeFileSync(join(dir, 'src', 'pages', 'Safe.jsx'), 'export default null\n')
        // The exact rule that caused it, and the exact way it was written.
        writeFileSync(join(dir, '.gitignore'), 'reports\ncoverage\n')
        execFileSync('git', ['add', '-Af'], { cwd: dir })

        // EXACTLY these two, not "contains the shadowed one". The control file is the
        // half that catches the opposite failure -- a shadowedSources that returns
        // EVERYTHING -- and `toContain` would let a missing control pass silently while
        // its own message claimed to have checked for both.
        const files = trackedSources(dir)
        expect(files, 'the throwaway repo must contain both files').toEqual([
          'src/pages/Safe.jsx',
          'src/pages/reports/Page.jsx',
        ])

        const shadowed = shadowedSources(dir, files)
        expect(
          shadowed.map((entry) => entry.path),
          'the scan did not see a file it was built to see -- it is broken, ' +
            'and the green result above means nothing',
        ).toEqual(['src/pages/reports/Page.jsx'])

        // ...and the anchored form must let it through, or the fix we shipped
        // would be a different bug rather than a fix.
        writeFileSync(join(dir, '.gitignore'), '/reports\n/coverage\n')
        // `files` again, NOT a fresh discovery. Re-discovering here would let a broken
        // trackedSources() returning [] satisfy this .toEqual([]) vacuously -- the exact
        // failure this block exists to rule out. Pinning both directions of the proof to
        // the one set already asserted non-empty is what makes the second half mean
        // anything. It is also one fewer git process.
        expect(
          shadowedSources(dir, files),
          'anchoring the rule must stop shadowing the nested directory',
        ).toEqual([])
      } finally {
        rmSync(dir, { recursive: true, force: true })
      }
    })
  })
})
