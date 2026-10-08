import { flushSync } from 'react-dom';
import { SunMoon } from 'lucide-react';
import { useTheme } from '../../context/useTheme';
import { pourTheme } from '../../lib/themePour';
import { cn } from '../../lib/utils';

/**
 * The theme control. N7, and since 2026-09-10 card.html's `Appearance · Light`.
 *
 * 🔴 IT WAS A `<div onClick>`, so the only way to change theme was a mouse: no tab
 * stop, no Enter, no Space, nothing announced. `01.2` had N7 on its list and shipped
 * without it, and nothing noticed until the contrast phase's theme check looked for a
 * BUTTON and found none.
 *
 * `aria-pressed` rather than `role="switch"`: this toggles a property of the page the
 * user is already on, which is what a toggle button is for. The visible label names the
 * property and its current value -- "Appearance · Light" -- exactly as the mock's rail
 * foot does, and `aria-pressed` carries whether dark is on.
 *
 * 🔑 `04.1` MERGED THE SECOND ONE INTO THIS. `AuthLayout` carried its own theme button:
 * same job, different markup, and -- because it was written separately -- a different
 * accessible name and no `aria-pressed` at first. Two implementations of one control is
 * two places for the next accessibility fix to be applied to only one of. The auth
 * screens use the `compact` presentation of this same control.
 *
 * 🎬 THE POUR. The switch is the one cinematic moment card.html has: the new appearance
 * pours out of this button across the whole window (`themePour.js`, index.css "The theme
 * switch"). The DOM change has to land INSIDE the view transition's callback, so the
 * React update is flushed synchronously there; the `.dark` class is toggled directly as
 * well because `ThemeContext`'s effect would otherwise apply it a frame late, after the
 * new snapshot had already been taken.
 */
export default function ThemeToggle({ variant = 'nav', compact = false, className }) {
  const { darkMode, setDarkMode } = useTheme();
  const label = darkMode ? 'Dark' : 'Light';

  const onClick = (event) => {
    pourTheme({
      origin: event.currentTarget,
      next: !darkMode,
      apply: (next) => {
        document.documentElement.classList.toggle('dark', next);
        flushSync(() => setDarkMode(next));
      },
    });
  };

  if (compact || variant === 'compact') {
    return (
      <button
        type="button"
        onClick={onClick}
        aria-pressed={darkMode}
        className={cn(
          'touch-target grid size-10 place-items-center rounded-full bg-surface text-ink shadow-(--shadow-1)',
          'transition-colors duration-(--duration-instant) hover-hover:hover:bg-surface-2',
          'focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-brand-ring',
          className,
        )}
      >
        {/* The same string a sighted user reads on the rail, so the two presentations
            share one accessible name. */}
        <span className="sr-only">Appearance · {label}</span>
        <SunMoon size={20} strokeWidth={1.75} aria-hidden="true" />
      </button>
    );
  }

  return (
    <button
      type="button"
      onClick={onClick}
      aria-pressed={darkMode}
      className={cn(
        // `touch-size`: a 44px row on a coarse pointer, like the rail rows above it (`07.2`).
        'touch-size flex h-9 w-full items-center gap-3 rounded-lg px-2.5 text-left text-base font-medium text-nav-ink',
        'transition-[background-color,color] duration-(--duration-instant)',
        'hover-hover:hover:bg-nav-hover hover-hover:hover:text-ink',
        'focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-brand',
        className,
      )}
    >
      <SunMoon size={18} strokeWidth={1.75} aria-hidden="true" className="shrink-0 text-nav-icon" />
      <span className="truncate">
        Appearance · <b className="font-semibold text-ink">{label}</b>
      </span>
    </button>
  );
}
