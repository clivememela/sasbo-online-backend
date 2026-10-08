import { Suspense, useEffect, useRef } from "react";
import { useLocation, useNavigate } from "react-router-dom";
import { Bell, Menu, Search } from "lucide-react";
import Breadcrumbs from "./common/Breadcrumbs";
import Button from "./form/Button";
import { MENU_BUTTON_ID, SIDEBAR_ID } from "./shellIds";
import { cn } from "../lib/utils";
import useMediaQuery from "../hooks/useMediaQuery";
// LAZY (OB17a): who is signed in, and the way in or out, arrive with the page's own chunk -- see `AccountControls`.
import { HeaderAccount } from "./account/lazyAccount";

/**
 * THE TOPBAR. card.html's `.topbar`, and it is 60px.
 *
 * 🔴 **WHAT THIS REPLACES.** A **160px** band (`h-40`) carrying a 24px application title, a
 * standalone avatar, the theme toggle and the breadcrumbs stacked underneath it — a sixth of
 * a 1080px screen, on every route, saying *"Membership Administration System"* to a person
 * who has been looking at this application all morning.
 *
 * The mock's topbar is `auto | minmax(220px, 520px) | 1fr`: **crumbs, search, tools.** The
 * application's name is in the rail's wordmark, where an application's name belongs, and
 * the appearance switch is in the rail's foot, where the mock keeps it.
 *
 * 🔑 **THE SEARCH IS THE POINT OF THE ROW.** `directionContract.md`'s thesis is that the
 * register hands you one member's card; the fastest path to a member is typing their number.
 * It lives in the shell because it is how you get to a member from anywhere: typing here on
 * any other route takes you to the register with the query applied. The chip beside the
 * field names what the register recognised -- a membership number, an I.D. number, a name --
 * so the user knows the search understood them before the rows move.
 *
 * ⌘K, Ctrl+K and `/` focus it, as the mock's `kbd` promises.
 *
 * 🔑 **BR-18 STILL HOLDS**: this carries no `<h1>`. `PageTitle` remains the page's only one,
 * and `headingContract.test.jsx` mounts every route and counts.
 */

/**
 * What the search recognised: the register's own identifiers, named as the user types them.
 *
 * 🔁 THE REGISTER SEARCHES ON THE SERVER SINCE A10, and the chip names only what that search finds -- the start of a
 * name or a number, an identity number whole or by its last four (`memberRegister.js`). It named a cellphone number, a
 * telephone number and an e-mail address too, which the register's box matched when it filtered every member in the
 * browser; those are "More criteria" now, and a chip promising them would promise a match that never comes.
 */
