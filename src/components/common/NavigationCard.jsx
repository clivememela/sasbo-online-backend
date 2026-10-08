import { Link } from "react-router-dom";
import { ChevronRight } from "lucide-react";

import { cn } from "../../lib/utils";
import Sheet from "./Sheet.jsx";

/**
 * One destination on a menu page -- a ROW of the wallet, not a card.
 *
 * 🔴 It was a grid of same-size icon-disc + heading + text cards, which is the scaffold the
 * direction contract refuses by name ("dashboards of same-size cards") and the first thing
 * the craft floor lists under page scaffolds; the finish review of 2026-09-10 named it as
 * the one material contradiction on Home. card.html has no menu page, so the menu takes the
 * register's own grammar: a sheet of rows, each with its icon in the brand's ink, its name,
 * what it holds, and a chevron -- read down the page the way the register is.
 *
 * 📱 THE WHOLE ROW IS THE TARGET. A row whose only hit area is its title is the commonest
 * way a touch target ends up under 44px while looking enormous.
 */

const ROW = "flex items-center gap-4 px-5 py-3.5";

/**
 * 🔴 A ROW WITH NO `href` IS NOT A LINK, AND UNTIL NOW IT PRETENDED TO BE ONE.
 *
 * `<Link to={undefined}>` renders an `<a href>` pointing at the CURRENT url. React-router
 * gives it no error and no warning, so the row arrived with a pointer cursor, a hover wash,
 * a chevron promising somewhere to go, and a click that did nothing whatever. Measured on
 * the running app, 2026-09-10: **ten such rows** -- all nine of Monthly Menu, and Bank/Branch
 * on Maintenance. A menu of nine identical dead ends is the worst thing a landing surface
 * can be, because the visitor blames themselves for it.
 *
 * These are not broken links; they are OPERATIONS THAT ARE NOT BUILT YET. So the row says
 * that, in the quietest way the world has: muted ink, no chevron, no hover, no cursor, out
 * of the tab order -- and a small caption on the right instead of the arrow. The decision
 * lives HERE rather than on each menu page, so the next entry someone adds without a route
 * cannot ship as a lie.
 *
 * 🗣️ It matches what the rail already does: `SidebarItem` renders a `<span>`, not a
 * `<Link>`, for a menu entry with no route.
 *
 * `note` is that caption, where "not yet available" is not the truth: month end's listings (`04.1`) ARE built, and
 * refuse until sign-in -- so their rows say *Waits for sign-in*. Keep it short: it never wraps, and sits beside the
 * title on a phone.
 *
 * 🗣️ `tile` IS HOME'S CARD VIEW, AND IT IS SASBO'S ASK OVER THE DIRECTION CONTRACT'S REFUSAL ABOVE. At the 5 Oct Show &
 * Tell Natalie said she liked the cards Home had before the rows, so Home offers both (handover §A) and opens on the
 * cards. A card is this row's own parts on the sheet's surface -- the icon and the way in on a line above the words --
 * with the row's hover wash, focus ring and honest dead state, so the two views cannot drift apart, and the stylesheet
 * gains nothing for it (the bundle had no room: bundle-budget.mjs). Every other menu page keeps the rows.
 */
const TILE = "flex h-full flex-col gap-3 rounded-lg bg-surface p-5 shadow-(--shadow-1)";
const LIVE =
  "group text-ink transition-colors duration-(--duration-instant) hover-hover:hover:bg-surface-2 " +
  "focus:outline-none focus-visible:ring-2 focus-visible:ring-inset focus-visible:ring-brand-ring";

export default function NavigationCard({ title, icon: Icon, href, desc, onSelect, note = 'Not yet available', layout = 'row' }) {
  const tile = layout === 'tile';
  const icon = Icon && (
    <Icon size={tile ? 24 : 18} strokeWidth={1.75} aria-hidden="true" className={cn("shrink-0", href ? "text-brand" : "text-ink-faint")} />
  );
  const body = (
    <span className="min-w-0 flex-1">
      <span className={cn("block font-semibold leading-tight", tile ? "text-lg" : "text-base")}>{title}</span>
      {desc && <span className="mt-0.5 block text-sm text-ink-muted">{desc}</span>}
    </span>
  );
  const end = href ? (
    <ChevronRight
      size={16}
      strokeWidth={1.75}
      aria-hidden="true"
      className="ml-auto shrink-0 text-ink-faint transition-transform duration-(--duration-fast) hover-hover:group-hover:translate-x-0.5"
    />
  ) : (
    /* A NOTE, NOT A STATUS CHIP. Set as small capitals it read as a label of the same
       weight as a table head, and nine of them down the Monthly Menu formed a second
       column that competed with the nine titles it was annotating. Sentence case in the
       quiet ink says the same thing and lets the names be read first. */
    <span className="ml-auto shrink-0 whitespace-nowrap text-sm text-ink-muted">{note}</span>
  );
  // A row reads across; a card puts the icon and the way in on one line, above its words.
  const parts = tile ? <><span className="flex items-start gap-3">{icon}{end}</span>{body}</> : <>{icon}{body}{end}</>;

  return href ? (
    <Link to={href} onClick={onSelect} className={cn(tile ? TILE : ROW, LIVE)}>{parts}</Link>
  ) : (
    <div className={cn(tile ? TILE : ROW, "text-ink-muted")}>{parts}</div>
  );
}

/**
 * A menu page's body: its cards as one ruled sheet (RF). Eight pages -- Home, the four admin menus, ACB, Archives and
 * Reports -- each wrote this sheet and this map, differing only in the React key they chose and in what a press
 * tells the shell. The key is `href` and title together, because two menus hold entries with no route yet.
 *
 * `onSelect(card)` is the page's word to the shell; `wide` is Home's, the one menu that spans the page. `className`
 * places the sheet -- month end's page (`04.1`) sets its lists under section headings, a step closer than a page head.
 * `tiles` lays the same cards out as cards (Home's card view): one a row on a phone, two from `md`, three from `lg`.
 */
export function NavigationList({ cards, onSelect, wide = false, tiles = false, className }) {
  const items = cards.map((card) => (
    <NavigationCard
      key={`${card.href}-${card.title}`}
      {...card}
      layout={tiles ? 'tile' : 'row'}
      onSelect={onSelect && (() => onSelect(card))}
    />
  ));
  return tiles ? (
    <div className={cn("mt-6 grid gap-4 md:grid-cols-2 lg:grid-cols-3", className)}>{items}</div>
  ) : (
    <Sheet className={cn("mt-6 divide-y divide-line", !wide && "max-w-4xl", className)}>{items}</Sheet>
  );
}
