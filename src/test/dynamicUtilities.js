/**
 * Tailwind utility classes assembled at runtime — which is to say, classes that do nothing.
 *
 * 🔴 THE FAILURE IS SILENT IN BOTH DIRECTIONS AND THAT IS WHY IT NEEDS A GUARD. Tailwind
 * generates CSS by scanning SOURCE TEXT for class names. It never runs the code. So
 * `` `col-span-${span}` `` puts a perfectly valid, correctly spelled class into the DOM that
 * matches no rule in the stylesheet: the element renders, nothing errors, no build warning is
 * emitted, and the layout is simply not the one that was written. The only way to see it is
 * to diff the DOM against the generated CSS, which nobody does.
 *
 * `LocationListing.jsx` carried one for the life of the file. Its header grid asked two of
 * eight fields to span three columns; `.col-span-2` was in the built stylesheet (something
 * else asks for it literally) and `.col-span-3` was not, so the two widest values — the fax
 * number and the street address — sat in a single narrow column at every width.
 *
 * 🔑 WHY A PREFIX LIST AND NOT "ANY INTERPOLATED IDENTIFIER". Interpolating into a string is
 * normal and correct nearly everywhere it appears: this repository builds DOM ids that way
 * for `aria-activedescendant` and `aria-controls` (`option-${index}`, `detail-${id}`), and
 * convicting those would make the guard something to be worked around. What is never correct
 * is interpolating into the SCALE POSITION of a known Tailwind utility. So the predicate is
 * aimed at that fact, and the list below is the fact it is aimed at.
 *
 * Interpolating a WHOLE class is fine and stays fine — `` `${base} col-span-3` `` names a
 * complete class the scanner can see. It is only splicing a value into the middle that
 * defeats it.
 */

/**
 * Tailwind utilities whose final segment is a scale value.
 *
 * ⚠️ THE `.sort()` IS INSURANCE, NOT A FIX FOR A LIVE HAZARD, and the comment used to claim
 * otherwise. It said *"longest first, so `min-w` is tested before `w`"* — which describes a real
 * class of bug and not this pattern's: the alternation is anchored by `-\$\{` immediately after
 * the name and by a boundary before it, so a short alternative cannot complete where a long one
 * should and the engine backtracks to the right one regardless of order. Checked across the whole
 * list for two utilities that can both complete at the same start offset: there are none.
 *
 * It is kept because the ORDERING is free and the anchoring is the kind of property a later edit
 * to the pattern could remove without anyone noticing — but a comment asserting a hazard the code
 * has already removed costs the next reader a regex simulation to disprove, which is what it cost
 * to write this paragraph.
 */
export const SCALE_UTILITIES = [
  'col-span', 'col-start', 'col-end', 'row-span', 'row-start', 'row-end',
  'grid-cols', 'grid-rows', 'auto-cols', 'auto-rows',
  'min-w', 'min-h', 'max-w', 'max-h', 'w', 'h', 'size',
  'px', 'py', 'pt', 'pr', 'pb', 'pl', 'ps', 'pe', 'p',
  'mx', 'my', 'mt', 'mr', 'mb', 'ml', 'ms', 'me', 'm',
  'gap-x', 'gap-y', 'gap', 'space-x', 'space-y',
  'text', 'bg', 'border', 'divide', 'ring', 'outline', 'shadow', 'accent', 'caret',
  'fill', 'stroke', 'from', 'via', 'to', 'decoration',
  'rounded', 'opacity', 'z', 'order', 'basis', 'flex', 'grow', 'shrink',
  'inset', 'top', 'right', 'bottom', 'left', 'start', 'end',
  'translate-x', 'translate-y', 'scale', 'rotate', 'skew-x', 'skew-y',
  'leading', 'tracking', 'indent', 'duration', 'delay', 'columns', 'aspect',
].sort((a, b) => b.length - a.length)

/**
 * A utility name in the scale position of a template interpolation.
 *
 * The leading boundary is what keeps `min-w-${n}` from also matching as `w`, and what keeps
 * `data-w-${n}` — an attribute, not a class — out of it entirely. Any stack of variant
 * prefixes (`sm:`, `hover:dark:`) is allowed in front, because a broken class is no less
 * broken for being conditional.
 */
const INTERPOLATED = new RegExp(
  String.raw`(?:^|[\s"'\`{(\[])((?:[a-z][a-z0-9-]*:)*)(` +
    SCALE_UTILITIES.join('|') +
    String.raw`)-\$\{`,
  'g',
)

/** Every runtime-assembled utility in `code`, with the line it sits on. */
export function dynamicUtilitiesIn(code) {
  const hits = []
  for (const match of code.matchAll(INTERPOLATED)) {
    hits.push({
      utility: `${match[1]}${match[2]}-\${…}`,
      line: code.slice(0, match.index).split('\n').length,
    })
  }
  return hits
}

/**
 * 🔴 THE DETECTOR'S OWN TEST FIXTURE. A regex with one bad escape matches nothing, convicts
 * nothing, and reports every file clean — which is also exactly what a clean tree looks
 * like. This arc has now met that shape twelve times, so no scan below is believed until
 * this pair passes.
 */
export const DETECTOR_CANARY = {
  mustConvict: [
    'className={`flex gap-1.5 col-span-${span}`}',
    'const cls = `grid-cols-${count}`',
    '<div className={`w-${size} p-2`} />',
    'className={`sm:col-span-${n}`}',
    'className={`hover:dark:bg-${tone}`}',
    'clsx(`max-w-${width}`)',
    "cn('flex', `mt-${gap}`)",
  ],
  mustAcquit: [
    // DOM ids, which this repository builds this way on purpose (ARIA relationships).
    'id={`option-${index}`}',
    'aria-controls={`detail-${row.id}`}',
    'const key = `member-${memNo}`',
    // Whole classes the scanner can see, however they are composed.
    'className="col-span-3 lg:col-span-3"',
    'className={`${base} col-span-3`}',
    'className={`flex gap-2 ${isOpen ? "col-span-3" : "col-span-1"}`}',
    // Not classes at all.
    'data-w-${n}',
    'fetch(`/api/members/${id}`)',
    'className="text-ink bg-surface"',
  ],
}
