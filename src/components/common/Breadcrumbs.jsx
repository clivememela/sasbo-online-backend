import { Link, useLocation } from "react-router-dom";
import { ChevronRight } from "lucide-react";
import { navItems } from "../../config/navigation";
import { cn } from "../../lib/utils";

/**
 * The trail in the topbar -- card.html's `.crumbs`: 13px, muted, the current page in ink.
 */
export default function Breadcrumbs() {
  const location = useLocation();
  const pathnames = location.pathname.split("/").filter((x) => x);

  // Helper function to find title from navItems or capitalize path segment
  const getTitle = (path) => {
    // Exact match for top-level or sub-items
    for (const item of navItems) {
      if (item.href === path) return item.title;
      if (item.subItems) {
        const subItem = item.subItems.find(si => si.href === path);
        if (subItem) return subItem.title;
      }
    }

    // Special cases or formatting for segments not explicitly in navItems
    const lastSegment = path.split('/').pop();
    if (!lastSegment) return "Home";

    if (lastSegment.toLowerCase() === "banks") return "Bank / Branch";
    if (lastSegment.toLowerCase() === "apply") return "Member Application";
    if (lastSegment.toLowerCase() === "organizer-report") return "Location Visit";
    if (lastSegment.toLowerCase() === "daily-menu") return "Daily Menu";
    if (lastSegment.toLowerCase() === "miscellaneous") return "Admin Menu";

    // Fallback: capitalize and replace hyphens
    return lastSegment.charAt(0).toUpperCase() + lastSegment.slice(1).replace(/-/g, ' ');
  };

  const link = "rounded-sm transition-colors duration-(--duration-instant) hover-hover:hover:text-ink focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-brand-ring";

  if (location.pathname === "/home") {
    return (
      <nav aria-label="Breadcrumb" className="flex items-center gap-1.5 text-sm text-ink-muted">
        <span aria-current="page" className="font-medium text-ink">Home</span>
      </nav>
    );
  }

  return (
    <nav aria-label="Breadcrumb" className="flex min-w-0 items-center gap-1.5 whitespace-nowrap text-sm text-ink-muted">
      {/* `crumb-lead` is what a narrow topbar drops -- see the note at `.topbar` in
          index.css. Home is one click away in the rail; the page you are on is the thing a
          crumb is for, so it is the half that stays. */}
      <Link to="/home" className={cn(link, "crumb-lead min-w-0 shrink truncate")}>Home</Link>
      {pathnames.map((name, index) => {
        const routeTo = `/${pathnames.slice(0, index + 1).join("/")}`;
        const isLast = index === pathnames.length - 1;
        const displayName = getTitle(routeTo);

        return (
          <span key={routeTo} className={cn("flex min-w-0 items-center gap-1.5", !isLast && "crumb-lead")}>
            {/* ink-faint on the chevron only: card.css calls --ink-3 "icons, dividers;
                never body text", and the labels beside it are ink-muted for that reason. */}
            <ChevronRight size={14} strokeWidth={1.75} aria-hidden="true" className={cn("shrink-0 text-ink-faint", isLast && "crumb-lead")} />
            {/* A CEILING, NOT A SHRINK. The trail keeps its natural width -- it is the
                topbar's `auto` track -- but no single crumb may run away with the row:
                "Affiliations/Other Trade Unions" is a real route name, and at full length
                it would push the search under its own minimum. 22ch clears every ordinary
                name and clips only the outliers, which the page's own heading repeats
                anyway. */}
            {isLast ? (
              /* 🔑 THE CURRENT PAGE GIVES WAY LAST, AND NEVER BELOW 7ch. Everything ahead
                 of it ellipsises first, which is the opposite of what happened before: the
                 trail kept "Home" whole and slid the page you were on underneath the search
                 box. It is not `shrink-0`, because a crumb that refuses to shrink at all
                 overflows instead -- measured on the deepest route in the application,
                 "Home > Admin Menu > Maintenance > Recruitment channel" at 1280, where the
                 leads reach their floor 15px short. A floor of 7ch keeps it a word rather
                 than an ellipsis. */
              <span aria-current="page" className="min-w-[7ch] max-w-[22ch] shrink truncate font-medium text-ink" title={displayName}>{displayName}</span>
            ) : (
              <Link to={routeTo} className={cn(link, "min-w-0 max-w-[18ch] shrink truncate")} title={displayName}>
                {displayName}
              </Link>
            )}
          </span>
        );
      })}
    </nav>
  );
}
