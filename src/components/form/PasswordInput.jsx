import { useState } from 'react';
import { Eye, EyeOff } from 'lucide-react';

import Field, { controlClasses } from './Field';
import { attributesForKind } from './fieldKinds';

/**
 * A password field whose text can be shown -- sign-in and the change-password screen.
 *
 * 🔑 SHOWING IS A TOGGLE, NOT TWO BUTTONS. One control, always named "Show password", with `aria-pressed` saying whether
 * it is on: a name that flips between "Show" and "Hide" as well as a pressed state says the same thing twice and reads
 * as a different control each time. The field keeps `password`/`newPassword`'s autofill, spelling and capitalisation
 * settings whichever way it is shown, so showing it never hands the text to a spell checker.
 *
 * `kind` is `password` (sign in, the current password) or `newPassword` (a password being chosen) -- `fieldKinds.js`
 * says why the two must differ.
 */
export default function PasswordInput({ label, value, onChange, kind = 'password', error, hint, required, disabled, className, ...rest }) {
  const [shown, setShown] = useState(false);

  return (
    <Field label={label} required={required} error={error} hint={hint} className={className}>
      {({ id, describedBy, invalid }) => (
        <div className="relative">
          <input
            id={id}
            value={value}
            onChange={onChange}
            required={required}
            disabled={disabled}
            aria-invalid={invalid}
            aria-describedby={describedBy}
            {...attributesForKind(kind)}
            {...rest}
            type={shown ? 'text' : 'password'}
            className={controlClasses({ invalid, className: 'pr-11' })}
          />
          {/* 🔴 THE WRAPPER PLACES THE TOGGLE, NOT THE TOGGLE ITSELF. `touch-target` is `position: relative` (its 44px
              overlay needs it), and Tailwind emits it AFTER `absolute`, so on one element it won: the eye fell out of
              the field to its foot, at the left (the presentation session's /login capture, 7 Oct). */}
          <span className="absolute inset-y-0 right-1 flex items-center">
            <button
              type="button"
              aria-label="Show password"
              aria-pressed={shown}
              aria-controls={id}
              disabled={disabled}
              onClick={() => setShown((was) => !was)}
              className="touch-target grid size-9 place-items-center rounded-md text-ink-muted hover-hover:hover:text-ink focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-brand-ring"
            >
              {shown ? <EyeOff size={18} strokeWidth={1.75} aria-hidden="true" /> : <Eye size={18} strokeWidth={1.75} aria-hidden="true" />}
            </button>
          </span>
        </div>
      )}
    </Field>
  );
}
