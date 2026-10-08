import { describe, expect, it } from 'vitest'

import {
  assertDetectorCanary,
  assertScanIsHonest,
  FLOORS,
} from './assertNonVacuousScan.js'
import { blankComments, nonTestSources } from './sourceScan.js'

/**
 * `text-ink-faint` NEVER PAINTS TEXT A READER IS MEANT TO READ.
 *
 * 🔴 THE DEFECT THIS EXISTS BECAUSE OF, and it was a sentence rather than a bug. `card.css:27`
 * defines `--ink-3` as *"icons, dividers; never body text"* and puts placeholders on `--ink-2`
 * (line 165). The arc's `tokens_doc.py` -- the ONE HOME for that note, feeding both `tokens.md`
 * and the generated comment in `index.css` -- widened it to *"placeholders, icons at rest, days
 * outside the month"*. Six sites then did exactly what the comment licensed:
 *
 *   Field.jsx            placeholder:text-ink-faint   -- EVERY input in the application
 *   CaptureMember.jsx    inactive tab labels
 *   Members.jsx          inactive tab labels          -- the same class, a second site
 *   SearchableSelect.jsx the "no matches" message
 *   DateField.jsx        the weekday column headers
 *   DateField.jsx        out-of-month days, at opacity-60 on top
 *
 * Measured on rendered pixels: `ink-faint` is 3.65:1 at worst in dark and 4.15:1 on `bg-raised`
 * in light, against 1.4.3's 4.5:1. It clears 1.4.11's 3:1, so it is fine for what card.css
 * always said it was for -- icons and rules -- and fails for what the note invented.
 *
 * 🔑 WHY A GUARD AND NOT THE CORRECTED COMMENT. The comment was already there, in card.css,
 * and it was already right; it lost to a second copy of itself that was wrong. That is
 * contract ⑮ exactly -- a convention carried by prose is a convention that gets forgotten --
 * so the rule is asserted instead of stated. And the pixel sweep cannot assert it: axe judges
 * text NODES, so a placeholder (an attribute) is invisible to it, and a dropdown, a dialog and
 * a calendar are all state-gated and never opened (contract ⑤, L2). Five of the six were
 * unreachable by the 100-combination run that passed.
 *
 * WHAT IT CHECKS, AND WHAT IT DELIBERATELY CANNOT
 * For every JSX element carrying `text-ink-faint`, either
 *   - it is SELF-CLOSING (`<ChevronRight className="text-ink-faint" />`) -- an icon, or
 *   - every child is itself a JSX element (an icon-only control wrapping one icon), or
 *   - it is `aria-hidden` -- decoration, which 1.4.3 exempts as "pure decoration".
 * Anything else is text a reader is meant to read, and it is a finding.
 *
 * ⚠️ IT IS A SOURCE SCAN, SO IT IS A LOWER BOUND (the arc's standing caveat, T18). A class
 * held in a variable, composed by `cn()` from a token, or applied by a stylesheet is invisible
 * to it -- which is the same blindness that hid four controls from the 16px check at F-P34. It
 * catches the literal form, which is the form all six defects took.
 */

/** The utility, written once. A regex typo here would silently find nothing. */
const UTILITY = 'text-ink-faint'

/**
 * 🔴 THE OTHER SPELLING, AND IT HID A REAL DEFECT FOR THE WHOLE ARC (R4-F2, P9's independent review).
 *
 * Tailwind has TWO ways to colour a placeholder: the variant `placeholder:text-<colour>`, which RULE 2 below
 * catches, and the utility `placeholder-<colour>`, which contains neither `text-` nor a `:` and was invisible to
 * this guard. `GeneralForm.jsx` used the second form on all 120 roster inputs -- prompts at 3.65:1 in dark, on a
 * state-gated surface no sweep reaches -- and the guard reported the tree clean. Aimed at the FACT: a placeholder
 * is text however the class is spelt.
 */
const PLACEHOLDER_UTILITY = 'placeholder-ink-faint'

/**
 * The opening tag containing an offset, and its children up to the matching close.
 *
 * Deliberately small and deliberately not a JSX parser: it walks back to the `<` that opens
 * the tag the match sits inside, then forward to that tag's end. A parser would be the honest
 * tool for arbitrary JSX; for "is there a text child", angle-bracket balance is enough, and it
 * is auditable in a way a hand-rolled parser is not.
 */
