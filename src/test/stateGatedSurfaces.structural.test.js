// @vitest-environment node -- it reads src/ from disk; there is nothing to render
import { describe, expect, it } from 'vitest'

import { SURFACES, UNREACHABLE, coverageComplaints, siteCount } from '../../tools/surfaceCatalogue.mjs'

/**
 * 🔴 L2 · `07.2` — A STATE-GATED SURFACE NOBODY DRIVES CANNOT PASS AGAIN.
 *
 * `tools/surfaces.mjs` opens every dialog, fails and empties every list, answers every question that reveals a field,
 * and measures contrast and touch targets inside each surface. That run needs a browser and a quiet box, so it is not on
 * every commit. THIS is: every `<Modal`, `<ConfirmDialog`, `<BankBranchModal`, `<DataState`, `<DateField` and
 * `aria-expanded` site under `src/` must be named by a surface the browser run drives, or by an `UNREACHABLE` entry
 * saying why no person can reach it. A dialog added tomorrow fails here tomorrow.
 *
 * Its two canaries are the proof it can fail: a driven site dropped from the catalogue, and a claim for a site the
 * source does not have. Without them an empty complaint list is indistinguishable from a guard that looks at nothing.
 */
describe('the state-gated surface catalogue, held against the source', () => {
  it('names every state-gated site in src/ -- driven by a surface, or unreachable with a reason', () => {
    expect(coverageComplaints()).toEqual([])
  })

  it('fails when a site the source has is driven by nothing', () => {
    const dropped = 'pages/members/Recruiters.jsx#ConfirmDialog@remove recruiter'
    // Presence first (T37): a claim that was never in the catalogue would make the drop a no-op and the test vacuous.
    expect(SURFACES.some((s) => s.covers?.includes(dropped))).toBe(true)
    const catalogue = SURFACES.map((s) => ({ ...s, covers: (s.covers ?? []).filter((c) => c !== dropped) }))
    expect(coverageComplaints(catalogue, UNREACHABLE).join('\n')).toMatch(
      /pages\/members\/Recruiters\.jsx#ConfirmDialog: 2 site\(s\) in the source, 1 driven or excused/,
    )
  })

  it('fails when the catalogue claims a site the source no longer has', () => {
    const ghost = [...SURFACES, { name: 'ghost', route: '/', present: { NONE: 'false' }, covers: ['pages/Nowhere.jsx#Modal@ghost'] }]
    expect(coverageComplaints(ghost, UNREACHABLE).join('\n')).toMatch(/pages\/Nowhere\.jsx#Modal is claimed and no longer exists/)
  })

  it('counts an aria-expanded ATTRIBUTE as a site, and an attribute SELECTOR inside a query as none', () => {
    // The must-convict half: the two ways a control that expands is written.
    expect(siteCount('<button type="button" aria-expanded={open} onClick={toggle}>', 'aria-expanded')).toBe(1)
    expect(siteCount('<button aria-expanded="false">', 'aria-expanded')).toBe(1)
    // The must-acquit half: Modal.jsx looks for an open popup inside the dialog with an attribute selector (R2-F1). It
    // is a query, not a control anyone expands -- and counted as one, it read as "2 sites nobody drives".
    expect(siteCount(`content.querySelector('[role="combobox"][aria-expanded="true"], [aria-haspopup][aria-expanded="true"]')`, 'aria-expanded')).toBe(0)
  })

  it('gives every surface a unique name and a presence check, and every unreachable site its reason', () => {
    const names = SURFACES.map((s) => s.name)
    expect(new Set(names).size).toBe(names.length)
    for (const surface of SURFACES) expect(Object.keys(surface.present ?? {}).length, surface.name).toBeGreaterThan(0)
    for (const entry of UNREACHABLE) expect(entry.why.length, entry.site).toBeGreaterThan(20)
  })
})
