---
name: SASBO Online
description: A members' register for union administrators, where every member is a card and the register is the wallet that hands it to you.
colors:
  bg: "oklch(94.4% 0.007 248)"
  bg-raised: "oklch(91.3% 0.012 252)"
  surface: "oklch(100.0% 0.000 90)"
  surface-2: "oklch(95.7% 0.006 255)"
  ink: "oklch(17.9% 0.020 252)"
  ink-muted: "oklch(40.0% 0.025 251)"
  ink-faint: "oklch(52.6% 0.028 252)"
  nav-ink: "oklch(33.2% 0.027 253)"
  head-ink: "oklch(17.9% 0.020 252)"
  line: "oklch(87.9% 0.013 252)"
  line-strong: "oklch(77.8% 0.022 253)"
  brand: "oklch(51.3% 0.086 210)"
  brand-hover: "oklch(44.6% 0.075 210)"
  brand-ink: "oklch(41.9% 0.069 207)"
  brand-soft: "oklch(93.2% 0.023 207)"
  brand-ring: "rgba(11, 116, 130, 0.3)"
  on-brand: "oklch(100.0% 0.000 90)"
  accent: "oklch(68.0% 0.110 84)"
  accent-ink: "oklch(45.8% 0.083 87)"
  accent-soft: "oklch(94.8% 0.029 88)"
  status-active: "oklch(41.9% 0.069 207)"
  status-active-bg: "oklch(92.5% 0.024 207)"
  status-active-dot: "oklch(51.3% 0.086 210)"
  status-pending: "oklch(47.7% 0.100 72)"
  status-pending-bg: "oklch(93.1% 0.051 87)"
  status-pending-dot: "oklch(66.8% 0.141 72)"
  status-suspended: "oklch(43.6% 0.152 26)"
  status-suspended-bg: "oklch(91.3% 0.033 18)"
  status-suspended-dot: "oklch(56.3% 0.179 25)"
  status-resigned: "oklch(42.9% 0.030 260)"
  status-resigned-bg: "oklch(93.2% 0.010 253)"
  status-resigned-dot: "oklch(63.0% 0.026 256)"
  card-metal-a: "oklch(32.6% 0.052 215)"
  card-metal-b: "oklch(40.7% 0.067 211)"
  card-metal-c: "oklch(37.7% 0.061 211)"
  card-engrave: "oklch(97.9% 0.008 197)"
  card-engrave-gold: "oklch(88.8% 0.102 93)"
typography:
  display:
    fontFamily: "Marcellus, Georgia, serif"
    fontSize: "1.5rem"
    fontWeight: 400
    lineHeight: 1.12
    letterSpacing: "0.05em"
  headline:
    fontFamily: "Schibsted Grotesk, system-ui, -apple-system, Segoe UI, sans-serif"
    fontSize: "1.625rem"
    fontWeight: 600
    lineHeight: 1.15
    letterSpacing: "-0.02em"
  title:
    fontFamily: "Schibsted Grotesk, system-ui, -apple-system, Segoe UI, sans-serif"
    fontSize: "1rem"
    fontWeight: 600
    lineHeight: 1.3
    letterSpacing: "normal"
  body:
    fontFamily: "Schibsted Grotesk, system-ui, -apple-system, Segoe UI, sans-serif"
    fontSize: "0.875rem"
    fontWeight: 400
    lineHeight: 1.5
    letterSpacing: "normal"
  label:
    fontFamily: "Schibsted Grotesk, system-ui, -apple-system, Segoe UI, sans-serif"
    fontSize: "0.71875rem"
    fontWeight: 600
    lineHeight: 1.2
    letterSpacing: "0.08em"
  figure:
    fontFamily: "Schibsted Grotesk, system-ui, -apple-system, Segoe UI, sans-serif"
    fontSize: "1.5rem"
    fontWeight: 600
    lineHeight: 1.1
    letterSpacing: "normal"
    fontFeature: "tabular-nums"
rounded:
  sm: "4px"
  base: "8px"
  lg: "14px"
  card: "18px"
  sheet: "20px"
  full: "999px"
spacing:
  xs: "4px"
  sm: "8px"
  md: "12px"
  lg: "16px"
  xl: "24px"
  2xl: "32px"
  3xl: "48px"
