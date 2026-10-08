import { useEffect, useState } from 'react';
import { AlertTriangle, Inbox, Loader2, WifiOff } from 'lucide-react';

import { cn } from '../../lib/utils';
import { ERROR_KINDS, SLOW_NOTICE_MS } from '../../services/parseApiError';
import Button from '../form/Button';

/**
 * THE FOUR STATES, ONCE. `06.1b`.
 *
 * 🔴 **WHY THIS IS A COMPONENT AND NOT A CONVENTION.** Before this file the application
 * carried eight separate empty-state copies across six files, three separate spinners and
 * exactly one error state (`Table`'s). A convention -- *"render an error state"* -- is
 * something every one of twenty-seven surfaces has to remember; the ones that forgot are
 * how A27 came to be a CLASS rather than a row. The states are a component so that
 * forgetting is not possible: a caller who has a state to render has this to render it
 * with, and a caller who does not is visible as a caller not using it.
 *
 * ---
 *
 * 🔑 **THE FOURTH STATE IS `partial`, AND IT IS THE ONE NOBODY HAS.** `loading`, `empty`
 * and `error` are the three every framework ships with. A screen with nine mount fetches
 * (`CaptureMember`) or four (`Members`) has a fourth outcome that none of them describe:
 * **three of four sources answered.** Rendered as "loaded" it is a quiet lie -- a form
 * whose bank list is missing looks exactly like a bank list that is genuinely empty.
 * Rendered as "error" it blanks a screen that is 75% usable.
 *
 * The automation arc's own reporting tool is the model, and it is where this came from:
 * it marks a section `incomplete` and prints `DID NOT RUN` rather than zero, because a
 * zero that means "we did not look" and a zero that means "we looked and there was
 * nothing" are different facts that must not share a rendering.
 *
 * ---
 *
 * 🔑 **`error` NEVER RENDERS THE EMPTY COPY** (conventions §8, EH-D4). The branches are
 * mutually exclusive by construction rather than by four conditions agreeing with each
 * other, and the error branch always offers a way out when one is honest -- see
 * `retryable` below.
 *
 * 🔴 **RETRY IS OFFERED WHEN IT CAN WORK, NOT WHENEVER THERE IS AN ERROR.** `06.1a` derives
 * `retryable` once, in `parseApiError`, precisely so that no consumer re-derives it from a
 * status code. A 4xx is the server saying the request was wrong: retrying it unchanged
 * produces the same refusal, and a button that cannot work is worse than no button. So the
 * control appears when `onRetry` is given AND the error does not say `retryable: false`.
 * An error object with no `retryable` field at all -- a thrown `TypeError`, say -- is
 * treated as retryable, because the alternative is a dead end on the failures we understand
 * least.
 *
 * 🗣️ **THE COPY RULE, EH-D12**: what happened and what to do next, in the reading voice,
 * and **no code in the sentence**. `status` and the envelope live behind `detail`, which
 * this renders in a `<details>` a reader has to open -- present for the person diagnosing
 * it, absent for the person who just wants their list back.
 */

/** @typedef {'loading'|'empty'|'error'|'partial'} DataStateKind */

/**
 * The copy each state shows when the caller does not override it.
 *
 * All four, not the two that needed defaults -- for the reason `parseApiError` hoists all
 * six of its strings: a reader cannot tell "the vocabulary" from "the incidental" when only
 * half of it is in one place.
 */
const DEFAULTS = {
  loading: { title: 'Loading…', message: null },
  /*
   * 🔑 THE SAME STATE, LATER, AND IT IS A DIFFERENT SENTENCE ON PURPOSE (EH-D2).
   *
   * A spinner communicates one thing for about two seconds and then stops communicating
   * anything: after that it is indistinguishable from a hang, and the user's model quietly
   * changes from "it is working" to "it is broken" with nothing on screen to justify either.
   * A14 exists because that ambiguity has the longest time-to-detection in this application
   * — an administrator reads a permanent spinner as "the system is slow" rather than "it is
   * down", so nobody is called.
   *
   * The 5s copy is the honest middle: still working, longer than it should be, and the
   * ceiling behind it means this cannot go on forever.
   */
  slow: {
    title: 'This is taking longer than expected.',
    message: 'Still waiting for the server. It will stop trying shortly.',
  },
  empty: { title: 'Nothing here yet', message: null },
  error: { title: 'We could not load this.', message: null },
  partial: {
    title: 'Some of this is missing.',
    message: 'What loaded is shown below. The rest did not answer.',
  },
};

