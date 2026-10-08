import { X } from "lucide-react";
import { Link, useLocation } from "react-router-dom";
import { memo, Suspense, useEffect, useRef, useState } from "react";
import Logo from "./common/Logo";
import SidebarItem from "./common/SidebarItem";
import ThemeToggle from "./common/ThemeToggle";
import Button from "./form/Button";
import { navItems } from "../config/navigation";
import { MENU_BUTTON_ID, SIDEBAR_ID } from "./shellIds";
import { cn } from "../lib/utils";
import useMediaQuery from "../hooks/useMediaQuery";
// LAZY (OB17a): the foot's Sign in or Sign out, from the same lazy module as the header's account (`AccountControls`).
import { RailAccount } from "./account/lazyAccount";

/*
 * The chain of hrefs from the rail's root to `target`, and the SHORTEST one when a route
 * appears more than once. The menu lists Members twice -- at the top level, and three deep
 * under Admin Menu > Daily Menu -- and the first match in reading order is the deep one,
 * which opened Admin Menu and Daily Menu on every visit to the register. The rail the mock
 * shows has Members open at the top and Admin Menu closed.
 */
const findAncestors = (items, target, path = []) => {
  let best = null;
  for (const item of items) {
    // An entry with no route (Labels, Maintenance > Bank/Branch) is a label, not a
    // destination: it can never be the target and it contributes nothing to a path.
    if (!item.href) continue;
    const current = [...path, item.href];
    if (item.href === target && (!best || current.length < best.length)) best = current;
    if (item.subItems) {
      const found = findAncestors(item.subItems, target, current);
      if (found && (!best || found.length < best.length)) best = found;
    }
  }
  return best;
};

/**
 * The width below which the rail is a DRAWER rather than furniture, as a media query.
 *
 * ⚠️ IT MUST AGREE WITH TAILWIND'S `lg:` BELOW, and nothing enforces that. If the two ever disagree, the desk rail
 * is `inert` while fully visible -- a silent, total keyboard lockout of the navigation. Named here so the pairing is
 * at least visible to the next reader; one shared breakpoint source is a job for the arc's closing refactor.
 */
const BELOW_DESK = "(max-width: 1023px)";

/**
 * THE RAIL. card.html's `.side`: the quiet second layer -- a full step below the page
 * ground, 256px, the wordmark at the top, the navigation as 36px rows, and the foot
 * carrying Appearance and Sign out (or Sign in).
 *
 * Below `lg` it is the same column as a drawer, which is this application's measured
 * phone treatment (R-RESP) rather than the mock's 68px icon rail; the mock only ever
 * specified a desk.
 */
/**
 * `expanded` with every section in `hrefs` open -- the same object when they already were, so a render-phase update
 * that changes nothing does not ask for another render. The card and the route each opened a chain of sections by
 * hand (RF); both open it here.
 */
const withOpened = (expanded, hrefs) => {
  let changed = false;
  const next = { ...expanded };
  hrefs.forEach((href) => {
    if (!next[href]) { next[href] = true; changed = true; }
  });
  return changed ? next : expanded;
};

