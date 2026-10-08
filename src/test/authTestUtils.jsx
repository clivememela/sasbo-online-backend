import { useMemo } from 'react';
import { AuthContext, ANONYMOUS } from '../auth/AuthContext';

/**
 * A signed-in user for a test, holding exactly the permissions it is given.
 *
 *   <TestAuthProvider permissions={[PERMISSIONS.PAYROLL_PROCESS]}>…</TestAuthProvider>
 *
 * With no `permissions` (or `signedIn={false}`) it is the anonymous caller, so the refused state of a screen is
 * tested with the same wrapper as the allowed one.
 */
export function TestAuthProvider({ permissions = [], signedIn = permissions.length > 0, user, mode = 'open', children }) {
  const value = useMemo(() => {
    if (!signedIn) return { ...ANONYMOUS, mode };
    const held = new Set(permissions);
    return {
      ...ANONYMOUS,
      status: 'signed-in',
      mode,
      user: user ?? {
        id: 1,
        username: 'test.user',
        displayName: 'Test User',
        position: { code: 'HEAD_OFFICE_ADMINISTRATOR', name: 'Head Office Administrator' },
        permissions: [...held],
        scope: { type: 'ALL', codes: [] },
        mustChangePassword: false,
      },
      can: (permission) => held.has(permission),
    };
  }, [permissions, signedIn, user, mode]);

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export default TestAuthProvider;
