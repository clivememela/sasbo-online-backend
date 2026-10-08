import { useId, useState } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import { AlertCircle, CheckCircle2, KeyRound } from 'lucide-react';

import Button from '../../components/form/Button';
import PasswordInput from '../../components/form/PasswordInput';
import useFocusOn from '../../hooks/useFocusOn.js';
import reportError from '../../lib/reportError.js';
import { useAuth } from '../../auth/useAuth';
import { safeNext } from '../../auth/safeNext';
import { changePasswordWith } from '../../auth/authActions';
import { AUTH_REFUSAL, refusalOf } from '../../auth/authVocabulary';
import { PASSWORD_MIN, PASSWORD_RULES, passwordFailure, passwordProblem } from './signInRules';

/**
 * `/change-password` -- a person's own password, and the first thing a temporary one leads to (signin.md: "a temporary
 * password (from an administrator) must be changed at first sign-in"). The shell sends a user here, with `next`, for as
 * long as their account says `mustChangePassword`; the header's key opens it at any other time.
 *
 * The rules are on the screen before anything is typed, and the two the browser can check -- the length and the
 * username -- are checked before asking. The common-password list is the server's alone, and its refusal
 * (`PASSWORD_RULES`) is explained against the same list.
 */

const CARD = 'w-full max-w-md rounded-card bg-surface p-8 shadow-(--shadow-2)';
const NOTICE = 'mb-4 rounded-lg border p-3 text-sm focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-brand-ring';

const validate = ({ current, chosen, again }, username) => {
  const errors = {};
  if (!current) errors.current = 'Enter your current password.';
  const problem = passwordProblem(chosen, username);
  if (problem) errors.chosen = problem;
  else if (chosen === current) errors.chosen = 'Choose a password that is not the one you have now.';
  if (!errors.chosen && chosen !== again) errors.again = 'The two new passwords do not match.';
  return errors;
};

export default function ChangePassword() {
  const { user } = useAuth();
  const [params] = useSearchParams();
  const next = safeNext(params.get('next'));
  const navigate = useNavigate();

  const [values, setValues] = useState({ current: '', chosen: '', again: '' });
  const [busy, setBusy] = useState(false);
  // What happened last: the fields refused, the server's refusal, or the change made (`signedIn` says whether the
  // session survived it).
  const [outcome, setOutcome] = useState(null);
  const outcomeId = useId();
  const outcomeRef = useFocusOn(outcome);

  const temporary = Boolean(user?.mustChangePassword);
  const set = (field) => (event) => setValues((all) => ({ ...all, [field]: event.target.value }));

  const handleSubmit = async (event) => {
    event.preventDefault();
    const fields = validate(values, user?.username);
    if (Object.keys(fields).length > 0) {
      setOutcome({ fields });
      return;
    }
    setBusy(true);
    try {
      const signedIn = await changePasswordWith({ currentPassword: values.current, newPassword: values.chosen });
      setValues({ current: '', chosen: '', again: '' });
      setOutcome({ changed: true, signedIn });
    } catch (error) {
      reportError('ChangePassword.submit', error);
      if (refusalOf(error) === AUTH_REFUSAL.SIGN_IN_FAILED) setValues((all) => ({ ...all, current: '' }));
      setOutcome({ refusal: passwordFailure(error) });
    } finally {
      setBusy(false);
    }
  };

  const heading = (
    <h1 className="mt-2 flex items-center gap-3 text-3xl font-semibold leading-[1.15] tracking-[-0.02em] text-ink">
      <KeyRound aria-hidden="true" className="size-7 shrink-0 text-brand" strokeWidth={1.75} />
      {temporary ? 'Choose your password' : 'Change your password'}
    </h1>
  );

  // Nobody signed in, and not because this change just ended the session: there is no account here to change.
  if (!user && !outcome?.changed) {
    return (
      <div className="flex flex-1 flex-col items-center justify-center p-4">
        <div className={CARD}>
          {heading}
          <p role="status" className="mt-4 text-base text-ink-muted">
            Sign in first, then change your password here. <Link className="font-medium underline" to="/login">Sign in</Link>
          </p>
        </div>
      </div>
    );
  }

  const fields = outcome?.fields ?? {};

  return (
    <div className="flex flex-1 flex-col items-center justify-center p-4">
      <div className={CARD}>
        {heading}
        <p className="mt-1.5 text-base text-ink-muted">
          {temporary
            ? 'You signed in with a temporary password. Choose your own before you carry on.'
            : 'Choose a new password for your account.'}
        </p>

        <div className="mt-5 mb-6 rounded-lg border border-line-strong bg-surface-2 p-3 text-sm text-ink">
          <p className="font-medium">A password must be:</p>
          <ul className="mt-1 list-disc pl-6 text-ink-muted">
            {PASSWORD_RULES.map((rule) => <li key={rule}>{rule}</li>)}
          </ul>
        </div>

        {outcome && (
          <div
            ref={outcomeRef}
            tabIndex={-1}
            aria-labelledby={outcomeId}
            className={`${NOTICE} ${outcome.changed ? 'border-status-active-dot bg-status-active-bg text-status-active' : 'border-status-suspended-dot bg-status-suspended-bg text-status-suspended'}`}
          >
            <p id={outcomeId} className="flex items-center gap-2 font-medium">
              {outcome.changed
                ? <CheckCircle2 aria-hidden="true" className="h-4 w-4 shrink-0" strokeWidth={1.75} />
                : <AlertCircle aria-hidden="true" className="h-4 w-4 shrink-0" strokeWidth={1.5} />}
              {outcome.changed ? 'Your password has been changed' : outcome.refusal ? 'Your password was not changed' : 'There is a problem with this form'}
            </p>
            {outcome.refusal && <p className="mt-1">{outcome.refusal}</p>}
            {outcome.fields && (
              <ul className="mt-1 list-disc pl-6">
                {Object.entries(fields).map(([field, message]) => <li key={field}>{message}</li>)}
              </ul>
            )}
            {outcome.changed && !outcome.signedIn && (
              <p className="mt-1">
                Sign in again with your new password. <Link className="font-medium underline" to="/login">Sign in</Link>
              </p>
            )}
          </div>
        )}

        {outcome?.changed ? (
          outcome.signedIn && (
            <Button variant="primary" className="w-full justify-center" onClick={() => navigate(next, { replace: true })}>
              Continue
            </Button>
          )
        ) : (
          <form onSubmit={handleSubmit} noValidate className="space-y-4">
            <PasswordInput
              label={temporary ? 'Temporary password' : 'Current password'}
              value={values.current}
              onChange={set('current')}
              error={fields.current}
              required
              maxLength={200}
            />
            <PasswordInput
              label="New password"
              kind="newPassword"
              value={values.chosen}
              onChange={set('chosen')}
              error={fields.chosen}
              hint={`At least ${PASSWORD_MIN} characters, and not your username.`}
              required
              maxLength={200}
            />
            <PasswordInput
              label="Confirm the new password"
              kind="newPassword"
              value={values.again}
              onChange={set('again')}
              error={fields.again}
              required
              maxLength={200}
            />
            <Button type="submit" variant="primary" loading={busy} className="mt-4 w-full justify-center">
              {busy ? 'Changing your password…' : 'Change password'}
            </Button>
          </form>
        )}
      </div>
    </div>
  );
}