/**
 * What a CALLER may ask for. `slow` is deliberately absent: it is `loading` after
 * `SLOW_NOTICE_MS`, decided here, and a caller that could pass it would be able to claim a
 * request is late without one being in flight.
 */
const PUBLIC_STATES = new Set(['loading', 'empty', 'error', 'partial']);

const ICONS = {
  loading: Loader2,
  slow: Loader2,
  empty: Inbox,
  error: AlertTriangle,
  partial: AlertTriangle,
};

/**
 * Is a retry control honest for this failure?
 *
 * @param {unknown} error the rejection, in `conventions.md` §16's shape
 * @returns {boolean}
 */
const canRetry = (error) => error?.retryable !== false;

/**
 * Is this failure the network being gone rather than the server refusing?
 *
 * 🔑 Read off `kind`, never off the message. §16 fixes the vocabulary at five values so
 * that a consumer can branch on it; matching on the sentence is how a copy edit silently
 * turns an offline banner off.
 */
const isOffline = (error) => error?.kind === ERROR_KINDS.NETWORK;

/**
 * @param {{
 *   state: DataStateKind,
 *   title?: string,
 *   message?: string,
 *   error?: unknown,
 *   onRetry?: () => void,
 *   compact?: boolean,
 *   layout?: 'column'|'row',
 *   action?: React.ReactNode,
 *   className?: string,
 *   'data-testid'?: string,
 * }} props
 *
 * `layout="row"` is card.html's `.state`: the icon on the left, the sentence beside it,
 * the one action on the right -- the shape a register's state takes inside its sheet.
 * The centred column stays for panels and fields. `action` is a caller's own control for
 * the empty state (the register's "Clear"); the retry is still this component's.
 */
