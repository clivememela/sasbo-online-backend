import { useCallback, useEffect, useId, useMemo, useRef, useState } from 'react';
import { ChevronDown, Loader2, X } from 'lucide-react';

import { cn } from '../../lib/utils';
import LoadFailureFrame from './LoadFailureFrame';
import { choicePlaceholder } from './choicePlaceholder';
import Field, { controlClasses } from './Field';
import { asOptionValue, findOption, normaliseOptions } from './options';

/**
 * The filtering select. `03.2a`.
 *
 * 🔴 THREE THINGS WERE WRONG WITH THE ONE THIS REPLACES, and two of them were invisible.
 *
 * ① **It was not a combobox in the ARIA sense.** It had `aria-haspopup="listbox"` on a
 * button and a `role="listbox"` on a `<ul>`, which is enough to look right in a review
 * and is not enough to work: no `role="combobox"`, no `aria-controls`, and no
 * `aria-activedescendant`, so a screen reader was never told which option the arrow keys
 * had landed on. This is the same shape as the `AddressAutocomplete` finding -- *"a
 * working keyboard widget whose semantics say nothing"* -- recorded in the don't-regress
 * list as **HOLDS WITH A CORRECTION**.
 *
 * ② **The options were mouse-only.** They were `<li onClick>` with no key handling at
 * all: the arrow keys did nothing, and the list could be opened but never navigated.
 * `a11y.test.jsx` was green over it, because axe cannot see that a `<li>` is unreachable
 * -- [T17](../../../planning/branches/design/measurementTraps.md), and the reason
 * conventions §7 says the keyboard is proved with `userEvent` and never with a scan.
 *
 * ③ **The clear button was an SVG with an `onClick`.** Not focusable, not a button, not
 * announced -- one of the six controls run 7 found that only a mouse can reach.
 *
 * 🔑 THE WHOLE FIELD IS THE TRIGGER, and that is the owner's rule, not a preference. The
 * complaint that produced the date picker's contract was that only the icon opened it.
 * The same rule here means the control IS the text box: click it anywhere, or focus it
 * and type, and it filters. That also collapses two focusable things (a button, then a
 * search box inside the popup) into one, which is what makes the keyboard model simple
 * enough to be correct.
 *
 * ⚠️ **A member flagged `is_representative` with no `representatives` row is offered by
 * this control and cannot be saved.** That is `03.10`, a defect in the model on
 * `origin/main` (`4e41ed5` points `/api/representatives` at the members table while the
 * member's FK still resolves through `representatives`). **It is deliberately NOT
 * handled here.** A select that quietly filters the unsaveable options hides a data
 * defect and makes it unfindable; the Capture Member arc holds it with a tripwire test.
 */