const elementAt = (src, index) => {
  const open = src.lastIndexOf('<', index)
  if (open === -1) return null

  // Find the end of the opening tag, skipping over quoted strings and {braced} expressions
  // so a `>` inside either does not look like the end of the tag.
  let i = open + 1
  let depth = 0
  let quote = null
  for (; i < src.length; i++) {
    const c = src[i]
    if (quote) {
      if (c === quote) quote = null
      continue
    }
    if (c === '"' || c === "'" || c === '`') { quote = c; continue }
    if (c === '{') { depth++; continue }
    if (c === '}') { depth--; continue }
    if (depth === 0 && c === '>') break
  }
  if (i >= src.length) return null

  const tag = src.slice(open, i + 1)
  const selfClosing = /\/\s*>$/.test(tag)
  const name = (tag.match(/^<\s*([A-Za-z][\w.]*)/) || [])[1] ?? '?'
  if (selfClosing) return { tag, name, selfClosing, children: '' }

  /*
   * 🔴 THE CHILDREN RUN TO THE FIRST MATCHING CLOSE TAG, AND A DEPTH COUNTER HERE MADE THE
   * GUARD WEAKER RATHER THAN STRONGER.
   *
   * This was 22 lines that counted same-name nesting so a `</span>` belonging to a nested
   * span could not end the outer one early. Run against all 13 canary samples and all 17 live
   * occurrences under `src/`, it produced verdicts IDENTICAL to the three lines below — with
   * exactly one exception, and the exception went the wrong way:
   *
   *     <span className="text-ink-faint"><span>real text</span></span>
   *
   * The counter returned the full children, `hasReadableChild` then stripped the nested span
   * *with its text*, and the guard ACQUITTED an offence. Stopping at the first close tag
   * leaves `<span>real text` as the children, which convicts. That shape is now in
   * `mustConvict`, so this cannot silently regress.
   *
   * 🔑 IT ALSO CARRIED A REAL ESCAPING BUG: `name.replace('.', '\\.')` escapes only the FIRST
   * dot, so `<A.B.C>` built a regex whose later dots were wildcards. Deleting the counter
   * deletes the bug, which is the better kind of fix.
   */
  const rest = src.slice(i + 1)
  const end = rest.search(new RegExp(`</\\s*${name.replace(/\./g, '\\.')}\\s*>`))
  return { tag, name, selfClosing, children: end === -1 ? rest : rest.slice(0, end) }
}

/**
 * Is there anything in these children a reader would read?
 *
 * JSX elements, whitespace, and JSX comments are not. A bare word, or a `{expression}` that is
 * not an element, is -- `{day.short}` and `{noMatchLabel}` are precisely the two shapes that
 * were wrong here, and both are expressions rather than literals.
 */
const hasReadableChild = (children) => {
  const stripped = children
    // JSX comments: {/* ... */}
    .replace(/\{\s*\/\*[\s\S]*?\*\/\s*\}/g, '')
    // Any nested element, self-closing or paired, greedy over its own content.
    .replace(/<[A-Za-z][\s\S]*?>[\s\S]*?<\/\s*[A-Za-z][\w.]*\s*>/g, '')
    .replace(/<[A-Za-z][^>]*\/\s*>/g, '')
    .trim()
  return stripped.length > 0
}

