/* global google */
import { useCallback, useEffect, useId, useRef, useState } from 'react';
import { MapPin, Search } from 'lucide-react';
import { useMapsLibrary } from '@vis.gl/react-google-maps';

import { cn } from '../../lib/utils';
import reportError from '../../lib/reportError';
import Field, { controlClasses } from './Field';

/**
 * Address input backed by Google Places. `03.2a`.
 *
 * ⚠️ **THE BEHAVIOUR IS PRESERVED; THE SEMANTICS ARE ADDED.** This component is on the
 * don't-regress list, and `00.6` verified it as **HOLDS WITH A CORRECTION**: three
 * consumers, six usage sites, and real keyboard handling (`ArrowDown`, `ArrowUp`,
 * `Enter`, `Escape`) — *"but it is not a combobox in the ARIA sense: no `role="combobox"`,
 * no `role="listbox"`, no `aria-activedescendant`."* So the keyboard worked and a screen
 * reader was told nothing at all. **The row explicitly refuses to be read as permission
 * to leave it as-is**, so the debounce, the session token, the place-details fetch and
 * every key binding are untouched, and only the announcement changed.
 *
 * 🔴 **AND ONE DEFECT FOUND WHILE DOING IT: the input's `id` was the literal string
 * `"address-autocomplete-input"`.** `Members.jsx` renders two of these on one page — Bank
 * Address and Bank Work Address — so the page shipped two elements with the same id, and
 * the second field's `<label htmlFor>` pointed at the FIRST field's input. Clicking the
 * second label focused the wrong box, and a screen reader announced the wrong name. It is
 * `useId()` now, which cannot collide however many are rendered. Recorded in `findings.md`.
 *
 * 🔴 **The option rows were `<li onMouseDown>`** — the same mouse-only shape the old
 * `SearchableSelect` had. They are `role="option"` inside a real listbox now, driven by
 * `aria-activedescendant` rather than by a highlight class alone.
 */
