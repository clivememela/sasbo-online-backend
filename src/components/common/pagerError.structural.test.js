import { describe, expect, it } from 'vitest'

import { assertDetectorCanary, assertScanIsHonest, FLOORS } from '../../test/assertNonVacuousScan.js'
import { nonTestSources } from '../../test/sourceScan.js'

/**
 * Every `<Pager>` says whether its list FAILED. `06.3`, K1, contract ⑮.
 *
 * 🔴 `Pager.test.jsx` proves the component can tell a failed list from an empty one. It cannot
 * make a page ask it to: `error` defaults to `false`, so a pager whose page forgets to pass it
 * goes back to printing "No members match" under "The register could not be reached" -- which is
 * exactly what all four pagers in the application did until this unit. The fifth pager should
 * redden a lane, not ship.
 *
 * 🔑 THE OPENING TAG IS WALKED, NOT MATCHED. The register's own pager carries
 * `hint={filteredMembers.length > 0 ? (<KeyHints … />) : null}` -- a `>` and a `/>` inside an
 * expression -- so "everything up to the first `>`" stops in the middle of a comparison, and an
 * `error=` inside that nested element would acquit a pager that passes none of its own.
 */

/** The `<Pager …>` opening tags in `code` that pass no top-level `error` attribute, as line numbers. */
export function pagersWithoutError(code) {
  const offenders = []
  for (const match of code.matchAll(/<Pager\b/g)) {
    let depth = 0
    let topLevel = ''
    for (let i = match.index + match[0].length; i < code.length; i += 1) {
      const ch = code[i]
      if (ch === '{') depth += 1
      else if (ch === '}') depth -= 1
      else if (depth === 0 && ch === '>') break
      if (depth === 0 && ch !== '}') topLevel += ch
    }
    if (!/(^|\s)error=/.test(topLevel)) offenders.push(code.slice(0, match.index).split('\n').length)
  }
  return offenders
}

const CANARY = {
  mustConvict: [
    '<Pager count={0} noun="members" />',
    // The `error=` belongs to a nested element inside an expression, not to the pager.
    '<Pager count={0} hint={n > 0 ? (<KeyHints error={1} />) : null} />',
    '<Pager\n  page={page}\n  loading={loading}\n/>',
  ],
  mustAcquit: [
    '<Pager count={0} error={failed} />',
    '<Pager\n  count={rows.length}\n  hint={rows.length > 0 ? (<KeyHints hints={[]} />) : null}\n  error={state === "error"}\n/>',
    // No pager at all.
    '<Table rows={rows} />',
  ],
}

describe('every pager knows whether its list failed', () => {
  it('the detector convicts a pager with no error and acquits one with it', () => {
    assertDetectorCanary(CANARY, (sample) => pagersWithoutError(sample), 'the pager-error scan')
  })

  it('no page renders a pager that cannot say its list failed', () => {
    const sources = nonTestSources({ pattern: /\.jsx$/ })
    assertScanIsHonest(sources, FLOORS.SRC_JSX_FILES, 'the pager-error scan')

    const usages = sources.filter((source) => /<Pager\b/.test(source.code))
    expect(usages.length, 'no <Pager> found anywhere -- the scan is not seeing the pages').toBeGreaterThan(0)

    const offenders = usages.flatMap((source) =>
      pagersWithoutError(source.code).map((line) => `${source.path}:${line}`),
    )
    expect(
      offenders,
      'These pagers pass no `error`, so under a failed list they will say nothing matched -- ' +
        'A27\'s confident wrong answer in the sheet\'s foot. Pass `error={…}` from the same state ' +
        'the table uses.',
    ).toEqual([])
  })
})
