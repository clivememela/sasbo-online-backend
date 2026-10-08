import { Link, useLocation } from "react-router-dom";
import { ChevronRight } from "lucide-react";
import { cn } from "../../lib/utils";

/**
 * One row of the rail -- card.html's `.nav__item`: 36px, 14px medium, an 18px icon, a
 * 14px chevron on the right for a section, and the active row lifted onto the surface
 * with the resting shadow. Sub-items (`.nav__item--sub`) are 32px, text only, indented
 * under their parent's icon.
 *
 * ♻️ `item.color` IS GONE -- the twin of the dead prop `NavigationCard` carried, found
 * in the same pass and for the same reason: every `color:` in `navigation.js` sits in a
 * commented-out block, so both branches were unreachable and both would have re-admitted
 * a palette literal the day somebody uncommented one.
 */

const ROW =
  "transition-[background-color,color,box-shadow] duration-(--duration-instant) " +
  "focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-brand";
const RESTING = "text-nav-ink hover-hover:hover:bg-nav-hover hover-hover:hover:text-ink";
const ACTIVE = "bg-surface font-semibold text-brand-ink shadow-(--shadow-1)";

/* The sub-item indents: under the parent's icon, then a step further per level. Written
   out rather than computed, because a utility built from `depth` is a class Tailwind
   never generates (`dynamicUtilities.test.js`). */
const INDENT = ["pl-10", "pl-14", "pl-[4.5rem]"];

export default function SidebarItem({ item, isActive, expandedItems, onToggle, onClick, selectedCardHref, depth = 0 }) {
  const location = useLocation();
  const Icon = item.icon;
  const hasSubItems = item.subItems && item.subItems.length > 0;
  const isExpanded = expandedItems?.[item.href];
  const isCardSelected = selectedCardHref === item.href;
  const highlighted = isActive || isCardSelected;

  // A menu entry with no route yet (Maintenance > Bank/Branch) is a label, not a link.
  // Every sub-item renders through this component now, so the id and the link both have
  // to survive an `href` that is not there.
  const subListId = `sidebar-sub-${String(item.href ?? item.title).replace(/\W+/g, '-')}`;
  const linkClass = "flex min-w-0 flex-1 items-center gap-3 self-stretch rounded-lg";

  /*
   * 🔴 `contents`, NOT A BOX — and it is the difference between half-transcribing the mock
   * and transcribing it. card.css:135 is `.nav { display: grid; gap: 2px; flex: 1 }`, and
   * in the mock EVERY row is a direct child of that grid: `<a class="nav__item">Members</a>`
   * is followed by `<a class="nav__item--sub">Capture Member</a>` as a SIBLING. So the
   * stretch is shared by all of them and the pitch is uniform (measured on the mock:
   * 66/65/62/65).
   *
   * With this component's wrapper as a real box, only the top-level rows were grid tracks:
   * the parents spread to 73px while the sub-rows stayed at 34-36 inside their own box, so
   * the rail read as a tight cluster under Members with everything else over-spread. The
   * finish review measured exactly that and called the fix half-made.
   *
   * `display: contents` removes the wrapper's box and promotes its children into the grid,
   * which is the mock's markup expressed in a component that has to nest.
   */
  return (
    <div className="contents">
      {/* 🔴 THIS ROW WAS A `<div onClick>` WRAPPING A `<Link>`, AND BOTH HALVES WERE WRONG.

          For a parent item the div's handler called `onToggle`, so the ONLY way to expand a
          section was a mouse: no tab stop, no role, no key handler, and no `aria-expanded`
          to announce that a section existed at all. A keyboard user reached the `<Link>`,
          pressed Enter, and navigated — the sub-items were unreachable unless the route they
          landed on happened to expand them.

          The disclosure is now its own `<button>` beside the link -- the mock's chevron,
          made real: the link navigates, the button expands, and each says what it is. */}
      <div
        className={cn(
          // `touch-size`: 44px rows on a coarse pointer, so the link's target and its disclosure's never land on a neighbour.
          "touch-size flex items-center rounded-lg pr-1.5",
          depth === 0 ? "h-9 pl-2.5 text-sm font-medium" : cn("h-8 text-[13.5px] font-medium", INDENT[Math.min(depth - 1, INDENT.length - 1)]),
          ROW,
          highlighted ? ACTIVE : RESTING,
        )}
      >
        {item.href ? (
          <Link
            to={item.href}
            className={linkClass}
            onClick={onClick}
            aria-current={isActive ? "page" : undefined}
          >
            {depth === 0 && Icon && (
              <Icon
                size={18}
                strokeWidth={1.75}
                aria-hidden="true"
                className={cn("shrink-0", highlighted ? "text-brand" : "text-nav-icon")}
              />
            )}
            <span className="truncate">{item.title}</span>
          </Link>
        ) : (
          <span className={cn(linkClass, "text-ink-muted")} title="Not available yet">
            {depth === 0 && Icon && (
              <Icon size={18} strokeWidth={1.75} aria-hidden="true" className="shrink-0 text-nav-icon" />
            )}
            <span className="truncate">{item.title}</span>
          </span>
        )}
        {hasSubItems && (
          <button
            type="button"
            aria-expanded={Boolean(isExpanded)}
            aria-controls={isExpanded ? subListId : undefined}
            aria-label={`${isExpanded ? 'Collapse' : 'Expand'} ${item.title}`}
            onClick={() => onToggle(item.href)}
            className={cn(
              "touch-target grid size-6 shrink-0 place-items-center rounded-md",
              "hover-hover:hover:bg-nav-hover focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-brand",
            )}
          >
            <ChevronRight
              size={14}
              strokeWidth={1.75}
              aria-hidden="true"
              className={cn("transition-transform duration-(--duration-instant)", isExpanded && "rotate-90")}
            />
          </button>
        )}
      </div>

      {/* Also `contents`: the sub-rows join the rail's own grid rather than forming a
          second one inside it. The element stays in the document so `aria-controls` on the
          disclosure still resolves to something real. */}
      {hasSubItems && isExpanded && (
        <div id={subListId} className="contents">
          {item.subItems.map((subItem, index) => (
            <SidebarItem
              key={`${subItem.href}-${subItem.title}-${index}`}
              item={subItem}
              depth={depth + 1}
              isActive={location.pathname === subItem.href}
              expandedItems={expandedItems}
              onToggle={onToggle}
              onClick={onClick}
              selectedCardHref={selectedCardHref}
            />
          ))}
        </div>
      )}

    </div>
  );
}