components:
  button-primary:
    backgroundColor: "{colors.brand}"
    textColor: "{colors.on-brand}"
    typography: "{typography.body}"
    rounded: "{rounded.lg}"
    padding: "8px 14px"
    height: "38px"
  button-primary-hover:
    backgroundColor: "{colors.brand-hover}"
    textColor: "{colors.on-brand}"
  button-default:
    backgroundColor: "{colors.surface}"
    textColor: "{colors.ink}"
    typography: "{typography.body}"
    rounded: "{rounded.lg}"
    padding: "8px 14px"
    height: "38px"
  button-default-hover:
    backgroundColor: "{colors.surface-2}"
    textColor: "{colors.ink}"
  button-ghost:
    backgroundColor: "transparent"
    textColor: "{colors.ink-muted}"
    typography: "{typography.body}"
    rounded: "{rounded.lg}"
    padding: "8px 14px"
    height: "38px"
  button-ghost-hover:
    backgroundColor: "{colors.surface}"
    textColor: "{colors.ink}"
  button-danger:
    backgroundColor: "{colors.status-suspended-bg}"
    textColor: "{colors.status-suspended}"
    typography: "{typography.body}"
    rounded: "{rounded.lg}"
    padding: "8px 14px"
    height: "38px"
  button-danger-hover:
    backgroundColor: "{colors.status-suspended-dot}"
    textColor: "{colors.on-brand}"
  button-sm:
    typography: "{typography.body}"
    rounded: "{rounded.lg}"
    padding: "4px 10px"
    height: "32px"
  button-icon:
    rounded: "{rounded.lg}"
    padding: "8px"
    height: "38px"
    width: "38px"
  input:
    backgroundColor: "{colors.surface}"
    textColor: "{colors.ink}"
    rounded: "{rounded.lg}"
    padding: "8px 12px"
    height: "38px"
  input-invalid:
    backgroundColor: "{colors.surface}"
    textColor: "{colors.ink}"
    rounded: "{rounded.lg}"
  field-label:
    textColor: "{colors.ink-muted}"
    typography: "{typography.label}"
  field-error:
    textColor: "{colors.status-suspended}"
    typography: "{typography.label}"
  status-chip-active:
    backgroundColor: "{colors.status-active-bg}"
    textColor: "{colors.status-active}"
    typography: "{typography.label}"
    rounded: "{rounded.full}"
    padding: "0 10px 0 8px"
    height: "24px"
  status-chip-pending:
    backgroundColor: "{colors.status-pending-bg}"
    textColor: "{colors.status-pending}"
    rounded: "{rounded.full}"
    height: "24px"
  status-chip-suspended:
    backgroundColor: "{colors.status-suspended-bg}"
    textColor: "{colors.status-suspended}"
    rounded: "{rounded.full}"
    height: "24px"
  status-chip-unknown:
    backgroundColor: "{colors.surface-2}"
    textColor: "{colors.ink-muted}"
    rounded: "{rounded.full}"
    height: "24px"
  table-head-cell:
    backgroundColor: "{colors.surface-2}"
    textColor: "{colors.head-ink}"
    typography: "{typography.label}"
    padding: "0 14px"
  table-row-selected:
    backgroundColor: "{colors.brand-soft}"
    textColor: "{colors.ink}"
    height: "40px"
  table-row-hover:
    backgroundColor: "{colors.surface-2}"
    textColor: "{colors.ink}"
    height: "40px"
  nav-item:
    textColor: "{colors.nav-ink}"
    typography: "{typography.body}"
    rounded: "{rounded.base}"
    padding: "0 10px"
    height: "36px"
  nav-item-active:
    backgroundColor: "{colors.brand-soft}"
    textColor: "{colors.brand-ink}"
    rounded: "{rounded.base}"
    height: "36px"
  modal:
    backgroundColor: "{colors.surface}"
    textColor: "{colors.ink}"
    rounded: "{rounded.card}"
    padding: "16px 20px"
  membership-card:
    backgroundColor: "{colors.card-metal-b}"
    textColor: "{colors.card-engrave}"
    typography: "{typography.display}"
    rounded: "{rounded.card}"
    padding: "16px"
---

# Design System: SASBO Online

## Overview

**Creative North Star: "The Wallet and the Desk"**

Every member is a card, and the register is the wallet that hands you that card. The page is a cool grey desk; the register is the white sheet lying on it; and when a member is chosen, an engraved metal card is laid down with the record rising beneath it. Nothing here is a dashboard: there are no hero-metric tiles, no same-size card grids, no modal for every edit. Administrators live in this application for hours at a stretch, so the surface is quiet, dense and ruled rather than boxed — hairlines do the separating, a single ledger line carries the totals, and status arrives stamped as a chip with a dot.

