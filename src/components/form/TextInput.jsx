import Field, { controlClasses } from './Field';
import { attributesForKind } from './fieldKinds';

/**
 * The text field. `03.2a`.
 *
 * 🔴 `field-text`, NOT `text-sm` -- the rule lives in `controlClasses` now, so it cannot
 * be forgotten by the next control that ships. This input used to be `text-sm`, which
 * was 14px under Tailwind's scale and is **13px** under this design's, and iOS Safari
 * zooms the page when a focused control is under 16px. See conventions §5, and
 * `mockups/design/tools/prove_field_text.mjs`, which measures it in a browser because
 * jsdom cannot see a font size.
 *
 * 🔑 THE LABEL IS A REAL `<label htmlFor>`, from `Field`, and the `aria-label` that used
 * to sit on the input is GONE -- it duplicated the visible label, and when the two
 * disagree the accessible name silently wins over the one the user can read.
 *
 * `kind` decides the keyboard, the autofill and the return key together. See
 * `fieldKinds.js` for why it is one prop and not four.
 */
export default function TextInput({
  label,
  value,
  onChange,
  placeholder,
  kind = 'text',
  name,
  error,
  required,
  disabled,
  className,
  hint,
  multiline = false,
  rows = 3,
  ...rest
}) {
  const kindAttributes = attributesForKind(kind);

  return (
    <Field label={label} required={required} error={error} hint={hint} className={className}>
      {({ id, describedBy, invalid }) => {
        const shared = {
          id,
          name,
          value,
          onChange,
          placeholder,
          required,
          disabled,
          'aria-invalid': invalid,
          'aria-describedby': describedBy,
        };

        // A textarea has no `type`, `inputMode` or `enterKeyHint` worth setting from a
        // kind -- it is free text by definition -- so the kind attributes are dropped
        // rather than passed through and silently ignored.
        return multiline ? (
          <textarea
            {...shared}
            rows={rows}
            {...rest}
            className={controlClasses({ invalid, className: 'resize-y' })}
          />
        ) : (
          <input {...shared} {...kindAttributes} {...rest} className={controlClasses({ invalid })} />
        );
      }}
    </Field>
  );
}