/** Every offence this must find, and every innocent shape it must leave alone. */
const CANARY = {
  mustConvict: [
    // A tab label -- CaptureMember and Members, the two sites the class actually had.
    '<button className="border-b-2 text-ink-faint">{tab.label}</button>',
    // A message in a dropdown -- SearchableSelect's "no matches".
    '<li className="px-3 py-2 text-ink-faint" role="presentation">{noMatchLabel}</li>',
    // A calendar column header. It carries an aria-label, which is NOT an exemption: the
    // letters are on screen and 1.4.3 is about what the eye reads.
    '<span role="columnheader" aria-label={day.long} className="text-ink-faint">{day.short}</span>',
    // Bare literal text.
    '<p className="text-ink-faint">No records found</p>',
    // 🔴 NESTED, SAME-NAME, WITH TEXT INSIDE. This is the shape the deleted depth counter
    // ACQUITTED: it handed back both spans, `hasReadableChild` stripped the inner one WITH its
    // text, and nothing was left to convict. It is here so that walker can never come back.
    '<span className="text-ink-faint"><span>real text</span></span>',
    // RULE 2: a class STRING with no element anywhere near it -- `Field.jsx`'s shape, and the
    // one site that reaches every form in the application.
    "cn('bg-surface text-ink placeholder:text-ink-faint')",
    // RULE 3: the `placeholder-<colour>` UTILITY -- the spelling that hid R4-F2 on 120 inputs. Self-closing, so
    // rule 1 would acquit it, and it contains no `text-`, so rule 2 never saw it.
    '<input placeholder="Ind." className="field-text placeholder-ink-faint uppercase" />',
  ],
  mustAcquit: [
    // 🔑 THE HALF THAT CATCHES A GUARD THAT STARTED CONVICTING EVERYTHING. Without these, a
    // regex that matched any occurrence at all would pass the convict half and quietly make
    // 17 legitimate icon uses into failures -- and the fix somebody would reach for is
    // recolouring the icons, not repairing the guard.
    '<ChevronRight size={16} className="mx-2 text-ink-faint" />',
    '<MapPin aria-hidden="true" className="size-4 text-ink-faint" />',
    // An icon-only control: children, but every child is an element.
    '<button aria-label="Clear" className="text-ink-faint"><X aria-hidden="true" /></button>',
    // Decoration, marked as such. 1.4.3 exempts pure decoration and aria-hidden is how a
    // reader of this code can tell that claim is being made deliberately.
    '<span aria-hidden="true" className="text-ink-faint">•</span>',
    // The CORRECTED placeholder must not convict -- otherwise rule 2 is matching the token
    // rather than the variant, and every fix would read as a fresh offence.
    "cn('bg-surface text-ink placeholder:text-ink-muted')",
    // And the corrected UTILITY spelling likewise: rule 3 must match the colour, not the word `placeholder`.
    '<input placeholder="Ind." className="field-text placeholder:text-ink-muted" />',
    // A comment mentioning the utility is not a use of it -- the F-P29 trap, and the reason
    // this scan reads `code` (comments blanked) rather than `raw`.
    '<p className="text-ink-muted">{/* never text-ink-faint here */}Real copy</p>',
  ],
}

/**
 * Tailwind variants that address text which is ALWAYS read, wherever the class is written.
 *
 * 🔴 THIS SECOND RULE EXISTS BECAUSE THE FIRST ONE CAUGHT `placeholder:` BY ACCIDENT, AND
 * ACCIDENTALLY IS NOT GOOD ENOUGH. `Field.jsx` composes its classes in a `cn()` STRING inside a
 * plain function -- there is no enclosing JSX element anywhere near it. The element walk below
 * still "found" one, by scanning backwards to the nearest `<` in unrelated code, and reported
 *
 *     Field.jsx:121  <?> renders "); } export function controlClasses({ invalid, className } ="
 *
 * which is red for entirely the wrong reason. It would have sent the next reader hunting for a
 * broken element that does not exist -- and it meant the guard's accuracy on the single
 * highest-impact site in the application was luck rather than design.
 *
 * 🔑 SO IT IS AIMED AT THE FACT INSTEAD: a placeholder is text no matter how the class reaches
 * it, so `placeholder:text-ink-faint` is a defect on sight -- in a string, in a variable, in a
 * template literal. No element required, and nothing inferred.
 */
const ALWAYS_TEXT_VARIANTS = ['placeholder']