The world is restrained: cool neutral grounds (never warm cream), a slate-black dark theme (never pure black), one brand teal that owns action and selection, and gold spent only where a card would spend it — the engraving, the today ring, the Remarks slip. One family, Schibsted Grotesk, sets everything that is read; Marcellus engraves exactly one component. Both themes are the same semantic names with a second set of values, so no component knows a hue and no component knows a theme.

Conformance is measured, not intended. The build targets **WCAG 2.2 AA** and the closing gate (commit `626cf7e`) measured it: **contrast on rendered pixels** — 100 token combinations with 0 violations, and a live, state-gated sweep of 63 surfaces / 178 rows finding 0 contrast violations across 20,972 text nodes, 1,048 of them read off rendered pixels rather than computed styles; **target size (2.5.8)** — 0 short touch targets under real touch emulation; **focus visibility**; and **heading structure**. The design also ships inside a hard weight budget — JavaScript 641.92 of 650 KiB, CSS 78.80 of 80 KiB — which is why depth is three shadows and motion is one authored moment rather than a library.

**Key Characteristics:**
- Cool neutral grounds and a slate-black dark theme; one teal, one gold, never a third accent.
- Hairline separation: 1px rules, ruled figure strips, no boxes inside boxes.
- Two text inks only; a third faint ink exists for icons and dividers and is never text.
- 38px controls on a desk, real 44px targets on a coarse pointer, 16px fields wherever iOS would zoom.
- One engraved metal card, the only place the palette is allowed to be material.
- Measured AA: pixels, targets, focus, headings.

## Colors

Restrained cool neutrals plus one brand teal, with gold used as a line and a slip rather than as a second action colour; every value is defined twice — once in `@theme`, once under `.dark` — and a third time under `@media print`, so paper gets the light palette on white ground.

### Primary
- **Union Teal** (`{colors.brand}`): the one action colour. Primary buttons, the selected register row's fill, the chosen day in the date picker, the active nav mark. In dark it lifts to a bright cyan-teal, which is why text on it is never white by hand.
- **Deep Teal Ink** (`{colors.brand-ink}`): the brand as *text and icons on a surface* — membership numbers in the register, the active tab's label. It exists because the fill colour is not a legible text colour.
- **Teal Wash** (`{colors.brand-soft}`): selected rows, active chips, the text selection highlight. In dark it is a 14% tint rather than a solid, so the row beneath still reads.
- **On-Brand** (`{colors.on-brand}`): white in light, near-black teal in dark. One token, two values: white on the dark theme's bright teal measures 2.3:1 and cannot ship.

### Secondary
- **Card Gold** (`{colors.accent}`): gold *as a line* — the today ring in the calendar, the crest. Never a fill for an action.
- **Gold Ink** (`{colors.accent-ink}`): gold as text, for the engraved-feeling section labels.
- **Gold Slip** (`{colors.accent-soft}`): exactly one surface, the Remarks slip.

### Tertiary
- **Status family** (`{colors.status-active}`, `{colors.status-pending}`, `{colors.status-suspended}`, `{colors.status-resigned}` and their `-bg` / `-dot` siblings): five member states, each with a text ink, a chip fill and a dot. Semantic colour, held apart from the brand. Suspended doubles as the error family — the invalid field border, the destructive button, the required marker.

### Neutral
- **Desk Grey** (`{colors.bg}`) and **Raised Desk** (`{colors.bg-raised}`): the page ground and the navigation rail. A desk, not a page; the white register sits *on* it.
- **Paper** (`{colors.surface}`) and **Quiet Fill** (`{colors.surface-2}`): the sheets things are printed on, and the hover/chip/table-head wash.
- **Record Ink** (`{colors.ink}`) and **Muted Ink** (`{colors.ink-muted}`): the two text inks. Muted carries labels, secondary values, table heads and **placeholders**.
- **Faint Ink** (`{colors.ink-faint}`): icons and dividers only.
- **Hairline** (`{colors.line}`) and **Strong Hairline** (`{colors.line-strong}`): the 1px rules and the control borders.

### Named Rules

**The Faint-Ink Rule.** `ink-faint` is for icons and dividers only, never for text — not body, not a hint, not a placeholder. It measures 3.65:1 on dark, below the 4.5:1 floor, and `inkFaintIsNeverText.test.js` fails the suite on a violation. Placeholders are `ink-muted`.

