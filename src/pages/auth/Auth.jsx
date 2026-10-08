import { useId, useState } from 'react';
import { Navigate, useNavigate, useSearchParams } from 'react-router-dom';
import { AlertCircle, Info, LogIn } from 'lucide-react';

import Button from '../../components/form/Button';
import Logo from '../../components/common/Logo';
import PasswordInput from '../../components/form/PasswordInput';
import TextInput from '../../components/form/TextInput';
import useFocusOn from '../../hooks/useFocusOn.js';
import reportError from '../../lib/reportError.js';
import { useAuth } from '../../auth/useAuth';
import { safeNext } from '../../auth/safeNext';
import { signInWith } from '../../auth/authActions';
import { signInFailure } from './signInRules';

/**
 * The sign-in screen -- `/login`, made real by the sign-in stream (contracts/signin.md).
 *
 * 🔴 R-SEC-C STILL HOLDS, AND MORE THAN BEFORE. This screen once did `console.log('Login attempt:', { email, password })`.
 * Nothing here logs, stores or transmits what is typed except the one request that signs in: the username and password
 * go in its body and nowhere else, and a failure is reported by `reportError`, which logs a kind and a status, never a
 * value. `Auth.test.jsx` watches every console sink while it signs in.
 *
 * 🔑 THE DOOR STAYS OPEN IN OPEN MODE. Until SASBO's accounts exist the link must keep working without one (the decks
 * and SASBO's own demonstrations open `/home` directly), so open mode offers "Continue without signing in" beside the
 * form. Required mode does not; the shell sends everyone here.
 *
 * `next` is honoured only as a path on this site (`safeNext`): a sign-in page that forwards to any address it is given
 * is the first step of somebody else's phishing page. A temporary password goes to the change-password screen first.
 *
 * The "Sign Up" half of this screen is gone: there is no self-service account here. An administrator creates every
 * user, with a position and an area (the 5 Oct Show & Tell), under Maintenance > Users.
 */

/** Every reason a submission can be refused before it is sent, in the order a reader meets the fields. */
const validate = ({ username, password }) => {
  const errors = {};
  if (!username.trim()) errors.username = 'Enter your username.';
  if (!password) errors.password = 'Enter your password.';
  return errors;
};