export default function DataState({
  state,
  title,
  message,
  error = null,
  onRetry,
  compact = false,
  layout = 'column',
  action,
  className,
  'data-testid': testId,
}) {
  /*
   * ⚠️ VALIDATED AGAINST `PUBLIC_STATES`, NOT AGAINST `DEFAULTS`, and the difference is not
   * cosmetic. `DEFAULTS` gained a `slow` entry when the 5s notice landed, and a check written
   * as `if (!DEFAULTS[state])` would have quietly started ACCEPTING `state="slow"` from a
   * caller -- a fifth value nobody documented, arriving through a guard that was supposed to
   * be closing the vocabulary. `slow` is this component's own business: it is `loading`,
   * later, and no caller should be able to assert it.
   */
  if (!PUBLIC_STATES.has(state)) {
    throw new Error(
      `DataState was given state "${state}". Known states: ${[...PUBLIC_STATES].join(', ')}. ` +
        `A typo that fell back to a default would decide, silently, whether a failed fetch ` +
        `renders as "nothing found" -- which is the whole of A27.`,
    );
  }

  /*
   * 🔑 THE THRESHOLD IS `SLOW_NOTICE_MS`, IMPORTED, NOT A `5000` TYPED HERE.
   *
   * §16 puts the notice at 5s and the read ceiling at 10s, and the relationship between
   * them is the point: the notice has to arrive BEFORE the deadline or it never renders at
   * all. Two numbers in two files drift, and the way this one drifts is silent — a ceiling
   * lowered to 4s would simply mean nobody ever sees the notice, and no test that checks
   * either number alone would notice.
   *
   * ⚠️ THE TIMER IS ONLY ARMED WHILE LOADING. A component that armed it unconditionally
   * would leave a pending timeout behind on every error and empty state — and under the
   * suite's fake timers a stray deadline fires into whichever test runs next, which is the
   * anti-flake rule's own worst case.
   */
  const [slow, setSlow] = useState(false);

  useEffect(() => {
    if (state !== 'loading') return undefined;

    const timer = setTimeout(() => setSlow(true), SLOW_NOTICE_MS);

    /*
     * 🔑 THE RESET IS IN THE CLEANUP, NOT IN THE BODY, and it has to be somewhere: a
     * `loading -> error -> loading` cycle with a stale `slow` would show "taking longer than
     * expected" the INSTANT the second attempt began, which is a false statement about a
     * request that is a few milliseconds old. Retry makes that cycle the common case rather
     * than an edge one.
     *
     * Putting it here rather than in the body also keeps the effect from calling `setState`
     * synchronously on every state change, which is a cascading render for a value that is
     * false already almost every time.
     */
    return () => {
      clearTimeout(timer);
      setSlow(false);
    };
  }, [state]);

  // Rendered as `slow` while the caller still legitimately says `loading` -- the fetch has
  // not failed and nothing about the caller's state has changed, so this stays the
  // component's business rather than becoming a fifth value every caller has to track.
  const shown = state === 'loading' && slow ? 'slow' : state;

  const isFailure = shown === 'error' || shown === 'partial';
  const offline = isFailure && isOffline(error);
  const Icon = offline ? WifiOff : ICONS[shown];

  // The server's own sentence beats ours: it is the one written about THIS failure. Ours
  // is the fallback for a rejection that carries nothing worth reading.
  const headline =
    // ⚠️ The caller's `title` is deliberately IGNORED once a load has gone slow: it says
    // "Loading…" or names the panel, and the whole point of the 5s notice is that the
    // original copy has stopped being true.
    (shown === 'slow' ? DEFAULTS.slow.title : title) ??
    (shown === 'error' && typeof error?.message === 'string' && error.message.trim()
      ? error.message
      : DEFAULTS[shown].title);
  const body = (shown === 'slow' ? DEFAULTS.slow.message : message) ?? DEFAULTS[shown].message;

  /*
   * 🔑 `status` VS `alert`, AND IT IS NOT INTERCHANGEABLE. A loading or empty state is a
   * polite update -- a screen reader should hear it when it gets there. A failure
   * interrupts, because the user is about to act on a list that is not what they think it
   * is. EH-D6 draws the same line for notices: a result is `status`, a refusal is `alert`.
   */
  const role = isFailure ? 'alert' : 'status';

  const showRetry = isFailure && typeof onRetry === 'function' && canRetry(error);

  // The diagnostic line, and only when there is genuinely something to diagnose. `status: 0`
  // is what a network failure carries and says nothing a reader can use.
  const detail = [
    error?.kind && error.kind !== ERROR_KINDS.HTTP ? error.kind : null,
    error?.status ? `HTTP ${error.status}` : null,
  ]
    .filter(Boolean)
    .join(' · ');

  const row = layout === 'row';
  const busy = shown === 'loading' || shown === 'slow';

  const icon = (
    <Icon
      aria-hidden="true"
      className={cn(
        compact && !row ? 'size-4' : 'size-6',
        busy && 'animate-spin motion-reduce:animate-none',
        shown === 'empty' && 'text-ink-faint',
        isFailure && 'text-status-suspended',
        busy && 'text-ink-muted',
      )}
    />
  );

  const details = isFailure && detail && (
    <details className="text-xs text-ink-muted">
      {/* `touch-size`: measured under real touch emulation this disclosure offered a 49×15 target (`07.2`). An overlay
          was tried first and put its square on the Try again beside it; grown to a real 44px box, the two abut. */}
      <summary className="touch-size cursor-pointer rounded focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-brand-ring">
        Details
      </summary>
      <p className="mt-1">{detail}</p>
    </details>
  );

  const retry = showRetry && (
    <Button variant="default" size={row ? 'md' : 'sm'} onClick={onRetry}>
      Try again
    </Button>
  );

  // What a reader and a test know the frame by, identical in both shapes -- spelled once (RF).
  const frame = {
    role,
    ...(role === 'status' && { 'aria-live': 'polite' }),
    'data-testid': testId,
    'data-state': shown,
  };

  if (row) {
    return (
      <div
        {...frame}
        className={cn(
          'grid items-center gap-4 text-left',
          compact ? 'px-4 py-3' : 'px-6 py-7',
          busy ? 'grid-cols-[auto_auto] justify-start text-ink-muted' : 'grid-cols-[auto_1fr_auto]',
          className,
        )}
      >
        {icon}
        <div className="grid gap-0.5">
          <p className={cn('text-ink', busy ? 'text-base font-medium text-ink-muted' : 'text-md font-semibold')}>
            {headline}
          </p>
          {body && <p className="max-w-[60ch] text-base text-ink-muted">{body}</p>}
          {details}
        </div>
        {!busy && (retry || action) && (
          <div className="flex items-center gap-2">
            {retry}
            {action}
          </div>
        )}
      </div>
    );
  }

  return (
    <div
      {...frame}
      className={cn(
        'flex flex-col items-center text-center',
        compact ? 'gap-1.5 py-2' : 'gap-3 py-6',
        className,
      )}
    >
      {icon}

      <p className={cn('font-medium text-ink', compact ? 'text-xs' : 'text-sm')}>{headline}</p>

      {body && <p className={cn('text-ink-muted', compact ? 'text-xs' : 'text-sm')}>{body}</p>}

      {retry}
      {action}

      {details}
    </div>
  );
}
