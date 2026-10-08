import reportError from '../lib/reportError';
import { getMe, getMode, signOut } from '../services/authService';
import { ERROR_KINDS } from '../services/parseApiError';
import { SIGN_IN_MODES, clearSession, isFresh, readSession, saveMode, saveSession } from './session';

/**
 * The provider's conversations with the server -- asking the mode, reading the user again, signing out. LAZY (OB17a):
 * `AuthProvider` imports this with `import()`, so the first paint carries none of it, and its few requests leave a
 * moment after the shell is on screen. Nothing here needs to be in the first load: the session is read from the tab
 * (`session.js`), and the token goes on every request through `api.js`, from the very first.
 */

/** The server's mode, kept for the tab -- or `null` when it did not say (a failed read changes nothing). */
export const askMode = () => getMode()
  .then((answer) => {
    if (!SIGN_IN_MODES.includes(answer?.mode)) return null;
    saveMode(answer.mode);
    return answer.mode;
  })
  .catch((error) => {
    // Unreachable is not reported, so it raises no offline banner of its own: the screen's own reads say so if the
    // server is down, and a page stubbed without this one route (a deck slide) must not show a false alarm.
    if (error?.kind !== ERROR_KINDS.NETWORK) reportError('AuthProvider.mode', error);
    return null;
  });

/**
 * A session found in the tab, read again from the server once: a role may have changed since sign-in, and a revoked
 * session ends here rather than at the first screen that asks. A `401` is ended by `api.js`; any other failure keeps
 * the session -- a network blip is not a sign-out. A sign-in's own answer is not asked for again (`isFresh`).
 */
export const refreshUser = (token) => {
  if (isFresh(token)) return Promise.resolve();
  return getMe()
    .then((answer) => {
      const stored = readSession();
      if (answer?.user && stored?.token === token) saveSession({ ...stored, user: answer.user });
    })
    .catch((error) => reportError('AuthProvider.me', error));
};

/** Sign out: the request goes WITH the token, so the server can revoke it; the tab forgets it whatever the answer. */
export const signOutNow = async () => {
  try {
    if (readSession()) await signOut();
  } catch (error) {
    reportError('AuthProvider.signOut', error);
  } finally {
    clearSession();
  }
};