export default function AddressAutocomplete({
  label,
  value,
  onChange,
  placeholder = 'Start typing an address...',
  className = '',
  error,
  hint,
  disabled = false,
}) {
  const listboxId = useId();
  const [query, setQuery] = useState(value || '');
  const [predictions, setPredictions] = useState([]);
  // 🔴 A SUGGESTION LOOKUP THAT FAILED LOOKED EXACTLY LIKE AN ADDRESS GOOGLE DID NOT KNOW (`06.4`): the list
  // simply never opened, and the person typing waited for suggestions that were not coming. The field says so
  // now -- and that what they type is kept, which is true: `onChange` carries every keystroke.
  const [suggestionsFailed, setSuggestionsFailed] = useState(false);
  const [isOpen, setIsOpen] = useState(false);
  const [highlightIndex, setHighlightIndex] = useState(-1);
  const wrapperRef = useRef(null);
  const sessionTokenRef = useRef(null);

  const placesLib = useMapsLibrary('places');

  /*
   * 🔴 BELOW THREE CHARACTERS THE SUGGESTIONS ARE CLEARED -- DURING RENDER, NOT AFTER IT (`10.4`).
   *
   * They used to be cleared by the debounce effect (`setPredictions([])`), which
   * `react-hooks/set-state-in-effect` makes an error this phase. Two replacements were weighed and one
   * was wrong:
   *
   *   HIDE the list while the query is short (derive it) -- rejected. That clears nothing; it only
   *   stops SHOWING the old list. Back out of "12 Synthetic" to "12", type " S", and the suggestions
   *   for the abandoned query reappear under the new one until the new lookup answers.
   *
   *   CLEAR it during render, guarded -- this. Same semantics as before, exactly: short query, no
   *   suggestions, and they are gone rather than hidden. React re-renders before anything commits, so
   *   the one frame the effect used to leave (a short query still showing its old list) is never drawn.
   *   The guard is what makes it terminate. It also clears a LATE answer for a longer query that lands
   *   after the user has backed out, which the effect -- keyed on `query` -- never saw.
   *
   * `AddressAutocomplete.suggestions.test.jsx` holds the backed-out case, with the second lookup held
   * so that a slow box cannot let a fresh answer hide the stale one.
   */
  const searchable = query.trim().length >= 3;
  if (!searchable && predictions.length > 0) setPredictions([]);

  // Declared here, above the effects that read it: `const` is not hoisted, and the
  // outside-dismiss effect below runs before the render body reaches the JSX.
  const listOpen = isOpen && predictions.length > 0;

  useEffect(() => {
    if (!placesLib) return;
    sessionTokenRef.current = new google.maps.places.AutocompleteSessionToken();
  }, [placesLib]);

  /*
   * 🔴 A VALUE THE FORM CHANGES REACHES THE FIELD -- DURING RENDER, WITH A PREVIOUS-VALUE GUARD (`10.4`).
   *
   * A reset, or a record loaded into the form, changes `value` from outside; the field keeps its own
   * `query` so it can be typed into, and has to adopt the new value when that happens. This was an effect
   * keyed on `[value]` that also needed an `eslint-disable-next-line react-hooks/exhaustive-deps` to stop
   * it re-running on every keystroke -- and `react-hooks/set-state-in-effect` makes the effect an error
   * this phase.
   *
   * The render-phase form is React's own answer ("storing information from previous renders"): remember
   * the last `value` seen, and adopt a new one when it differs. Same trigger as the effect had, so the
   * suppression goes with it -- one fewer disable comment in the tree -- and the frame in which a reset
   * form still showed the old text is never committed. `value !== query` is still the guard that stops
   * the parent's echo of each keystroke from fighting the keyboard.
   *
   * `AddressAutocomplete.test.jsx` holds both directions now; before this phase only the outbound one had
   * a witness.
   */
  const [seenValue, setSeenValue] = useState(value);
  if (value !== seenValue) {
    setSeenValue(value);
    if (value !== undefined && value !== query) setQuery(value || '');
  }

  const fetchPredictions = useCallback(
    async (input) => {
      if (!placesLib || !input || input.trim().length < 3) {
        setPredictions([]);
        return;
      }

      try {
        const { suggestions } =
          await google.maps.places.AutocompleteSuggestion.fetchAutocompleteSuggestions({
            input: input.trim(),
            sessionToken: sessionTokenRef.current,
            includedRegionCodes: ['za'],
          });

        setSuggestionsFailed(false);
        if (suggestions && suggestions.length > 0) {
          setPredictions(
            suggestions
              .filter((suggestion) => suggestion.placePrediction)
              .map((suggestion) => {
                const prediction = suggestion.placePrediction;
                return {
                  placeId: prediction.placeId,
                  description: prediction.text?.text || '',
                  primaryText:
                    prediction.structuredFormat?.mainText?.text || prediction.text?.text || '',
                  secondaryText: prediction.structuredFormat?.secondaryText?.text || '',
                };
              }),
          );
        } else {
          setPredictions([]);
        }
      } catch (err) {
        reportError('AddressAutocomplete.suggest', err);
        setPredictions([]);
        setSuggestionsFailed(true);
      }
    },
    [placesLib],
  );

  // Debounce the predictions fetch.
  useEffect(() => {
    if (!searchable) return undefined;
    const timer = setTimeout(() => fetchPredictions(query), 300);
    return () => clearTimeout(timer);
  }, [query, searchable, fetchPredictions]);

  // 🔑 `pointerdown`, and ONLY WHILE OPEN -- matching `SearchableSelect`, which documents
  // why: `mousedown` alone misses touch on iOS. This file shipped the `mousedown` version in
  // the same commit whose sibling explains that it is insufficient. The `open` guard is the
  // other half: `Members.jsx` renders two of these, and an always-attached listener walks
  // `contains()` on every pointer press anywhere in the application, open or shut.
  useEffect(() => {
    if (!listOpen) return undefined;
    const handleClickOutside = (event) => {
      if (wrapperRef.current && !wrapperRef.current.contains(event.target)) setIsOpen(false);
    };
    document.addEventListener('pointerdown', handleClickOutside);
    return () => document.removeEventListener('pointerdown', handleClickOutside);
  }, [listOpen]);

  const handleInputChange = (event) => {
    setQuery(event.target.value);
    setIsOpen(true);
    setHighlightIndex(-1);
    onChange(event);
  };

  const handleSelect = async (prediction) => {
    setQuery(prediction.description);
    setPredictions([]);
    setIsOpen(false);
    setHighlightIndex(-1);

    if (prediction.placeId) {
      try {
        const place = new google.maps.places.Place(prediction.placeId);
        const result = await place.fetchPlaceDetails({
          fields: ['formattedAddress'],
          sessionToken: sessionTokenRef.current,
        });

        if (result?.formattedAddress) {
          onChange({ target: { value: result.formattedAddress } });
          setQuery(result.formattedAddress);
        } else {
          onChange({ target: { value: prediction.description } });
        }
      } catch (err) {
        reportError('AddressAutocomplete.details', err);
        onChange({ target: { value: prediction.description } });
      }
    } else {
      onChange({ target: { value: prediction.description } });
    }

    sessionTokenRef.current = new google.maps.places.AutocompleteSessionToken();
  };

  const handleKeyDown = (event) => {
    if (!isOpen || predictions.length === 0) return;

    if (event.key === 'ArrowDown') {
      event.preventDefault();
      setHighlightIndex((prev) => (prev < predictions.length - 1 ? prev + 1 : 0));
    } else if (event.key === 'ArrowUp') {
      event.preventDefault();
      setHighlightIndex((prev) => (prev > 0 ? prev - 1 : predictions.length - 1));
    } else if (event.key === 'Enter' && highlightIndex >= 0) {
      event.preventDefault();
      handleSelect(predictions[highlightIndex]);
    } else if (event.key === 'Escape') {
      setIsOpen(false);
    }
  };

  const activeId = listOpen && highlightIndex >= 0 ? `${listboxId}-option-${highlightIndex}` : undefined;

  return (
    <div ref={wrapperRef} className={cn('relative', className)}>
      <Field
        label={label}
        error={error}
        hint={suggestionsFailed ? 'Address suggestions could not be loaded. Type the full address; it is kept as typed.' : hint}
      >
        {({ id, describedBy, invalid }) => (
          <div className="relative">
            <input
              id={id}
              type="text"
              role="combobox"
              value={query}
              onChange={handleInputChange}
              onKeyDown={handleKeyDown}
              onFocus={() => {
                if (predictions.length > 0) setIsOpen(true);
              }}
              placeholder={placeholder}
              disabled={disabled}
              autoComplete="off"
              aria-expanded={listOpen}
              aria-controls={listOpen ? listboxId : undefined}
              aria-autocomplete="list"
              aria-activedescendant={activeId}
              aria-invalid={invalid}
              aria-describedby={describedBy}
              className={controlClasses({ invalid, className: 'touch-target pr-9' })}
            />
            <Search
              aria-hidden="true"
              className="pointer-events-none absolute right-3 top-1/2 size-4 -translate-y-1/2 text-ink-faint"
            />
          </div>
        )}
      </Field>

      {listOpen && (
        <ul
          id={listboxId}
          role="listbox"
          aria-label={label ? `${label} suggestions` : 'Address suggestions'}
          className={cn(
            'absolute z-50 mt-1 max-h-52 w-full overflow-y-auto overscroll-contain',
            'rounded-lg border border-line bg-surface shadow-2',
          )}
        >
          {predictions.map((prediction, index) => (
            <li
              key={prediction.placeId || index}
              id={`${listboxId}-option-${index}`}
              role="option"
              aria-selected={index === highlightIndex}
              onMouseDown={() => handleSelect(prediction)}
              onMouseEnter={() => setHighlightIndex(index)}
              className={cn(
                'cursor-pointer px-4 py-2.5 text-ink transition-colors duration-(--duration-instant)',
                index === highlightIndex && 'bg-brand-soft text-brand-ink',
              )}
            >
              <div className="flex items-center gap-2">
                <MapPin aria-hidden="true" className="size-4 shrink-0 text-ink-faint" />
                <div className="min-w-0">
                  <p className="field-text truncate font-medium">
                    {prediction.primaryText || prediction.description}
                  </p>
                  {prediction.secondaryText && (
                    <p className="truncate text-xs text-ink-muted">{prediction.secondaryText}</p>
                  )}
                </div>
              </div>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