function searchKind(query) {
  const q = String(query ?? "").trim();
  if (!q) return "";
  if (/^m-?\d{3,}$/i.test(q)) return "Membership number";
  if (/^\d{13}$/.test(q)) return "I.D. number";
  if (/^\d{4}$/.test(q)) return "Last four of an I.D.";
  if (/^\d{5,12}$/.test(q)) return "Number";
  if (/^[a-z]{1,4}-?\d{3,}$/i.test(q)) return "Employee number";
  if (/^[a-z][a-z' -]{1,}$/i.test(q)) return "Name or surname";
  return "";
}

const REGISTER_PATH = "/members";

export default function Header({ setSideBarOpen, sideBarOpen, search = "", setSearch }) {
  const inputRef = useRef(null);
  const navigate = useNavigate();
  const location = useLocation();
  const kind = searchKind(search);

  /*
   * 📱 THE SEARCH COPY SHORTENS WITH THE ROOM. On a phone the long placeholder was cut
   * mid-word -- "Search members: su" -- and a half-word in the most prominent control on the
   * screen reads as breakage, not as truncation. The long form earns its place on a desk,
   * where it names the five things the search will match; a placeholder is an ATTRIBUTE, so
   * no media query can swap it and the component has to know the width itself.
   *
   * 640px is Tailwind's `sm`, the same boundary the topbar's own ⌘K hint appears at, so the
   * two changes happen together rather than a step apart.
   */
  const narrow = useMediaQuery('(max-width: 639px)');

  useEffect(() => {
    const onKeyDown = (event) => {
      const typing = /^(INPUT|SELECT|TEXTAREA)$/.test(event.target?.tagName || "") || event.target?.isContentEditable;
      if ((event.key === "k" || event.key === "K") && (event.metaKey || event.ctrlKey)) {
        event.preventDefault();
        inputRef.current?.focus();
        inputRef.current?.select();
        return;
      }
      if (event.key === "/" && !typing) {
        event.preventDefault();
        inputRef.current?.focus();
      }
    };
    document.addEventListener("keydown", onKeyDown);
    return () => document.removeEventListener("keydown", onKeyDown);
  }, []);

  const onChange = (event) => {
    const value = event.target.value;
    setSearch?.(value);
    // The search is the register's. Typing it anywhere else takes you there, once, and
    // keeps the focus in the field so the next keystroke lands where the last one did.
    if (value && location.pathname !== REGISTER_PATH) {
      navigate(REGISTER_PATH);
      requestAnimationFrame(() => inputRef.current?.focus());
    }
  };

  return (
    <header
      className={cn(
        // `shrink-0`: the topbar is a flex child of a column whose content overflows on
        // every long register, and a flex column squashes its children to their content
        // before it scrolls -- measured at 39px instead of 60 on Members, exactly the
        // rows of the search pill the reviewer found cut off. The height is the mock's
        // and it does not negotiate.
        "sticky top-0 z-10 grid h-15 shrink-0 items-center gap-3 border-b border-line bg-surface px-4 text-ink",
        "topbar",
        "grid-cols-[auto_minmax(0,1fr)_auto]",
        "lg:grid-cols-[max-content_minmax(220px,440px)_1fr] lg:gap-5 lg:px-7",
      )}
    >
      <div className="flex min-w-0 items-center gap-2">
        {/* 🔴 IT HAD NO ACCESSIBLE NAME. An icon-only <button> whose only child is an SVG
            announces as "button" and nothing else, so the one control that opens the
            navigation on a phone was unusable by anyone not looking at it. `aria-expanded` +
            `aria-controls` are the other half: the drawer it opens is a sibling in
            `AppLayout`, so without them nothing connects the two. */}
        <Button
          id={MENU_BUTTON_ID}
          size="icon"
          variant="ghost"
          className="lg:hidden"
          aria-label="Open navigation menu"
          aria-expanded={Boolean(sideBarOpen)}
          aria-controls={SIDEBAR_ID}
          onClick={() => setSideBarOpen(true)}
        >
          <Menu size={20} strokeWidth={1.75} aria-hidden="true" />
        </Button>

        {/* The mock drops the crumbs at 1100px: on a narrow window, where you are matters
            less than getting somewhere, and two competing rows would wrap.

            ⚠️ `min-w-0` IS HERE NOW, AND THIS COMMENT USED TO REFUSE IT. The reason it gave
            was true at the time: this is the grid's `auto` track, `min-w-0` makes its
            min-content zero, and the search then took everything and squeezed the trail to
            "Membe…" at 1440 where there was room for all three.

            🔴 BUT REFUSING IT COST MORE. Without `min-w-0` the trail cannot shrink, so it
            OVERFLOWED its track instead -- and the search field, which has a solid ground,
            drew straight over the top of it. Measured with the record panel open: 62px of
            "Members" under the search at 1280, 33px at 1366. The owner saw it and it looked
            like a rendering fault. Worse, the half it left showing was the CURRENT PAGE,
            which is the one crumb worth reading.

            What makes `min-w-0` safe now is that the shrinking has an ORDER. `Breadcrumbs`
            gives every crumb ahead of the current page `shrink` and no floor, and gives the
            current page a `7ch` floor, so the trail loses "Home" and the sections before it
            gives up a single character of where you are. The old squeeze cannot come back
            because the thing it squeezed is the thing that now yields last. The search's
            ceiling comes down 520 → 440 in the same breath: at 1280, on the deepest route in
            the application, the difference is exactly what the trail needed.

            🔴 AND IT STILL TRUNCATED -- THE GRID DID IT, NOT THE CRUMBS (`06.3`, measured). An
            `auto` track whose item has `min-w-0` has a minimum of zero, so the grid shared its
            free space EVENLY between the trail and the search: at 1440 with a record open the
            trail was handed 118 of the 120px it needed ("Ho… › Membe…"), at 1280 57 of 58
            ("Membe…"). The trail's track is `max-content` now, so it is always its own width,
            the search gives way toward its 220px floor first, and where even that cannot fit,
            the `topbar` container query has already dropped the crumbs ahead of the page. */}
        <div className="hidden min-w-0 min-[1101px]:block">
          <Breadcrumbs />
        </div>
      </div>

      <label
        className={cn(
          // `touch-size`: the label is the search's target -- a tap on it focuses the field -- and 44px on a coarse pointer.
          "touch-size flex h-[38px] min-w-0 items-center gap-2.5 rounded-[10px] border border-line-strong bg-surface-2 px-3",
          "transition-[border-color,box-shadow,background-color] duration-(--duration-instant)",
          "focus-within:border-brand focus-within:bg-surface focus-within:shadow-[0_0_0_3px_var(--color-brand-ring)]",
        )}
      >
        <span className="sr-only">Search members</span>
        <Search size={17} strokeWidth={1.75} aria-hidden="true" className="shrink-0 text-ink-faint" />
        <input
          ref={inputRef}
          type="search"
          autoComplete="off"
          value={search}
          onChange={onChange}
          placeholder={narrow ? "Search members" : "Search members: surname, first name, number, I.D."}
          className="search-text min-w-0 flex-1 border-0 bg-transparent text-ink placeholder:text-ink-muted focus:outline-none [&::-webkit-search-cancel-button]:appearance-none"
        />
        {kind && (
          <span
            aria-live="polite"
            className="kind-in inline-flex h-[22px] shrink-0 items-center whitespace-nowrap rounded-full bg-brand-soft px-2 text-xs font-semibold tracking-[0.03em] text-brand-ink"
          >
            {kind}
          </span>
        )}
        <kbd
          title="Ctrl or ⌘ + K focuses search; / also works"
          className="hidden shrink-0 rounded-[5px] border border-line-strong border-b-2 bg-surface px-[5px] py-[3px] font-sans text-[11px] font-medium leading-none text-ink-muted sm:inline-block"
        >
          ⌘K
        </kbd>
      </label>

      <div className="flex items-center justify-end gap-1.5">
        {/* 🔴 IT HAD A HOVER, A FOCUS RING AND NO HANDLER -- the one dishonest control left
            in the shell, and the avatar beside it was demoted to an inert chip for exactly
            this reason. There is no notifications feature to open. So the bell keeps the
            mock's topbar shape and stops pretending: disabled, out of the tab order, and its
            accessible name says what is true. It is the same call `NavigationCard` makes for
            a menu row with no route. */}
        <Button
          size="icon"
          variant="ghost"
          disabled
          aria-label="Notifications — none yet"
          title="No notifications yet"
        >
          <Bell size={18} strokeWidth={1.75} aria-hidden="true" />
        </Button>

        {/* The placeholder chip ("SA", "Administrator") that stood here while authentication was deferred (R-SEC-C)
            is the real account now: the session's name and position, and the way out. */}
        <Suspense fallback={null}>
          <HeaderAccount />
        </Suspense>
      </div>
    </header>
  );
}
