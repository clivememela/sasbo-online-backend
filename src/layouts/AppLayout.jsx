import '../assets/styles/App.css'
import { useState } from "react";
import { Navigate, useLocation } from "react-router-dom";
import Sidebar from "../components/Sidebar.jsx";
import Main from "../components/Main";
import { MAIN_CONTENT_ID, PANEL_SLOT_ID } from "../components/shellIds";
import { useAuth } from "../auth/useAuth";
import { loginPath } from "../auth/next";

export default function AppLayout() {
  const [sideBarOpen, setSideBarOpen] = useState(false);
  const [selectedCardHref, setSelectedCardHref] = useState(null);
  /*
   * The global search is the SHELL's, not a page's -- card.html puts it in the topbar
   * because it is how you reach a member from anywhere. The value lives here so the
   * topbar can own the control and the register can read it through the outlet context.
   */
  const [search, setSearch] = useState("");
  const { status, mode, user } = useAuth();
  const location = useLocation();

  /*
   * 🔑 THE TWO DOORS THE SHELL KEEPS (signin.md). In REQUIRED mode nobody reaches a screen without a session: they go
   * to sign in, and `next` brings them back here -- including a session the server has just ended, which leaves this
   * render anonymous. In OPEN mode everyone comes in, as today, and an ended session is a notice in `Main` instead.
   * A temporary password is changed before anything else is opened, in either mode.
   */
  if (mode === "required" && status !== "signed-in") return <Navigate to={loginPath(location)} replace />;
  if (user?.mustChangePassword) {
    return <Navigate to={`/change-password?next=${encodeURIComponent(location.pathname + location.search)}`} replace />;
  }

  // ✅ R-RESP-F, done in P5 as P1 said it would be. `h-screen` is `100vh`, which on a phone
  // is the viewport WITHOUT the browser chrome that is actually on screen -- so the shell was
  // taller than the window and the bottom of every page sat under the address bar. `h-dvh`
  // is the same measurement against the viewport that is really there.
  return (
    /* 📐 card.html's `.app`: ONE ROW -- rail | main | panel. The rail and the panel are
       full-height columns with their own scroll; the main column scrolls under a sticky
       60px topbar. The header, main and footer used to be `position: fixed` and offset by
       hard-coded numbers in three files that could not see each other; the columns are
       real now, so the layout cannot disagree with itself. */
    /* `overflow-clip`, NOT `overflow-hidden`. A hidden overflow can still be scrolled by
       script -- `focus()` and `scrollIntoView()` on anything below the fold shift the whole
       shell by whatever the inner scroller could not absorb, and the topbar slides under
       the top edge. `clip` has no scroll position at all; only the main column scrolls. */
    <div className="bg-bg flex h-dvh overflow-clip">
      {/* 🔑 THE SKIP LINK, AND IT IS THE FIRST FOCUSABLE THING IN THE DOCUMENT — which is
          the entire requirement. This application puts a full navigation tree ahead of the
          content, so without it a keyboard user tabs through every menu item on every
          route before reaching the page. Visible only while focused: `sr-only` until
          `focus-visible`, then a real, positioned control. */}
      <a
        href={`#${MAIN_CONTENT_ID}`}
        className="sr-only focus-visible:not-sr-only focus-visible:fixed focus-visible:left-4 focus-visible:top-4 focus-visible:z-50 focus-visible:rounded-lg focus-visible:bg-brand focus-visible:px-4 focus-visible:py-2 focus-visible:text-on-brand focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-brand-ring"
      >
        Skip to main content
      </a>
      <Sidebar
        sideBarOpen={sideBarOpen}
        setSideBarOpen={setSideBarOpen}
        selectedCardHref={selectedCardHref}
      />
      <Main
        sideBarOpen={sideBarOpen}
        setSideBarOpen={setSideBarOpen}
        setSelectedCardHref={setSelectedCardHref}
        search={search}
        setSearch={setSearch}
      />
      {/* The third column. A page with a record panel portals it here (`MemberPanel`), so
          the panel stands beside the topbar rather than under it. `display: contents`
          makes the portalled <aside> the row's own flex item, and an empty slot costs
          nothing on the pages that have no panel. */}
      <div id={PANEL_SLOT_ID} className="contents" />
    </div>
  );
}
