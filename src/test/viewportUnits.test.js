
import { describe, expect, it } from 'vitest'

import {
  assertScanIsHonest,
  FLOORS,
} from './assertNonVacuousScan.js'
import { nonTestSources } from './sourceScan.js'

/**
 * No `vh` in `src`. `05.1`, and conventions §6.
 *
 * 🔴 `100vh` IS NOT THE HEIGHT OF A PHONE SCREEN. It is the viewport as it would be with the
 * browser chrome retracted, which on iOS Safari and Chrome Android is taller than what the
 * user can actually see. A dialog capped at `max-h-[90vh]` therefore puts its own action bar
 * below the fold on the exact devices where reaching it matters most — and it looks correct
 * on every desktop and in every screenshot. `dvh` is the same measurement taken against the
 * viewport that is really there.
 *
 * 🔑 COMMENTS ARE BLANKED, AND THAT IS LOAD-BEARING HERE. Six files carry `100vh` and
 * `max-h-[90vh]` in PROSE — comments written to record why the unit was changed. A scan that
 * reads its own documentation as the defect punishes writing any, which is [F-P29] exactly:
 * the raw-control metric counted the comments explaining its own fixes and reported the
 * members lane as getting worse. `readSources` blanks comments with a character walk before
 * this scan ever sees a file, and `assertBlankingPreservedCode` proves the blanker did not
 * simply empty them.
 */

/**
 * The dynamic units are the correct answers and must not be convicted: `dvh`/`svh`/`lvh`,
 * and Tailwind's `h-dvh`/`min-h-dvh`.
 *
 * ⚠️ `h-screen` IS `100vh` WEARING A TAILWIND NAME, and the first version of this guard did
 * not see it. It matched `[90vh]` and passed a tree containing five `h-screen`/`min-h-screen`
 * sites — including the application SHELL, which was therefore taller than the phone window
 * it was drawn in. A guard aimed at the notation rather than at the fact catches whichever
 * spelling it happened to be written against.
 *
 * `w-screen` is here for the neighbouring reason: it is `100vw`, which INCLUDES the
 * scrollbar's width, so on any desktop with a classic scrollbar it is wider than the
 * viewport — the single most common cause of the horizontal overflow `05.1` measures.
 */
/*
 * 🔑 DECLARED WITHOUT `/g`, AND THAT IS WHY IT CAN BE USED DIRECTLY.
 *
 * It used to carry the flag, which forced every consumer to write `new RegExp(X.source)` —
 * three times in this file — because a global regex keeps `lastIndex` between `.test()` calls
 * and so reports a match on one sample and a miss on the next. That re-wrapping is
 * flag-laundering a reader has to decode, and it is one "simplification" away from becoming the
 * intermittent bug it was avoiding: anybody who tidies `new RegExp(X.source).test(s)` down to
 * `X.test(s)` reintroduces the statefulness silently.
 *
 * Without the flag `.test()` is stateless, and the one place that needs every match uses
 * `matchAll`, which per spec CLONES the regex and cannot leak state either — the same shape
 * `dynamicUtilities.js` already uses.
 */
const BARE_VH = /(?<![a-z])(?<!\d)\d[\d.]*vh\b|(?<![-\w])(?:min-|max-)?[hw]-screen\b/

/**
 * 🔴 THE CANARY. This assertion is `toEqual([])`, so a regex that has stopped matching is
 * indistinguishable from a clean tree.
 */
const CANARY = {
  mustConvict: [
    'h-[100vh]', 'className="max-h-[90vh]"', 'height: 100vh;',
    'className="bg-bg h-screen"', 'flex min-h-screen flex-col', 'w-screen overflow-x-hidden',
  ],
  mustAcquit: [
    'max-h-[90dvh]', 'h-[100dvh]', 'max-h-[60svh]', 'min-h-[50lvh]',
    'className="bg-bg h-dvh"', 'min-h-dvh', 'max-h-96',
    // Not viewport units at all.
    'const vh = 12', 'archvhelper', 'hover-hover:h-screenshot',
  ],
}

function scan() {
  // The walk, the non-test exclusion and the read all live in `sourceScan.js`; the PATTERN
  // stays here because it is this guard's own population — `.css` included, because a bare
  // `vh` in a stylesheet is exactly what this rule is about.
  return nonTestSources({ pattern: /\.(jsx?|mjs|css)$/ })
}

describe('no static viewport-height units in src', () => {
  it('the detector sees a bare vh and lets every dynamic unit through', () => {
    expect(
      CANARY.mustConvict.filter((sample) => !BARE_VH.test(sample)),
      'The detector stopped recognising a bare `vh`. The scan below is now vacuous.',
    ).toEqual([])

    expect(
      CANARY.mustAcquit.filter((sample) => BARE_VH.test(sample)),
      'The detector convicted `dvh`/`svh`/`lvh` — which are the FIX — or something that ' +
        'merely contains the letters. Re-aim the predicate; do not rewrite the code.',
    ).toEqual([])
  })

  it('no source file carries one', () => {
    const sources = scan()

    assertScanIsHonest(sources, FLOORS.SRC_STYLEABLE_FILES, 'the viewport-unit scan')

    const offenders = sources.flatMap((source) => {
      const hits = [...source.code.matchAll(new RegExp(BARE_VH, 'g'))].map((m) => m[0])
      return hits.map((hit) => `${source.path} ${hit}`)
    })

    expect(
      offenders,
      'A static `vh` unit is back in `src`. On a phone `100vh` is the viewport WITHOUT the ' +
        'browser chrome that is actually on screen, so anything capped in `vh` puts its ' +
        'bottom edge — usually the Save button — below the fold on exactly the devices ' +
        'where that matters. Use `dvh` (conventions §6).',
    ).toEqual([])
  })
})