export default function SearchableSelect({
  label,
  value,
  onChange,
  options,
  optionValue,
  optionLabel,
  placeholder = 'Select…',
  name,
  error,
  hint,
  required,
  disabled,
  loading = false,
  emptyLabel = 'No options available',
  noMatchLabel = 'No matches',
  // The options could not be fetched -- a state of the CHOICES, not of the value (`error`). See
  // `SelectInput`: a failed list renders as "Unavailable" with a compact error and a retry, never as
  // an empty one (F-P54, `06.4`).
  loadError = null,
  onRetryLoad,
  clearable = true,
  /**
   * LIST FIRST, TYPING ALLOWED. Off by default, and that default is load-bearing rather than polite:
   * this control is consumed by Members, Recruiters and Capture, so one changed default is three
   * screens changed in silence. `SearchableSelect.test.jsx` pairs every custom-value case with the
   * same interaction and no prop.
   *
   * When on, a value typed that matches no option survives blur and is reported through the SAME
   * `onChange(value, option)` -- with no option, because there is not one. No second callback.
   *
   * ⚠️ The control does NOT guess. A near-miss prompt ("did you mean BA2?") is the call site's, where
   * the vocabulary is known; see `utils/jobGradeMatch.js`. Silently turning what somebody typed into
   * something else is the failure 02.5 exists to correct, and a primitive is the wrong place to do it.
   */
  allowCustom = false,
  className,
  ...rest
}) {
  const listboxId = useId();
  const [open, setOpen] = useState(false);
  const [search, setSearch] = useState('');
  const [activeIndex, setActiveIndex] = useState(-1);
  const containerRef = useRef(null);
  const inputRef = useRef(null);
  const listRef = useRef(null);

  const normalised = useMemo(
    () => normaliseOptions(options, optionValue, optionLabel),
    [options, optionValue, optionLabel],
  );

  const selected = findOption(normalised, value);
  const failed = Boolean(loadError) && !loading;
  const isEmpty = !loading && !failed && normalised.length === 0;

  const filtered = useMemo(() => {
    const needle = search.trim().toLowerCase();
    if (!needle) return normalised;
    return normalised.filter((entry) => entry.label.toLowerCase().includes(needle));
  }, [normalised, search]);

  // 🔑 Closed, the box shows the SELECTION; open, it shows what is being typed. Keeping
  // one input for both is what makes the whole field the trigger, and resetting `search`
  // on close is what stops a half-typed filter from reading as the current value.
  //
  // ⚠️ Under `allowCustom` the value may be one NO option carries, so `selected` is undefined and
  // falling back to '' would blank a value the user typed and the form is still holding -- the field
  // would look empty while posting a grade. The raw value is shown instead.
  const closedDisplay = selected?.label ?? (allowCustom ? asOptionValue(value) : '');
  const displayValue = open ? search : closedDisplay;

  const close = useCallback(() => {
    setOpen(false);
    setSearch('');
    setActiveIndex(-1);
  }, []);

  const commit = useCallback(
    (entry) => {
      if (!entry || entry.disabled) return;
      onChange?.(entry.value, entry.option);
      close();
    },
    [onChange, close],
  );

  /**
   * Leaving the field with something typed -- the only thing `allowCustom` adds.
   *
   * 🔑 A TYPED VALUE THAT NAMES AN OPTION IS THE OPTION, not the text. `BA2` typed and `BA2` chosen
   * must store the same thing, or the same grade exists twice over depending on how it was entered.
   * Matched case-insensitively for the same reason, and the option's own value is what is stored.
   *
   * ⚠️ MATCHED AGAINST THE VALUE AS WELL AS THE LABEL, and the job grades are why. Their options are
   * `{value: "C1", label: "C1 - Clerk - Permanent"}`: the code is what an administrator types and the
   * label is decorated, so a label-only comparison would have captured "C1" as a BRAND NEW grade
   * beside the one already on the list. Both are identities of the same option.
   *
   * Blank is not a value: whitespace commits nothing rather than storing "   ".
   *
   * ⚠️ NOT called from `close()`, and that is deliberate. Escape means "never mind" everywhere else
   * in this control, and routing it through here would make it a second way to commit. Escape clears
   * `search` first, so the blur that follows finds nothing to keep.
   */
  const commitTyped = useCallback(() => {
    const typed = search.trim();
    if (!allowCustom || typed === '') {
      close();
      return;
    }
    const wanted = typed.toLowerCase();
    const match = normalised.find(
      (entry) => !entry.disabled
        && (entry.label.toLowerCase() === wanted || entry.value.toLowerCase() === wanted),
    );
    if (match) onChange?.(match.value, match.option);
    else onChange?.(typed, undefined);
    close();
  }, [allowCustom, search, normalised, onChange, close]);

  // Pointer-down outside, not click: a click that starts inside and ends outside is not
  // a dismissal, and `mousedown` alone misses touch on iOS.
  useEffect(() => {
    if (!open) return undefined;
    const handler = (event) => {
      // Clicking away IS leaving the field, so it keeps a typed value exactly as blur does.
      if (containerRef.current && !containerRef.current.contains(event.target)) commitTyped();
    };
    document.addEventListener('pointerdown', handler);
    return () => document.removeEventListener('pointerdown', handler);
  }, [open, commitTyped]);

  // Keep the active option in view when the arrows move past the visible window.
  useEffect(() => {
    if (!open || activeIndex < 0) return;
    listRef.current?.children?.[activeIndex]?.scrollIntoView?.({ block: 'nearest' });
  }, [open, activeIndex]);

  const isDisabled = disabled || loading || isEmpty || failed;

  const openList = useCallback(() => {
    if (isDisabled) return;
    setOpen(true);
  }, [isDisabled]);

  const move = useCallback(
    (delta) => {
      setActiveIndex((current) => {
        if (filtered.length === 0) return -1;
        const start = current < 0 ? (delta > 0 ? -1 : 0) : current;
        let next = start + delta;
        if (next < 0) next = filtered.length - 1;
        if (next >= filtered.length) next = 0;
        return next;
      });
    },
    [filtered.length],
  );

  const handleKeyDown = (event) => {
    switch (event.key) {
      case 'ArrowDown':
        event.preventDefault();
        if (!open) {
          openList();
          setActiveIndex(0);
        } else {
          move(1);
        }
        break;
      case 'ArrowUp':
        event.preventDefault();
        if (!open) {
          openList();
          setActiveIndex(filtered.length - 1);
        } else {
          move(-1);
        }
        break;
      case 'Home':
        if (open) {
          event.preventDefault();
          setActiveIndex(0);
        }
        break;
      case 'End':
        if (open) {
          event.preventDefault();
          setActiveIndex(filtered.length - 1);
        }
        break;
      case 'Enter':
        if (open && activeIndex >= 0) {
          // Only swallow the key when it actually selects something -- otherwise Enter
          // must reach the form, which is how every other field in this app submits.
          event.preventDefault();
          commit(filtered[activeIndex]);
        } else if (open && allowCustom && search.trim() !== '') {
          // Nothing highlighted, but something typed: Enter keeps it. Without this the key
          // would submit the form while the field still held the old value.
          event.preventDefault();
          commitTyped();
        }
        break;
      case 'Escape':
        if (open) {
          event.preventDefault();
          close();
        }
        break;
      case 'Tab':
        if (open) commitTyped();
        break;
      default:
        break;
    }
  };

  const handleClear = (event) => {
    event.stopPropagation();
    onChange?.('', undefined);
    setSearch('');
    inputRef.current?.focus();
  };

  const activeId = open && activeIndex >= 0 ? `${listboxId}-option-${activeIndex}` : undefined;
  // Loop-invariant: the same for every option row. Computed once rather than twice per row,
  // which on a several-hundred-entry bank list was 2N string coercions per render.
  const currentValue = asOptionValue(value);
  const shownPlaceholder = choicePlaceholder({ loading, failed, isEmpty }, emptyLabel, placeholder);

  const control = (
    <Field label={label} required={required} error={error} hint={hint} className={failed ? undefined : className}>
      {({ id, describedBy, invalid }) => (
        <div ref={containerRef} className="relative">
          <input
            {...rest}
            ref={inputRef}
            id={id}
            name={name}
            type="text"
            role="combobox"
            autoComplete="off"
            spellCheck={false}
            value={displayValue}
            placeholder={shownPlaceholder}
            /* 🔴 `data-blank` IS HOW A CRITERION SAYS IT IS NOT FILTERING ANYTHING.
               `card.css:217-218` separates exactly two states -- `.crit__value` in the
               brand's ink for a value somebody chose, `.crit__blank` in `--ink-2` at weight
               400 for "Any" -- so a glance down the row tells you what is narrowing the
               register and what is not. `Criteria`'s `CRITERION` already drew that
               distinction for a native `<select>`, by testing whether the checked option is
               the empty one. It could not draw it here, because this control is an `<input>`
               whose blank state is a real VALUE ("All banks", "Any"), not a placeholder --
               so `:not(:placeholder-shown)` was true and every unset filter came out teal.
               Measured on the round-four captures at rgb(9, 87, 96) on five screens. */
            data-blank={value === '' || value === null || value === undefined || undefined}
            disabled={isDisabled}
            required={required}
            aria-expanded={open}
            aria-controls={open ? listboxId : undefined}
            aria-autocomplete="list"
            aria-activedescendant={activeId}
            aria-invalid={invalid}
            aria-describedby={describedBy}
            aria-busy={loading || undefined}
            onChange={(event) => {
              setSearch(event.target.value);
              setActiveIndex(0);
              if (!open) openList();
            }}
            onKeyDown={handleKeyDown}
            onBlur={() => {
              // `allowCustom` off: `commitTyped` closes and keeps nothing, which is what the
              // list-only control has always done.
              if (open) commitTyped();
            }}
            onMouseDown={() => {
              // The WHOLE FIELD opens it. Toggle rather than open, so a second click on
              // an open field closes it the way a native select does.
              if (open) close();
              else openList();
            }}
            className={controlClasses({
              invalid,
              className: cn('touch-target cursor-default', clearable && value ? 'pr-16' : 'pr-9'),
            })}
          />

          <div className="pointer-events-none absolute inset-y-0 right-2 flex items-center gap-1">
            {clearable && value && !isDisabled && (
              <button
                type="button"
                onClick={handleClear}
                aria-label={`Clear ${label || 'selection'}`}
                className={cn(
                  'pointer-events-auto touch-target rounded-sm p-1 text-ink-faint',
                  'hover-hover:hover:text-ink',
                  'focus:outline-none focus-visible:ring-2 focus-visible:ring-brand-ring',
                )}
              >
                <X aria-hidden="true" className="size-3.5" />
              </button>
            )}
            {loading ? (
              <Loader2
                aria-hidden="true"
                className="size-4 animate-spin text-ink-faint motion-reduce:animate-none"
              />
            ) : (
              <ChevronDown
                aria-hidden="true"
                className={cn(
                  'size-4 text-ink-faint transition-transform duration-(--duration-instant)',
                  open && 'rotate-180',
                )}
              />
            )}
          </div>

          {open && (
            <ul
              ref={listRef}
              id={listboxId}
              role="listbox"
              aria-label={label || placeholder}
              className={cn(
                'absolute z-50 mt-1 max-h-60 w-full overflow-y-auto overscroll-contain py-1',
                'rounded-lg border border-line bg-surface shadow-2',
              )}
            >
              {filtered.length === 0 ? (
                // Not a `role="option"`: an unselectable row inside a listbox is
                // announced as a choice, and this one is a message.
                <li className="field-text px-3 py-2 text-ink-muted" role="presentation">
                  {noMatchLabel}
                </li>
              ) : (
                filtered.map((entry, index) => (
                  <li
                    key={entry.value}
                    id={`${listboxId}-option-${index}`}
                    role="option"
                    aria-selected={entry.value === currentValue}
                    aria-disabled={entry.disabled || undefined}
                    // `pointerdown`, not `click`: the outside-dismiss handler fires on
                    // pointerdown, so a click handler here would run after the list closed.
                    onPointerDown={(event) => {
                      event.preventDefault();
                      commit(entry);
                    }}
                    onMouseEnter={() => setActiveIndex(index)}
                    className={cn(
                      // `touch-size`: each option is 44px on a coarse pointer -- the second sweep measured them at 39 (`07.2`).
                      'field-text touch-size cursor-pointer px-3 py-2 text-ink',
                      entry.disabled && 'cursor-not-allowed opacity-50',
                      index === activeIndex && 'bg-brand-soft text-brand-ink',
                      entry.value === currentValue && 'font-medium text-brand-ink',
                    )}
                  >
                    {entry.label}
                  </li>
                ))
              )}
            </ul>
          )}
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
