import { createContext, useCallback, useEffect, useMemo, useState, useSyncExternalStore } from 'react';

import reportError from '../lib/reportError';
import { clearSession, onSessionEnded, readMode, readSession, subscribeSession } from './session';

/**
 * Who is signed in, and what they may do -- `{ status, mode, user, can(permission), signOut, sessionEnded,
 * dismissSessionEnded }`. Signing in and changing a password are `authActions.js`'s, loaded with the pages that do them;
 * the provider's own server conversations are `authServer.js`'s, loaded a moment after the shell. This file is in the
 * first load, so it holds only what the shell needs to know who is there.
 *
 * The DEFAULT VALUE is the anonymous caller, deliberately -- unlike `ThemeContext`, which has none. A screen that asks
 * `useAuth()` outside a provider (every test that mounts one page) gets "nobody is signed in" rather than a throw, which
 * is also what an open-mode visitor without an account is.
 */
export const ANONYMOUS = Object.freeze({
  status: 'anonymous',
  mode: 'open',
  user: null,
  can: () => false,
  signOut: async () => {},
  sessionEnded: false,
  dismissSessionEnded: () => {},
});

const AuthContext = createContext(ANONYMOUS);

const noSessionOnTheServer = () => null;

// LAZY (OB17a): the mode, the user read again and signing out -- see `authServer.js`.
const server = () => import('./authServer');
const unreachable = (error) => reportError('AuthProvider.server', error);

/**
 * The real caller, from the session `api.js` sends (`session.js`), read as an external store: a session the server ends
 * mid-request is gone from the header in the same render as from the next request, and one a sign-in saves is here at once.
 *
 * 🔑 THE SERVER DECIDES THE MODE; THIS ONLY ASKS. Until `GET /api/auth/mode` answers, the tab's last answer stands (or
 * `open`), and a failed read changes nothing: in required mode the server refuses every anonymous request whatever the
 * browser believes, so a wrong guess here costs a redirect, never access.
 */
export const AuthProvider = ({ children }) => {
  const session = useSyncExternalStore(subscribeSession, readSession, noSessionOnTheServer);
  const [mode, setMode] = useState(readMode);
  const [ended, setEnded] = useState(false);

  useEffect(() => {
    let current = true;
    server()
      .then((s) => s.askMode())
      .then((answer) => {
        if (current && answer) setMode(answer);
      })
      .catch(unreachable);
    return () => {
      current = false;
    };
  }, []);

  // A session found in the tab is read again from the server, once a page load (`authServer.refreshUser`).
  const token = session?.token;
  useEffect(() => {
    if (token) server().then((s) => s.refreshUser(token)).catch(unreachable);
  }, [token]);

  useEffect(() => onSessionEnded(({ hadSession }) => {
    if (hadSession) setEnded(true);
  }), []);

  const signOut = useCallback(async () => {
    try {
      await (await server()).signOutNow();
    } catch (error) {
      // The module itself did not arrive: the tab still forgets the session.
      unreachable(error);
      clearSession();
    } finally {
      setEnded(false);
    }
  }, []);

  const dismissSessionEnded = useCallback(() => setEnded(false), []);

  const user = session?.user ?? null;
  const value = useMemo(() => {
    // "Your session has ended" is true only while nobody is signed in again.
    const shared = { mode, signOut, sessionEnded: ended && !user, dismissSessionEnded };
    if (!user) return { ...ANONYMOUS, ...shared };
    const held = new Set(Array.isArray(user.permissions) ? user.permissions : []);
    return { ...shared, status: 'signed-in', user, can: (permission) => held.has(permission) };
  }, [user, mode, signOut, ended, dismissSessionEnded]);

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
};

export { AuthContext };
