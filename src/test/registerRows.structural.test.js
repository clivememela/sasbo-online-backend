import { readFileSync } from 'node:fs'
import { describe, expect, it } from 'vitest'

/**
 * THE REGISTER'S CARD ROWS ARE WRITTEN TWICE, AND HELD IDENTICAL HERE. `06.3`.
 *
 * `index.css` carries the phone's arrangement of the register under two conditions: a viewport
 * of 760px or less, and a register narrower than its three essential columns -- which, on a
 * desktop, only enlarged text produces (at a 125% root font with a record docked at 1280, Status
 * was cut by 162px). CSS has no way to say "@media or @container" once, so the rules are written
 * under both. Two copies drift the day one of them is edited, and the one that drifts is the
 * one nobody looks at. This fails that day.
 */

const CSS = readFileSync(new URL('../assets/styles/index.css', import.meta.url), 'utf8')

export const stripComments = (css) => css.replace(/\/\*[\s\S]*?\*\//g, '')

/** Declarations only, whitespace collapsed -- so a comment or a line break is not a difference. */
export const normalise = (body) => body.replace(/\s+/g, ' ').trim()

/**
 * The body of the first at-rule whose prelude begins with `prelude` and whose body contains
 * `mustContain`. The prelude is a prefix on purpose: index.css also has
 * `@media (max-width: 760px), (pointer: coarse)` blocks, and those must be skipped by their
 * content rather than by a spelling that would break on the next edit to either.
 */
export function atRuleBody(css, prelude, mustContain) {
  let from = 0
  for (;;) {
    const start = css.indexOf(prelude, from)
    if (start === -1) return null
    const open = css.indexOf('{', start)
    let depth = 0
    for (let i = open; i < css.length; i += 1) {
      if (css[i] === '{') depth += 1
      else if (css[i] === '}') {
        depth -= 1
        if (depth === 0) {
          const body = css.slice(open + 1, i)
          if (body.includes(mustContain)) return body
          break
        }
      }
    }
    from = start + prelude.length
  }
}

const PHONE = '@media (max-width: 760px)'
const NARROW = '@container register (max-width: 33rem)'
const bodyOf = (css, prelude) => {
  const body = atRuleBody(stripComments(css), prelude, '.register thead')
  return body === null ? null : normalise(body)
}

describe("the register's card rows", () => {
  it('the comparison convicts a drifted copy, and acquits one that differs only in comments and spacing', () => {
    const phone = `${PHONE} { .register thead { position: absolute; } .register tr[data-row] { gap: 1px 12px; } }`
    const drifted = `${NARROW} { .register thead { position: absolute; } .register tr[data-row] { gap: 2px 12px; } }`
    const respaced = `${NARROW} {\n  /* the same rules */\n  .register thead { position: absolute; }\n  .register tr[data-row] {   gap: 1px 12px; }\n}`
    // A block that is not the register's must be stepped over, not compared.
    const decoy = `${PHONE}, (pointer: coarse) { .search-text { font-size: 16px; } }\n${phone}`

    expect(bodyOf(drifted, NARROW)).not.toBe(bodyOf(phone, PHONE))
    expect(bodyOf(respaced, NARROW)).toBe(bodyOf(phone, PHONE))
    expect(bodyOf(decoy, PHONE)).toBe(bodyOf(phone, PHONE))
  })

  it('the phone block and the narrow-register block hold the same rules', () => {
    const phone = bodyOf(CSS, PHONE)
    const narrow = bodyOf(CSS, NARROW)

    expect(phone, 'the phone register block is gone or renamed, so there is nothing to compare').not.toBeNull()
    expect(narrow, 'the narrow-register block is gone: enlarged text would cut Status off again').not.toBeNull()
    expect(narrow, 'the two card-row blocks have drifted apart: edit both, or neither').toBe(phone)
  })

  it('a column marked `end` stands right in the card -- the workplaces\' R/A number, area and staff (MO24)', () => {
    // A sheet whose columns name no area of the members' fills the card's two columns in its own order (01.5).
    expect(bodyOf(CSS, PHONE)).toContain('.register td[data-end] { justify-self: end; text-align: right; }')
  })

  it('what a row opens onto is a block of its own under the card, the sheet\'s whole width (A.1, a workplace\'s visits)', () => {
    // Left a table row inside a block body, it would sit in an anonymous table as wide as its words.
    expect(bodyOf(CSS, PHONE)).toContain('.register tr[data-below], .register tr[data-below] > td { display: block; }')
  })
})

/**
 * MONTH END 02.1 -- THE FIRST SCREEN'S SHEET ON A PHONE (MO24, the stand-in review: at 390 it ran 58px past its own
 * edge and Penetration was cut). Its mock's phone measures, read where they are written: jsdom applies no media query,
 * and the widths they buy were measured in a browser. `MonthEndFigures.test.jsx` holds the class that reaches the sheet.
 */
describe("the first screen's sheet on a phone", () => {
  it('takes month-end-first-screen.html\'s measures at 760px and below: a head 8 and 12, a cell 6 and 10, the grouping 6 and wrapping, the expander 36', () => {
    const block = atRuleBody(stripComments(CSS), PHONE, '.figures-sheet')
    expect(block, 'the phone block for the first screen\'s sheet is gone').not.toBeNull()
    const rule = (selector) => {
      const at = block.indexOf(`${selector} {`)
      return at === -1 ? null : normalise(block.slice(block.indexOf('{', at) + 1, block.indexOf('}', at)))
    }
    expect(rule('.figures-sheet th[data-col]')).toBe('padding-left: 8px; padding-right: 12px;')
    expect(rule('.figures-sheet td[data-col]')).toBe('padding-left: 6px; padding-right: 10px;')
    // ...and the grouping's head wraps: on one line it was what still ran the sheet 10px past at 390, measured.
    expect(rule('.figures-sheet th[data-col="label"]')).toBe('padding-left: 6px; white-space: normal;')
    expect(rule('.figures-sheet [data-expander]')).toBe('width: 36px; padding: 0 0 0 12px;')
  })
})