export default function Auth() {
  // `user` is set exactly when someone is signed in; a page tests that rather than `status`, whose name the vocabulary
  // parity scan keeps for the member's status (vocabulary.parity.test.js).
  const { mode, user, sessionEnded } = useAuth();
  const [params] = useSearchParams();
  const next = safeNext(params.get('next'));
  const navigate = useNavigate();

  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [busy, setBusy] = useState(false);
  // ONE value for what went wrong: the fields the form refused, or the server's refusal -- never both, so the summary
  // cannot point at a field while saying the server said no.
  const [problem, setProblem] = useState(null);
  const summaryId = useId();
  // The summary takes focus each time it appears, a fresh object every submit, so the same mistake twice moves it again.
  const summaryRef = useFocusOn(problem);

  // Already signed in (another tab, or straight back here): on to where they were going.
  if (user) {
    return <Navigate to={user.mustChangePassword ? `/change-password?next=${encodeURIComponent(next)}` : next} replace />;
  }

  const handleSubmit = async (event) => {
    event.preventDefault();
    const fields = validate({ username, password });
    if (Object.keys(fields).length > 0) {
      setProblem({ fields });
      return;
    }
    setProblem(null);
    setBusy(true);
    try {
      await signInWith({ username, password });
      // The navigation itself happens through the `user` check above, on the render this sign-in causes.
    } catch (error) {
      reportError('Auth.signIn', error);
      setPassword('');
      setProblem({ refusal: signInFailure(error) });
    } finally {
      setBusy(false);
    }
  };

  const fields = problem?.fields ?? {};

  return (
    // `flex-1`, not `min-h-dvh`: `AuthLayout`'s column is already a viewport tall (see its own comment).
    <div className="flex flex-1 flex-col items-center justify-center p-4">
      <div className="w-full max-w-md rounded-card bg-surface p-8 shadow-(--shadow-2)">
        {/* The rail's own wordmark: the first surface anybody sees introduces the same product the shell does. */}
        <div className="mb-7">
          <span className="flex items-center gap-3">
            <Logo className="size-11 shrink-0 object-contain" alt="" />
            <span className="grid min-w-0 leading-[1.15]">
              <span className="truncate text-md font-semibold tracking-[-0.01em] text-ink">SASBO Online</span>
              <small className="mt-[3px] truncate text-[11px] font-semibold uppercase tracking-[0.14em] text-accent-ink">
                The Finance Union
              </small>
            </span>
          </span>
          <h1 className="mt-6 text-3xl font-semibold leading-[1.15] tracking-[-0.02em] text-ink">Sign in</h1>
          <p className="mt-1.5 text-base text-ink-muted">To the membership administration system.</p>
        </div>

        {sessionEnded && (
          <p role="status" className="mb-6 flex gap-2 rounded-lg border border-line-strong bg-surface-2 p-3 text-sm text-ink-muted">
            <Info aria-hidden="true" className="mt-0.5 h-4 w-4 shrink-0 text-brand" strokeWidth={1.5} />
            <span>Your session has ended. Sign in again to carry on where you were.</span>
          </p>
        )}

        {/* THE SUMMARY IS FOCUSED, NOT A LIVE REGION: `Field` already makes each field's message an alert, and a
            focused element is announced without one. `aria-labelledby` leads the announcement with what happened. */}
        {problem && (
          <div
            ref={summaryRef}
            tabIndex={-1}
            aria-labelledby={summaryId}
            className="mb-4 rounded-lg border border-status-suspended-dot bg-status-suspended-bg p-3 text-sm text-status-suspended focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-brand-ring"
          >
            <p id={summaryId} className="flex items-center gap-2 font-medium">
              <AlertCircle aria-hidden="true" className="h-4 w-4 shrink-0" strokeWidth={1.5} />
              {problem.refusal ? 'You are not signed in' : 'There is a problem with this form'}
            </p>
            {problem.refusal ? (
              <p className="mt-1">{problem.refusal}</p>
            ) : (
              <ul className="mt-1 list-disc pl-6">
                {Object.entries(fields).map(([field, message]) => <li key={field}>{message}</li>)}
              </ul>
            )}
          </div>
        )}

        <form onSubmit={handleSubmit} noValidate className="space-y-4">
          <TextInput
            label="Username"
            value={username}
            onChange={(event) => setUsername(event.target.value)}
            error={fields.username}
            required
            autoComplete="username"
            autoCapitalize="none"
            spellCheck={false}
            enterKeyHint="next"
            maxLength={100}
          />
          <PasswordInput
            label="Password"
            value={password}
            onChange={(event) => setPassword(event.target.value)}
            error={fields.password}
            required
            maxLength={200}
          />
          <Button type="submit" variant="primary" loading={busy} className="mt-4 w-full justify-center">
            <LogIn size={20} aria-hidden="true" />
            {busy ? 'Signing in…' : 'Sign in'}
          </Button>
        </form>

        {/* OPEN MODE ONLY: the application works without an account until SASBO's users exist (signin.md, "Modes").
            Kept as a control of its own line rather than a link inside a sentence, for its 44px hit area on a phone. */}
        {mode === 'open' && (
          <div className="mt-6 flex flex-col items-center gap-1 text-sm text-ink-muted">
            <p>No account yet? Sign-in is not required while SASBO’s accounts are being set up.</p>
            <Button variant="ghost" size="sm" onClick={() => navigate(next)}>
              Continue without signing in
            </Button>
          </div>
        )}
      </div>
    </div>
  );
}
