import { Loader2 } from 'lucide-react';

import { cn, pick } from '../../lib/utils';

/**
 * The button. `03.2a`.
 *
 * 🔴 WHAT THIS REPLACED, AND WHY THE REPLACEMENT IS NOT A RE-SKIN. The previous
 * implementation hard-coded `cyan-500`, `gray-300` and eleven `dark:` twins across four
 * variants -- 22 colour decisions that the token layer already owns, restated by hand,
 * and therefore free to drift from it. Every colour here is now a token, so the dark
 * theme follows without a single `dark:` prefix and P2's direction reaches this control
 * without anybody editing it.
 *
 * 🔑 `loading` IS A STATE, NOT A CALLER'S PROBLEM. A submit button that stays clickable
 * while its request is in flight is how this application gets duplicate members, and
 * every call site solving it separately is 53 chances to forget. `loading` disables the
 * control, sets `aria-busy`, and swaps the leading slot for a spinner while KEEPING the
 * label -- a button whose text vanishes mid-flight loses its accessible name exactly
 * when a screen-reader user most needs it, and it also resizes, which moves whatever
 * sits beside it.
 *
 * ⚠️ TWO OF THE EIGHT STATES DO NOT APPLY HERE, and saying so is the record
 * (conventions §7, contract ⑬). `invalid` belongs to a control that holds a value --
 * a button holds none, so `aria-invalid` is passed through untouched for the one case
 * that matters, a submit button described by a form-level error summary. `empty`
 * likewise: a button with no children is a defect, not a state, and `Button.test.jsx`
 * asserts the six that do apply plus the ring.
 *
 * 📱 `touch-target` gives a 44px hit area on a coarse pointer WITHOUT growing the
 * visible control, so desktop density is provably untouched (conventions §4, T6).
 */

/* card.css `.btn`: 38px, 14px medium, radius 8, and the resting shadow on anything that
   sits on the page as a control -- the primary and the secondary carry `--shadow-1`, the
   ghost carries nothing until hovered. */
const VARIANTS = {
  /** The neutral control -- card.css `.btn--secondary`. Kept under its old name so no call site moves. */
  default: cn(
    'border-line-strong bg-surface text-ink shadow-(--shadow-1)',
    'hover-hover:hover:bg-surface-2',
    'active:bg-surface-2',
  ),

  /** The one action a surface wants you to take -- card.css `.btn--primary`. */
  primary: cn(
    'border-brand bg-brand text-on-brand shadow-(--shadow-1)',
    'hover-hover:hover:border-brand-hover hover-hover:hover:bg-brand-hover',
    'active:border-brand-hover active:bg-brand-hover',
  ),

  /** Quiet, for a control that must not compete -- card.css `.btn--ghost`. */
  ghost: cn(
    'border-transparent bg-transparent text-ink-muted',
    'hover-hover:hover:bg-surface hover-hover:hover:text-ink',
    'active:bg-surface active:text-ink',
  ),

  /**
   * Destructive. NEW in `03.2a`: the old set had none, so every delete in this
   * application is currently a `default` button beside a `confirm()`. `03.4` migrates
   * those four dialogs and needs a control that looks like what it does.
   */
  danger: cn(
    'border-status-suspended-dot bg-status-suspended-bg text-status-suspended',
    'hover-hover:hover:border-status-suspended hover-hover:hover:bg-status-suspended-dot hover-hover:hover:text-on-brand',
    'active:border-status-suspended active:bg-status-suspended-dot active:text-on-brand',
  ),

  /** Pager controls. Same skin as `default`; a separate name so P5's Table can retune it alone. */
  pagination: cn(
    'border-line-strong bg-surface text-ink shadow-(--shadow-1)',
    'hover-hover:hover:bg-surface-2',
    'active:bg-surface-2',
  ),
};

const SIZES = {
  /** card.css `.btn--sm`: 32px, 13px. */
  sm: 'min-h-(--size-control-sm) gap-1.5 px-2.5 py-1 text-sm',
  /** card.css `.btn`: 38px, 14px, 14px of side padding. */
  md: 'min-h-(--size-control) gap-2 px-3.5 py-2 text-base',
  /** Square, for an icon with no label -- card.css `.iconbtn`. `aria-label` is then the caller's obligation. */
  icon: 'min-h-(--size-control) min-w-(--size-control) justify-center p-2',
};

/**
 * 🔗 `href` MAKES IT A LINK, AND THAT IS THE POINT RATHER THAN A CONVENIENCE.
 *
 * A control that navigates or downloads is an `<a>`: it belongs in the link rota of a
 * screen reader, it opens in a new tab on the modifier the user already knows, and it
 * carries a destination the browser can show in the status bar. Rendered as a `<button>`
 * with an `onClick` that sets `location`, none of that is true — and the alternative,
 * hand-styling an anchor to look like this control, is how a design system grows a second
 * button that drifts from the first (BR-4's whole story, one element down).
 *
 * `type`, `disabled` and `loading` are a button's properties and do not apply; a link that
 * must be unavailable is not rendered, or is rendered without an `href`.
 */
export default function Button({
  children,
  onClick,
  href,
  type = 'button',
  variant = 'default',
  size = 'md',
  disabled = false,
  loading = false,
  className = '',
  ...rest
}) {
  const isDisabled = disabled || loading;

  const Tag = href ? 'a' : 'button';
  const tagProps = href
    ? { href, onClick }
    : { type, onClick, disabled: isDisabled, 'aria-busy': loading || undefined };

  return (
    <Tag
      {...tagProps}
      {...rest}
      className={cn(
        'touch-target inline-flex items-center whitespace-nowrap rounded-lg border font-medium',
        'transition-[color,background-color,border-color,box-shadow] duration-(--duration-instant) ease-(--ease-out)',
        // card.css `.btn:active { transform: translateY(1px) }` -- the press you can feel.
        'active:translate-y-px',
        'focus:outline-none focus-visible:ring-2 focus-visible:ring-brand-ring',
        'disabled:cursor-not-allowed disabled:opacity-50 disabled:shadow-none disabled:active:translate-y-0',
        pick(SIZES, size, 'Button size'),
        pick(VARIANTS, variant, 'Button variant'),
        className,
      )}
    >
      {loading && (
        <Loader2
          aria-hidden="true"
          className="size-4 shrink-0 animate-spin motion-reduce:animate-none"
        />
      )}
      {children}
    </Tag>
  );
}

export const BUTTON_VARIANTS = Object.freeze(Object.keys(VARIANTS));
export const BUTTON_SIZES = Object.freeze(Object.keys(SIZES));
