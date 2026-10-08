import { Suspense, useSyncExternalStore } from "react";
import { Outlet, useLocation } from "react-router-dom";
import Header from "./Header";
import ErrorBoundary from "./common/ErrorBoundary";
import RoutePending from "./common/RoutePending";
import { MAIN_CONTENT_ID } from "./shellIds";
import OfflineBanner from "./common/OfflineBanner";
import { useAuth } from "../auth/useAuth";
// LAZY: it says something only after a session has ended, so the first load does not carry it (OB17a).
import { SessionNotice } from "./account/lazyAccount";
import { PAGE_GUTTER } from "./common/Sheet.jsx";
import { cn } from "../lib/utils";
import { getLastNetworkFailureAt, onNetworkFailure } from "../lib/reportError";

export default function Main({ sideBarOpen, setSideBarOpen, setSelectedCardHref, search, setSearch }) {
  /*
   * 🔑 OFFLINE IS ANNOUNCED ONCE, IN THE SHELL, AND NOT ONCE PER PANEL (EH-D11).
   *
   * It belongs here for the same reason the ErrorBoundary does: it is a fact about the
   * APPLICATION, not about any one screen. A member in a lift whose every panel reports its
   * own honest failure gets three true sentences and not the one that explains them -- and
   * then retries each panel in turn, from a lift.
   *
   * ⚠️ IT DOES NOT SUPPRESS THE PANELS' OWN STATES. A panel that failed while online and a
   * panel that failed because the connection went look alike for a moment and are not the
   * same situation; hiding the first to tidy the second would lose a real failure.
   */
  // An external store read as one (RF): `reportError` already has the subscribe shape, and a state copy plus an
  // effect left a gap between the first render and the subscription.
  const lastNetworkFailureAt = useSyncExternalStore(onNetworkFailure, getLastNetworkFailureAt);
  const { pathname } = useLocation();
  const { sessionEnded } = useAuth();

  return (
    /* 🔴 THE LANDMARKS WERE NESTED WRONG, AND THE COST WAS THAT THERE WERE NONE.
       `<main>` used to be this outer element, with `<Header>` inside it — and HTML says a
       `<header>` maps to the `banner` role ONLY when it is not a descendant of `<main>`,
       `<article>`, `<aside>`, `<nav>` or `<section>`. So the application rendered a header
       and a main region, and a screen reader was offered exactly ONE landmark. The elements
       were right and the nesting silently voided one of them.

       📐 card.html's `.main`: a column that scrolls under a sticky 60px topbar, and it ends
       where the page ends. The footer band that used to sit under every route is gone with
       the mock -- the application's name and the union's are in the rail's wordmark. */
    <div className="flex min-w-0 flex-1 flex-col overflow-y-auto scroll-quiet">
      <Header
        sideBarOpen={sideBarOpen}
        setSideBarOpen={setSideBarOpen}
        search={search}
        setSearch={setSearch}
      />
      {/* `tabIndex={-1}` so the skip link has something it can actually move focus TO —
          a fragment link only moves the caret unless the target is focusable. */}
      <main id={MAIN_CONTENT_ID} tabIndex={-1} className="flex w-full flex-1 flex-col">
        {/* Above the boundary, so it survives a screen that threw -- an offline banner that
            disappears with the screen it was explaining is the one moment it was for. */}
        <div className={cn(PAGE_GUTTER, "pt-5 empty:hidden")}>
          <OfflineBanner
            lastNetworkFailureAt={lastNetworkFailureAt}
            // 🔴 A RELOAD, AND SAYING WHY IT IS NOT A CHEAT MATTERS. Every panel already
            // owns its own retry; this one control stands for "everything that failed while
            // I was offline", which no single panel can re-run. The shell has no handle on
            // the route's fetches -- reaching for one would mean a global request registry,
            // which is a large amount of machinery for a button pressed once a month.
            onRetry={() => window.location.reload()}
          />
          {/* Beside the offline banner and for its reason: a fact about the application, not about one screen. */}
          {sessionEnded && (
            <Suspense fallback={null}>
              <SessionNotice />
            </Suspense>
          )}
        </div>
        {/* 🔴 THE BOUNDARY IS HERE, INSIDE THE SHELL, and the position is the whole point.
            A11's standard-facing test says it in one line: "the shell must survive, and
            offer a way to recover". A boundary wrapped around <Routes> would catch the
            same errors and still blank the header and the navigation, because the layout
            renders INSIDE the routes. One broken panel has to stay one broken panel.

            Suspense sits inside it for the same reason: `03.1` made every route lazy, so
            the outlet is where a chunk arrives -- and where a chunk that fails to arrive
            throws. A boundary above the Suspense would never see it. */}
        {/* 🔴 KEYED BY ROUTE (R1-F1). A boundary holds its error in state and renders the fallback whatever its
            children become, so one page that threw left "This page could not be displayed." on every route the rail
            could reach -- and the fallback's own sentence, "you can carry on somewhere else", was false. A key change
            remounts it, which clears the error for the page you have moved to and keeps it for the one that threw. */}
        <ErrorBoundary key={pathname}>
          <Suspense fallback={<RoutePending />}>
            <Outlet context={{ setSelectedCardHref, setSideBarOpen, search, setSearch }} />
          </Suspense>
        </ErrorBoundary>
      </main>
    </div>
  );
}