/** Offending sites in one source string. Both rules, and each says which one convicted it. */
const inkFaintOnText = (code) => {
  const offenders = []
  const lineOf = (at) => code.slice(0, at).split('\n').length

  // RULE 2 runs first, because it is the unambiguous one and must not be shadowed by RULE 1's
  // guess at an enclosing element.
  const claimed = new Set()
  for (const variant of ALWAYS_TEXT_VARIANTS) {
    const needle = `${variant}:${UTILITY}`
    let from = 0
    for (;;) {
      const at = code.indexOf(needle, from)
      if (at === -1) break
      from = at + needle.length
      claimed.add(at + variant.length + 1)
      offenders.push({
        line: lineOf(at),
        name: `${variant}:`,
        text: `a ${variant} is text wherever this class is written`,
      })
    }
  }

  // RULE 3: the `placeholder-<colour>` UTILITY. Unconditional, like rule 2 and for the same reason: whatever it
  // is written on, a placeholder is text somebody reads.
  {
    let at = code.indexOf(PLACEHOLDER_UTILITY)
    while (at !== -1) {
      offenders.push({
        line: lineOf(at),
        name: 'placeholder-',
        text: 'a placeholder is text, and this is the utility spelling the guard could not see',
      })
      at = code.indexOf(PLACEHOLDER_UTILITY, at + PLACEHOLDER_UTILITY.length)
    }
  }

  // RULE 1: the utility applied to a JSX element.
  let from = 0
  for (;;) {
    const at = code.indexOf(UTILITY, from)
    if (at === -1) break
    from = at + UTILITY.length
    if (claimed.has(at)) continue

    const el = elementAt(code, at)
    if (!el) continue
    // 🔑 AN UNATTRIBUTABLE MATCH IS REPORTED AS THAT, NEVER DRESSED UP AS AN ELEMENT. If the
    // backward walk could not name a tag, the class is not on an element -- it is in a string
    // or a variable, which is RULE 2's territory or a genuine blind spot. Saying so is the
    // difference between a finding and a red herring.
    if (el.name === '?') {
      offenders.push({
        line: lineOf(at),
        name: 'unattributed',
        text: 'the class is not on a JSX element -- read the line and decide',
      })
      continue
    }
    if (el.selfClosing) continue
    if (/aria-hidden\s*=\s*(["']true["']|\{true\})/.test(el.tag)) continue
    if (!hasReadableChild(el.children)) continue

    offenders.push({
      line: lineOf(at),
      name: `<${el.name}>`,
      text: el.children.trim().replace(/\s+/g, ' ').slice(0, 60),
    })
  }
  return offenders
}

function scan() {
  // The walk, the non-test exclusion and the read all live in `sourceScan.js`; the PATTERN
  // stays here because it is this guard's own population.
  return nonTestSources({ pattern: /\.jsx?$/ })
}

describe(`${UTILITY} is never text a reader is meant to read`, () => {
  // 🔑 THE SAMPLES GO THROUGH `blankComments` BECAUSE THE REAL SCAN DOES. `readSources`
  // hands back `code`, not `raw`, so a canary fed raw source is exercising a different
  // code path from the guard it is certifying -- and it caught itself doing exactly that:
  // the last acquit sample failed until this was added, because the guard sees no comments
  // and the canary was still showing it one.
  it('the detector convicts every offending shape and acquits every innocent one', () => {
    assertDetectorCanary(CANARY, (sample) => inkFaintOnText(blankComments(sample)), 'the ink-faint scan')
  })

  it('the scan reached the whole tree, and the comment blanker did not eat it', () => {
    const sources = scan()
    assertScanIsHonest(sources, FLOORS.SRC_SOURCE_FILES, 'the ink-faint scan')

    // 🔑 AND THE UTILITY MUST STILL EXIST. Rename the token without renaming it here and the
    // rule below finds zero elements, reports zero offenders, and goes green — a source-scan
    // guard failing blind, which is the failure mode this repository has already been bitten
    // by twice. Aimed at the FACT (the utility is in use), never at the spelling alone.
    const uses = sources.reduce((n, { code }) => n + code.split(UTILITY).length - 1, 0)
    expect(
      uses,
      `No occurrence of \`${UTILITY}\` anywhere in src/. Either the token was renamed and ` +
        `this guard was not, or the utility is gone — in both cases the rule below is now ` +
        `checking nothing and reporting success.`,
    ).toBeGreaterThan(5)
  })

  it('every element using it is an icon, an icon-only control, or aria-hidden', () => {
    const offenders = scan().flatMap(({ path, code }) =>
      inkFaintOnText(code).map((o) => `${path}:${o.line}  ${o.name}  ${o.text}`),
    )

    expect(
      offenders,
      `\`${UTILITY}\` is painting text somebody is meant to read. It measures 3.65:1 at ` +
        `worst in dark and 4.15:1 on bg-raised in light, against WCAG 1.4.3's 4.5:1 -- so ` +
        `this text fails AA. card.css:27 defines the token as "icons, dividers; never body ` +
        `text"; use \`text-ink-muted\`, which is what card.css:165 uses for the quietest ` +
        `text it has (7.1:1 at worst, both themes).\n\n  ${offenders.join('\n  ')}`,
    ).toEqual([])
  })
})
