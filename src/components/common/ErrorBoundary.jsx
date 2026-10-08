import { Component } from 'react';
import { AlertTriangle } from 'lucide-react';

import reportError from '../../lib/reportError';

/**
 * THE APPLICATION'S ONLY ERROR BOUNDARY. R-ROB-A, register row A11.
 *
 * 🔴 There was none. A throw during render in any component unmounted the ENTIRE
 * application and left a white page -- no message, no way back, nothing in the UI to
 * say anything had happened. `03.1` made that worse before it made it better: every
 * route is now `lazy()`, so a chunk that fails to load (a bad deploy, a network blip,
 * a stale index.html pointing at a hash that no longer exists) throws during render
 * on a completely healthy application.
 *
 * It shipped in `03.1`, rather than in `06.1` where its fallback is designed, because
 * `03.1` is the unit that introduced the failure mode. ✅ **`06.1b` HAS NOW DESIGNED IT**,
 * and the rest of this paragraph is what it must already have done --
 *
 *   - say something happened, in words, rather than showing nothing
 *   - offer a way BACK, because a boundary with no exit is a white page with text
 *   - hand the error to `onError` so a TEST can attribute the failure to a route
 *
 * That last one is why the route smoke test can name the page that broke. Without a
 * boundary a failed import surfaces as an unattributed "1 unhandled error" after the
 * run, and the test for the broken route reports GREEN.
 */
export default class ErrorBoundary extends Component {
  constructor(props) {
    super(props);
    this.state = { error: null };
  }

  static getDerivedStateFromError(error) {
    return { error };
  }

  componentDidCatch(error, info) {
    /*
     * 🔑 IT REPORTS THROUGH THE SINK NOW (EH-D10), where before it deliberately said
     * nothing. The old comment's reasoning was that `main.jsx` already listens and a
     * boundary that logs unconditionally makes a HANDLED error look unhandled -- which was
     * right about the noise and wrong about the outcome: the result was that the one class
     * of failure that blanks a whole screen was the only one leaving no trace at all.
     *
     * ⚠️ The sink is what makes it safe to say something. A render error's message
     * interpolates the offending VALUE -- on these screens, a member -- so `reportError`
     * emits `kind`, `status` and `retryable` and never the message. A bare `console.error`
     * here would have printed the member.
     */
    reportError('ErrorBoundary', error);
    this.props.onError?.(error, info);
  }

  render() {
    if (!this.state.error) return this.props.children;

    if (this.props.fallback) {
      return this.props.fallback(this.state.error, () => this.setState({ error: null }));
    }

    /*
     * ✅ THE DESIGNED FALLBACK. `06.1b`, closing what `03.1` left open on A11.
     *
     * 🗣️ THE AUTOMATION ARC'S CONSTRAINT, VERBATIM AND UNPROMPTED (2026-09-09, on being
     * told A11 had closed): *"keep the sentence naming what failed and the one action that
     * recovers it, and never a stack trace or a chunk name."* That is a constraint on this
     * unit, and it is the whole specification of what is below.
     *
     * 🔴 WHY "NEVER A CHUNK NAME" IS THE SHARP HALF. `03.1` made every route `lazy()`, so
     * the most likely thing to arrive here is not a bug in a component -- it is a chunk that
     * failed to load, because the user is on a stale `index.html` pointing at a hash that a
     * deploy replaced. The error's own message for that is *"Failed to fetch dynamically
     * imported module: /assets/Members-B7fQ2x.js"*. Rendering it would show a member of a
     * trade union a build artefact's content hash, and the one action that actually fixes
     * their situation -- reload, and get the new index -- is not in that sentence anywhere.
     *
     * 🔑 SO THE TWO RECOVERIES ARE DIFFERENT ACTIONS AND BOTH ARE OFFERED, in the order
     * that costs least:
     *
     *   "Try again"   re-renders the subtree. Right for a transient failure, and it keeps
     *                 everything the user has typed on other screens.
     *   "Reload"      fetches a fresh index.html. The ONLY thing that fixes a stale-chunk
     *                 failure, and `Try again` on one of those simply throws again --
     *                 which is a loop the user cannot get out of if it is the only button.
     *
     * ⚠️ NOTHING IS SAID ABOUT WHAT THE USER TYPED. The plain fallback used to promise
     * *"Nothing you entered has been sent"*, and this boundary cannot know that: a render
     * that threw AFTER a successful save is exactly as likely as one that threw before it.
     * A reassurance that is sometimes false is worse than none.
     */
    return (
      <div
        data-testid="route-error"
        role="alert"
        className="flex-1 flex flex-col items-center justify-center gap-4 p-8 text-center"
      >
        <AlertTriangle
          aria-hidden="true"
          className="size-8 text-status-suspended"
          strokeWidth={1.5}
        />

        <p className="text-lg font-medium text-ink">This page could not be displayed.</p>

        <p className="max-w-prose text-sm text-ink-muted">
          Something went wrong while loading it. The rest of the application is still
          working, so you can carry on somewhere else.
        </p>

        <div className="flex flex-wrap items-center justify-center gap-2">
          <button
            type="button"
            onClick={() => this.setState({ error: null })}
            className="touch-target rounded-lg bg-brand px-4 py-2 text-sm font-medium text-on-brand hover-hover:hover:bg-brand-hover focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-brand-ring"
          >
            Try again
          </button>
          <button
            type="button"
            onClick={() => window.location.reload()}
            className="touch-target rounded-lg border border-line-strong px-4 py-2 text-sm font-medium text-ink hover-hover:hover:bg-surface-2 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-brand-ring"
          >
            Reload the page
          </button>
        </div>
      </div>
    );
  }
}
