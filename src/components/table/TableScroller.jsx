import { useCallback, useEffect, useRef, useState } from 'react';

import { cn } from '../../lib/utils';

/**
 * THE THING EVERY TABLE SCROLLS INSIDE. `03.2a`.
 *
 * 🔴 THIS IS CONTRACT ⑮ IN ITS ORIGINAL FORM, and the failure it prevents is a matter of
 * record rather than a hypothetical. MobiZone's rule was *"admin tables are wrapped in
 * `overflow-x-auto`"*, carried by memory. It was honoured in seven files and forgotten in
 * the eighth, where it clipped the Actions column off the screen entirely -- their worst
 * responsive defect. Their fix was not the one table; it was a component every table goes
 * through, so *"every table scrolls"* stopped being something an author had to remember.
 *
 * **This repository is already one file into the same story.** `Members.jsx:452` and
 * `LocationListing.jsx:112` wrap their tables; `Subscriptions.jsx:122` does not -- verified
 * from source, not assumed. `05.2` routes all ten tabular surfaces through here, and the
 * point of building it in `03.2a` is that the ten do not each get a decision.
 *
 * 🔑 IT ALSO SHOWS THAT THERE IS MORE TO SEE. A container that scrolls with no visible
 * affordance is a container users do not scroll -- on a trackpad there is no scrollbar
 * until you move, and on touch there is no cue at all. The edge fades appear only on the
 * side that has content beyond it, so the cue is true rather than decorative, and they are
 * `aria-hidden` because a screen reader reaches the cells regardless.
 *
 * ⚠️ `tabIndex={0}` AND A ROLE ARE NOT DECORATION EITHER. A scrollable region that cannot
 * be reached by keyboard is a region a keyboard user cannot scroll -- WCAG 2.1.1, and the
 * reason Firefox and Chrome both ship a devtools warning for it. It carries the caption as
 * its accessible name so the region announces what it holds.
 */
export default function TableScroller({ children, label, className }) {
  const ref = useRef(null);
  const [edges, setEdges] = useState({ start: false, end: false });
  const [scrollable, setScrollable] = useState(false);

  const measure = useCallback(() => {
    const element = ref.current;
    if (!element) return;
    const { scrollLeft, scrollWidth, clientWidth } = element;
    const overflowing = scrollWidth - clientWidth > 1;
    const start = overflowing && scrollLeft > 1;
    const end = overflowing && scrollLeft < scrollWidth - clientWidth - 1;

    setScrollable(overflowing);
    // 🔑 BAIL WHEN NOTHING CHANGED. `onScroll` fires at frame rate, and a fresh object is
    // never `Object.is`-equal -- so a plain `setEdges({...})` re-rendered the ENTIRE table,
    // every row and every cell, on every scroll event. These two booleans only change at
    // the two ends of the range.
    setEdges((previous) =>
      previous.start === start && previous.end === end ? previous : { start, end },
    );
  }, []);

  useEffect(() => {
    const element = ref.current;
    if (!element) return undefined;

    // ResizeObserver is absent in jsdom, so the observer is optional and the re-measure
    // effect below stands on its own. A guard here rather than a polyfill: the affordance
    // is progressive, and a test that cannot resize does not need it.
    const observer =
      typeof ResizeObserver === 'undefined' ? null : new ResizeObserver(measure);
    observer?.observe(element);
    return () => observer?.disconnect();
    // 🔑 `children` is DELIBERATELY not a dep here. It is a fresh React element on every
    // parent render, so including it tore down and rebuilt the ResizeObserver on each one.
    // What `children` actually wants is a re-measure, which is the effect below.
  }, [measure]);

  // Re-measure when the content changes -- the thing the `children` dep was there for.
  useEffect(() => {
    measure();
  }, [measure, children]);

  return (
    <div className={cn('relative', className)}>
      <div
        ref={ref}
        onScroll={measure}
        // Only a region once it can actually scroll: an always-on tab stop over a table
        // that fits is a keyboard user tabbing through nothing on every screen.
        tabIndex={scrollable ? 0 : undefined}
        role={scrollable ? 'region' : undefined}
        aria-label={scrollable ? label : undefined}
        className={cn(
          'w-full overflow-x-auto overscroll-x-contain',
          'focus:outline-none focus-visible:ring-2 focus-visible:ring-brand-ring',
        )}
      >
        {children}
      </div>

      {edges.start && (
        <div
          aria-hidden="true"
          className="pointer-events-none absolute inset-y-0 left-0 w-6 bg-gradient-to-r from-bg/80 to-transparent"
        />
      )}
      {edges.end && (
        <div
          aria-hidden="true"
          className="pointer-events-none absolute inset-y-0 right-0 w-6 bg-gradient-to-l from-bg/80 to-transparent"
        />
      )}
    </div>
  );
}
