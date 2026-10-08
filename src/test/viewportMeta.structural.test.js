import { describe, expect, it } from 'vitest'
import { readFileSync } from 'node:fs'
import { join } from 'node:path'

import { REPO_ROOT, readSources } from './sourceScan.js'

/**
 * THE SAFE-AREA INSETS ARE ONLY REAL WHEN THE VIEWPORT ASKS FOR THEM.
 *
 * `env(safe-area-inset-bottom)` reads 0 on every phone unless the page's viewport meta carries
 * `viewport-fit=cover`. The 2026-09-14 audit found it had never been added, although
 * seniorReview.md R-RESP-J said it had -- so the bottom-sheet padding in DateField and
 * CaptureMember's save bar did nothing, and on a phone with a home indicator a bar can sit under
 * it. No emulated capture can see this: headless Chrome has no notch, and every inset is 0 there
 * with or without the meta. So the guard is on the FACT that makes the insets work: while any
 * source asks for a safe-area inset, the viewport must enable them.
 *
 * Its must-pass: the viewport still lets a person zoom. `maximum-scale=1` or `user-scalable=no`
 * would stop iOS zooming on a focused field, which is the wrong fix for the 16px rule
 * (conventions.md §5), and fails WCAG 1.4.4.
 */
const html = readFileSync(join(REPO_ROOT, 'index.html'), 'utf8')
const viewport = (html.match(/<meta\s+name="viewport"\s+content="([^"]*)"/) || [])[1]

describe('the viewport meta', () => {
  it('exists, so the rules below read something', () => {
    expect(viewport, 'index.html has no <meta name="viewport" content="...">').toBeTruthy()
  })

  it('enables the safe-area insets while the code uses them (viewport-fit=cover)', () => {
    const users = readSources(join(REPO_ROOT, 'src'), { root: REPO_ROOT, pattern: /\.(jsx?|css)$/ })
      .filter(({ path, code }) => !/\.test\./.test(path) && /env\(\s*safe-area-inset-/.test(code))
      .map(({ path }) => path)
    expect(users.length, 'no source uses env(safe-area-inset-*) -- the scan cannot see the source, or the rule is moot').toBeGreaterThan(0)
    expect(
      viewport,
      `${users.join(', ')} ask(s) for env(safe-area-inset-*), which reads 0 on every phone without viewport-fit=cover`,
    ).toMatch(/(^|,)\s*viewport-fit\s*=\s*cover\s*(,|$)/)
  })

  it('must-pass: still lets a person zoom -- no maximum-scale=1, no user-scalable=no', () => {
    expect(viewport).toMatch(/width\s*=\s*device-width/)
    expect(viewport).not.toMatch(/user-scalable\s*=\s*(no|0)/)
    expect(viewport).not.toMatch(/maximum-scale\s*=\s*1(\.0*)?\s*(,|$)/)
  })
})