**The Token-Only Rule.** No raw colour utility and no `dark:` twin may appear outside the token layer. A component names a role (`bg-surface`, `text-brand-ink`), never a hue and never a theme; `paletteLiterals.test.js` reddens on one.

**The One Voice Rule.** Teal is the only action colour and gold is never a second one: gold is a line, an engraving and one slip. A third accent does not exist.

**The On-Brand Rule.** Text or an icon on a brand fill is `on-brand`, never a hand-written white. The two themes disagree about what is legible on teal, and the token is where that disagreement lives.

## Typography

**Display Font:** Marcellus (with Georgia, serif) — the engraving only
**Body Font:** Schibsted Grotesk (with system-ui, -apple-system, Segoe UI, sans-serif)

**Character:** One grotesque does all the reading — labels, values, tables, buttons, copy — and the numerals do the identity work, tabular wherever figures line up. Marcellus appears exactly once, engraving the member's name on the metal card, and its rarity is what makes the card read as an object rather than a panel.

### Hierarchy
- **Display** (Marcellus 400, 24px / 1.5rem, 1.12, +0.05em, uppercase): the member's name engraved on the metal card. Nowhere else.
- **Headline** (600, 26px / `--text-3xl`, 1.15, −0.02em): the page title, one per screen.
- **Title** (600, 16px / `--text-lg`): panel and dialog titles.
- **Body** (400, 14px / `--text-base`): values, table cells, buttons, copy. Prose is capped at `max-w-prose`.
- **Label** (600, 11.5px / `--text-xs`, +0.08em, uppercase): field labels, table heads, status chips, figure-strip terms.
- **Figure** (600, 24px / `--text-2xl`, tabular): the totals in the ruled figure strip.

The shipped ramp is nine rem steps — 10.5 · 11.5 · 13 · 14 · 15 · 16 · 20 · 24 · 26px — declared in `rem` rather than `px` so the browser's and Android's font-size settings still work. (The mock was authored in px; the build is not, and the px value survives only as the comment documenting each step.)

### Named Rules

**The Label-Over-Value Rule.** Uppercase small-capital labels sit *above their own value* inside a field or a figure. They are never kickers or eyebrows above a heading; a heading stands alone.

**The Sixteen-Pixel Rule.** Any control that accepts typing uses the `field-text` utility: the design's 13px on a fine pointer above 760px, and a literal **16px** at ≤ 760px or on a coarse pointer. 16 is iOS Safari's zoom threshold, not this design's number, so it is the one hard-coded px left in the type system and it must not move when the scale moves.

**The Tabular Rule.** Numerals are `tabular-nums` wherever they stack: the register, the figure strip, the calendar, the card's membership number.

**The One Engraving Rule.** Marcellus sets the engraved name and nothing else. It never sets UI text.

## Layout

A three-column shell on a desk: the navigation rail at a fixed **256px**, the register in the middle, and the record panel at `clamp(424px, 32vw, 448px)` on the right — **present only while a record is open**, travelling over `--duration-panel: 380ms`. Below `xl` (1280px) the panel is a drawer; at ≤ 760px everything is one column and the register's rows become small cards, with nothing hidden and nothing scrolling sideways. Content sits in a 28px page gutter (16px below `lg`), and rows are 40px, controls 38px, nav rows 36px.

Spacing runs on a 4 · 8 · 12 · 16 · 24 · 32 · 48 rhythm and nothing in between. Full-height measurements are `dvh`, never `vh`.

**The Container Rule.** A component that can be squeezed by a sibling responds to **its own container, not the viewport**: the register (column ladder at 69.875 / 57 / 41.75rem), the topbar (crumb lead at 660px), the figure strip (five figures → three → two), the criteria row and the metal card's foot all use container queries. The record panel opens and closes by 448px without the window moving a pixel, so a media query cannot see the thing that causes the reflow.

**The Rem-Steps Rule.** Every responsive step is expressed in `rem`, because what it measures — text and content caps — is in rem. A px step silently breaks at a 125% root font size (WCAG 1.4.4).

**The Coarse-Pointer Rule.** Density branches on the *pointer*, never on the width. On `(pointer: coarse)` fields, selects, date fields, rail rows and table rows grow to a real 44px box (`touch-size`), and anything that draws an `::after` gets a 44px hit area without growing (`touch-target`). Desk density is provably untouched: a fine pointer never matches.

## Elevation & Depth

Depth is **ambient and scarce**: exactly three shadows, each with a real offset and blur, over a flat, tonally layered page. Most surfaces are separated by a 1px hairline and a ground/surface tone step, not by a shadow. There are no zero-offset halos, no hard block shadows, and no coloured glows.

