import { readFileSync } from 'node:fs'
import { join } from 'node:path'

import { describe, expect, it } from 'vitest'

import { assertNonVacuousScan } from './assertNonVacuousScan.js'
import { REPO_ROOT } from './sourceScan.js'

/**
 * 🔴 R5-F1 (P9's independent review): PRINTING FROM THE DARK THEME STILL PRINTED DARK-THEME INK.
 *
 * F-P37 fixed the grounds and the `ink*` family -- *"a dark-mode user was printing invisible ink"* -- and stopped
 * there. Everything else the `.dark` rule sets survived into `@media print`: **50 of the 59 tokens the dark theme
 * declares**, measured, which is wider than the review's own list. Among them `--color-brand-ink` (the register's
 * membership numbers), every `--color-status-*` text (every StatusChip), `--color-accent-ink` (the gold section
 * labels), `--color-head-ink` and `--color-nav-icon` (table heads), and the whole metal-card set. Paper drops
 * backgrounds, so all of it printed near-white on white. Print is live, by the owner's ruling of 2026-09-09.
 *
 * 🔑 THE PREDICATE IS AIMED AT THE FACT, NOT AT A LIST SOMEBODY HAS TO MAINTAIN: **no custom property the dark theme
 * sets may survive into print.** A token added to `.dark` tomorrow and forgotten in the print block reddens this
 * tomorrow. A guard listing today's token names would have gone green on exactly the ones that were wrong.
 *
 * ⚠️ IT IS A STRUCTURAL SCAN, so it proves the declarations exist, not what they render. The rendered half was
 * measured once, in Chrome under print media, and the numbers are in the batch's commit message.
 */

const CSS = readFileSync(join(REPO_ROOT, 'src/assets/styles/index.css'), 'utf8')

/** Comments blanked, length preserved, so every offset below still points at the real file. */
const blankComments = (css) => css.replace(/\/\*[\s\S]*?\*\//g, (m) => ' '.repeat(m.length))

/**
 * Every innermost `{ ... }` block, with the selector text that precedes it.
 *
 * ⚠️ THE FIRST VERSION OF THIS ANCHORED EACH RULE TO THE PRECEDING `}` AND FOUND EXACTLY ONE `.dark` BLOCK OUT OF
 * FOUR -- the three that follow a comment were invisible to it, and the guard would have certified the file on a
 * population of one. The floor below is what caught it, and it is why there is a floor.
 */
const blocks = (css) => [...blankComments(css).matchAll(/([^{}]*)\{([^{}]*)\}/g)].map((m) => ({
  at: m.index,
  selector: (m[1].trim().split('\n').pop() ?? '').trim(),
  body: m[2],
}))

const propertiesIn = (body) => [...body.matchAll(/(--[\w-]+)\s*:/g)].map((m) => m[1])

/** The `@media print` block: from its at-rule to the closing brace in column 0. */
const printBlock = (css) => {
  const at = css.indexOf('@media print')
  expect(at, 'there is no @media print block at all').toBeGreaterThan(-1)
  return { at, text: css.slice(at, css.indexOf('\n}', css.indexOf('{', at))) }
}

/** The tokens `.dark` sets, anywhere but inside the print block itself -- where resetting them is the fix. */
const darkTokens = (css) => {
  const print = printBlock(css)
  const inDark = blocks(css)
    .filter((b) => b.selector.includes('.dark') && !(b.at >= print.at && b.at < print.at + print.text.length))
    .flatMap((b) => propertiesIn(b.body))
  return [...new Set(inDark)]
}

describe('printing never inherits the dark theme (R5-F1)', () => {
  it('every custom property the dark theme sets is reset for paper', () => {
    const tokens = darkTokens(CSS)
    // The shared non-vacuity canary, not a bare threshold: it carries this repository's one message about what a
    // low count MEANS (fix the walker; never lower the floor), which is the sentence that stops the next person
    // lowering it. `src/test/floors.js` names the bare-threshold habit as the defect it exists to end.
    assertNonVacuousScan(tokens, 40, 'the dark-theme token scan')

    const printed = printBlock(CSS).text
    const survivors = tokens.filter((token) => !printed.includes(`${token}:`))
    expect(
      survivors,
      'these dark-theme tokens survive into print, so a page printed from the dark theme carries them: ' +
        `${survivors.join(', ')}. Paper drops backgrounds, so dark ink prints near-white on white.`,
    ).toEqual([])
  })

  it('and the print block declares the light colour scheme, so controls are not drawn for a dark screen', () => {
    expect(printBlock(CSS).text).toMatch(/color-scheme:\s*light/)
  })

  it('the canary: the scan tells a reset token from a surviving one, and sees a rule that follows a comment', () => {
    const sample = [
      '/* a comment, which is what hid three of the four real .dark rules from the first parser */',
      '.dark { --color-brand-ink: oklch(84.1% 0.091 206); --color-ink: #fff; }',
      '@media print {',
      '  :root, :root.dark { color-scheme: light; --color-ink: #111111; }',
      '}',
    ].join('\n')

    const tokens = darkTokens(sample)
    expect(tokens, 'a .dark rule preceded by a comment must still be seen').toEqual(['--color-brand-ink', '--color-ink'])

    const printed = printBlock(sample).text
    expect(tokens.filter((t) => !printed.includes(`${t}:`))).toEqual(['--color-brand-ink'])
  })
})
