
import { describe, expect, it } from 'vitest'

import {
  assertDetectorCanary,
  assertScanIsHonest,
  FLOORS,
} from './assertNonVacuousScan.js'
import { DETECTOR_CANARY, dynamicUtilitiesIn } from './dynamicUtilities.js'
import { nonTestSources } from './sourceScan.js'

/**
 * No Tailwind utility is assembled at runtime. `05.2`.
 *
 * 🔴 THE BUG THIS EXISTS FOR PRODUCES NO SYMPTOM. Tailwind reads source text; it never runs
 * the code. `` `col-span-${span}` `` therefore emits a valid, correctly spelled class that
 * matches nothing in the stylesheet — the element renders, no error is thrown, no build
 * warning appears, and the layout is quietly not the one that was written. `LocationListing`
 * carried one from the day it was written: two header fields asked to span three columns and
 * never did, at any width, in any theme.
 *
 * ⚠️ IT SURVIVED EVERY EXISTING INSTRUMENT. The palette gate reads class names and found
 * nothing to object to — the class is a layout utility with no colour in it. The width matrix
 * measured the page at seven widths and found no overflow — a span that does not apply makes
 * the grid NARROWER, never wider. jsdom has no layout at all. It was found by diffing the
 * built stylesheet against the class the component emits, and the only durable form of that
 * check is this one: refuse to emit such a class in the first place.
 */

/** Every non-test source file under `src/`, comments blanked. */
function scan() {
  // The walk, the non-test exclusion and the read all live in `sourceScan.js`; the PATTERN
  // stays here because it is this guard's own population.
  return nonTestSources({ pattern: /\.(jsx?|mjs)$/ })
}

describe('the runtime-utility detector', () => {
  it('convicts a spliced utility and acquits everything else built the same way', () => {
    // 🔴 THE CANARY, and it is doing more work here than usual: the whole assertion below
    // is `toEqual([])`, so a detector that has stopped matching anything at all is
    // indistinguishable from a clean tree.
    assertDetectorCanary(DETECTOR_CANARY, (sample) => dynamicUtilitiesIn(sample), 'the runtime-utility scan')
  })
})

describe('no Tailwind utility is assembled at runtime', () => {
  it('every file under src emits class names Tailwind can see', () => {
    const sources = scan()

    assertScanIsHonest(sources, FLOORS.SRC_SOURCE_FILES, 'the runtime-utility scan')

    const offenders = sources.flatMap((source) =>
      dynamicUtilitiesIn(source.code).map((hit) => `${source.path}:${hit.line} ${hit.utility}`),
    )

    expect(
      offenders,
      'A Tailwind class is being built by splicing a value into it. Tailwind scans source ' +
        'text and never runs this code, so the generated stylesheet will not contain the ' +
        'class and the rule will silently do nothing. Write every class out in full — a ' +
        'lookup object keyed by the value (`const SPAN = { 3: "lg:col-span-3" }`) is the ' +
        'usual shape, and it is what `LocationListing.jsx` now uses.',
    ).toEqual([])
  })
})