### Shadow Vocabulary
- **Resting** (`--shadow-1`, `0 1px 2px rgba(16,24,40,.08), 0 2px 6px rgba(16,24,40,.08)`): anything sitting on the page as a control — buttons, the register sheet.
- **Floating** (`--shadow-2`, `0 4px 12px rgba(16,24,40,.10), 0 12px 32px rgba(16,24,40,.12)`): popovers, menus, the drawer, the dialog.
- **The Card** (`--shadow-card`, three layers ending `0 40px 80px`): the metal membership card only.

### Named Rules

**The Cast-Not-Stamped Rule.** The dark theme's shadows are *softer and wider* than the light theme's, not merely darker: alpha down, blur up. A tight, high-alpha shadow against a dark ground at the edge of the window stops reading as depth and starts reading as a drawn black line.

**The Material-Once Rule.** Gradients, gloss, milled edges and foil exist in exactly one component — the membership card — and its treatments are component tokens (`--card-*`), so nothing else can borrow the material.

## Shapes

Corners are gentle and graded by what they hold: **4px** for focus-ring corners and small icon buttons, **8px** for cells and chips, **14px** for controls and surfaces (buttons, inputs, popovers), **18px** for the membership card and the dialog, and a full pill (999px) for status chips and dots. Borders are **always 1px** — a heavier border does not exist in this system, and neither does a coloured left border as a device. Separation is a hairline; emphasis is an inset 2px underline on the active tab, not a box.

## Components

### Buttons
- **Shape:** softly rounded (14px), 38px tall on a desk, 32px for the small variant, and a square 38px for an icon-only control; a 44px hit area appears on a coarse pointer.
- **Primary:** teal fill with on-brand text and the resting shadow; the one action a surface wants you to take.
- **Default / Secondary:** paper fill, strong hairline border, record ink, resting shadow.
- **Ghost:** transparent with muted ink, for a control that must not compete.
- **Danger:** the suspended family — a soft red fill and red ink at rest, inverting to a solid red fill on hover. Destructive actions look like what they do.
- **Hover / Focus / Active:** colour-only transitions over 120ms on the standard ease; a 1px downward press on `:active`; focus is a 2px `brand-ring`. Hover is applied through a `hover-hover:` variant so a tap on a touch screen never leaves a control latched lit.
- **Loading is a state, not a caller's problem:** the control disables, sets `aria-busy`, and swaps its leading slot for a spinner while keeping its label.

### Inputs / Fields
- **Style:** paper fill, 1px strong-hairline border, 14px radius, 38px minimum height, `field-text` sizing.
- **Chrome:** the label is an uppercase small-capital in muted ink above the control; a hint sits below in muted ink; an error replaces the hint in suspended ink with `role="alert"`. The wrapper hands down `id` / `describedBy` / `invalid` rather than rendering the control, so the wiring cannot be forgotten and the control can be an input, a select, a listbox or a date sheet.
- **Focus:** a 2px brand ring, no outline shift.
- **Error:** the border moves to the suspended dot colour; required is a red asterisk plus an `sr-only` "(required)".
- **Disabled:** 60% opacity, not-allowed cursor.
- **Placeholders are `ink-muted`.** A placeholder is text and WCAG 1.4.3 applies to it.

### Chips
- **Status chip:** a 24px pill with a 6px dot, uppercase small capitals, the status family's fill and ink. Five known statuses; an unknown value falls back to the quiet surface and neutral ink rather than being coloured like a status it is not.
- **Cancelled members** (Resigned, Retired) are struck through in the register: nothing disappears, it cancels.

### Cards / Containers
- **Corner Style:** 14px for surfaces and panels, 18px for the membership card and dialogs.
- **Background:** paper on the desk ground; quiet fill for headers and footers of a container.
- **Shadow Strategy:** resting shadow for a sheet on the page, floating for anything that overlays, the card shadow for the card alone.
- **Border:** a single hairline.
- **Internal Padding:** 16–20px, on the 4px rhythm.

### Tables
- **Head:** sticky, quiet-fill ground, a strong hairline beneath, uppercase small capitals in head ink (which is the one role that swaps between themes rather than swapping value).
- **Rows:** 40px, body text in record ink, hairline separated; hover is the quiet fill, selection is the teal wash.
- **Responsive:** columns yield by container query in a measured order; below the width three columns need, the row becomes a two-column card — member over number, status at the top right, bank and branch beneath.

