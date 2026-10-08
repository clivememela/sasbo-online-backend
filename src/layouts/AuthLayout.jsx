import { Suspense } from 'react';
import { Outlet, useLocation } from 'react-router-dom';
import Footer from '../components/Footer';
import ErrorBoundary from '../components/common/ErrorBoundary';
import RoutePending from '../components/common/RoutePending';
import ThemeToggle from '../components/common/ThemeToggle';

export default function AuthLayout() {
  const { pathname } = useLocation();

  return (
    <div className="bg-bg min-h-dvh transition-colors duration-300 flex flex-col">
      {/* 🔑 04.1 MERGED THE SECOND THEME BUTTON AWAY. This was a hand-rolled copy of
          `ThemeToggle` — the same job in different markup, and therefore a second place
          for every future accessibility fix to be applied to only one of. P1 left it
          deliberately, because consuming the shared control changes how the auth screens
          look and P1 was a like-for-like colour migration; lifting that constraint is
          this wave's job. What it buys: one accessible name, one `aria-pressed`, one
          focus ring, one hit area. The floating presentation survives as `compact`. */}
      <div className="absolute top-4 right-4 z-50">
        <ThemeToggle compact />
      </div>
      <div className="flex-1 flex flex-col">
        {/* The auth screens are lazy too, and they are the FIRST thing anyone loads --
            so this is the outlet most likely to meet a chunk that never arrives. */}
        {/* Keyed by route for the reason `Main` gives (R1-F1): a boundary that has caught renders its fallback
            whatever its children become, so /login would keep showing the failure of /forgot-password. */}
        <ErrorBoundary key={pathname}>
          <Suspense fallback={<RoutePending />}>
            <Outlet />
          </Suspense>
        </ErrorBoundary>
      </div>
      {/* 🔴 THE FOOTER SAT ON TOP OF THE LOGIN CARD -- on the first screen anybody sees.
          It was `<Footer absolute />`: pinned to the bottom of the viewport at `z-50`, with a
          translucent blurred ground, so it took no space in the layout and painted over
          whatever reached the bottom. On a laptop-height window the card does reach the
          bottom, and "Don't have an account?" ran underneath a frosted band reading
          "© 2026 SASBO - The Finance Union". The owner noticed, and it was the login page.

          In flow, after a `flex-1` content column, the footer lands at the bottom of the
          window when the card is short and below the card when it is tall. It cannot
          overlap anything, because it is no longer drawn over the page -- it is part of it. */}
      <Footer />
    </div>
  );
}
