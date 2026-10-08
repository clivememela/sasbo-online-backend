import { useId } from 'react';

import { cn } from '../../lib/utils';

/**
 * THE CHROME EVERY FORM CONTROL WEARS -- label, required marker, hint, error. `03.2a`.
 *
 * 🔴 THIS IS THE END OF THE FOUR-WAY FRACTURE (BR-4). Today this application has four
 * separate answers to "what wraps a form control": the shared `SearchableSelect`, a
 * private `SearchableSelect` at `CaptureMember.jsx:761` that shadows it, and a `Field`
 * at `CaptureMember.jsx:888` and another at `OrganizerReport.jsx:55`. They disagree
 * about the label element, about whether the error is announced, and about whether the
 * control has an accessible name at all. **Two of the four are inside `CaptureMember.jsx`,
 * which the Capture Member arc is actively editing -- those stay theirs until `04.2`.**
 * This is the one the other three converge on.
 *
 * 🔑 IT HANDS DOWN IDS RATHER THAN RENDERING THE CONTROL. A wrapper that renders its own
 * `<input>` cannot wrap a native `<select>`, a listbox trigger and a date sheet, which is
 * how a codebase ends up with four of them. `children` is a function receiving
 * `{ id, describedBy, invalid }`, so the control stays whatever it needs to be while the
 * wiring -- `htmlFor`, `aria-describedby`, `aria-invalid` -- is impossible to get wrong
 * and impossible to forget. Contract ⑮: structural, not remembered.
 *
 * ⚠️ NO `aria-label` ON THE CONTROL. The old primitives put one there beside a visible
 * label. When the two disagree the accessible name silently wins over the text the user
 * can read, so a field announced as "Bank Name" while reading "Branch" is invisible to
 * every check except a screen reader. One name, from the `<label htmlFor>`.
 *
 * 🔑 THE HINT IS SUPPRESSED WHILE AN ERROR SHOWS, and both are in `aria-describedby`
 * only when rendered. A `describedby` naming an element that is not in the document
 * makes the whole description silently empty in several screen readers -- so the id list
 * is built from what actually renders, never from what might.
 */
export default function Field({
  label,
  required,
  error,
  hint,
  className,
  labelClassName,
  children,
}) {
  const id = useId();
  const errorId = `${id}-error`;
  const hintId = `${id}-hint`;

  const describedBy = cn(error && errorId, !error && hint && hintId) || undefined;

  const labelText = (
    <>
      {label}
      {required && (
        <>
          {' '}
          <span aria-hidden="true" className="text-status-suspended">*</span>
          <span className="sr-only">(required)</span>
        </>
      )}
    </>
  );

  return (
    <div className={cn('flex min-w-0 flex-col gap-1', className)}>
      {label && (
        <label
          htmlFor={id}
          // card.css `.field__label` / `.crit__label`: 11.5px, 600, tracked .08em, muted.
          className={cn('text-xs font-semibold uppercase tracking-[0.08em] text-ink-muted', labelClassName)}
        >
          {labelText}
        </label>
      )}

      {children({
        id,
        describedBy,
        invalid: error ? true : undefined,
      })}

      {hint && !error && (
        <span id={hintId} className="text-xs font-normal normal-case tracking-normal text-ink-muted">
          {hint}
        </span>
      )}

      {error && (
        <span
          id={errorId}
          role="alert"
          className="text-xs font-normal normal-case tracking-normal text-status-suspended"
        >
          {error}
        </span>
      )}
    </div>
  );
}

/**
 * The skin every control shares, so `field-text`, the border, the focus ring and the
 * disabled treatment are one decision rather than five.
 *
 * 🔴 `field-text`, NEVER `text-sm`. iOS Safari zooms the page when a focused control is
 * under 16px; this design's `--text-sm` is 13px. `field-text` is 16px on a coarse
 * pointer or under 760px and the design's size otherwise. conventions §5, and
 * `mockups/design/tools/prove_field_text.mjs`, which measures it in a browser because
 * jsdom cannot see a font size.
 */
export function controlClasses({ invalid, className } = {}) {
  return cn(
    // `touch-size`: 44px on a coarse pointer (index.css) -- a replaced element draws no ::after, so `touch-target` cannot.
    'field-text touch-size w-full rounded-lg border px-3 py-2 font-normal normal-case tracking-normal',
    // 🔴 `placeholder:` IS `ink-muted`, NOT `ink-faint`, AND THE DIFFERENCE IS EVERY FORM IN
    // THE APPLICATION. A placeholder is text, so WCAG 1.4.3 applies to it -- and `ink-faint`
    // measures 3.65:1 at worst in dark and 4.15:1 on `bg-raised` in light, against a 4.5:1
    // bar. `card.css:165` puts placeholders on `--ink-2` and says of `--ink-3` "icons,
    // dividers; NEVER body text"; the arc's own `tokens.md` had widened that note to include
    // placeholders, and this line believed the note. Both are corrected at their one home.
    //
    // ⚠️ NO PROBE COULD HAVE CAUGHT IT. axe judges text NODES; a placeholder is an attribute,
    // so the 100-combination pixel sweep passed every one of these controls without ever
    // measuring the colour. `tools/tokenContrast.mjs` measures the PAIR, which is why it exists.
    'min-h-(--size-control) bg-surface text-ink placeholder:text-ink-muted',
    'transition-colors duration-(--duration-instant) ease-(--ease-out)',
    'focus:outline-none focus-visible:ring-2 focus-visible:ring-brand-ring',
    'disabled:cursor-not-allowed disabled:opacity-60',
    invalid ? 'border-status-suspended-dot' : 'border-line-strong',
    className,
  );
}