### Navigation
- **Rail:** 256px of raised ground with a right hairline, the wordmark at the top, rows at 36px with 18px lucide icons at stroke 1.75, nav ink at rest.
- **States:** hover is a translucent wash over the rail rather than a surface; the active item takes the teal wash with deep teal ink; a disclosure chevron rotates 90° over 120ms. Nested items step down to 13.5px with an indent.
- **Mobile:** the rail becomes a scrim-backed drawer.

### Overlays
- **One dialog primitive**, portalled to the body, focus-trapped, Escape-cancelling, scroll-locking, centred by a grid (never by a transform, which would trap every `position: fixed` child inside it). Scrim is 50% ink with a small backdrop blur.
- **Shape:** 18px radius, hairline border, paper fill, floating shadow; the header and footer stay put and the **body** scrolls, capped at `85dvh` so a footer's Save can never sit under a phone's browser chrome. The cap is not negotiable per call site.
- **A title is required.** A dialog with no accessible name is a defect; `titleHidden` makes it `sr-only` rather than absent.
- **The confirm names the action.** "Remove recruiter", never "OK", and a destructive confirm states the consequence in words.

### The Membership Card (signature)
The one material object: a three-stop teal-metal gradient at an 18px radius, a milled light edge at the top and a shade at the bottom, a gold-foil crest, the member's name engraved in Marcellus gold, and the membership number in tracked tabular figures. It carries the deep card shadow and it is the only element allowed a gradient or a gloss.

### Motion
One authored moment: choosing a member. The card is drawn from the wallet and laid on the desk — a 580ms hinged tilt with a sweep of light — and the record rises beneath it as a stagger capped at ten fields. Around it, the theme **pours** from the control that changed it (a 1.2s soft-edged iris), tabs slide in the direction of travel, rows settle in only when a query changes them, and everything else is a 120–200ms state transition on `cubic-bezier(.16, 1, .3, 1)` from an already-visible resting state.

**The Reduced-Motion Rule.** `prefers-reduced-motion` keeps opacity, colour and state and drops every *spatial* move. It is a surface rule narrowing `transition-property` to the non-spatial channels — never the reflex snippet that sets every duration to 0.01ms, which would also kill the colour feedback that tells a user a control responded. Keyframe animations are neutralised at 0.01ms rather than `none`, so an `animationend` listener can never hang a state machine.

## Do's and Don'ts

### Do:
- **Do** name a role, never a hue or a theme: `bg-surface`, `text-brand-ink`, `border-line` — the dark theme then follows without a single `dark:` prefix.
- **Do** put `ink-faint` on icons and dividers only, and `ink-muted` on every piece of secondary text, including placeholders.
- **Do** use `field-text` on anything typed into, so it lands at 16px wherever iOS would otherwise zoom the page.
- **Do** branch density on the pointer: `touch-target` for a 44px hit area, `touch-size` for a replaced element that must actually grow.
- **Do** let a squeezed component respond to its own container, in `rem`, at a measured step.
- **Do** separate with a 1px hairline and a tone step first; reach for a shadow only when something genuinely overlays.
- **Do** put labels as uppercase small capitals *over* their values, and use tabular numerals wherever figures stack.
- **Do** name the action on a confirm button and state the consequence in words.
- **Do** measure contrast on rendered pixels in a browser; a computed style is not a measurement, and an attribute like a placeholder is not a text node.

### Don't:
- **Don't** write a raw colour utility or a `dark:` twin outside the token layer.
- **Don't** set text in `ink-faint`, in any size, in either theme.
- **Don't** introduce a third accent colour, use gold as an action colour, ground the page in warm cream, or make the dark theme pure black.
- **Don't** add hero-metric tiles, grids of same-size cards, or a card inside a card; the totals are a ruled figure strip, not tiles.
- **Don't** put a kicker or an eyebrow above a heading, tint text with a gradient, use glass as decoration, or draw a coloured left border.
- **Don't** use a zero-offset halo or a hard block shadow; the three shadows all carry offset and blur.
- **Don't** set Marcellus in UI text, and don't use emoji or Unicode glyphs as icons — lucide at stroke 1.75, everywhere.
- **Don't** open a modal for a task that needs neither interruption nor protected focus; the record edits in place.
- **Don't** leave a native control in its browser costume: selection, caret, focus rings, scrollbars and the date picker all wear this design.
- **Don't** pass a height or a `max-h` into the dialog primitive, and don't measure full height in `vh`; `dvh` or nothing.
