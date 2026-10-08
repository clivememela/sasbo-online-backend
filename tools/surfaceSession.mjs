/**
 * A SIGNED-IN SESSION FOR THE BROWSER RUN -- the one definition `surfaces.mjs` plants and `serve-dist.mjs` answers.
 *
 * Screens behind `RequirePermission` show their refusal to nobody, so a surface that measures one declares
 * `session: [<permission names>]` and is reached signed in, exactly as the deck reaches a signed-in state
 * (contracts/signin.md, "For the deck"): `sessionStorage['sasbo.session'] = {token, expiresAt, user}` before any page
 * script runs, and `GET /api/auth/me` answering that user.
 *
 * 🔑 THE TOKEN CARRIES ITS PERMISSIONS, SO THE STUB HOLDS NO STATE. `serve-dist` reads them back off the
 * `Authorization` header (`userForToken`), so a surface planted with `['USERS_ADMIN']` is answered as that user and no
 * other, however many surfaces ran before it. Test-only tooling: a real token is 256 random bits and means nothing.
 *
 * ⚠️ SYNTHETIC DATA ONLY (R-SEC-H): an invented person, an address that cannot exist.
 */
export const SESSION_KEY = 'sasbo.session';
const PREFIX = 'surface-session:';

/** The user a planted token stands for -- the contract's `user` shape, holding exactly `permissions`. */
export const surfaceUser = (permissions) => ({
  id: 9100,
  username: 'surface.tester',
  displayName: 'Surface Tester',
  email: 'surface.tester@example.invalid',
  position: { code: 'SYSTEM_ADMINISTRATOR', name: 'System Administrator' },
  role: { code: 'SURFACE_ROLE', name: 'Surface role' },
  permissions: [...permissions],
  scope: { type: 'ALL', codes: [] },
  mustChangePassword: false,
  lastSignInAt: '2026-10-05T07:30:00Z',
});

/** What `surfaces.mjs` plants: the contract's session for those permissions. */
export const surfaceSession = (permissions) => ({
  token: `${PREFIX}${permissions.join(',')}`,
  expiresAt: '2099-01-01T00:00:00Z',
  user: surfaceUser(permissions),
});

/** The user an `Authorization` header stands for, or `null` for no token, or one this file did not make. */
export const userForToken = (authorization) => {
  const token = /^Bearer (.+)$/.exec(authorization ?? '')?.[1];
  if (!token?.startsWith(PREFIX)) return null;
  const permissions = token.slice(PREFIX.length).split(',').filter(Boolean);
  return surfaceUser(permissions);
};

/**
 * A page expression, run BEFORE navigating (as `storeTheme` is): plants the session for `permissions`, or -- for a
 * surface with none -- removes any session a previous surface left in the tab, so no surface inherits another's user.
 */
export const storeSession = (permissions) => `(() => {
  try {
    ${Array.isArray(permissions)
    ? `sessionStorage.setItem(${JSON.stringify(SESSION_KEY)}, ${JSON.stringify(JSON.stringify(surfaceSession(permissions)))});`
    : `sessionStorage.removeItem(${JSON.stringify(SESSION_KEY)});`}
  } catch { /* storage refused: the surface is measured signed out, and its presence checks say so */ }
  return true;
})()`;
