import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'

import { describe, expect, it } from 'vitest'

/**
 * 🔴 THE MEMBERSHIP CARD'S TEXT MEETS AA ON ITS OWN METAL, IN BOTH THEMES (`07.3`).
 *
 * THE DEFECT THIS EXISTS BECAUSE OF. The card is the thesis of the design, and the one surface
 * the palette is allowed to be material -- a 135° gradient from `metal-a` through a LIGHTER
 * `metal-b` to `metal-c`, a gloss over the top-left. Every text on it was chosen against the
 * dark ends of that gradient. P7's pixel sampler, measuring behind the glyphs of the real card,
 * read the secondary engraving at 2.5–3.2:1, the status chip at 2.6–3.0:1 and the gold name at
 * 3.1:1 where they cross the lighter middle -- against 1.4.3's 4.5:1. axe could not judge any
 * of them (text on a gradient is "incomplete" to axe), which is how five contrast gates passed.
 *
 * WHAT IT CHECKS. Each card text against the LIGHTEST backdrop it can sit on, flat:
 *   - the engraving, the secondary engraving and the gold name: `metal-b` with the gloss laid
 *     over it at full strength -- stricter than anywhere on the real card;
 *   - the status chip: `metal-b` under the chip's own fill. Not the gloss: the gloss is a radial
 *     from 18% across the top edge that is transparent by 52% of the card's width, and the chip
 *     is pinned to the top-RIGHT corner.
 * The pixel sweep (`tools/surfaces.mjs`) is the measurement on the rendered card; this is the
 * floor under the tokens, so a regenerated or re-tuned palette cannot quietly undo it.
 */

const AA = 4.5

const css = readFileSync(fileURLToPath(new URL('../assets/styles/index.css', import.meta.url)), 'utf8')

/** The declarations of the first block that opens with exactly `opener`. */
const block = (source, opener) => {
  const lines = source.split(/\r?\n/)
  const start = lines.findIndex((line) => line === opener)
  if (start === -1) return null
  const end = lines.findIndex((line, i) => i > start && line === '}')
  return Object.fromEntries(
    [...lines.slice(start + 1, end).join('\n').matchAll(/(--card-[\w-]+)\s*:\s*([^;]+);/g)].map(([, name, value]) => [name, value.trim()]),
  )
}

const encode = (v) => (v <= 0.0031308 ? 12.92 * v : 1.055 * v ** (1 / 2.4) - 0.055)

/** A colour token as sRGB 0–255 plus alpha: `oklch(L% C H)`, `rgba(r, g, b, a)` or `#RRGGBB`. */
const parse = (value) => {
  let m = value.match(/^oklch\(\s*([\d.]+)%\s+([\d.]+)\s+([\d.]+)\s*\)$/)
  if (m) {
    const [L, C, H] = [Number(m[1]) / 100, Number(m[2]), (Number(m[3]) * Math.PI) / 180]
    const a = C * Math.cos(H)
    const b = C * Math.sin(H)
    const l = (L + 0.3963377774 * a + 0.2158037573 * b) ** 3
    const mm = (L - 0.1055613458 * a - 0.0638541728 * b) ** 3
    const s = (L - 0.0894841775 * a - 1.291485548 * b) ** 3
    const rgb = [
      4.0767416621 * l - 3.3077115913 * mm + 0.2309699292 * s,
      -1.2684380046 * l + 2.6097574011 * mm - 0.3413193965 * s,
      -0.0041960863 * l - 0.7034186147 * mm + 1.707614701 * s,
    ].map((v) => encode(Math.min(1, Math.max(0, v))) * 255)
    return [...rgb, 1]
  }
  m = value.match(/^rgba\(\s*([\d.]+)\s*,\s*([\d.]+)\s*,\s*([\d.]+)\s*,\s*([\d.]+)\s*\)$/)
  if (m) return m.slice(1).map(Number)
  m = value.match(/^#([0-9a-f]{6})$/i)
  if (m) {
    const n = parseInt(m[1], 16)
    return [(n >> 16) & 255, (n >> 8) & 255, n & 255, 1]
  }
  throw new Error(`a card token this guard cannot read: ${value}`)
}

const over = ([r, g, b, a], [br, bg, bb]) => [r * a + br * (1 - a), g * a + bg * (1 - a), b * a + bb * (1 - a)]
const luminance = (rgb) => {
  const [r, g, b] = rgb.map((c) => {
    const s = c / 255
    return s <= 0.04045 ? s / 12.92 : ((s + 0.055) / 1.055) ** 2.4
  })
  return 0.2126 * r + 0.7152 * g + 0.0722 * b
}
const ratio = (x, y) => {
  const [a, b] = [luminance(x), luminance(y)]
  return (Math.max(a, b) + 0.05) / (Math.min(a, b) + 0.05)
}

const NEEDED = ['--card-metal-b', '--card-engrave', '--card-engrave-gold', '--card-chip-ink', '--card-text-2', '--card-chip-bg', '--card-gloss']

/** Every card text below AA on its lightest backdrop, as readable lines. */
const cardFailures = (tokens) => {
  const t = Object.fromEntries(NEEDED.map((name) => [name, parse(tokens[name])]))
  const lit = over(t['--card-gloss'], t['--card-metal-b'])
  const chip = over(t['--card-chip-bg'], t['--card-metal-b'])
  return [
    ['the engraving', over(t['--card-engrave'], lit), lit],
    ['the secondary engraving', over(t['--card-text-2'], lit), lit],
    ['the gold name', over(t['--card-engrave-gold'], lit), lit],
    ['the status chip', over(t['--card-chip-ink'], chip), chip],
  ]
    .map(([what, fg, bg]) => [what, ratio(fg, bg)])
    .filter(([, r]) => r < AA)
    .map(([what, r]) => `${what}: ${r.toFixed(2)}:1`)
}

/** The tokens as they shipped before P7 measured the card -- the shape this guard must convict. */
const AS_SHIPPED_LIGHT = {
  '--card-metal-b': 'oklch(53.2% 0.088 209)', '--card-engrave': 'oklch(97.9% 0.008 197)',
  '--card-engrave-gold': 'oklch(88.8% 0.102 93)', '--card-chip-ink': 'oklch(88.8% 0.102 93)',
  '--card-text-2': 'rgba(242, 250, 250, 0.66)', '--card-chip-bg': 'rgba(255, 255, 255, 0.1)', '--card-gloss': 'rgba(255, 255, 255, 0.16)',
}

describe("the membership card's text meets AA on its own metal", () => {
  const themes = { light: block(css, '@theme {'), dark: block(css, '.dark {') }

  it('the guard reads every card token it needs, in both themes', () => {
    for (const [theme, tokens] of Object.entries(themes)) {
      expect(tokens, `no \`${theme === 'light' ? '@theme {' : '.dark {'}\` block found in index.css`).not.toBeNull()
      expect(NEEDED.filter((name) => !tokens[name]), `card tokens missing from the ${theme} block`).toEqual([])
    }
  })

  it('convicts the card as it shipped -- the guard can fail', () => {
    expect(cardFailures(AS_SHIPPED_LIGHT).length).toBeGreaterThan(0)
  })

  it.each(['light', 'dark'])('%s: every card text is 4.5:1 or better', (theme) => {
    expect(cardFailures(themes[theme]), `card text below AA in ${theme}`).toEqual([])
  })
})
