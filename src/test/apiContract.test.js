import { describe, expect, it } from 'vitest'
import { readdirSync } from 'node:fs'
import { join } from 'node:path'

import {
  DYNAMIC_PATHS,
  KNOWN_MISSING,
  REPO_ROOT,
  committedPaths,
  committedSurface,
  discoverEndpoints,
  discoverServiceModules,
  normalisePath,
  parameterShape,
} from './apiContractScan.js'
import {
  FLOORS,
  assertBlankingPreservedCode,
  assertNonVacuousScan,
  assertNothingWasSkipped,
} from './assertNonVacuousScan'
import { DEFECTS, disabled, pin } from './knownDefects'
import { blankingDestroyedCode, unreadableDirectories } from './sourceScan.js'

/**
 * THE BROWSER MAY NOT CALL AN ENDPOINT THE SERVER DOES NOT HAVE.
 *
 * WHAT THIS COSTS AND WHY THAT MATTERS
 * No browser, no database, no running server -- a static read of
 * `src/services/` against a committed copy of the server's routing table. It
 * is milliseconds, which is what makes it affordable in the inner loop, and it
 * is the reason a broken contract is found by the person who broke it rather
 * than by a red end-to-end run three phases later.
 *
 * WHERE THE SURFACE COMES FROM
 * `src/test/api-surface.txt` is a COPY of `server/api-surface.txt`, which the
 * server generates from Spring's live `RequestMappingHandlerMapping` and
 * asserts fresh on every server build. The two repositories are independent
 * checkouts: nothing here reads across the boundary, and the artifact crosses
 * as a committed file or it does not cross. Refresh with the copy step in
 * `src/test/README.md`.
 *
 * Runs under `node`, which is now the suite's DEFAULT rather than something this
 * file opts into -- see `vite.config.js`. It reads files and touches no DOM; P3
 * measured jsdom construction at ~48% of suite wall clock, and that measurement
 * is one of the two this file's old opt-out contributed to the case for
 * inverting the default.
 */
