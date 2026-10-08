import { useContext } from 'react';
import { AuthContext } from './AuthContext';

/**
 * `{ status, mode, user, can(permission), signOut, sessionEnded }` -- the anonymous caller outside a provider. Signing
 * in is `authActions.js`'s, loaded with /login.
 */
export const useAuth = () => useContext(AuthContext);
