import { LogIn } from 'lucide-react';
import { Link, useLocation } from 'react-router-dom';

import { useAuth } from '../../auth/useAuth';
import { loginPath } from '../../auth/next';

/**
 * "Your session has ended" -- OPEN mode's answer to a session the server ended (signin.md, "The client"). Required mode
 * sends the person to sign in instead (`AppLayout`), so this only ever shows where the page is still usable without one.
 *
 * 🔑 THE PAGE STAYS. Whatever was on screen is kept, and the person chooses whether to sign in again now; a redirect
 * here would throw away a half-filled form for a session nobody needed to keep using that screen.
 *
 * A polite `status`, never a focus grab: it arrives when some request is refused, which is in the middle of whatever the
 * person was doing.
 */
export default function SessionNotice() {
  const { sessionEnded, dismissSessionEnded } = useAuth();
  const location = useLocation();
  if (!sessionEnded) return null;

  return (
    <div role="status" className="mx-auto mb-4 flex max-w-6xl items-start gap-2 rounded-lg border border-line-strong bg-surface-2 p-3 text-sm text-ink">
      <LogIn aria-hidden="true" className="mt-0.5 h-4 w-4 shrink-0 text-brand" strokeWidth={1.75} />
      <p className="flex-1">
        Your session has ended. <Link className="font-medium underline" to={loginPath(location)}>Sign in again</Link> to
        carry on where you were; anything on this page that needs no sign-in still works.
      </p>
      <button
        type="button"
        onClick={dismissSessionEnded}
        className="touch-target shrink-0 rounded-lg px-2 py-1 underline underline-offset-2 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-brand-ring"
      >
        Dismiss
      </button>
    </div>
  );
}