describe('the API contract between this browser and its server', () => {
  const calls = discoverEndpoints()
  const modules = discoverServiceModules()
  const distinctPaths = [...new Set(calls.map((call) => call.path))].sort()
  const surface = committedPaths()

  // Compared on route SHAPE, not on the parameter's name -- see
  // `parameterShape`. The displayed paths stay unshaped, so a failure names the
  // real route rather than a normalised one.
  const surfaceShapes = new Set([...surface].map(parameterShape))
  const missing = distinctPaths.filter((path) => !surfaceShapes.has(parameterShape(path))).sort()

  describe('the scan actually scanned something', () => {
    it('read at least the floor of service modules', () => {
      // FLOORS.SERVICE_MODULES is 7 against an actual of 9. The headroom is
      // deliberate: merge two services in a routine refactor and a floor of 9
      // would redden for a reason nobody caused.
      assertNonVacuousScan(modules, FLOORS.SERVICE_MODULES, 'service modules under src/services/')
    })

    it('found at least the floor of distinct endpoints', () => {
      assertNonVacuousScan(
        distinctPaths,
        FLOORS.ENDPOINTS,
        'distinct endpoints discovered in src/services/',
      )
    })

    it('scanned EVERY service module on disk, not merely enough of them', () => {
      // Completeness, carrying no number -- the other half of a discovery
      // guard. The floor proves the scan is not vacuous; this proves it is not
      // partial. A module that stopped being scanned would still clear a floor
      // of 7 and would be invisible without this.
      const onDisk = readdirSync(join(REPO_ROOT, 'src', 'services'))
        .filter((name) => name.endsWith('.js') && !name.includes('.test.'))
        .sort()

      expect(
        modules.map((module) => module.path.replace('src/services/', '')).sort(),
        'every .js file in src/services/ must be scanned. A module missing here is a ' +
          'module whose endpoints nothing is checking, and it would still clear the floor',
      ).toEqual(onDisk)
    })

    it('read every directory it walked, rather than skipping one in silence', () => {
      // 🔴 THE COMPLETENESS CHECK ABOVE DOES NOT COVER THIS, and believing it did
      // was the mistake -- E2.
      //
      // That check compares the scan against a NON-RECURSIVE `readdirSync` of
      // `src/services`, while the walker underneath is recursive. So an unreadable
      // SUBDIRECTORY of `src/services` drops out of both sides at once: its name
      // is not `.js`, so it never appears in `onDisk`, and its files never reach
      // `modules`. The two lists still agree, and the guard reports success over
      // a scan that lost a subtree.
      //
      // 🔑 `src/services` is flat today, so that is latent rather than live --
      // which is exactly the shape worth asserting. The protection was a fact
      // about the directory being flat, not a property of the check.
      //
      // ⚠️ AND IT HAS TO BE ASSERTED HERE RATHER THAN ONCE SOMEWHERE ELSE. Vitest
      // isolates per file, so the walker's record of what it could not read lives
      // in this file's own worker; `vocabulary.parity.test.js` asserting it covers
      // only the walks IT performs.
      assertNothingWasSkipped(unreadableDirectories(), 'the service module scan')
    })

    it('did not destroy any code while blanking comments', () => {
      assertBlankingPreservedCode(blankingDestroyedCode(modules))
    })
  })

  describe('every endpoint the browser calls', () => {
    it('exists on the server, or is a named known-missing endpoint', () => {
      const unexplained = missing
        .filter((path) => !Object.hasOwn(KNOWN_MISSING, path))
        .map((path) => {
          const site = calls.find((call) => call.path === path)
          return `${path}  (called from ${site.at})`
        })

      expect(
        unexplained,
        'These endpoints are called by this browser and DO NOT EXIST on the server. ' +
          'Every request to one fails, and the screen that depends on it shows nothing ' +
          'with no error. Either the path is wrong here, or the endpoint has not been ' +
          'built -- decide which, then either fix the call or add it to KNOWN_MISSING ' +
          'with its register row.',
      ).toEqual([])
    })

    it('KNOWN_MISSING names every missing endpoint and no others', () => {
      // Asserted, not subtracted. An entry that is no longer missing hides a
      // fix; a missing endpoint that is not listed hides a defect.
      expect(missing).toEqual(Object.keys(KNOWN_MISSING).sort())
    })

    it('every dynamically built path is declared, and every declared one exists', () => {
      // Empty today, and asserted rather than assumed. A path the static scan
      // cannot see is exactly where the next missed endpoint hides, so the list
      // is visible and checked rather than absent.
      for (const path of DYNAMIC_PATHS) {
        expect(surface.has(path), `declared dynamic path ${path} is not on the server`).toBe(true)
      }
      expect(DYNAMIC_PATHS).toEqual([])
    })
  })

  /**
   * The two known-missing endpoints, through the register rather than beside it.
   *
   * Each row gets the pair this suite requires of every row: the skipped
   * standard-facing test saying what SHOULD happen, and a live pin asserting it
   * still does not. `defectPopulation.test.js` asserts that correspondence in
   * both directions, so these rows are counted like every other -- which they
   * were not in this guard's first draft, where they lived only in
   * `KNOWN_MISSING` and no correspondence guard could see them.
   */
  describe('the endpoints the server does not have', () => {
    it.skip(disabled(DEFECTS.A1, 'the districts endpoint exists'), () => {
      expect(surface.has('/api/districts')).toBe(true)
    })

    it(pin(DEFECTS.A1, 'the districts endpoint is still missing'), () => {
      expect(surface.has('/api/districts')).toBe(false)
    })

    it.skip(disabled(DEFECTS.A2, 'the debounce endpoint exists'), () => {
      expect(surface.has('/api/debounce')).toBe(true)
    })

    it(pin(DEFECTS.A2, 'the debounce endpoint is still missing'), () => {
      expect(surface.has('/api/debounce')).toBe(false)
    })
  })

  describe('the committed surface itself', () => {
    it('is a copy of a generated artifact, not something written by hand', () => {
      const lines = committedSurface()

      // Shape, not content: every line is `METHOD /path`. A hand-edited file
      // drifts into prose, blank sections and trailing notes, and this is what
      // notices.
      for (const line of lines) {
        expect(line, `${line} is not a METHOD /path line`).toMatch(/^[A-Z]+ \/\S*$/)
      }

      expect(lines.length).toBeGreaterThanOrEqual(FLOORS.SURFACE_LINES)
    })

    it('has LF line endings, so the copy compares byte for byte', () => {
      // The `.gitattributes` rule at the repository root pins this file. If it
      // is ever checked out with CRLF, the diff against the server's copy
      // becomes whole-file and nobody can see what actually changed.
      expect(committedSurface().join('\n')).not.toContain('\r')
    })
  })

  describe('path normalisation', () => {
    // Written against the CLASS: these are the shapes this repository's
    // services actually build, not a list of today's twenty paths.
    it.each([
      ['/api/members', '/api/members'],
      ['/api/members/${id}', '/api/members/{id}'],
      ['/api/members/${updatedMember.id}', '/api/members/{id}'],
      ['/api/members/${id}/banking', '/api/members/{id}/banking'],
      ['/api/districts?provinceId=${provinceId}', '/api/districts'],
      ['/api/job-grades${qs}', '/api/job-grades'],
      ['/api/branches?${params}', '/api/branches'],
      ['/api/debounce?fn=${fn}&delay=${delay}', '/api/debounce'],
    ])('normalises %s to %s', (raw, expected) => {
      expect(normalisePath(raw)).toBe(expected)
    })
  })
})
