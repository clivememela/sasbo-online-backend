
import { describe, expect, it } from 'vitest'

import {
  assertDetectorCanary,
  assertScanIsHonest,
  FLOORS,
} from './assertNonVacuousScan.js'
import { nonTestSources } from './sourceScan.js'

/**
 * No `<table>` anywhere in `src` renders outside a `TableScroller`. `05.2`, contract ⑮.
 *
 * 🔴 THE COMPANION TEST IS ABOUT ONE COMPONENT; THIS IS ABOUT THE RULE.
 * `TableScroller.test.jsx` proves the `Table` PRIMITIVE cannot render its table unwrapped,
 * which is worth having and is not the same guarantee. Nothing stopped the next author
 * writing a bare `<table>` in a page — and nothing had: `Subscriptions.jsx` did exactly
 * that, and `LocationListing.jsx` did too. Both are fixed; the point of this file is that
 * the third one reddens a lane instead of shipping.
 *
 * ⚠️ MobiZone's worst responsive defect was this shape. A rule that every author must
 * remember is a rule that holds until somebody is in a hurry, and a nine-column table in a
 * 390px viewport does not announce itself on a desktop.
 *
 * 🔑 COMMENTS ARE BLANKED FIRST, and here that is not a nicety. `Recruiters.jsx:441` says
 * *"None of them was a `<table>`"* — prose recording that its four surfaces were pseudo-
 * tables — and a scan reading its own documentation would convict the one file that had
 * nothing to answer for.
 */

/** Walk the tags in order and report any `<table>` that is not inside an open scroller. */
export function unwrappedTables(code) {
  const tags = [...code.matchAll(/<TableScroller\b|<\/TableScroller>|<table\b/g)]
  const offenders = []
  let depth = 0
  for (const tag of tags) {
    if (tag[0] === '<TableScroller') depth += 1
    else if (tag[0] === '</TableScroller>') depth = Math.max(0, depth - 1)
    else if (depth === 0) offenders.push(code.slice(0, tag.index).split('\n').length)
  }
  return offenders
}

const CANARY = {
  mustConvict: [
    '<div><table><tbody /></table></div>',
    '<TableScroller><table /></TableScroller>\n<table />',
    '<TableScroller>\n<x />\n</TableScroller>\n<div><table /></div>',
  ],
  mustAcquit: [
    '<TableScroller label="x"><table><tbody /></table></TableScroller>',
    '<TableScroller>\n  <table className="w-full">\n  </table>\n</TableScroller>',
    // Nested scrollers still count as wrapped.
    '<TableScroller><div><TableScroller><table /></TableScroller></div></TableScroller>',
    // No table at all.
    '<div className="grid grid-cols-4" />',
  ],
}

function scan() {
  // The walk, the non-test exclusion and the read all live in `sourceScan.js`; the PATTERN
  // stays here because it is this guard's own population — `.jsx` only, since a `<table>` can
  // only be written in JSX. That narrower population is why this guard has its OWN floor
  // (`SRC_JSX_FILES`, 61 measured) rather than borrowing one calibrated against 84 files.
  return nonTestSources({ pattern: /\.jsx$/ })
}

describe('every table scrolls, structurally', () => {
  it('the detector sees a bare table and acquits a wrapped one', () => {
    assertDetectorCanary(CANARY, (sample) => unwrappedTables(sample), 'the table-scroller scan')
  })

  it('no page renders a table outside one', () => {
    const sources = scan()

    assertScanIsHonest(sources, FLOORS.SRC_JSX_FILES, 'the table-scroller scan')

    const offenders = sources.flatMap((source) =>
      unwrappedTables(source.code).map((line) => `${source.path}:${line}`),
    )

    expect(
      offenders,
      'A `<table>` renders outside `TableScroller`. On a phone it will push the whole page ' +
        'sideways, and the only sign on a desktop is that nothing looks wrong. Wrap it — ' +
        'or render it through the `Table` primitive, which wraps it for you (contract ⑮).',
    ).toEqual([])

    // ✅ The must-pass half. Without it, a tree with no tables at all would pass this
    // suite while proving nothing: the scan has to have had something to look at.
    const wrapped = sources.filter((source) => /<table\b/.test(source.code))
    expect(
      wrapped.length,
      'No file in `src` renders a `<table>`, so the clean result above is about an empty ' +
        'tree rather than a correct one. If the tables really have gone, this floor is ' +
        'what should be updated -- deliberately, in the commit that removed them.',
    ).toBeGreaterThanOrEqual(3)
  })
})
