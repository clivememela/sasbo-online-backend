import reportError from '../lib/reportError';
import { getMe } from '../services/authService';
import { changePassword, signIn } from '../services/signInService';
import { readSession, saveSession } from './session';

/**
 * Signing in and changing one's own password -- loaded with `/login` and `/change-password`, never by the shell, which
 * only reads the session these save (`session.js`): `AuthProvider` sees the new session through the same store as the
 * requests do, so nothing here has to tell it.
 */

/** Signs in; the session is kept as the server answered it. Resolves the user. */
export const signInWith = async ({ username, password }) => {
  const answer = await signIn({ username: username.trim(), password });
  if (typeof answer?.token !== 'string' || !answer.user) {
    throw new Error('The server answered the sign-in in a way this page cannot read. Try again.');
  }
  saveSession({ token: answer.token, expiresAt: answer.expiresAt ?? null, user: answer.user }, { fresh: true });
  return answer.user;
};

/**
 * Changes the password, then reads the user again (their temporary password is no longer one). Resolves `true` while
 * still signed in afterwards, `false` if the server ended the session with the change -- `api.js` has then cleared it.
 */
export const changePasswordWith = async (passwords) => {
  await changePassword(passwords);
  const before = readSession();
  if (!before) return false;
  let user = { ...before.user, mustChangePassword: false };
  try {
    const answer = await getMe();
    if (answer?.user) user = answer.user;
  } catch (error) {
    reportError('authActions.changePassword', error);
  }
  const after = readSession();
  if (after?.token !== before.token) return false;
  saveSession({ ...after, user });
  return true;
};
