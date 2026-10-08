/**
 * Finding a Tailwind PALETTE colour written straight into a class name.
 *
 * 🔴 WHAT THIS IS FOR. P1 built a token layer so that a component never learns a hue --
 * it asks for a ROLE (`bg-surface`, `text-ink-muted`, `border-line`) and the theme
 * decides. Every `bg-gray-800` or `text-cyan-700` left in the tree is a component that
 * learned a hue anyway, and it is invisible to every other check in this suite: it
 * renders, it passes axe, it looks approximately right in the theme it was written for,
 * and it silently ignores the direction P2 locked. P4's Definition of Done says "no
 * literal colour utilities anywhere in src, grep-proved". This is that grep.
 *
 * 🔑 WHY A FAMILY-AND-SHADE PATTERN AND NOT A LIST OF STRINGS. The offenders arrive in
 * variants -- `dark:bg-gray-700`, `hover:text-gray-200`, `lg:border-cyan-500/40` -- and
 * a list of literals misses every one it was not told about, while reporting green.
 * Matching `<head>-<family>-<shade>` catches the shape instead of the instance, so a
 * NEW literal nobody predicted is caught the day it lands.
 *
 * ⚠️ IT CANNOT COLLIDE WITH A TOKEN, and that is checked rather than assumed. Every
 * token this repository defines is a ROLE name -- bg, surface, ink, line, brand, accent,
 * status-*, on-brand -- and none of them is spelled like a Tailwind palette family.
 * `paletteLiterals.test.js` asserts that against the real `@theme` block, so the day
 * somebody adds `--color-slate-500` the guard says so instead of quietly convicting it.
 */

/**
 * Tailwind's built-in palette families. Written out rather than derived: deriving them
 * from the installed Tailwind would make this guard agree with whatever Tailwind ships
 * next, and the point is to catch what a HUMAN typed, which is this vocabulary.
 */
export const PALETTE_FAMILIES = Object.freeze([
  'slate', 'gray', 'zinc', 'neutral', 'stone',
  'red', 'orange', 'amber', 'yellow', 'lime', 'green', 'emerald', 'teal', 'cyan',
  'sky', 'blue', 'indigo', 'violet', 'purple', 'fuchsia', 'pink', 'rose',
])

/**
 * The utility heads that take a colour.
 *
 * `text-shadow` is in the list DELIBERATELY and it is not hypothetical: this codebase
 * shipped `text-shadow-cyan-500` and `dark:text-shadow-white` on two components, setting
 * a shadow COLOUR with no shadow to colour. They rendered nothing at all. A head list
 * that stopped at `text` would have matched `text-shadow-cyan-500` as `text-` plus the
 * family `shadow`, found no such family, and passed.
 */
const HEADS = [
  'bg', 'text-shadow', 'text', 'border', 'ring-offset', 'ring', 'divide', 'outline',
  'decoration', 'shadow', 'accent', 'caret', 'fill', 'stroke', 'placeholder',
  'from', 'via', 'to',
].join('|')

const FAMILIES = PALETTE_FAMILIES.join('|')

/**
 * A palette literal, with any number of variant prefixes in front of it.
 *
 * `(?:[a-z-]+:)*` eats `dark:`, `hover:`, `md:`, `group-hover:` and any stack of them.
 * `(?:\/\d+)?` eats an opacity suffix. The shade is 2-3 digits, which is 50 through 950.
 */
const LITERAL = new RegExp(
  String.raw`(?<![\w-])((?:[a-z-]+:)*(?:${HEADS})-(?:(?:${FAMILIES})-\d{2,3}|white|black))(?:\/\d{1,3})?(?![\w-])`,
  'g',
)

/**
 * Every palette literal in one file's (comment-blanked) source, with its line number.
 *
 * Takes blanked code, never raw: this module's own doc comment above names
 * `bg-gray-800` and `text-cyan-700` in prose, and a scan that reads comments would
 * convict the file explaining the rule -- and would go green the day somebody deleted
 * the explanation. That is the same failure `sourceScan.js` records for its blanker.
 */