function Sidebar({ sideBarOpen, setSideBarOpen, selectedCardHref }) {
  const location = useLocation();
  const [expandedItems, setExpandedItems] = useState({});
  const [prevSelectedCardHref, setPrevSelectedCardHref] = useState(null);

  if (selectedCardHref !== prevSelectedCardHref) {
    setPrevSelectedCardHref(selectedCardHref);
    if (selectedCardHref) {
      const ancestors = findAncestors(navItems, selectedCardHref) || [selectedCardHref];
      setExpandedItems(prev => withOpened(prev, ancestors));
    }
  }

  /*
   * The section holding the current route is open when you arrive on it. card.html shows
   * Members expanded with Capture Member under it because Members is where you are; a rail
   * that only expands on a click leaves a deep link -- /members/capture from a bookmark --
   * with its own section collapsed above it.
   */
  //
  // 🔴 DURING RENDER, THE SAME WAY AS THE SELECTED CARD ABOVE (`10.4`). This was an effect on the pathname, which
  // `react-hooks/set-state-in-effect` makes an error this phase; the file already had the render-phase form for the
  // card, so the route gets the same one. A deep link now arrives with its section open on the FIRST paint, rather
  // than one frame later. `shell.routeExpansion.test.jsx` holds it.
  const [prevPathname, setPrevPathname] = useState(null);
  if (location.pathname !== prevPathname) {
    setPrevPathname(location.pathname);
    const ancestors = findAncestors(navItems, location.pathname);
    // The whole chain, the current section included: Members open shows Capture Member
    // and Labels under it, which is how the mock draws the rail on the register.
    if (ancestors) setExpandedItems(prev => withOpened(prev, ancestors));
  }

  const toggleExpand = (href) => {
    setExpandedItems(prev => ({
      ...prev,
      [href]: !prev[href]
    }));
  };

  /*
   * 🔴 R1-F2, THE OTHER HALF: OPENING THE DRAWER MOVED NO FOCUS. The hamburger stays focused behind the scrim, so a
   * keyboard user who opens the menu is still outside it and the next Tab walks the page underneath. Focus goes to
   * the drawer's own close button -- the first control in it, and the way back out -- matching where Escape returns
   * focus from. Only below the desk breakpoint: at `lg` the rail is static furniture nobody "opened".
   */
  const belowDesk = useMediaQuery(BELOW_DESK);
  const closeRef = useRef(null);

  useEffect(() => {
    if (!sideBarOpen || !belowDesk) return;
    // 🔑 A REF, NOT A SELECTOR ON THE LABEL. The first spelling found the button by
    // `[aria-label="Close navigation menu"]`, which bound this behaviour to a copy string 100 lines away: rename
    // the label and focus silently stops moving, with nothing red to say so. P9's refactor pass.
    closeRef.current?.focus();
  }, [sideBarOpen, belowDesk]);

  /**
   * 🔴 ESCAPE CLOSES THE DRAWER, AND UNTIL `05.4` NOTHING DID.
   *
   * The drawer had exactly two ways out and both of them were a mouse: the scrim below, and
   * the X inside it. On a phone that is fine, because a phone has no Escape key -- but the
   * drawer is also what a keyboard user on a narrow window gets, and for them an opened
   * drawer was a one-way door. Escape is the convention every dismissible overlay obeys, and
   * it is what makes the scrim's `aria-hidden` honest rather than a way of quieting a linter:
   * the click target is a mouse convenience, and the keyboard has its own route out.
   *
   * Focus goes back to the control that opened it. Leaving it where it was drops it on a
   * `<nav>` that is now translated off-screen, so the next Tab resumes from a place the user
   * cannot see -- the same defect `05.3` found in every dialog in the application, which is
   * why the drawer is checked for it too rather than assumed different.
   */
  useEffect(() => {
    if (!sideBarOpen) return;
    const onKeyDown = (event) => {
      if (event.key !== "Escape") return;
      setSideBarOpen(false);
      // The trigger is in `Header`, a sibling two levels up, so it is reached by the id
      // `Header` exports rather than by a ref threaded through `Main`.
      document.getElementById(MENU_BUTTON_ID)?.focus();
    };
    document.addEventListener("keydown", onKeyDown);
    return () => document.removeEventListener("keydown", onKeyDown);
  }, [sideBarOpen, setSideBarOpen]);

  return (
    <>
      {/* The scrim: a click anywhere outside the drawer closes it.

          🔑 `aria-hidden` BECAUSE IT IS TRULY DECORATIVE, not to satisfy a rule. It is a
          transparent full-screen `<div>` whose only job is to catch a click a mouse user
          aims at "not the menu". There is nothing here to announce, nothing to focus, and
          putting it in the accessibility tree as a giant unlabelled control would be worse
          than leaving it out. The keyboard equivalent is Escape, above -- and `jsx-a11y`'s
          `click-events-have-key-events` and `no-static-element-interactions` both stand down
          for exactly this reason, because the element is no longer *visible* to assistive
          technology. Measured, 2026-09-10: with `aria-hidden` both rules go quiet; with
          `role="none"` instead, `role-supports-aria-props` fires. */}
      {sideBarOpen && (
        <div
          aria-hidden="true"
          className="fixed inset-0 bg-ink/25 z-10 lg:hidden"
          onClick={() => setSideBarOpen(false)}
        ></div>
      )}

      {/* 🔴 A `<div>` HELD THE ENTIRE NAVIGATION. The application's whole menu tree — the
          only way to reach 25 of its 27 routes — carried no landmark at all, so a screen
          reader had nothing to jump to and nothing to skip past. `<nav>` costs a tag.

          The label is explicit because `Breadcrumbs` is also a `<nav>`: two unlabelled
          navigation landmarks are announced identically, which is worse than one. */}
      <nav
        id={SIDEBAR_ID}
        aria-label="Main"
        /**
         * 🔴 R1-F2: A CLOSED DRAWER IS OFF SCREEN, NOT OUT OF THE WAY. Below `lg` it was hidden by
         * `-translate-x-64` alone, and a translated element is still focusable -- so a keyboard user on a narrow
         * window tabbed through about twenty stops they could not see before reaching the page (WCAG 2.4.3 / 2.4.7).
         * `inert` removes the whole subtree from the tab order and the accessibility tree, and it is applied ONLY
         * when the drawer is genuinely off canvas: at desk width the rail is static and always reachable.
         * Pixel-neutral -- `inert` has no rendering of its own, and the drawer is already translated away.
         */
        inert={belowDesk && !sideBarOpen}
        className={cn(
          "fixed z-20 flex h-dvh w-64 flex-col border-r border-line bg-bg-raised px-3 pb-3 pt-3.5",
          "shadow-(--shadow-2) transition-transform duration-(--duration-base) ease-(--ease-out)",
          sideBarOpen ? "translate-x-0" : "-translate-x-64",
          "lg:static lg:shrink-0 lg:translate-x-0 lg:shadow-none",
        )}
      >
        {/* 🔴 THE WORDMARK, AND WHAT IT REPLACES: "Enquiry Navigation Menu" — a label
            describing the furniture rather than naming the product. card.html puts SASBO
            Online and the union's strapline here, in the brand's gold, which is also WHY
            the topbar carries no application title: an application's name belongs in one
            place, and the rail is the place that is on screen at every width. */}
        <div className="relative flex items-center gap-2 px-2 pb-4 pt-1.5">
          {/* 🔴 R1-F4: WAS A RAW `<a href="/home">`, so the one thing at the top of every screen RELOADED THE WHOLE
              APPLICATION -- the entry chunk fetched and parsed again, the topbar search and any page state (a record
              mid-edit) dropped with no confirm, and on a phone the drawer left open because `setSideBarOpen` never
              ran. Every other rail entry was already a `<Link>`. Same classes, so no pixel moves. */}
          <Link
            to="/home"
            onClick={() => setSideBarOpen(false)}
            aria-label="SASBO Online home"
            className="touch-size flex min-w-0 items-center gap-2.5 rounded-lg focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-brand-ring"
          >
            <Logo className="size-10 shrink-0 object-contain" />
            <span className="grid min-w-0 leading-[1.15]">
              <span className="truncate text-md font-semibold tracking-[-0.01em] text-ink">SASBO Online</span>
              {/* The strapline keeps the mock's tracking on the desk; in the drawer, where the
                  close control shares the row, it tightens a step rather than truncating. */}
              <small className="mt-[3px] whitespace-nowrap text-[11px] font-semibold uppercase tracking-[0.14em] text-accent-ink max-lg:tracking-[0.1em]">
                The Finance Union
              </small>
            </span>
          </Link>
          {/* Same defect as the hamburger it undoes: an icon-only raw <button> with no
              accessible name, announcing as "button". The primitive brings the name, the
              44px coarse-pointer hit area and the focus ring. Positioned over the row's
              corner so the wordmark keeps its full width beside it. */}
          <Button
            ref={closeRef}
            size="icon"
            variant="ghost"
            className="absolute -top-1 right-0 lg:hidden"
            aria-label="Close navigation menu"
            onClick={() => setSideBarOpen(false)}
          >
            <X size={20} strokeWidth={1.75} aria-hidden="true" />
          </Button>
        </div>

        {/* 📐 THE RAIL'S RHYTHM -- AND THE SECOND TIME THIS BLOCK HAS BEEN WRONG.
            `card.css:135-141` is `.nav { display: grid; gap: 2px }` over rows of `height: 36px`.
            That is a 38px pitch, and a 32px pitch for `.nav__item--sub`: a dense, quiet list in
            which an indented child sits close enough to its parent to read as belonging to it.

            The first attempt measured the pitch off `mock-light.png` as 66px and fixed a
            64px TRACK to reproduce it. Both halves of that were wrong. 66px is what the mock's
            OWN page happens to render, because `.nav { flex: 1 }` stretches eleven rows to fill
            a 900px rail -- it is an artefact of that one page's row count, not a measurement of
            the design. And reproducing it cost more than it bought: 36px rows floating in 64px
            tracks put "Capture Member" as far from "Members" as "Recruiters" is, so the
            parentage the indent is drawing dissolved -- while the FOOT below the rule kept real
            36px rows 38px apart, so one rail ran two rhythms at once. The round-four review
            measured both and named it.

            `content-start` alone is the whole fix. It stops the stretch `flex: 1` would cause,
            the rows are their own 36px, the gap is the mock's 2px, and the pitch is identical
            on every route because nothing is being divided.

            🔑 `flex-1` IS THE OTHER HALF OF `.nav { flex: 1 }`, AND IT LEFT WITH THE STRETCH
            (`06.3`). The comp's foot sits at the bottom of the rail on every screen because the
            list takes the height between the wordmark and the foot. Without it the foot rode
            under the list -- 515px down on Members, 447 on Home -- and moved whenever a section
            opened. `flex-1` gives the list that height; `content-start` keeps the rows out of it. */}
        <div className="scroll-quiet grid min-h-0 flex-1 content-start gap-0.5 overflow-y-auto [&>*]:self-start">
          {navItems.map((item) => (
            <SidebarItem
              key={item.href}
              item={item}
              isActive={location.pathname === item.href}
              expandedItems={expandedItems}
              onToggle={toggleExpand}
              onClick={() => setSideBarOpen(false)}
              selectedCardHref={selectedCardHref}
            />
          ))}
        </div>

        {/* card.html's `.side__foot`: the appearance switch, then the way out. */}
        <div className="grid gap-0.5 border-t border-line pt-3">
          <ThemeToggle variant="nav" />
          <Suspense fallback={null}>
            <RailAccount onClick={() => setSideBarOpen(false)} />
          </Suspense>
        </div>
      </nav>
    </>
  );
}

/*
 * Memoised because the topbar's search lives in `AppLayout`, so every keystroke re-renders the shell, and the rail's
 * props never change with it. The route and the theme still reach it through context. Its props must stay stable: an
 * inline callback passed here would quietly undo this.
 */
export default memo(Sidebar);
