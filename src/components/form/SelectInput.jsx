import { useMemo } from 'react';
import { ChevronDown } from 'lucide-react';

import { cn } from '../../lib/utils';
import LoadFailureFrame from './LoadFailureFrame';
import { choicePlaceholder } from './choicePlaceholder';
import Field, { controlClasses } from './Field';
import { asOptionValue, findOption, normaliseOptions } from './options';

/**
 * The native select. `03.2a`.
 *
 * 🔑 STILL NATIVE, DELIBERATELY. A native `<select>` on Android and iOS opens the
 * platform's own wheel or sheet -- a control every member already knows, that handles
 * long lists at 390px better than anything we would build, and that a screen reader
 * announces correctly without a single ARIA attribute. `SearchableSelect` exists for the
 * lists that genuinely need filtering; this is what the other lists should be.
 *
 * 🔴 THE THREE STATES A `<select>` CANNOT SHOW BY ITSELF, and which the previous
 * implementation left the caller to invent each time:
 *
 *   `loading`  the options have not arrived. Rendering an empty select is a lie -- it
 *              says "these are the choices" when the answer is "we do not know yet",
 *              and it is indistinguishable from a genuinely empty list, which is A27's
 *              exact shape one control down (conventions §8).
 *   `empty`    the options arrived and there are none. Says so, and disables itself,
 *              rather than offering a dropdown that opens onto nothing.
 *   `loadError` the options could not be fetched. NOT `error`, which says the VALUE is wrong
 *              and is announced through `Field`: a failed list is nothing the user can fix by
 *              choosing, so it renders as "Unavailable" with a compact error and, where one
 *              could work, a retry beneath (EH-D4). Before `06.4` a failed list reached this
 *              control as an empty one and read "No options available" (F-P54).
 *
 * ⚠️ `onChange` GIVES THE VALUE FIRST AND THE RECORD SECOND. The old signature was
 * `onChange(value)`, and every existing call site keeps working; `onChange(value, option)`
 * is what a caller selecting from real records needs, so it does not have to search its
 * own list again. See `options.js` for why the value is compared as a string.
 */
export default function SelectInput({
  label,
  value,
  onChange,
  options,
  optionValue,
  optionLabel,
  placeholder,
  name,
  error,
  hint,
  required,
  disabled,
  loading = false,
  emptyLabel = 'No options available',
  loadError = null,
  onRetryLoad,
  className,
  ...rest
}) {
  const normalised = useMemo(
    () => normaliseOptions(options, optionValue, optionLabel),
    [options, optionValue, optionLabel],
  );

  const failed = Boolean(loadError) && !loading;
  const isEmpty = !loading && !failed && normalised.length === 0;
  const isDisabled = disabled || loading || isEmpty || failed;

  const handleChange = (event) => {
    const next = event.target.value;
    onChange?.(next, findOption(normalised, next)?.option);
  };

  // The placeholder is a real, non-selectable `<option value="">` rather than a CSS
  // trick, so the control has something to show before a choice is made and cannot be
  // submitted back into the placeholder by keyboard.
  const placeholderText = choicePlaceholder({ loading, failed, isEmpty }, emptyLabel, placeholder);

  const control = (
    <Field label={label} required={required} error={error} hint={hint} className={failed ? undefined : className}>
      {({ id, describedBy, invalid }) => (
        <div className="relative">
          <select
            id={id}
            name={name}
            value={asOptionValue(value)}
            onChange={handleChange}
            // The `.crit__blank` half of card.css:217-218, said as an attribute so one rule in
            // `Criteria` covers this control and `SearchableSelect` alike.
            data-blank={asOptionValue(value) === '' || undefined}
            required={required}
            disabled={isDisabled}
            aria-invalid={invalid}
            aria-describedby={describedBy}
            aria-busy={loading || undefined}
            {...rest}
            className={controlClasses({
              invalid,
              // Room for the chevron, and the UA arrow removed so there are not two.
              className: 'touch-target appearance-none pr-9',
            })}
          >
            {(placeholderText || !findOption(normalised, value)) && (
              <option value="" disabled={required}>
                {placeholderText ?? ''}
              </option>
            )}
            {(failed ? [] : normalised).map((entry) => (
              <option key={entry.value} value={entry.value} disabled={entry.disabled}>
                {entry.label}
              </option>
            ))}
          </select>

          <ChevronDown
            aria-hidden="true"
            className={cn(
              'pointer-events-none absolute right-3 top-1/2 size-4 -translate-y-1/2 text-ink-faint',
              loading && 'animate-pulse motion-reduce:animate-none',
            )}
          />
        </div>
      )}
    </Field>
  );

  return (
    <LoadFailureFrame failed={failed} className={className} label={label} loadError={loadError} onRetryLoad={onRetryLoad}>
      {control}
    </LoadFailureFrame>
  );
}
