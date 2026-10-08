/**
 * HOW A SELECT READS ITS OPTIONS. `03.2a`.
 *
 * 🔴 THE REASON THIS EXISTS, and it is a real one rather than an anticipated one. The
 * Capture Member arc's Job Grade field selects from records that carry a code, a
 * description and a bank -- not from `{ value, label }` pairs -- and their
 * `optionValue` / `optionLabel` helpers in `src/utils/jobGradeMatch.js` are what that
 * cost them. **A shared select primitive that only understands `{ value, label }` is a
 * primitive that field cannot consume**, so the layer would fracture again on the first
 * screen that has real records, which is precisely the four-way fracture `03.2a` exists
 * to end (BR-4).
 *
 * ⚠️ THE ACCESSORS ARE NOT A CONVENIENCE. `options.map(r => ({ value: r.code, label:
 * r.description }))` at the call site looks equivalent and is not: it allocates a new
 * array on every render, which defeats memoisation in the consumer, and it throws away
 * the record, so `onChange` can only hand back a string when the caller usually wants
 * the row. `onChange(value, option)` passes both -- the value first, so every existing
 * call site keeps working unchanged.
 *
 * 🔑 VALUES ARE COMPARED AS STRINGS, deliberately. A native `<option value>` is a
 * string no matter what was put in it, so a numeric id round-trips as `"7"` and
 * `7 === "7"` is false -- which is how a select silently shows the placeholder while
 * holding a perfectly good selection. One coercion, here, rather than a `Number()` that
 * each call site remembers or does not.
 */

/** @typedef {{ value: string, label: string, disabled?: boolean, option: unknown }} NormalisedOption */

/**
 * Resolve an accessor, which may be a key name or a function.
 * A key name is what a call site reaches for first (`optionValue="id"`), and a function
 * is what a computed label needs (`optionLabel={g => `${g.code} — ${g.description}`}`).
 */
function accessor(spec, fallback) {
  if (typeof spec === 'function') return spec;
  if (typeof spec === 'string') return (option) => option?.[spec];
  return fallback;
}

/**
 * Normalise a caller's options into the one shape every select in this application
 * renders from.
 *
 * @param options the raw list -- `{ value, label }` pairs, plain strings, or records
 * @param optionValue key or function producing the stored value. Default: `option.value`
 * @param optionLabel key or function producing the visible text. Default: `option.label`
 * @returns {NormalisedOption[]}
 */
export function normaliseOptions(options, optionValue, optionLabel) {
  if (!Array.isArray(options)) return [];

  const readValue = accessor(optionValue, (option) =>
    option && typeof option === 'object' ? option.value : option,
  );
  const readLabel = accessor(optionLabel, (option) =>
    option && typeof option === 'object' ? option.label : option,
  );

  return options.map((option) => {
    const raw = readValue(option);
    const label = readLabel(option);
    return {
      // Through `asOptionValue` rather than restated: 0 and false are legitimate values and
      // must survive, and that rule belongs in ONE function.
      value: asOptionValue(raw),
      label: asOptionValue(label),
      disabled: Boolean(option && typeof option === 'object' && option.disabled),
      // The caller's own record, handed back by `onChange(value, option)`.
      option,
    };
  });
}

/** The stored value as a select compares it. See the string note above. */
export function asOptionValue(value) {
  return value === null || value === undefined ? '' : String(value);
}

/**
 * Find the option a value refers to, or `undefined`.
 * Separate from `normaliseOptions` because a consumer calls it on every render and the
 * normalisation is memoised.
 */
export function findOption(normalised, value) {
  const wanted = asOptionValue(value);
  return normalised.find((entry) => entry.value === wanted);
}