export function paletteLiteralsIn(code) {
  const found = []
  const lines = code.split('\n')

  lines.forEach((line, index) => {
    for (const match of line.matchAll(LITERAL)) {
      found.push({ line: index + 1, literal: match[1] })
    }
  })

  return found
}

/**
 * Every ROLE the token layer defines, as a Tailwind utility suffix.
 *
 * Derived from `index.css`'s `@theme` block by `paletteLiterals.test.js`, never listed
 * here: a hand-typed copy of a token list is a second source of truth, and this one exists
 * precisely to catch a mismatch between source and tokens.
 */

/**
 * A `dark:` variant sitting on a TOKEN utility -- `dark:text-on-brand`, `dark:bg-surface`.
 *
 * 🔴 IT IS ALWAYS REDUNDANT AND SOMETIMES WRONG, AND NO OTHER CHECK HERE CAN SEE IT.
 * A token already carries both themes: `--color-ink` is dark ink in light mode and light
 * ink in dark mode, by definition. Writing `text-ink dark:text-on-brand` says "and in dark
 * mode paint it white instead", which either does nothing (if it agrees) or silently
 * overrides the theme (if it does not).
 *
 * 🔑 `paletteLiteralsIn` REPORTS IT CLEAN, because `on-brand` is a token and the whole
 * point of that scan is that tokens are the good answer. So a mechanical migration that
 * half-translates `text-gray-900 dark:text-white` into `text-ink dark:text-on-brand`
 * passes the colour gate while leaving a theme override behind. Found exactly that way in
 * `04.3`: four sites, from a light/dark PAIR whose two halves were too far apart in the
 * class string for the pair rule to match, so the singles rule translated each half on its
 * own.
 *
 * ⚠️ A `dark:` on a PALETTE literal is not this function's business -- that is already
 * a literal and `paletteLiteralsIn` convicts it.
 */
export function darkTwinsOnTokens(code, tokenRoles) {
  if (!tokenRoles.length) return []

  // Longest first, so `status-active-bg` is matched before `status-active`.
  const roles = [...tokenRoles].sort((a, b) => b.length - a.length).join('|')
  const pattern = new RegExp(
    String.raw`(?<![\w-])((?:[a-z-]+:)*dark:(?:[a-z-]+:)*(?:${HEADS})-(?:${roles}))(?![\w-])`,
    'g',
  )

  const found = []
  code.split('\n').forEach((line, index) => {
    for (const match of line.matchAll(pattern)) {
      found.push({ line: index + 1, utility: match[1] })
    }
  })
  return found
}

/**
 * A string the detector MUST convict, and one it MUST NOT.
 *
 * 🔑 THIS IS THE CANARY, and it is exported rather than inlined in the test so the
 * shape is part of the module the rest of the suite imports. A detector whose regex
 * stops matching -- one bad escape is enough -- finds nothing, reports every file
 * clean, and passes. That is this arc's recurring species: a check that cannot tell its
 * subject from nothing.
 */
export const DETECTOR_CANARY = Object.freeze({
  mustConvict: [
    'bg-gray-800',
    'dark:bg-gray-700',
    'hover:text-cyan-400',
    'lg:dark:border-slate-200/40',
    'text-shadow-cyan-500',
    'dark:text-shadow-white',
    'bg-white',
    'text-black',
    'ring-red-500',
    'from-blue-50',
  ],
  mustAcquit: [
    'bg-bg',
    'bg-bg-raised',
    'bg-surface',
    'bg-surface-2',
    'text-ink',
    'text-ink-muted',
    'text-ink-faint',
    'text-nav-ink',
    'border-line',
    'border-line-strong',
    'bg-brand',
    'text-brand-ink',
    'bg-brand-soft',
    'ring-brand-ring',
    'text-accent',
    'bg-status-active-bg',
    'text-status-suspended',
    'text-on-brand',
    'bg-transparent',
    'border-transparent',
    'text-current',
    // Not a colour at all: a numeric utility that merely ends in digits.
    'grid-cols-5',
    'gap-4',
    'z-50',
    'text-2xl',
    'max-w-6xl',
    'duration-300',
  ],
})
