/**
 * The shell's element ids, in one module because every one of them is a relationship between
 * two files that cannot see each other.
 *
 * 🔑 WHY A MODULE AND NOT THREE EXPORTS WHERE EACH ELEMENT LIVES. `Header` needs the drawer's
 * id for `aria-controls`; the drawer needs the hamburger's id to put focus back on it; the
 * skip link needs `<main>`'s id, and `<main>` is two components away from the layout that
 * renders the link. Exporting each id from the file that renders it made `Header` import from
 * `Sidebar` and `Sidebar` import from `Header` -- a cycle, for two string constants.
 *
 * ⚠️ AND EVERY ONE OF THESE RELATIONSHIPS FAILS SILENTLY WHEN IT BREAKS, which is the real
 * reason they are not written twice:
 *   - `aria-controls` naming an element that is not in the document is simply IGNORED. No
 *     error, no warning, and the relationship is gone.
 *   - `document.getElementById(...)?.focus()` on a renamed id does nothing at all, and focus
 *     stays wherever it was -- indistinguishable from never having written the line.
 *   - a skip link whose fragment names nothing moves the caret and not the focus, and it is
 *     invisible until focused, so nobody ever sees it fail.
 *
 * Each id is asserted to RESOLVE in `shell.test.jsx`, not merely to be present, because
 * "the attribute is set" is the half that was never the problem.
 */

/** The navigation drawer -- `Header`'s `aria-controls` target. */
export const SIDEBAR_ID = 'app-navigation'

/** The hamburger that opens the drawer -- where focus returns when Escape closes it. */
export const MENU_BUTTON_ID = 'app-navigation-trigger'

/** The main region -- the skip link's target, and focusable so the jump moves focus. */
export const MAIN_CONTENT_ID = 'main-content'

/**
 * The shell's third column -- where a page docks its record panel.
 *
 * card.html lays the page out as ONE grid: rail | main | panel, and the panel is a sibling of
 * the topbar, not a child of the page under it. A panel rendered inside the scrolling page
 * slides under the sticky topbar the moment the register scrolls; a panel portalled into this
 * slot is a real column, full height, with its own scroll. `MemberPanel` portals here when
 * the slot exists and renders in place when it does not (tests mount pages without the shell).
 */
export const PANEL_SLOT_ID = 'app-panel'
