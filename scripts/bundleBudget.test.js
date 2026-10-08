import { spawnSync } from 'node:child_process'
import { mkdirSync, mkdtempSync, rmSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'
import process from 'node:process'

import { describe, expect, it } from 'vitest'

/**
 * THE BUNDLE BUDGET CAN ACTUALLY FAIL.
 *
 * 🔴 WHY THIS IS NOT CEREMONY. `npm run budget` prints four green rows against the
 * real dist/, and green is exactly what a guard measuring NOTHING also prints. This
 * repository has already been bitten by that shape: the automation arc recorded this
 * very script as able to pass while measuring nothing, and the design arc's P1 found
 * two instruments that reported clean because they could not read their subject.
 *
 * P3's `03.1` states it as a precondition rather than a nicety -- "bundle-budget.mjs
 * proven to fail on a deliberate overage BEFORE anything relies on it" -- because
 * `03.1` is the unit that adds shadcn and route splitting, and the budget is the only
 * thing standing between that and a bundle nobody notices doubling.
 *
 * So: run the real script against synthetic dist/ directories whose answer is known,
 * and require it to be right about each. It reads `dist` relative to the CWD, so a
 * temporary directory is all the isolation needed -- the real dist/ is never touched.
 */

const HERE = dirname(fileURLToPath(import.meta.url))
const SCRIPT = join(HERE, 'bundle-budget.mjs')

/** Run the real budget script against a dist/ we built to have a known answer. */
function budgetAgainst(build) {
  const dir = mkdtempSync(join(tmpdir(), 'bundle-budget-'))
  try {
    build(dir)
    const run = spawnSync(process.execPath, [SCRIPT], { cwd: dir, encoding: 'utf8' })
    return { status: run.status, out: `${run.stdout}${run.stderr}` }
  } finally {
    rmSync(dir, { recursive: true, force: true })
  }
}

/**
 * A dist/ that looks like a real build: an ENTRY chunk that index.html names, some
 * lazy route chunks it does not, css, and a couple of assets.
 *
 * The entry/lazy split is not decoration -- `entryJs` is read out of index.html, so a
 * fixture whose index.html names no script measures 0 and trips the entry FLOOR. That
 * happened the moment the entry row was added, which is the fixture doing its job.
 */
function realisticBuild(dir, { entryKiB = 280, lazyKiB = 220, cssKiB = 50, assetKiB = 240 } = {}) {
  const dist = join(dir, 'dist')
  mkdirSync(join(dist, 'assets'), { recursive: true })
  writeFileSync(join(dist, 'assets', 'index-aaaa.js'), 'x'.repeat(entryKiB * 1024))
  writeFileSync(join(dist, 'assets', 'Members-dddd.js'), 'x'.repeat(lazyKiB * 1024))
  writeFileSync(join(dist, 'assets', 'index-bbbb.css'), 'y'.repeat(cssKiB * 1024))
  writeFileSync(join(dist, 'assets', 'logo-cccc.png'), 'z'.repeat(assetKiB * 1024))
  writeFileSync(join(dist, 'robot.png'), 'w'.repeat(4 * 1024))
  writeFileSync(
    join(dist, 'index.html'),
    '<!doctype html><html><head>' +
      '<script type="module" crossorigin src="/assets/index-aaaa.js"></script>' +
      '<link rel="stylesheet" crossorigin href="/assets/index-bbbb.css">' +
      '</head><body><div id="root"></div></body></html>',
  )
  return dist
}

/** The javascript ceiling the script holds today, read from its own report on a build within every limit. */
function javascriptCeilingKiB() {
  const { out } = budgetAgainst((dir) => realisticBuild(dir))
  const row = out.match(/ javascript +[\d.]+ KiB +floor +[\d.]+ KiB +ceiling +([\d.]+) KiB/)
  expect(row, `the report names no javascript ceiling:\n${out}`).not.toBeNull()
  return Number(row[1])
}

describe('the bundle budget can fail', () => {
  it('passes a build that is within every limit — so a failure below means something', () => {
    const { status, out } = budgetAgainst((dir) => realisticBuild(dir))

    expect(out, 'the four rows should all read OK').not.toMatch(/OVER |UNDER/)
    expect(status, `expected a clean exit, got ${status}:\n${out}`).toBe(0)
  })

  /**
   * The direction the ceiling exists for: something got bigger and nobody looked.
   */
  it('FAILS when the javascript is over its ceiling', () => {
    // 50 KiB of js over the javascript ceiling the script holds TODAY (bundle-budget.mjs's `js`, read from its own report)
    // -- and deliberately arranged as a 300 KiB entry plus the rest in lazy chunks, so the ENTRY row stays green and this
    // case can only be caught by the total. Each row is proved on its own. The fixture was a number -- 700, 750, 850,
    // then 900 -- each until the ceiling passed it, and a fixture under the ceiling proves nothing (October build
    // integration, run 37675550980: 850 against 883.88, so the case went red for the wrong reason). Read off the ceiling,
    // it is always over it.
    const lazyKiB = Math.ceil(javascriptCeilingKiB()) - 300 + 50
    const { status, out } = budgetAgainst((dir) =>
      realisticBuild(dir, { entryKiB: 300, lazyKiB }))

    expect(out).toMatch(/OVER +javascript/)
    expect(out, 'it should say what to look for, not just that a number moved')
      .toMatch(/test tooling has leaked into the product/)
    expect(status, 'an overage must be a non-zero exit, or CI will not see it').toBe(1)
  })

  /**
   * 🔴 The regression the entry row exists for, and the ONLY row that can see it.
   *
   * `03.1` split every route, cutting the entry chunk from 506 KiB to 287. Converting
   * one lazy route back to a static import moves its bytes into the entry and changes
   * the TOTAL not at all -- so `javascript`, `all assets` and the file count all stay
   * green while every first paint gets slower.
   */
  it('FAILS when a route un-lazies itself into the entry chunk, with the total unchanged', () => {
    // Same total js as the realistic build (280 + 220), all of it in the entry now.
    const overEntry = budgetAgainst((dir) => realisticBuild(dir, { entryKiB: 500, lazyKiB: 0 }))
    const baseline = budgetAgainst((dir) => realisticBuild(dir))

    expect(overEntry.out).toMatch(/OVER +entry js/)
    expect(overEntry.status, 'a slower first paint must be a failure, not a shrug').toBe(1)
    expect(baseline.out, 'and the split build it is compared against must pass')
      .not.toMatch(/OVER |UNDER/)
  })

  it('FAILS when the css is over its ceiling', () => {
    const { status, out } = budgetAgainst((dir) => realisticBuild(dir, { cssKiB: 90 }))

    expect(out).toMatch(/OVER +css/)
    expect(status).toBe(1)
  })

  /**
   * 🔴 The direction that matters more, and the one a ceiling-only guard misses
   * entirely: a dist/ that shipped almost nothing satisfies every `actual <= ceiling`
   * assertion. A guard that only has ceilings reports a broken build as its best
   * result ever.
   */
  it('FAILS when the build emitted almost nothing — the vacuous PASS', () => {
    const { status, out } = budgetAgainst((dir) => {
      const dist = join(dir, 'dist')
      mkdirSync(dist, { recursive: true })
      writeFileSync(join(dist, 'index.html'), '<!doctype html>')
    })

    expect(out, 'every byte row is under its floor, not over a ceiling').toMatch(/UNDER/)
    expect(out).toMatch(/under its FLOOR/)
    expect(status, 'a build that emitted nothing must not be the greenest run in history')
      .toBe(1)
  })

  /**
   * The file-count floor earns its own case: one big asset in an otherwise empty
   * dist/ clears every BYTE floor while shipping no application at all. Bytes alone
   * cannot tell "a built app" from "a large image".
   */
  it('FAILS on one large asset and nothing else, even though the bytes look fine', () => {
    const { status, out } = budgetAgainst((dir) => {
      const dist = join(dir, 'dist')
      mkdirSync(dist, { recursive: true })
      // Comfortably over every byte floor, and not an application.
      writeFileSync(join(dist, 'enormous.png'), 'p'.repeat(400 * 1024))
    })

    expect(out, 'the byte rows are satisfied; the COUNT is what must object')
      .toMatch(/UNDER +emitted files/)
    expect(status).toBe(1)
  })

  /**
   * 🔑 DOCUMENTS ARE MEASURED ON THEIR OWN ROW, AND THIS PAIR IS WHAT KEEPS THAT HONEST.
   *
   * The membership application PDF is 487.6 KiB, and counted inside `all assets` it put a
   * healthy build 350 KiB over its ceiling. The script now sums documents separately. A
   * split like that has two ways to go wrong -- documents could leak back into `total`, or
   * the new row could quietly never fail -- so each half is proved on its own.
   */
  it('passes a realistic build carrying a large form PDF -- documents are not page weight', () => {
    const { status, out } = budgetAgainst((dir) => {
      const dist = realisticBuild(dir)
      writeFileSync(join(dist, 'assets', 'Membership Application-hash.pdf'), 'd'.repeat(500 * 1024))
    })

    expect(out, 'a 500 KiB document must not push `all assets` over its ceiling').not.toMatch(/OVER +all assets/)
    expect(out).toMatch(/OK +documents/)
    expect(status, `expected a clean exit, got ${status}:
${out}`).toBe(0)
  })

  it('FAILS when the documents themselves are over their own ceiling', () => {
    const { status, out } = budgetAgainst((dir) => {
      const dist = realisticBuild(dir)
      writeFileSync(join(dist, 'assets', 'scan-hash.pdf'), 'd'.repeat(700 * 1024))
    })

    expect(out, 'excluding documents from the total must not exempt them from any limit')
      .toMatch(/OVER +documents/)
    expect(status).toBe(1)
  })

  it('FAILS rather than passes when dist/ is absent entirely', () => {
    const { status, out } = budgetAgainst(() => {})

    expect(out).toMatch(/dist\/ does not exist/)
    expect(status, 'a missing build is the easiest way to accidentally report clean')
      .toBe(1)
  })
})
